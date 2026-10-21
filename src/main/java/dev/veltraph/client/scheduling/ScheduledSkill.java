package dev.veltraph.client.scheduling;
import dev.veltraph.client.config.*;
/** The actual six source AutobreakConfig skill capabilities; no other trigger families are declared. */
public enum ScheduledSkill {
    FARMING(SchedulingConfig.AUTO_BREAK_FARMING),COMBAT(SchedulingConfig.AUTO_BREAK_COMBAT),FORAGING(SchedulingConfig.AUTO_BREAK_FORAGING),MINING(SchedulingConfig.AUTO_BREAK_MINING),FISHING(SchedulingConfig.AUTO_BREAK_FISHING),HUNTING(SchedulingConfig.AUTO_BREAK_HUNTING);
    private final ConfigKey<Boolean> enabled;ScheduledSkill(ConfigKey<Boolean> enabled){this.enabled=enabled;}
    public boolean enabled(ConfigService config){return config.get(enabled);}
}
