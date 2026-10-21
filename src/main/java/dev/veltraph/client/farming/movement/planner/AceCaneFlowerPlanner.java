package dev.veltraph.client.farming.movement.planner;

/** Creator's four input phases. Markers/collision are supplied from the loaded author layout. */
public final class AceCaneFlowerPlanner {
    public enum Phase { ROW_CHORD, ROW_LEFT, LOOP_CHORD, LOOP_EXIT }
    public record Keys(boolean forward, boolean back, boolean left, boolean right, boolean sprint) {}
    private Phase phase = Phase.ROW_CHORD;
    private int rows, loops;
    public void reset() { phase=Phase.ROW_CHORD; rows=loops=0; }
    public void reset(Phase initial) { phase=java.util.Objects.requireNonNull(initial); rows=loops=0; }
    public Phase phase() { return phase; }
    public int rows() { return rows; }
    public int loops() { return loops; }
    public Keys keys() {
        return switch(phase) {
            case ROW_CHORD -> new Keys(true,false,false,true,true);
            case ROW_LEFT -> new Keys(false,false,true,false,false);
            case LOOP_CHORD -> new Keys(true,true,false,true,true);
            case LOOP_EXIT -> new Keys(false,true,false,false,false);
        };
    }
    /** The trapdoor return takes precedence over the normal carpet row marker. */
    public void observe(boolean passedCarpet, boolean trapdoorReturn, boolean stationaryCorner) {
        switch(phase) {
            case ROW_CHORD -> {
                if(trapdoorReturn) phase=Phase.LOOP_CHORD;
                else if(passedCarpet) { phase=Phase.ROW_LEFT; rows++; }
            }
            case ROW_LEFT -> { if(stationaryCorner) { phase=Phase.ROW_CHORD; rows++; } }
            case LOOP_CHORD -> { if(stationaryCorner) phase=Phase.LOOP_EXIT; }
            case LOOP_EXIT -> { if(stationaryCorner) { phase=Phase.ROW_LEFT; loops++; } }
        }
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "phase", phase,
                "rows", rows,
                "loops", loops);
    }
}
