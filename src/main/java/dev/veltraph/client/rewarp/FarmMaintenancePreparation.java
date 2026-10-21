package dev.veltraph.client.rewarp;

import java.util.Optional;

/** Native pause/ACK drain precedes activation under the original finite transaction lease. */
public final class FarmMaintenancePreparation<W> {
    public interface Port<W> {
        boolean valid(); Optional<W> captureWitness(); boolean activate(W witness); void committed(W witness); void rollback();
    }
    public enum State { WAITING, COMMITTED, CANCELLED }
    private final FarmMaintenanceLease lease;private final FarmMaintenanceLease.Token token;
    private final Object farm,helper;private final Port<W> port;private State state=State.WAITING;private W witness;
    public FarmMaintenancePreparation(FarmMaintenanceLease lease,FarmMaintenanceLease.Token token,Object farm,Object helper,Port<W> port){
        this.lease=java.util.Objects.requireNonNull(lease);this.token=java.util.Objects.requireNonNull(token);
        this.farm=java.util.Objects.requireNonNull(farm);this.helper=java.util.Objects.requireNonNull(helper);this.port=java.util.Objects.requireNonNull(port);
    }
    public void tick(long now){
        if(state!=State.WAITING)return;
        if(!lease.validate(token,farm,helper,now,port.valid())){cancel();return;}
        if(witness==null){var captured=port.captureWitness();if(captured.isEmpty())return;witness=captured.get();}
        if(!port.activate(witness)||!port.valid()||!lease.commit(token)){cancel();return;}
        state=State.COMMITTED;port.committed(witness);
    }
    private void cancel(){state=State.CANCELLED;port.rollback();}
    public State state(){return state;}
}
