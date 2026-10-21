package dev.veltraph.client.farming.module;

import dev.veltraph.client.service.BlockBreakingService;

/** One suspension's immutable logical checkpoint and actual native crop-guard receipt. */
public final class FarmMaintenanceEngineWitness {
    final FarmModule owner;
    final Object token;
    final long suspension;
    final BlockBreakingService.TargetGuardBinding binding;
    final dev.veltraph.client.farming.movement.FarmRouteDriver routeDriver;
    final dev.veltraph.client.farming.movement.FarmRouteDriver.MaintenanceBinding routeBinding;
    private final FarmMaintenanceState progress;
    FarmMaintenanceEngineWitness(FarmModule owner,Object token,long suspension,FarmMaintenanceState progress,
                                 BlockBreakingService.TargetGuardBinding binding,
                                 dev.veltraph.client.farming.movement.FarmRouteDriver routeDriver,
                                 dev.veltraph.client.farming.movement.FarmRouteDriver.MaintenanceBinding routeBinding){
        this.owner=owner;this.token=token;this.suspension=suspension;this.progress=progress;this.binding=binding;this.routeDriver=routeDriver;this.routeBinding=routeBinding;
    }
    public String family(){return owner.id();}
    public FarmMaintenanceState progress(){return progress;}
}
