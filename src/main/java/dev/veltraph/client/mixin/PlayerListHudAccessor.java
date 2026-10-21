package dev.veltraph.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.multiplayer.PlayerInfo;
import java.util.List;

/**
 * Read-only accessor for the player-list (tab) overlay's footer/header text, which has no public
 * getter in vanilla. Veltraph uses the tab footer as a <i>fallback</i> location signal — mirroring the
 * original mod's tab-overlay accessor concept — when the scoreboard alone doesn't identify the area.
 *
 * <p>This is a pure accessor mixin (no behavioural injection): it only exposes existing private fields.
 */
@Mixin(PlayerTabOverlay.class)
public interface PlayerListHudAccessor {

    /** Current native sorted/tab-limited rows, preserving the source neighbor grammar. */
    @Invoker("getPlayerInfos")
    List<PlayerInfo> veltraph$getPlayerInfos();

    @Accessor("footer")
    Component getFooter();

    @Accessor("header")
    Component getHeader();
}
