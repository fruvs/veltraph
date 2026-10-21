package dev.veltraph.client.observation.unexpected;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
/** Existing issued-slot observer contract; no exemption for a particular family or selected slot. */
class FishingWormIssuedSlotIntentTest {
    private final Context context=new Context(new Activation("core_fishing",7,true),1,2,3);
    private UnexpectedBehaviorMonitor monitor(){return new UnexpectedBehaviorMonitor(f->{});}
    private Finding slot(UnexpectedBehaviorMonitor m,Context scope,int before,int after,long now){return m.observe(new Observation(scope,Kind.SELECTED_SLOT,Value.number(before),Value.number(after),now));}
    @Test void exactIssuedToolAndRodWritesEachAcknowledgeOnlyOnce(){var m=monitor();m.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500);assertEquals(Classification.EXPLAINED,slot(m,context,0,2,101).classification());assertEquals(0,m.pendingIntents());m.intent(context,Kind.SELECTED_SLOT,Value.number(0),0,200,500);assertEquals(Classification.EXPLAINED,slot(m,context,2,0,201).classification());assertEquals(Classification.UNEXPECTED,slot(m,context,2,0,202).classification());}
    @Test void manualDifferentSlotRemainsUnexpectedWithinIssuedWindow(){var m=monitor();m.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500);assertEquals(Classification.UNEXPECTED,slot(m,context,0,3,101).classification());}
    @Test void cancelledOrExpiredIntentCannotExplainLaterMatchingSlot(){var m=monitor();long receipt=m.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500,()->true);m.revokeIntent(receipt);assertEquals(Classification.UNEXPECTED,slot(m,context,0,2,101).classification());m.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,200,500);assertEquals(Classification.UNEXPECTED,slot(m,context,0,2,701).classification());}
    @Test void foreignSameParentActivationCannotReuseOldReceipt(){var m=monitor();m.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500);var other=new Context(new Activation("core_fishing",8,true),1,2,3);m.context(other,101);assertEquals(Classification.UNEXPECTED,slot(m,other,0,2,101).classification());}
    @Test void eachForeignNativeContextClearsTheIssuedExpectation(){for(var other:new Context[]{new Context(context.activation(),9,2,3),new Context(context.activation(),1,9,3),new Context(context.activation(),1,2,9)}){var m=monitor();m.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500);m.context(other,101);assertEquals(Classification.UNEXPECTED,slot(m,other,0,2,101).classification());assertEquals(0,m.pendingIntents());}}
    @Test void disabledCleanupDoesNotCreateAnActiveFamilyExemption(){var m=monitor();var inactive=new Context(new Activation("core_fishing",7,false),1,2,3);m.intent(inactive,Kind.SELECTED_SLOT,Value.number(0),0,100,500);assertEquals(0,m.pendingIntents());assertEquals(Classification.UNCERTAIN,slot(m,inactive,2,0,101).classification());}
}
