package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Creator Ace A/D course: native fluid/collision transports the actor; never a coordinate route. */
public final class CactusAcePlanner {
    public enum State { STOPPED, ACTIVE, WAITING_GEOMETRY, HARVESTING_FENCE, AT_END }
    private State state=State.STOPPED;
    private LaneSide side=LaneSide.RIGHT;
    private Direction facing=Direction.NORTH;
    private float yaw;
    private boolean finalGuideSeen;
    private int reversals;
    private Vec3 lastReversal;
    private String reason="not started";
    public void start(float currentYaw,LaneSide firstSide) {
        yaw=Angles.snapToCardinal(currentYaw);facing=Direction.fromYaw(yaw);side=firstSide;
        state=State.WAITING_GEOMETRY;finalGuideSeen=false;reversals=0;lastReversal=null;reason="source entry";
    }
    public void stop(){state=State.STOPPED;reason="disabled";}
    public State state(){return state;}
    public LaneSide side(){return side;}
    public float yaw(){return yaw;}
    public int reversals(){return reversals;}
    public String reason(){return reason;}
    public boolean tick(WorldService world,Vec3 feet) {return tick(world,feet,false);}
    public boolean tick(WorldService world,Vec3 feet,boolean reachableOrUnacknowledgedUpper) {
        if(state==State.STOPPED||state==State.AT_END)return false;
        if(!loaded(world,feet)||!world.bodyClear(feet,.3,1.8))return waitFor("unloaded or occupied actor body");
        boolean wet=world.isWater(feet.toBlockPos())||world.isWater(feet.toBlockPos().up());
        if(!wet&&!world.hasGroundSupport(feet,.3))return waitFor("unsupported dry footing");
        BlockPos p=feet.toBlockPos(),support=new Vec3(feet.x(),feet.y()-.025,feet.z()).toBlockPos();
        String floor=world.blockId(support);
        if(!wet&&!floor.equals("minecraft:quartz_slab")&&!floor.equals("minecraft:player_head")&&!floor.equals("minecraft:oak_fence")
                &&!floor.equals("minecraft:snow"))return waitFor("not an authored corridor support");
        // A full-footprint support witness may come from a tiny overlap with the front bank.
        // On a source bottom-slab corridor that would lift feet by .5 and make the original
        // 1.5-high fence climbable. Observe the actual source slab state/height; never change pose.
        if(!wet&&floor.equals("minecraft:quartz_slab")){
            var type=world.blockProperty(support,"type");
            if(type.isEmpty()||!type.get().equals("bottom"))return waitFor("unknown or unauthored corridor slab state");
            if(Math.abs(feet.y()-(support.y()+.5))>.025)return waitFor("feet raised above authored bottom-slab corridor; deny edge traversal");
        }
        // The red glass/terracotta bank marks the last course. Observe it, but finish the actual
        // lateral run before declaring END; neither the color nor a timer substitutes for end contact.
        BlockPos front=new BlockPos(p.x()+facing.dx(),support.y(),p.z()+facing.dz());
        if(world.isLoaded(front)&&world.isLoaded(front.down())&&world.isBlock(front,"minecraft:red_stained_glass")
                &&world.isBlock(front.down(),"minecraft:red_terracotta"))finalGuideSeen=true;
        Direction lateral=side==LaneSide.LEFT?facing.rotateYCounterclockwise():facing.rotateYClockwise();
        Vec3 contact=new Vec3(feet.x()+lateral.dx()*.001,feet.y(),feet.z()+lateral.dz()*.001);
        if(!loaded(world,contact))return waitFor("unknown lateral contact");
        if(!world.bodyClear(contact,.3,1.8)) {
            // Only the original fence end admits a reversal/reset. A crop or ceiling obstruction
            // releases input instead of turning the actor into a convenient neighboring passage.
            if(!fenceNear(world,feet,lateral))return waitFor("blocked before an authored fence end");
            if(reachableOrUnacknowledgedUpper){state=State.HARVESTING_FENCE;reason="native fence; finish reachable and unacknowledged upper targets";return true;}
            if(finalGuideSeen){state=State.AT_END;reason="complete red-bank fence END; await saved source plot reset";return false;}
            if(lastReversal!=null&&feet.horizontalDistanceTo(lastReversal)<.8)return waitFor("same end contact");
            Vec3 other=new Vec3(feet.x()-lateral.dx()*.12,feet.y(),feet.z()-lateral.dz()*.12);
            if(!loaded(world,other)||!world.bodyClear(other,.3,1.8))return waitFor("no opposite native continuation");
            side=side==LaneSide.LEFT?LaneSide.RIGHT:LaneSide.LEFT;lastReversal=feet;reversals++;
        }
        state=State.ACTIVE;reason=wet?"native author water transfer":"native author lateral harvest";return true;
    }
    private boolean waitFor(String why){state=State.WAITING_GEOMETRY;reason=why;return false;}
    private static boolean loaded(WorldService w,Vec3 feet) {
        if(feet==null||!Double.isFinite(feet.x())||!Double.isFinite(feet.y())||!Double.isFinite(feet.z()))return false;
        for(double x:new double[]{-.3,.3})for(double z:new double[]{-.3,.3})for(double y:new double[]{-.025,0,1.8})
            if(!w.isLoaded(new Vec3(feet.x()+x,feet.y()+y,feet.z()+z).toBlockPos()))return false;
        return true;
    }
    private static boolean fenceNear(WorldService w,Vec3 feet,Direction lateral) {
        BlockPos p=feet.toBlockPos();
        for(int dy=-1;dy<=0;dy++)for(int step=0;step<=1;step++) {
            BlockPos q=new BlockPos(p.x()+lateral.dx()*step,p.y()+dy,p.z()+lateral.dz()*step);
            if(!w.isLoaded(q))return false;if(w.isBlock(q,"minecraft:oak_fence"))return true;
        }
        return false;
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "state", state,
                "side", side,
                "facing", facing,
                "yaw", yaw,
                "finalGuideSeen", finalGuideSeen,
                "reversals", reversals,
                "lastReversal", lastReversal,
                "reason", reason);
    }
}
