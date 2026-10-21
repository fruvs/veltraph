package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** Normal END-marker -> explicit START reset. Never captures or returns to an interrupted live pose. */
public final class FarmCycleResetRuntime {
    private static final double SOURCE_TELEPORT_STEP = 2.0;
    public enum State { IDLE, REQUEST_DELAY, WAITING_ARRIVAL, SETTLING, ALIGNING, RESTARTING }
    public interface Actions {
        boolean suspend(Object owner);
        /** Command dispatch is a request; the observer must separately prove arrival. */
        boolean sendRewarp(Object owner);
        /** Request/continue gradual START aiming; true does not mean measured angle arrival. */
        boolean aimAtStart(Object owner,FarmReturnPoint start);
        boolean restartAfterCycleReset(Object owner);
        void stop(Object owner,String reason);
        default void cancelAim(Object owner) {}
    }
    public record Observation(boolean valid,Object world,GameArea area,String dimension,Vec3 position,
                              double speed,boolean onGround,float yaw,float pitch) {
        public Observation {
            if(world==null||area==null||dimension==null||dimension.isBlank()||position==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())
                    ||!Double.isFinite(position.z())||!Double.isFinite(speed)||speed<0
                    ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch < -90||pitch > 90)valid=false;
        }
    }
    public record Inputs(boolean farmRunning,Object owner,String activeProfileId,FarmCyclePlan plan,
                         Observation observation,boolean toolReady,long tick,long nowMs) {
        public Inputs { Objects.requireNonNull(observation,"observation"); }
    }
    /** Pose guards/total deadline are explicit clean local defaults; marker geometry is source-backed. */
    public record Settings(double markerRadius,long requestDelayMs,long settleMs,long maxWaitMs,
                           double startTolerance,double angleTolerance,double maxSpeed) {
        public Settings {
            if(!Double.isFinite(markerRadius)||markerRadius<=0||requestDelayMs<0||settleMs<0||maxWaitMs<=0
                    ||!Double.isFinite(startTolerance)||startTolerance<=0
                    ||!Double.isFinite(angleTolerance)||angleTolerance<=0
                    ||!Double.isFinite(maxSpeed)||maxSpeed<0)throw new IllegalArgumentException("Invalid cycle reset settings");
        }
        public static Settings defaults(){return new Settings(1,1500,250,10000,.35,1,.1);}
    }
    private final Actions actions;
    private State state=State.IDLE;
    private Object owner;
    private FarmCyclePlan plan;
    private Observation previous,settleOrigin;
    private Observation expectedLanding;
    private long expectedLandingAt;
    private long startedMs,lastMs,settleSince=-1;
    private boolean arrivalObserved;
    private int completedCycles;
    private String lastReason="none";
    public FarmCycleResetRuntime(Actions actions){this.actions=Objects.requireNonNull(actions,"actions");}
    public State state(){return state;}
    public boolean active(){return state!=State.IDLE;}
    /** Read-only exact plan binding before its issued warp; not a new trigger or phase writer. */
    public FarmReturnPoint issuedStart(Object candidate){return candidate==owner&&plan!=null&&state==State.REQUEST_DELAY?plan.start():null;}
    public long remainingRequestWindow(long now,long maxWait){return now>=startedMs&&now>=lastMs?Math.max(0,maxWait-(now-startedMs)):0;}
    public int completedCycles(){return completedCycles;}
    public String lastReason(){return lastReason;}
    /** Caller already owns manual stop/world replacement. Discard pending normal reset and its aim. */
    public void cancel(){reset();}
    /** One actual own landing acknowledgment, retained until the later same-tick Hydra observation. */
    public boolean consumeExpectedTeleport(Object candidate,Object world,Vec3 position,long now) {
        if(candidate!=owner||expectedLanding==null||world!=expectedLanding.world()||position==null
                ||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
                ||now<expectedLandingAt||now-expectedLandingAt>=500||expectedLanding.position().distanceTo(position)>.35)return false;
        expectedLanding=null;return true;
    }
    public void tick(Inputs in,Settings settings) {
        Objects.requireNonNull(settings,"settings");
        if(!in.farmRunning()){
            if(active())fail("Cycle owner stopped or unavailable");
            return;
        }
        if(state==State.IDLE) {
            if(in.owner()==null||in.plan()==null||!in.observation().valid()
                    ||!in.plan().profileId().equals(in.activeProfileId())||Math.floorMod(in.tick(),3)!=0)return;
            var end=in.plan().matchingEnd(in.observation().area(),in.observation().dimension(),
                    in.observation().position(),settings.markerRadius());
            if(end.isEmpty())return;
            owner=in.owner();plan=in.plan();previous=in.observation();
            startedMs=lastMs=in.nowMs();settleSince=-1;arrivalObserved=false;
            state=State.REQUEST_DELAY;lastReason="matched END marker "+end.get();
            try{if(!actions.suspend(owner))fail("Cycle owner could not be suspended");}
            catch(RuntimeException failure){fail("Cycle suspension failed");}
            return;
        }
        if(in.owner()!=owner||!Objects.equals(in.activeProfileId(),plan.profileId())||!Objects.equals(in.plan(),plan)) {
            fail("Cycle owner/profile/plan changed");return;
        }
        if(!in.observation().valid()){fail("Invalid player/world/menu context during cycle reset");return;}
        // A requested warp may replace the world while waiting for arrival. Once START is observed,
        // that world owns the settle/aim evidence; another replacement cannot inherit it.
        if(state!=State.REQUEST_DELAY&&state!=State.WAITING_ARRIVAL
                &&previous.world()!=in.observation().world()) {
            fail("Cycle world changed after START arrival");return;
        }
        if(in.nowMs()<lastMs||in.nowMs()-startedMs>=settings.maxWaitMs()) {
            fail("Cycle reset deadline expired");return;
        }
        lastMs=in.nowMs();
        try {
            switch(state) {
                case REQUEST_DELAY -> {
                    if(!sameScope(in.observation())||previous.world()!=in.observation().world()) {
                        fail("Cycle context changed before command dispatch");return;
                    }
                    if(in.nowMs()-startedMs>=settings.requestDelayMs()) {
                        previous=in.observation();
                        if(!actions.sendRewarp(owner)){fail("Cycle rewarp command could not be sent");return;}
                        state=State.WAITING_ARRIVAL;
                    }
                }
                case WAITING_ARRIVAL -> {
                    observeArrival(in.observation());
                    if(arrivalObserved&&sameScope(in.observation())) {
                        if(!atStart(in.observation(),settings)){fail("Cycle warp arrived away from explicit START");return;}
                        if(previous.world()==in.observation().world()
                                &&previous.position().distanceTo(in.observation().position())>SOURCE_TELEPORT_STEP) {
                            expectedLanding=in.observation();expectedLandingAt=in.nowMs();
                        }
                        state=State.SETTLING;settleSince=-1;updateSettle(in,settings);
                    }
                }
                case SETTLING -> updateSettle(in,settings);
                case ALIGNING -> {
                    if(!readyAtStart(in,settings)){state=State.SETTLING;settleSince=-1;return;}
                    if(!actions.aimAtStart(owner,plan.start())){fail("Cycle START aim could not be requested");return;}
                    if(anglesReady(in.observation(),settings))state=State.RESTARTING;
                }
                case RESTARTING -> {
                    if(!readyAtStart(in,settings)){state=State.SETTLING;settleSince=-1;return;}
                    if(!anglesReady(in.observation(),settings)){state=State.ALIGNING;return;}
                    if(!actions.restartAfterCycleReset(owner)){fail("Cycle owner could not restart at START");return;}
                    completedCycles++;lastReason="completed normal cycle at explicit START";reset();
                }
                case IDLE -> {}
            }
        } catch(RuntimeException failure){fail("Cycle reset action failed");}
        if(active())previous=in.observation();
    }
    private void observeArrival(Observation now) {
        if(previous==null)return;
        boolean worldChanged=previous.world()!=null&&now.world()!=null&&previous.world()!=now.world();
        boolean step=previous.position().distanceTo(now.position())>SOURCE_TELEPORT_STEP;
        if(worldChanged||step)arrivalObserved=true;
    }
    private boolean sameScope(Observation observation) {
        return observation.area()==plan.start().area()&&Objects.equals(observation.dimension(),plan.start().dimension());
    }
    private boolean atStart(Observation observation,Settings settings) {
        return sameScope(observation)&&observation.position().distanceTo(plan.start().position())<=settings.startTolerance();
    }
    private boolean readyAtStart(Inputs in,Settings settings) {
        return arrivalObserved&&atStart(in.observation(),settings)&&in.observation().onGround()
                &&in.toolReady()&&in.observation().speed()<=settings.maxSpeed();
    }
    private void updateSettle(Inputs in,Settings settings) {
        if(!atStart(in.observation(),settings)){fail("Cycle START pose drifted before restart");return;}
        if(!readyAtStart(in,settings)){settleSince=-1;return;}
        if(settleSince<0||settleOrigin==null||settleOrigin.world()!=in.observation().world()
                ||settleOrigin.position().distanceTo(in.observation().position())>settings.startTolerance()) {
            settleSince=in.nowMs();settleOrigin=in.observation();
        }
        if(in.nowMs()-settleSince>=settings.settleMs())state=State.ALIGNING;
    }
    private boolean anglesReady(Observation observation,Settings settings) {
        return angleError(observation.yaw(),plan.start().yaw())<=settings.angleTolerance()
                &&Math.abs(observation.pitch()-plan.start().pitch())<=settings.angleTolerance();
    }
    private static double angleError(float actual,float expected) {
        double difference=(actual-expected)%360;
        if(difference>180)difference-=360;if(difference< -180)difference+=360;
        return Math.abs(difference);
    }
    private void fail(String reason) {
        Object failedOwner=owner;lastReason=reason;
        try{reset();}finally{if(failedOwner!=null)actions.stop(failedOwner,reason);}
    }
    private void reset() {
        Object old=owner;state=State.IDLE;owner=null;plan=null;previous=settleOrigin=null;
        expectedLanding=null;expectedLandingAt=0;
        settleSince=-1;startedMs=lastMs=0;arrivalObserved=false;
        if(old!=null)actions.cancelAim(old);
    }
    public String statusLine(){return "farm-cycle: "+state+" | completed="+completedCycles+" | "+lastReason;}
}
