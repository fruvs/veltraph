package dev.veltraph.client.fishing.setup;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.*;

/**
 * The empty profile uses inertly recovered stable names; explicit profiles remain validated.
 * The source tables are HashMaps, not ordered
 * preference lists: overlapping substrings follow the HashMap key iteration, as in the source.
 */
public record RodSelectionProfile(String excludedNameContains, List<Binding> water, List<Binding> lava) {
    public record Binding(String nameContains, int weight) {
        public Binding {
            if (nameContains == null || nameContains.isBlank() || nameContains.length() > 128
                    || nameContains.indexOf('\n') >= 0 || nameContains.indexOf('§') >= 0)
                throw new IllegalArgumentException("Supply an observed plain rod name binding");
        }
    }
    public RodSelectionProfile {
        if (excludedNameContains == null || excludedNameContains.isBlank() || excludedNameContains.length() > 128
                || excludedNameContains.indexOf('\n') >= 0)
            throw new IllegalArgumentException("Supply the observed excluded rod name binding");
        water = validate(water, List.of(50, 30, 30, 20, 10, 5, 1));
        lava = validate(lava, List.of(50, 30, 20, 10, 3, 1));
    }
    private static List<Binding> validate(List<Binding> values, List<Integer> weights) {
        if (values == null || values.size() != weights.size())
            throw new IllegalArgumentException("Bind every recovered water/lava rod table entry");
        var names = new HashSet<String>();
        var actual = new ArrayList<Integer>();
        for (var entry : values) {
            if (entry == null || !names.add(entry.nameContains()))
                throw new IllegalArgumentException("Rod bindings must be unique");
            actual.add(entry.weight());
        }
        actual.sort(Integer::compareTo);
        var expected = new ArrayList<>(weights); expected.sort(Integer::compareTo);
        if (!actual.equals(expected)) throw new IllegalArgumentException("Rod weights must match the recovered source table");
        return List.copyOf(values);
    }
    public HashMap<String,Integer> table(boolean lavaNearby) {
        var map = new HashMap<String,Integer>();
        for (var binding : lavaNearby ? lava : water) map.put(binding.nameContains(), binding.weight());
        return map;
    }
    public static RodSelectionProfile parse(String json) {
        if (json == null || json.length() > 8192) throw new IllegalArgumentException("Invalid rod profile size");
        try {
            var value = JsonParser.parseString(json);
            if (value.isJsonObject() && value.getAsJsonObject().size() == 0)
                return SourceRodBindings.profile();
            return Objects.requireNonNull(new Gson().fromJson(value, RodSelectionProfile.class));
        }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Configure all observed rod selection bindings", failure); }
    }
}
