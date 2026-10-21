package dev.veltraph.client.rewarp;
import dev.veltraph.client.combat.ghost.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostRootAdmissionTest {
    final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object();final UUID uuid=UUID.randomUUID();
    final Vec3 anchor=new Vec3(8.5,70,.5);final GhostPreflightProfile profile=new GhostPreflightProfile("private_warp","Denied",anchor,2500,false,0,null);
    final GhostWarpPermit permit=new GhostWarpPermit(player,world,connection,uuid,profile,"minecraft:overworld",2,100,2600);
    InstalledGhostWarpLifecycle flow(){var f=new InstalledGhostWarpLifecycle();assertTrue(f.capture(owner,owner,1,true,permit,player,world,connection,uuid,GameArea.GARDEN,100));return f;}
    InstalledGhostWarpLifecycle.Observation receipt(boolean higherPriority,long serial,long now){return new InstalledGhostWarpLifecycle.Observation(player,world,connection,uuid,true,true,true,true,false,GameArea.DWARVEN_MINES,"minecraft:overworld",anchor,serial,anchor,higherPriority,now);}
    @Test void actualReceivedFrame59WaitsUntilNextGroundedFrame60(){var f=flow();var received=f.evaluate(owner,owner,1,true,permit,receipt(false,3,200));assertEquals(InstalledGhostWarpLifecycle.Decision.COMMIT,received);assertEquals(InstalledGhostWarpLifecycle.Decision.WAIT,GhostRootAdmission.withParentFooting(received,false,0));assertEquals(InstalledGhostWarpLifecycle.Decision.COMMIT,GhostRootAdmission.withParentFooting(f.evaluate(owner,owner,1,true,permit,receipt(false,3,250)),true,0));}
    @Test void receivedMovingBodyWaitsAtExactExistingParentThreshold(){var f=flow();var received=f.evaluate(owner,owner,1,true,permit,receipt(false,3,200));assertEquals(InstalledGhostWarpLifecycle.Decision.WAIT,GhostRootAdmission.withParentFooting(received,true,.02000001));assertEquals(InstalledGhostWarpLifecycle.Decision.COMMIT,GhostRootAdmission.withParentFooting(received,true,.02));}
    @Test void absentFreshReceiptNeverBecomesCommitBecauseGrounded(){var f=flow();assertEquals(InstalledGhostWarpLifecycle.Decision.WAIT,GhostRootAdmission.withParentFooting(f.evaluate(owner,owner,1,true,permit,receipt(false,2,200)),true,0));}
    @Test void higherPriorityStopCannotBeConvertedToFootingWait(){var f=flow();assertEquals(InstalledGhostWarpLifecycle.Decision.STOP,GhostRootAdmission.withParentFooting(f.evaluate(owner,owner,1,true,permit,receipt(true,3,200)),false,0));}
    @Test void originalDeadlineAndDisabledOwnerRemainTerminalWhileWaiting(){var f=flow();assertEquals(InstalledGhostWarpLifecycle.Decision.STOP,GhostRootAdmission.withParentFooting(f.evaluate(owner,owner,1,true,permit,receipt(false,3,2601)),false,0));assertEquals(InstalledGhostWarpLifecycle.Decision.STOP,GhostRootAdmission.withParentFooting(f.evaluate(owner,owner,1,false,permit,receipt(false,3,200)),false,0));}
    @Test void timerContinuesAcrossUngroundedWaitWithoutNewArm(){var timer=new dev.veltraph.client.scheduling.SourceStopTimer();timer.arm(1,dev.veltraph.client.scheduling.ScheduleTimeUnit.SECONDS);var f=flow();assertEquals(InstalledGhostWarpLifecycle.Decision.WAIT,GhostRootAdmission.withParentFooting(f.evaluate(owner,owner,1,true,permit,receipt(false,3,200)),false,0));assertFalse(timer.tick());assertEquals(19,timer.remainingTicks());assertFalse(timer.tick());assertEquals(18,timer.remainingTicks());}
}
