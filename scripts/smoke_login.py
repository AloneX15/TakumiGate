"""Black-box login checks against a real server, using only Python's standard library.

Use a disposable run directory. The harness writes its own config and server.properties,
starts a local fake Gate API, then runs the server command after --.
It fails if the Gate query is missing, even if FML or another mod disconnects the client.
"""

import argparse
import hashlib
import http.server
import io
import json
import os
import signal
import socket
import struct
import subprocess
import threading
import time
import uuid
import zlib
from pathlib import Path


def varint(value):
    value &= 0xFFFFFFFF
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def read_varint(stream):
    value = 0
    for shift in range(0, 35, 7):
        raw = stream.read(1)
        if not raw:
            raise EOFError("Connection closed")
        value |= (raw[0] & 127) << shift
        if raw[0] < 128:
            return value - (1 << 32) if value & (1 << 31) else value
    raise ValueError("VarInt exceeds five bytes")


def string(value):
    data = value.encode("utf-8")
    return varint(len(data)) + data


def read_string(stream):
    length = read_varint(stream)
    if not 0 <= length <= 1_048_576:
        raise ValueError("Invalid string length")
    return stream.read(length).decode("utf-8")


class Wire:
    def __init__(self, port):
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=20)
        self.stream = self.socket.makefile("rb")
        self.compression = None

    def send(self, packet, data=b""):
        body = varint(packet) + data
        if self.compression is not None:
            body = varint(len(body)) + zlib.compress(body) if len(body) >= self.compression else b"\0" + body
        self.socket.sendall(varint(len(body)) + body)

    def receive(self):
        length = read_varint(self.stream)
        if not 0 < length <= 2_097_152:
            raise ValueError("Invalid packet length")
        body = self.stream.read(length)
        if len(body) != length:
            raise EOFError("Truncated packet")
        stream = io.BytesIO(body)
        if self.compression is not None:
            size = read_varint(stream)
            stream = io.BytesIO(zlib.decompress(stream.read()) if size else stream.read())
        return read_varint(stream), stream

    def close(self):
        self.stream.close()
        self.socket.close()


def handshake(wire, port, protocol, state):
    wire.send(0, varint(protocol) + string("localhost") + struct.pack(">H", port) + varint(state))


def protocol_version(port):
    wire = Wire(port)
    try:
        handshake(wire, port, -1, 1)
        wire.send(0)
        packet, data = wire.receive()
        assert packet == 0
        return json.loads(read_string(data))["version"]["protocol"]
    finally:
        wire.close()


def offline_uuid(name):
    return uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3)


def login(port, protocol, ticket, *, version=1, trailing=False, name="GateTest"):
    wire = Wire(port)
    queried = False
    try:
        handshake(wire, port, protocol, 2)
        wire.send(0, string(name) + offline_uuid(name).bytes)
        for _ in range(40):
            packet, data = wire.receive()
            if packet == 3:
                wire.compression = read_varint(data)
            elif packet == 4:
                transaction = read_varint(data)
                channel = read_string(data)
                if channel == "takumi_gate:ticket":
                    queried = True
                    assert data.read() == b"\x01", "Unexpected Gate query format"
                    answer = b"\0" if ticket is None else b"\1" + bytes([version]) + string(ticket)
                    if trailing:
                        answer += b"\x01"
                    wire.send(2, varint(transaction) + answer)
                else:
                    wire.send(2, varint(transaction) + b"\0")
            elif packet == 0:
                assert queried, "Disconnected before Takumi Gate could check access"
                return False
            elif packet == 2:
                assert queried, "Login succeeded without a Takumi Gate query"
                return True
            else:
                raise AssertionError(f"Unexpected login packet {packet}")
        raise AssertionError("Login never completed")
    except EOFError:
        if queried and trailing:
            return False
        raise
    finally:
        wire.close()


class Api(http.server.BaseHTTPRequestHandler):
    checks = []

    def log_message(self, *_):
        pass

    def reply(self, status, body):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        self.reply(200, {"releases": []})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        type(self).checks.append(body)
        assert self.headers.get("Authorization") == "Bearer tgk_smoketest0123456789"
        ticket = body.get("ticket")
        if ticket == "tkt_Unavailable":
            self.reply(503, {})
        elif ticket == "tkt_DeniedAccount":
            self.reply(403, {"ok": False, "error": "Wrong account"})
        else:
            self.reply(200, {"ok": True, "username": "smoke", "version": 1, "current_version": 2,
                             "restart_required": ticket == "tkt_Outdated"})


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--cwd", type=Path)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("Pass the server launch command after --")
    run = args.run_dir.resolve()
    run.mkdir(parents=True, exist_ok=True)
    # Never overwrite a real server's configuration.
    config = run / "config" / "takumi_gate.json"
    if config.exists() and not (run / ".takumi-smoke").exists():
        parser.error("Use a disposable run directory without an existing Gate config")
    (run / ".takumi-smoke").touch()
    port = free_port()
    api = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Api)
    threading.Thread(target=api.serve_forever, daemon=True).start()
    config.parent.mkdir(exist_ok=True)
    config.write_text(json.dumps({"config_version": 1, "enabled": True,
                                  "api_url": f"http://127.0.0.1:{api.server_port}",
                                  "server_key": "tgk_smoketest0123456789", "poll_seconds": 30,
                                  "allow_when_api_down": False,
                                  "bypass_uuids": [str(offline_uuid("GateBypass"))]}), encoding="utf-8")
    (run / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run / "server.properties").write_text(
        f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=minecraft:flat\n"
        "white-list=false\nenforce-secure-profile=false\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n",
        encoding="utf-8")
    log = run / "smoke-console.log"
    process = None
    try:
        with log.open("w", encoding="utf-8") as output:
            process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, cwd=args.cwd, start_new_session=os.name != "nt")
            deadline = time.monotonic() + args.timeout
            while True:
                if process.poll() is not None:
                    raise AssertionError(f"Server exited with {process.returncode}; see {log}")
                if time.monotonic() > deadline:
                    raise TimeoutError(f"Server did not start; see {log}")
                try:
                    protocol = protocol_version(port)
                    break
                except (OSError, EOFError):
                    time.sleep(1)
            cases = [
                ("client without mod", None, {}, False),
                ("missing ticket", "", {}, False),
                ("wrong protocol", "tkt_ValidTicket", {"version": 2}, False),
                ("malformed ticket", "bad", {}, False),
                ("wrong account", "tkt_DeniedAccount", {}, False),
                ("outdated instance", "tkt_Outdated", {}, False),
                ("API unavailable", "tkt_Unavailable", {}, False),
                ("trailing payload", "tkt_ValidTicket", {"trailing": True}, False),
                ("valid ticket", "tkt_ValidTicket", {}, True),
                ("staff bypass", None, {"name": "GateBypass"}, True),
            ]
            for label, ticket, options, expected in cases:
                actual = login(port, protocol, ticket, **options)
                assert actual == expected, f"{label}: expected allowed={expected}, got {actual}"
                print(f"PASS {label}", flush=True)
            assert all(body.get("name") == "GateTest" and "uuid" not in body for body in Api.checks)
            assert len(Api.checks) == 4, f"Unexpected API calls: {len(Api.checks)}"
            print("PASS offline identity and API call count", flush=True)
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(b"stop\n")
                process.stdin.flush()
                process.wait(timeout=45)
            except (OSError, subprocess.TimeoutExpired):
                if os.name == "nt":
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
                else:
                    os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=15)
        api.shutdown()


if __name__ == "__main__":
    main()
