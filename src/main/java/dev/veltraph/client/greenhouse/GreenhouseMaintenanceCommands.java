package dev.veltraph.client.greenhouse;

import dev.veltraph.client.core.ModuleActivationGuard;

/** The full existing read/prepare/inspect command surface targets this exact parent instance. */
public final class GreenhouseMaintenanceCommands {
    private GreenhouseMaintenanceCommands(){}
    public static void register(GreenhouseMaintenanceModule module,ModuleActivationGuard guard){GreenhouseCommands.register(module,guard,"vgreenhouse-maintain",()->module.dispatch(()->guard.start(module)));}
}
