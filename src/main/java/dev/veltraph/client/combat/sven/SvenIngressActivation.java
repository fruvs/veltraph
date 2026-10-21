package dev.veltraph.client.combat.sven;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContext;
import java.util.*;

/** Admission for the dedicated parent's source Preparation -> Warp branch, never its combat child. */
public final class SvenIngressActivation {
    private SvenIngressActivation() {}
    private static final Set<GameArea> KNOWN_SOURCE_AREAS;
    static {
        var areas=EnumSet.noneOf(GameArea.class);
        for(var area:GameArea.values())if(area.isSkyBlock())areas.add(area);
        KNOWN_SOURCE_AREAS=Collections.unmodifiableSet(areas);
    }
    public static boolean knownSkyBlock(GameContext context) {
        return context!=null&&context.onHypixel()&&context.inSkyBlock()
                &&KNOWN_SOURCE_AREAS.contains(context.area());
    }
    public static Set<GameArea> startAreas(String binding,SvenCaveProfile selected) {
        return SvenBindingProfile.parse(binding,selected).bound()?KNOWN_SOURCE_AREAS:Set.of();
    }
    /** Fresh check after the shared guard, so changed settings cannot use an earlier admission. */
    public static Optional<String> readiness(GameContext context,String binding,SvenCaveProfile selected) {
        if(!knownSkyBlock(context))return Optional.of("Sven parent ingress requires a known Hypixel SkyBlock source area");
        var parsed=SvenBindingProfile.parse(binding,selected);
        return parsed.bound()?Optional.empty():Optional.of(parsed.error());
    }
}
