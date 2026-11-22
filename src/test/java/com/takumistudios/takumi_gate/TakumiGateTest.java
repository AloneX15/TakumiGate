package com.takumistudios.takumi_gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests unitarios de la lógica pura (sin Minecraft). Todo bug corregido añade su caso aquí o en los gametests. */
class TakumiGateTest {
    private static final Instant T0 = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void modIdIsValid() {
        assertTrue(TakumiGate.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"));
    }

    // ─── Tickets ──────────────────────────────────────────────────────────────

    @Test
    void ticketsAreValidated() {
        assertEquals(Optional.of("tkt_AbC-_09xyzXYZ"), GateProtocol.cleanTicket("  tkt_AbC-_09xyzXYZ \n"));
        assertTrue(GateProtocol.cleanTicket(null).isEmpty());
        assertTrue(GateProtocol.cleanTicket("").isEmpty());
        assertTrue(GateProtocol.cleanTicket("tgk_serverkeynotaticket").isEmpty());
        assertTrue(GateProtocol.cleanTicket("tkt_with space").isEmpty());
        assertTrue(GateProtocol.cleanTicket("tkt_" + "a".repeat(200)).isEmpty());
    }

    @Test
    void uuidsAreCompactedLikeTheWebsite() {
        UUID id = UUID.fromString("0123ABCD-0000-4000-8000-00000000FFFF");
        assertEquals("0123abcd00004000800000000000ffff", GateProtocol.compactUuid(id));
    }

    // ─── Update policy ─────────────────────────────────────────────────────────

    private static UpdatePolicy.Release release(int version, boolean restart, int grace, Instant at) {
        return new UpdatePolicy.Release(version, restart, grace, at);
    }

    @Test
    void onlyNewerRestartReleasesSetADeadline() {
        List<UpdatePolicy.Release> releases = List.of(
                release(4, false, 5, T0.plusSeconds(600)),
                release(3, true, 15, T0),
                release(2, true, 0, T0.minusSeconds(3600)));
        assertEquals(Optional.of(T0.plusSeconds(15 * 60)), UpdatePolicy.deadline(2, releases));
        assertEquals(3, UpdatePolicy.targetVersion(2, releases));
        // Resource-pack-only releases never force anyone out.
        assertTrue(UpdatePolicy.deadline(3, releases).isEmpty());
        assertEquals(0, UpdatePolicy.targetVersion(3, releases));
    }

    @Test
    void theEarliestDeadlineWins() {
        List<UpdatePolicy.Release> releases = List.of(
                release(3, true, 0, T0.plusSeconds(60)),
                release(2, true, 30, T0));
        assertEquals(Optional.of(T0.plusSeconds(60)), UpdatePolicy.deadline(1, releases));
    }

    @Test
    void noticesAreSentOnceThenKick() {
        Instant deadline = T0.plusSeconds(12 * 60);
        UpdatePolicy.Notified state = new UpdatePolicy.Notified();
        assertEquals(UpdatePolicy.Action.ANNOUNCE, UpdatePolicy.decide(T0, deadline, state));
        assertEquals(UpdatePolicy.Action.NONE, UpdatePolicy.decide(T0.plusSeconds(5), deadline, state));
        assertEquals(UpdatePolicy.Action.WARN, UpdatePolicy.decide(T0.plusSeconds(2 * 60 + 1), deadline, state));
        assertEquals(UpdatePolicy.Action.NONE, UpdatePolicy.decide(T0.plusSeconds(3 * 60), deadline, state));
        assertEquals(UpdatePolicy.Action.WARN, UpdatePolicy.decide(T0.plusSeconds(7 * 60), deadline, state));
        assertEquals(UpdatePolicy.Action.WARN, UpdatePolicy.decide(T0.plusSeconds(11 * 60 + 30), deadline, state));
        assertEquals(UpdatePolicy.Action.NONE, UpdatePolicy.decide(T0.plusSeconds(11 * 60 + 40), deadline, state));
        assertEquals(UpdatePolicy.Action.KICK, UpdatePolicy.decide(deadline, deadline, state));
    }

    @Test
    void graceZeroKicksAtOnceAndANewDeadlineAnnouncesAgain() {
        UpdatePolicy.Notified state = new UpdatePolicy.Notified();
        assertEquals(UpdatePolicy.Action.KICK, UpdatePolicy.decide(T0, T0, state));
        Instant later = T0.plusSeconds(3 * 60);
        assertEquals(UpdatePolicy.Action.ANNOUNCE, UpdatePolicy.decide(T0, later, state));
        assertEquals(3, UpdatePolicy.minutesLeft(T0, later));
        assertEquals(1, UpdatePolicy.minutesLeft(T0, T0.plusSeconds(1)));
        assertEquals(0, UpdatePolicy.minutesLeft(T0, T0.minusSeconds(10)));
    }

    // ─── Website answers ──────────────────────────────────────────────────────────

    @Test
    void verifyAnswersAreParsed() {
        GateApi.Verdict ok = GateApi.parseVerify(200,
                "{\"ok\":true,\"username\":\"marcelo\",\"version\":3,\"current_version\":4,\"restart_required\":false}");
        assertEquals(new GateApi.Verdict.Allowed("marcelo", 3, false), ok);
        GateApi.Verdict denied = GateApi.parseVerify(403, "{\"ok\":false,\"error\":\"Ya no eres miembro.\"}");
        assertEquals(new GateApi.Verdict.Denied("Ya no eres miembro."), denied);
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(401, "{\"error\":\"x\"}"));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(500, "<html>"));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(200, "{\"ok\":true}"));
        GateApi.Verdict longReason = GateApi.parseVerify(403, "{\"error\":\"" + "x".repeat(1000) + "\"}");
        assertEquals(300, ((GateApi.Verdict.Denied) longReason).reason().length());
    }

    /** Bug: a refusal with a malformed body became "unavailable", which allow_when_api_down let in. */
    @Test
    void malformedRefusalsAreStillDenials() {
        assertEquals(new GateApi.Verdict.Denied("Acceso denegado."), GateApi.parseVerify(403, "{\"error\":null}"));
        assertEquals(new GateApi.Verdict.Denied("Acceso denegado."), GateApi.parseVerify(403, "{\"error\":{\"x\":1}}"));
        assertEquals(new GateApi.Verdict.Denied("Acceso denegado."), GateApi.parseVerify(403, "<html>"));
        assertEquals(new GateApi.Verdict.Denied("No."), GateApi.parseVerify(200, "{\"ok\":false,\"error\":\"No.\"}"));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(200, "{\"ok\":\"yes\",\"username\":\"a\",\"version\":1}"));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(200, "{\"ok\":true,\"username\":null,\"version\":1}"));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(200, "{\"ok\":true,\"username\":\"a\",\"version\":\"x\"}"));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(200, null));
        assertEquals(new GateApi.Verdict.Allowed("a", 2, false),
                GateApi.parseVerify(200, "{\"ok\":true,\"username\":\"a\",\"version\":2,\"restart_required\":null}"));
    }

    @Test
    void statusIsParsed() {
        List<UpdatePolicy.Release> releases = GateApi.parseStatus("""
                {"instance":"i","name":"Evento","version":2,"releases":[
                  {"version":2,"restart_required":true,"grace_minutes":5,"notes":"","published_at":"2026-11-15T12:00:00.000Z"},
                  {"version":1,"restart_required":true,"grace_minutes":0,"notes":"x","published_at":"2026-11-14T12:00:00.000Z"}]}
                """);
        assertEquals(2, releases.size());
        assertEquals(new UpdatePolicy.Release(2, true, 5, T0), releases.getFirst());
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    @Test
    void configIsCreatedValidatedAndRecovered(@TempDir Path dir) throws Exception {
        GateConfig created = GateConfig.load(dir);
        assertFalse(created.active(), "no key yet");
        assertNotNull(created.problem());
        assertTrue(Files.exists(dir.resolve(GateConfig.FILE_NAME)));

        Files.writeString(dir.resolve(GateConfig.FILE_NAME), """
                {"config_version":1,"enabled":true,"api_url":"https://takumilabs.org/","server_key":"tgk_0123456789abcdefghij",
                 "poll_seconds":2,"bypass_uuids":["0123abcd00004000800000000000ffff","nope"]}
                """, StandardCharsets.UTF_8);
        GateConfig loaded = GateConfig.load(dir);
        assertTrue(loaded.active());
        assertNull(loaded.problem());
        assertEquals("https://takumilabs.org", loaded.baseUrl());
        assertEquals(10, loaded.pollSeconds(), "clamped to the minimum");
        assertEquals(Set.of(UUID.fromString("0123abcd-0000-4000-8000-00000000ffff")), loaded.bypass());

        Files.writeString(dir.resolve(GateConfig.FILE_NAME), "{ not json", StandardCharsets.UTF_8);
        GateConfig recovered = GateConfig.load(dir);
        assertEquals(GateConfig.defaults(), recovered);
        assertEquals("{ not json", Files.readString(dir.resolve(GateConfig.FILE_NAME + ".bak")));
    }

    @Test
    void onlySafeApiUrls() {
        assertTrue(GateConfig.isSafeUrl("https://takumilabs.org"));
        assertTrue(GateConfig.isSafeUrl("http://localhost:5173"));
        assertFalse(GateConfig.isSafeUrl("http://takumilabs.org"));
        assertFalse(GateConfig.isSafeUrl("ftp://x"));
        assertFalse(GateConfig.isSafeUrl("not a url"));
    }

    // ─── Launcher signals ───────────────────────────────────────────────────────

    @Test
    void launcherSignalsAreParsedDefensively() {
        Optional<Signals.Notice> notice = Signals.parseNotice(
                "{\"version\":5,\"grace_minutes\":10,\"deadline\":\"2026-11-15T12:00:00Z\",\"notes\":\"Nuevo jefe\"}");
        assertEquals(Optional.of(new Signals.Notice(5, 10, T0, "Nuevo jefe")), notice);
        assertTrue(Signals.parseNotice("{\"version\":5}").isEmpty());
        assertTrue(Signals.parseNotice("garbage").isEmpty());

        assertEquals(List.of("file/Evento.zip", "file/Carpeta"),
                Signals.parsePacks("{\"version\":2,\"packs\":[\"file/Evento.zip\",\"file/Carpeta\",\"file/../x\",\"vanilla\",\"file/a/b\"]}"));
        assertEquals(List.of(), Signals.parsePacks("[]"));
    }

    /** notice.json exactly as Takumi Launcher writes it (serde_json pretty + chrono to_rfc3339, nanos and +00:00). */
    @Test
    void launcherNoticeFormatIsUnderstood() {
        Optional<Signals.Notice> notice = Signals.parseNotice("""
                {
                  "version": 7,
                  "grace_minutes": 5,
                  "deadline": "2026-11-15T12:00:00.123456789+00:00",
                  "notes": ""
                }""");
        assertEquals(Optional.of(new Signals.Notice(7, 5, T0.plusNanos(123_456_789), "")), notice);
    }

    /** Bug: the website answers 400 for an offline name that is not a Minecraft name; that is not an outage. */
    @Test
    void clientErrorsAreDenialsNotOutages() {
        assertInstanceOf(GateApi.Verdict.Denied.class, GateApi.parseVerify(400, "{\"error\":\"Falta uuid o name.\"}"));
        assertInstanceOf(GateApi.Verdict.Denied.class, GateApi.parseVerify(404, ""));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(429, ""));
        assertInstanceOf(GateApi.Verdict.Unavailable.class, GateApi.parseVerify(503, ""));
    }
}
