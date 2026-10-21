package dev.veltraph.client.mining.vein;

import dev.veltraph.client.navigation.LegacyTravelRetirement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real cleanup kernel plus production dispatch: foreign/new custody cannot reach raw effects. */
final class VeinCollectionCleanupDispatchTest {
    private static final class Effects implements LegacyTravelRetirement.Effects {
        final Object original=new Object(),foreign=new Object();Object actual=original;
        final boolean[] keys={true,true,true,true,true,true,true,true,true};
        boolean camera=true,fence=true,supersedeOnCamera;int released,cancelled,raw;int supersedeAfter=-1;
        public boolean keysPublished(){return true;}public boolean cameraPublishedOrQueued(){return true;}
        public boolean activeLegMatches(){return actual==original;}public boolean cleanupFence(){return fence;}
        public boolean release(LegacyTravelRetirement.Key key){keys[key.ordinal()]=false;released++;if(released==supersedeAfter)actual=foreign;return true;}
        public boolean cancelCamera(){camera=false;cancelled++;if(supersedeOnCamera){actual=foreign;camera=true;}return true;}
        public LegacyTravelRetirement.Removal retireLogical(){if(actual==original){actual=null;return LegacyTravelRetirement.Removal.REMOVED_OWN;}return actual==null?LegacyTravelRetirement.Removal.ALREADY_ABSENT:LegacyTravelRetirement.Removal.FOREIGN_PRESERVED;}
        void rawCleanup(){raw++;java.util.Arrays.fill(keys,false);camera=false;}
    }
    private static VeinCollectionCleanupDispatch typed(){var d=new VeinCollectionCleanupDispatch();d.enterTyped();return d;}
    @Test void untouchedLegacyCleanupRemainsOriginal(){var d=new VeinCollectionCleanupDispatch();var e=new Effects();var out=d.stop(()->{fail("Unexpected typed retirement");return null;},e::rawCleanup);assertEquals(VeinCollectionCleanupDispatch.Route.LEGACY,out.route());assertEquals(1,e.raw);}
    @Test void genuineOwnedRetirementReleasesAllNineAndCameraWithoutRaw(){var d=typed();var e=new Effects();var out=d.stop(()->LegacyTravelRetirement.retire(e),e::rawCleanup);assertEquals(VeinCollectionCleanupDispatch.Route.TYPED,out.route());assertTrue(out.typed().allNineReleased());assertTrue(out.typed().cameraCancelled());assertEquals(9,e.released);assertEquals(1,e.cancelled);assertEquals(0,e.raw);}
    @Test void alreadyForeignLegPreservesAllNineCameraAndIdentity(){var d=typed();var e=new Effects();e.actual=e.foreign;var out=d.stop(()->LegacyTravelRetirement.retire(e),e::rawCleanup);assertEquals(LegacyTravelRetirement.Removal.FOREIGN_PRESERVED,out.typed().removal());assertSame(e.foreign,e.actual);for(boolean key:e.keys)assertTrue(key);assertTrue(e.camera);assertEquals(0,e.released);assertEquals(0,e.cancelled);assertEquals(0,e.raw);}
    @Test void foreignSupersessionInsideKeyCallbackCannotFallThrough(){var d=typed();var e=new Effects();e.supersedeAfter=2;var out=d.stop(()->LegacyTravelRetirement.retire(e),e::rawCleanup);assertEquals(LegacyTravelRetirement.Status.REVOKED,out.typed().status());assertSame(e.foreign,e.actual);assertEquals(2,e.released);for(int i=2;i<9;i++)assertTrue(e.keys[i]);assertTrue(e.camera);assertEquals(0,e.cancelled);assertEquals(0,e.raw);}
    @Test void ownMismatchKeepsRawEffectsUnreachable(){var d=typed();var e=new Effects();e.fence=false;var out=d.stop(()->LegacyTravelRetirement.retire(e),e::rawCleanup);assertTrue(out.typed().localFailStopCandidate());for(boolean key:e.keys)assertTrue(key);assertTrue(e.camera);assertEquals(0,e.raw);}
    @Test void newOwnerInstalledInsideCameraCancellationKeepsItsCamera(){var d=typed();var e=new Effects();e.supersedeOnCamera=true;var out=d.stop(()->LegacyTravelRetirement.retire(e),e::rawCleanup);assertEquals(LegacyTravelRetirement.Status.REVOKED,out.typed().status());assertSame(e.foreign,e.actual);assertTrue(e.camera);assertEquals(1,e.cancelled);assertEquals(0,e.raw);}
    @Test void noIssuedLegCannotBecomeLegacyCleanup(){var d=typed();var e=new Effects();var out=d.stop(()->null,e::rawCleanup);assertEquals(VeinCollectionCleanupDispatch.Route.TYPED,out.route());assertNull(out.typed());assertEquals(0,e.raw);}
    @Test void repeatedStopAfterCleanRetirementCannotEraseNewOwner(){var d=typed();var e=new Effects();var first=d.stop(()->LegacyTravelRetirement.retire(e),e::rawCleanup);assertTrue(first.typed().cleanSuccess());e.actual=e.foreign;java.util.Arrays.fill(e.keys,true);e.camera=true;var repeated=d.stop(first::typed,e::rawCleanup);assertSame(first.typed(),repeated.typed());assertSame(e.foreign,e.actual);for(boolean key:e.keys)assertTrue(key);assertTrue(e.camera);assertEquals(0,e.raw);}
    @Test void thrownTypedCleanupNeverRunsRawFallback(){var d=typed();var e=new Effects();assertThrows(IllegalStateException.class,()->d.stop(()->{throw new IllegalStateException("Native retirement unavailable");},e::rawCleanup));assertEquals(0,e.raw);for(boolean key:e.keys)assertTrue(key);assertTrue(e.camera);}
}
