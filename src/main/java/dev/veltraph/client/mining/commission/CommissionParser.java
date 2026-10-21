package dev.veltraph.client.mining.commission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

/** Bounded read-only parsing. Unknown tasks remain visible; no unknown task becomes actionable. */
public final class CommissionParser {
    private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("(?:100(?:\\.0+)?|[0-9]{1,2}(?:\\.[0-9]+)?)%");
    private static final List<String> REGIONS = List.of("Upper Mines", "Royal Mines", "Lava Springs",
            "Cliffside Veins", "Rampart's Quarry");
    private static final List<String> GEMS = List.of("Topaz", "Amber", "Sapphire", "Jade", "Amethyst",
            "Ruby", "Aquamarine", "Peridot", "Onyx", "Citrine");
    private CommissionParser() {}
    public record Row(int slot, String visibleName, String itemId, List<String> lore) {
        public Row { lore = List.copyOf(lore); }
    }
    public static String plain(String text) {
        return text == null ? "" : FORMAT.matcher(text).replaceAll("").replace('\u00a0', ' ').trim();
    }
    private static Task task(String name, double percent, boolean known, boolean claimable, int slot) {
        String n = plain(name), region = "", resource = ""; Work work = Work.UNSUPPORTED;
        for (String r : REGIONS) if (n.equals(r + " Mithril") || n.equals(r + " Titanium")) {
            region = r; work = n.endsWith("Titanium") ? Work.TITANIUM : Work.MITHRIL;
        }
        if (n.equals("Mithril Miner")) work = Work.MITHRIL;
        if (n.equals("Titanium Miner")) work = Work.TITANIUM;
        if (n.equals("Goblin Slayer") || n.equals("Mines Slayer") || n.equals("Glacite Walker Slayer")) {
            work = Work.MOB; resource = n;
        }
        for (String r : List.of("Glacite", "Umber", "Tungsten"))
            if (n.equals(r + " Collector")) { work = Work.valueOf(r.toUpperCase(Locale.ROOT)); resource = r; region = "Glacite Tunnels"; }
        for (String gem : GEMS) if (n.equals(gem + " Gemstone Collector")) {
            work = Work.GEMSTONE; resource = gem; region = "Glacite Tunnels";
        }
        return new Task(n, work, region, resource, percent, known, claimable, slot);
    }
    private static Optional<Double> percentage(String value) {
        String v = plain(value);
        if (!NUMBER.matcher(v).matches()) return Optional.empty();
        return Optional.of(Double.parseDouble(v.substring(0, v.length() - 1)));
    }
    public static CommissionObservation tab(String context, long serial, long now, List<String> lines) {
        if (lines.size() > 256) throw new IllegalArgumentException("Oversized tab snapshot");
        List<Task> tasks = new ArrayList<>(); boolean section = false, ended = false;
        for (String raw : lines) {
            String line = plain(raw);
            if (!section) { if (line.equals("Commissions:")) section = true; continue; }
            if (line.isEmpty()) { ended = true; break; }
            int delimiter = line.lastIndexOf(": ");
            if (delimiter < 1) { tasks.add(task(line, 0, false, false, -1)); }
            else {
                String value = line.substring(delimiter + 2);
                boolean done = value.equals("DONE");
                var p = percentage(value);
                tasks.add(task(line.substring(0, delimiter), done ? 100 : p.orElse(0.0), done || p.isPresent(), false, -1));
            }
            if (tasks.size() > 16) throw new IllegalArgumentException("Too many commissions");
        }
        return new CommissionObservation(context, serial, now, tasks, section && ended);
    }
    /** Qualified identity comes from the caller's real current menu, never a substring title match. */
    public static CommissionObservation menu(String context, long serial, long now, String title,
                                              boolean completeSnapshot, List<Row> rows) {
        if (rows.size() > 90) throw new IllegalArgumentException("Oversized menu snapshot");
        List<Task> tasks = new ArrayList<>();
        if (!plain(title).equals("Commissions")) return new CommissionObservation(context, serial, now, tasks, false);
        for (Row row : rows) {
            if (!plain(row.visibleName()).matches("Commission #[1-9][0-9]?")) continue;
            if (!row.itemId().equals("minecraft:writable_book") || row.slot() < 0 || row.lore().size() > 128) {
                completeSnapshot = false; continue;
            }
            List<String> lore = row.lore().stream().map(CommissionParser::plain).toList();
            int blank = lore.indexOf("");
            if (blank < 0 || blank + 1 >= lore.size() || lore.get(blank + 1).isEmpty()) { completeSnapshot = false; continue; }
            String name = lore.get(blank + 1);
            int progress = lore.indexOf("Progress");
            Optional<Double> p = progress >= 0 && progress + 1 < lore.size() ? percentage(lore.get(progress + 1)) : Optional.empty();
            // The current supplied menus contain no completed claim row. Exact legacy completion lore
            // remains an observation only; the native claim adapter must independently qualify it.
            boolean completed = lore.stream().anyMatch(v -> v.equals("COMPLETED") || v.equals("Completed"));
            tasks.add(task(name, completed ? 100 : p.orElse(0.0), completed || p.isPresent(), completed, row.slot()));
            if (tasks.size() > 16) throw new IllegalArgumentException("Too many commissions");
        }
        return new CommissionObservation(context, serial, now, tasks, completeSnapshot);
    }
}
