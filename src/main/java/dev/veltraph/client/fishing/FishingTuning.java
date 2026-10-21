package dev.veltraph.client.fishing;

import dev.veltraph.client.config.FishingConfig;
import dev.veltraph.client.config.ConfigService;

/**
 * Immutable per-run tuning snapshot for the pure {@link FishingRuntime}, derived from {@link FishingConfig}
 * (seconds → millis). Decouples the FSM from {@link ConfigService}.
 *
 * @param minCatchMs    "too soon" guard: minimum millis from cast before a bite may be reeled (recovered
 *                      {@code floatValue13}/minWaitSec; default 0.7 s)
 * @param catchDelayMs  post-reel delay before recasting ({@code FISHING_CATCH_DELAY}; default 0.2 s)
 * @param humanize      add a 0–2 tick jitter to the post-reel delay ({@code FISHING_CATCH_HUMANIZE_DELAY})
 * @param castTimeoutMs recast if no bobber appears within this long after a cast; stop if a reeled bobber remains beyond it (default 4 s)
 * @param mode          bite-detection signal
 * @param dipThreshold  Y-velocity threshold for {@link FishingDetectionMode#BOBBER_DIP}
 */
public record FishingTuning(
        long minCatchMs,
        long catchDelayMs,
        boolean humanize,
        long castTimeoutMs,
        FishingDetectionMode mode,
        double dipThreshold) {

    public static FishingTuning fromConfig(ConfigService config) {
        return new FishingTuning(
                Math.round(config.get(FishingConfig.FISHING_MIN_CATCH_SECONDS) * 1000.0),
                Math.round(config.get(FishingConfig.FISHING_CATCH_DELAY) * 1000.0),
                config.get(FishingConfig.FISHING_CATCH_HUMANIZE_DELAY),
                Math.round(config.get(FishingConfig.FISHING_CAST_TIMEOUT_SECONDS) * 1000.0),
                config.get(FishingConfig.FISHING_BITE_DETECTION),
                config.get(FishingConfig.FISHING_BOBBER_DIP_THRESHOLD));
    }
}
