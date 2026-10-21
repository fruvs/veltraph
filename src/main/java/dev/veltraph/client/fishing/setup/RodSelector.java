package dev.veltraph.client.fishing.setup;

import java.util.*;

/** Stable readable score helper and its actual interface default selector, kept Minecraft independent. */
public final class RodSelector {
    private RodSelector() {}
    public record Candidate(int slot, boolean nativeRod, String rawName, boolean foil) {
        public Candidate { Objects.requireNonNull(rawName); }
    }
    public enum Block { WATER, LAVA, OTHER, UNLOADED }
    @FunctionalInterface public interface Blocks { Block at(int x, int y, int z); }
    /** Stable scan uses native block identity, not arbitrary waterlogged fluid or guessed pool physics. */
    public static boolean lavaNearby(Blocks blocks) {
        int balance = 0;
        for (int x = -5; x < 5; x++) for (int y = -5; y < 5; y++) for (int z = -5; z < 5; z++) {
            var block = Objects.requireNonNull(blocks.at(x,y,z));
            if (block == Block.UNLOADED) throw new IllegalArgumentException("Rod setup area is not fully loaded");
            if (block == Block.WATER) balance++;
            else if (block == Block.LAVA) balance--;
        }
        return balance < 0;
    }
    public static int score(Candidate candidate, boolean lava, RodSelectionProfile profile) {
        if (!candidate.nativeRod() || candidate.rawName().contains(profile.excludedNameContains())) return 0;
        int score = candidate.foil() ? 10 : 0;
        var cleanName = candidate.rawName().replaceAll("§.", "");
        var table = profile.table(lava);
        for (var name : table.keySet()) {
            if (cleanName.contains(name)) { score += table.get(name); break; }
        }
        return score;
    }
    /** Source scans slots 0..7 only and updates on strict improvement; first positive maximum wins. */
    public static int select(List<Candidate> hotbar, boolean lava, RodSelectionProfile profile) {
        var slots = new HashMap<Integer,Candidate>();
        for (var candidate : hotbar) {
            if (candidate.slot() < 0 || candidate.slot() > 8 || slots.put(candidate.slot(),candidate) != null)
                throw new IllegalArgumentException("Invalid native hotbar snapshot");
        }
        int best = 0, selected = -1;
        for (int slot = 0; slot < 8; slot++) {
            var candidate = slots.get(slot);
            if (candidate == null) throw new IllegalArgumentException("Native hotbar snapshot is incomplete");
            int score = score(candidate,lava,profile);
            if (score > best) { best = score; selected = slot; }
        }
        return selected;
    }
}
