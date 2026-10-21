package dev.veltraph.client.fishing.trash;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FishingTrashWorkflowTest {
    private final FishingTrashProfile profile=new FishingTrashProfile(List.of(new FishingTrashProfile.Rule("minecraft:enchanted_book","Fixture junk")),0L,1000L,60000L,1000L);
    private static final class Port implements FishingTrashWorkflow.Port {
        boolean valid=true,hook=true,aim,opened=true,dropped,closed=true,returnStarted=true,restored;
        int selected,walk,back,reels,selects,drops,release;String failure="";final List<String> events=new ArrayList<>();
        public boolean valid(){return valid;}public boolean bobber(){return hook;}public void reel(){reels++;events.add("reel");}
        public int selectCliff(){selects++;return selected;}public int walkCliff(){return walk;}public boolean aimCliff(){return aim;}
        public boolean openInventory(){events.add("open");return opened;}public void discardTick(){events.add("dropTick");drops++;}
        public boolean discarded(){return dropped;}public String discardFailure(){return failure;}
        public boolean closeInventory(){events.add("close");return closed;}public boolean startReturn(){events.add("return");return returnStarted;}
        public int returnTick(){return back;}public boolean restoreAim(){events.add("restore");return restored;}public void release(){release++;events.add("release");}
    }
    @Test void sourceParentSequenceWaitsForRealReelScanPathDropAndPhysicalReturn() {
        var runtime=new FishingTrashWorkflow();var port=new Port();assertTrue(runtime.start(profile,0,port));
        runtime.tick(50,port);assertEquals(1,port.reels);assertEquals(0,port.selects);
        port.hook=false;runtime.tick(100,port);runtime.tick(150,port);assertEquals(FishingTrashWorkflow.State.SELECT_CLIFF,runtime.state());
        port.selected=1;runtime.tick(200,port);assertEquals(FishingTrashWorkflow.State.WALK_TO_CLIFF,runtime.state());
        runtime.tick(250,port);assertFalse(port.events.contains("open"));port.walk=1;runtime.tick(300,port);
        runtime.tick(350,port);assertFalse(port.events.contains("open"));port.aim=true;runtime.tick(400,port);runtime.tick(450,port);
        runtime.tick(500,port);assertFalse(port.events.contains("close"));port.dropped=true;runtime.tick(550,port);runtime.tick(600,port);
        assertEquals(FishingTrashWorkflow.State.RETURN,runtime.state());assertEquals(List.of("reel","open","dropTick","dropTick","close","return"),port.events);
        runtime.tick(650,port);assertEquals(FishingTrashWorkflow.State.RETURN,runtime.state());port.back=1;runtime.tick(700,port);
        runtime.tick(750,port);assertEquals(FishingTrashWorkflow.State.RESTORE_AIM,runtime.state());port.restored=true;runtime.tick(800,port);
        assertEquals(FishingTrashWorkflow.State.COMPLETE,runtime.state());assertEquals(1,port.release);
    }
    @Test void failedDropReceiptContextAndUnreachableCliffDoNotReturnOrResume() {
        var runtime=new FishingTrashWorkflow();var port=new Port();port.hook=false;port.selected=-1;runtime.start(profile,0,port);runtime.tick(10,port);
        assertEquals(FishingTrashWorkflow.State.FAILED,runtime.state());assertEquals(0,port.drops);assertEquals(1,port.release);
        var broken=new FishingTrashWorkflow();var other=new Port();broken.start(profile,0,other);other.valid=false;broken.tick(10,other);
        assertEquals(FishingTrashWorkflow.State.FAILED,broken.state());assertEquals(1,other.reels);assertEquals(1,other.release);assertFalse(other.events.contains("return"));
    }
    @Test void initialReelAndDeclaredStageTimeoutReleaseWithoutRepeats() {
        var runtime=new FishingTrashWorkflow();var port=new Port();runtime.start(profile,0,port);runtime.tick(1001,port);runtime.tick(2000,port);
        assertEquals(FishingTrashWorkflow.State.FAILED,runtime.state());assertEquals(1,port.reels);assertEquals(1,port.release);
        var timed=new FishingTrashWorkflow();var other=new Port();other.hook=false;timed.start(profile,0,other);timed.tick(60001,other);
        assertEquals(FishingTrashWorkflow.State.FAILED,timed.state());assertEquals(0,other.selects);assertEquals(1,other.release);
    }
}
