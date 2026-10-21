package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Local ordinary-key correction over a measured original slab; never writes actor/world state. */
public final class CactusAceRecentering {
    // Current 4.0.30 MovementDirection near-middle body: .5 +/- .21. Optional .3 branch is unused.
    public static final double SOURCE_RADIUS=.21;
    // Clean adapter limits, not claimed original timing constants. A correction cannot become a route.
    public static final int MAX_TICKS=40,MAX_PULSES=3;
    public enum State { IDLE, SETTLING, PULSING, VERIFYING, COMPLETE, CANCELLED }
    public record Keys(boolean forward,boolean back,boolean sneak) {public static final Keys NONE=new Keys(false,false,false);}
    private State state=State.IDLE;
    private Direction facing;
    private boolean axisX;
    private int corridor,supportY,pulses;
    private long started,lastTick;
    private Vec3 previous,pulseOrigin;
    private String reason="idle";
    public State state(){return state;}
    public boolean active(){return state==State.SETTLING||state==State.PULSING||state==State.VERIFYING;}
    public String reason(){return reason;}
    public int pulses(){return pulses;}
    public void reset(){state=State.IDLE;previous=null;pulseOrigin=null;pulses=0;reason="reset";}
    public void cancel(String why){state=State.CANCELLED;reason=why;}
    public boolean begin(WorldService world,Vec3 feet,Direction heading,long tick){
        reset();facing=heading;axisX=heading==Direction.EAST||heading==Direction.WEST;
        if(feet==null||heading==null||!heading.isHorizontal()){cancel("invalid correction pose");return false;}
        corridor=(int)Math.floor(axis(feet));supportY=(int)Math.floor(feet.y()-.025);started=lastTick=tick;
        if(!valid(world,feet)){cancel("no loaded authored slab correction footprint");return false;}
        previous=feet;state=State.SETTLING;reason="release inputs; observe native settling";return true;
    }
    public Keys tick(WorldService world,Vec3 feet,boolean onGround,long tick){return tick(world,feet,onGround,tick,true);}
    public Keys tick(WorldService world,Vec3 feet,boolean onGround,long tick,boolean acknowledgmentsClear){
        if(!active())return Keys.NONE;
        if(tick<lastTick||tick-started>=MAX_TICKS){cancel("bounded correction deadline");return Keys.NONE;}
        lastTick=tick;
        if(!valid(world,feet)){cancel("correction left loaded original corridor or clipped");return Keys.NONE;}
        if(state==State.PULSING){state=State.VERIFYING;reason="release precision pulse; observe native footing";}
        double motion=previous==null?Double.POSITIVE_INFINITY:feet.horizontalDistanceTo(previous);
        double vertical=previous==null?Double.POSITIVE_INFINITY:Math.abs(feet.y()-previous.y());previous=feet;
        if(pulseOrigin!=null&&Math.abs(axis(feet)-axis(pulseOrigin))>.5){cancel("precision movement exceeded local cell bound");return Keys.NONE;}
        if(!acknowledgmentsClear){reason="await native block acknowledgments before physical correction";return Keys.NONE;}
        boolean near=Math.abs(axis(feet)-(corridor+.5))<=SOURCE_RADIUS;
        boolean slab=Math.abs(feet.y()-(supportY+.5))<=.025&&world.hasGroundSupport(feet,.3);
        if(near&&slab&&onGround&&motion<=.025&&vertical<=.025){state=State.COMPLETE;reason="measured near-middle and original slab footing";return Keys.NONE;}
        if(!onGround||motion>.025||vertical>.025){reason="observe ordinary settling before another pulse";return Keys.NONE;}
        if(pulses>=MAX_PULSES){cancel("bounded precision pulses did not restore footing");return Keys.NONE;}
        double error=corridor+.5-axis(feet);
        if(Math.abs(error)<.025){cancel("centered pose still lacks original slab support");return Keys.NONE;}
        int sign=error>0?1:-1;Vec3 probe=axisX?new Vec3(feet.x()+sign*SOURCE_RADIUS,feet.y(),feet.z()):new Vec3(feet.x(),feet.y(),feet.z()+sign*SOURCE_RADIUS);
        if(!loaded(world,probe)||!world.bodyClear(probe,.3,1.8)){cancel("precision correction ray footprint blocked");return Keys.NONE;}
        boolean forward=sign*(axisX?facing.dx():facing.dz())>0;
        pulseOrigin=feet;pulses++;state=State.PULSING;reason="ordinary precision crouch toward source near-middle";
        return new Keys(forward,!forward,true);
    }
    private double axis(Vec3 p){return axisX?p.x():p.z();}
    private boolean valid(WorldService world,Vec3 feet){
        if(feet==null||!Double.isFinite(feet.x())||!Double.isFinite(feet.y())||!Double.isFinite(feet.z())
                ||Math.floor(axis(feet))!=corridor||feet.y()<supportY+.47||feet.y()>supportY+1.025||!loaded(world,feet)
                ||!world.bodyClear(feet,.3,1.8))return false;
        var slab=new BlockPos((int)Math.floor(feet.x()),supportY,(int)Math.floor(feet.z()));
        if(!world.isLoaded(slab)||!world.isBlock(slab,"minecraft:quartz_slab")||!world.blockProperty(slab,"type").filter("bottom"::equals).isPresent())return false;
        Vec3 landing=axisX?new Vec3(corridor+.5,supportY+.5,feet.z()):new Vec3(feet.x(),supportY+.5,corridor+.5);
        return loaded(world,landing)&&world.bodyClear(landing,.3,1.8)&&world.hasGroundSupport(landing,.3);
    }
    private static boolean loaded(WorldService world,Vec3 p){
        for(double x:new double[]{-.3,.3})for(double z:new double[]{-.3,.3})for(double y:new double[]{-.025,0,1.8})
            if(!world.isLoaded(new Vec3(p.x()+x,p.y()+y,p.z()+z).toBlockPos()))return false;
        return true;
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "state", state,
                "facing", facing,
                "axisX", axisX,
                "corridor", corridor,
                "supportY", supportY,
                "pulses", pulses,
                "started", started,
                "lastTick", lastTick,
                "previous", previous,
                "pulseOrigin", pulseOrigin,
                "reason", reason);
    }
}
