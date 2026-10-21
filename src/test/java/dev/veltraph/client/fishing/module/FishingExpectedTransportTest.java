package dev.veltraph.client.fishing.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.fishing.*;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FishingExpectedTransportTest {
    static final class Services implements FishingServices {
        int requests;boolean accepted;Vec3 observed;long now;
        public boolean available(){return true;}public boolean hasFishingRod(){return true;}
        public BobberFacts bobber(){return BobberFacts.none();}public void useRod(){}
        public boolean consumeExpectedTeleport(Vec3 p,long n){requests++;observed=p;now=n;return accepted;}
    }
    @Test void DisabledParentNeverAsksHelperToConsume(){var s=new Services();s.accepted=true;var m=new FishingModule(s,new MapConfigService(),new Random(0));assertFalse(m.consumeExpectedTeleport(new Vec3(1,2,3),100));assertEquals(0,s.requests);}
    @Test void EnabledParentForwardsExactPositionAndTimeWithoutInventingAcceptance(){var s=new Services();var m=new FishingModule(s,new MapConfigService(),new Random(0));m.setEnabled(true);var p=new Vec3(1.25,2.5,3.75);assertFalse(m.consumeExpectedTeleport(p,123));assertSame(p,s.observed);assertEquals(123,s.now);s.accepted=true;assertTrue(m.consumeExpectedTeleport(p,124));assertEquals(2,s.requests);}
    @Test void StoppingParentRevokesTheHelperReceiptRoute(){var s=new Services();s.accepted=true;var m=new FishingModule(s,new MapConfigService(),new Random(0));m.setEnabled(true);assertTrue(m.consumeExpectedTeleport(new Vec3(1,2,3),100));m.setEnabled(false);assertFalse(m.consumeExpectedTeleport(new Vec3(1,2,3),101));assertEquals(1,s.requests);}
    @Test void UnboundServiceDoesNotClaimAnUnsolicitedPositionPacket(){assertFalse(FishingServices.NONE.consumeExpectedTeleport(new Vec3(1,2,3),100));}
    @Test void ScheduledRestCannotConsumeAHelperTransportReceipt(){
        var s=new Services();s.accepted=true;var m=new FishingModule(s,new MapConfigService(),new Random(0));m.setEnabled(true);
        var hooks=new FishingScheduledHooks(){public boolean ready(){return true;}public boolean pause(Object ticket){return true;}public boolean rebind(Object ticket){return true;}public boolean resume(Object ticket){return true;}public void cancel(Object ticket){}};
        assertTrue(m.suspendForScheduledRest(new Object(),hooks));
        assertFalse(m.consumeExpectedTeleport(new Vec3(1,2,3),100));assertEquals(0,s.requests);
    }
}
