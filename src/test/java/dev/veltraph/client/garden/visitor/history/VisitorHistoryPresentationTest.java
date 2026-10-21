package dev.veltraph.client.garden.visitor.history;

import dev.veltraph.client.garden.visitor.*;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryPresentationTest {
    static final class Source implements VisitorScreenSource {
        VisitorHistoryStore.Observation observation=VisitorHistoryResolver.UNKNOWN.observe("");
        public Optional<VisitorOffer> read(){return Optional.of(VisitorOffer.of("Beth",VisitorRarity.RARE,observation.newness()==VisitorHistoryStore.Newness.NEW,List.of(new VisitorItem("Wheat",1,2))));}
        public VisitorHistoryStore.Observation historyObservation(){return observation;}
    }
    MapConfigService config(){var c=new MapConfigService();c.set(VisitorConfig.VISITOR_ADVISOR_ENABLED,true);c.set(VisitorConfig.VISITOR_ACCEPT_FILTER_ALL,true);c.set(VisitorConfig.VISITOR_HAVE_MAX_PRICES,true);c.set(VisitorConfig.VISITOR_MAX_COST,10L);return c;}
    @Test void unknownIsExplicitInChatStatusHudAndDebug(){var chat=new ArrayList<String>();var a=new VisitorAdvisor(config(),new Source(),chat::add);a.tick(true);assertTrue(chat.getFirst().contains("history=UNKNOWN"));assertTrue(a.status().contains("history=UNKNOWN"));assertTrue(a.hudLabel().contains("History: UNKNOWN"));assertTrue(a.debugDump().stream().anyMatch(s->s.contains("history=UNKNOWN")));assertTrue(a.debugDump().stream().noneMatch(s->s.contains("new=false")));}
    @Test void unknownHistoryStillAllowsIndependentAllPolicy(){var a=new VisitorAdvisor(config(),new Source(),null);a.tick(true);assertTrue(a.lastDecision().isAccept());}
    @Test void unknownDoesNotErasePriceGuards(){var c=config();c.set(VisitorConfig.VISITOR_MAX_COST,1L);var a=new VisitorAdvisor(c,new Source(),null);a.tick(true);assertFalse(a.lastDecision().isAccept());assertTrue(a.status().contains("history=UNKNOWN"));}
    @Test void reconciliationReasonIsVisibleWithoutCallingItSeen(){var src=new Source();src.observation=new VisitorHistoryStore.Observation(VisitorHistoryStore.Newness.UNKNOWN,"Coverage reconciliation required; observed competing serve");var a=new VisitorAdvisor(config(),src,null);a.tick(true);assertTrue(a.debugDump().stream().anyMatch(s->s.contains("Coverage reconciliation required")));assertTrue(a.status().contains("history=UNKNOWN"));}
    @Test void absentSourceDoesNotBecomeKnownSeen(){var a=new VisitorAdvisor(config(),null,null);a.tick(true);assertTrue(a.status().contains("history=UNKNOWN"));assertNull(a.hudLabel());}
    @Test void sameVisitorLosingNewEvidenceUpdatesRecommendation(){var c=config();c.set(VisitorConfig.VISITOR_ACCEPT_FILTER_ALL,false);c.set(VisitorConfig.VISITOR_ACCEPT_FILTER_VALUABLE_DROPS,false);c.set(VisitorConfig.VISITOR_ACCEPT_FILTER_NEW_VISITORS,true);var src=new Source();src.observation=new VisitorHistoryStore.Observation(VisitorHistoryStore.Newness.NEW,"declared known test roster");var messages=new ArrayList<String>();var a=new VisitorAdvisor(c,src,messages::add);a.tick(true);assertTrue(a.lastDecision().isAccept());src.observation=VisitorHistoryResolver.UNKNOWN.observe("Beth");a.tick(true);assertFalse(a.lastDecision().isAccept());assertEquals(2,messages.size());assertTrue(a.status().contains("history=UNKNOWN"));}
}
