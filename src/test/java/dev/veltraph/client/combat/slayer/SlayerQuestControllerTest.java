package dev.veltraph.client.combat.slayer;
import dev.veltraph.client.config.*;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SlayerQuestControllerTest {
    final SlayerSettings s=SlayerSettings.from(new MapConfigService());
    MenuSnapshot menu(int id,String title,String label,String... lore) { return new MenuSnapshot(id,title,List.of(new MenuSnapshot.Slot(17,label,List.of(lore),false))); }
    SlayerQuestController atTier() {
        var q=new SlayerQuestController();q.start(false,0);
        assertEquals(SlayerQuestController.Kind.USE_PHONE,q.tick(null,s,0).kind());
        assertEquals(17,q.tick(menu(1,s.rootTitle(),s.bossLabel()),s,300).slotId());return q;
    }
    @Test void unsolicitedStartAndUnrelatedAcquisitionErrorsDoNotChangeActiveQuest() {
        var q=new SlayerQuestController();q.start(false,0);q.message("SLAYER QUEST STARTED!",s,10);
        assertEquals(SlayerQuestController.State.OPENING,q.state());
        q=atTier();q.tick(menu(2,s.bossTitle(),s.tierLabel(),"Cost: 500 Coins"),s,600);q.message("SLAYER QUEST STARTED!",s,650);
        q.message("You already have an active Slayer quest!",s,700);q.message("You cannot afford to start this quest!",s,750);
        assertEquals(SlayerQuestController.State.ACTIVE,q.state());
    }
    @Test void standaloneHeaderInsideMultilineServerMessageIsRecognized() {
        var q=atTier();q.tick(menu(2,s.bossTitle(),s.tierLabel(),"Cost: 500 Coins"),s,600);
        q.message("§eSLAYER QUEST STARTED!\nSlay Zombies",s,650);assertEquals(SlayerQuestController.State.ACTIVE,q.state());
    }
    @Test void fullCycleRequiresServerStartAndCompletionBeforeRepeat() {
        var q=atTier();assertEquals(SlayerQuestController.Kind.CLICK,q.tick(menu(2,s.bossTitle(),s.tierLabel(),"Cost: 500 Coins"),s,600).kind());
        var confirmation=q.tick(menu(3,s.confirmTitle(),s.confirmLabel()),s,900);assertEquals(SlayerQuestController.Kind.CLICK,confirmation.kind());
        assertEquals(SlayerQuestController.State.WAIT_START,q.state());
        assertEquals(SlayerQuestController.Kind.NONE,q.tick(menu(3,s.confirmTitle(),s.confirmLabel()),s,1300).kind());
        q.message("SLAYER QUEST STARTED!",s,1400);assertEquals(SlayerQuestController.State.ACTIVE,q.state());
        q.message("Player: SLAYER QUEST COMPLETE!",s,1500);assertEquals(SlayerQuestController.State.ACTIVE,q.state());
        q.message("SLAYER QUEST COMPLETE!",s,1600);assertEquals(SlayerQuestController.State.RESTART,q.state());
        assertEquals(SlayerQuestController.Kind.USE_PHONE,q.tick(null,s,1900).kind());
    }
    @Test void supportsDirectStartWithoutConfirmationMenu() {
        var q=atTier();q.tick(menu(2,s.bossTitle(),s.tierLabel(),"Cost:","500 Coins"),s,600);
        q.message("SLAYER QUEST STARTED!",s,650);assertEquals(SlayerQuestController.State.ACTIVE,q.state());
    }
    @Test void sourceAlreadyActiveResponseDuringTierSelectionHandsOffToCombat() {
        var q=atTier();q.message("You already have an active Slayer quest!",s,600);
        assertEquals(SlayerQuestController.State.ACTIVE,q.state());
    }
    @Test void unreadableOrExpensiveTierNeverGetsClicked() {
        for(String price:List.of("Reward: 100 Coins","Cost: 200,000 Coins","Cost: -1 Coins")) {
            var q=atTier();assertEquals(SlayerQuestController.Kind.NONE,q.tick(menu(2,s.bossTitle(),s.tierLabel(),price),s,600).kind());
            assertEquals(SlayerQuestController.State.FAILED,q.state());
        }
    }
    @Test void changedConfirmationPriceIsRechecked() {
        var q=atTier();q.tick(menu(2,s.bossTitle(),s.tierLabel(),"Cost: 500 Coins"),s,600);
        assertEquals(SlayerQuestController.Kind.NONE,q.tick(menu(3,s.confirmTitle(),s.confirmLabel(),"Cost: 999,999 Coins"),s,900).kind());
        assertEquals(SlayerQuestController.State.FAILED,q.state());
    }
    @Test void tierOneDoesNotMatchTierFourAndPlayerInventoryDoesNotSupplyButtons() {
        var slots=new MenuSnapshot(1,"test",List.of(new MenuSnapshot.Slot(2,"Revenant Horror IV",List.of(),false),
                new MenuSnapshot.Slot(40,"Revenant Horror I",List.of(),true),new MenuSnapshot.Slot(8,"✔ Revenant Horror I",List.of(),false)));
        assertEquals(8,slots.matching("Revenant Horror I").getFirst().id());
    }
    @Test void ambiguousButtonsFailInsteadOfGuessingASlot() {
        var q=atTier();var duplicate=new MenuSnapshot(2,s.bossTitle(),List.of(
                new MenuSnapshot.Slot(1,s.tierLabel(),List.of("Cost: 1 Coins"),false),new MenuSnapshot.Slot(2,s.tierLabel(),List.of("Cost: 1 Coins"),false)));
        assertEquals(SlayerQuestController.Kind.NONE,q.tick(duplicate,s,600).kind());assertEquals(SlayerQuestController.State.FAILED,q.state());
    }
    @Test void cancelWaitsForServerAcknowledgment() {
        var q=new SlayerQuestController();q.start(true,0);q.tick(null,s,0);
        q.tick(menu(1,s.rootTitle(),s.cancelLabel()),s,300);q.tick(menu(2,s.cancelTitle(),s.cancelConfirmLabel()),s,600);
        assertEquals(SlayerQuestController.State.WAIT_CANCEL,q.state());
        q.message("Your Slayer Quest has been cancelled!",s,900);assertEquals(SlayerQuestController.State.COMPLETE,q.state());
    }
    @Test void canCancelAConflictingQuestAndThenAcquireRequestedOne() {
        var c=new MapConfigService();c.set(SlayerConfig.SLAYER_CANCEL_EXISTING,true);var settings=SlayerSettings.from(c);
        var q=atTier();q.message("You already have an active Slayer quest!",settings,700);
        q.tick(null,settings,1000);q.tick(menu(3,s.rootTitle(),s.cancelLabel()),settings,1300);
        q.tick(menu(4,s.cancelTitle(),s.cancelConfirmLabel()),settings,1600);q.message("Your Slayer Quest has been cancelled!",settings,1700);
        assertEquals(SlayerQuestController.State.RESTART,q.state());assertEquals(SlayerQuestController.Kind.USE_PHONE,q.tick(null,settings,2000).kind());
    }
    @Test void onlyUnansweredPhoneCallsAreRetriedAndPaidClicksAreNotRepeated() {
        var q=new SlayerQuestController();q.start(false,0);q.tick(null,s,0);
        assertEquals(SlayerQuestController.Kind.USE_PHONE,q.tick(null,s,10001).kind());
        q.tick(null,s,20002);assertEquals(SlayerQuestController.State.FAILED,q.state());
        q=atTier();q.tick(menu(2,s.bossTitle(),s.tierLabel(),"Cost: 1 Coins"),s,600);q.tick(null,s,11000);
        assertEquals(SlayerQuestController.State.FAILED,q.state());
    }
    @Test void rewardClaimCanUpdateInPlaceWithoutChangingMenuId() {
        var q=new SlayerQuestController();q.start(false,0);q.tick(null,s,0);
        assertEquals(SlayerQuestController.Kind.CLICK,q.tick(menu(1,s.rootTitle(),s.claimLabel()),s,300).kind());
        assertEquals(SlayerQuestController.Kind.NONE,q.tick(menu(1,s.rootTitle(),s.claimLabel()),s,600).kind());
        var changed=menu(1,s.rootTitle(),s.bossLabel());
        assertEquals(SlayerQuestController.Kind.CLICK,q.tick(new MenuSnapshot(changed.id(),changed.title(),changed.slots(),1),s,900).kind());
    }
    @Test void parsesOnlyCostLinesAndRejectsOverflow() {
        assertEquals(1250,SlayerQuestController.cost(List.of("Cost:","1.25k Coins")).orElseThrow());
        assertTrue(SlayerQuestController.cost(List.of("Reward: 999 Coins")).isEmpty());
        assertTrue(SlayerQuestController.cost(List.of("Cost: 9999999999999999999999 Coins")).isEmpty());
        assertEquals(0,SlayerQuestController.cost(List.of("Cost: Free")).orElseThrow());
    }
    @Test void sourceTierClampChoosesHighestAvailableAtOrBelowRequested() {
        var c=new MapConfigService();c.set(SlayerConfig.SLAYER_QUEST_TIER,5);var settings=SlayerSettings.from(c);
        var menu=new MenuSnapshot(3,settings.bossTitle(),List.of(
                new MenuSnapshot.Slot(6,"Revenant Horror III",List.of("Cost: 500 Coins"),false),
                new MenuSnapshot.Slot(8,"Revenant Horror IV",List.of(),false,"minecraft:barrier")));
        assertEquals("Revenant Horror III",settings.availableTier(menu));
    }
    @Test void aClientPredictedSlotChangeDoesNotAcknowledgeARewardClaim() {
        var q=new SlayerQuestController();q.start(false,0);q.tick(null,s,0);
        q.tick(menu(1,s.rootTitle(),s.claimLabel()),s,300);
        assertEquals(SlayerQuestController.Kind.NONE,q.tick(menu(1,s.rootTitle(),s.bossLabel()),s,600).kind());
    }
}
