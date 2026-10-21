package dev.veltraph.client.mining.tool;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Passive observations only. Neither a menu label nor a nominal cooldown grants action authority. */
public final class MiningToolEvidence {
    private MiningToolEvidence() {}
    public enum Ability {
        MINING_SPEED_BOOST("Mining Speed Boost"), PICKOBULUS("Pickobulus"),
        TUNNEL_VISION("Tunnel Vision"), MANIAC_MINER("Maniac Miner"),
        GEMSTONE_INFUSION("Gemstone Infusion"), SHEER_FORCE("Sheer Force");
        private final String label;
        Ability(String label) { this.label = label; }
        public String label() { return label; }
        public static Optional<Ability> named(String label) {
            for (Ability ability : values()) if (ability.label.equals(label)) return Optional.of(ability);
            return Optional.empty();
        }
    }
    /** Adapter must obtain actual objects and full current component/content identities.
     * toolGeneration advances on every held-slot or loadout/component custody change,
     * including A-to-B-to-A, and never resets within the same activation.
     */
    public record Stamp(Object player, Object world, Object connection, Object owner,
                        long activation, long policyRevision, long toolGeneration, long observedNanos,
                        int heldSlot, String toolComponents) {
        public Stamp {
            if (player == null || world == null || connection == null || owner == null
                    || activation < 1 || policyRevision < 0 || toolGeneration < 1 || observedNanos < 0
                    || heldSlot < 0 || heldSlot > 8 || toolComponents == null || toolComponents.isBlank())
                throw new IllegalArgumentException("Incomplete mining tool observation");
        }
        public boolean currentFor(Stamp current, long nowNanos, long maxAgeNanos) {
            return current != null && maxAgeNanos >= 0 && nowNanos >= observedNanos
                    && nowNanos - observedNanos <= maxAgeNanos
                    && current.observedNanos <= nowNanos && current.observedNanos >= observedNanos
                    && player == current.player && world == current.world
                    && connection == current.connection && owner == current.owner
                    && activation == current.activation && policyRevision == current.policyRevision
                    && toolGeneration == current.toolGeneration
                    && heldSlot == current.heldSlot && toolComponents.equals(current.toolComponents);
        }
    }
    public record Row(int slot, String name, List<String> lore, String components) {
        public Row {
            if (slot < 0 || name == null || lore == null || components == null || components.isBlank())
                throw new IllegalArgumentException("Incomplete mining menu row");
            lore = List.copyOf(lore);
            if (lore.size() > 128 || name.length() > 512 || components.length() > 65536
                    || lore.stream().anyMatch(line -> line.length() > 4096))
                throw new IllegalArgumentException("Oversize mining menu row");
        }
    }
    /** Producer epoch advances on every menu/page replacement, including reuse of a handle;
     * contentIdentity is the entire current menu content, not its id or revision.
     */
    public record Menu(Stamp stamp, Object handle, long epoch, int containerId, int revision,
                       String title, String contentIdentity, boolean complete, List<Row> rows) {
        public Menu {
            if (stamp == null || handle == null || epoch < 0 || containerId < 0 || revision < 0
                    || title == null || contentIdentity == null || contentIdentity.isBlank() || rows == null)
                throw new IllegalArgumentException("Incomplete mining menu observation");
            rows = List.copyOf(rows);
            if (rows.size() > 128) throw new IllegalArgumentException("Oversize mining menu");
        }
        public boolean currentFor(Menu current, long nowNanos, long maxAgeNanos) {
            return complete && current != null && current.complete
                    && stamp.currentFor(current.stamp, nowNanos, maxAgeNanos)
                    && handle == current.handle && epoch == current.epoch && containerId == current.containerId
                    && revision == current.revision && title.equals(current.title)
                    && contentIdentity.equals(current.contentIdentity) && rows.equals(current.rows);
        }
    }
    public record SelectedAbility(Ability ability, int slot, Optional<BigDecimal> nominalCooldownSeconds,
                                  String rowComponents) {}
    public record Fuel(long remaining, long capacity) {
        public Fuel {
            if (remaining < 0 || capacity <= 0 || remaining > capacity)
                throw new IllegalArgumentException("Invalid fuel ratio");
        }
    }
    private static final String NUMBER = "(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?";
    private static final Pattern FUEL = Pattern.compile("Fuel: (" + NUMBER + "[kK]?)/(" + NUMBER + "[kK]?)");
    private static final Pattern SPEED = Pattern.compile("Mining Speed:? (" + NUMBER + ")");
    private static final Pattern COOLDOWN = Pattern.compile("Cooldown: (" + NUMBER + ")s");
    private static final Pattern BOOST = Pattern.compile("Grants \\+(" + NUMBER + ")% (?:[\\p{Co}] )?Mining Speed(?: for)?");

