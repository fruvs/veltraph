package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import dev.veltraph.client.navigation.GroundCapturedEffects;
import dev.veltraph.client.navigation.GroundNativeEmptyProjection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundEmptyReadonlyProfileMatchTest {
    private record Raw(GroundNativePhysicsProfile.State values,List<GroundNativePhysicsProfile.Modifier> mods,List<GroundCapturedEffects.Effect> effects) {}
    private static final List<GroundCapturedEffects.Effect> EFFECTS=List.of(
        new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));
    private static final class Reads {
        final List<String> trace=new ArrayList<>();final int failAt;final AssertionError failure;
        Reads(){this(-1,new AssertionError("unused"));}Reads(int failAt,AssertionError failure){this.failAt=failAt;this.failure=failure;}
        <T>T get(String name,T value){trace.add(name);if(trace.size()==failAt)throw failure;return value;}
    }
    private static GroundNativePhysicsProfile.State state(double base,List<GroundNativePhysicsProfile.Modifier> mods,List<GroundCapturedEffects.Effect> effects){
        double speed=mods.isEmpty()?base:base*(1D+mods.getFirst().amount());int boost=effects.stream().anyMatch(e->e.id().equals("minecraft:jump_boost"))?5:-1;
        return new GroundNativePhysicsProfile.State(base,speed,(float)speed,mods,.42D,.08D,1D,1D,boost,effects,.15D,1F);
    }
    private static GroundNativePhysicsProfile.Modifier mod(Reads r,GroundNativePhysicsProfile.Modifier m){
        return new GroundNativePhysicsProfile.Modifier(r.get("modifier.name",r.get("modifier.id",m.id())),r.get("modifier.amount",m.amount()),r.get("modifier.ordinal",r.get("modifier.operation",m.operation())));
    }
    private static GroundCapturedEffects.Effect effect(Reads r,GroundCapturedEffects.Effect e){
        return new GroundCapturedEffects.Effect(r.get("effect.name",r.get("effect.holder",e.id())),r.get("effect.amplifier",e.amplifier()),r.get("effect.ambient",e.ambient()),r.get("effect.visible",e.visible()),r.get("effect.icon",e.icon()));
    }
    private static double[] scalars(Reads r,GroundNativePhysicsProfile.State s){return new double[]{r.get("base",s.baseSpeed()),r.get("effective",s.effectiveSpeed()),r.get("travel",s.travelSpeed()),r.get("jump",s.jumpStrength()),r.get("gravity",s.gravity()),r.get("friction",s.frictionModifier()),r.get("air",s.airDragModifier()),r.get("amplifier",s.jumpAmplifier())};}
    /** Independent reference constructs the original full State, including its effect normalization. */
    private static boolean reference(GroundNativePhysicsProfile.State expected,Raw raw,Reads r){
        var rows=r.get("getModifiers",raw.mods());var mods=rows.stream().map(m->mod(r,m)).toList();
        var v=scalars(r,raw.values());var effects=raw.effects().stream().map(e->effect(r,e)).toList();
        var current=new GroundNativePhysicsProfile.State(v[0],v[1],(float)v[2],mods,v[3],v[4],v[5],v[6],(int)v[7],effects,r.get("efficiency",raw.values().movementEfficiency()),r.get("blockFactor",raw.values().blockSpeedFactor()));
        return expected.equals(current);
    }
    /** Exercises the actual helper and actual readonly matcher; it cannot grant a native journey. */
    private static boolean candidate(GroundNativePhysicsProfile.State expected,Raw raw,Reads r){
        var match=new GroundNativeProfileReadonlyMatch(expected);var rows=r.get("getModifiers",raw.mods());
        if(GroundNativeEmptyProjection.ifEmpty(rows)==null)rows.stream().forEachOrdered(m->{var value=mod(r,m);match.modifier(value.id(),value.amount(),value.operation());});
        var v=scalars(r,raw.values());
        if(GroundNativeEmptyProjection.ifEmpty(raw.effects())==null)raw.effects().stream().forEachOrdered(e->{var value=effect(r,e);match.effect(value.id(),value.amplifier(),value.ambient(),value.visible(),value.icon());});
        return match.matches(v[0],v[1],(float)v[2],v[3],v[4],v[5],v[6],(int)v[7],r.get("efficiency",raw.values().movementEfficiency()),r.get("blockFactor",raw.values().blockSpeedFactor()));
    }
    private static boolean parity(GroundNativePhysicsProfile.State expected,Raw raw){var a=new Reads();var b=new Reads();boolean before=reference(expected,raw,a),after=candidate(expected,raw,b);assertEquals(before,after);assertEquals(a.trace,b.trace);return after;}
    @Test void positiveWalkSprintAndAllCapturedEffectSubsetsRetainFullGetterOrder(){
        for(double base:new double[]{.1D,(double).1F,GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})for(int sprint=0;sprint<3;sprint++)for(int subset=0;subset<128;subset++){
            var rows=new ArrayList<GroundCapturedEffects.Effect>();for(int i=0;i<7;i++)if((subset&(1<<i))!=0)rows.add(EFFECTS.get(i));
            var mods=sprint==0?List.<GroundNativePhysicsProfile.Modifier>of():List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",sprint==1?.3D:GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2));
            var expected=state(base,mods,rows);assertTrue(parity(expected,new Raw(expected,mods,rows)));
        }
    }
    @Test void emptyRowsNeverSuppressAnyScalarOrLateBlockGetter(){
        var expected=state(.1D,List.of(),List.of());var reads=new Reads();assertTrue(candidate(expected,new Raw(expected,List.of(),List.of()),reads));
        assertEquals(List.of("getModifiers","base","effective","travel","jump","gravity","friction","air","amplifier","efficiency","blockFactor"),reads.trace);
    }
    @Test void missingNonemptyCapturedRowsRefuseWhileStillConsumingEveryScalar(){
        var mods=List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3D,2));var expected=state(GroundNativePhysicsProfile.CAPTURED_400,mods,EFFECTS);
        for(var raw:List.of(new Raw(expected,List.of(),EFFECTS),new Raw(expected,mods,List.of()),new Raw(expected,List.of(),List.of())))assertFalse(parity(expected,raw));
    }
    @Test void foreignModifiersAndEffectsStillTraverseOriginalNonemptyPathAndRefuse(){
        var expected=state(.1D,List.of(),List.of());
        assertFalse(parity(expected,new Raw(expected,List.of(new GroundNativePhysicsProfile.Modifier("foreign",.1D,2)),List.of())));
        assertFalse(parity(expected,new Raw(expected,List.of(),List.of(new GroundCapturedEffects.Effect("minecraft:speed",1,false,true,true)))));
    }
    @Test void reversedEffectOrderRemainsEquivalentToOriginalStateSorting(){
        var expected=state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),EFFECTS);var reversed=new ArrayList<>(EFFECTS);Collections.reverse(reversed);
        assertTrue(parity(expected,new Raw(expected,List.of(),reversed)));
    }
    @Test void exactErrorAndOrderedPrefixAreRetainedAtEveryGetterInEmptyAndNonemptyCases(){
        var mods=List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3D,2));
        for(var raw:List.of(new Raw(state(.1D,List.of(),List.of()),List.of(),List.of()),new Raw(state(GroundNativePhysicsProfile.CAPTURED_400,mods,EFFECTS),mods,EFFECTS))){
            var all=new Reads();reference(raw.values(),raw,all);
            for(int i=1;i<=all.trace.size();i++){var failure=new AssertionError("getter "+i);var a=new Reads(i,failure);var b=new Reads(i,failure);
                assertSame(failure,assertThrows(AssertionError.class,()->reference(raw.values(),raw,a)));
                assertSame(failure,assertThrows(AssertionError.class,()->candidate(raw.values(),raw,b)));assertEquals(a.trace,b.trace);}
        }
    }
    @Test void nullModifierReceiverRetainsFailureAndNoLaterScalarRead(){
        var expected=state(.1D,List.of(),List.of());var raw=new Raw(expected,null,List.of());var a=new Reads();var b=new Reads();
        assertThrows(NullPointerException.class,()->reference(expected,raw,a));assertThrows(NullPointerException.class,()->candidate(expected,raw,b));assertEquals(a.trace,b.trace);assertEquals(List.of("getModifiers"),b.trace);
    }
    @Test void freshRowMismatchCannotReuseEarlierSuccessfulEmptyDecision(){
        var expected=state(.1D,List.of(),List.of());var rows=new ArrayList<GroundNativePhysicsProfile.Modifier>();
        assertTrue(parity(expected,new Raw(expected,rows,List.of())));rows.add(new GroundNativePhysicsProfile.Modifier("foreign",0D,2));
        assertFalse(parity(expected,new Raw(expected,rows,List.of())));rows.clear();assertTrue(parity(expected,new Raw(expected,rows,List.of())));
    }
}
