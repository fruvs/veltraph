package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlayerEncounterHazardPolicyTest {
    private final Vec3 player=new Vec3(.5,70,.5), hazard=new Vec3(.5,70,1.5);
    @Test void publishedPrimordialBroodNameRetainsSpiderAndCryptEscapeClassification(){
        assertEquals(SlayerSignals.Family.SPIDER,SlayerSignals.family("§cPrimordial Brood 10M❤"));
        assertTrue(SlayerSignals.cryptBoss("§cPrimordial Brood 10M❤"));
        assertEquals(SlayerSignals.Family.SPIDER,SlayerSignals.family("Primordial Broodfather V"));
        assertTrue(SlayerSignals.cryptBoss("Conjoined Broodmother"));
        assertFalse(SlayerSignals.cryptBoss("Tarantula Broodfather V"));
    }
    @Test void physicalHazardsHoldKnownEncountersThroughAcknowledgedRemoval(){
        for(String label:List.of("Atoned Horror","Tarantula Broodfather","Sven Packmaster","Voidgloom Seraph","Inferno Demonlord","Riftstalker Bloodfiend","Primordial Brood")){
            var escape=new EnvironmentalHazardEscape();
            var family=SlayerSignals.family(label);
            assertNotEquals(SlayerSignals.Family.OTHER,family);
            assertTrue(escape.observe(family!=SlayerSignals.Family.OTHER,player,List.of(hazard),p->true,0,"Slayer environmental").active());
            assertTrue(escape.observe(true,new Vec3(8,70,.5),List.of(hazard),p->true,1000,"Slayer environmental").active());
            assertTrue(escape.observe(true,player,List.of(),p->true,3999,"Slayer environmental").active());
            assertFalse(escape.observe(true,player,List.of(),p->true,4000,"Slayer environmental").active());
        }
    }
    @Test void unseenRemovalAndSecondThreatCannotReleaseEnvironmentalHold(){
        var escape=new EnvironmentalHazardEscape();
        escape.observe(true,player,List.of(hazard),p->true,0,"Slayer environmental");
        assertTrue(escape.observe(true,new Vec3(20,70,0),List.of(),p->false,10000,"Slayer environmental").active());
        var second=new Vec3(.5,70,-1);
        assertEquals(second,escape.observe(true,player,List.of(second),p->true,10001,"Slayer environmental").source());
        assertTrue(escape.observe(true,player,List.of(),p->true,13000,"Slayer environmental").active());
        assertFalse(escape.observe(true,player,List.of(),p->true,13001,"Slayer environmental").active());
    }
    @Test void unknownFamilyCannotAcquirePhysicalEncounterAuthority(){
        var escape=new EnvironmentalHazardEscape();
        assertFalse(escape.observe(SlayerSignals.family("Nearby Player")!=SlayerSignals.Family.OTHER,player,List.of(hazard),p->true,0,"Slayer environmental").active());
        assertTrue(escape.footprint().isEmpty());
    }
    @Test void maniaSafeFloorCannotChooseAPhysicalImpactZone(){
        assertFalse(EnvironmentalHazardEscape.clearOfHazards(player,List.of(hazard)));
        assertTrue(EnvironmentalHazardEscape.clearOfHazards(new Vec3(8,70,8),List.of(hazard)));
        assertTrue(EnvironmentalHazardEscape.clearOfHazards(player,List.of(new Vec3(.5,75,.5))));
    }
    @Test void legacyRevenantDetailsRemainExact(){
        var escape=new EnvironmentalHazardEscape();
        assertEquals("Revenant physical hazard: escape and await observed removal",escape.observe(true,player,List.of(hazard),p->true,0).detail());
        assertEquals("Revenant physical hazard: removal quiet hold",escape.observe(true,player,List.of(),p->true,1).detail());
    }
}
