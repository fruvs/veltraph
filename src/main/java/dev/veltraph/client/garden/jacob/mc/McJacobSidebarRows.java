package dev.veltraph.client.garden.jacob.mc;

import dev.veltraph.client.game.LocationText;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;
import java.util.Comparator;
import java.util.List;

/** Exact ordered native sidebar ingress for the source's adjacent Jacob crop/countdown row. */
public final class McJacobSidebarRows {
    /** Actual 26.3 Hud.displayScoreboardSidebar display bound; the stable source limit stays opaque. */
    public static final int NATIVE_DISPLAY_LIMIT=15;
    private static final Comparator<PlayerScoreEntry> SOURCE_ORDER=Comparator.comparingInt(PlayerScoreEntry::value)
        .reversed().thenComparing(PlayerScoreEntry::owner,String.CASE_INSENSITIVE_ORDER);
    private McJacobSidebarRows() {}
    public static List<String> read(Minecraft client){
        if(client==null||client.level==null)return List.of();
        var scoreboard=client.level.getScoreboard();
        return read(scoreboard,scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR));
    }
    /** Reads the actual native scores; does not sort already-parsed text or manufacture a timer. */
    public static List<String> read(Scoreboard scoreboard,Objective objective){
        if(scoreboard==null||objective==null||objective.getScoreboard()!=scoreboard)return List.of();
        return scoreboard.listPlayerScores(objective).stream().filter(entry->!entry.isHidden())
            .sorted(SOURCE_ORDER).limit(NATIVE_DISPLAY_LIMIT)
            .map(entry->PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()),entry.ownerName()).getString())
            .map(LocationText::stripColorCodes).map(String::trim).toList();
    }
}
