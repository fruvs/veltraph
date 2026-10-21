package dev.veltraph.client.capture.tool;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class SelectedToolCaptureSessionTest {
 @TempDir Path temp;
 private Path root()throws Exception{return temp.toRealPath();}
 private JsonObject frame(String event){var f=new JsonObject();f.addProperty("manualEvent",event);return f;}
 @Test void manualStartSamplesFinishAreBoundedAndAppendOnly()throws Exception{var s=new SelectedToolCaptureSession(root(),"tool",0);assertTrue(s.record(frame("start"),1));assertTrue(s.record(frame("sample"),2));assertTrue(s.record(frame("finish"),3));s.stop("user_finish",4);assertFalse(s.record(frame("after"),5));assertEquals(3,s.frames());var m=JsonParser.parseString(Files.readString(s.directory().resolve("manifest.json"))).getAsJsonObject();assertEquals("manual_selected_farming_tool",m.get("captureKind").getAsString());assertFalse(m.get("automaticSampling").getAsBoolean());assertTrue(m.get("privacyBoundary").getAsString().contains("salt is never saved"));}
 @Test void rollbackAndOversizedSampleStopWithoutOverwritingEvidence()throws Exception{var s=new SelectedToolCaptureSession(root(),"tool",10);s.record(frame("start"),11);assertFalse(s.record(frame("x".repeat(SelectedToolCaptureSession.MAX_FRAME_BYTES)),12));assertFalse(s.active());assertTrue(Files.exists(s.directory().resolve("context-00001.json")));var t=new SelectedToolCaptureSession(root(),"clock",10);t.record(frame("start"),11);assertFalse(t.record(frame("later"),10));assertEquals("clock_rollback",t.stopReason());}
 @Test void durationAndMissingVolumeFailClosed()throws Exception{var s=new SelectedToolCaptureSession(root(),"duration",0);s.record(frame("start"),1);assertFalse(s.record(frame("later"),SelectedToolCaptureSession.MAX_DURATION_MS));assertFalse(s.active());var missing=Path.of("/Volumes/veltraph-selected-tool-missing-volume-771/captures");assertFalse(Files.exists(missing.getParent()));assertThrows(java.io.IOException.class,()->new SelectedToolCaptureSession(missing,"tool",0));assertFalse(Files.exists(missing.getParent()));}
 @Test void sameLabelCreatesNewDirectoryAndUnsafeOutputFails()throws Exception{var r=root();var a=new SelectedToolCaptureSession(r,"same",0);var b=new SelectedToolCaptureSession(r,"same",0);assertNotEquals(a.directory(),b.directory());assertThrows(IllegalArgumentException.class,()->new SelectedToolCaptureSession(r,"../bad",0));var target=r.resolve("target");Files.createDirectory(target);var link=r.resolve("link");Files.createSymbolicLink(link,target);assertThrows(java.io.IOException.class,()->new SelectedToolCaptureSession(link,"tool",0));}
}
