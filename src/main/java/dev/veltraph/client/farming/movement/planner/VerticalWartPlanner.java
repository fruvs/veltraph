package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.world.*;

/**
 * Source-first vertical crop counter/height controller. Raw owner llIlIllllIlllIIII... scans
 * horizontally, probes two direction getters on entry, and flips LEFT/RIGHT directly on the
 * recorded-height fallback. The forty/twenty controls are counters and diagnostic scheduler units,
 * not a twenty-tick physical strafe pulse. See VERTICAL_WART_SOURCE_AUDIT.md.
 */
public final class VerticalWartPlanner {
    public static final int STILL_CHECK_PERIOD=40,FORWARD_RECHECK_PERIOD=20,MAX_SCAN_DISTANCE=256;
    public static final int DIAGNOSTIC_SCHEDULE_UNITS=20;
    public static final double STILL_DISTANCE=.25,TINY_JITTER=.5,ROW_STEP=1.5,Y_SENTINEL=32767;
    public interface World {
        Vec3 position(); double verticalPos(); double horizontalSpeed();
        boolean rowAdvanceCheck(); boolean stepPredicates();
        /** Original two independent horizontal direction probes, first then second. */
        ProbeResult scanDirection(boolean second,int limit);
        /** Original still-handler logs/schedules and sets its sentinel; no timed movement. */
        void onStill(TurnDirection current,int diagnosticUnits);
        /** Apply the selected persistent lateral key after a source direction change. */
        void commitTurn();
        /** Original input-manager reset when moving during a significant height change. */
        void releaseForHeightChange();
    }
    private final ConfigService config;
    private final World world;
    private int stillCounter,forwardCounter;
    private Vec3 lastPos;
    private double lastY;
    private TurnDirection currentDir=TurnDirection.LEFT,lastResolvedSide=TurnDirection.LEFT;
    private ProbeResult lastScan=ProbeResult.NONE;
    public VerticalWartPlanner(ConfigService config,World world){this.config=config;this.world=world;}
    public void start(){
        resetObservations();
        // Explicit user seed is retained as a clean configuration extension; raw ctor starts LEFT.
        currentDir=TurnDirection.fromLaneSide(config.get(FarmingConfig.NETHERWART_STARTING_DIRECTION));
        resolveEntryDirection();
    }
    public void resumeAfterTeleport(){
        resetObservations();
        if(config.get(FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP)) currentDir=currentDir.opposite();
        lastResolvedSide=currentDir;
    }
    /** A real lower standing arrival replaces the stale height and performs the direct source flip. */
    public void acknowledgeDropLanding(){resetObservations();currentDir=currentDir.opposite();lastResolvedSide=currentDir;world.commitTurn();}
    private void resetObservations(){stillCounter=forwardCounter=0;lastPos=world.position();lastY=world.verticalPos();lastScan=ProbeResult.NONE;}
    public void resolveEntryDirection(){
        ProbeResult first=world.scanDirection(false,MAX_SCAN_DISTANCE);lastScan=first;
        if(first==ProbeResult.FOUND)currentDir=TurnDirection.LEFT;
        else if(first==ProbeResult.BLOCKED)currentDir=TurnDirection.RIGHT;
        else {
            ProbeResult second=world.scanDirection(true,MAX_SCAN_DISTANCE);lastScan=second;
            if(second==ProbeResult.FOUND)currentDir=TurnDirection.RIGHT;
            else if(second==ProbeResult.BLOCKED)currentDir=TurnDirection.LEFT;
        }
        lastResolvedSide=currentDir;
    }
    public void tick(){
        stillCounter++;forwardCounter++;
        if(stillCounter>=STILL_CHECK_PERIOD){
            stillCounter=0;Vec3 observed=world.position();double moved=observed.distanceTo(lastPos);lastPos=observed;
            if(moved<=STILL_DISTANCE&&config.get(FarmingConfig.VERT_WART_SWITCH_DIRECTION_IF_STILL)){
                world.onStill(currentDir,DIAGNOSTIC_SCHEDULE_UNITS);lastY=Y_SENTINEL;return;
            }
        }
        if(forwardCounter<FORWARD_RECHECK_PERIOD)return;
        forwardCounter=0;
        if(world.rowAdvanceCheck())return;
        double y=world.verticalPos(),delta=Math.abs(y-lastY);
        if(world.horizontalSpeed()>0&&delta>TINY_JITTER){world.releaseForHeightChange();return;}
        if(delta>=ROW_STEP&&world.stepPredicates()&&world.rowAdvanceCheck())return;
        currentDir=currentDir.opposite();lastResolvedSide=currentDir;
        world.commitTurn();lastY=y;
    }
    public TurnDirection currentDir(){return currentDir;} public int stillCounter(){return stillCounter;}
    public int forwardCounter(){return forwardCounter;} public ProbeResult lastScan(){return lastScan;}
    public TurnDirection lastResolvedSide(){return lastResolvedSide;} public double recordedY(){return lastY;}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "world", world,
                "stillCounter", stillCounter,
                "forwardCounter", forwardCounter,
                "lastPos", lastPos,
                "lastY", lastY,
                "currentDir", currentDir,
                "lastResolvedSide", lastResolvedSide,
                "lastScan", lastScan);
    }
}
