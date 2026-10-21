package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;

/** Conservative continuous local rectangle covers momentum/input hull and player footprint. */
public final class CapturedGourdSweptWalk {
    private CapturedGourdSweptWalk() {}

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y())
                && Double.isFinite(point.z()) && Math.abs(point.x()) <= 30_000_000
                && Math.abs(point.z()) <= 30_000_000 && Math.abs(point.y()) <= 4096;
    }

    public static boolean clear(WorldService world, Vec3 from, Vec3 to) {
        if (world == null || !finite(from) || !finite(to) || Math.abs(from.y()-to.y()) > .025
                || from.horizontalDistanceTo(to) > 1.5) return false;
        return rectangleClear(world, from.y(), Math.min(from.x(),to.x()), Math.max(from.x(),to.x()),
                Math.min(from.z(),to.z()), Math.max(from.z(),to.z()));
    }

    public static boolean momentumClear(WorldService world, Vec3 from, Vec3 proposed, Optional<Vec3> velocity) {
        if (world == null || !finite(from) || !finite(proposed) || velocity == null || velocity.isEmpty()
                || !finite(velocity.get()) || Math.abs(velocity.get().y()) > 1e-9
                || Math.abs(from.y()-proposed.y()) > .025) return false;
        var old = velocity.get();
        double speed = Math.hypot(old.x(),old.z());
        if (speed > .6) return false;
        double nx = proposed.x()-from.x(), nz = proposed.z()-from.z();
        if (Math.hypot(nx,nz) > 1.5) return false;
        // The convex momentum/input hull is inside this axis-aligned rectangle for all
        // coast coefficients [0,2.5] and input coefficients [0,1]. Check its entire interior.
        double minX = from.x()+Math.min(0,old.x()*2.5)+Math.min(0,nx);
        double maxX = from.x()+Math.max(0,old.x()*2.5)+Math.max(0,nx);
        double minZ = from.z()+Math.min(0,old.z()*2.5)+Math.min(0,nz);
        double maxZ = from.z()+Math.max(0,old.z()*2.5)+Math.max(0,nz);
        return rectangleClear(world,from.y(),minX,maxX,minZ,maxZ);
    }

    private static boolean rectangleClear(WorldService world, double y, double minX, double maxX,
            double minZ, double maxZ) {
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxZ) || maxX-minX > 1.5 || maxZ-minZ > 1.5
                || Math.abs(y-Math.rint(y)) > .025) return false;
        int xSteps=Math.max(1,(int)Math.ceil((maxX-minX)/.1));
        int zSteps=Math.max(1,(int)Math.ceil((maxZ-minZ)/.1));
        // Every possible hull center is within .05 on each axis of a grid sample.
        // Native probe expands actual actor body and erodes actual support footprint
        // by .050001, covering collision and preserving a contained support witness
        // for every neighboring center without assuming actor dimensions.
        for(int x=0;x<=xSteps;x++) for(int z=0;z<=zSteps;z++) {
            double cx=minX+(maxX-minX)*x/xSteps;
            double cz=minZ+(maxZ-minZ)*z/zSteps;
            var feet=new Vec3(cx,y,cz);
            if(!world.envelopeProbe(feet,.050001))return false;
        }
        return true;
    }
}
