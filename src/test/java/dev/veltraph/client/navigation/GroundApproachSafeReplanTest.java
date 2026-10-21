package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundApproachSafeReplanTest {
    private static GroundJumpApproach.Plan plan(GroundJumpApproach.Key key){return new GroundJumpApproach.Plan(key,new Vec3(.53,70,7.28),new Vec3(0,-.0784,0),new Vec3(.5,70,7.2),0,List.of(key));}
    @Test void definitePaddedCoastCellFailureCanChooseDifferentFreshlyProvedActionWithinSameEpisode(){var tail=plan(GroundJumpApproach.Key.BACK);var safe=plan(GroundJumpApproach.Key.RIGHT);var calls=new AtomicInteger();var abandoned=new AtomicInteger();var proof=new GroundApproachSafeReplan.Proof(){public boolean accepts(GroundJumpApproach.Plan p){calls.incrementAndGet();return p==safe;}public boolean definiteUnsafe(){return true;}};assertSame(safe,GroundApproachSafeReplan.choose(tail,()->List.of(tail,safe),proof,()->true,()->{abandoned.incrementAndGet();return true;}).orElseThrow());assertEquals(2,calls.get());assertEquals(1,abandoned.get());assertFalse(GroundJumpApproach.currentClock(100,1250,1301));}
    @Test void unknownQueryOrRevokedBudgetCannotTriggerFallback(){var generated=new AtomicInteger();var abandoned=new AtomicInteger();var proof=new GroundApproachSafeReplan.Proof(){public boolean accepts(GroundJumpApproach.Plan p){return false;}public boolean definiteUnsafe(){return false;}};assertTrue(GroundApproachSafeReplan.choose(plan(GroundJumpApproach.Key.BACK),()->{generated.incrementAndGet();return List.of(plan(GroundJumpApproach.Key.LEFT));},proof,()->true,()->{abandoned.incrementAndGet();return true;}).isEmpty());assertEquals(0,generated.get());assertEquals(0,abandoned.get());}
    @Test void scopeLossDuringTailQueryDoesNotAbandonOrPublishFreshAction(){var current=new AtomicBoolean(true);var abandoned=new AtomicInteger();var proof=new GroundApproachSafeReplan.Proof(){public boolean accepts(GroundJumpApproach.Plan p){current.set(false);return false;}public boolean definiteUnsafe(){return true;}};assertTrue(GroundApproachSafeReplan.choose(plan(GroundJumpApproach.Key.BACK),()->List.of(plan(GroundJumpApproach.Key.RIGHT)),proof,current::get,()->{abandoned.incrementAndGet();return true;}).isEmpty());assertEquals(0,abandoned.get());}
    @Test void reentrantRootCheckReplacingEpisodeCannotClearNewTailOrSelectOldAction(){
        var kernel=new GroundApproachCommitment();var old=new Object();var fresh=new Object();var live=new java.util.concurrent.atomic.AtomicReference<Object>(old);var oldOffer=new Object();var newOffer=new Object();kernel.begin(old);kernel.offer(old,oldOffer,List.of(GroundJumpApproach.Key.NEUTRAL,GroundJumpApproach.Key.BACK));assertTrue(kernel.commit(old,oldOffer));
        var changes=new AtomicInteger();java.util.function.BooleanSupplier current=()->GroundApproachSafeReplan.sameEpisode(old,live::get,()->true,()->{if(changes.getAndIncrement()==0){live.set(fresh);kernel.begin(fresh);kernel.offer(fresh,newOffer,List.of(GroundJumpApproach.Key.NEUTRAL,GroundJumpApproach.Key.RIGHT));assertTrue(kernel.commit(fresh,newOffer));}return true;});
        var proof=new GroundApproachSafeReplan.Proof(){public boolean accepts(GroundJumpApproach.Plan p){return false;}public boolean definiteUnsafe(){return true;}};
        assertTrue(GroundApproachSafeReplan.choose(plan(GroundJumpApproach.Key.BACK),()->List.of(plan(GroundJumpApproach.Key.RIGHT)),proof,current,()->kernel.abandonTail(old)).isEmpty());
        assertEquals(List.of(GroundJumpApproach.Key.RIGHT),kernel.remaining(fresh));assertFalse(kernel.commit(old,oldOffer));
    }
    @Test void oldFailedSelectionCannotRefuseNewPendingUncommittedOffer(){
        var kernel=new GroundApproachCommitment();var old=new Object();var fresh=new Object();var live=new java.util.concurrent.atomic.AtomicReference<Object>(old);kernel.begin(old);var newOffer=new Object();
        java.util.function.BooleanSupplier current=()->GroundApproachSafeReplan.sameEpisode(old,live::get,()->true,()->{live.set(fresh);kernel.begin(fresh);kernel.offer(fresh,newOffer,java.util.List.of(GroundJumpApproach.Key.RIGHT));return true;});
        var proof=new GroundApproachSafeReplan.Proof(){public boolean accepts(GroundJumpApproach.Plan p){return false;}public boolean definiteUnsafe(){return true;}};
        assertTrue(GroundApproachSafeReplan.choose(plan(GroundJumpApproach.Key.BACK),()->java.util.List.of(plan(GroundJumpApproach.Key.RIGHT)),proof,current,()->kernel.abandonTail(old)).isEmpty());
        kernel.refuse(old); // Actual helper failure completion uses captured old selection token.
        assertTrue(kernel.commit(fresh,newOffer));
    }
    @Test void failedCapturedAbandonmentCannotProceedToFreshCandidate(){var generated=new AtomicInteger();var proof=new GroundApproachSafeReplan.Proof(){public boolean accepts(GroundJumpApproach.Plan p){return false;}public boolean definiteUnsafe(){return true;}};assertTrue(GroundApproachSafeReplan.choose(plan(GroundJumpApproach.Key.BACK),()->{generated.incrementAndGet();return List.of(plan(GroundJumpApproach.Key.RIGHT));},proof,()->true,()->false).isEmpty());assertEquals(0,generated.get());}
    @Test void abandonedTailCannotLateCommitButSameEpisodeCanOfferSafeReplacement(){var c=new GroundApproachCommitment();var token=new Object();var old=new Object();c.begin(token);c.offer(token,old,List.of(GroundJumpApproach.Key.NEUTRAL,GroundJumpApproach.Key.BACK,GroundJumpApproach.Key.BACK));assertTrue(c.commit(token,old));assertTrue(c.abandonTail(token));assertFalse(c.commit(token,old));var fresh=new Object();c.offer(token,fresh,List.of(GroundJumpApproach.Key.RIGHT));assertTrue(c.commit(token,fresh));assertTrue(c.remaining(token).isEmpty());}
}
