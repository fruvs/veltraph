package dev.veltraph.client.capture.visitor;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorContextCaptureSessionTest {
    @TempDir Path temp;
    @org.junit.jupiter.api.BeforeEach void canonicalTemporaryPath() throws Exception { temp = temp.toRealPath(); }
    JsonObject frame(String value){var r=new JsonObject();r.addProperty("nativeObservedText",value);r.addProperty("profileIdentityStatus","UNKNOWN");return r;}
    @Test void duplicateContextDoesNotWriteDuplicateFiles()throws Exception{var s=new VisitorContextCaptureSession(temp,"profile",0);assertTrue(s.record(frame("one"),1));assertFalse(s.record(frame("one"),2));assertEquals(1,s.frames());}
    @Test void reusedLabelPreservesPreviousSession()throws Exception{var a=new VisitorContextCaptureSession(temp,"same",0);a.record(frame("old"),1);var b=new VisitorContextCaptureSession(temp,"same",0);b.record(frame("new"),1);assertNotEquals(a.directory(),b.directory());assertTrue(Files.exists(a.directory().resolve("context-00001.json")));}
    @Test void clockRollbackStopsWithoutAddingEvidence()throws Exception{var s=new VisitorContextCaptureSession(temp,"clock",10);s.record(frame("before"),11);assertFalse(s.record(frame("after"),10));assertFalse(s.active());assertEquals("clock_rollback",s.stopReason());assertEquals(1,s.frames());}
    @Test void oversizedFrameStopsAndPreservesEarlierFrames()throws Exception{var s=new VisitorContextCaptureSession(temp,"size",0);s.record(frame("keep"),1);assertFalse(s.record(frame("x".repeat(VisitorContextCaptureSession.MAX_FRAME_BYTES)),2));assertEquals(1,s.frames());assertTrue(Files.exists(s.directory().resolve("context-00001.json")));}
    @Test void durationLimitAppliesEvenToUnchangedFrames()throws Exception{var s=new VisitorContextCaptureSession(temp,"time",0);s.record(frame("same"),1);assertFalse(s.record(frame("same"),VisitorContextCaptureSession.MAX_DURATION_MS));assertFalse(s.active());}
    @Test void symlinkOutputCannotWriteOutsideRequestedFolder()throws Exception{var actual=temp.resolve("actual");Files.createDirectory(actual);var link=temp.resolve("link");Files.createSymbolicLink(link,actual);assertThrows(IOException.class,()->new VisitorContextCaptureSession(link,"label",0));try(var listing=Files.list(actual)){assertEquals(0,listing.count());}}
    @Test void unsafeLabelsCannotTraverseOutput()throws Exception{assertThrows(IllegalArgumentException.class,()->new VisitorContextCaptureSession(temp,"../escape",0));assertThrows(IllegalArgumentException.class,()->new VisitorContextCaptureSession(temp,"bad label",0));}
    @Test void missingVolumeCannotFallBackToBootDisk()throws Exception{var missing=Path.of("/Volumes/veltraph-capture-test-missing-volume-95197317/context");assertFalse(Files.exists(missing.getParent()));assertThrows(IOException.class,()->new VisitorContextCaptureSession(missing,"test",0));assertFalse(Files.exists(missing.getParent()));}
    @Test void manifestDeclaresReadonlyContextKindAndPrivacy()throws Exception{var s=new VisitorContextCaptureSession(temp,"kind",0);s.record(frame("actual"),1);s.stop("user_stop",2);var m=JsonParser.parseString(Files.readString(s.directory().resolve("manifest.json"))).getAsJsonObject();assertEquals("visitor_visible_context_observations",m.get("captureKind").getAsString());assertTrue(m.get("privacyBoundary").getAsString().contains("no inventory enumeration"));assertFalse(m.get("active").getAsBoolean());}
}
