package dev.veltraph.client.garden.pests;
import dev.veltraph.client.garden.jacob.*;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
/** Exact already-consumed source sample, carried once across native farm ACK drain. */
public final class PreparedTrapActivation {
    private final FarmMaintenanceLease.Token token;private final JacobContestTracker.Scope scope;
    private final JacobContestObservation window;private boolean retired;
    public PreparedTrapActivation(FarmMaintenanceLease.Token token,JacobContestTracker.Scope scope,JacobContestObservation window){
        this.token=java.util.Objects.requireNonNull(token);this.scope=java.util.Objects.requireNonNull(scope);this.window=java.util.Objects.requireNonNull(window);
        if(!window.clockKnown())throw new IllegalArgumentException("Known original consumed contest window required");
    }
    public boolean consume(FarmMaintenanceLease.Token token,JacobContestTracker.Scope current,JacobContestObservation window,boolean leaseValid){
        boolean valid=!retired&&this.token==token&&current!=null&&scope.player()==current.player()&&scope.world()==current.world()
                &&scope.connection()==current.connection()&&scope.profile().equals(current.profile())&&this.window==window&&leaseValid;
        retired=true;return valid;
    }
    public JacobContestTracker.Scope scope(){return scope;}
    public boolean boundTo(FarmMaintenanceLease.Token token){return this.token==token;}
    public boolean owns(FarmMaintenanceLease.Token token){return !retired&&this.token==token;}
    public void cancel(FarmMaintenanceLease.Token token){if(this.token==token)retired=true;}
}
