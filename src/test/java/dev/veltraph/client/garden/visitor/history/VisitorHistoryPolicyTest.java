package dev.veltraph.client.garden.visitor.history;

import dev.veltraph.client.garden.visitor.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryPolicyTest extends VisitorHistoryTestSupport {
    VisitorOffer offer(VisitorHistoryResolver history,long price){return new VisitorOffer("Beth",VisitorRarity.RARE,
            history.observe("Beth").newness()==VisitorHistoryStore.Newness.NEW,List.of(new VisitorItem("Wheat",1,price)),50);}
    @Test void unknownHistoryCannotContributeNewFilter(){assertFalse(VisitorPolicy.decide(offer(VisitorHistoryResolver.UNKNOWN,2),VisitorSettings.of(10,true,VisitorAcceptFilter.NEW_VISITORS)).isAccept());}
    @Test void unknownHistoryDoesNotBlockIndependentAllFilter(){assertTrue(VisitorPolicy.decide(offer(VisitorHistoryResolver.UNKNOWN,2),VisitorSettings.of(10,true,VisitorAcceptFilter.NEW_VISITORS,VisitorAcceptFilter.ALL)).isAccept());}
    @Test void unknownHistoryDoesNotBlockIndependentValuableFilter(){assertTrue(VisitorPolicy.decide(offer(VisitorHistoryResolver.UNKNOWN,2),VisitorSettings.of(10,true,VisitorAcceptFilter.NEW_VISITORS,VisitorAcceptFilter.VALUABLE_DROPS)).isAccept());}
    @Test void confirmedNewStillRequiresPriceAndBudget()throws Exception{var s=store();s.commitBook(book(false,10),context(),10);var h=VisitorHistoryResolver.scoped(s,this::context,()->11,100);assertTrue(VisitorPolicy.decide(offer(h,2),VisitorSettings.of(10,true,VisitorAcceptFilter.NEW_VISITORS)).isAccept());assertFalse(VisitorPolicy.decide(offer(h,11),VisitorSettings.of(10,true,VisitorAcceptFilter.NEW_VISITORS)).isAccept());assertFalse(VisitorPolicy.decide(offer(h,-1),VisitorSettings.of(10,true,VisitorAcceptFilter.NEW_VISITORS)).isAccept());}
}
