package dev.veltraph.client.combat.slayer;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class EnvironmentalHazardEscapeTest {
    private final Vec3 player=new Vec3(.5,70,.5), hazard=new Vec3(.5,70,1.5);
    @Test void physicalThreatEscapesAndRetainsFixedSourceWhenPlayerAndBossMove(){
        var h=new EnvironmentalHazardEscape();assertTrue(h.observe(true,player,List.of(hazard),p->true,0).active());
        var d=h.observe(true,new Vec3(8,70,8),List.of(hazard),p->true,1000);
        assertTrue(d.active());assertEquals(hazard,d.source());
    }
    @Test void confirmedRemovalRequiresExistingQuietPolicyBeforeResume(){
        var h=new EnvironmentalHazardEscape();h.observe(true,player,List.of(hazard),p->true,100);
        assertTrue(h.observe(true,player,List.of(),p->true,101).active());
        assertTrue(h.observe(true,player,List.of(),p->true,3099).active());
        assertFalse(h.observe(true,player,List.of(),p->true,3100).active());
    }
    @Test void unloadedAbsenceCannotAcknowledgeRemoval(){
        var h=new EnvironmentalHazardEscape();h.observe(true,player,List.of(hazard),p->true,0);
        assertTrue(h.observe(true,new Vec3(20,70,20),List.of(),p->false,10000).active());
        assertEquals(java.util.Set.of(hazard),h.footprint());
    }
    @Test void distantAndVerticallySeparatedHazardsDoNotTrigger(){
        var h=new EnvironmentalHazardEscape();assertFalse(h.observe(true,player,List.of(new Vec3(4,70,4),new Vec3(.5,75,.5)),p->true,0).active());
    }
    @Test void eligibilityAndResetInvalidateOldEncounter(){
        var h=new EnvironmentalHazardEscape();h.observe(true,player,List.of(hazard),p->true,0);
        assertFalse(h.observe(false,player,List.of(hazard),p->true,1).active());
        assertFalse(h.observe(true,player,List.of(),p->true,2).active());
        h.observe(true,player,List.of(hazard),p->true,3);h.reset();assertTrue(h.footprint().isEmpty());
    }
    @Test void newNearbyThreatExtendsHoldWithoutResumingBetweenThreats(){
        var h=new EnvironmentalHazardEscape();h.observe(true,player,List.of(hazard),p->true,0);
        var next=new Vec3(-1,70,.5);assertEquals(next,h.observe(true,player,List.of(next),p->true,2500).source());
        assertTrue(h.observe(true,player,List.of(),p->true,4000).active());
        assertFalse(h.observe(true,player,List.of(),p->true,5500).active());
    }
    @Test void denseGroundPatchPermitsOutwardStepButRejectsInwardAndEntry(){
        var patch=new java.util.ArrayList<Vec3>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)patch.add(new Vec3(x,70,z));
        var center=new Vec3(0,70,0);assertTrue(EnvironmentalHazardEscape.permitsAdvance(center,new Vec3(.8,70,0),patch));
        assertFalse(EnvironmentalHazardEscape.permitsAdvance(new Vec3(2,70,0),new Vec3(1.2,70,0),patch));
        assertFalse(EnvironmentalHazardEscape.permitsAdvance(new Vec3(4.1,70,0),new Vec3(3.5,70,0),patch));
        assertTrue(EnvironmentalHazardEscape.permitsAdvance(new Vec3(4.1,70,0),new Vec3(5,70,0),patch));
    }
    @Test void momentumProjectionMustAlsoReduceExposure(){
        assertTrue(EnvironmentalHazardEscape.permitsAdvance(player,new Vec3(.5,70,-.3),List.of(hazard)));
        assertFalse(EnvironmentalHazardEscape.permitsAdvance(player,new Vec3(.5,70,.9),List.of(hazard)));
        assertFalse(EnvironmentalHazardEscape.permitsAdvance(player,player,List.of(hazard)));
    }
}
