package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.Angles;
/** Fixed authored view and source-constrained W/S lane selection; never blind corner reversal. */
public final class MushroomRingPlanner {
    private boolean forward;
    private MushroomRingLaneClassifier.Lane previousLane;
    private int turns;
    private float yaw;

    public void start(float currentYaw) { start(currentYaw,true); }
    public void start(float currentYaw,boolean startForward) {
        float nearest=-116.57f;
        for(float candidate:new float[]{-116.57f,-26.57f,153.43f,63.43f})
            if(Math.abs(Angles.signedDelta(currentYaw,candidate))<Math.abs(Angles.signedDelta(currentYaw,nearest)))nearest=candidate;
        yaw=nearest;forward=startForward;previousLane=null;turns=0;
    }
    /** Pausing cannot change the selected input or create a turn. Fresh geometry is required to resume. */
    public void pause() {}
    public boolean selectLane(MushroomRingLaneClassifier.Lane lane) {
        if(lane!=MushroomRingLaneClassifier.Lane.NON_TRAPDOOR&&lane!=MushroomRingLaneClassifier.Lane.TRAPDOOR)return false;
        if(previousLane!=null&&previousLane!=lane)turns++;
        forward=lane==MushroomRingLaneClassifier.Lane.NON_TRAPDOOR;previousLane=lane;return true;
    }
    public boolean forward(){return forward;}
    public float yaw(){return yaw;}
    public int turns(){return turns;}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "forward", forward,
                "previousLane", previousLane,
                "turns", turns,
                "yaw", yaw);
    }
}
