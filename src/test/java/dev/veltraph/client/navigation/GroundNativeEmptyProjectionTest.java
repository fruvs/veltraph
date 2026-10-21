package dev.veltraph.client.navigation;

import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundNativeEmptyProjectionTest {
    private static <T,R>List<R> selected(Collection<T> source,Function<T,R> originalMapper){
        List<R> empty=GroundNativeEmptyProjection.ifEmpty(source);
        return empty!=null?empty:source.stream().map(originalMapper).toList();
    }
    private static <T,R>List<R> original(Collection<T> source,Function<T,R> mapper){return source.stream().map(mapper).toList();}
    private static final List<GroundCapturedEffects.Effect> EFFECTS=List.of(
        new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));
    private static GroundNativePhysicsProfile.State state(double base,List<GroundNativePhysicsProfile.Modifier> mods,List<GroundCapturedEffects.Effect> effects){
        double speed=mods.isEmpty()?base:base*(1D+mods.getFirst().amount());
        return new GroundNativePhysicsProfile.State(base,speed,(float)speed,mods,.42D,.08D,1D,1D,
            effects.stream().anyMatch(e->e.id().equals("minecraft:jump_boost"))?5:-1,effects,.15D,1F);
    }
    @Test void emptyProjectionAvoidsTraversalAndElementMapperAndIsImmutable(){
        Collection<String> rows=new AbstractCollection<>(){
            public int size(){return 0;}
            public Iterator<String> iterator(){throw new AssertionError("empty traversal");}
        };
        List<Integer> projected=selected(rows,v->{throw new AssertionError("empty element getter");});
        assertEquals(List.of(),projected);assertThrows(UnsupportedOperationException.class,()->projected.add(1));
    }
    @Test void capturedFamiliesAndEveryNeutralEffectSubsetRetainOriginalState(){
        for(double base:new double[]{.1D,(double).1F,GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})
        for(int sprint=0;sprint<3;sprint++)for(int subset=0;subset<128;subset++){
            var effects=new ArrayList<GroundCapturedEffects.Effect>();for(int i=0;i<7;i++)if((subset&(1<<i))!=0)effects.add(EFFECTS.get(i));
            var mods=sprint==0?List.<GroundNativePhysicsProfile.Modifier>of():List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",sprint==1?.3D:GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2));
            var before=state(base,original(mods,Function.identity()),original(effects,Function.identity()));
            var after=state(base,selected(mods,Function.identity()),selected(effects,Function.identity()));
            assertEquals(before,after);assertEquals(GroundNativePhysicsProfile.classify(before).isPresent(),GroundNativePhysicsProfile.classify(after).isPresent());
        }
    }
    @Test void nonemptyUnknownModifierAndEffectMetadataAreNeverReplacedByEmpty(){
        var modifier=new GroundNativePhysicsProfile.Modifier("foreign:modifier",.5D,1);
        var effect=new GroundCapturedEffects.Effect("minecraft:speed",2,true,false,true);
        assertNull(GroundNativeEmptyProjection.ifEmpty(List.of(modifier)));assertNull(GroundNativeEmptyProjection.ifEmpty(List.of(effect)));
        assertEquals(List.of(modifier),selected(List.of(modifier),Function.identity()));
        assertEquals(List.of(effect),selected(List.of(effect),Function.identity()));
    }
    @Test void nonemptyNullRowsRetainOriginalElementAndStateFailure(){
        var rows=Collections.<GroundNativePhysicsProfile.Modifier>singletonList(null);
        assertNull(GroundNativeEmptyProjection.ifEmpty(rows));
        assertEquals(original(rows,Function.identity()),selected(rows,Function.identity()));
        assertThrows(NullPointerException.class,()->state(.1D,original(rows,Function.identity()),List.of()));
        assertThrows(NullPointerException.class,()->state(.1D,selected(rows,Function.identity()),List.of()));
    }
    @Test void originalEncounterOrderAndExactMapperErrorAreRetainedAtEveryNonemptyIndex(){
        var rows=List.of("first","second","third");
        for(int failed=0;failed<3;failed++){
            int at=failed;var failure=new AssertionError("exact error "+failed);var a=new ArrayList<String>();var b=new ArrayList<String>();
            Function<String,String> before=x->{a.add(x);if(a.size()-1==at)throw failure;return x;};
            Function<String,String> after=x->{b.add(x);if(b.size()-1==at)throw failure;return x;};
            assertSame(failure,assertThrows(AssertionError.class,()->original(rows,before)));
            assertSame(failure,assertThrows(AssertionError.class,()->selected(rows,after)));assertEquals(a,b);
        }
    }
    @Test void nullReceiverRefusesInsteadOfInventingEmptyMetadata(){
        assertThrows(NullPointerException.class,()->original(null,Function.identity()));
        assertThrows(NullPointerException.class,()->GroundNativeEmptyProjection.ifEmpty(null));
    }
    @Test void subsequentFreshCollectionChangesCannotUseEarlierEmptyResultAsSnapshot(){
        var rows=new ArrayList<String>();List<String> empty=GroundNativeEmptyProjection.ifEmpty(rows);assertNotNull(empty);
        rows.add("new row");assertNull(GroundNativeEmptyProjection.ifEmpty(rows));assertEquals(List.of("new row"),selected(rows,Function.identity()));
        assertTrue(empty.isEmpty());rows.clear();assertNotNull(GroundNativeEmptyProjection.ifEmpty(rows));
    }
    @Test void emptyDecisionErrorStaysPrimaryAndDoesNotExecuteNonemptyFallback(){
        var error=new AssertionError("collection read error");Collection<String> rows=new AbstractCollection<>(){
            public boolean isEmpty(){throw error;}public int size(){throw new AssertionError("size");}
            public Iterator<String> iterator(){throw new AssertionError("iterator");}
        };
        assertSame(error,assertThrows(AssertionError.class,()->GroundNativeEmptyProjection.ifEmpty(rows)));
        // This defensive helper boundary is not claimed to match arbitrary custom Collection.stream behavior.
        // Production uses the pinned native ImmutableSet and privately immutable list domain.
    }
}
