package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FishingConfig;
import dev.veltraph.client.fishing.SlugFishGuard;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Read-only Slug Fish native hook/tab adapter. It never writes pose, entities or received stats. */
final class McFishingSlugGuard {
    private final Minecraft client;
    private final ConfigService config;
    private final Supplier<GameArea> area;
    private final SlugFishGuard guard = new SlugFishGuard();
    private final String contract;
    private Object player, world;
    private String failure = "";
    private boolean enabled;

    McFishingSlugGuard(Minecraft client, ConfigService config, Supplier<GameArea> area) {
        this.client = client; this.config = config; this.area = area;
        contract = config.get(FishingConfig.FISHING_SLUG_PROFILE);
        enabled = config.get(FishingConfig.FISHING_SLUG_FISH);
        player = client.player; world = client.level;
    }
    static String problem(ConfigService cfg) {
        if (!cfg.get(FishingConfig.FISHING_SLUG_FISH)) return "";
        try { SlugFishGuard.Profile.parse(cfg.get(FishingConfig.FISHING_SLUG_PROFILE)); return ""; }
        catch (RuntimeException e) { return e.getMessage(); }
    }
    void observe() {
        if (!failure.isEmpty()) return;
        if (enabled != config.get(FishingConfig.FISHING_SLUG_FISH)
                || enabled && !Objects.equals(contract, config.get(FishingConfig.FISHING_SLUG_PROFILE))) {
            failure = "Slug Fish configuration changed during fishing"; return;
        }
        if (!enabled || area.get() != GameArea.CRIMSON_ISLE) { guard.reset(); return; }
        if (client.player != player || client.level != world || client.player == null || !client.player.isAlive()) {
            failure = "Slug Fish player or world changed"; return;
        }
        try {
            List<String> rows = client.gui.hud.getTabList() instanceof PlayerListHudAccessor accessor
                    ? accessor.veltraph$getPlayerInfos().stream().map(info -> info.getTabListDisplayName())
                    .filter(Objects::nonNull).map(component -> component.getString()).toList() : List.of();
            int seconds = SlugFishGuard.Profile.parse(contract).thresholdSeconds(rows);
            guard.observe(client.player.fishing, seconds, System.currentTimeMillis());
        } catch (RuntimeException e) { failure = "Slug Fish: " + e.getMessage(); }
    }
    boolean ready() { observe(); return failure.isEmpty() && (!enabled || area.get() != GameArea.CRIMSON_ISLE || guard.ready(System.currentTimeMillis())); }
    String failure() { return failure; }
    String status() { return !enabled ? "OFF" : area.get() != GameArea.CRIMSON_ISLE ? "AREA_GATE" : failure.isEmpty() ? "WAIT_MS=" + guard.remainingMillis(System.currentTimeMillis()) : "FAILED"; }
    void stop() { guard.reset(); enabled = false; }
    boolean rebindIdle() {
        if (!failure.isEmpty() || client.player == null || client.player.fishing != null) return false;
        player = client.player; world = client.level; guard.reset(); return true;
    }
}
