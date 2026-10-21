package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Exercises the same final producer boundary with actual owned-area receipts and mutable live parent facts. */
final class GhostSourceFallbackScopeFactsTest {
    private static final class Producer {
        final Object parent=new Object(),child=new Object(),player=new Object(),world=new Object(),connection=new Object();
        final GhostSourceOwnedParentArea area=new GhostSourceOwnedParentArea(parent);final GhostSourceOwnedParentArea.Stamp issued=new GhostSourceOwnedParentArea.Stamp(parent,1,2,player,world,connection);
        final GhostSourceFallbackScopeFacts facts;boolean registered=true,owner=true,enabled=true,deadline=true,item=true,nativeParent=true;long generation=1,settings=2;Object currentChild=child;
        Producer(){area.observeOwnedConstructorAbsence(issued);facts=new GhostSourceFallbackScopeFacts(issued,area.capture(issued));}
        GhostSourceOwnedParentArea.Stamp fresh(){return owner&&enabled&&deadline&&item&&nativeParent?new GhostSourceOwnedParentArea.Stamp(parent,generation,settings,player,world,connection):null;}
        Object fieldChild(GhostSourceOwnedParentArea.Stamp actual){return actual==null?null:currentChild;}
        boolean finish(){return facts.finalCheck(()->registered,this::fresh,this::fieldChild,child);}
    }
    @Test void unchangedActualAreaAndFreshSameParentFactsPermitProducerBoundary(){var producer=new Producer();assertTrue(producer.facts.areaCurrent(producer.fresh()));assertTrue(producer.finish());}
    @Test void callbackLossNewOwnerSettingsDeadlineItemAndNativeContextRejectFinalBoundary(){
        for(int change=0;change<7;change++){var producer=new Producer();switch(change){case 0->producer.registered=false;case 1->producer.owner=false;case 2->producer.settings++;case 3->producer.deadline=false;case 4->producer.item=false;case 5->producer.nativeParent=false;case 6->producer.enabled=false;default->throw new AssertionError();}assertFalse(producer.finish(),"change="+change);}}
    @Test void actualAreaInvalidationAndReplacementCannotBeHiddenByCapturedImmutableReceipt(){var producer=new Producer();producer.area.invalidate();assertFalse(producer.finish());producer.area.observeOwnedConstructorAbsence(producer.issued);assertFalse(producer.finish());}
    @Test void changedChildOrGenerationRejectsWithoutRenewingOriginalStamp(){var producer=new Producer();producer.currentChild=new Object();assertFalse(producer.finish());producer.currentChild=producer.child;producer.generation++;assertFalse(producer.finish());}
    @Test void namedHandoffSideEffectStillOccursOnceBeforeLaterGuardFailure(){var producer=new Producer();var named=new Object();var handoff=new GhostSourceNamedChildHandoff(producer.child);assertTrue(handoff.authorize(producer.child,named));assertTrue(handoff.current(named));producer.settings++;
        assertFalse(producer.finish());assertFalse(handoff.current(producer.child));}
    @Test void finalRegistrationShortCircuitSkipsFreshParentCallbackButFailedFreshStillReadsFieldAsOriginalNullGetter(){var producer=new Producer();var freshCalls=new AtomicInteger();var childCalls=new AtomicInteger();
        assertFalse(producer.facts.finalCheck(()->false,()->{freshCalls.incrementAndGet();return producer.fresh();},actual->{childCalls.incrementAndGet();return producer.fieldChild(actual);},producer.child));assertEquals(0,freshCalls.get());assertEquals(0,childCalls.get());
        assertFalse(producer.facts.finalCheck(()->true,()->null,actual->{childCalls.incrementAndGet();return producer.fieldChild(actual);},producer.child));assertEquals(1,childCalls.get());}
    @Test void lateFreshParentCallbackClearingRealProducerRegistrationCannotReturnTrue(){var producer=new Producer();
        assertFalse(producer.facts.finalCheck(()->producer.registered,()->{var same=producer.fresh();producer.registered=false;return same;},producer::fieldChild,producer.child));
        assertFalse(producer.registered);assertTrue(producer.facts.areaCurrent(producer.fresh()));}
    @Test void lateFieldChildCallbackClearingRealProducerRegistrationCannotReturnTrue(){var producer=new Producer();
        assertFalse(producer.facts.finalCheck(()->producer.registered,producer::fresh,actual->{var same=producer.fieldChild(actual);producer.registered=false;return same;},producer.child));
        assertFalse(producer.registered);assertTrue(producer.facts.areaCurrent(producer.fresh()));}

}
