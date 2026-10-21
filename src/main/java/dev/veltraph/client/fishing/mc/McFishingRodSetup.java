package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.fishing.setup.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Startup selection uses native slot state only; it neither swaps inventory nor uses an item. */
final class McFishingRodSetup {
    private final Minecraft client;
    private final ConfigService config;
    private final String profileJson;
    private final boolean enabled;
    private Object player, world, connection;
    private RodSelectionProfile profile;
    private NativeStackIdentity.Snapshot original, selected;
    private int originalSlot = -1, rodSlot = -1;
    private boolean requested, ready, owned;private int manualSlot=-1;private NativeStackIdentity.Snapshot manualRod;
    private String failure = "";

    McFishingRodSetup(Minecraft client, ConfigService config) {
        this.client = client; this.config = config;
        enabled = config.get(FishingSetupConfig.FISHING_AUTO_ROD);
        profileJson = config.get(FishingSetupConfig.FISHING_ROD_PROFILE);
        if (!enabled) {
            if(client.player!=null&&client.level!=null&&client.getConnection()!=null){player=client.player;world=client.level;connection=client.getConnection();manualSlot=client.player.getInventory().getSelectedSlot();manualRod=identity(client.player.getMainHandItem());}
            ready = true; return;
        }
        try {
            profile = RodSelectionProfile.parse(profileJson);
            requireReady();
            player = client.player; world = client.level; connection = client.getConnection();
            rodSlot = choose(client, profile);
            if (rodSlot < 0) throw new IllegalArgumentException("No positive scored native rod in hotbar slots 1–8");
            originalSlot = client.player.getInventory().getSelectedSlot();
            original = identity(client.player.getInventory().getItem(originalSlot));
            selected = identity(client.player.getInventory().getItem(rodSlot));
        } catch (RuntimeException error) { fail(error.getMessage()); }
    }
    static String configurationError(Minecraft client, ConfigService config) {
        if (!config.get(FishingSetupConfig.FISHING_AUTO_ROD)) return "";
        try {
            var profile = RodSelectionProfile.parse(config.get(FishingSetupConfig.FISHING_ROD_PROFILE));
            requireReady(client);
            if (choose(client,profile) < 0) return "No positive scored native rod in hotbar slots 1–8";
            return "";
        } catch (RuntimeException error) { return "Rod setup: " + error.getMessage(); }
    }
    private static int choose(Minecraft client, RodSelectionProfile profile) {
        var center = client.player.blockPosition();
        boolean lava = RodSelector.lavaNearby((x,y,z) -> {
            var position = center.offset(x,y,z);
            if (!client.level.hasChunkAt(position)) return RodSelector.Block.UNLOADED;
            var state = client.level.getBlockState(position);
            if (state.is(net.minecraft.world.level.block.Blocks.WATER)) return RodSelector.Block.WATER;
            if (state.is(net.minecraft.world.level.block.Blocks.LAVA)) return RodSelector.Block.LAVA;
            return RodSelector.Block.OTHER;
        });
        var hotbar = new ArrayList<RodSelector.Candidate>();
        for (int slot = 0; slot < 8; slot++) {
            var stack = client.player.getInventory().getItem(slot);
            hotbar.add(new RodSelector.Candidate(slot,!stack.isEmpty() && stack.is(Items.FISHING_ROD),
                    stack.getHoverName().getString(),stack.hasFoil()));
        }
        return RodSelector.select(hotbar,lava,profile);
    }
    /** true means startup still owns the tick. A later tick must observe the selected native stack. */
    boolean tick() {
        if (!enabled) return false;
        if (!failure.isEmpty()) return true;
        try {
            if (enabled != config.get(FishingSetupConfig.FISHING_AUTO_ROD)
                    || !profileJson.equals(config.get(FishingSetupConfig.FISHING_ROD_PROFILE)))
                throw new IllegalArgumentException("Rod setup bindings changed during fishing");
            if (client.player != player || client.level != world || client.getConnection() != connection
                    || client.player == null || !client.player.isAlive())
                throw new IllegalArgumentException("Rod setup player/world/connection changed");
            if (ready) return false;
            requireReady();
            if (!matches(selected,rodSlot)) throw new IllegalArgumentException("Selected rod identity changed during setup");
            if (!requested) {
                if (client.player.getInventory().getSelectedSlot() != originalSlot || !matches(original,originalSlot))
                    throw new IllegalArgumentException("Manual slot or original item changed during setup");
                // Recheck the loaded scene and complete candidates before the only slot mutation.
                if (choose(client,profile) != rodSlot) throw new IllegalArgumentException("Rod candidates or pool changed during setup");
                dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,rodSlot);
                client.player.getInventory().setSelectedSlot(rodSlot);
                owned = originalSlot != rodSlot; requested = true;
                return true;
            }
            if (client.player.getInventory().getSelectedSlot() != rodSlot)
                throw new IllegalArgumentException("Manual slot selection interrupted rod setup");
            ready = true;
            return false;
        } catch (RuntimeException error) { fail(error.getMessage()); return true; }
    }
    boolean mayUseRod() {
        if (!enabled) return true;
        if (!ready || !failure.isEmpty()) return false;
        if (client.player == null || client.player.getInventory().getSelectedSlot() != rodSlot || !matches(selected,rodSlot)) {
            fail("Automatic fishing rod slot or native identity changed"); return false;
        }
        return true;
    }
    private void requireReady() { requireReady(client); }
    private static void requireReady(Minecraft client) {
        if (!client.isSameThread() || client.player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null || !client.player.isAlive()
                || client.gui.screen() != null || client.player.containerMenu != client.player.inventoryMenu
                || !client.player.containerMenu.getCarried().isEmpty() || client.player.fishing != null
                || client.player.isUsingItem())
            throw new IllegalArgumentException("Rod setup needs an idle native player, inventory and loaded world");
    }
    private NativeStackIdentity.Snapshot identity(ItemStack stack) {
        return NativeStackIdentity.capture(normalize(stack),client.level.registryAccess());
    }
    private static ItemStack normalize(ItemStack stack) {
        var copy = stack.copy();
        if (copy.is(Items.FISHING_ROD)) copy.remove(DataComponents.DAMAGE);
        return copy;
    }
    private boolean matches(NativeStackIdentity.Snapshot identity, int slot) {
        return client.player != null && client.level != null && identity != null
                && identity.matches(normalize(client.player.getInventory().getItem(slot)),client.level.registryAccess());
    }
    boolean rebindReady() {
        if(!enabled)return true;
        if(!ready() || client.player==null || client.level==null || client.getConnection()==null
                || client.player.getInventory().getSelectedSlot()!=rodSlot || !matches(selected,rodSlot)
                || !profileJson.equals(config.get(FishingSetupConfig.FISHING_ROD_PROFILE))
                || !config.get(FishingSetupConfig.FISHING_AUTO_ROD))return false;
        player=client.player;world=client.level;connection=client.getConnection();return true;
    }
    void stop() {
        // Restore only our slot, only to the still-identical original stack and same native context.
        if (owned && client.isSameThread() && client.player == player && client.level == world
                && client.getConnection() == connection && client.player != null && client.player.fishing == null
                && client.gui.screen() == null && client.player.containerMenu == client.player.inventoryMenu
                && client.player.containerMenu.getCarried().isEmpty()
                && client.player.getInventory().getSelectedSlot() == rodSlot
                && matches(selected,rodSlot) && matches(original,originalSlot)) {
            dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,originalSlot);
            client.player.getInventory().setSelectedSlot(originalSlot);
        }
        owned = false;
    }
    private void fail(String reason) { failure = "Rod setup: " + (reason == null ? "Native observation unavailable" : reason); }
    String failure() { return failure; }
    String status() { return !enabled ? "OFF" : !failure.isEmpty() ? "FAILED" : ready ? "READY" : requested ? "WAIT_SELECTION" : "SELECT"; }
    boolean ready() { return ready && failure.isEmpty(); }
    /** Startup identity remains original; an exact helper may lend selection but cannot replace this rod. */
    final class NativeCustody {
        private final Object originalPlayer=player,originalWorld=world,originalConnection=connection;
        private final RodSelectionProfile originalProfile=profile;private final NativeStackIdentity.Snapshot originalItem=original,selectedItem=selected,manualItem=manualRod;
        private final int originalIndex=originalSlot,selectedIndex=rodSlot,manualIndex=manualSlot;
        private final boolean originallyRequested=requested,originallyOwned=owned;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings=config instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot()
            :config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
        private NativeCustody(){}
        /** Registry codec comparisons run before the final original stored-state veto. */
        boolean nativeCurrent(){
            boolean semantic=enabled?selectedIndex>=0&&selectedIndex<9&&originalIndex>=0&&originalIndex<9
                &&matches(selectedItem,selectedIndex)&&matches(originalItem,originalIndex)
                :manualIndex>=0&&manualIndex<9&&matches(manualItem,manualIndex);
            return semantic&&rawCurrent();
        }
        /** No codec or semantic visitor: rechecked after every outer source visitor as well. */
        boolean rawCurrent(){return client.isSameThread()&&ready&&failure.isEmpty()&&settings!=null&&settings.current()
            &&player==originalPlayer&&world==originalWorld&&connection==originalConnection&&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==originalConnection
            &&profile==originalProfile&&original==originalItem&&selected==selectedItem&&manualRod==manualItem
            &&originalSlot==originalIndex&&rodSlot==selectedIndex&&manualSlot==manualIndex&&requested==originallyRequested&&owned==originallyOwned;}
    }
    NativeCustody captureNativeCustody(){var result=new NativeCustody();return result.nativeCurrent()?result:null;}
}
