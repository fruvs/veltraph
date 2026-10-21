package dev.veltraph.client.rewarp;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BafAutostartPolicyTest {
    @Test void emergencyAndSeparateTerminalHandlerPrecedeIntermediaries(){var all=EnumSet.allOf(BafAutostartPolicy.Trigger.class);
        assertEquals(BafAutostartPolicy.Trigger.HYDRA,BafAutostartPolicy.select(new BafAutostartPolicy.Observation(true,true,false,all)).orElseThrow());
        assertEquals(BafAutostartPolicy.Trigger.TIMER,BafAutostartPolicy.select(new BafAutostartPolicy.Observation(false,true,false,all)).orElseThrow());}
    @Test void autosellPrecedesFarmingAvoidanceVeto(){assertEquals(BafAutostartPolicy.Trigger.AUTOSELL,BafAutostartPolicy.select(new BafAutostartPolicy.Observation(false,false,true,Set.of(BafAutostartPolicy.Trigger.AUTOSELL,BafAutostartPolicy.Trigger.PEST_GATE))).orElseThrow());}
    @Test void avoidanceVetoStopsEveryLaterBranch(){var ready=EnumSet.allOf(BafAutostartPolicy.Trigger.class);ready.remove(BafAutostartPolicy.Trigger.AUTOSELL);assertTrue(BafAutostartPolicy.select(new BafAutostartPolicy.Observation(false,false,true,ready)).isEmpty());}
    @Test void readableOrderingIsPreserved(){var order=List.of(BafAutostartPolicy.Trigger.AUTOSELL,BafAutostartPolicy.Trigger.SPRAYONATOR,BafAutostartPolicy.Trigger.PEST_GATE,BafAutostartPolicy.Trigger.VISITOR_GATE,BafAutostartPolicy.Trigger.PEST_THRESHOLD,BafAutostartPolicy.Trigger.CONTEST_TRAPS,BafAutostartPolicy.Trigger.CONTEST_PEST,BafAutostartPolicy.Trigger.AGATHA,BafAutostartPolicy.Trigger.DYNAMICREST);
        for(int i=0;i<order.size();i++)assertEquals(order.get(i),BafAutostartPolicy.select(new BafAutostartPolicy.Observation(false,false,false,Set.copyOf(order.subList(i,order.size())))).orElseThrow());}
    @Test void noCandidatesMeansNoInventedActivation(){assertTrue(BafAutostartPolicy.select(new BafAutostartPolicy.Observation(false,false,false,Set.of())).isEmpty());}
}
