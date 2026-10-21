package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.*;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Loaded wall/crop-guided diagonal passes. No lane-centre row sweep or pose/velocity writes. */
public final class CapturedGourdWallPlanner {
    public enum State { START_ENTRY, FARMING, ALIGNING, FOCUSING, END_APPROACH, END_TRANSFER, BLOCKED }
    public record Band(Direction wall,Direction crop,int wallDistance,String key,int minCropAxis,int maxCropAxis,int cropY){
        public Band(Direction wall,Direction crop,int wallDistance,String key){this(wall,crop,wallDistance,key,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);}
        public boolean contains(BlockPos cell){int axis=wall.dx()!=0?cell.x():cell.z();return minCropAxis!=Integer.MIN_VALUE&&axis>=minCropAxis&&axis<=maxCropAxis&&cell.y()==cropY;}
    }
    public record Next(Band band,Vec3 entry,Direction transfer,Vec3 approach,Direction approachDirection){
        public Next(Band band,Vec3 entry,Direction transfer){this(band,entry,transfer,null,null);}
    }
    public record Focus(BlockPos position,float yaw,float pitch){}
    public interface World {
        Optional<Band> detect(Vec3 position,Direction forward);
        boolean supported();
        boolean forwardBlocked(Direction forward);
        default boolean forwardLoaded(Direction forward){return true;}
        Optional<Next> next(Band current,Direction forward,java.util.Set<String> visited);
        boolean cropTargeted();
        default boolean connectorClear(Next next){return true;}
        default boolean approachClear(Next next){return true;}
        default boolean movementClear(Vec3 from, Vec3 goal, Direction actualDirection){return false;}
        default boolean turningMovementClear(Vec3 from,Vec3 proposed){return false;}
        default boolean requireLeftStart(){return false;}
        default Optional<Vec3> leftStartEntry(Band band,Direction forward,Vec3 from){return Optional.empty();}
        default boolean leftStartAlreadySatisfied(Band band,Direction forward,Vec3 position){return band.wall()==forward.rotateYCounterclockwise();}
        default Optional<Focus> nextFocus(Band band,Direction forward){return Optional.empty();}
        default boolean focusLoaded(Focus focus){return false;}
        default boolean focusRemoved(Focus focus){return false;}
        default boolean wallGapAhead(Band band,Direction forward){return false;}
    }
    private final FarmServices services;
    private final World world;
    private final BooleanSupplier aligned;
    private State state=State.BLOCKED;
    private Direction forward=Direction.SOUTH;
    private Band band;
    private Next next;
    private Focus focus;
    private int focusTicks;
    private Vec3 previous;
    private int stillTicks,travelTicks,passes;
    private boolean transferHandoffPending;
    private Vec3 startEntry;
    private final dev.veltraph.client.farming.movement.CapturedGourdTurnKeys turnKeys =
            new dev.veltraph.client.farming.movement.CapturedGourdTurnKeys();
    private String blocker="idle";
    private final dev.veltraph.client.farming.movement.CapturedGourdArrival arrival =
            new dev.veltraph.client.farming.movement.CapturedGourdArrival();
    private final java.util.Set<String> visited=new java.util.HashSet<>();
    public CapturedGourdWallPlanner(FarmServices services,World world,BooleanSupplier aligned){this.services=services;this.world=world;this.aligned=aligned;}
    public void start(Direction forward){
        this.forward=forward;band=world.detect(services.player().position(),forward).orElse(null);
        startEntry=null;
        state=band==null?State.BLOCKED:State.ALIGNING;
        if(band!=null&&world.requireLeftStart()&&!world.leftStartAlreadySatisfied(band,forward,services.player().position())){
            startEntry=world.leftStartEntry(band,forward,services.player().position()).orElse(null);
            state=startEntry==null?State.BLOCKED:State.START_ENTRY;
        }
        previous=null;stillTicks=travelTicks=passes=0;next=null;focus=null;focusTicks=0;
        visited.clear();if(band!=null)visited.add(band.key());
        blocker=band==null?"no loaded stem/slab wall and crop band":"aligning diagonal wall view";transferHandoffPending=false;turnKeys.reset();arrival.reset();release();
    }
    public void stop(){startEntry=null;transferHandoffPending=false;turnKeys.reset();arrival.reset();focus=null;state=State.BLOCKED;blocker="stopped";release();}
    public State state(){return state;}
    public Direction forward(){return forward;}
    public Band band(){return band;}
    public int passes(){return passes;}
    public String blocker(){return blocker;}
    public Next nextGoal(){return next;}
    public Optional<Float> focusPitch(){return focus==null?Optional.empty():Optional.of(focus.pitch());}
    public float yaw(){
        if(focus!=null)return focus.yaw();
        if(state==State.START_ENTRY)return (float)forward.toYaw();
        if(state==State.END_APPROACH)return (float)next.approachDirection().toYaw();
        if(state==State.END_TRANSFER&&next!=null)return (float)next.transfer().toYaw();
        if(band==null)return (float)forward.toYaw();
        double offset=world.wallGapAhead(band,forward)?45:35;
        return (float)(forward.toYaw()+(band.crop()==forward.rotateYClockwise()?offset:-offset));
    }
    public TurnDirection side(){
        if(band==null)return TurnDirection.FORWARD;
        double left=Math.toRadians(yaw()-45),right=Math.toRadians(yaw()+45);
        double l=-Math.sin(left)*band.wall().dx()+Math.cos(left)*band.wall().dz();
        double r=-Math.sin(right)*band.wall().dx()+Math.cos(right)*band.wall().dz();
        return l>r?TurnDirection.LEFT:TurnDirection.RIGHT;
    }
    public void tick(){
        if(state==State.BLOCKED){release();return;}
        if(state==State.START_ENTRY){
            if(++travelTicks>240){state=State.BLOCKED;blocker="left within-band startup timed out";release();return;}
            if(!world.supported()||!world.turningMovementClear(services.player().position(),startEntry)){arrival.reset();blocker="left within-band startup blocked or unsupported";release();return;}
            if(!aligned.getAsBoolean()){arrival.reset();blocker="aligning ordinary startup view";release();return;}
            Vec3 from=services.player().position();
            Direction left=forward.rotateYCounterclockwise();
            var action=arrival.observe(from,startEntry,left,services.player().horizontalSpeed());
            if(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.ARRIVED&&from.horizontalDistanceTo(startEntry)<=.10){
                String originalKey=band.key();
                band=world.detect(from,forward).orElse(null);startEntry=null;travelTicks=0;
                state=band!=null&&band.key().equals(originalKey)&&world.leftStartAlreadySatisfied(band,forward,services.player().position())?State.ALIGNING:State.BLOCKED;
                if(band!=null){visited.clear();visited.add(band.key());}
                arrival.reset();release();return;
            }
            release();
            if(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.FORWARD||action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.BACK){
                double yaw=Math.toRadians(services.rotation().yaw());double sign=action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.FORWARD?1:-1;
                double step=Math.min(.35,Math.max(.10,from.horizontalDistanceTo(startEntry)));
                Vec3 actualStep=new Vec3(from.x()+sign*Math.cos(yaw)*step,from.y(),from.z()+sign*Math.sin(yaw)*step);
                if(!world.turningMovementClear(from,actualStep)){arrival.reset();blocker="left startup actual-yaw momentum sweep unsafe";return;}
                services.input().setLeftHeld(sign>0);services.input().setRightHeld(sign<0);
            }
            services.input().setSneakHeld(false);
            blocker="ordinary walk to player-left side within first lane";return;
        }
        if((state==State.END_APPROACH||state==State.END_TRANSFER)&&++travelTicks>240){state=State.BLOCKED;blocker="authored end leg timed out";arrival.reset();release();return;}
        if(!world.supported()){arrival.reset();blocker="loaded supported wall corridor required";release();return;}
        if(state!=State.END_APPROACH&&state!=State.END_TRANSFER&&state!=State.FOCUSING&&!world.forwardLoaded(forward)){blocker="forward corridor unloaded";release();return;}
        if(state==State.FOCUSING){
            release();
            if(!world.focusLoaded(focus)){blocker="focused crop unloaded";return;}
            if(world.focusRemoved(focus)){focus=null;state=State.ALIGNING;previous=null;blocker="observed native outer crop removal";return;}
            if(++focusTicks>120){state=State.BLOCKED;blocker="outer crop not removed through native interaction";return;}
            if(aligned.getAsBoolean())services.breaking().setBreaking(world.cropTargeted());
            blocker="gradual native outer crop focus "+focus.position();return;
        }
        if(state==State.END_TRANSFER&&transferHandoffPending){
            transferHandoffPending=false;
            arrival.reset();
            previous=services.player().position();
            blocker="released orthogonal transfer handoff; observing fresh pose/view";
            release();
            return;
        }
        if(!aligned.getAsBoolean()){
            arrival.reset();
            if(state==State.END_APPROACH||state==State.END_TRANSFER){
                Vec3 goal=state==State.END_APPROACH?next.approach():next.entry();
                var keys=turnKeys.toward(services.player().position(),goal,services.rotation().yaw());
                release();
                if(keys!=null){
                    Vec3 from=services.player().position();double distance=from.horizontalDistanceTo(goal);
                    double step=Math.min(distance,Math.max(.35,services.player().horizontalSpeed()*2.5));
                    Vec3 proposed=new Vec3(from.x()+keys.dx()*step,from.y(),from.z()+keys.dz()*step);
                    if(world.turningMovementClear(from,proposed)){
                        services.input().setForwardHeld(keys.forward());services.input().setBackHeld(keys.back());
                        services.input().setLeftHeld(keys.left());services.input().setRightHeld(keys.right());
                        services.input().setSneakHeld(false);
                        blocker="safe ordinary walk overlapping gradual end turn";return;
                    }
                }
                blocker="tight corner holds while gradual end view aligns";return;
            }
            blocker="aligning diagonal view";release();return;
        }
        Vec3 position=services.player().position();
        if(state==State.END_APPROACH){
            var action=arrival.observe(position,next.approach(),next.approachDirection(),services.player().horizontalSpeed());
            if(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.ARRIVED&&position.horizontalDistanceTo(next.approach())<=.10){
                if(!world.connectorClear(next)){arrival.reset();blocker="settled approach connector obstructed or unloaded";release();return;}
                state=State.END_TRANSFER;travelTicks=0;transferHandoffPending=true;arrival.reset();release();return;
            }
            walkArrival(action,position,next.approach(),next.approachDirection());
            return;
        }
        if(state==State.END_TRANSFER){
            var action=arrival.observe(position,next.entry(),next.transfer(),services.player().horizontalSpeed());
            if(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.ARRIVED&&position.horizontalDistanceTo(next.entry())<=.10){
                if(!world.movementClear(position,position,next.transfer())){arrival.reset();blocker="settled endpoint body obstructed or unsupported";release();return;}
                band=next.band();visited.add(band.key());next=null;forward=forward.opposite();state=State.ALIGNING;previous=null;arrival.reset();release();return;
            }
            walkArrival(action,position,next.entry(),next.transfer());
            return;
        }
        state=State.FARMING;
        var candidate=world.nextFocus(band,forward);
        if(candidate.isPresent()){focus=candidate.get();focusTicks=0;state=State.FOCUSING;release();blocker="aligning loaded legitimate outer crop";return;}
        if(world.forwardBlocked(forward)){
            // Stop at a known unsafe/capped forward cell before native inertia leaves the
            // supported bridge. Stationary evidence must not require walking into a void.
            release();
            stillTicks=previous!=null&&position.horizontalDistanceTo(previous)<.015?stillTicks+1:0;
            if(stillTicks>=3){
                passes++;next=world.next(band,forward,java.util.Set.copyOf(visited)).orElse(null);
                if(next==null){state=State.BLOCKED;blocker="no next loaded crop band on authored end walkway";release();return;}
                state=next.approach()==null?State.END_TRANSFER:State.END_APPROACH;transferHandoffPending=false;travelTicks=stillTicks=0;arrival.reset();release();return;
            }
            previous=position;blocker="settling on supported authored end";return;
        }else stillTicks=0;
        previous=position;
        desired(true,side(),world.cropTargeted());blocker="diagonal wall pressure "+band.wall()+" crop "+band.crop();
    }
    private void walkArrival(dev.veltraph.client.farming.movement.CapturedGourdArrival.Action action,
            Vec3 position, Vec3 goal, Direction direction){
        release();
        if(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.ARRIVED&&position.horizontalDistanceTo(goal)>.10){
            var keys=turnKeys.toward(position,goal,services.rotation().yaw());
            if(keys!=null){Vec3 proposed=new Vec3(position.x()+keys.dx()*.20,position.y(),position.z()+keys.dz()*.20);
                if(world.turningMovementClear(position,proposed)){services.input().setForwardHeld(keys.forward());services.input().setBackHeld(keys.back());services.input().setLeftHeld(keys.left());services.input().setRightHeld(keys.right());blocker="safe cross-axis recenter to full end point";return;}}
            arrival.reset();blocker="cross-axis end alignment obstructed";return;
        }
        if(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.RELEASE){blocker="braking/settling ordinary end walk";return;}
        Direction actual=action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.BACK?direction.opposite():direction;
        if(!world.movementClear(position,goal,actual)){arrival.reset();blocker="actual corrective end path obstructed or unsupported";return;}
        services.input().setForwardHeld(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.FORWARD);
        services.input().setBackHeld(action==dev.veltraph.client.farming.movement.CapturedGourdArrival.Action.BACK);
        services.input().setSneakHeld(false);
        blocker="ordinary end walk "+action;
    }
    private void desired(boolean f,TurnDirection side,boolean attack){
        services.input().setForwardHeld(f);services.input().setBackHeld(false);
        services.input().setLeftHeld(side==TurnDirection.LEFT);services.input().setRightHeld(side==TurnDirection.RIGHT);
        services.input().setJumpHeld(false);services.input().setSneakHeld(false);services.input().setSprintHeld(false);
        services.breaking().setBreaking(attack);
    }
    private void release(){services.input().releaseAllMovement();services.breaking().setBreaking(false);}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "services", services,
                "world", world,
                "aligned", aligned,
                "state", state,
                "forward", forward,
                "band", band,
                "next", next,
                "focus", focus,
                "focusTicks", focusTicks,
                "previous", previous,
                "stillTicks", stillTicks,
                "travelTicks", travelTicks,
                "passes", passes,
                "blocker", blocker,
                "visited", visited,
                "arrival", arrival.maintenanceState(),
                "transferHandoffPending", transferHandoffPending,
                "turnKeys", turnKeys.maintenanceState(),"startEntry",startEntry);
    }
}
