package dev.veltraph.client.garden.pests;

import dev.veltraph.client.game.LocationText;
import java.util.List;

/** Only the source-recovered exact active-sidebar header qualifies; menus/upcoming rows do not. */
public final class GardenContestSignal {
    private GardenContestSignal() {}
    public static boolean active(List<String> sidebar){return sidebar!=null&&sidebar.stream().anyMatch(s->"Jacob's Contest".equals(LocationText.stripColorCodes(s).trim()));}
}
