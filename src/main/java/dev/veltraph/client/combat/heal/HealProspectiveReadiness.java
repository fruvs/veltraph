package dev.veltraph.client.combat.heal;

import java.util.List;

/** Source common readiness selects a fresh candidate without mutating a current native lease. */
public final class HealProspectiveReadiness {
    private HealProspectiveReadiness() {}
    public static boolean ready(HealItemSelector selector,List<HealItemSelector.Item> hotbar,
            HealStackingMode mode,long now,boolean worldPlayer,boolean enabled,boolean blockedByPlayer,
            int observedMana,int rawHealth,boolean ignoreHealth,boolean emergency,HealRawThresholds thresholds) {
        var selected=selector.select(hotbar,mode,now);
        if(selected.isEmpty())return false;
        var type=selected.get().type();
        return HealSourceReadiness.ready(new HealSourceReadiness.Input(worldPlayer,enabled,blockedByPlayer,
                type,observedMana,rawHealth,selector.cooling(type,now),ignoreHealth,emergency,
                thresholds.normal(),thresholds.emergency()));
    }
}
