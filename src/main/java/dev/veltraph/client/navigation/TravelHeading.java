package dev.veltraph.client.navigation;

/** Travel-specific critically damped heading, bounded in degrees per simulation tick. */
public final class TravelHeading {
    private float velocity;
    public void reset() { velocity=0; }
    public float step(float yaw,float desired,double speed) {
        return advance(yaw,WalkController.wrap(desired-yaw),speed);
    }
    /** Pitch is bounded, not circular: -90 to +90 must travel through zero. */
    public float stepLinear(float angle,float desired,double speed) {
        return advance(angle,desired-angle,speed);
    }
    private float advance(float angle,float error,double speed) {
        double limit=6*Math.max(.25,Math.min(2,Double.isFinite(speed)?speed:1));
        float wanted=(float)Math.max(-limit,Math.min(limit,error*.22));
        velocity+=(float)Math.max(-1.2,Math.min(1.2,wanted-velocity));
        return angle+velocity;
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "velocity", velocity);
    }
}
