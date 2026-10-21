package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import java.util.Objects;

/** Per-call pure comparison against the privately captured classified profile. No native data,
 * getter, permission result or world observation is cached. Expected effects have unique non-null
 * IDs and size <=7 (the unchanged native classifier's domain); raw IDs are non-null canonical holder
 * names. Raw duplicate/unknown/count mismatches refuse, while callers still consume EVERY getter.
 * This is not a generic replacement for State sorting with nullable/duplicate expected effect IDs. */
final class GroundNativeProfileReadonlyMatch {
    private final GroundNativePhysicsProfile.State expected;
    private int modifierCount,effectCount,seenEffects;
    private boolean metadataMatches=true;
    GroundNativeProfileReadonlyMatch(GroundNativePhysicsProfile.State expected){this.expected=Objects.requireNonNull(expected);}
    private static boolean same(double a,double b){return Double.doubleToLongBits(a)==Double.doubleToLongBits(b);}
    private static boolean same(float a,float b){return Float.floatToIntBits(a)==Float.floatToIntBits(b);}
    void modifier(String id,double amount,int operation){
        int index=modifierCount++;
        if(index>=expected.movementModifiers().size()){metadataMatches=false;return;}
        var wanted=expected.movementModifiers().get(index);
        if(!Objects.equals(wanted.id(),id)||!same(wanted.amount(),amount)||wanted.operation()!=operation)metadataMatches=false;
    }
    void effect(String id,int amplifier,boolean ambient,boolean visible,boolean icon){
        Objects.requireNonNull(id);effectCount++;
        int index=-1;
        for(int i=0;i<expected.effects().size();i++)if(expected.effects().get(i).id().equals(id)){index=i;break;}
        if(index<0){metadataMatches=false;return;}
        int bit=1<<index;if((seenEffects&bit)!=0)metadataMatches=false;seenEffects|=bit;
        var wanted=expected.effects().get(index);
        if(wanted.amplifier()!=amplifier||wanted.ambient()!=ambient||wanted.visible()!=visible||wanted.icon()!=icon)metadataMatches=false;
    }
    boolean matches(double baseSpeed,double effectiveSpeed,float travelSpeed,double jumpStrength,double gravity,
            double frictionModifier,double airDragModifier,int jumpAmplifier,double movementEfficiency,float blockSpeedFactor){
        return metadataMatches&&modifierCount==expected.movementModifiers().size()&&effectCount==expected.effects().size()
                &&seenEffects==(1<<expected.effects().size())-1
                &&same(expected.baseSpeed(),baseSpeed)&&same(expected.effectiveSpeed(),effectiveSpeed)
                &&same(expected.travelSpeed(),travelSpeed)&&same(expected.jumpStrength(),jumpStrength)&&same(expected.gravity(),gravity)
                &&same(expected.frictionModifier(),frictionModifier)&&same(expected.airDragModifier(),airDragModifier)
                &&expected.jumpAmplifier()==jumpAmplifier&&same(expected.movementEfficiency(),movementEfficiency)
                &&same(expected.blockSpeedFactor(),blockSpeedFactor);
    }
}
