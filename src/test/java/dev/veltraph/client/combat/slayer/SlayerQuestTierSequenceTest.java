package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlayerQuestTierSequenceTest {
    private static final class Run {
        final SlayerQuestController controller=new SlayerQuestController();final SlayerSettings settings;
        final Object tierNative=new Object(),confirmNative=new Object();
        final MenuSnapshot tierMenu,confirmMenu;
        final SlayerQuestController.ReturnedAction tier;
        final SlayerQuestTierSequence sequence;
        Run(){
            var cfg=new MapConfigService();cfg.set(SlayerConfig.SLAYER_QUEST_TIER,5);settings=SlayerSettings.from(cfg);
            tierMenu=new MenuSnapshot(10,settings.bossTitle(),List.of(slot(21,"Revenant Horror III",List.of("Cost: 500 Coins"))),1);
            confirmMenu=new MenuSnapshot(11,settings.confirmTitle(),List.of(slot(3,"Confirm",List.of("Revenant Horror III"))),2);
            controller.start(false,0);assertEquals(SlayerQuestController.Kind.USE_PHONE,controller.tick(null,settings,0).kind());
            var action=controller.tick(tierMenu,settings,300);tier=controller.returnedAction(action).orElseThrow();
            sequence=new SlayerQuestTierSequence(tier,tierNative,0);
        }
        SlayerQuestController.ReturnedAction confirmation(){
            var action=controller.tick(confirmMenu,settings,600);return controller.returnedAction(action).orElseThrow();
        }
        void tierAndMenu(){sequence.tierSubmitted(tier,1,true,1);sequence.menuApplied(confirmNative,11,2,"Confirm",2,true,2);}
        SlayerQuestController.ReturnedAction confirmAndSubmit(){tierAndMenu();var confirm=confirmation();assertTrue(sequence.confirmChoice(confirm,confirmNative,3));sequence.confirmSubmitted(confirm,3,true,4);return confirm;}
        void started(){var before=controller.state();controller.message("SLAYER QUEST STARTED!",settings,900);sequence.receivedStarted("SLAYER QUEST STARTED!",4,before,controller.state(),true,5);}
    }
    private static MenuSnapshot.Slot slot(int id,String label,List<String> lore){return new MenuSnapshot.Slot(id,label,lore,false,"minecraft:paper",1,false,"full-native-components");}
    @Test void fallbackAttestsActualReturnedThreeRatherThanRequestedFive(){var r=new Run();assertEquals(5,r.settings.requestedTier());assertEquals(3,r.tier.actualTier());assertEquals("Revenant Horror III",r.tier.action().expectedName());}
    @Test void completeOrderedSequenceExposesOnlyReadOnlyFact(){var r=new Run();r.confirmAndSubmit();r.started();var f=r.sequence.fact().orElseThrow();assertEquals(3,f.actualTier());assertEquals("Revenant Horror III",f.nativeTierLabel());assertEquals(1,f.tierSend());assertEquals(2,f.menuReceive());assertEquals(3,f.confirmSend());assertEquals(4,f.startReceive());}
    @Test void copiedActionCannotAccessPrivateReturnedIdentity(){var r=new Run();var a=r.tier.action();assertTrue(r.controller.returnedAction(new SlayerQuestController.Action(a.kind(),a.menuId(),a.slotId(),a.expectedName())).isEmpty());}
    @Test void actionChoiceAloneNeverAttestsNativeSubmission(){var r=new Run();assertTrue(r.sequence.fact().isEmpty());r.sequence.tierSubmitted(r.tier,1,false,1);assertEquals(SlayerQuestTierSequence.Phase.UNKNOWN,r.sequence.phase());}
    @Test void newerLocalMenuWithoutNativeAppliedReceiptCannotAdvance(){var r=new Run();r.sequence.tierSubmitted(r.tier,1,true,1);r.sequence.menuApplied(r.confirmNative,11,2,"Confirm",2,false,2);assertTrue(r.sequence.fact().isEmpty());assertEquals(SlayerQuestTierSequence.Phase.UNKNOWN,r.sequence.phase());}
    @Test void unchangedServerMenuOrWrongTitleDenies(){for(boolean wrongTitle:new boolean[]{false,true}){var r=new Run();r.sequence.tierSubmitted(r.tier,1,true,1);r.sequence.menuApplied(wrongTitle?r.confirmNative:r.tierNative,wrongTitle?11:10,wrongTitle?2:1,wrongTitle?"Other":"Confirm",2,true,2);assertEquals(SlayerQuestTierSequence.Phase.UNKNOWN,r.sequence.phase());}}
    @Test void outOfOrderDuplicateOrOldNativeSerialDenies(){for(long serial:new long[]{0,1}){var r=new Run();r.sequence.tierSubmitted(r.tier,1,true,1);r.sequence.menuApplied(r.confirmNative,11,2,"Confirm",serial,true,2);assertEquals(SlayerQuestTierSequence.Phase.UNKNOWN,r.sequence.phase());}}
    @Test void newerNativeMenuMustBeSamePhysicalMenuForConfirmation(){var r=new Run();r.tierAndMenu();assertFalse(r.sequence.confirmChoice(r.confirmation(),new Object(),3));}
    @Test void realConfirmationChoiceWithoutSubmittedClickCannotActivate(){var r=new Run();r.tierAndMenu();var confirm=r.confirmation();assertTrue(r.sequence.confirmChoice(confirm,r.confirmNative,3));r.sequence.confirmSubmitted(confirm,3,false,4);assertTrue(r.sequence.fact().isEmpty());}
    @Test void publicStartedBeforeConfirmSubmissionDoesNotAttestTier(){var r=new Run();r.tierAndMenu();r.controller.message("SLAYER QUEST STARTED!",r.settings,700);r.sequence.receivedStarted("SLAYER QUEST STARTED!",3,SlayerQuestController.State.CONFIRM_START,r.controller.state(),true,3);assertTrue(r.sequence.fact().isEmpty());}
    @Test void existingActiveResponseNeverSubstitutesForTierEvidence(){var r=new Run();r.sequence.tierSubmitted(r.tier,1,true,1);r.controller.message("You already have an active Slayer quest!",r.settings,700);assertEquals(SlayerQuestController.State.ACTIVE,r.controller.state());r.sequence.receivedStarted("You already have an active Slayer quest!",2,SlayerQuestController.State.CONFIRM_START,r.controller.state(),true,2);assertTrue(r.sequence.fact().isEmpty());}
    @Test void abnormalNativeReturnAndWrongEntryPhaseDeny(){for(boolean abnormal:new boolean[]{false,true}){var r=new Run();r.confirmAndSubmit();r.controller.message("SLAYER QUEST STARTED!",r.settings,900);r.sequence.receivedStarted("SLAYER QUEST STARTED!",4,abnormal?SlayerQuestController.State.WAIT_START:SlayerQuestController.State.ACTIVE,r.controller.state(),!abnormal,5);assertTrue(r.sequence.fact().isEmpty());}}
    @Test void nativeScopeRetirementAndNewControllerStartCannotReuseOldAction(){var r=new Run();r.sequence.invalidate();r.sequence.tierSubmitted(r.tier,1,true,1);assertTrue(r.sequence.fact().isEmpty());r.controller.start(false,1000);assertFalse(r.tier.current());}
    @Test void deadlineIsOriginalTierChoiceDeadlineAndRollbackDenies(){for(long now:new long[]{-1,10_000_000_001L}){var r=new Run();r.sequence.tierSubmitted(r.tier,1,true,now);assertEquals(SlayerQuestTierSequence.Phase.UNKNOWN,r.sequence.phase());}}
    @Test void uniqueExplicitTierEchoRejectsMissingWrongOrMixedTiers(){var r=new Run();assertTrue(SlayerQuestTierSequence.confirmationEcho(List.of("Start: Revenant Horror III","Cost: 500 Coins"),r.tier));for(var lines:List.of(List.of("Cost: 500 Coins"),List.of("Revenant Horror V"),List.of("Revenant Horror III","Revenant Horror IV"),List.of("Revenant Horror IIII")))assertFalse(SlayerQuestTierSequence.confirmationEcho(lines,r.tier));}
    @Test void sourcePrivatePhaseVersionDetectsChangeAndRevert(){var r=new Run();r.confirmAndSubmit();r.started();long active=r.controller.observedStateVersion();r.controller.message("SLAYER QUEST COMPLETE!",r.settings,1000);r.controller.message("SLAYER QUEST STARTED!",r.settings,1100);assertEquals(SlayerQuestController.State.ACTIVE,r.controller.state());assertEquals(active+2,r.controller.observedStateVersion());}
    @Test void originalQuoteLimitAndCurrencyStillRejectBeforeAnyReturnedChoice(){for(String quote:new String[]{"Cost: 100001 Coins","Cost: 500 Motes","Cost: unreadable Coins"}){var cfg=new MapConfigService();var s=SlayerSettings.from(cfg);var c=new SlayerQuestController();c.start(false,0);c.tick(null,s,0);var menu=new MenuSnapshot(10,s.bossTitle(),List.of(slot(21,"Revenant Horror I",List.of(quote))),1);var action=c.tick(menu,s,300);assertEquals(SlayerQuestController.State.FAILED,c.state());assertTrue(c.returnedAction(action).isEmpty());}}
    @Test void multipleTierPlaceholdersAndFuzzyRowsDoNotInventTierIdentity(){var cfg=new MapConfigService();cfg.set(SlayerConfig.SLAYER_QUEST_TIER,3);var s=SlayerSettings.from(cfg);var row=slot(21,"Bonus Revenant Horror III option",List.of("Cost: 500 Coins"));var menu=new MenuSnapshot(1,s.bossTitle(),List.of(row),1);var action=new SlayerQuestController.Action(SlayerQuestController.Kind.CLICK,1,21,row.name());assertEquals(0,SlayerQuestTierSequence.actualChoiceTier(action,menu,s));cfg.set(SlayerConfig.SLAYER_TIER_LABEL,"Boss {tier} {tier}");var ambiguous=SlayerSettings.from(cfg);assertEquals(0,SlayerQuestTierSequence.actualChoiceTier(action,menu,ambiguous));}
    @Test void mixedQuestHeadersOrDuplicateStartedHeadersAreUnknown(){assertTrue(SlayerQuestTierSequence.uniqueStartedHeader("SLAYER QUEST STARTED!\nNeutral description"));for(String text:new String[]{"SLAYER QUEST STARTED!\nSLAYER QUEST COMPLETE!","SLAYER QUEST STARTED!\nSLAYER QUEST STARTED!","SLAYER QUEST STARTED!\nYou already have an active quest!","Not a quest"})assertFalse(SlayerQuestTierSequence.uniqueStartedHeader(text));}
    @Test void admissionClockCannotRollBackWithinOriginalBornDeadlineRange(){
        var r=new Run();r.sequence.tierSubmitted(r.tier,1,true,50);
        r.sequence.menuApplied(r.confirmNative,11,2,"Confirm",2,true,25);
        assertEquals(SlayerQuestTierSequence.Phase.UNKNOWN,r.sequence.phase());assertTrue(r.sequence.fact().isEmpty());
    }

}
