package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import dev.veltraph.client.navigation.GroundCapturedEffects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GroundNativeProfileReadonlyMatchTest {
    private record Raw(GroundNativePhysicsProfile.State values,List<GroundNativePhysicsProfile.Modifier> modifiers,List<GroundCapturedEffects.Effect> effects) {}
    private static final List<GroundCapturedEffects.Effect> EFFECTS=List.of(
            new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));
    private static GroundNativePhysicsProfile.State state(double base,List<GroundNativePhysicsProfile.Modifier> mods,List<GroundCapturedEffects.Effect> effects){
        double effective=mods.isEmpty()?base:base*(1D+mods.getFirst().amount());int jump=effects.stream().anyMatch(e->e.id().equals("minecraft:jump_boost"))?5:-1;
        return new GroundNativePhysicsProfile.State(base,effective,(float)effective,mods,.42D,.08D,1D,1D,jump,effects,.15D,1F);
    }
    private static final class Reads {
        final List<String> events=new ArrayList<>();final int failAt;
        Reads(){this(-1);}Reads(int failAt){this.failAt=failAt;}
        <T>T read(String name,T value){events.add(name);if(events.size()==failAt)throw new IllegalStateException(name);return value;}
    }
    private static String modifierId(Reads r,GroundNativePhysicsProfile.Modifier m){return r.read("modifier.string",r.read("modifier.id",m.id()));}
    private static int operation(Reads r,GroundNativePhysicsProfile.Modifier m){return r.read("modifier.ordinal",r.read("modifier.operation",m.operation()));}
    private static String effectId(Reads r,GroundCapturedEffects.Effect e){return r.read("effect.id",r.read("effect.holder",e.id()));}
    /** Independent reference reconstructs the unchanged original materialized State and sorting. */
    private static boolean original(GroundNativePhysicsProfile.State expected,Raw raw,Reads r){
        var modifiers=new ArrayList<GroundNativePhysicsProfile.Modifier>();for(var m:raw.modifiers())modifiers.add(new GroundNativePhysicsProfile.Modifier(modifierId(r,m),r.read("modifier.amount",m.amount()),operation(r,m)));
        var v=raw.values();double base=r.read("base",v.baseSpeed()),effective=r.read("effective",v.effectiveSpeed());float travel=r.read("travel",v.travelSpeed());
        double jump=r.read("jumpStrength",v.jumpStrength()),gravity=r.read("gravity",v.gravity()),friction=r.read("friction",v.frictionModifier()),air=r.read("air",v.airDragModifier());int amp=r.read("jumpAmplifier",v.jumpAmplifier());
        var effects=new ArrayList<GroundCapturedEffects.Effect>();for(var e:raw.effects())effects.add(new GroundCapturedEffects.Effect(effectId(r,e),r.read("effect.amplifier",e.amplifier()),r.read("effect.ambient",e.ambient()),r.read("effect.visible",e.visible()),r.read("effect.icon",e.icon())));
        var observed=new GroundNativePhysicsProfile.State(base,effective,travel,modifiers,jump,gravity,friction,air,amp,effects,r.read("efficiency",v.movementEfficiency()),r.read("blockFactor",v.blockSpeedFactor()));
        return expected.equals(observed);
    }
    private static boolean candidate(GroundNativePhysicsProfile.State expected,Raw raw,Reads r){
        var match=new GroundNativeProfileReadonlyMatch(expected);for(var m:raw.modifiers())match.modifier(modifierId(r,m),r.read("modifier.amount",m.amount()),operation(r,m));
        var v=raw.values();double base=r.read("base",v.baseSpeed()),effective=r.read("effective",v.effectiveSpeed());float travel=r.read("travel",v.travelSpeed());
        double jump=r.read("jumpStrength",v.jumpStrength()),gravity=r.read("gravity",v.gravity()),friction=r.read("friction",v.frictionModifier()),air=r.read("air",v.airDragModifier());int amp=r.read("jumpAmplifier",v.jumpAmplifier());
        for(var e:raw.effects())match.effect(effectId(r,e),r.read("effect.amplifier",e.amplifier()),r.read("effect.ambient",e.ambient()),r.read("effect.visible",e.visible()),r.read("effect.icon",e.icon()));
        return match.matches(base,effective,travel,jump,gravity,friction,air,amp,r.read("efficiency",v.movementEfficiency()),r.read("blockFactor",v.blockSpeedFactor()));
    }
    private static void parity(GroundNativePhysicsProfile.State expected,Raw raw){var a=new Reads();var b=new Reads();assertEquals(original(expected,raw,a),candidate(expected,raw,b));assertEquals(a.events,b.events);}
    @Test void actualClassifiedWalkSprintAndAllEffectSubsetsMatchOriginalModel(){
        for(double base:new double[]{.1D,(double).1F,GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})for(int sprint=0;sprint<3;sprint++)for(int subset=0;subset<128;subset++){
            var effects=new ArrayList<GroundCapturedEffects.Effect>();for(int i=0;i<7;i++)if((subset&(1<<i))!=0)effects.add(EFFECTS.get(i));
            var mods=sprint==0?List.<GroundNativePhysicsProfile.Modifier>of():List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",sprint==1?.3D:GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2));
            var expected=state(base,mods,effects);var profile=GroundNativePhysicsProfile.classify(expected).orElseThrow();var raw=new Raw(expected,mods,effects);
            assertTrue(profile.matches(new GroundNativePhysicsProfile.State(expected.baseSpeed(),expected.effectiveSpeed(),expected.travelSpeed(),mods,expected.jumpStrength(),expected.gravity(),expected.frictionModifier(),expected.airDragModifier(),expected.jumpAmplifier(),effects,expected.movementEfficiency(),expected.blockSpeedFactor())));
            parity(expected,raw);
        }
    }
    private static void permutations(List<GroundCapturedEffects.Effect> rows,int i,GroundNativePhysicsProfile.State expected){
        if(i==rows.size()){parity(expected,new Raw(expected,expected.movementModifiers(),rows));return;}
        for(int j=i;j<rows.size();j++){Collections.swap(rows,i,j);permutations(rows,i+1,expected);Collections.swap(rows,i,j);}
    }
    @Test void allSevenEffectEncounterOrdersEqualOriginalIdSort(){var expected=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);permutations(new ArrayList<>(EFFECTS),0,expected);}
    @Test void duplicateMissingUnknownAndFlagChangesRefuseWithoutSkippingReads(){
        var expected=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);
        var duplicate=new ArrayList<>(EFFECTS);duplicate.set(0,EFFECTS.get(1));var unknown=new ArrayList<>(EFFECTS);unknown.set(0,new GroundCapturedEffects.Effect("minecraft:speed",0,false,true,true));
        var flags=new ArrayList<>(EFFECTS);flags.set(0,new GroundCapturedEffects.Effect("minecraft:haste",0,true,false,false));var amplifier=new ArrayList<>(EFFECTS);amplifier.set(0,new GroundCapturedEffects.Effect("minecraft:haste",1,false,true,true));
        for(var rows:List.of(duplicate,unknown,flags,amplifier,EFFECTS.subList(1,7),List.<GroundCapturedEffects.Effect>of())){var raw=new Raw(expected,List.of(),rows);parity(expected,raw);assertFalse(candidate(expected,raw,new Reads()));}
    }
    @Test void modifierCountIdOperationAmountAndEncounterOrderAreExact(){
        var sprint=List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3D,2));var expected=state(GroundNativePhysicsProfile.CAPTURED_400,sprint,EFFECTS);
        var foreign=new GroundNativePhysicsProfile.Modifier("foreign",.3D,2);
        for(var mods:List.of(List.<GroundNativePhysicsProfile.Modifier>of(),List.of(foreign),List.of(sprint.getFirst(),foreign),List.of(foreign,sprint.getFirst()),List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3D,1)),List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2)))){var raw=new Raw(expected,mods,EFFECTS);parity(expected,raw);assertFalse(candidate(expected,raw,new Reads()));}
    }
    private static GroundNativePhysicsProfile.State changed(GroundNativePhysicsProfile.State s,int field,double value){
        return new GroundNativePhysicsProfile.State(field==0?value:s.baseSpeed(),field==1?value:s.effectiveSpeed(),field==2?(float)value:s.travelSpeed(),s.movementModifiers(),field==3?value:s.jumpStrength(),field==4?value:s.gravity(),field==5?value:s.frictionModifier(),field==6?value:s.airDragModifier(),field==7?(int)value:s.jumpAmplifier(),s.effects(),field==8?value:s.movementEfficiency(),field==9?(float)value:s.blockSpeedFactor());
    }
    @Test void everyScalarMismatchIncludingLateFactorConsumesCompleteGetterSequence(){
        var expected=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);
        for(int field=0;field<10;field++)for(double value:new double[]{-0D,0D,Double.NaN,Double.POSITIVE_INFINITY,Double.MIN_VALUE,-1D,2D})parity(expected,new Raw(changed(expected,field,value),List.of(),EFFECTS));
    }
    @Test void canonicalNaNPayloadsAndSignedZeroExactlyFollowRecordEquals(){
        var base=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);
        for(int field:new int[]{0,1,2,3,4,5,6,8,9}){
            var expected=changed(base,field,Double.NaN);parity(expected,new Raw(changed(base,field,Double.longBitsToDouble(0x7ff8000000000001L)),List.of(),EFFECTS));
            expected=changed(base,field,0D);var raw=new Raw(changed(base,field,-0D),List.of(),EFFECTS);parity(expected,raw);assertFalse(candidate(expected,raw,new Reads()));
        }
    }
    @Test void throwAtEveryGetterKeepsOriginalOrderedExceptionPrefixEvenAfterMismatch(){
        var mods=List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3D,2));var expected=state(GroundNativePhysicsProfile.CAPTURED_360,mods,EFFECTS);var rows=new ArrayList<>(EFFECTS);rows.set(0,new GroundCapturedEffects.Effect("minecraft:speed",0,false,true,true));var raw=new Raw(changed(expected,9,.5D),mods,rows);var all=new Reads();candidate(expected,raw,all);
        for(int stop=1;stop<=all.events.size();stop++){var a=new Reads(stop);var b=new Reads(stop);assertThrows(IllegalStateException.class,()->original(expected,raw,a));assertThrows(IllegalStateException.class,()->candidate(expected,raw,b));assertEquals(a.events,b.events);}
    }
    @Test void scratchResultsCannotCarryAcrossFreshCallsOrDifferentCapturedProfile(){
        var a=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);var b=state(GroundNativePhysicsProfile.CAPTURED_400,List.of(),EFFECTS);
        assertTrue(candidate(a,new Raw(a,List.of(),EFFECTS),new Reads()));assertFalse(candidate(a,new Raw(b,List.of(),EFFECTS),new Reads()));assertTrue(candidate(b,new Raw(b,List.of(),EFFECTS),new Reads()));assertTrue(candidate(a,new Raw(a,List.of(),EFFECTS),new Reads()));
    }
    @Test void nativeDurationGuardRemainsSeparateAndCannotBeGrantedByMetadataMatch(){
        var s=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);assertTrue(candidate(s,new Raw(s,List.of(),EFFECTS),new Reads()));
        assertTrue(GroundCapturedEffects.durationCurrent(10,15,16,20,14));assertFalse(GroundCapturedEffects.durationCurrent(10,15,16,20,0));assertFalse(GroundCapturedEffects.durationCurrent(10,15,14,20,14));assertTrue(GroundCapturedEffects.durationCurrent(10,15,16,-1,-1));
    }
    @Test void internalNonNullIdContractDoesNotPretendNullableForeignStateSortingParity(){
        var s=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);var m=new GroundNativeProfileReadonlyMatch(s);assertThrows(NullPointerException.class,()->m.effect(null,0,false,true,true));assertThrows(NullPointerException.class,()->new GroundNativeProfileReadonlyMatch(null));
    }
}
