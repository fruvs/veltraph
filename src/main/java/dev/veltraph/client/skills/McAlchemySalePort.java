package dev.veltraph.client.skills;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.selling.*;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Requests use actual native player/world/connection, menu objects, revisions, slots and cursor contents. */
final class McAlchemySalePort implements AlchemySaleParent.Port {
    private final Minecraft client;
    private final SellingMenuAccess access;
    private Object predictedCookieMenu;
    private ItemStack predictedCookie = ItemStack.EMPTY;
    private int previousSelection = -1;
    private ItemStack previousSelectionItem = ItemStack.EMPTY;

    McAlchemySalePort(Minecraft client) {
        this.client = client;
        access = new SellingMenuAccess(client);
    }

    void reset(){access.clear();predictedCookieMenu=null;predictedCookie=ItemStack.EMPTY;previousSelection=-1;previousSelectionItem=ItemStack.EMPTY;}

    private boolean cookie(AlchemySaleProfile profile) {
        if (!(client.gui.hud.getTabList() instanceof PlayerListHudAccessor footer)) return false;
        String text = footer.getFooter() == null ? "" : footer.getFooter().getString();
        var tab = footer.veltraph$getPlayerInfos().stream().map(info -> info.getTabListDisplayName())
                .filter(Objects::nonNull).map(component -> MenuSnapshot.clean(component.getString()))
                .filter(s -> !s.isBlank()).toList();
        return CookieEvidence.active(text, tab, profile.activeCookieLine(), profile.cookieGrammar());
    }

    @Override public AlchemySaleParent.Observation read(AlchemySaleProfile profile) {
        if (!client.isSameThread()) throw new IllegalStateException("Alchemy sale requires client thread");
        var player = client.player;
        boolean gameplay = player != null && client.gui.screen() == null && player.containerMenu == player.inventoryMenu;
        boolean ownPrediction = player != null && player.containerMenu == predictedCookieMenu
                && !predictedCookie.isEmpty() && ItemStack.matches(predictedCookie, player.containerMenu.getCarried());
        return new AlchemySaleParent.Observation(
                new AlchemySaleParent.Scope(player, client.level, client.getConnection()),
                player == null ? null : player.containerMenu, access.read(profile.pursePrefix()),
                gameplay, cookie(profile), player == null ? -1 : player.getInventory().getSelectedSlot(), ownPrediction);
    }

    private boolean same(AlchemySaleParent.Observation expected, AlchemySaleParent.Observation current) {
        return expected.scope().same(current.scope()) && expected.nativeMenu() == current.nativeMenu()
                && expected.sale().equals(current.sale()) && expected.gameplay() == current.gameplay()
                && current.cookieActive() && expected.selectedSlot() == current.selectedSlot();
    }

    @Override public boolean openSkyblock(AlchemySaleParent.Observation expected, AlchemySaleProfile profile) {
        var fresh = read(profile);
        if (!same(expected, fresh) || !fresh.gameplay() || !fresh.sale().cursorEmpty()
                || client.gameMode == null || !client.player.isAlive()) return false;
        var opener = client.player.getInventory().getItem(profile.openerSlot());
        if (opener.isEmpty() || !AlchemyFlowMenus.same(AlchemyFlowMenus.identity(opener), profile.opener())) return false;
        if (previousSelection < 0) {
            previousSelection = client.player.getInventory().getSelectedSlot();
            previousSelectionItem = client.player.getInventory().getItem(previousSelection).copy();
        }
        client.player.getInventory().setSelectedSlot(profile.openerSlot());
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        return true;
    }

    @Override public boolean openCookie(AlchemySaleParent.Observation expected, AlchemySaleProfile profile) {
        var fresh = read(profile);
        if (!same(expected, fresh) || !fresh.sale().cursorEmpty() || fresh.sale().menu() == null
                || !fresh.sale().menu().title().equals(profile.skyblockTitle()) || client.gameMode == null) return false;
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu() != client.player.containerMenu) return false;
        var menu = client.player.containerMenu;
        if (profile.cookieSlot() >= menu.slots.size()) return false;
        var slot = menu.slots.get(profile.cookieSlot());
        var item = slot.getItem();
        var lore = item.get(DataComponents.LORE);
        if (slot.container == client.player.getInventory() || item.isEmpty()
                || !AlchemyFlowMenus.same(AlchemyFlowMenus.identity(item), profile.cookieButton()) || lore == null
                || !lore.lines().stream().map(c -> MenuSnapshot.clean(c.getString())).toList().equals(profile.cookieLore())) return false;
        client.gameMode.handleContainerInput(menu.containerId, profile.cookieSlot(), 0, ContainerInput.PICKUP, client.player);
        predictedCookieMenu = menu;
        predictedCookie = menu.getCarried().copy();
        return true;
    }

    @Override public boolean act(SellingRuntime.Action action, SellingRule rule, AlchemySaleProfile profile) {
        return access.act(action, rule, profile.pursePrefix());
    }

    @Override public void closeOwned(Object menu, AlchemySaleProfile profile) {
        var current = read(profile);
        if (current.nativeMenu() == menu && current.sale().menu() != null && current.sale().cursorEmpty()) {
            access.closeOwned(current.sale().menu().id(), current.sale().menu().title());
        }
    }

    @Override public void restoreSelectedSlot(int previous, int issued) {
        if (client.player != null && previousSelection == previous && client.player.getInventory().getSelectedSlot() == issued
                && ItemStack.matches(previousSelectionItem, client.player.getInventory().getItem(previous))) {
            client.player.getInventory().setSelectedSlot(previous);
            if (client.getConnection() != null) client.getConnection().send(new ServerboundSetCarriedItemPacket(previous));
        }
        previousSelection = -1;
        previousSelectionItem = ItemStack.EMPTY;
    }
}