    /** Exact explicit SELECTED is mandatory; missing or competing selected rows are unknown. */
    public static Optional<SelectedAbility> selectedAbility(Menu menu) {
        if (!qualified(menu, "Heart of the Mountain")) return Optional.empty();
        SelectedAbility selected = null;
        Set<Integer> slots = new HashSet<>();
        for (Row row : menu.rows) {
            if (!slots.add(row.slot)) return Optional.empty();
            long selectedMarkers = row.lore.stream().map(MiningToolEvidence::plain).filter("SELECTED"::equals).count();
            if (selectedMarkers == 0) continue;
            if (selectedMarkers != 1) return Optional.empty();
            Optional<Ability> ability = Ability.named(plain(row.name));
            if (ability.isEmpty() || selected != null
                    || row.lore.stream().map(MiningToolEvidence::plain)
                        .filter(line -> line.startsWith("Pickaxe Ability: ")).count() != 1
                    || row.lore.stream().map(MiningToolEvidence::plain)
                        .noneMatch(line -> line.equals("Pickaxe Ability: " + ability.get().label())))
                return Optional.empty();
            selected = new SelectedAbility(ability.get(), row.slot,
                    uniqueNumber(row.lore, COOLDOWN, Field.COOLDOWN), row.components);
        }
        return Optional.ofNullable(selected);
    }
    public static Optional<BigDecimal> miningSpeed(Menu menu) {
        if (!qualified(menu, "Your Equipment and Stats")) return Optional.empty();
        List<Row> stats = menu.rows.stream().filter(row -> plain(row.name).equals("Mining Stats")).toList();
        return stats.size() == 1 ? uniqueNumber(stats.getFirst().lore, SPEED, Field.SPEED) : Optional.empty();
    }
    public static Optional<BigDecimal> nominalSpeedBoost(Menu menu) {
        if (!qualified(menu, "Heart of the Mountain")) return Optional.empty();
        List<Row> rows = menu.rows.stream().filter(row -> plain(row.name).equals("Mining Speed Boost")).toList();
        return rows.size() == 1 ? uniqueNumber(rows.getFirst().lore, BOOST, Field.BOOST) : Optional.empty();
    }
    public static Optional<Fuel> fuel(List<String> lore) {
        if (!bounded(lore)) return Optional.empty();
        Fuel result = null;
        for (String line : lore) {
            String text = plain(line);
            if (!text.startsWith("Fuel:")) continue;
            var matcher = FUEL.matcher(text);
            if (!matcher.matches() || result != null) return Optional.empty();
            try { result = new Fuel(integralFuel(matcher.group(1)), integralFuel(matcher.group(2))); }
            catch (ArithmeticException | IllegalArgumentException ex) { return Optional.empty(); }
        }
        return Optional.ofNullable(result);
    }
    private static long integralFuel(String text) {
        boolean kilo = text.endsWith("k") || text.endsWith("K");
        BigDecimal value = new BigDecimal((kilo ? text.substring(0, text.length() - 1) : text).replace(",", ""));
        return (kilo ? value.movePointRight(3) : value).longValueExact();
    }
    private enum Field { SPEED, COOLDOWN, BOOST }
    private static boolean claims(String line, Field field) {
        return switch (field) {
            case SPEED -> line.equals("Mining Speed") || line.startsWith("Mining Speed:") || line.startsWith("Mining Speed ");
            case COOLDOWN -> line.equals("Cooldown") || line.startsWith("Cooldown:") || line.startsWith("Cooldown ");
            case BOOST -> line.startsWith("Grants ") && line.contains("Mining Speed");
        };
    }
    private static Optional<BigDecimal> uniqueNumber(List<String> lore, Pattern pattern, Field field) {
        if (!bounded(lore)) return Optional.empty();
        BigDecimal found = null;
        for (String line : lore) {
            String text = plain(line);
            if (!claims(text, field)) continue;
            var matcher = pattern.matcher(text);
            if (!matcher.matches()) return Optional.empty();
            if (found != null) return Optional.empty();
            try {
                found = new BigDecimal(matcher.group(1).replace(",", ""));
                if (found.signum() < 0) return Optional.empty();
            } catch (NumberFormatException ex) { return Optional.empty(); }
        }
        return Optional.ofNullable(found);
    }
    private static boolean qualified(Menu menu, String title) {
        if (menu == null || !menu.complete || !plain(menu.title).equals(title)) return false;
        Set<Integer> slots = new HashSet<>();
        return menu.rows.stream().allMatch(row -> slots.add(row.slot));
    }
    private static boolean bounded(List<String> lore) {
        return lore != null && lore.size() <= 128
                && lore.stream().allMatch(line -> line != null && line.length() <= 4096);
    }
    /** Retains Unicode stat glyphs; strips only paired legacy formatting codes. */
    private static String plain(String text) {
        return text.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
