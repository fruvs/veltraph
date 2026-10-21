package dev.veltraph.client.scheduling;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RestReadinessTest {
    static RestReadiness facts(boolean boundaryCapable,boolean restOnly,boolean boundary,boolean priority,boolean contest,long remaining,long minimum,long since,long guard){return new RestReadiness(true,boundaryCapable,restOnly,boundary,priority,contest,remaining,minimum,since,guard);}
    @Test void boundaryOnlyAppliesToActualBoundaryCapableOwner(){assertFalse(facts(true,true,false,false,false,0,120,5000,1000).permits());assertTrue(facts(true,true,true,false,false,0,120,5000,1000).permits());assertTrue(facts(false,true,false,false,false,0,120,5000,1000).permits());}
    @Test void higherPriorityAndExplicitUnrecoveredGuardsNeverDefaultToReady(){assertFalse(facts(true,false,true,true,false,0,120,5000,1000).permits());assertFalse(facts(true,false,true,false,false,0,-1,5000,1000).permits());assertFalse(facts(true,false,true,false,false,0,120,5000,-1).permits());}
    @Test void contestAndRecentRewarpUseTheirDistinctSourceUnits(){assertFalse(facts(true,false,true,false,true,119,120,5000,1000).permits());assertTrue(facts(true,false,true,false,true,120,120,1000,1000).permits());assertFalse(facts(true,false,true,false,false,0,120,999,1000).permits());}
}
