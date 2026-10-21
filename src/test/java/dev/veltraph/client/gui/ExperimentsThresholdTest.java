package dev.veltraph.client.gui;

import dev.veltraph.client.config.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentsThresholdTest {
    static ExperimentSettings settings(int threshold,boolean maximize) {
        return new ExperimentSettings(0,1000,"Remember","Timer:","?","Claim Rewards","Rewards Claimed","Experiment Failed",maximize,
                new ExperimentSettings.RewardThreshold(threshold,"Private Receipt"));
    }
    static MenuSnapshot.Slot tile(int id,String name,String item,int count) { return new MenuSnapshot.Slot(id,name,List.of(),false,item,count,false); }
    static MenuSnapshot chrono(int rev,int rounds) { return new MenuSnapshot(7,"Chronomatron",List.of(tile(4,"Rounds","minecraft:bookshelf",rounds),tile(49,"Remember","minecraft:clock",1)),rev); }
    static MenuSnapshot ultra(int rev,int count) {
        var slots=new ArrayList<MenuSnapshot.Slot>();for(int i=1;i<=count;i++)slots.add(tile(9+i,"Tile","minecraft:red_dye",i));
        slots.add(tile(49,"Remember","minecraft:clock",1));return new MenuSnapshot(7,"Ultrasequencer",slots,rev);
    }
    @Test void chronoUsesBookshelfStackAndStrictGreaterThanWithoutCompletingOnClose() {
        var r=new ExperimentsRuntime();var p=settings(2,false);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(chrono(1,2),p,0).kind());
        assertEquals(ExperimentsRuntime.Kind.CLOSE,r.tick(chrono(2,3),p,1).kind());
        assertEquals(ExperimentsRuntime.State.EXITING,r.state());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(chrono(2,3),p,2).kind());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(null,p,3).kind());assertNotEquals(ExperimentsRuntime.State.COMPLETE,r.state());
        var reward=new MenuSnapshot(8,"Private Receipt",List.of(tile(49,"Claim Rewards","minecraft:paper",1)),1);
        assertEquals(ExperimentsRuntime.Kind.CLICK,r.tick(reward,p,4).kind());
        assertNotEquals(ExperimentsRuntime.State.COMPLETE,r.state());
        assertEquals(ExperimentsRuntime.Kind.CLOSE,r.tick(new MenuSnapshot(8,"Private Receipt",List.of(tile(49,"Rewards Claimed","minecraft:paper",1)),2),p,5).kind());
        assertEquals(ExperimentsRuntime.State.COMPLETE,r.state());
    }
    @Test void ultraUsesRememberedCountMinusOneRatherThanCurrentStackMaxOrMoveCount() {
        var r=new ExperimentsRuntime();var p=settings(2,false);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(ultra(1,3),p,0).kind());
        assertEquals(ExperimentsRuntime.Kind.CLOSE,r.tick(ultra(2,4),p,1).kind());
    }
    @Test void maximizeUsesDistinctVerifiedMaxima() {
        var r=new ExperimentsRuntime();var p=settings(0,true);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(chrono(1,15),p,0).kind());
        assertEquals(ExperimentsRuntime.Kind.CLOSE,r.tick(chrono(2,16),p,1).kind());
        r.reset();assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(ultra(1,21),p,0).kind());
        assertEquals(ExperimentsRuntime.Kind.CLOSE,r.tick(ultra(2,22),p,1).kind());
    }
    @Test void superpairsNeverUsesMinigameThresholdAndBookLookalikesCannotTriggerExit() {
        var r=new ExperimentsRuntime();var p=settings(0,true);
        assertEquals(ExperimentsRuntime.Kind.CLICK,r.tick(new MenuSnapshot(7,"Superpairs",List.of(tile(10,"?","minecraft:gray_stained_glass_pane",1),tile(4,"Round","minecraft:bookshelf",64)),1),p,0).kind());
        r.reset();var lookalike=new MenuSnapshot(7,"Chronomatron",List.of(new MenuSnapshot.Slot(4,"Round",List.of(),true,"minecraft:bookshelf",64,false),tile(49,"Remember","minecraft:clock",1)),1);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(lookalike,p,0).kind());assertEquals(ExperimentsRuntime.State.FAILED,r.state());
    }
    @Test void exitWithoutFreshExactRewardMenuTimesOutAndNeverRetriesOrAdoptsUnrelatedScreen() {
        var r=new ExperimentsRuntime();var p=settings(2,false);r.tick(chrono(1,3),p,0);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(null,p,1000).kind());assertEquals(ExperimentsRuntime.State.FAILED,r.state());
        r.reset();r.tick(chrono(1,3),p,0);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(new MenuSnapshot(9,"Wrong Receipt",List.of(tile(49,"Rewards Claimed","minecraft:paper",1)),1),p,1).kind());
        assertEquals(ExperimentsRuntime.State.FAILED,r.state());
    }
    @Test void bookshelfFirstServerSlotSemanticsAndMalformedOrMissingTierQuote() {
        var r=new ExperimentsRuntime();var p=settings(2,false);
        var books=new MenuSnapshot(7,"Chronomatron",List.of(tile(4,"Round","minecraft:bookshelf",2),tile(5,"Decorative","minecraft:bookshelf",64),tile(49,"Remember","minecraft:clock",1)),1);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(books,p,0).kind());
        assertEquals(2,ExperimentsParentRuntime.rewardThreshold(List.of("Round 1: first","Round 2: last"),"Round "));
        for(var rows:List.of(List.of("Unrelated 2: reward"),List.of("Round x: reward"),List.of("Round 2"),List.of("Round 9999999999999999: reward")))assertNull(ExperimentsParentRuntime.rewardThreshold(rows,"Round "));
    }
    @Test void explicitConfigRoundTripsAndMaximizeDefaultIsFalse() {
        var c=new MapConfigService();assertFalse(ExperimentsConfig.settings(c).maximizeXp());assertNull(ExperimentsConfig.settings(c).rewardThreshold());
        c.set(ExperimentsConfig.EXPERIMENTS_REWARD_THRESHOLD_PROFILE,"{\"rounds\":4,\"rewardMenuTitle\":\"Private Receipt\"}");c.set(ExperimentsConfig.MAXIMIZE_XP_EXPERIMENTS,true);
        assertEquals(settings(4,true).rewardThreshold(),ExperimentsConfig.settings(c).rewardThreshold());assertTrue(ExperimentsConfig.settings(c).maximizeXp());
        assertTrue(PersistableKeys.all().contains(ExperimentsConfig.MAXIMIZE_XP_EXPERIMENTS));assertTrue(PersistableKeys.all().contains(ExperimentsConfig.EXPERIMENTS_REWARD_THRESHOLD_PROFILE));
        for(String invalid:List.of("{}","{\"rewardMenuTitle\":\"Private Receipt\"}","{\"rounds\":2.5,\"rewardMenuTitle\":\"Private Receipt\"}","{\"rounds\":999999999999,\"rewardMenuTitle\":\"Private Receipt\"}")) {
            c.set(ExperimentsConfig.EXPERIMENTS_REWARD_THRESHOLD_PROFILE,invalid);assertThrows(IllegalArgumentException.class,()->ExperimentsConfig.settings(c));
        }
    }
    static ExperimentsParentSettings parentProfile(ExperimentsParentSettings.RewardThresholdContract contract) {
        var p=ExperimentsParentRuntimeTest.profile(false,0,0);
        var stage=new ExperimentsParentSettings.Stage(ExperimentsParentRuntimeTest.button("Table","Game","Tier"),ExperimentsParentRuntimeTest.button("Tier","Chosen","Chronomatron"),"Required: ","Cost: ",null,contract);
        return new ExperimentsParentSettings(p.tableTitle(),p.tableCommand(),List.of(stage),p.xpPath(),p.xpReturnPath(),p.xpBottleName(),p.xpAvailableLore(),p.xpCreditMode(),p.xpLevelsPrefix(),p.xpCostHeader(),p.xpCostSuffix(),p.purchaseReceipt(),p.currencyPrefix(),false,0,null,"","","","",0,0,0,0,1000,30000);
    }
    @Test void parentReadsLastNativeTierQuoteBeforeMaximizeAndRejectsMissingOrMalformedWithoutClick() {
        for(String quote:List.of("", "Rewards after round invalid: XP")) {
            var r=new ExperimentsParentRuntime();r.start(parentProfile(new ExperimentsParentSettings.RewardThresholdContract("Rewards after round","Private Receipt")),0);
            r.tick(ExperimentsParentRuntimeTest.obs(ExperimentsParentRuntimeTest.TABLE,20,1000,100),settings(0,true),1);
            var tier=ExperimentsParentRuntimeTest.menu("Tier",1,ExperimentsParentRuntimeTest.slot(10,"Chosen","Available","Required: 10","Cost: 5",quote));
            r.tick(ExperimentsParentRuntimeTest.obs(tier,20,1000,100),settings(0,true),2);
            assertEquals(ExperimentsParentRuntime.Kind.NONE,r.tick(ExperimentsParentRuntimeTest.obs(tier,20,1000,100),settings(0,true),3).kind());
            assertEquals(ExperimentsParentRuntime.State.FAILED,r.state());
        }
        var r=new ExperimentsParentRuntime();r.start(parentProfile(null),0);
        r.tick(ExperimentsParentRuntimeTest.obs(ExperimentsParentRuntimeTest.TABLE,20,1000,100),settings(0,false),1);
        var tier=ExperimentsParentRuntimeTest.menu("Tier",1,ExperimentsParentRuntimeTest.slot(10,"Chosen","Available","Required: 10","Cost: 5","Rewards after round 2: XP"));
        r.tick(ExperimentsParentRuntimeTest.obs(tier,20,1000,100),settings(0,false),2);r.tick(ExperimentsParentRuntimeTest.obs(tier,20,1000,100),settings(0,false),3);
        assertEquals(ExperimentsParentRuntime.State.FAILED,r.state());
    }
    @Test void parentExitWaitsForServerMenuClaimReceiptAndCancellationHasNoFurtherActions() {
        var r=new ExperimentsParentRuntime();var p=settings(99,false);r.start(parentProfile(new ExperimentsParentSettings.RewardThresholdContract("Rewards after round","Private Receipt")),0);
        r.tick(ExperimentsParentRuntimeTest.obs(ExperimentsParentRuntimeTest.TABLE,20,1000,100),p,1);
        var tier=ExperimentsParentRuntimeTest.menu("Tier",1,ExperimentsParentRuntimeTest.slot(10,"Chosen","Available","Required: 10","Cost: 5","Rewards after round 1: older","Rewards after round 2: XP"));
        r.tick(ExperimentsParentRuntimeTest.obs(tier,20,1000,100),p,2);assertEquals(10,r.tick(ExperimentsParentRuntimeTest.obs(tier,20,1000,100),p,3).slot());
        r.tick(ExperimentsParentRuntimeTest.obs(chrono(1,2),15,1000,100),p,4);
        assertEquals(ExperimentsParentRuntime.Kind.NONE,r.tick(ExperimentsParentRuntimeTest.obs(chrono(1,2),15,1000,100),p,5).kind());
        assertEquals(ExperimentsParentRuntime.Kind.CLOSE,r.tick(ExperimentsParentRuntimeTest.obs(chrono(2,3),15,1000,100),p,6).kind());
        assertEquals(ExperimentsParentRuntime.State.PLAYING,r.state());r.tick(ExperimentsParentRuntimeTest.obs(null,15,1000,100),p,7);
        var reward=new MenuSnapshot(8,"Private Receipt",List.of(tile(49,"Claim Rewards","minecraft:paper",1)),1);
        assertEquals(ExperimentsParentRuntime.Kind.NONE,r.tick(new ExperimentsParentRuntime.Observation(reward,15,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),true,false,false),p,8).kind());
        assertEquals(ExperimentsParentRuntime.Kind.CLICK,r.tick(ExperimentsParentRuntimeTest.obs(reward,15,1000,100),p,9).kind());
        assertEquals(ExperimentsParentRuntime.State.PLAYING,r.state());r.cancel();
        var receipt=new MenuSnapshot(8,"Private Receipt",List.of(tile(49,"Rewards Claimed","minecraft:paper",1)),2);
        assertEquals(ExperimentsParentRuntime.Kind.NONE,r.tick(ExperimentsParentRuntimeTest.obs(receipt,15,1000,100),p,10).kind());assertEquals(ExperimentsParentRuntime.State.CANCELLED,r.state());
    }

    @Test void absentServerRevisionCannotAcknowledgeReceiptAndStillExpiresClaim() {
        var r=new ExperimentsRuntime();var p=settings(2,false);r.tick(chrono(1,3),p,0);
        var reward=new MenuSnapshot(8,"Private Receipt",List.of(tile(49,"Claim Rewards","minecraft:paper",1)),1);
        assertEquals(ExperimentsRuntime.Kind.CLICK,r.tick(reward,p,900).kind());
        r.waitForServerOutcome(p,1001);assertEquals(ExperimentsRuntime.State.WAITING_ACK,r.state());
        r.waitForServerOutcome(p,1900);assertEquals(ExperimentsRuntime.State.FAILED,r.state());
    }

}
