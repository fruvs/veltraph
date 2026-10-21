package dev.veltraph.client.core;

import dev.veltraph.client.game.FixedGameContextService;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.testutil.FakeChatService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleActivationGuardTest {
    @Test void freshStartHookRunsOnceAndNeverForDuplicateOrSelfDisabledEnable(){
        var manager=new ModuleManager();var good=new GardenModule();manager.register(good);
        var gate=new ModuleActivationGuard(manager,new FixedGameContextService(garden()),new FakeChatService());var fresh=new java.util.concurrent.atomic.AtomicInteger();gate.afterFreshStart(module->fresh.incrementAndGet());
        assertTrue(gate.start(good));assertTrue(gate.start(good));assertEquals(1,fresh.get());manager.disable(good.id());
        var selfStop=new AbstractModule("self_stop","Self stop",ModuleCategory.INTERNAL){public void onEnable(){setEnabled(false);}};manager.register(selfStop);
        gate.start(selfStop);assertFalse(selfStop.isEnabled());assertEquals(1,fresh.get());
    }
    @Test void sharedReadinessBlocksBeforeNativePreStartAndPreservesExternalBlocker(){
        var manager=new ModuleManager();var pre=new java.util.concurrent.atomic.AtomicInteger();var before=new java.util.concurrent.atomic.AtomicInteger();var after=new java.util.concurrent.atomic.AtomicInteger();
        var module=new AbstractModule("shared_ready","Shared readiness",ModuleCategory.INTERNAL){public Optional<String> preStartCheck(){pre.incrementAndGet();return Optional.empty();}};manager.register(module);
        var chat=new FakeChatService();var gate=new ModuleActivationGuard(manager,new FixedGameContextService(garden()),chat);
        gate.standardReadiness(candidate->Optional.of("Timer configuration not ready"));gate.beforeActivation(candidate->before.incrementAndGet());gate.afterFreshStart(candidate->after.incrementAndGet());
        assertFalse(gate.start(module));assertEquals(0,pre.get());assertEquals(1,before.get());assertEquals(0,after.get());
        gate.externalControlBlocker(candidate->Optional.of("Healer owns control"));assertFalse(gate.start(module));assertEquals(1,before.get());assertEquals(0,pre.get());assertFalse(module.isEnabled());
    }
    @Test void externalControlIsCheckedBeforeTransactionsReadinessAndActivation() {
        var manager=new ModuleManager();int[] calls={0,0,0};
        var module=new AbstractModule("action","Action",ModuleCategory.MISC) {
            @Override public Optional<String> preStartCheck(){calls[1]++;return Optional.empty();}
            @Override public void onEnable(){calls[2]++;}
        };
        var observer=new Observer();manager.register(module);manager.register(observer);
        var guard=new ModuleActivationGuard(manager,new FixedGameContextService(garden()),new FakeChatService());
        guard.activationTransaction((candidate,activation)->{calls[0]++;return activation.getAsBoolean();});
        guard.externalControlBlocker(candidate->Optional.of("Survival response owns controls"));
        assertFalse(guard.start(module));assertArrayEquals(new int[]{0,0,0},calls);assertFalse(module.isEnabled());
        assertTrue(guard.start(observer));assertArrayEquals(new int[]{1,0,0},calls);
        guard.externalControlBlocker(candidate->{throw new IllegalStateException("Unavailable ownership");});
        assertFalse(guard.start(module));assertArrayEquals(new int[]{1,0,0},calls);assertFalse(module.isEnabled());
        guard.externalControlBlocker(candidate->Optional.empty());
        assertTrue(guard.start(module));assertArrayEquals(new int[]{2,1,1},calls);
    }
    @Test void preparedHandoffAdmitsOnlyExactPairAndPreservesLocationAndReadiness() {
        var manager=new ModuleManager();var farm=new GardenModule("farm","Farm");
        var helper=new GardenModule("helper","Helper");var unrelated=new GardenModule("other","Other");
        manager.register(farm);manager.register(helper);manager.register(unrelated);
        var context=new FixedGameContextService(garden());
        var guard=new ModuleActivationGuard(manager,context,new FakeChatService());
        assertTrue(guard.start(farm));assertFalse(guard.start(helper));
        guard.activationConflictExemption((running,starting)->running==farm&&starting==helper);
        assertFalse(guard.start(unrelated));
        context.set(hub());assertFalse(guard.start(helper));assertTrue(farm.isEnabled());
        context.set(garden());assertTrue(guard.start(helper));
        assertTrue(farm.isEnabled());assertFalse(guard.start(unrelated));
        helper.setEnabled(false);guard.activationConflictExemption((running,starting)->false);
        assertFalse(guard.start(helper));
        int[] activations={0};
        var unready=new AbstractModule("unready","Unready",ModuleCategory.FARMING) {
            @Override public Optional<String> preStartCheck(){return Optional.of("Missing configured profile");}
            @Override public void onEnable(){activations[0]++;}
        };
        manager.register(unready);
        guard.activationConflictExemption((running,starting)->running==farm&&starting==unready);
        assertFalse(guard.start(unready));assertEquals(0,activations[0]);assertTrue(farm.isEnabled());
    }
    private static class Observer extends AbstractModule {
        Observer(){super("observer","Observer",ModuleCategory.HUNTING);}
        @Override public boolean requiresSkyBlock(){return true;}
        @Override public boolean ownsGameplayInput(){return false;}
        @Override public boolean isPassiveObserver(){return true;}
    }

    @Test void observerCanRunWithOneControllerInEitherStartOrder() {
        for(boolean observerFirst:new boolean[]{false,true}) {
            var manager=new ModuleManager();var observer=new Observer();var first=new GardenModule("first","First");
            var second=new GardenModule("second","Second");manager.register(observer);manager.register(first);manager.register(second);
            var guard=new ModuleActivationGuard(manager,new FixedGameContextService(garden()),new FakeChatService());
            assertTrue(guard.start(observerFirst?observer:first));
            assertTrue(guard.start(observerFirst?first:observer));
            assertFalse(guard.start(second));assertTrue(observer.isEnabled());assertTrue(first.isEnabled());
            assertEquals(java.util.List.of(first),manager.enabledControllers());
            manager.stopControllers();assertFalse(first.isEnabled());assertTrue(observer.isEnabled());
            manager.stopAll();assertFalse(observer.isEnabled());
        }
    }

    @Test void menuControllerWithoutMovementOwnershipStillRemainsExclusive() {
        var manager=new ModuleManager();var menu=new AbstractModule("menu","Menu",ModuleCategory.MISC) {
            @Override public boolean ownsGameplayInput(){return false;}
            @Override public boolean requiresSkyBlock(){return true;}
        };
        var farm=new GardenModule();manager.register(menu);manager.register(farm);
        var guard=new ModuleActivationGuard(manager,new FixedGameContextService(garden()),new FakeChatService());
        assertTrue(guard.start(menu));assertFalse(guard.start(farm));
    }

    @Test void stoppedCleanupReservesInteractionsUntilRestitutionAndObserverStillWorks() {
        var manager=new ModuleManager();var pending=new AbstractModule("restore","Restoring",ModuleCategory.MISC) {
            boolean waiting=true;
            @Override public boolean hasPendingCleanup(){return waiting;}
            @Override public void maintenanceTick(){waiting=false;}
        };
        var farm=new GardenModule();var observer=new Observer();manager.register(pending);manager.register(farm);manager.register(observer);
        var guard=new ModuleActivationGuard(manager,new FixedGameContextService(garden()),new FakeChatService());
        assertFalse(guard.start(farm));assertTrue(guard.start(observer));assertFalse(farm.isEnabled());
        manager.maintenanceTick();assertTrue(guard.start(farm));
    }

    /** A module that requires Garden + SkyBlock, like the farming modules. */
    private static final class GardenModule extends AbstractModule {
        boolean ticked;

        GardenModule() {
            this("garden_test", "Wart/Crops");
        }

        GardenModule(String id, String displayName) {
            super(id, displayName, ModuleCategory.FARMING);
        }

        @Override
        public Optional<GameArea> requiredArea() {
            return Optional.of(GameArea.GARDEN);
        }

        @Override
        public boolean requiresSkyBlock() {
            return true;
        }

        @Override
        public void onTick() {
            ticked = true;
        }
    }

    private static GameContext garden() {
        return new GameContext(true, true, GameArea.GARDEN);
    }

    private static GameContext hub() {
        return new GameContext(true, true, GameArea.HUB);
    }

    private static GameContext bedwars() {
        return new GameContext(true, false, GameArea.BEDWARS_LOBBY);
    }

    @Test
    void allowedInGarden() {
        assertTrue(ModuleActivationGuard.evaluate(new GardenModule(), garden()).isEmpty());
    }

    @Test
    void blockedOutsideGardenWithSpecificMessage() {
        Optional<String> reason = ModuleActivationGuard.evaluate(new GardenModule(), hub());
        assertTrue(reason.isPresent());
        assertEquals("Veltraph: Wart/Crops requires the Garden. Current location: Hub.", reason.get());
    }

    @Test
    void blockedOutsideSkyBlock() {
        Optional<String> reason = ModuleActivationGuard.evaluate(new GardenModule(), bedwars());
        assertTrue(reason.isPresent());
        assertTrue(reason.get().contains("requires Hypixel SkyBlock"));
    }

    @Test
    void startOutsideGardenEmitsChatErrorAndDoesNotEnable() {
        ModuleManager mm = new ModuleManager();
        GardenModule m = new GardenModule();
        mm.register(m);
        FixedGameContextService ctx = new FixedGameContextService(hub());
        FakeChatService chat = new FakeChatService();
        ModuleActivationGuard guard = new ModuleActivationGuard(mm, ctx, chat);

        boolean started = guard.toggle("garden_test");
        assertFalse(started);
        assertFalse(m.isEnabled());
        assertEquals(1, chat.messages.size());
        assertTrue(chat.last().contains("requires the Garden"));
    }

    @Test
    void startInGardenEnables() {
        ModuleManager mm = new ModuleManager();
        GardenModule m = new GardenModule();
        mm.register(m);
        FixedGameContextService ctx = new FixedGameContextService(garden());
        FakeChatService chat = new FakeChatService();
        ModuleActivationGuard guard = new ModuleActivationGuard(mm, ctx, chat);

        assertTrue(guard.toggle("garden_test"));
        assertTrue(m.isEnabled());
        assertTrue(chat.messages.isEmpty());
    }

    @Test
    void startBlocksWhenAnotherMacroIsAlreadyRunning() {
        ModuleManager mm = new ModuleManager();
        GardenModule first = new GardenModule("wart", "Wart/Crops");
        GardenModule second = new GardenModule("pumpkin", "Pumpkin/Melon");
        mm.register(first);
        mm.register(second);
        FixedGameContextService ctx = new FixedGameContextService(garden());
        FakeChatService chat = new FakeChatService();
        ModuleActivationGuard guard = new ModuleActivationGuard(mm, ctx, chat);

        assertTrue(guard.start(first));
        assertFalse(guard.start(second));
        assertTrue(first.isEnabled());
        assertFalse(second.isEnabled());
        assertTrue(chat.last().contains("stop Wart/Crops before starting Pumpkin/Melon"));
    }

    @Test
    void enforceRunningStopsModuleWhenLocationBecomesInvalid() {
        ModuleManager mm = new ModuleManager();
        GardenModule m = new GardenModule();
        mm.register(m);
        FixedGameContextService ctx = new FixedGameContextService(garden());
        FakeChatService chat = new FakeChatService();
        ModuleActivationGuard guard = new ModuleActivationGuard(mm, ctx, chat);

        guard.toggle("garden_test");
        assertTrue(m.isEnabled());

        // Player leaves the Garden.
        ctx.set(hub());
        guard.enforceRunning();
        assertFalse(m.isEnabled(), "module must auto-stop when location becomes invalid");
        assertTrue(chat.last().contains("stopped Wart/Crops"));
    }

    @Test
    void unknownLocationAppendsDebugHint() {
        GameContext unknown = new GameContext(true, true, GameArea.UNKNOWN);
        Optional<String> reason = ModuleActivationGuard.evaluate(new GardenModule(), unknown);
        assertTrue(reason.isPresent());
        assertTrue(reason.get().contains("Current location: Unknown"));
        assertTrue(reason.get().endsWith(ModuleActivationGuard.DEBUG_HINT),
                "unknown location must append the debug hint: " + reason.get());
    }

    @Test
    void knownLocationHasNoDebugHint() {
        Optional<String> reason = ModuleActivationGuard.evaluate(new GardenModule(), hub());
        assertTrue(reason.isPresent());
        assertFalse(reason.get().contains(ModuleActivationGuard.DEBUG_HINT));
    }
    @Test void ownedRunningExemptionNeverChangesStartRulesOrOtherControllerLocations(){
        var manager=new ModuleManager();var owner=new GardenModule("owner","Owner");var other=new GardenModule("other","Other");
        manager.register(owner);manager.register(other);var context=new FixedGameContextService(garden());
        var guard=new ModuleActivationGuard(manager,context,new FakeChatService());guard.start(owner);
        guard.runningLocationExemption((module,ctx)->module==owner);context.set(hub());other.setEnabled(true);
        guard.enforceRunning();assertTrue(owner.isEnabled());assertFalse(other.isEnabled());
        assertFalse(guard.canStart(owner));assertFalse(guard.start(other));
        guard.runningLocationExemption((module,ctx)->false);guard.enforceRunning();assertFalse(owner.isEnabled());
    }
    @Test void runningExemptionCannotBypassUnknownLimboOrNonSkyBlock(){
        for(var invalid:java.util.List.of(new GameContext(true,true,GameArea.UNKNOWN),new GameContext(true,false,GameArea.LIMBO),
                new GameContext(true,false,GameArea.HUB),new GameContext(false,true,GameArea.HUB))){
            var manager=new ModuleManager();var farm=new GardenModule();manager.register(farm);
            var context=new FixedGameContextService(garden());var guard=new ModuleActivationGuard(manager,context,new FakeChatService());
            guard.start(farm);guard.runningLocationExemption((module,ctx)->true);context.set(invalid);guard.enforceRunning();
            assertFalse(farm.isEnabled(),"Invalid context cannot inherit an owned exemption: "+invalid);
        }
    }
    @Test void deferredTransactionStillRunsStandardLocationAndReadiness(){
        var manager=new ModuleManager();var module=new GardenModule();manager.register(module);
        var context=new FixedGameContextService(hub());var gate=new ModuleActivationGuard(manager,context,new FakeChatService());
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        gate.activationTransaction((candidate,activation)->{attempts.incrementAndGet();return activation.getAsBoolean();});
        assertFalse(gate.start(module));assertFalse(module.isEnabled());assertEquals(1,attempts.get());
        context.set(garden());assertTrue(gate.start(module));assertEquals(2,attempts.get());
    }
}
