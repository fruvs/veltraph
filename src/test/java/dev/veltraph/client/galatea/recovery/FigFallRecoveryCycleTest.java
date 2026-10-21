package dev.veltraph.client.galatea.recovery;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigFallRecoveryCycleTest {
    @Test void exactSourceAreasNormalizeCornersAndExcludeMaximumFaces() {
        assertEquals(2,FigFallAtlas.AREAS.size());assertEquals(9,FigFallAtlas.AREAS.stream().mapToInt(a->a.goals().size()).sum());
        assertTrue(FigFallAtlas.AREAS.getFirst().contains(new Vec3(-733,109,-39)));
        assertTrue(FigFallAtlas.AREAS.getFirst().contains(new Vec3(-725.001,114.999,-31.001)));
        assertFalse(FigFallAtlas.AREAS.getFirst().contains(new Vec3(-725,112,-35)));
        assertFalse(FigFallAtlas.AREAS.getFirst().contains(new Vec3(-730,115,-35)));
        assertFalse(FigFallAtlas.AREAS.getFirst().contains(new Vec3(-730,112,-31)));
        assertTrue(FigFallAtlas.AREAS.get(1).contains(new Vec3(-735,105,-31)));
        assertFalse(FigFallAtlas.AREAS.get(1).contains(new Vec3(-718,110,-20)));
    }
    @Test void filtersBeforeRandomChoiceAndUsesFirstContainingArea() {
        Set<BlockPos> permitted=Set.of(new BlockPos(-733,115,-35),new BlockPos(-729,120,-23));
        Set<BlockPos> observed=new java.util.HashSet<>();
        var random=new Random(29);
        for(int seed=0;seed<32;seed++) {var c=new FigFallRecoveryCycle();assertTrue(c.start(new Vec3(-730,112,-35),permitted::contains,random,100));assertTrue(permitted.contains(c.goal()));observed.add(c.goal());}
        assertEquals(permitted,observed);
    }
    @Test void noAreaOrReachableGoalFailsWithoutDispatch() {
        var c=new FigFallRecoveryCycle();assertFalse(c.start(new Vec3(0,70,0),p->true,new Random(0),0));assertEquals(FigFallRecoveryCycle.State.FAILED,c.state());assertNull(c.goal());
        c.reset();assertFalse(c.start(new Vec3(-730,112,-35),p->false,new Random(0),0));assertNull(c.goal());
    }
    private FigFallRecoveryCycle active() {var c=new FigFallRecoveryCycle();assertTrue(c.start(new Vec3(-730,112,-35),p->true,new Random(0),100));return c;}
    @Test void successRequiresNativeArrivalAndOwnership() {var c=active();c.observe(true,true,false,false,200,1000);assertEquals(FigFallRecoveryCycle.State.TRAVELLING,c.state());c.observe(true,true,true,false,300,1000);assertEquals(FigFallRecoveryCycle.State.RECOVERED,c.state());}
    @Test void lossCannotReceiveAnArrivalCredit() {var c=active();c.observe(false,true,true,false,200,1000);assertEquals(FigFallRecoveryCycle.State.FAILED,c.state());}
    @Test void unloadedGoalFailsInsteadOfBeingCredited() {var c=active();c.observe(true,false,true,false,200,1000);assertEquals(FigFallRecoveryCycle.State.FAILED,c.state());}
    @Test void travelFailureAndTimeoutDoNotRetryAnotherGoal() {var c=active();var goal=c.goal();c.observe(true,true,false,true,200,1000);assertEquals(FigFallRecoveryCycle.State.FAILED,c.state());assertEquals(goal,c.goal());assertFalse(c.start(new Vec3(-730,112,-35),p->true,new Random(2),201));c.reset();c=active();c.observe(true,true,true,false,1101,1000);assertEquals(FigFallRecoveryCycle.State.FAILED,c.state());}
    @Test void cancellationDoesNotBecomeSuccess() {var c=active();c.cancel();c.observe(true,true,true,false,200,1000);assertEquals(FigFallRecoveryCycle.State.CANCELLED,c.state());}
}
