package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.game.FixedGameContextService;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpRotationService;
import dev.veltraph.client.testutil.FakeChatService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakeInventoryService;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeWorldService;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tool selection is enforced at start through the activation guard's pre-start check. */
class FarmModuleStartTest {

    private FakeInventoryService inventory;
    private FakeChatService chat;
    private CactusFarmModule cactus;
    private ModuleActivationGuard guard;

    private void setup(GameArea area) {
        inventory = new FakeInventoryService();
        chat = new FakeChatService();
        FarmServices services = new FarmServices(new FakeClock(), new FakePlayerService(),
                new FakeWorldService(), new RecordingInputService(), new NoOpRotationService(),
                new RecordingBlockBreaking(), inventory);
        cactus = new CactusFarmModule(services, new MapConfigService(), FarmLayoutRegistry.createDefault());
        ModuleManager mm = new ModuleManager();
        mm.register(cactus);
        guard = new ModuleActivationGuard(mm, new FixedGameContextService(
                new GameContext(true, true, area)), chat);
    }

    @Test
    void toolMissingBlocksStartWithChatError() {
        setup(GameArea.GARDEN); // location is valid; only the tool is missing
        boolean started = guard.toggle(CactusFarmModule.ID);
        assertFalse(started);
        assertFalse(cactus.isEnabled(), "must not start without the tool");
        assertEquals(1, chat.messages.size());
        assertTrue(chat.last().toLowerCase().contains("tool") || chat.last().contains("Cactus"));
    }

    @Test
    void selectedSlotSetOnStartWhenToolPresent() {
        setup(GameArea.GARDEN);
        inventory.setItem(6, "Cactus Knife");
        boolean started = guard.toggle(CactusFarmModule.ID);
        assertTrue(started);
        assertTrue(cactus.isEnabled());
        assertEquals(6, inventory.selectedSlot(), "the crop tool's hotbar slot is selected on start");
        assertTrue(chat.messages.isEmpty(), "no error when the tool is found");
    }
    @Test
    void rewarpReadinessReassertsTheActualStartToolAndRejectsReplacement() {
        setup(GameArea.GARDEN);inventory.setItem(6,"Cactus Knife");guard.toggle(CactusFarmModule.ID);
        inventory.selected=1;
        assertTrue(cactus.toolReadyForRewarp());assertEquals(6,inventory.selectedSlot());
        inventory.setItem(6,"Stone");assertFalse(cactus.toolReadyForRewarp());
        inventory.setItem(6,"");assertFalse(cactus.toolReadyForRewarp());
    }
    @Test
    void normalCycleSuspensionIsExclusiveAndManualDisableClearsIt() {
        setup(GameArea.GARDEN);inventory.setItem(6,"Cactus Knife");guard.toggle(CactusFarmModule.ID);
        assertTrue(cactus.suspendForCycleReset());assertFalse(cactus.suspendForCycleReset());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,cactus::suspendForRewarp);
        cactus.setEnabled(false);assertFalse(cactus.isSuspended());assertFalse(cactus.restartAfterCycleReset());
    }
    @Test
    void unvalidatedToolCannotAuthorizeAResume() {
        setup(GameArea.GARDEN);assertFalse(cactus.toolReadyForRewarp());
    }
    @Test
    void freshEngineThatStopsItselfCannotReportSuccessfulCycleRestart() {
        setup(GameArea.GARDEN);
        var farm=new FarmModule("restart_failure_fixture","Restart fixture",cactus.services,new MapConfigService(),FarmLayoutRegistry.createDefault()){
            public dev.veltraph.client.farming.domain.FarmLayoutProfile activeProfile(){return cactus.activeProfile();}
            public String debugState(){return "fixture";}
            protected java.util.List<String> toolKeywords(){return java.util.List.of();}
            protected void onTickFarming(){}
            protected void onCycleRestart(){setEnabled(false);}
        };
        farm.setEnabled(true);assertTrue(farm.suspendForCycleReset());
        assertFalse(farm.restartAfterCycleReset());assertFalse(farm.isEnabled());assertFalse(farm.isSuspended());
    }
    @Test
    void invalidFarmConfigurationBlocksBeforeToolSelectionOrOtherStartMutations() {
        setup(GameArea.GARDEN);inventory.setItem(6,"Cactus Knife");
        var farm=new FarmModule("readiness_fixture","Readiness fixture",cactus.services,new MapConfigService(),FarmLayoutRegistry.createDefault()){
            public dev.veltraph.client.farming.domain.FarmLayoutProfile activeProfile(){return cactus.activeProfile();}
            public String debugState(){return "fixture";}
            protected java.util.List<String> toolKeywords(){return java.util.List.of("knife");}
            protected void onTickFarming(){}
            protected java.util.Optional<String> configurationReadinessError(){return java.util.Optional.of("Invalid selected farm angle");}
        };
        assertEquals("Invalid selected farm angle",farm.preStartCheck().orElseThrow());
        assertEquals(0,inventory.selectCalls);assertFalse(farm.toolReadyForRewarp());
    }
    @Test void scheduledRestRebindRequiresExactTokenKeepsEnginePausedAndDoesNotEnableAgain(){
        setup(GameArea.GARDEN);var starts=new java.util.concurrent.atomic.AtomicInteger();var rebinds=new java.util.concurrent.atomic.AtomicInteger();
        var farm=new FarmModule("scheduled_fixture","Scheduled fixture",cactus.services,new MapConfigService(),FarmLayoutRegistry.createDefault()){
            public dev.veltraph.client.farming.domain.FarmLayoutProfile activeProfile(){return cactus.activeProfile();}public String debugState(){return "fixture";}
            protected java.util.List<String> toolKeywords(){return java.util.List.of();}protected void onTickFarming(){throw new AssertionError("Paused engine ticked");}
            public void onEnable(){starts.incrementAndGet();}protected void onResumeAfterRewarp(){rebinds.incrementAndGet();}
        };
        farm.setEnabled(true);var token=new Object();assertTrue(farm.suspendForMaintenance(token));
        assertFalse(farm.rebindForScheduledRest(new Object()));assertEquals(0,rebinds.get());assertTrue(farm.rebindForScheduledRest(token));
        assertTrue(farm.isEnabled());assertTrue(farm.maintenanceOwnedBy(token));farm.onTick();assertEquals(1,starts.get());assertEquals(1,rebinds.get());
        assertTrue(farm.resumeAfterMaintenance(token));assertFalse(farm.rebindForScheduledRest(token));
    }
}
