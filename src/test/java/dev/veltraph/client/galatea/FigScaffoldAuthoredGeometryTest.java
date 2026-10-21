package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Physical counterpart model checks every allowed source central offset, not the spawn alone. */
class FigScaffoldAuthoredGeometryTest {
    private final BlockPos base=new BlockPos(2,70,0);
    private static final class Terrain implements FigSourceGeometry.World {
        final Set<BlockPos> logs=new HashSet<>();
        @Override public boolean passable(BlockPos p){return p.y()>=70&&!logs.contains(p);}
        @Override public boolean log(BlockPos p){return logs.contains(p);}
        @Override public boolean leaves(BlockPos p){return false;}
        @Override public Object blockType(BlockPos p){return logs.contains(p)?"oak":p.y()==69?"stone":"air";}
        @Override public boolean standing(BlockPos p){return p.y()==70&&passable(p)&&passable(p.up());}
        @Override public boolean dryStanding(BlockPos p){return standing(p);}
        @Override public Vec3 standingEye(Vec3 p){return new Vec3(p.x(),71.62,p.z());}
        @Override public boolean sourceRay(Vec3 from,BlockPos target,boolean leavesAllowed) {
            // Axis-aligned whole-block outline counterpart; LOG hit proximity uses original<3.
            Vec3 to=FigSourceGeometry.center(target);
            for(int i=0;i<=2000;i++) {
                double t=(double)i/2000;BlockPos hit=new Vec3(from.x()+(to.x()-from.x())*t,from.y()+(to.y()-from.y())*t,from.z()+(to.z()-from.z())*t).toBlockPos();
                if(logs.contains(hit))return new Vec3(hit.x(),hit.y(),hit.z()).distanceTo(new Vec3(target.x(),target.y(),target.z()))<3;
            }
            return false;
        }
    }
    @Test void sourceStemGroupsThenRemainingScaffoldYieldExactThreeOrFourAtEveryCentralOffset() {
        for(boolean extra:List.of(false,true)) {
            var world=new Terrain();var all=new ArrayList<BlockPos>();
            for(int y=70;y<=78;y++){var p=new BlockPos(2,y,0);world.logs.add(p);all.add(p);}
            if(extra){var p=new BlockPos(3,76,0);world.logs.add(p);all.add(p);}
            var geometry=new FigSourceGeometry(base,all,world,1,9);
            var stems=geometry.candidates(GalateaPlanner.AxeMode.STEM,Set.of());
            assertEquals(Set.of(base,base.up(),base.up(2),base.up(3),base.up(4)),new HashSet<>(stems));
            world.logs.removeAll(stems);
            var remaining=FigScaffoldCentralSeed.liveModeList(geometry.candidates(GalateaPlanner.AxeMode.SCAFFOLD,Set.of()),world::log);
            assertEquals(extra?5:4,remaining.size());
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                var feet=base.add(x,0,z);assertTrue(world.dryStanding(feet));
                for(double dx:new double[]{-.35,-.175,0,.175,.35})for(double dz:new double[]{-.35,-.175,0,.175,.35}) {
                    if(Math.hypot(dx,dz)>.350001)continue;
                    var eye=new Vec3(feet.x()+.5+dx,71.62,feet.z()+.5+dz);
                    int current=FigSourceCentralStandPolicy.reachableCount(eye,remaining);
                    int raised=FigSourceCentralStandPolicy.reachableCount(new Vec3(eye.x(),eye.y()+3,eye.z()),remaining);
                    assertEquals(1,current);assertEquals(extra?5:4,raised);assertEquals(extra?4:3,raised-current);
                }
            }
        }
    }
    @Test void survivingRemovedStemCoordinatesCannotCountAsNativeReachableLogs() {
        var all=new ArrayList<BlockPos>();for(int y=70;y<=78;y++)all.add(new BlockPos(2,y,0));
        var diagonalEye=new Vec3(1.5,71.62,-.5);
        assertEquals(2,FigSourceCentralStandPolicy.reachableCount(new Vec3(diagonalEye.x(),diagonalEye.y()+3,diagonalEye.z()),all)-FigSourceCentralStandPolicy.reachableCount(diagonalEye,all));
        var live=FigScaffoldCentralSeed.liveModeList(all,p->p.y()>=75);
        assertEquals(3,FigSourceCentralStandPolicy.reachableCount(new Vec3(diagonalEye.x(),diagonalEye.y()+3,diagonalEye.z()),live)-FigSourceCentralStandPolicy.reachableCount(diagonalEye,live));
    }
}
