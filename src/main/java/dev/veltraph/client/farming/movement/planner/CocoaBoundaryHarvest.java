package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

/** Local current-map view adaptation: loaded native shape witnesses, never an attack grant. */
public final class CocoaBoundaryHarvest {
    public enum State { IDLE, FOCUSING, RESTORING, BLOCKED }
    public record Focus(BlockPos target,Vec3 point){}
    private State state=State.IDLE;
    private BlockPos target;
    private boolean crosswiseOnly;
    private long targetSince;
    private String reason="";

    public Optional<Focus> observe(WorldService world,Vec3 feet,Vec3 eye,double range,
            Direction heading,boolean ready,long now){
        if(state==State.BLOCKED)return Optional.empty();
        if(!ready||!finite(feet)||!finite(eye)||!Double.isFinite(range)||range<=0||range>64){
            if(state!=State.IDLE){state=State.BLOCKED;reason="Boundary crop lost supported native player context";}
            return Optional.empty();
        }
        if(state==State.RESTORING)return Optional.empty();
        var origin=feet.toBlockPos();
        if(state==State.IDLE){
            boolean gate=nearGate(world,origin);
            crosswiseOnly=!gate;
            if(!gate&&!nearCrosswiseCrop(world,origin,heading))return Optional.empty();
        }
        if(target!=null&&mature(world,target)){
            var point=validPoint(world,target,eye,range);
            if(point.isPresent()){
                if(now-targetSince>8000){state=State.BLOCKED;reason="Native boundary Cocoa target did not clear";return Optional.empty();}
                return Optional.of(new Focus(target,point.get()));
            }
        }
        target=null;
        var candidates=new ArrayList<BlockPos>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=2;dy<=6;dy++){
            var cell=origin.add(dx,dy,dz);
            if(mature(world,cell)&&(!crosswiseOnly||facesHeading(world,cell,heading)))candidates.add(cell);
        }
        candidates.sort(Comparator.comparingDouble(cell->center(cell).distanceTo(eye)));
        for(var cell:candidates){
            var point=validPoint(world,cell,eye,range);
            if(point.isEmpty())continue;
            target=cell;targetSince=now;state=State.FOCUSING;reason="Native boundary Cocoa outline focus";
            return Optional.of(new Focus(cell,point.get()));
        }
        if(state==State.FOCUSING){state=State.RESTORING;reason="Restoring posted Cocoa row camera";}
        return Optional.empty();
    }
    private static Optional<Vec3> validPoint(WorldService world,BlockPos target,Vec3 eye,double range){
        if(!mature(world,target))return Optional.empty();
        return world.interactionPoint(target,eye,range).filter(CocoaBoundaryHarvest::finite)
                .filter(point->point.distanceTo(eye)>1e-9&&point.distanceTo(eye)<=range+1e-9);
    }
    private static boolean nearGate(WorldService world,BlockPos origin){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=1;dy<=2;dy++){
            var p=origin.add(dx,dy,dz);
            if(world.isLoaded(p)&&world.isBlock(p,"minecraft:oak_trapdoor")
                    &&world.blockProperty(p,"open").filter("true"::equals).isPresent())return true;
        }
        return false;
    }
    private static boolean nearCrosswiseCrop(WorldService world,BlockPos origin,Direction heading){
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            var p=origin.add(dx,2,dz);if(mature(world,p)&&facesHeading(world,p,heading))return true;
        }
        return false;
    }
    private static boolean facesHeading(WorldService world,BlockPos p,Direction heading){
        return world.blockProperty(p,"facing").filter(heading.name().toLowerCase(java.util.Locale.ROOT)::equals).isPresent();
    }
    private static boolean mature(WorldService world,BlockPos p){
        return world.isLoaded(p)&&world.isBlock(p,"minecraft:cocoa")&&world.blockProperty(p,"age").filter("2"::equals).isPresent();
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static Vec3 center(BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}
    public void restored(){if(state==State.RESTORING){state=State.IDLE;reason="";}}
    public void reset(){state=State.IDLE;target=null;crosswiseOnly=false;reason="";}
    public State state(){return state;}
    public String status(){return state+" "+reason+(target==null?"":" | "+target);}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "state", state,
                "target", target,
                "crosswiseOnly", crosswiseOnly,
                "targetSince", targetSince,
                "reason", reason);
    }
}
