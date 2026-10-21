package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;

/** One-use receipt for an explicitly sent travel action, never a blanket failsafe suppression window. */
public final class ExpectedTeleport {
    private Vec3 destination;private long armedAt,expires;
    public void arm(Vec3 destination,long now,long timeout) {
        clear();
        if(destination==null||!Double.isFinite(destination.x())||!Double.isFinite(destination.y())||!Double.isFinite(destination.z()))
            throw new IllegalArgumentException("Expected teleport destination must be finite");
        this.destination=destination;armedAt=now;expires=now+Math.max(500,Math.min(30000,timeout));
    }
    public boolean consume(Vec3 position,long now) {
        if(destination==null) return false;
        if(position==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())) return false;
        if(now<armedAt||now>expires) { clear();return false; }
        if(position.distanceTo(destination)>=.9) return false;
        clear();return true;
    }
    public void clear() { destination=null; }
}
