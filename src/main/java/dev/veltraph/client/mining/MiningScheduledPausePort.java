package dev.veltraph.client.mining;

import dev.veltraph.client.mining.module.MiningModule;
import dev.veltraph.client.scheduling.ScheduledPausePort;
import java.util.Objects;
import java.util.Optional;

/** Actual mining engine port; parent owns matched transport/arrival and scheduler registration. */
public final class MiningScheduledPausePort implements ScheduledPausePort {
    private final MiningModule module;
    public MiningScheduledPausePort(MiningModule module){this.module=Objects.requireNonNull(module);}
    @Override public boolean supportsDueRestPreparation(){return module.miningMode()==MiningMode.COBBLESTONE;}
    @Override public boolean prepareForDueRest(Object cause){return module.prepareForDueRest(cause);}
    @Override public void cancelDueRestPreparation(Object cause){module.cancelDueRestPreparation(cause);}
    @Override public boolean pause(Object ticket){return module.pauseForScheduledRest(ticket);}
    @Override public boolean owns(Object ticket){return module.scheduledPauseOwnedBy(ticket);}
    @Override public boolean rebindAfterOwnedArrival(Object ticket){return module.rebindAfterScheduledArrival(ticket);}
    @Override public boolean resume(Object ticket){return module.resumeAfterScheduledRest(ticket);}
    @Override public void stop(Object ticket){if(owns(ticket))module.setEnabled(false);}
    public Optional<String> readiness(){return module.scheduledRestReadiness();}
    public boolean atBoundary(){return module.controller().scheduledBoundary();}
    public String profile(){return module.id()+"|"+module.controller().scheduledProfile();}
    public String settings(){return module.scheduledSettingsSignature();}
}
