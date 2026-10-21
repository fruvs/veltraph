package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;

/** Read-only Pumpkin getBlockPos16 bytecode and GeometryEngine test71/test191 geometry. */
public final class GourdSourceGeometry {
    private GourdSourceGeometry(){}
    public static BlockPos body(Vec3 position) {
        return new Vec3(position.x(),position.y()-.2+.35,position.z()).toBlockPos();
    }
    public static BlockPos sideProbe(Vec3 position,float yaw,TurnDirection side) {
        return body(position).offset(side.resolve(Direction.fromYaw(yaw)));
    }
    public static boolean endAir(WorldService world,Vec3 position,float yaw,TurnDirection side) {
        BlockPos probe=sideProbe(position,yaw,side),base=body(position).down();
        return world.isLoaded(base)&&world.isLoaded(probe)&&world.isLoaded(probe.up())
                &&airOrCarpet(world.blockId(probe))&&airOrCarpet(world.blockId(probe.up()));
    }
    private static boolean airOrCarpet(String id){return id.equals("minecraft:air")||id.endsWith("_carpet");}
    public static boolean corner(Vec3 current,Vec3 previous) {
        return current!=null&&previous!=null&&corner(current.x(),current.z())&&corner(previous.x(),previous.z());
    }
    private static boolean corner(double x,double z){return edge(x)&&edge(z);}
    private static boolean edge(double coordinate){
        if(!Double.isFinite(coordinate))return false;
        long fraction=Math.round(Math.abs(coordinate-Math.floor(coordinate))*1000);
        return fraction==300||fraction==700;
    }
}
