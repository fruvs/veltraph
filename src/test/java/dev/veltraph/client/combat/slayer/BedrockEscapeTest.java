package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockEscapeTest {
    private final Vec3 start=new Vec3(.5,70,.5),boss=new Vec3(2.5,70,.5),west=new Vec3(-8.5,70,.5),east=new Vec3(9.5,70,.5);
    private final Set<BlockPos> patch=Set.of(new BlockPos(0,69,0),new BlockPos(1,69,0));
    @Test void scopedFloorContactAndEightBlockDestinationAreRequired() {
        var e=new BedrockEscape();
        assertFalse(e.observe(false,start,boss,patch,List.of(west),p->true,0).active());
        assertFalse(e.observe(true,new Vec3(5,70,0),boss,patch,List.of(west),p->true,0).active());
        var d=e.observe(true,start,boss,patch,List.of(new Vec3(-6,70,0),west,east),p->true,0);
        assertTrue(d.active());assertEquals(west,d.objective());assertTrue(d.objective().horizontalDistanceTo(start)>=8);
    }
    @Test void fixedOriginAndObjectiveDoNotFollowChasingBoss() {
        var e=new BedrockEscape();e.observe(true,start,boss,patch,List.of(west,east),p->true,0);
        var d=e.observe(true,west,new Vec3(-10,70,0),patch,List.of(west,east),p->true,1000);
        assertTrue(d.active());assertEquals(start,e.origin());assertEquals(west,d.objective());
    }
    @Test void floorRemovalAcknowledgesCompletionOnlyAfterQuietInterval() {
        var e=new BedrockEscape();e.observe(true,start,boss,patch,List.of(west),p->true,100);
        assertTrue(e.observe(true,west,boss,Set.of(),List.of(west),p->true,500).active());
        assertFalse(e.observe(true,west,boss,Set.of(),List.of(west),p->true,850).active());
        assertNull(e.origin());
    }
    @Test void unsafeOrObstructedDestinationsCannotAuthorizeMovement() {
        var e=new BedrockEscape();
        var d=e.observe(true,start,boss,patch,List.of(west,east),p->false,0);
        assertTrue(d.active());assertNull(d.objective());assertTrue(d.detail().contains("hold attacks"));
        assertEquals(east,e.observe(true,start,boss,patch,List.of(west,east),east::equals,50).objective());
        assertEquals(west,e.observe(true,start,boss,patch,List.of(west,east),west::equals,100).objective());
    }
    @Test void unrelatedFarBedrockDoesNotPreventCompletion() {
        var e=new BedrockEscape();e.observe(true,start,boss,patch,List.of(west),p->true,0);
        var distant=Set.of(new BlockPos(12,69,0));
        assertFalse(e.observe(true,west,boss,distant,List.of(west),p->true,1000).active());
    }
    @Test void resetAndNewCastHaveIndependentOrigins() {
        var e=new BedrockEscape();e.observe(true,start,boss,patch,List.of(west),p->true,0);e.reset();
        var other=new Vec3(20.5,70,.5);
        assertTrue(e.observe(true,other,boss,Set.of(other.toBlockPos().down()),List.of(new Vec3(11.5,70,.5)),p->true,50).active());
        assertEquals(other,e.origin());
    }
    @Test void onlyCryptBossNamesAuthorizeTheAdapter() {
        assertTrue(SlayerSignals.cryptBoss("§cPrimordial Broodfather V"));assertTrue(SlayerSignals.cryptBoss("Conjoined Broodmother"));
        assertFalse(SlayerSignals.cryptBoss("Tarantula Broodfather"));assertFalse(SlayerSignals.cryptBoss("Riftstalker Bloodfiend"));
    }
}
