package dev.veltraph.client.combat.heal;

import java.util.*;

/** Same-source received raw values only. No source switch, changed maximum or getter can satisfy recovery. */
public final class HealHealthObservationBasis {
    private final HealthObservation before;
    private long issuedSerial=Long.MAX_VALUE,issuedAt=Long.MAX_VALUE;
    public HealHealthObservationBasis(HealthObservation before){this.before=Objects.requireNonNull(before);}
    public static HealHealthObservationBasis begin(dev.veltraph.client.combat.ghost.GhostHealReturn recovery,dev.veltraph.client.world.Vec3 position,float yaw,float pitch,int slot,HealthObservation observation,long now){
        var basis=new HealHealthObservationBasis(observation);recovery.start(new dev.veltraph.client.combat.ghost.GhostHealReturn.Pose(position,yaw,pitch,slot,basis.before(),basis.beforeAbsorption()),now);return basis;
    }
    public double before(){return before.health();}
    public float beforeAbsorption(){return before.absorption();}
    public void issued(Optional<HealthObservation> latest,long now){issuedAt=now;issuedSerial=latest.filter(before::sameBasis).map(HealthObservation::serial).orElse(before.serial());}
    public Optional<HealthObservation> after(Optional<HealthObservation> current){return current.filter(value->before.sameBasis(value)&&value.serial()>issuedSerial&&value.receivedAt()>issuedAt);}
    public double health(Optional<HealthObservation> current){return after(current).map(HealthObservation::health).orElse(before.health());}
    public float absorption(Optional<HealthObservation> current){return after(current).filter(v->v.format()==HealthObservation.Format.NATIVE_ENTITY).map(HealthObservation::absorption).orElse(before.absorption());}
}
