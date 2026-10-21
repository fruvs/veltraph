package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerTest {
    @Test void globalStopHookRunsEvenWhenNoModuleIsEnabled(){
        var manager=new ModuleManager();var cancels=new java.util.concurrent.atomic.AtomicInteger();manager.beforeStopAll(cancels::incrementAndGet);
        manager.stopAll();manager.stopControllers();assertEquals(2,cancels.get());
    }

    /** Minimal countable module for manager tests. */
    private static final class CountingModule extends AbstractModule {
        int ticks;
        int enables;
        int disables;
        boolean throwOnTick;

        CountingModule(String id) {
            super(id, id, ModuleCategory.INTERNAL);
        }

        @Override
        public void onEnable() {
            enables++;
        }

        @Override
        public void onDisable() {
            disables++;
        }

        @Override
        public void onTick() {
            ticks++;
            if (throwOnTick) {
                throw new RuntimeException("boom");
            }
        }
    }

    @Test
    void registerToggleAndLookup() {
        ModuleManager mm = new ModuleManager();
        CountingModule a = new CountingModule("a");
        mm.register(a);

        assertTrue(mm.get("a").isPresent());
        assertEquals(1, mm.all().size());
        assertEquals(1, mm.byCategory(ModuleCategory.INTERNAL).size());

        assertTrue(mm.enable("a"));
        assertTrue(a.isEnabled());
        assertEquals(MacroState.RUNNING, a.state());
        assertEquals(1, a.enables);

        assertTrue(mm.toggle("a"));
        assertFalse(a.isEnabled());
        assertEquals(MacroState.STOPPED, a.state());
        assertEquals(1, a.disables);
    }

    @Test
    void tickOnlyDrivesEnabledModules() {
        ModuleManager mm = new ModuleManager();
        CountingModule a = new CountingModule("a");
        CountingModule b = new CountingModule("b");
        mm.register(a);
        mm.register(b);
        mm.enable("a");

        mm.tick();
        assertEquals(1, a.ticks);
        assertEquals(0, b.ticks);
    }

    @Test
    void stopAllDisablesEveryEnabledModule() {
        ModuleManager mm = new ModuleManager();
        CountingModule a = new CountingModule("a");
        CountingModule b = new CountingModule("b");
        mm.register(a);
        mm.register(b);
        mm.enable("a");
        mm.enable("b");

        mm.stopAll();
        assertFalse(a.isEnabled());
        assertFalse(b.isEnabled());
        assertEquals(1, a.disables);
        assertEquals(1, b.disables);
    }

    @Test
    void tickExceptionDisablesOffendingModule() {
        ModuleManager mm = new ModuleManager();
        CountingModule a = new CountingModule("a");
        a.throwOnTick = true;
        mm.register(a);
        mm.enable("a");

        mm.tick(); // must not propagate
        assertFalse(a.isEnabled(), "module should be disabled after throwing");
        assertEquals(1, a.disables, "onDisable should run to release inputs");
    }

    @Test
    void duplicateIdIsRejected() {
        ModuleManager mm = new ModuleManager();
        mm.register(new CountingModule("dup"));
        assertThrows(IllegalArgumentException.class, () -> mm.register(new CountingModule("dup")));
    }
    @Test void ownedStopCallbackCannotToggleDisabledOwnerBackOn(){
        var manager=new ModuleManager();var farm=new CountingModule("farm");var helper=new CountingModule("helper");
        manager.register(farm);manager.register(helper);farm.setEnabled(true);helper.setEnabled(true);
        manager.beforeDisable(module->{farm.setEnabled(false);helper.setEnabled(false);});
        manager.toggle("helper");assertFalse(farm.isEnabled());assertFalse(helper.isEnabled());assertEquals(1,helper.enables);
    }
    @Test void leasedInputOwnerMustBeTheRegisteredEnabledController(){
        var manager=new ModuleManager();var farm=new CountingModule("farm");var helper=new CountingModule("helper");
        manager.register(farm);manager.register(helper);farm.setEnabled(true);helper.setEnabled(true);
        manager.leasedInputOwner(()->helper);assertEquals(helper,manager.inputOwner().orElseThrow());
        helper.setEnabled(false);assertEquals(farm,manager.inputOwner().orElseThrow());
        manager.leasedInputOwner(()->new CountingModule("farm"));assertEquals(farm,manager.inputOwner().orElseThrow());
    }
}
