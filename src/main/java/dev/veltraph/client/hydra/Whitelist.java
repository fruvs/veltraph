package dev.veltraph.client.hydra;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;










public final class Whitelist {

    private final Set<String> names; // normalized (lower-cased, trimmed)

    private Whitelist(Set<String> names) {
        this.names = names;
    }

    /** Parse a comma-joined whitelist string (null/blank → empty). Names are trimmed + lower-cased. */
    public static Whitelist parse(String commaJoined) {
        Set<String> out = new LinkedHashSet<>();
        if (commaJoined != null) {
            for (String raw : commaJoined.split(",")) {
                String n = raw.trim();
                if (!n.isEmpty()) {
                    out.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new Whitelist(out);
    }

    /** An empty whitelist (everyone triggers the alert). */
    public static Whitelist empty() {
        return new Whitelist(new LinkedHashSet<>());
    }

    /** True if {@code name} (any case) is whitelisted. Null/blank names are never whitelisted. */
    public boolean contains(String name) {
        if (name == null) {
            return false;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        return !n.isEmpty() && names.contains(n);
    }

    public int size() {
        return names.size();
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    /** Normalized (trimmed, lower-cased) names in insertion order, for display/serialization. */
    public List<String> names() {
        return List.copyOf(names);
    }

    /**
     * Return a copy with {@code name} added (trimmed + lower-cased; idempotent). Null/blank names are
     * ignored. Pure — does not mutate this instance.
     */
    public Whitelist add(String name) {
        Set<String> copy = new LinkedHashSet<>(names);
        if (name != null) {
            String n = name.trim().toLowerCase(Locale.ROOT);
            if (!n.isEmpty()) {
                copy.add(n);
            }
        }
        return new Whitelist(copy);
    }

    /** Return a copy with {@code name} removed (case-insensitive). Pure — does not mutate this instance. */
    public Whitelist remove(String name) {
        Set<String> copy = new LinkedHashSet<>(names);
        if (name != null) {
            copy.remove(name.trim().toLowerCase(Locale.ROOT));
        }
        return new Whitelist(copy);
    }

    /** Serialize back to the comma-joined config form ({@code config/HydraConfig#LOCAL_WHITELIST}). */
    public String toConfigString() {
        return String.join(",", names);
    }
}
