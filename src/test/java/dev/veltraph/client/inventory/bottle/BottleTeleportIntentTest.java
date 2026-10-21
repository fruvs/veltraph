package dev.veltraph.client.inventory.bottle;

import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
import static dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.*;

class BottleTeleportIntentTest {
    private final UnexpectedBehaviorMonitor monitor=new UnexpectedBehaviorMonitor(f->{});
    private final Context context=new Context(new Activation(BottleAcquisitionModule.ID,1,true),1,1,1);
    private final Value target=Value.position(.5,70,.5);
    private Finding event(Context ctx,Value position,long now){return monitor.observe(new UnexpectedBehaviorMonitor.Observation(ctx,Kind.POSITION,Value.position(20.5,70,.5),position,now));}
    @Test void fullSourceTimeoutRegistersAgainstActualUnchangedMonitor(){
        int originalActionTicks=1000;long lifetime=BottleTeleportIntent.forActionTimeoutTicks(originalActionTicks).millis();
        assertEquals(5000,lifetime);assertDoesNotThrow(()->monitor.intent(context,Kind.POSITION,target,.9,100,lifetime));assertEquals(1,monitor.pendingIntents());
    }
    @Test void original50000OperandReallyExceedsTheActualMonitorBound(){assertThrows(IllegalArgumentException.class,()->monitor.intent(context,Kind.POSITION,target,.9,100,1000*50L));assertEquals(0,monitor.pendingIntents());}
    @Test void shorterOriginalTimeoutKeepsItsActualAttributionExpiry(){long ttl=BottleTeleportIntent.forActionTimeoutTicks(30).millis();assertEquals(1500,ttl);monitor.intent(context,Kind.POSITION,target,.9,100,ttl);assertEquals(Classification.UNEXPECTED,event(context,target,1601).classification());}
    @Test void exactOneTargetOneReceiptWithinBoundNoRefresh(){monitor.intent(context,Kind.POSITION,target,.9,100,BottleTeleportIntent.forActionTimeoutTicks(1000).millis());assertEquals(Classification.EXPLAINED,event(context,target,5100).classification());assertEquals(0,monitor.pendingIntents());assertEquals(Classification.UNEXPECTED,event(context,target,5100).classification());}
    @Test void otherwiseCorrectLateIntentIsNotIndefinitelyRearmed(){monitor.intent(context,Kind.POSITION,target,.9,100,BottleTeleportIntent.forActionTimeoutTicks(1000).millis());assertEquals(Classification.UNEXPECTED,event(context,target,5101).classification());assertEquals(0,monitor.pendingIntents());}
    @Test void wrongLandingCannotConsumeTheExactIntent(){monitor.intent(context,Kind.POSITION,target,.9,100,BottleTeleportIntent.forActionTimeoutTicks(1000).millis());assertEquals(Classification.UNEXPECTED,event(context,Value.position(100,70,.5),101).classification());assertEquals(1,monitor.pendingIntents());assertEquals(Classification.EXPLAINED,event(context,target,102).classification());}
    @Test void intentCannotSurviveDifferentNativeWorld(){monitor.intent(context,Kind.POSITION,target,.9,100,BottleTeleportIntent.forActionTimeoutTicks(1000).millis());var changed=new Context(context.activation(),1,2,1);monitor.context(changed,101);assertEquals(0,monitor.pendingIntents());assertEquals(Classification.UNEXPECTED,event(changed,target,102).classification());}
    private static Profile fullProfile(){var p=BottleAcquisitionRuntimeTest.profile(1,1000);return new Profile(p.glass(),p.hub(),p.island(),p.path(),p.npcAnchor(),p.npcStand(),p.npcName(),p.npcType(),p.npcTitle(),p.option(),p.buyTitle(),p.offer(),p.reserveThreshold(),p.cycles(),p.maxStackCost(),p.maxTotalCost(),p.delayTicks(),1000,60000);}
    @Test void fiveSecondIntentDoesNotShortenOriginalFiftySecondNativeTravelPermit(){var p=new BottleAcquisitionRuntimeTest.Fake();var runtime=new BottleAcquisitionRuntime(p);assertTrue(runtime.start(fullProfile(),0));p.now=50;runtime.tick(1);var permit=runtime.pendingTravel().orElseThrow();assertEquals(50000,permit.expiresMillis()-permit.issuedMillis());assertEquals(5000,BottleTeleportIntent.forActionTimeoutTicks(runtime.profile().timeoutTicks()).millis());p.now=permit.issuedMillis()+6000;assertEquals(Progress.COMPLETE,runtime.pollTravel(p.read(),121));assertEquals(State.PATH,runtime.state());assertEquals(50000,permit.expiresMillis()-permit.issuedMillis());assertEquals(1,p.commands);assertEquals(0,p.interactions);}
    @Test void originalFiftySecondTravelDeadlineStillExpiresWithoutRenewal(){var p=new BottleAcquisitionRuntimeTest.Fake();var runtime=new BottleAcquisitionRuntime(p);assertTrue(runtime.start(fullProfile(),0));p.now=50;runtime.tick(1);var permit=runtime.pendingTravel().orElseThrow();p.now=permit.expiresMillis()+1;assertEquals(Progress.FAILED,runtime.pollTravel(p.read(),1002));assertEquals(State.FAILED,runtime.state());assertEquals(1,p.commands);}
    @Test void unboundOrInvalidTimeoutCannotMakeAnIntent(){assertThrows(IllegalArgumentException.class,()->BottleTeleportIntent.forActionTimeoutTicks(0));assertThrows(IllegalArgumentException.class,()->BottleTeleportIntent.forActionTimeoutTicks(2401));assertThrows(IllegalArgumentException.class,()->new BottleTeleportIntent.Lifetime(5001));}
}
