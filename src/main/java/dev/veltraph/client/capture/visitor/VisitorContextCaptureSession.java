package dev.veltraph.client.capture.visitor;

import com.google.gson.*;
import dev.veltraph.client.capture.GuiCaptureSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Explicit opt-in, bounded, append-only local visible gameplay context. No identity inference. */
public final class VisitorContextCaptureSession {
    public static final int MAX_FRAMES=1200,MAX_FRAME_BYTES=256*1024;
    public static final long MAX_BYTES=32L*1024*1024,MAX_DURATION_MS=20L*60*1000;
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path directory;private final String label;private final long started;
    private long bytes,lastTime;private int frames;private boolean active=true;
    private String lastContent,reason="";private final JsonArray files=new JsonArray();
    public VisitorContextCaptureSession(Path root,String label,long now)throws IOException{
        if(root==null||!root.isAbsolute()||!root.equals(root.normalize())||!GuiCaptureSession.validLabel(label)||now<0)throw new IllegalArgumentException("Normalized absolute path and valid capture label required");
        if(root.startsWith(Path.of("/Volumes"))&&(root.getNameCount()<2||!Files.isDirectory(Path.of("/Volumes").resolve(root.getName(1)))))throw new IOException("Capture volume unavailable");
        rejectLinks(root);Files.createDirectories(root);rejectLinks(root);this.label=label;started=lastTime=now;
        String timestamp=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(now));
        directory=root.resolve(timestamp+"_"+label+"_"+UUID.randomUUID().toString().substring(0,8));Files.createDirectory(directory);manifest(now);
    }
    public boolean record(JsonObject content,long now)throws IOException{
        if(!active)return false;
        if(now<lastTime||now-started>=MAX_DURATION_MS){stop(now<lastTime?"clock_rollback":"duration_limit",Math.max(now,lastTime));return false;}
        lastTime=now;String plain=content.toString();if(plain.equals(lastContent))return false;
        var frame=content.deepCopy();frame.addProperty("observedAtUtc",Instant.ofEpochMilli(now).toString());frame.addProperty("observedAtEpochMillis",now);frame.addProperty("frameSequence",frames+1);
        byte[] data=JSON.toJson(frame).getBytes(StandardCharsets.UTF_8);
        if(data.length>MAX_FRAME_BYTES||frames>=MAX_FRAMES||bytes+data.length>MAX_BYTES){stop("size_limit",now);return false;}
        rejectLinks(directory);String name=String.format(Locale.ROOT,"context-%05d.json",frames+1);
        Files.write(directory.resolve(name),data,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        frames++;bytes+=data.length;lastContent=plain;files.add(name);manifest(now);return true;
    }
    public void stop(String reason,long now)throws IOException{active=false;this.reason=reason;manifest(now);}
    public boolean active(){return active;}public int frames(){return frames;}public String stopReason(){return reason;}public Path directory(){return directory;}
    private void manifest(long now)throws IOException{
        rejectLinks(directory);var m=new JsonObject();m.addProperty("schemaVersion",1);m.addProperty("captureKind","visitor_visible_context_observations");m.addProperty("label",label);m.addProperty("startedAtUtc",Instant.ofEpochMilli(started).toString());m.addProperty("updatedAtUtc",Instant.ofEpochMilli(now).toString());m.addProperty("active",active);m.addProperty("stopReason",reason);m.addProperty("frames",frames);m.addProperty("bytes",bytes);
        m.addProperty("minimumSampleIntervalMillis",250);m.addProperty("maxFrames",MAX_FRAMES);m.addProperty("maxFrameBytes",MAX_FRAME_BYTES);m.addProperty("maxBytes",MAX_BYTES);m.addProperty("maxDurationMillis",MAX_DURATION_MS);
        m.addProperty("privacyBoundary","Explicit local visible sidebar/tab/menu context and non-player armor-stand/ray geometry. Explicit selected main-hand item metadata on tool command only; no inventory enumeration, authentication/account files, credentials, chat, network payloads or server addresses. Gameplay fields are observations, not confirmed profile/Garden identities.");m.add("files",files.deepCopy());
        Path target=directory.resolve("manifest.json"),temp=directory.resolve("manifest-"+UUID.randomUUID()+".tmp");if(Files.isSymbolicLink(target))throw new IOException("Symlink capture manifest");
        Files.writeString(temp,JSON.toJson(m),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private static void rejectLinks(Path p)throws IOException{for(var q=p;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("Capture path is a symlink");}
}
