package dev.veltraph.client.navigation;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Data-only validation parity against exact original S72 source references, renamed only.
 * Native lifecycle/input/world/current checks are not modeled, bypassed or claimed proved. */
class GroundFreshProfileValidationParityTest {
    private record Data(double base,double effective,float travel,List<GroundNativePhysicsProfile.Modifier> modifiers,
                        double jump,double gravity,double friction,double air,int amplifier,List<GroundCapturedEffects.Effect> effects,double efficiency,float factor) {}
    private static final List<GroundCapturedEffects.Effect> EFFECTS=List.of(
        new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));
    private static Data ordinary(){return new Data(.1D,.1D,.1F,List.of(),.42D,.08D,1D,1D,-1,List.of(),.15D,1F);}
    private static GroundNativePhysicsProfile.State actual(Data d){return new GroundNativePhysicsProfile.State(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor);}
    private static OriginalAllocationGroundNativePhysicsProfile.State original(Data d){
        var mods=d.modifiers==null?null:d.modifiers.stream().map(m->new OriginalAllocationGroundNativePhysicsProfile.Modifier(m.id(),m.amount(),m.operation())).toList();
        return new OriginalAllocationGroundNativePhysicsProfile.State(d.base,d.effective,d.travel,mods,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor);
    }
    private static void parity(Data d){
        var old=original(d);var now=actual(d);boolean expected=OriginalAllocationGroundNativePhysicsProfile.classify(old).isPresent();
        assertEquals(expected,GroundNativePhysicsProfile.supportedState(now));assertEquals(expected,GroundNativePhysicsProfile.classify(now).isPresent());
        assertEquals(OriginalAllocationCapturedProfileRouteFamily.supported(old),CapturedProfileRouteFamily.supported(now));
        assertEquals(OriginalAllocationGroundOwnedSprintTransition.directionalVanillaProfileSupported(old),GroundOwnedSprintTransition.directionalVanillaProfileSupported(now));
        assertEquals(OriginalAllocationGroundOwnedSprintTransition.directionalProfileSupported(old),GroundOwnedSprintTransition.directionalProfileSupported(now));
        if(expected){assertSame(now,GroundNativePhysicsProfile.classify(now).orElseThrow().state());
            assertEquals(OriginalAllocationGroundNativePhysicsProfile.classify(old).orElseThrow().sprinting(),GroundNativePhysicsProfile.classify(now).orElseThrow().sprinting());}
    }
    private static Data scalar(Data d,int field,double value){return switch(field){
        case 0->new Data(value,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor);
        case 1->new Data(d.base,value,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor);
        case 2->new Data(d.base,d.effective,d.travel,d.modifiers,value,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor);
        case 3->new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,value,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor);
        case 4->new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,value,d.air,d.amplifier,d.effects,d.efficiency,d.factor);
        case 5->new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,value,d.amplifier,d.effects,d.efficiency,d.factor);
        case 6->new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,value,d.factor);
        default->throw new AssertionError(field);};}
    @Test void allOriginalBaseSprintEffectFamiliesAndJumpStrengthsKeepClassifierAndFamilyResults(){
        for(double base:new double[]{.1D,(double).1F,GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})
            for(double jump:new double[]{.42D,(double).42F})for(int sprint=0;sprint<3;sprint++)for(int subset=0;subset<128;subset++){
                var effects=new ArrayList<GroundCapturedEffects.Effect>();for(int i=0;i<7;i++)if((subset&(1<<i))!=0)effects.add(EFFECTS.get(i));
                var mods=sprint==0?List.<GroundNativePhysicsProfile.Modifier>of():List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",sprint==1?.3D:GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2));
                double speed=mods.isEmpty()?base:base*(1D+mods.getFirst().amount());parity(new Data(base,speed,(float)speed,mods,jump,.08D,1D,1D,(subset&64)==0?-1:5,effects,.15D,1F));
            }
    }
    @Test void exactScalarBitsNeighborsSignedZeroNanInfinityAndBoundsKeepOriginalRejections(){
        var d=ordinary();double[] nominal={d.base,d.effective,d.jump,d.gravity,d.friction,d.air,d.efficiency};
        for(int field=0;field<7;field++)for(double value:new double[]{nominal[field],Math.nextUp(nominal[field]),Math.nextDown(nominal[field]),0D,-0D,-1D,1D,Math.nextUp(1D),Double.MIN_VALUE,Double.MAX_VALUE,Double.NaN,Double.longBitsToDouble(0x7ff0000000000001L),Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})parity(scalar(d,field,value));
        for(float travel:new float[]{d.travel,Math.nextUp(d.travel),Math.nextDown(d.travel),0F,-0F,Float.NaN,Float.intBitsToFloat(0x7f800001),Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            parity(new Data(d.base,d.effective,travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor));
        for(float factor:new float[]{1F,Math.nextUp(1F),Math.nextDown(1F),0F,-0F,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            parity(new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,factor));
    }
    @Test void nullUnknownDuplicateAndFlagEffectMetadataKeepOriginalDecisions(){
        var d=ordinary();assertFalse(GroundNativePhysicsProfile.supportedState(null));assertTrue(GroundNativePhysicsProfile.classify(null).isEmpty());
        for(List<GroundCapturedEffects.Effect> effects:Arrays.<List<GroundCapturedEffects.Effect>>asList(null,List.of(),Arrays.asList((GroundCapturedEffects.Effect)null),List.of(EFFECTS.getFirst(),EFFECTS.getFirst()),List.of(new GroundCapturedEffects.Effect("foreign",0,false,true,true)),List.of(new GroundCapturedEffects.Effect(null,0,false,true,true)),List.of(new GroundCapturedEffects.Effect("minecraft:haste",1,false,true,true)),List.of(new GroundCapturedEffects.Effect("minecraft:haste",0,true,false,false)),EFFECTS))
            for(int amplifier:new int[]{-2,-1,0,5,6,Integer.MAX_VALUE})parity(new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,amplifier,effects,d.efficiency,d.factor));
    }
    @Test void everyMalformedModifierIdAmountOperationAndCardinalityKeepsOriginalDecision(){
        var d=ordinary();parity(new Data(d.base,d.effective,d.travel,null,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor));
        for(String id:Arrays.asList(null,"foreign","minecraft:sprinting"))for(double amount:new double[]{.3D,GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,0D,-0D,Double.NaN,Double.POSITIVE_INFINITY})for(int operation:new int[]{-1,0,1,2,3,Integer.MAX_VALUE}){
            var m=new GroundNativePhysicsProfile.Modifier(id,amount,operation);var mods=List.of(m);double speed=d.base*(1D+amount);
            parity(new Data(d.base,speed,(float)speed,mods,d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor));
            parity(new Data(d.base,speed,(float)speed,List.of(m,m),d.jump,d.gravity,d.friction,d.air,d.amplifier,d.effects,d.efficiency,d.factor));
        }
    }
    @Test void directionalAndCapturedEvolutionPredicatesKeepAllOriginalCallAndSpeedBranches(){
        for(double base:new double[]{.1D,GroundNativePhysicsProfile.CAPTURED_360})for(boolean beforeSprint:new boolean[]{false,true})for(boolean afterSprint:new boolean[]{false,true}){
            var effects=base==.1D?List.<GroundCapturedEffects.Effect>of():EFFECTS;int amp=base==.1D?-1:5;
            var modsA=beforeSprint?List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2)):List.<GroundNativePhysicsProfile.Modifier>of();
            var modsB=afterSprint?List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2)):List.<GroundNativePhysicsProfile.Modifier>of();
            double sa=base*(beforeSprint?1D+GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT:1D),sb=base*(afterSprint?1D+GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT:1D);
            var da=new Data(base,sa,(float)sa,modsA,.42D,.08D,1D,1D,amp,effects,.15D,1F);var db=new Data(base,sb,(float)sb,modsB,.42D,.08D,1D,1D,amp,effects,.15D,1F);
            for(int calls=-1;calls<=2;calls++)for(boolean argument:new boolean[]{false,true})for(float travel:new float[]{(float)sa,Math.nextUp((float)sa),Float.NaN}){
                assertEquals(OriginalAllocationGroundOwnedSprintTransition.profileEvolutionDirectionalQualified(original(da),original(db),travel,calls,argument),GroundOwnedSprintTransition.profileEvolutionDirectionalQualified(actual(da),actual(db),travel,calls,argument));
                assertEquals(OriginalAllocationGroundOwnedSprintTransition.profileEvolution(original(da),original(db),travel,calls,argument),GroundOwnedSprintTransition.profileEvolution(actual(da),actual(db),travel,calls,argument));
            }
        }
    }
    private static final class ForeignEffects extends AbstractList<GroundCapturedEffects.Effect>{
        final List<String> trace=new ArrayList<>();final Error failure;ForeignEffects(Error failure){this.failure=failure;}
        public int size(){trace.add("size");return 0;}public GroundCapturedEffects.Effect get(int i){trace.add("get:"+i);throw new IndexOutOfBoundsException(i);}
        public boolean isEmpty(){throw new AssertionError("No new foreign isEmpty callback");}
        public Stream<GroundCapturedEffects.Effect> stream(){trace.add("stream");if(failure!=null)throw failure;return super.stream();}
    }
    @Test void foreignStateConstructionKeepsExactOriginalStreamCallbackAndFailurePrefix(){
        for(boolean throwsStream:new boolean[]{false,true}){
            Error failure=new AssertionError("original source stream");var a=new ForeignEffects(throwsStream?failure:null);var z=new ForeignEffects(throwsStream?failure:null);var d=ordinary();
            var da=new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,a,d.efficiency,d.factor);var dz=new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,z,d.efficiency,d.factor);
            if(throwsStream){assertSame(failure,assertThrows(Error.class,()->original(da)));assertSame(failure,assertThrows(Error.class,()->actual(dz)));}
            else{assertEquals(OriginalAllocationGroundNativePhysicsProfile.classify(original(da)).isPresent(),GroundNativePhysicsProfile.supportedState(actual(dz)));}
            assertEquals(a.trace,z.trace);assertEquals("stream",a.trace.getFirst());
        }
    }
    @Test void classifyStillIssuesFreshProfileObjectsAndRetainsExactInputStateIdentity(){
        var state=actual(ordinary());var a=GroundNativePhysicsProfile.classify(state).orElseThrow();var b=GroundNativePhysicsProfile.classify(state).orElseThrow();
        assertNotSame(a,b);assertSame(state,a.state());assertSame(state,b.state());assertTrue(GroundNativePhysicsProfile.supportedState(state));
    }
    @Test void freshValidationNeverMemoizesStateOrCallerMutableListResults(){
        var d=ordinary();var effects=new ArrayList<GroundCapturedEffects.Effect>();var good=new Data(d.base,d.effective,d.travel,d.modifiers,d.jump,d.gravity,d.friction,d.air,d.amplifier,effects,d.efficiency,d.factor);
        var first=actual(good);assertTrue(GroundNativePhysicsProfile.supportedState(first));effects.add(new GroundCapturedEffects.Effect("foreign",0,false,true,true));
        assertFalse(GroundNativePhysicsProfile.supportedState(actual(good)));assertTrue(GroundNativePhysicsProfile.supportedState(first));
        effects.clear();assertTrue(GroundNativePhysicsProfile.supportedState(actual(good)));parity(good);
    }
}
