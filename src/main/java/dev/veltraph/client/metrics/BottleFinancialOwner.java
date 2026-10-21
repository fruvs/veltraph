package dev.veltraph.client.metrics;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel;
import java.util.Optional;
/** Passive exact issued-travel descriptor; it cannot admit native gameplay or renew a timer. */
public interface BottleFinancialOwner extends Module {Optional<Travel> pendingBottleFinancialTravel();}
