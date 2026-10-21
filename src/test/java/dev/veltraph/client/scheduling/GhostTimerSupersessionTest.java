package dev.veltraph.client.scheduling;
import dev.veltraph.client.combat.ghost.*;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostTimerSupersessionTest {
    final Object owner=new Object(),pin=new Object(),player=new Object(),world=new Object(),connection=new Object(),activation=new Object();final UUID uuid=UUID.randomUUID();
    final GhostPreflightProfile profile=new GhostPreflightProfile("private_warp","Denied",new Vec3(.5,149,-68.5),10000,false,0,null);
    GhostTransportLoop.Attempt attempt(int ordinal,long issued){return new GhostTransportLoop.Attempt(activation,ordinal,issued,10100);}
    GhostWarpPermit permit(GhostTransportLoop.Attempt a,long serial){return new GhostWarpPermit(player,world,connection,uuid,profile,"minecraft:overworld",serial,a.issuedAt(),a.deadline(),a);}
    boolean grant(GhostTimerContinuation t,GhostWarpPermit p,GhostTransportLoop.Attempt a,long now){return t.grant(owner,pin,p,a,connection,uuid,true,true,now);}
    boolean consume(GhostTimerContinuation t,GhostWarpPermit p,GhostTransportLoop.Attempt a,long now){return t.consume(owner,pin,p,a,connection,uuid,true,true,now);}
    @Test void validNewAttemptPreservesOriginalTimerInstanceCountAndDeadline(){var a=attempt(1,100);var p=permit(a,4);var t=new GhostTimerContinuation(owner,pin,p);var timer=new SourceStopTimer();timer.arm(1,ScheduleTimeUnit.SECONDS);assertTrue(grant(t,p,a,100));assertTrue(consume(t,p,a,100));assertFalse(timer.tick());var b=attempt(2,250);var q=permit(b,5);assertTrue(grant(t,q,b,250));assertTrue(consume(t,q,b,250));assertFalse(timer.tick());assertEquals(18,timer.remainingTicks());assertFalse(t.owns(owner,pin,p));assertTrue(t.owns(owner,pin,q));assertEquals(p.expiresAt(),q.expiresAt());}
    @Test void failedDispatchRetiresAllOldReceiptAuthorityButTimerKeepsCounting(){var a=attempt(1,100);var p=permit(a,4);var t=new GhostTimerContinuation(owner,pin,p);var b=attempt(2,250);assertTrue(grant(t,null,b,250));assertTrue(consume(t,null,b,250));assertFalse(t.owns(owner,pin,p));assertFalse(t.owns(owner,pin,null));var c=attempt(3,400);var q=permit(c,6);assertTrue(grant(t,q,c,400));assertTrue(consume(t,q,c,400));assertTrue(t.owns(owner,pin,q));}
    @Test void staleAttemptCannotRenewOrCommitAfterSupersession(){var a=attempt(1,100);var p=permit(a,4);var t=new GhostTimerContinuation(owner,pin,p);var b=attempt(2,250);var q=permit(b,5);assertTrue(grant(t,q,b,250));assertTrue(consume(t,q,b,250));assertFalse(grant(t,p,a,300));assertFalse(t.owns(owner,pin,q));}
    @Test void anotherActivationOrRenewedDeadlineIsNeverSupersession(){var a=attempt(1,100);var p=permit(a,4);var t=new GhostTimerContinuation(owner,pin,p);var different=new GhostTransportLoop.Attempt(new Object(),2,250,10100);assertFalse(grant(t,null,different,250));var u=new GhostTimerContinuation(owner,pin,p);assertFalse(grant(u,null,new GhostTransportLoop.Attempt(activation,2,250,10200),250));}
    @Test void profileNativeContextAndSerialFloorRemainExact(){var a=attempt(1,100);var p=permit(a,4);var b=attempt(2,250);var t=new GhostTimerContinuation(owner,pin,p);assertFalse(grant(t,permit(b,3),b,250));var otherWorld=new GhostWarpPermit(player,new Object(),connection,uuid,profile,"minecraft:overworld",5,250,10100,b);assertFalse(grant(new GhostTimerContinuation(owner,pin,p),otherWorld,b,250));}
    @Test void attemptChangeAfterGrantCannotBeForgivenByConsume(){var a=attempt(1,100);var p=permit(a,4);var t=new GhostTimerContinuation(owner,pin,p);assertTrue(grant(t,p,a,100));var b=attempt(2,250);assertFalse(consume(t,permit(b,5),b,250));assertFalse(grant(t,permit(b,5),b,251));}
}
