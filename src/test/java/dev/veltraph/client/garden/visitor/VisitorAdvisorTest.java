package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.config.VisitorConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the advisory Garden-visitor watcher (no Minecraft; source + chat are injected). */
class VisitorAdvisorTest {

    /** A mutable fake screen source. */
    private static final class FakeSource implements VisitorScreenSource {
        Optional<VisitorOffer> current = Optional.empty();
        @Override public Optional<VisitorOffer> read() {
            return current;
        }
    }

    private static VisitorOffer wheatVisitor() {
        return VisitorOffer.of("Jacob", VisitorRarity.COMMON, false, List.of(VisitorItem.of("Wheat", 64)));
    }

    /** Enabled advisor with the "Accept All" filter on and the budget cap off → a clean ACCEPT path. */
    private static MapConfigService acceptAllConfig() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(VisitorConfig.VISITOR_ADVISOR_ENABLED, true);
        cfg.set(VisitorConfig.VISITOR_ACCEPT_FILTER_ALL, true);
        cfg.set(VisitorConfig.VISITOR_HAVE_MAX_PRICES, false);
        return cfg;
    }

    @Test
    void disabledDoesNothing() {
        MapConfigService cfg = new MapConfigService();
        FakeSource src = new FakeSource();
        src.current = Optional.of(wheatVisitor());
        List<String> chat = new ArrayList<>();
        VisitorAdvisor advisor = new VisitorAdvisor(cfg, src, chat::add);
        advisor.tick(true);
        assertTrue(chat.isEmpty(), "disabled advisor must not announce");
        assertNull(advisor.lastDecision());
    }

    @Test
    void notInGardenDoesNothing() {
        MapConfigService cfg = acceptAllConfig();
        FakeSource src = new FakeSource();
        src.current = Optional.of(wheatVisitor());
        List<String> chat = new ArrayList<>();
        VisitorAdvisor advisor = new VisitorAdvisor(cfg, src, chat::add);
        advisor.tick(false);
        assertTrue(chat.isEmpty(), "out of Garden the advisor must not announce");
    }

    @Test
    void announcesAcceptOncePerVisitor() {
        MapConfigService cfg = acceptAllConfig();
        FakeSource src = new FakeSource();
        src.current = Optional.of(wheatVisitor());
        List<String> chat = new ArrayList<>();
        VisitorAdvisor advisor = new VisitorAdvisor(cfg, src, chat::add);

        advisor.tick(true);
        advisor.tick(true); // same visitor still open -> no duplicate
        assertEquals(1, chat.size());
        assertTrue(chat.get(0).contains("ACCEPT"), chat.get(0));
        assertEquals(VisitorDecision.Outcome.ACCEPT, advisor.lastDecision().outcome());
        assertTrue(advisor.menuOpen());
    }

    @Test
    void reAnnouncesAfterMenuCloses() {
        MapConfigService cfg = acceptAllConfig();
        FakeSource src = new FakeSource();
        List<String> chat = new ArrayList<>();
        VisitorAdvisor advisor = new VisitorAdvisor(cfg, src, chat::add);

        src.current = Optional.of(wheatVisitor());
        advisor.tick(true);
        src.current = Optional.empty(); // menu closed
        advisor.tick(true);
        assertTrue(!advisor.menuOpen());
        src.current = Optional.of(wheatVisitor()); // re-opened
        advisor.tick(true);
        assertEquals(2, chat.size(), "re-opening the menu re-announces");
    }

    @Test
    void noFilterEnabledRecommendsSkip() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(VisitorConfig.VISITOR_ADVISOR_ENABLED, true);
        // default VALUABLE_DROPS is on; turn it off so no filter is enabled at all.
        cfg.set(VisitorConfig.VISITOR_ACCEPT_FILTER_VALUABLE_DROPS, false);
        FakeSource src = new FakeSource();
        src.current = Optional.of(wheatVisitor());
        List<String> chat = new ArrayList<>();
        VisitorAdvisor advisor = new VisitorAdvisor(cfg, src, chat::add);

        advisor.tick(true);
        assertEquals(VisitorDecision.Outcome.SKIP, advisor.lastDecision().outcome());
        assertTrue(chat.get(0).contains("SKIP"), chat.get(0));
        assertTrue(advisor.status().contains("none"), advisor.status());
    }

    @Test
    void debugDumpIncludesFiltersAndDecision() {
        MapConfigService cfg = acceptAllConfig();
        FakeSource src = new FakeSource();
        src.current = Optional.of(wheatVisitor());
        VisitorAdvisor advisor = new VisitorAdvisor(cfg, src, s -> { });
        advisor.tick(true);
        String dump = String.join("\n", advisor.debugDump());
        assertTrue(dump.contains("filters=ALL"), dump);
        assertTrue(dump.contains("decision=ACCEPT"), dump);
        assertTrue(dump.contains("Jacob"), dump);
    }
}
