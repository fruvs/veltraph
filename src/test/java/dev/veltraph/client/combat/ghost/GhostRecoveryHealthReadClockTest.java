package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.heal.*;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual cache and registered module read-boundary seam; no native branch/runtime claim. */
class GhostRecoveryHealthReadClockTest {
    private static final String LOW="§c1,401/1,434\uE010     §a791§a\uE008 Defense     §b1,290/1,290\uE003 Mana     ";
    private static final class Fixture {
        final Object player=new Object(),world=new Object(),connection=new Object();
        final CapturedHudVitals.Scope scope=CapturedHudVitals.Scope.of(player,world,connection,1);
        final CapturedHudVitals vitals=new CapturedHudVitals();
        Fixture(long receivedAt){vitals.bind(scope);assertTrue(vitals.receive(scope,CapturedHudVitals.parse(LOW),1,receivedAt,true));}
        Optional<HealthObservation> read(long now){return vitals.read(scope,now,true).map(HealthObservation::captured);}
    }
    @Test void earlierTickTimestampAfterLaterHpReadInvalidatesOtherwiseFreshCapturedFrame(){
        var f=new Fixture(1000);long tickNow=1000;
        assertTrue(f.read(1001).isPresent(),"hp() samples later actual time and advances the real cache lastRead");
        assertTrue(f.read(tickNow).isEmpty(),"Original begin(now) replays the earlier argument and correctly trips rollback guard");
        assertTrue(f.read(1002).isEmpty(),"An invalidated receipt is not revived or retried");
    }
    @Test void registeredBoundarySamplesActualClockOnceAfterHpReadAndPreservesRawReceipt(){
        var f=new Fixture(1000);assertTrue(f.read(1001).isPresent());
        var clockCalls=new AtomicInteger();var providerCalls=new AtomicInteger();var providerTime=new AtomicLong();
        var observed=GhostGrindModule.readRecoveryHealthAtCurrentClock(()->{clockCalls.incrementAndGet();return 1020;},at->{providerCalls.incrementAndGet();providerTime.set(at);return f.read(at);}).orElseThrow();
        assertEquals(1,clockCalls.get());assertEquals(1,providerCalls.get());assertEquals(1020,providerTime.get());
        assertEquals(HealthObservation.Format.CAPTURED_HUD,observed.format());assertEquals(1401,observed.health());assertEquals(1434,observed.maximum());assertEquals(1,observed.serial());assertEquals(1000,observed.receivedAt());assertTrue(observed.scope().same(f.scope));
        assertTrue(f.read(1021).isPresent(),"No reset or clamping changed the live cache");
    }
    @Test void freshReadTimeDoesNotExtendOriginalRecoveryStartDeadline(){
        var f=new Fixture(1000);f.read(1001);
        var observed=GhostGrindModule.readRecoveryHealthAtCurrentClock(()->1020,f::read).orElseThrow();
        var recovery=new GhostHealReturn();var home=new Vec3(.5,70,.5);
        HealHealthObservationBasis.begin(recovery,home,-90,-13,0,observed,1000);
        assertEquals(GhostHealReturn.Action.FAILED,recovery.tick(home,-90,-13,1401,0,true,true,0,5001,300,4000),"FSM retains original tick1000, not provider read1020");
    }
    @Test void actualClockRollbackAtBoundaryRemainsUnknownWithoutClampOrRetry(){
        var f=new Fixture(1000);assertTrue(f.read(1001).isPresent());var reads=new AtomicInteger();
        var observed=GhostGrindModule.readRecoveryHealthAtCurrentClock(()->999,at->{reads.incrementAndGet();return f.read(at);});
        assertTrue(observed.isEmpty());assertEquals(1,reads.get());assertTrue(f.read(1002).isEmpty());
    }
    @Test void futureStaleForeignScopeAndDeadNativeInputsStillReject(){
        var future=new Fixture(1000);assertTrue(GhostGrindModule.readRecoveryHealthAtCurrentClock(()->999,future::read).isEmpty());
        var stale=new Fixture(1000);assertTrue(GhostGrindModule.readRecoveryHealthAtCurrentClock(()->3001,stale::read).isEmpty());
        var foreign=new Fixture(1000);var other=CapturedHudVitals.Scope.of(new Object(),foreign.world,foreign.connection,1);
        assertTrue(GhostGrindModule.readRecoveryHealthAtCurrentClock(()->1001,at->foreign.vitals.read(other,at,true).map(HealthObservation::captured)).isEmpty());
        var dead=new Fixture(1000);assertTrue(GhostGrindModule.readRecoveryHealthAtCurrentClock(()->1001,at->dead.vitals.read(dead.scope,at,false).map(HealthObservation::captured)).isEmpty());
    }
}
