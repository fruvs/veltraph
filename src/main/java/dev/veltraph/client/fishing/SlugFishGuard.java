package dev.veltraph.client.fishing;

import com.google.gson.Gson;
import java.util.List;
import java.util.Objects;

/** Stable source's per-hook Slug Fish timer, driven exclusively by observed native hooks and tab rows. */
public final class SlugFishGuard {
    public record Profile(String levelPrefix, String levelSuffix) {
        public Profile {
            if (levelPrefix == null || levelPrefix.isBlank() || levelPrefix.length() > 128
                    || levelSuffix == null || levelSuffix.isEmpty() || levelSuffix.length() > 32)
                throw new IllegalArgumentException("Declare the exact Slug Fish tab level prefix and suffix");
        }
        public static Profile parse(String json) {
            try {
                if (json == null || json.length() > 2048) throw new IllegalArgumentException();
                return Objects.requireNonNull(new Gson().fromJson(json, Profile.class));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Configure an exact Slug Fish tab level profile", e);
            }
        }
        /** Source falls back to unmodified twenty seconds when the stat row is absent. */
        public int thresholdSeconds(List<String> rows) {
            Integer level = null;
            for (String raw : rows) {
                String row = raw.replaceAll("§.", "").strip();
                if (!row.startsWith(levelPrefix)) continue;
                if (level != null || !row.endsWith(levelSuffix))
                    throw new IllegalArgumentException("Slug Fish tab level is ambiguous or malformed");
                String number = row.substring(levelPrefix.length(), row.length() - levelSuffix.length());
                if (!number.matches("[0-9]{1,3}"))
                    throw new IllegalArgumentException("Slug Fish tab level must be an integer");
                level = Integer.parseInt(number);
                // Bounded rebuild acceptance: prevent untrusted rows from creating negative wait times.
                if (level > 200) throw new IllegalArgumentException("Slug Fish tab level exceeds the declared 0..200 range");
            }
            return (int) (20 * (1.0 - (level == null ? 0 : level) / 200.0)) + 1;
        }
    }

    private Object hook;
    private long observedAt;
    private int thresholdSeconds = 21;

    public void observe(Object nativeHook, int seconds, long now) {
        if (seconds < 1 || seconds > 21) throw new IllegalArgumentException("Invalid Slug Fish threshold");
        if (nativeHook == null) { reset(); return; }
        if (hook != nativeHook) { hook = nativeHook; observedAt = now; }
        thresholdSeconds = seconds;
    }
    public boolean ready(long now) {
        return hook != null && now >= observedAt && now - observedAt >= thresholdSeconds * 1000L;
    }
    public long remainingMillis(long now) {
        return hook == null ? 0 : Math.max(0, thresholdSeconds * 1000L - Math.max(0, now - observedAt));
    }
    public void reset() { hook = null; observedAt = 0; thresholdSeconds = 21; }
}
