package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

/** Local straight-lane role in the immutable 2026 author ring. Corners and stairs are handled separately by loaded continuation queries. */
public final class MushroomRingLaneClassifier {
    public enum Lane { NON_TRAPDOOR, TRAPDOOR, AMBIGUOUS, UNKNOWN }
    public enum CropFacing { READY, WRONG_SIDE, UNKNOWN }
    /** Axis of a previously validated straight lane; null for a corner or incomplete context. */
    public dev.veltraph.client.world.Direction axis(WorldService world,Vec3 feet,Lane role) {
        var p=feet.toBlockPos();boolean x=straight(world,p,1,0,role),z=straight(world,p,0,1,role);
        return x==z?null:x?dev.veltraph.client.world.Direction.EAST:dev.veltraph.client.world.Direction.SOUTH;
    }

    /** Readiness depends on the persistent mycelium bed, so harvested mushrooms do not invalidate it. */
    public CropFacing cropFacing(WorldService world,Vec3 feet,float fixedYaw) {
        Lane role=classify(world,feet);
        if(role==Lane.UNKNOWN)return CropFacing.UNKNOWN;
        if(role==Lane.AMBIGUOUS||!Float.isFinite(fixedYaw))return CropFacing.WRONG_SIDE;
        BlockPos floor=feet.toBlockPos();
        boolean alongX=straight(world,floor,1,0,role);
        double normal=alongX?Math.cos(Math.toRadians(fixedYaw)):-Math.sin(Math.toRadians(fixedYaw));
        if(Math.abs(normal)<.01)return CropFacing.WRONG_SIDE;
        BlockPos bed=new BlockPos(floor.x()+(alongX?0:(int)Math.signum(normal)),floor.y()+1,
                floor.z()+(alongX?(int)Math.signum(normal):0));
        if(!world.isLoaded(bed))return CropFacing.UNKNOWN;
        String bedId=world.blockId(bed);
        if(bedId.isEmpty())return CropFacing.UNKNOWN;
        return bedId.equals("minecraft:mycelium")?CropFacing.READY:CropFacing.WRONG_SIDE;
    }

    public Lane classify(WorldService world, Vec3 feet) {
        if (feet == null || !Double.isFinite(feet.x()) || !Double.isFinite(feet.y()) || !Double.isFinite(feet.z()))
            return Lane.UNKNOWN;
        BlockPos floor = feet.toBlockPos();
        // This authored lane uses bottom quartz slabs; stairs and other fractional support are not lanes.
        if (Math.abs(feet.y() - floor.y() - .5) > .025) return Lane.AMBIGUOUS;
        for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) for(int y=0;y<=2;y++)
            if (!world.isLoaded(new BlockPos(floor.x()+x,floor.y()+y,floor.z()+z))) return Lane.UNKNOWN;
        if (!world.bodyClear(feet,.3,1.8) || !world.hasGroundSupport(feet,.3)) return Lane.AMBIGUOUS;
        Lane role = cellRole(world,floor);
        if (role == Lane.AMBIGUOUS || role == Lane.UNKNOWN) return role;
        boolean alongX = straight(world,floor,1,0,role);
        boolean alongZ = straight(world,floor,0,1,role);
        // An intersection or incomplete end cannot establish which lane the actor occupies.
        return alongX == alongZ ? Lane.AMBIGUOUS : role;
    }

    private boolean straight(WorldService world,BlockPos floor,int dx,int dz,Lane role) {
        if (cellRole(world,new BlockPos(floor.x()-dx,floor.y(),floor.z()-dz)) != role
                || cellRole(world,new BlockPos(floor.x()+dx,floor.y(),floor.z()+dz)) != role) return false;
        // Actual adjacent blockers bound the one-block lane; a slab patch alone is insufficient.
        BlockPos sideA=new BlockPos(floor.x()-dz,floor.y()+1,floor.z()+dx);
        BlockPos sideB=new BlockPos(floor.x()+dz,floor.y()+1,floor.z()-dx);
        return !world.isPassable(sideA) && !world.isPassable(sideB);
    }

    private Lane cellRole(WorldService world,BlockPos floor) {
        String support=world.blockId(floor),guide=world.blockId(floor.up());
        if (support.isEmpty() || guide.isEmpty()) return Lane.UNKNOWN;
        if (!support.equals("minecraft:quartz_slab")) return Lane.AMBIGUOUS;
        if (guide.equals("minecraft:oak_trapdoor")) return Lane.TRAPDOOR;
        if (guide.equals("minecraft:air")) return Lane.NON_TRAPDOOR;
        return Lane.AMBIGUOUS;
    }
}
