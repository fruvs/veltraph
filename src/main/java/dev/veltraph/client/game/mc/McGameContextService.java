package dev.veltraph.client.game.mc;

import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.GameContextResolver;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.game.LocationResolution;
import dev.veltraph.client.game.LocationSignals;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Minecraft-backed {@link GameContextService}. Gathers client-visible signals only — server brand,
 * server address, the scoreboard sidebar (title + decorated rows), and the player-list (tab) footer +
 * player display names — and feeds them to the pure {@link GameContextResolver}. No network calls. All
 * reads are defensive; any failure resolves to "not on Hypixel".
 *
 * <p>The tab footer is read via {@link PlayerListHudAccessor} (a read-only accessor mixin). If the
 * mixin is unavailable for any reason the footer is simply skipped — detection then relies on the
 * scoreboard + tab player lines.
 */
public final class McGameContextService implements GameContextService {

    /** Cap on tab player lines collected, to bound work and debug output. */
    private static final int MAX_TAB_LINES = 80;

    private final Minecraft client;
    private final dev.veltraph.client.game.LocationSignalDerivationCache derivation=new dev.veltraph.client.game.LocationSignalDerivationCache();

    private volatile LocationSignals lastSignals;
    private volatile LocationResolution lastResolution = LocationResolution.NONE;

    public McGameContextService(Minecraft client) {
        this.client = client;
    }

    @Override
    public GameContext current() {
        return resolution().context();
    }

    @Override
    public LocationResolution resolution() {
        LocationSignals s = collect();
        lastSignals = s;
        LocationResolution r = derivation.resolve(s);
        lastResolution = r;
        return r;
    }

    @Override
    public LocationSignals signals() {
        LocationSignals s = lastSignals;
        if (s == null) {
            resolution();
            s = lastSignals;
        }
        return s;
    }

    public final class GameplaySignalsSnapshot {
        private final McGameContextService owner=McGameContextService.this;
        private final net.minecraft.client.player.LocalPlayer player=client.player;
        private final Object world=client.level,connection=client.getConnection();
        private final LocationSignals signals;
        private GameplaySignalsSnapshot(LocationSignals signals){this.signals=signals;}
        /** Native SDK headers only, no context/provider/role/config/clock/codec callback. */
        public boolean current(){return client.isSameThread()&&owner==McGameContextService.this&&player==client.player&&world==client.level&&connection==client.getConnection()
            &&sameGameplaySignals(signals,collect());}
    }
    /** Physical native headers only; actual ordinary Root/Leg admission supplies gameplay permission. */
    public GameplaySignalsSnapshot ownedUseHeadersSnapshot(){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)return null;
        var actual=new GameplaySignalsSnapshot(collect());return actual.current()?actual:null;
    }
    public GameplaySignalsSnapshot gameplaySignalsSnapshot(){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)return null;
        var nativeSignals=collect();var resolved=derivation.resolve(nativeSignals).context();
        if(!resolved.onHypixel()||!resolved.inSkyBlock()||resolved.area()==dev.veltraph.client.game.GameArea.UNKNOWN||resolved.area()==dev.veltraph.client.game.GameArea.LIMBO)return null;
        var actual=new GameplaySignalsSnapshot(nativeSignals);return actual.current()?actual:null;
    }
    private static boolean sameGameplaySignals(LocationSignals a,LocationSignals b){return java.util.Objects.equals(a.serverBrand(),b.serverBrand())&&java.util.Objects.equals(a.serverAddress(),b.serverAddress())
        &&a.sidebarTitleRaw().equals(b.sidebarTitleRaw())&&a.sidebarRowsRaw().equals(b.sidebarRowsRaw())&&a.tabFooterRaw().equals(b.tabFooterRaw())&&a.tabLinesRaw().equals(b.tabLinesRaw());}

    /** Collect a fresh signal set from the live client; never throws. */
    private LocationSignals collect() {
        try {
            ClientPacketListener handler = client.getConnection();
            if (handler == null || client.level == null) {
                return LocationSignals.of(null, null, null, List.of(), null, List.of());
            }
            String brand = handler.serverBrand();
            String address = serverAddress();
            String sidebarTitle = sidebarTitle();
            List<String> rows = sidebarLines();
            String footer = tabFooter();
            List<String> tabLines = tabLines(handler);
            return derivation.signals(brand, address, sidebarTitle, rows, footer, tabLines);
        } catch (Throwable t) {
            return LocationSignals.of(null, null, null, List.of(), null, List.of());
        }
    }

    private String serverAddress() {
        ServerData info = client.getCurrentServer();
        return info != null ? info.ip : null;
    }

    private Objective sidebarObjective() {
        if (client.level == null) {
            return null;
        }
        Scoreboard scoreboard = client.level.getScoreboard();
        return scoreboard == null ? null : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    private String sidebarTitle() {
        Objective objective = sidebarObjective();
        return objective == null ? "" : objective.getDisplayName().getString();
    }

    /** Read sidebar rows, decorating each score holder with its team prefix/suffix (Hypixel style). */
    private List<String> sidebarLines() {
        List<String> out = new ArrayList<>();
        Objective objective = sidebarObjective();
        if (objective == null || client.level == null) {
            return out;
        }
        Scoreboard scoreboard = client.level.getScoreboard();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            String owner = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(owner);
            String line = owner;
            if (team != null) {
                line = team.getPlayerPrefix().getString() + owner + team.getPlayerSuffix().getString();
            }
            out.add(line);
        }
        return out;
    }

    /** Tab (player-list) footer text via the accessor mixin; null if unavailable. */
    private String tabFooter() {
        try {
            if (client.gui == null) {
                return null;
            }
            PlayerTabOverlay hud = client.gui.hud.getTabList();
            if (hud instanceof PlayerListHudAccessor accessor) {
                StringBuilder sb = new StringBuilder();
                Component header = accessor.getHeader();
                if (header != null) {
                    sb.append(header.getString());
                }
                Component footer = accessor.getFooter();
                if (footer != null) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(footer.getString());
                }
                return sb.isEmpty() ? null : sb.toString();
            }
        } catch (Throwable ignored) {
            // Accessor unavailable — fall through to scoreboard/player-line signals.
        }
        return null;
    }

    /** Tab player display names (or profile names) as fallback location signals. */
    private List<String> tabLines(ClientPacketListener handler) {
        List<String> out = new ArrayList<>();
        try {
            for (PlayerInfo entry : handler.getListedOnlinePlayers()) {
                if (out.size() >= MAX_TAB_LINES) {
                    break;
                }
                Component display = entry.getTabListDisplayName();
                if (display != null) {
                    out.add(display.getString());
                } else if (entry.getProfile() != null && entry.getProfile().name() != null) {
                    out.add(entry.getProfile().name());
                }
            }
        } catch (Throwable ignored) {
            // Player list unavailable — scoreboard signals still apply.
        }
        return out;
    }
}
