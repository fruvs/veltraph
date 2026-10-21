package dev.veltraph.client.garden.pests.tracker;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PestTrackerTraceTest {
    static final String OPTIONS="{\"type\":\"minecraft:enchant\"}";
    static final PestTrackerTrace.Settings SETTINGS=new PestTrackerTrace.Settings(List.of(new PestTrackerTrace.Particle("minecraft:enchant",OPTIONS)),11,1.25,40);
    final Object player=new Object(),world=new Object(),connection=new Object();final Vec3 origin=new Vec3(0,70,0);
    PestTrackerTrace flow(){var t=new PestTrackerTrace(SETTINGS);t.begin(player,world,connection,origin,10,0);return t;}
    GardenTrackerParticles.Receipt r(long serial,String kind,String options,Vec3 pos,long tick){return new GardenTrackerParticles.Receipt(serial,kind,options,pos,new Vec3(0,0,0),tick*50);}
    @Test void exactNativeScopeOptionsAndSerialGateWithoutCachePollution(){var t=flow();assertFalse(t.receive(player,new Object(),connection,origin,r(11,"minecraft:enchant",OPTIONS,origin,0),0));
        assertFalse(t.receive(player,world,connection,origin,r(10,"minecraft:enchant",OPTIONS,origin,0),0));
        assertFalse(t.receive(player,world,connection,origin,r(12,"minecraft:crit",OPTIONS,origin,0),0));
        assertFalse(t.receive(player,world,connection,origin,r(13,"minecraft:enchant","wrong",origin,0),0));t.tick(1);assertEquals(0,t.events());assertTrue(t.goal().isEmpty());}
    @Test void oneTickContinuationAndSourceTwoHalfForecast(){var t=flow();for(int i=0;i<5;i++)assertTrue(t.receive(player,world,connection,origin,r(11+i,"minecraft:enchant",OPTIONS,new Vec3(i,70,0),0),0));
        t.tick(0);assertEquals(0,t.events());t.tick(1);var goal=t.goal().orElseThrow();assertEquals(new Vec3(.5,70,0),goal.origin());assertEquals(new Vec3(1,0,0),goal.direction());
        assertEquals(new Vec3(5.125,70,0),goal.predicted());assertEquals(5,goal.events());}
    @Test void sourceForecastSnapshotsSixPointsBeforeOldestUniqueEviction(){var t=flow();for(int i=0;i<7;i++)assertTrue(t.receive(player,world,connection,origin,r(11+i,"minecraft:enchant",OPTIONS,new Vec3(i,70,0),0),0));
        t.tick(1);var goal=t.goal().orElseThrow();assertEquals(new Vec3(2,70,0),goal.origin());assertEquals(new Vec3(1,0,0),goal.direction());assertEquals(new Vec3(7.25,70,0),goal.predicted());assertEquals(7,goal.events());}
    @Test void aNewNativeRequestCannotInheritPriorParticlePrediction(){var t=flow();for(int i=0;i<5;i++)assertTrue(t.receive(player,world,connection,origin,r(11+i,"minecraft:enchant",OPTIONS,new Vec3(i,70,0),0),0));t.tick(1);assertTrue(t.goal().isPresent());
        t.begin(player,world,connection,new Vec3(10,70,0),20,2);assertTrue(t.goal().isEmpty());assertEquals(0,t.events());assertFalse(t.receive(player,world,connection,origin,r(15,"minecraft:enchant",OPTIONS,new Vec3(4,70,0),1),2));t.tick(3);assertTrue(t.goal().isEmpty());}
    @Test void sourceOriginThreeAndStepEightRejectForeignTrails(){var t=flow();assertFalse(t.receive(player,world,connection,origin,r(11,"minecraft:enchant",OPTIONS,new Vec3(3.01,70,0),0),0));
        assertTrue(t.receive(player,world,connection,origin,r(12,"minecraft:enchant",OPTIONS,new Vec3(3,70,0),0),0));
        assertFalse(t.receive(player,world,connection,origin,r(13,"minecraft:enchant",OPTIONS,new Vec3(11.01,70,0),0),0));assertTrue(t.receive(player,world,connection,origin,r(14,"minecraft:enchant",OPTIONS,new Vec3(11,70,0),0),0));t.tick(1);assertEquals(2,t.events());}
    @Test void duplicateParticlesNeverProduceInvalidDivisionOrDirection(){var t=flow();for(int i=0;i<14;i++)assertTrue(t.receive(player,world,connection,origin,r(11+i,"minecraft:enchant",OPTIONS,origin,0),0));
        t.tick(1);assertTrue(t.goal().isEmpty());assertEquals(14,t.events());}
    @Test void staleFarOrFutureReceiptDoesNotAuthorizeMovement(){var t=flow();assertFalse(t.receive(player,world,connection,origin,r(11,"minecraft:enchant",OPTIONS,origin,42),0));
        assertFalse(t.receive(player,world,connection,origin,r(12,"minecraft:enchant",OPTIONS,origin,0),41));
        assertFalse(t.receive(player,world,connection,new Vec3(50,70,0),r(13,"minecraft:enchant",OPTIONS,origin,0),0));t.tick(1);assertTrue(t.goal().isEmpty());}
}
