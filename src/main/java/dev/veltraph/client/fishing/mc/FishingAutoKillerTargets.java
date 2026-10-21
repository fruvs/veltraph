package dev.veltraph.client.fishing.mc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.squid.Squid;
/** Both catch assignment and combat candidates use the actual physical native body type. */
public final class FishingAutoKillerTargets {
    private FishingAutoKillerTargets() {}
    public static boolean accepts(Entity body,boolean ignoreSquid) {
        return body!=null&&(!ignoreSquid||!(body instanceof Squid));
    }
}
