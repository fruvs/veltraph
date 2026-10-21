package dev.veltraph.client.hydra;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.HydraConfig;

/**
 * Immutable snapshot of the local anti-staff configuration, decoupled from {@link ConfigService} so the
 * pure {@link HydraDetector} never reads config (or Minecraft) directly. The adapter builds one of these
 * per tick via {@link #fromConfig(ConfigService)}.
 *
 * <p>All fields trace to {@link HydraConfig} (spec: {@code HydraAntiStaffLocal.md}).
 *
 * @param enabled                master switch
 * @param distanceBlocks         proximity threshold in blocks (non-whitelisted player within → alert)
 * @param dangerousDistanceBlocks proximity inner band → {@link HydraSeverity#DANGEROUS} (§10)
 * @param sound                  play a local sound ping on a fresh alert
 * @param title                  show a local title on a fresh alert
 * @param autoStopMacro          stop active automation + release input on a trigger
 * @param cooldownMs             quiet period before ALERTED→IDLE and minimum reaction spacing
 * @param teleportDistanceBlocks single-tick local-player jump beyond this = teleport anomaly
 * @param detectLagback          enable the invalid-movement / lagback trigger
 * @param lagbackDistanceBlocks  backward rubber-band beyond this (blocks, one tick) = lagback
 * @param detectTeleportIntoBlock enable the teleport-into-block trigger
 * @param detectForcedAdventure  enable the forced-adventure trigger
 * @param detectRoutingText      enable the routing/kick-text trigger
 * @param detectFloorChange      enable the repeated local floor/block-change trigger
 * @param detectBarrier          enable the forward-collision BARRIER trigger
 * @param barrierStationarySpeed max per-tick move (blocks) to count as "held in place" for the BARRIER trigger
 */
public record HydraSettings(
        boolean enabled,
        double distanceBlocks,
        double dangerousDistanceBlocks,
        boolean sound,
        boolean title,
        boolean autoStopMacro,
        long cooldownMs,
        double teleportDistanceBlocks,
        boolean detectLagback,
        double lagbackDistanceBlocks,
        boolean detectTeleportIntoBlock,
        boolean detectForcedAdventure,
        boolean detectRoutingText,
        boolean detectFloorChange,
        boolean detectBarrier,
        double barrierStationarySpeed) {

    /** Build a snapshot from the live config service (defaults apply for unset keys). */
    public static HydraSettings fromConfig(ConfigService config) {
        return new HydraSettings(
                config.get(HydraConfig.STAFF_ALERT_ENABLED),
                config.get(HydraConfig.STAFF_ALERT_DISTANCE_BLOCKS),
                config.get(HydraConfig.STAFF_ALERT_DANGEROUS_DISTANCE_BLOCKS),
                config.get(HydraConfig.STAFF_ALERT_SOUND),
                config.get(HydraConfig.STAFF_ALERT_TITLE),
                config.get(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO),
                config.get(HydraConfig.STAFF_ALERT_COOLDOWN_MS),
                config.get(HydraConfig.TELEPORT_DISTANCE_BLOCKS),
                config.get(HydraConfig.DETECT_LAGBACK),
                config.get(HydraConfig.LAGBACK_DISTANCE_BLOCKS),
                config.get(HydraConfig.DETECT_TELEPORT_INTO_BLOCK),
                config.get(HydraConfig.DETECT_FORCED_ADVENTURE),
                config.get(HydraConfig.DETECT_ROUTING_TEXT),
                config.get(HydraConfig.DETECT_FLOOR_CHANGE),
                config.get(HydraConfig.DETECT_BARRIER),
                config.get(HydraConfig.BARRIER_STATIONARY_SPEED));
    }
}
