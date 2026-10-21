package dev.veltraph.client.game;

import java.util.List;
import java.util.Locale;













public enum GameArea {
    ISLAND(true, "Your Island", "Private Island", "Garden Island"),
    GARDEN(true, "The Garden", "Garden"),
    LIMBO(false, "Limbo"),
    UNKNOWN(false),
    DUNGEON_HUB(true, "Dungeon Hub"),
    HUB(true, "Hub"),
    PROTOTYPE_LOBBY(false, "Prototype"),
    SKYWARS_LOBBY(false, "SkyWars"),
    BEDWARS_LOBBY(false, "Bed Wars", "BedWars"),
    MAIN_LOBBY(false, "Main Lobby"),
    THE_END(true, "The End"),
    THE_PARK(true, "The Park", "Park"),
    MUSHROOM_DESERT(true, "Mushroom Desert"),
    GOLD_MINE(true, "Gold Mine"),
    THE_BARN(true, "The Barn"),
    DEEP_CAVERNS(true, "Deep Caverns"),
    DWARVEN_MINES(true, "Dwarven Mines", "The Mist"),
    CRYSTAL_HOLLOWS(true, "Crystal Hollows"),
    SPIDERS_DEN(true, "Spider's Den", "Spiders Den"),
    CRIMSON_ISLE(true, "Crimson Isle"),
    WINTER_ISLAND(true, "Jerry's Workshop", "Winter Island"),
    THE_CATACOMBS(true, "The Catacombs", "Catacombs"),
    THE_RIFT(true, "The Rift"),
    SUMO_DUEL(false, "Sumo"),
    DUELS_LOBBY(false, "Duels"),
    BACKWATER_BAYOU(true, "Backwater Bayou"),
    LOTUS_ATOLL(true, "Lotus Atoll"),
    GALATEA(true, "Galatea");

    private final boolean skyBlock;
    private final List<String> aliases;

    GameArea(boolean skyBlock, String... aliases) {
        this.skyBlock = skyBlock;
        this.aliases = List.of(aliases);
    }

    /** Whether this is a SkyBlock gameplay area (false for lobbies, duels, limbo, unknown). */
    public boolean isSkyBlock() {
        return skyBlock;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** True if {@code text} contains one of this area's display aliases (case-insensitive). */
    public boolean matches(String text) {
        if (text == null || aliases.isEmpty()) {
            return false;
        }
        return matchesNormalized(text.toLowerCase(Locale.ROOT));
    }

    /**
     * True if {@code normalized} (already lowercased / sanitized via {@link LocationText#normalize})
     * contains one of this area's display aliases. Used by the resolver to avoid re-lowercasing.
     */
    public boolean matchesNormalized(String normalized) {
        if (normalized == null || normalized.isEmpty() || aliases.isEmpty()) {
            return false;
        }
        for (String alias : aliases) {
            if (normalized.contains(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Human-readable display name (Title Case of the enum name). */
    public String displayName() {
        String[] parts = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
