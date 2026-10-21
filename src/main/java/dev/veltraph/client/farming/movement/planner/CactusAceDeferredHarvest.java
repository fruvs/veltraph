package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import java.util.*;

/** Recover only actual crop coordinates interrupted by a support correction, using ordinary keys. */
public final class CactusAceDeferredHarvest {
    public enum State { IDLE, SETTLING, APPROACH_PULSE, HARVESTING, COMPLETE, CANCELLED }
    public record Keys(boolean left,boolean right,boolean sneak,boolean attack) {public static final Keys NONE=new Keys(false,false,false,false);}
    private final Set<BlockPos> targets=new HashSet<>();
    private State state=State.IDLE;private Direction facing;private boolean alongX;private int corridor,supportY;
    private long started,lastTick;private int pulses;private Vec3 previous,origin;private String reason="idle";
    public State state(){return state;}public boolean active(){return state==State.SETTLING||state==State.APPROACH_PULSE||state==State.HARVESTING;}
    public String reason(){return reason;}public Set<BlockPos> targets(){return Set.copyOf(targets);}
    public void reset(){targets.clear();state=State.IDLE;previous=null;reason="reset";}
    private Keys cancel(String why){state=State.CANCELLED;reason=why;return Keys.NONE;}
    public boolean begin(WorldService world,Vec3 feet,Direction heading,Collection<BlockPos> deferred,long tick){
        reset();facing=heading;alongX=heading==Direction.NORTH||heading==Direction.SOUTH;origin=previous=feet;
        if(feet==null||heading==null||!heading.isHorizontal()){cancel("invalid deferred crop context");return false;}
        corridor=(int)Math.floor(transverse(feet));supportY=(int)Math.floor(feet.y()-.025);started=lastTick=tick;targets.addAll(deferred);
        if(!valid(world,feet)){cancel("no restored authored deferred-harvest footing");return false;}
        state=State.SETTLING;reason="settle restored footing before deferred crop approach";return true;
    }
    public Keys tick(WorldService world,Vec3 feet,Vec3 eye,double range,long tick,Set<BlockPos> pending,Optional<BlockPos> freshUpper){
        if(!active())return Keys.NONE;
        if(tick<lastTick||tick-started>=80){return cancel("bounded deferred-harvest correction limit");}lastTick=tick;
        if(!valid(world,feet)||eye==null||!Double.isFinite(range)||range<=0||range>64)return cancel("deferred crop lost loaded original corridor/range witness");
        for(var target:List.copyOf(targets)){
            if(!world.isLoaded(target)||!world.isLoaded(target.down()))return cancel("deferred original crop unloaded");
            if(!pending.contains(target)&&!world.isBlock(target,"minecraft:cactus")){
                if(!world.isPassable(target))return cancel("deferred crop replaced by a foreign structure");targets.remove(target);
            }
        }
        if(targets.isEmpty()){state=State.COMPLETE;reason="deferred actual crop coordinates cleared and acknowledged";return Keys.NONE;}
        if(state==State.APPROACH_PULSE){state=State.SETTLING;reason="release local precision approach; observe ray/footing";}
        double motion=previous==null?Double.POSITIVE_INFINITY:feet.horizontalDistanceTo(previous);previous=feet;
        if(!pending.isEmpty()){state=State.HARVESTING;reason="await actual deferred crop acknowledgment";return new Keys(false,false,false,true);}
        var target=targets.stream().min(Comparator.comparingDouble(p->Math.abs(along(p)-along(feet)))).orElseThrow();
        if(freshUpper.filter(targets::contains).isPresent()&&eye.y()-feet.y()>1.55&&motion<=.025){
            state=State.HARVESTING;reason="normal standing native ray reaches interrupted upper crop";return new Keys(false,false,false,true);
        }
        if(motion>.025||eye.y()-feet.y()<=1.55){reason="observe native stance and local motion settling";return Keys.NONE;}
        Vec3 columnFoot=alongX?new Vec3(target.x()+.5,feet.y(),feet.z()):new Vec3(feet.x(),feet.y(),target.z()+.5);
        Vec3 projectedEye=new Vec3(columnFoot.x(),eye.y(),columnFoot.z());
        Vec3 cropCenter=new Vec3(target.x()+.5,target.y()+.5,target.z()+.5);
        if(projectedEye.distanceTo(cropCenter)>range)return cancel("deferred upper exceeds unchanged native ray reach from validated column");
        double error=along(target)+.5-along(feet);
        if(Math.abs(error)<.025)return cancel("aligned deferred coordinate has no actual upper ray");
        if(Math.abs(error)>Math.min(4,range))return cancel("deferred target exceeds original local observation window");
        if(pulses>=6)return cancel("bounded deferred precision approaches exhausted");
        int sign=error>0?1:-1;
        Direction travel=alongX?(sign>0?Direction.EAST:Direction.WEST):(sign>0?Direction.SOUTH:Direction.NORTH);
        Vec3 endpoint=alongX?new Vec3(target.x()+.5,feet.y(),feet.z()):new Vec3(feet.x(),feet.y(),target.z()+.5);
        for(double step=0;step<=Math.abs(error)+.001;step+=.21){
            var point=new Vec3(feet.x()+travel.dx()*Math.min(step,Math.abs(error)),feet.y(),feet.z()+travel.dz()*Math.min(step,Math.abs(error)));
            if(!valid(world,point))return cancel("deferred local approach clipped or lacks original slabs");
        }
        if(!valid(world,endpoint))return cancel("deferred column landing unsupported");
        boolean left=travel==facing.rotateYCounterclockwise();pulses++;state=State.APPROACH_PULSE;reason="ordinary local precision strafe toward interrupted source column";
        return new Keys(left,!left,true,false);
    }
    private double transverse(Vec3 p){return alongX?p.z():p.x();}private double along(Vec3 p){return alongX?p.x():p.z();}private double along(BlockPos p){return alongX?p.x():p.z();}
    private boolean valid(WorldService world,Vec3 feet){
        if(feet==null||!Double.isFinite(feet.x())||!Double.isFinite(feet.y())||!Double.isFinite(feet.z())||Math.floor(transverse(feet))!=corridor
                ||Math.abs(feet.y()-(supportY+.5))>.025||feet.horizontalDistanceTo(origin)>4.5||!world.bodyClear(feet,.3,1.8)||!world.hasGroundSupport(feet,.3))return false;
        var floor=new BlockPos((int)Math.floor(feet.x()),supportY,(int)Math.floor(feet.z()));
        return world.isLoaded(floor)&&world.isBlock(floor,"minecraft:quartz_slab")&&world.blockProperty(floor,"type").filter("bottom"::equals).isPresent();
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "targets", targets,
                "state", state,
                "facing", facing,
                "alongX", alongX,
                "corridor", corridor,
                "supportY", supportY,
                "started", started,
                "lastTick", lastTick,
                "pulses", pulses,
                "previous", previous,
                "origin", origin,
                "reason", reason);
    }
}
