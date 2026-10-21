package dev.veltraph.client.capture;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class GuiCaptureSessionTest {
    @TempDir Path temporary;
    @org.junit.jupiter.api.BeforeEach void canonicalTemporaryPath() throws Exception { temporary = temporary.toRealPath(); }
    private JsonObject content(String name, int revision) {
        var result = new JsonObject(); result.addProperty("title", name); result.addProperty("revision", revision); return result;
    }
    private JsonObject timing(long millis) { var result = new JsonObject(); result.addProperty("observedAtEpochMillis", millis); return result; }
    @Test void recordsRawChangedContentAndIgnoresTimeOnlyChanges() throws Exception {
        var session = new GuiCaptureSession(temporary.resolve("captures"), "chocolate", 1000);
        assertTrue(session.record(content("Native Factory", 5), timing(1001), 1001));
        assertFalse(session.record(content("Native Factory", 5), timing(1002), 1002));
        assertTrue(session.record(content("Native Factory", 6), timing(1003), 1003));
        session.stop("user_stop", 1004);
        assertFalse(session.active());
        var first = JsonParser.parseString(Files.readString(session.directory().resolve("snapshot-00001.json"))).getAsJsonObject();
        assertEquals(5, first.get("revision").getAsInt());
        assertEquals(1001, first.get("observedAtEpochMillis").getAsLong());
        var manifest = JsonParser.parseString(Files.readString(session.directory().resolve("manifest.json"))).getAsJsonObject();
        assertEquals(2, manifest.get("snapshotCount").getAsInt());
        assertFalse(manifest.get("active").getAsBoolean());
        assertEquals("user_stop", manifest.get("stopReason").getAsString());
        assertEquals(2, manifest.getAsJsonArray("snapshots").size());
        assertFalse(session.record(content("After stop", 7), timing(1005), 1005));
    }
    @Test void eventJournalHasSeparateTruthfulManifestTotalsAndNeverDeduplicatesBoundaries() throws Exception {
        var session = new GuiCaptureSession(temporary, "target", 1000);
        var event = new JsonObject(); event.addProperty("eventKind", "native_action_begin");
        assertTrue(session.recordEvent(event, 1001)); assertTrue(session.recordEvent(event, 1002));
        assertTrue(session.record(content("Diagnostics", 1), timing(1003), 1003)); session.stop("user_stop", 1004);
        var manifest = JsonParser.parseString(Files.readString(session.directory().resolve("manifest.json"))).getAsJsonObject();
        assertEquals(2, manifest.get("schemaVersion").getAsInt()); assertEquals(2, manifest.get("eventCount").getAsInt());
        assertEquals(2, manifest.getAsJsonArray("events").size());
        long eventBytes = Files.size(session.directory().resolve("event-00001.json")) + Files.size(session.directory().resolve("event-00002.json"));
        assertEquals(eventBytes, manifest.get("eventBytes").getAsLong());
        assertEquals(Files.size(session.directory().resolve("snapshot-00001.json")), manifest.get("snapshotBytes").getAsLong());
        assertEquals(eventBytes + manifest.get("snapshotBytes").getAsLong(), manifest.get("totalEvidenceBytes").getAsLong());
        assertFalse(session.recordEvent(event, 1005));
    }
    @Test void oversizedEventsStopBeforeWriting() throws Exception {
        var session = new GuiCaptureSession(temporary, "event_limit", 1000);
        var event = new JsonObject(); event.addProperty("tooLarge", "x".repeat(GuiCaptureSession.MAX_EVENT_BYTES));
        assertFalse(session.recordEvent(event, 1001)); assertEquals("event_size_limit", session.stopReason());
        assertFalse(Files.exists(session.directory().resolve("event-00001.json")));
    }
    @Test void labelsCannotEscapeAndSameLabelsNeverOverwrite() throws Exception {
        for (String invalid : List.of("", "../escape", "a/b", ".hidden", "space label", "a".repeat(49)))
            assertThrows(IllegalArgumentException.class, () -> new GuiCaptureSession(temporary, invalid, 1000));
        assertThrows(IllegalArgumentException.class, () -> new GuiCaptureSession(Path.of("relative"), "valid", 1000));
        var first = new GuiCaptureSession(temporary, "same", 1000);
        var second = new GuiCaptureSession(temporary, "same", 1000);
        assertNotEquals(first.directory(), second.directory());
        assertTrue(Files.exists(first.directory().resolve("manifest.json")));
    }
    @Test void symbolicLinkOutputAndManifestAreRejected() throws Exception {
        Path link = temporary.resolve("link"); Files.createSymbolicLink(link, temporary);
        assertThrows(java.io.IOException.class, () -> new GuiCaptureSession(link, "valid", 1000));
        var session = new GuiCaptureSession(temporary, "valid", 1000);
        Path target = temporary.resolve("untouched.json"); Files.writeString(target, "original");
        Files.delete(session.directory().resolve("manifest.json"));
        Files.createSymbolicLink(session.directory().resolve("manifest.json"), target);
        assertThrows(java.io.IOException.class, () -> session.stop("user_stop", 1001));
        assertEquals("original", Files.readString(target));
    }
    @Test void limitsStopRecordingWithoutWritingOversizedSnapshot() throws Exception {
        var duration = new GuiCaptureSession(temporary, "duration", 1000);
        assertFalse(duration.record(content("late", 1), timing(1000 + GuiCaptureSession.MAX_DURATION_MILLIS), 1000 + GuiCaptureSession.MAX_DURATION_MILLIS));
        assertEquals("duration_limit", duration.stopReason());
        var size = new GuiCaptureSession(temporary, "size", 1000);
        assertFalse(size.record(content("x".repeat(GuiCaptureSession.MAX_SNAPSHOT_BYTES), 1), timing(1001), 1001));
        assertEquals("snapshot_size_limit", size.stopReason());
        assertEquals(0, size.snapshots());
    }
}
