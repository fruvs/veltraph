package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;

/** Bounded movement ownership handoff; implementations must use normal input and observed arrival. */
public interface FarmRouteDriver {
    enum State { ACTIVE, ARRIVED, BLOCKED }
    boolean startDrop(Vec3 feetTarget,int maxDropBlocks);
    State tick();
    void stop();
    String status();
    /** An implementation-owned immutable native custody receipt. Defaults fail closed. */
    interface MaintenanceBinding {}
    default java.util.Optional<dev.veltraph.client.farming.module.FarmMaintenanceState> maintenanceState(){return java.util.Optional.empty();}
    default java.util.Optional<MaintenanceBinding> captureMaintenanceBinding(String profile){return java.util.Optional.empty();}
    default boolean maintenanceBindingUnchanged(MaintenanceBinding expected,String profile){return false;}
    default boolean canRebindMaintenance(MaintenanceBinding expected,String profile){return false;}
    default java.util.Optional<MaintenanceBinding> rebindMaintenance(MaintenanceBinding expected,String profile){return java.util.Optional.empty();}
    default boolean maintenanceBoundToCurrentContext(MaintenanceBinding expected,String profile){return false;}
}
