package dev.veltraph.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.farming.module.CactusFarmModule;
import dev.veltraph.client.farming.module.WartCropsFarmModule;
import dev.veltraph.client.ui.VeltraphScreens;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keybindings. The binds are <b>unbound by default</b> and appear in vanilla Controls under the Misc
 * category; the tester assigns keys there, so nothing conflicts out of the box.
 *
 * <p>The "open menu" keybind opens the <b>Automations</b> UI only (Settings is reached from the
 * Automations tab, the pause-menu button, or {@code /veltraph}). Module toggles route through the
 * shared {@link ModuleActivationGuard} so a wrong-location start is blocked with a chat error.
 */
public final class VeltraphKeybinds {

    private static final Logger LOGGER = LoggerFactory.getLogger("Veltraph");

    private static KeyMapping openAutomations;
    private static KeyMapping toggleCactus;
    private static KeyMapping toggleWartCrops;
    private static KeyMapping stopAll;
    private record MenuShortcut(KeyMapping key,String moduleId) {}
    private static final List<MenuShortcut> menuShortcuts=new ArrayList<>();
    private static ModuleActivationGuard menuGuard;
    private static final Set<KeyMapping> menuHeld=new HashSet<>();

    private VeltraphKeybinds() {
    }

    /** Register the keybindings. Call once during client init. */
    public static void register() {
        int unbound = InputConstants.UNKNOWN.getValue();
        openAutomations = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.veltraph.open_menu", unbound, KeyMapping.Category.MISC));
        toggleCactus = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.veltraph.toggle_cactus", unbound, KeyMapping.Category.MISC));
        toggleWartCrops = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.veltraph.toggle_wart_crops", unbound, KeyMapping.Category.MISC));
        registerMenuShortcut("key.veltraph.serve_visitor",dev.veltraph.client.garden.visitor.mc.McVisitorServeModule.ID,unbound);
        registerMenuShortcut("key.veltraph.sell_bazaar_inventory",dev.veltraph.client.inventory.BazaarAutoSellModule.ID,unbound);
        registerMenuShortcut("key.veltraph.alchemy_prepared_brew",dev.veltraph.client.skills.AlchemyModule.ID,unbound);
        registerMenuShortcut("key.veltraph.carpentry_inventory_craft",dev.veltraph.client.skills.CarpentryModule.ID,unbound);
        registerMenuShortcut("key.veltraph.bottle_buy_store",dev.veltraph.client.skills.BottleStoreModule.ID,unbound);
        stopAll = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.veltraph.stop_all", unbound, KeyMapping.Category.MISC));
        // Container screens do not populate gameplay key click queues. Route their actual native
        // key callbacks instead, retaining the screen for each owner's pre-start/menu checks.
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
            if(!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((current,key)->{
                var shortcut=matchingMenuShortcut(key);
                if(shortcut==null) return true;
                drain(shortcut.key());
                if(menuHeld.add(shortcut.key())) {
                    var gate=menuGuard==null?VeltraphBootstrap.guard():menuGuard;
                    if(gate!=null) gate.toggle(shortcut.moduleId());
                }
                return false;
            });
            ScreenKeyboardEvents.allowKeyRelease(screen).register((current,key)->{
                var shortcut=matchingMenuShortcut(key);
                if(shortcut==null) return true;
                menuHeld.remove(shortcut.key());drain(shortcut.key());return false;
            });
            ScreenEvents.remove(screen).register(current->{menuHeld.clear();menuShortcuts.forEach(s->drain(s.key()));});
        });
    }

    private static void registerMenuShortcut(String label,String moduleId,int unbound) {
        menuShortcuts.add(new MenuShortcut(KeyMappingHelper.registerKeyMapping(new KeyMapping(label,unbound,KeyMapping.Category.MISC)),moduleId));
    }
    private static MenuShortcut matchingMenuShortcut(KeyEvent key) {
        return menuShortcuts.stream().filter(s->s.key().matches(key)).findFirst().orElse(null);
    }

    private static void drain(KeyMapping key) { while(key.consumeClick()) {} }
    private static boolean consumeOnce(KeyMapping key) {
        boolean clicked=key.consumeClick();drain(key);return clicked&&!menuHeld.contains(key);
    }

    /** Poll the keybindings once per client tick and apply their actions through the guard. */
    public static void poll(ModuleActivationGuard guard) {
        menuGuard=guard;
        if (openAutomations == null) {
            return;
        }
        while (openAutomations.consumeClick()) {
            // Menu keybind opens Automations only.
            Minecraft.getInstance().gui.setScreen(VeltraphScreens.automations(guard));
        }
        while (stopAll.consumeClick()) {
            VeltraphBootstrap.moduleManager().stopAll();
            LOGGER.info("[Veltraph] Stop-all keybind: disabled all modules.");
        }
        for(var shortcut:menuShortcuts) if(consumeOnce(shortcut.key())) guard.toggle(shortcut.moduleId());
        while (toggleCactus.consumeClick()) {
            guard.toggle(CactusFarmModule.ID);
        }
        while (toggleWartCrops.consumeClick()) {
            guard.toggle(WartCropsFarmModule.ID);
        }
    }
}
