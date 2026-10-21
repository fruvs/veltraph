package dev.veltraph.client.foraging.hub;
import dev.veltraph.client.foraging.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HubSourceLoopTest {
    static class Port implements HubSourceLoop.Port {
        boolean inHub=true,path=true,stop,warpAvailable=true,walkAvailable=true;int warps,releases;List<BlockPos> walks=new ArrayList<>();
        HubSourceLoop.Motion motion=HubSourceLoop.Motion.ACTIVE,warpMotion=HubSourceLoop.Motion.ACTIVE;
        BlockPos checkpoint=HubTreeRoute.CHECKPOINTS.getFirst();boolean tree=true;
        public boolean inHub(){return inHub;}public boolean sourceStopRequested(){return stop;}
        public BlockPos playerBlock(){return checkpoint;}public Vec3 playerPosition(){return new Vec3(0,71,-27);}
        public boolean pathExists(BlockPos p){assertEquals(HubTreeRoute.AREA_PROBE,p);return path;}
        public boolean startAnchorWalk(BlockPos p){walks.add(p);return walkAvailable;}
        public HubSourceLoop.Motion anchorMotion(){return motion;}public boolean requestHubWarp(){warps++;return warpAvailable;}
        public HubSourceLoop.Motion hubWarpMotion(){return warpMotion;}public void release(){releases++;}
        public TreeDiscovery.Tree treeAt(BlockPos p){return tree&&p.equals(checkpoint)?new TreeDiscovery.Tree(p,"oak",List.of(p,p.up(),p.up(2))):null;}
        public BlockPos standingPosition(BlockPos p){return p.add(1,0,0);}public double checkpointJitter(){return 0;}
        public boolean blacklisted(BlockPos p,long now){return false;}
    }
    @Test void alreadyInAreaSkipsWarpThenSelectsTree(){var p=new Port();var loop=new HubSourceLoop(p);loop.start();loop.tick(0);assertEquals(HubSourceLoop.Phase.SEARCH,loop.phase());assertTrue(loop.tick(1).isPresent());assertEquals(0,p.warps);}
    @Test void noTreeWaitsTenTicksThenRequestsExactlyOneWarp(){var p=new Port();p.tree=false;var loop=new HubSourceLoop(p);loop.start();loop.tick(0);loop.tick(0);for(int i=0;i<9;i++)loop.tick(0);assertEquals(0,p.warps);loop.tick(0);assertEquals(1,p.warps);for(int i=0;i<20;i++)loop.tick(0);assertEquals(1,p.warps);assertEquals(HubSourceLoop.Phase.WAIT_WARP,loop.phase());}
    @Test void requiresWarpReceiptThenWalksBothOrderedAnchors(){var p=new Port();p.path=false;var loop=new HubSourceLoop(p);loop.start();loop.tick(0);assertTrue(p.walks.isEmpty());loop.tick(0);assertTrue(p.walks.isEmpty());p.warpMotion=HubSourceLoop.Motion.ARRIVED;loop.tick(0);loop.tick(0);assertEquals(List.of(HubTreeRoute.ENTRY_ANCHORS.getFirst()),p.walks);p.motion=HubSourceLoop.Motion.ARRIVED;loop.tick(0);loop.tick(0);assertEquals(HubTreeRoute.ENTRY_ANCHORS,p.walks);assertEquals(HubSourceLoop.Phase.SEARCH,loop.phase());}
    @Test void rejectsWrongAreaWarpReceipt(){var p=new Port();p.inHub=false;var loop=new HubSourceLoop(p);loop.start();loop.tick(0);p.warpMotion=HubSourceLoop.Motion.ARRIVED;loop.tick(0);assertEquals(HubSourceLoop.Phase.BLOCKED,loop.phase());assertTrue(p.walks.isEmpty());}
    @Test void missingContractAndAnchorFailureRemainExplicit(){var p=new Port();p.path=false;p.warpAvailable=false;var loop=new HubSourceLoop(p);loop.start();loop.tick(0);assertEquals(HubSourceLoop.Phase.BLOCKED,loop.phase());assertEquals("Hub warp contract unavailable",loop.reason());p.warpAvailable=true;p.walkAvailable=false;loop.start();loop.tick(0);p.warpMotion=HubSourceLoop.Motion.ARRIVED;loop.tick(0);loop.tick(0);assertEquals(HubSourceLoop.Phase.BLOCKED,loop.phase());}
    @Test void pathFailureRetriesFourTimesThenRewarpsWithoutSelectingLaterTree(){var p=new Port();var loop=new HubSourceLoop(p);loop.start();loop.tick(0);for(int i=0;i<4;i++){assertTrue(loop.tick(0).isPresent());loop.treeWalkFailed();for(int t=0;t<20;t++)loop.tick(0);assertEquals(i+1,loop.walkRetries());assertEquals(0,p.warps);}assertTrue(loop.tick(0).isPresent());loop.treeWalkFailed();loop.tick(0);assertEquals(1,p.warps);}
    @Test void recordsPreviousOnlyOnIssuedWalkAndClearsRetriesAfterTree(){var p=new Port();var loop=new HubSourceLoop(p);loop.start();loop.tick(0);loop.tick(0);assertNull(loop.previousCheckpoint());loop.treeWalkStarted(p.checkpoint);assertEquals(p.checkpoint,loop.previousCheckpoint());loop.treeFinished();assertEquals(0,loop.walkRetries());assertTrue(loop.tick(0).isEmpty());}
    @Test void savedRestoreRechecksEntryAndRetainsTargetExclusion(){var p=new Port();var loop=new HubSourceLoop(p);loop.start();loop.tick(0);loop.tick(0);loop.treeWalkStarted(p.checkpoint);var saved=loop.save();loop.stop();assertTrue(loop.restore(saved));assertEquals(HubSourceLoop.Phase.ENTRY,loop.phase());loop.tick(0);assertTrue(loop.tick(0).isEmpty());assertEquals(p.checkpoint,loop.previousCheckpoint());}
    @Test void stopCancelsPendingRetryAndBlocksSavedRestore(){var p=new Port();var loop=new HubSourceLoop(p);loop.start();loop.tick(0);loop.tick(0);loop.treeWalkFailed();loop.stop();for(int i=0;i<50;i++)loop.tick(0);assertEquals(0,p.warps);p.stop=true;assertFalse(loop.restore(new HubSourceLoop.Saved(null,0)));}
    @Test void issuedArrivalCallbackConsumesParentOnceWithoutMovement(){var p=new Port();p.path=false;var loop=new HubSourceLoop(p);loop.start();loop.tick(0);assertTrue(loop.confirmedWarpArrival());assertFalse(loop.confirmedWarpArrival());assertEquals(HubSourceLoop.Phase.BEGIN_ANCHORS,loop.phase());assertTrue(p.walks.isEmpty());loop.tick(0);assertEquals(HubSourceLoop.Phase.WALK_ANCHORS,loop.phase());assertEquals(1,p.walks.size());}
    @Test void anchorFailurePreservesCachedDiagnosticsWithoutAdvancingOrReplanning(){
        var p=new Port(){public String anchorDiagnostics(){return "anchor=(-64,70,-30) start=(-19.5,71,-26.5) search=5000/50 edge obstruction";}};
        p.path=false;var loop=new HubSourceLoop(p);loop.start();loop.tick(0);p.warpMotion=HubSourceLoop.Motion.ARRIVED;loop.tick(0);loop.tick(0);
        p.motion=HubSourceLoop.Motion.ARRIVED;loop.tick(0);p.motion=HubSourceLoop.Motion.FAILED;loop.tick(0);
        assertEquals(HubSourceLoop.Phase.BLOCKED,loop.phase());assertEquals(1,loop.entryAnchorIndex());assertTrue(loop.reason().contains("edge obstruction"));
        int walks=p.walks.size(),warps=p.warps;loop.tick(1);assertEquals(walks,p.walks.size());assertEquals(warps,p.warps);
    }
    @Test void sourceStopReleasesEveryActivePhase(){var p=new Port();var loop=new HubSourceLoop(p);loop.start();p.stop=true;int before=p.releases;loop.tick(0);assertEquals(before+1,p.releases);assertEquals(HubSourceLoop.Phase.BLOCKED,loop.phase());}
}
