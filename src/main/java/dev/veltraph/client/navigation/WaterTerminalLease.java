package dev.veltraph.client.navigation;
import java.util.Objects;import dev.veltraph.client.world.Vec3;
/** Exact native plan/owner/context capability under the caller's unchanged original expiry. */
public final class WaterTerminalLease {
 private final Object plan,authority,player,world,connection;private final Vec3 goal;private final double radius;private final long issuedAt,expiresAt;private long lastNow;private boolean revoked;
 public WaterTerminalLease(Object plan,Object authority,Object player,Object world,Object connection,Vec3 goal,double radius,long issuedAt,long expiresAt){this.plan=Objects.requireNonNull(plan);this.authority=Objects.requireNonNull(authority);this.player=Objects.requireNonNull(player);this.world=Objects.requireNonNull(world);this.connection=Objects.requireNonNull(connection);this.goal=Objects.requireNonNull(goal);if(!Double.isFinite(goal.x())||!Double.isFinite(goal.y())||!Double.isFinite(goal.z())||!Double.isFinite(radius)||radius<.05||radius>.3||expiresAt<issuedAt)throw new IllegalArgumentException("Exact strict goal/radius and original live deadline required");this.radius=radius;this.issuedAt=issuedAt;this.expiresAt=expiresAt;lastNow=issuedAt;}
 public boolean valid(Object plan,Object authority,Object player,Object world,Object connection,Vec3 goal,double radius,boolean enabled,long now){if(revoked||!enabled||this.plan!=plan||this.authority!=authority||this.player!=player||this.world!=world||this.connection!=connection||!this.goal.equals(goal)||Double.compare(this.radius,radius)!=0||now<lastNow||now<issuedAt||now>expiresAt){revoked=true;return false;}lastNow=now;return true;}
 public boolean expiresAt(long value){return value==expiresAt;}
 public void revoke(){revoked=true;}
 public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"plan",plan,"authority",authority,"player",player,"world",world,"connection",connection,"goal",goal,"radius",radius,"issuedAt",issuedAt,"expiresAt",expiresAt,"lastNow",lastNow,"revoked",revoked);}
}
