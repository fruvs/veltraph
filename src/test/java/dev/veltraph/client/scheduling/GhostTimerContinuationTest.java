package dev.veltraph.client.scheduling;
import dev.veltraph.client.combat.ghost.*;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostTimerContinuationTest {
    final Object owner=new Object(),pin=new Object(),player=new Object(),world=new Object(),connection=new Object();
    final UUID uuid=UUID.randomUUID();
    final GhostPreflightProfile profile=new GhostPreflightProfile("private_warp","Denied",new Vec3(.5,149,-68.5),10000,false,0,null);
    final GhostWarpPermit permit=new GhostWarpPermit(player,world,connection,uuid,profile,"minecraft:overworld",4,100,10100);
    GhostTimerContinuation ticket(){return new GhostTimerContinuation(owner,pin,permit);}
    boolean grant(GhostTimerContinuation t,long now){return t.grant(owner,pin,permit,connection,uuid,true,true,now);}
    boolean consume(GhostTimerContinuation t,long now){return t.consume(owner,pin,permit,connection,uuid,true,true,now);}
    @Test void sameOriginalTimerCountsEveryAuthorizedTransitUpdateAndExpiresOnce(){
        var timer=new SourceStopTimer();timer.arm(1,ScheduleTimeUnit.SECONDS);var t=ticket();
        for(int i=0;i<20;i++){assertTrue(grant(t,100+i));assertTrue(consume(t,100+i));assertEquals(i==19,timer.tick());assertEquals(i==19?-1:19-i,timer.remainingTicks());}
        assertEquals(SourceStopTimer.State.EXPIRED,timer.state());assertFalse(timer.tick());
    }
    @Test void everyUpdateRequiresOneFreshRootGrant(){var t=ticket();assertTrue(grant(t,100));assertTrue(consume(t,100));assertFalse(consume(t,100));assertFalse(grant(t,101));}
    @Test void exactOriginalTimerPinAndPermitCannotBeSubstituted(){var t=ticket();assertFalse(t.grant(owner,new Object(),permit,connection,uuid,true,true,100));assertFalse(grant(t,101));var other=ticket();var clone=new GhostWarpPermit(player,world,connection,uuid,profile,"minecraft:overworld",4,100,10100);assertFalse(other.grant(owner,pin,clone,connection,uuid,true,true,100));}
    @Test void changedCanonicalUuidOrConnectionIrrevocablyRevokes(){var t=ticket();assertFalse(t.grant(owner,pin,permit,new Object(),uuid,true,true,100));assertFalse(grant(t,101));assertFalse(ticket().grant(owner,pin,permit,connection,UUID.randomUUID(),true,true,100));}
    @Test void stoppedOwnerOrMetadataDriftCannotResumeOldTimerAuthority(){var t=ticket();assertTrue(grant(t,100));assertFalse(t.consume(owner,pin,permit,connection,uuid,false,true,100));assertFalse(grant(t,101));assertFalse(ticket().grant(owner,pin,permit,connection,uuid,true,false,100));}
    @Test void unchangedAbsolutePermitDeadlineNeverRearms(){var t=ticket();assertTrue(grant(t,10100));assertTrue(consume(t,10100));assertFalse(grant(t,10101));assertFalse(grant(t,200));}
    @Test void matchedLoadingMayHaveNoPlayerButConnectionRemainsExact(){var t=ticket();assertTrue(t.grant(owner,pin,permit,connection,null,true,true,100));assertTrue(t.consume(owner,pin,permit,connection,null,true,true,100));assertTrue(grant(t,101));assertTrue(consume(t,101));}
    @Test void explicitCancellationAndForeignOwnerRemainTerminal(){var t=ticket();assertTrue(grant(t,100));t.revoke();assertFalse(consume(t,100));assertFalse(grant(t,101));assertFalse(ticket().grant(new Object(),pin,permit,connection,uuid,true,true,100));}
}
