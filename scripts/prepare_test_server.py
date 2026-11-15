"""Install pinned upstream servers in build/server-tests for packaged-jar login checks."""

import argparse
import hashlib
import shutil
import subprocess
import tomllib
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARCLIGHT = {
    "1.21.1": ("FeudalKings/1.0.1", "1.0.1-8ec9529"),
    "26.1.2": ("Dipper/1.1.0-SNAPSHOT", "1.1.0-SNAPSHOT-9e3febe"),
}


def download(url, target, sha256=None):
    if target.exists():
        if sha256 is None or hashlib.sha256(target.read_bytes()).hexdigest() == sha256:
            return
    target.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "TakumiGate-tests"})
    partial = target.with_suffix(target.suffix + ".tmp")
    with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as output:
        shutil.copyfileobj(response, output)
    if sha256 is not None and hashlib.sha256(partial.read_bytes()).hexdigest() != sha256:
        raise ValueError(f"Checksum mismatch: {target.name}")
    partial.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True, choices=["fabric", "quilt", "arclight"])
    parser.add_argument("--minecraft", required=True, choices=["1.21.1", "26.1.2", "26.2", "26.3"])
    parser.add_argument("--loader", default="fabric", choices=["fabric", "forge", "neoforge"])
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    mc = args.minecraft
    if args.platform == "arclight" and mc not in ARCLIGHT:
        parser.error("No pinned upstream Arclight release for this Minecraft version")
    if args.platform != "arclight" and args.loader != "fabric":
        parser.error("Fabric and Quilt use the Fabric artifact")
    run = ROOT / "build" / "server-tests" / f"{args.platform}-{args.loader}-{mc}"
    run.mkdir(parents=True, exist_ok=True)
    tools = ROOT / "build" / "test-tools"
    if args.platform == "fabric":
        download(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/0.20.1/1.1.2/server/jar", run / "server-launch.jar")
    elif args.platform == "quilt":
        installer = tools / "quilt-installer.jar"
        download("https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/0.15.1/quilt-installer-0.15.1.jar", installer)
        loader_version = "0.30.2" if mc == "1.21.1" else "0.31.0-beta.6"
        subprocess.run([args.java, "-jar", str(installer), "install", "server", mc, loader_version,
                        "--download-server", "--install-dir=."], cwd=run, check=True)
        shutil.copyfile(run / "quilt-server-launch.jar", run / "server-launch.jar")
    else:
        tag, version = ARCLIGHT[mc]
        asset = f"arclight-{args.loader}-{mc}-{version}.jar"
        url = f"https://github.com/IzzelAliz/Arclight/releases/download/{tag}/{asset}"
        sha256 = None
        if mc == "1.21.1" and args.loader == "forge":
            # Official downloads site: stable 1.0.1 fails its own mixins even without Gate.
            asset = "arclight-forge-1.21.1-1.0.2-SNAPSHOT-a3e2991.jar"
            url = "https://files.hypertention.cn/v1/objects/635554699fa2f60e2bd21243cff7fedd0443d3fd"
            sha256 = "54bfd7b1861366bc5e279712040a91e7b78483ff2d828e71df8acb620ee491c9"
        upstream = tools / asset
        download(url, upstream, sha256)
        shutil.copyfile(upstream, run / "server-launch.jar")
    node = mc if args.loader == "fabric" else f"{mc}-{args.loader}"
    properties = tomllib.loads((ROOT / "stonecutter.properties.toml").read_text(encoding="utf-8"))
    mod_version = properties["mod"]["version"]
    artifact = ROOT / "versions" / node / "build" / "libs" / f"takumi_gate-{args.loader}-{mod_version}+mc{mc}.jar"
    if not artifact.is_file():
        raise FileNotFoundError(f"Build the packaged artifact first: {artifact}")
    mods = run / "mods"
    mods.mkdir(exist_ok=True)
    shutil.copyfile(artifact, mods / artifact.name)
    if args.loader == "fabric":
        properties = tomllib.loads((ROOT / "stonecutter.properties.toml").read_text(encoding="utf-8"))
        api = properties[mc]["deps"]["fabric_api"]
        download(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar", mods / "fabric-api.jar")
    print(run)


if __name__ == "__main__":
    main()
