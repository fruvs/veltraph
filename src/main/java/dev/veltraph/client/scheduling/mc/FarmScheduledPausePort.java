package dev.veltraph.client.scheduling.mc;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.scheduling.ScheduledPausePort;
import java.util.Objects;
import java.util.function.BooleanSupplier;
/** FarmModule's existing token gate preserves its engine; actual family native-ref rebinding is mandatory. */
public final class FarmScheduledPausePort implements ScheduledPausePort {
    private final FarmModule farm;private final BooleanSupplier rebind;
    public FarmScheduledPausePort(FarmModule farm){this.farm=Objects.requireNonNull(farm);this.rebind=null;}
    public FarmScheduledPausePort(FarmModule farm,BooleanSupplier rebind){this.farm=Objects.requireNonNull(farm);this.rebind=Objects.requireNonNull(rebind);}
    public boolean pause(Object ticket){return farm.suspendForMaintenance(ticket);}
    public boolean owns(Object ticket){return farm.maintenanceOwnedBy(ticket);}
    public boolean rebindAfterOwnedArrival(Object ticket){return owns(ticket)&&(rebind==null?farm.rebindForScheduledRest(ticket):rebind.getAsBoolean());}
    public boolean resume(Object ticket){return farm.resumeAfterMaintenance(ticket);}
    public void stop(Object ticket){if(owns(ticket)||farm.isEnabled())farm.setEnabled(false);}
}
