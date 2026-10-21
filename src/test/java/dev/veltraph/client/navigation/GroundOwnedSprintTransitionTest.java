package dev.veltraph.client.navigation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundOwnedSprintTransitionTest {
    static List<GroundCapturedEffects.Effect> effects(){return List.of(
        new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));}
    static GroundNativePhysicsProfile.State state(double base,boolean sprint){double total=base*(sprint?1+(double).3F:1);
        return new GroundNativePhysicsProfile.State(base,total,(float)total,sprint?List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",(double).3F,2)):List.of(),
            (double).42F,.08,1,1,5,effects(),0,1F);}
    @Test void nativeStartUsesPriorFieldThenNormalizes(){for(double base:new double[]{(double).36F,(double).4F}){
        var old=state(base,false);var next=state(base,true);
        assertTrue(GroundOwnedSprintTransition.profileEvolution(old,next,old.travelSpeed(),1,true));
        assertFalse(GroundOwnedSprintTransition.profileEvolution(old,next,next.travelSpeed(),1,true));}}
    @Test void nativeStopUsesPriorSprintField(){var old=state((double).4F,true);var next=state((double).4F,false);
        assertTrue(GroundOwnedSprintTransition.profileEvolution(old,next,old.travelSpeed(),1,false));
        assertFalse(GroundOwnedSprintTransition.profileEvolution(old,next,next.travelSpeed(),1,false));}
    @Test void noNativeCallCannotInventSprintTransition(){var a=state((double).36F,false);var b=state((double).36F,true);
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,b,a.travelSpeed(),0,true));}
    @Test void normalOwnedRefreshCanPreserveProfile(){var a=state((double).4F,true);
        assertTrue(GroundOwnedSprintTransition.profileEvolution(a,a,a.travelSpeed(),0,false));}
    @Test void mismatchedNativeArgumentOrMultipleCallsDenied(){var a=state((double).4F,false);var b=state((double).4F,true);
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,b,a.travelSpeed(),1,false));
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,b,a.travelSpeed(),2,true));
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,b,a.travelSpeed(),-1,true));}
    @Test void baseSwitchBetweenCapturedFamiliesDenied(){var a=state((double).36F,false);var b=state((double).4F,true);
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,b,a.travelSpeed(),1,true));}
    @Test void ordinaryScalarFamilyGetsNoTransitionExemption(){var a=state(.1,false);var b=state(.1,true);
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,b,a.travelSpeed(),1,true));}
    @Test void incompleteEffectsAndChangedFlagsDenied(){var a=state((double).36F,false);var b=state((double).36F,true);
        var partial=new GroundNativePhysicsProfile.State(b.baseSpeed(),b.effectiveSpeed(),b.travelSpeed(),b.movementModifiers(),b.jumpStrength(),.08,1,1,5,List.of(effects().getLast()),0,1F);
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,partial,a.travelSpeed(),1,true));
        var changed=new java.util.ArrayList<>(effects());changed.set(0,new GroundCapturedEffects.Effect("minecraft:haste",0,true,true,true));
        var flagged=new GroundNativePhysicsProfile.State(b.baseSpeed(),b.effectiveSpeed(),b.travelSpeed(),b.movementModifiers(),b.jumpStrength(),.08,1,1,5,changed,0,1F);
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,flagged,a.travelSpeed(),1,true));}
    @Test void lagIsNeverGeneralProfilePermission(){var a=state((double).4F,false);var b=state((double).4F,true);
        var lag=new GroundNativePhysicsProfile.State(b.baseSpeed(),b.effectiveSpeed(),a.travelSpeed(),b.movementModifiers(),b.jumpStrength(),.08,1,1,5,b.effects(),0,1F);
        assertTrue(GroundNativePhysicsProfile.classify(lag).isEmpty());
        assertFalse(GroundOwnedSprintTransition.profileEvolution(a,lag,a.travelSpeed(),1,true));}
}
