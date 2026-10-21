package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.ClockService;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;

/**
 * Builds a {@link FarmServices} bundle backed by the live Minecraft client. This is the normal
 * in-world runtime path (the {@code NoOp*} services are only a test/fallback aid).
 */
public final class McFarmServices {

    private McFarmServices() {
    }

    public static FarmServices create(Minecraft client, ClockService clock) {
        return create(client,clock,new dev.veltraph.client.config.MapConfigService());
    }
    public static FarmServices create(Minecraft client, ClockService clock,dev.veltraph.client.config.ConfigService config) {
        return new FarmServices(
                clock,
                new McPlayerService(client),
                new McWorldService(client),
                new McInputService(client),
                new McRotationService(client,config),
                new McBlockBreakingService(client),
                new McInventoryService(client));
    }
}
