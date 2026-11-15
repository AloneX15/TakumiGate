package com.takumistudios.takumi_gate;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Login handshake between the Takumi Gate client and server. During login the server sends a query on
 * {@code takumi_gate:ticket}; the client answers with {@code [protocol: byte][ticket: UTF-8 string]}. The ticket comes
 * from Takumi Launcher (environment variable {@code TAKUMI_GATE_TICKET}) and the server checks it with the website.
 */
public final class GateProtocol {
    public static final String CHANNEL_PATH = "ticket";
    /** Bumped when the payload changes; the server refuses other versions with a clear message. */
    public static final int VERSION = 1;
    public static final int MAX_TICKET_LENGTH = 128;
    public static final String TICKET_ENV = "TAKUMI_GATE_TICKET";

    private GateProtocol() {
    }

    /** System property read when the environment variable is missing (development and tests only). */
    public static final String TICKET_PROPERTY = "takumi_gate.ticket";

    /** The ticket Takumi Launcher gave this game, if any. Read on every login, so a test can change it. */
    public static Optional<String> launcherTicket() {
        String fromEnv = System.getenv(TICKET_ENV);
        return cleanTicket(fromEnv != null ? fromEnv : System.getProperty(TICKET_PROPERTY));
    }

    /** A ticket as issued by the website ({@code tkt_} + base64url), or empty. */
    public static Optional<String> cleanTicket(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String ticket = raw.trim();
        if (ticket.length() < 8 || ticket.length() > MAX_TICKET_LENGTH || !ticket.startsWith("tkt_")) {
            return Optional.empty();
        }
        for (int i = 4; i < ticket.length(); i++) {
            char c = ticket.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return Optional.empty();
            }
        }
        return Optional.of(ticket);
    }

    /** Profile id as the website stores it: 32 lowercase hex characters. */
    public static String compactUuid(UUID id) {
        return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }
}
