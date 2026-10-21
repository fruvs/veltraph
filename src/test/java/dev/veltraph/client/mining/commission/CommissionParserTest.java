package dev.veltraph.client.mining.commission;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

class CommissionParserTest {
    @Test void authenticCapturedMenuKeepsUnsupportedRaffleAndDoesNotInferClaim() {
        var rows = List.of(
            new CommissionParser.Row(11, "Commission #1", "minecraft:writable_book", List.of(
                "Commissions are tasks given directly", "by the king which give bountiful", "rewards.", "",
                "Rampart's Quarry Titanium", "Mine 10 Titanium Ore in Rampart's", "Quarry.", "", "Rewards",
                "- +900 HOTM Exp (Daily Bonus)", "- +410 HOTM Exp", "- 500 Mithril Powder", "- +7.5k Mining Exp",
                "", "Progress", "                     0%")),
            new CommissionParser.Row(15, "Commission #2", "minecraft:writable_book", List.of(
                "Commissions are tasks given directly", "by the king which give bountiful", "rewards.", "",
                "Lucky Raffle", "Deposit 20 Tickets during the Raffle", "Event.", "", "Rewards",
                "- +900 HOTM Exp (Daily Bonus)", "- +410 HOTM Exp", "- 500 Mithril Powder", "- +7.5k Mining Exp",
                "", "Progress", "                     0%")));
        var parsed = CommissionParser.menu("pwc", 1, 100, "Commissions", true, rows);
        assertEquals(2, parsed.tasks().size()); assertTrue(parsed.completeSection());
        assertEquals(Work.TITANIUM, parsed.tasks().get(0).work());
        assertEquals("Rampart's Quarry", parsed.tasks().get(0).region());
        assertEquals(Work.UNSUPPORTED, parsed.tasks().get(1).work());
        assertTrue(parsed.tasks().stream().noneMatch(Task::done));
        assertTrue(parsed.tasks().stream().noneMatch(Task::claimable));
    }
    @Test void tabPercentIsAnchoredAndCompletedRequiresExactValue() {
        var p = CommissionParser.tab("p", 1, 0, List.of("Other: DONE", "§bCommissions:",
            "§aMithril Miner: 12.5%", "Titanium Miner: DONE", "Lucky Raffle: NOT DONE", ""));
        assertEquals(3, p.tasks().size()); assertEquals(12.5, p.tasks().getFirst().percent());
        assertTrue(p.tasks().get(1).done()); assertFalse(p.tasks().get(2).progressKnown());
    }
    @Test void unterminatedSectionAndMissingHeaderCannotAuthorizeLifecycle() {
        assertFalse(CommissionParser.tab("p",1,0,List.of("Commissions:","Mithril Miner: 20%")).completeSection());
        assertFalse(CommissionParser.tab("p",1,0,List.of("Mithril Miner: 20%", "")).completeSection());
    }
    @Test void truncatedLoreAndForeignMenuRemainIncomplete() {
        var row = new CommissionParser.Row(11,"Commission #1","minecraft:writable_book",List.of("intro", ""));
        assertFalse(CommissionParser.menu("p",1,0,"Commissions",true,List.of(row)).completeSection());
        assertTrue(CommissionParser.menu("p",1,0,"Other Commissions",true,List.of(row)).tasks().isEmpty());
    }
    @Test void invalidPercentNeverBecomesWork() {
        for (String value : List.of("101%", "-1%", "NaN%", "50% DONE", "100.1%"))
            assertFalse(CommissionParser.tab("p",1,0,List.of("Commissions:","Mithril Miner: "+value,"")).tasks().getFirst().active());
    }
    @Test void compatibleGlobalOreAttachesToCurrentRegionWithoutCrossRegionOrMob() {
        var tasks = CommissionParser.tab("p",1,0,List.of("Commissions:","Royal Mines Mithril: 10%",
            "Titanium Miner: 10%", "Rampart's Quarry Titanium: 10%", "Goblin Slayer: 10%", "")).tasks();
        var chosen = CommissionSelector.select(tasks, "Rampart's Quarry");
        assertEquals(List.of("Rampart's Quarry Titanium", "Titanium Miner"), chosen.stream().map(Task::name).toList());
        assertEquals("Royal Mines Mithril", tasks.getFirst().name());
    }
    @Test void slayerSelectionReturnsActualChosenSlayerRatherThanSortedFirst() {
        var tasks = CommissionParser.tab("p",1,0,List.of("Commissions:","Goblin Slayer: 10%", "Mithril Miner: 10%", "")).tasks();
        assertEquals(List.of(tasks.getFirst()), CommissionSelector.select(tasks,""));
    }
    @Test void malformedSnapshotBoundsReject() {
        assertThrows(IllegalArgumentException.class, () -> CommissionParser.tab("p",1,0,java.util.Collections.nCopies(257,"")));
        assertThrows(IllegalArgumentException.class, () -> new Task("x",Work.MITHRIL,"","",Double.NaN,true,false,-1));
    }
}
