package dev.veltraph.client.capture;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Local bounded append-only snapshots. Does not depend on Minecraft or interpret observations. */
public final class GuiCaptureSession {
    public static final int MAX_SNAPSHOTS = 1200;
    public static final int MAX_EVENTS = 6000;
    public static final int MAX_EVENT_BYTES = 16 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 512 * 1024;
    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public static final long MAX_DURATION_MILLIS = 30L * 60 * 1000;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path directory;
    private final String label;
    private final long startedMillis;
    private int snapshots, events;
    private long bytes, recordedEventBytes;
    private boolean active = true;
    private String stopReason = "";
    private String lastContent;
    private final JsonArray files = new JsonArray();
    private final JsonArray eventFiles = new JsonArray();

    public GuiCaptureSession(Path root, String label, long nowMillis) throws IOException {
        if (!validLabel(label)) throw new IllegalArgumentException("Label must contain 1-48 letters, digits, underscores or hyphens and begin with a letter or digit");
        if (root == null || !root.isAbsolute() || !root.equals(root.normalize())) throw new IllegalArgumentException("An explicit normalized absolute output directory is required");
        // A removed external disk must never cause a replacement directory on the boot volume.
        if (root.startsWith(Path.of("/Volumes")) && (root.getNameCount() < 2 || !Files.isDirectory(Path.of("/Volumes").resolve(root.getName(1)))))
            throw new IOException("Capture volume is unavailable");
        rejectSymlinks(root);
        Files.createDirectories(root);
        rejectSymlinks(root);
        this.label = label;
        this.startedMillis = nowMillis;
        var stamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(nowMillis));
        directory = root.resolve(stamp + "_" + label + "_" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectory(directory);
        saveManifest(nowMillis);
    }
    public static boolean validLabel(String label) { return label != null && label.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,47}"); }
    static void rejectSymlinks(Path path) throws IOException {
        for (Path part = path; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw new IOException("Capture output cannot follow symbolic links");
    }
    public Path directory() { return directory; }
    public int snapshots() { return snapshots; }
    public boolean active() { return active; }
    public String stopReason() { return stopReason; }
    public boolean expired(long nowMillis) { return nowMillis - startedMillis >= MAX_DURATION_MILLIS; }
    /** content excludes observation timestamps, so unchanged screens do not emit duplicate files. */
    public boolean record(JsonObject content, JsonObject timing, long nowMillis) throws IOException {
        if (!active) return false;
        if (expired(nowMillis)) { stop("duration_limit", nowMillis); return false; }
        String contentJson = content.toString();
        if (contentJson.equals(lastContent)) return false;
        JsonObject snapshot = content.deepCopy();
        timing.entrySet().forEach(e -> snapshot.add(e.getKey(), e.getValue().deepCopy()));
        snapshot.addProperty("snapshotSequence", snapshots + 1);
        byte[] encoded = JSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_SNAPSHOT_BYTES) { stop("snapshot_size_limit", nowMillis); return false; }
        if (snapshots >= MAX_SNAPSHOTS || bytes + encoded.length > MAX_TOTAL_BYTES) { stop("session_size_limit", nowMillis); return false; }
        rejectSymlinks(directory);
        String filename = String.format(java.util.Locale.ROOT, "snapshot-%05d.json", snapshots + 1);
        Files.write(directory.resolve(filename), encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        snapshots++;
        bytes += encoded.length;
        lastContent = contentJson;
        files.add(filename);
        saveManifest(nowMillis);
        return true;
    }
    /** Action/menu boundary evidence is append-only and shares the session byte/duration budget. */
    public boolean recordEvent(JsonObject event, long nowMillis) throws IOException {
        if (!active) return false;
        if (expired(nowMillis)) { stop("duration_limit", nowMillis); return false; }
        var evidence = event.deepCopy(); evidence.addProperty("eventSequence", events + 1);
        byte[] encoded = JSON.toJson(evidence).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_EVENT_BYTES) { stop("event_size_limit", nowMillis); return false; }
        if (events >= MAX_EVENTS || bytes + encoded.length > MAX_TOTAL_BYTES) { stop("session_size_limit", nowMillis); return false; }
        rejectSymlinks(directory);
        String filename = String.format(java.util.Locale.ROOT, "event-%05d.json", events + 1);
        Files.write(directory.resolve(filename), encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        events++; bytes += encoded.length; recordedEventBytes += encoded.length; eventFiles.add(filename); saveManifest(nowMillis); return true;
    }
    public void stop(String reason, long nowMillis) throws IOException {
        active = false;
        stopReason = reason;
        saveManifest(nowMillis);
    }
    private void saveManifest(long nowMillis) throws IOException {
        rejectSymlinks(directory);
        Path destination = directory.resolve("manifest.json");
        if (Files.isSymbolicLink(destination)) throw new IOException("Capture manifest cannot be a symbolic link");
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", 2);
        manifest.addProperty("captureKind", "native_gui_observations");
        manifest.addProperty("label", label);
        manifest.addProperty("startedAtUtc", Instant.ofEpochMilli(startedMillis).toString());
        manifest.addProperty("updatedAtUtc", Instant.ofEpochMilli(nowMillis).toString());
        manifest.addProperty("active", active);
        manifest.addProperty("stopReason", stopReason);
        manifest.addProperty("snapshotCount", snapshots);
        manifest.addProperty("snapshotBytes", bytes - recordedEventBytes);
        manifest.addProperty("eventCount", events);
        manifest.addProperty("eventBytes", recordedEventBytes);
        manifest.addProperty("totalEvidenceBytes", bytes);
        manifest.addProperty("maxEvents", MAX_EVENTS);
        manifest.addProperty("maxEventBytes", MAX_EVENT_BYTES);
        manifest.addProperty("targetProvenanceNote", "Native API action arguments and menu boundaries are observations. Caller identity, server outcome and causal menu-to-plant binding remain unknown; temporal candidates never authorize care.");
        manifest.addProperty("minimumSampleIntervalMillis", 250);
        manifest.addProperty("maxSnapshots", MAX_SNAPSHOTS);
        manifest.addProperty("maxSnapshotBytes", MAX_SNAPSHOT_BYTES);
        manifest.addProperty("maxTotalBytes", MAX_TOTAL_BYTES);
        manifest.addProperty("maxDurationMillis", MAX_DURATION_MILLIS);
        manifest.addProperty("samplingNote", "Changes visible only between samples may be missed; files contain observations, never inferred values or formulas.");
        manifest.addProperty("privacyBoundary", "Only explicitly started native container menus and native gameplay action targets/pose/context epochs; excludes player inventory, carried stack, ender chest, accounts, authentication, chat, network traffic and server address.");
        manifest.add("snapshots", files.deepCopy());
        manifest.add("events", eventFiles.deepCopy());
        Path temporary = directory.resolve("manifest-" + UUID.randomUUID() + ".tmp");
        Files.writeString(temporary, JSON.toJson(manifest), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
}
