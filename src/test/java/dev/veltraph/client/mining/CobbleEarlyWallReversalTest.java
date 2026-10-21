package dev.veltraph.client.mining;

import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent full swept-box terrain model; exercises the exact native adapter's early-wall predicate. */
class CobbleEarlyWallReversalTest {
    static final class Terrain implements CobbleEarlyWallReversal.Terrain {
        final Set<BlockPos> cubes=new HashSet<>(),cobble=new HashSet<>(),floors=new HashSet<>(),unsafe=new HashSet<>(),unloaded=new HashSet<>();
        Terrain(){for(int x=-5;x<=8;x++)for(int z=-5;z<=8;z++)floors.add(new BlockPos(x,69,z));}
        void column(BlockPos p){cubes.add(p);cubes.add(p.up());cobble.add(p);cobble.add(p.up());}
        public boolean supportedCobbleColumn(BlockPos p){return cobble.contains(p)&&cobble.contains(p.up())&&floors.contains(p.down())
                &&!unsafe.contains(p.down())&&!unloaded.contains(p)&&!unloaded.contains(p.up())&&!unloaded.contains(p.down());}
        public boolean walkClear(Vec3 a,Vec3 b){
            double minX=Math.min(a.x(),b.x())-.3,maxX=Math.max(a.x(),b.x())+.3;
            double minZ=Math.min(a.z(),b.z())-.3,maxZ=Math.max(a.z(),b.z())+.3,minY=a.y()+.001,maxY=a.y()+1.8;
            var blocked=new HashSet<BlockPos>(cubes);blocked.addAll(unsafe);blocked.addAll(unloaded);
            for(var p:blocked)if(minX<p.x()+1&&maxX>p.x()&&minZ<p.z()+1&&maxZ>p.z()&&minY<p.y()+1&&maxY>p.y())return false;
            for(int x=(int)Math.floor(minX);x<=Math.floor(maxX-1e-8);x++)for(int z=(int)Math.floor(minZ);z<=Math.floor(maxZ-1e-8);z++){
                var floor=new BlockPos(x,(int)Math.floor(a.y())-1,z);if(!floors.contains(floor)||unsafe.contains(floor)||unloaded.contains(floor))return false;
            }
            return true;
        }
    }
    static boolean early(Terrain t,Vec3 p,Direction d){return CobbleEarlyWallReversal.blockedByEndWall(p,d,.3,1.8,t);}
    @Test void southWallReversesBeforeAdjacentCellWhileOriginalFullSweepRemainsBlocked(){
        var t=new Terrain();t.column(new BlockPos(0,70,4));var p=new Vec3(.5,70,2.95);
        assertFalse(t.cobble.contains(p.toBlockPos().offset(Direction.SOUTH)));
        assertFalse(t.walkClear(p,new Vec3(.5,70,3.75)));assertTrue(early(t,p,Direction.SOUTH));
    }
    @Test void allCardinalDirectionsUseTheFullBodyContactPlane(){
        for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
            var t=new Terrain();boolean positive=d==Direction.SOUTH||d==Direction.EAST;double along=positive?2.95:2.05;
            var p=d.dx()!=0?new Vec3(along,70,.5):new Vec3(.5,70,along);
            var wall=d.dx()!=0?new BlockPos(positive?4:0,70,0):new BlockPos(0,70,positive?4:0);t.column(wall);
            assertTrue(early(t,p,d),d.toString());
        }
    }
    @Test void openSweepAndExactTouchDoNotCausePrematureReversal(){
        var t=new Terrain();t.column(new BlockPos(0,70,4));assertFalse(early(t,new Vec3(.5,70,2.4),Direction.SOUTH));
        assertFalse(early(t,new Vec3(.5,70,2.9),Direction.SOUTH));assertTrue(early(t,new Vec3(.5,70,2.9001),Direction.SOUTH));
    }
    @Test void unrelatedStoneOrSingleHeightObstacleIsNotAnEndWall(){
        var t=new Terrain();t.cubes.add(new BlockPos(0,70,4));t.cubes.add(new BlockPos(0,71,4));assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
        t.cobble.add(new BlockPos(0,70,4));assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
    }
    @Test void unsupportedWallOrMissingFloorBeforeItDeniesEarlyReversal(){
        var t=new Terrain();t.column(new BlockPos(0,70,4));t.floors.remove(new BlockPos(0,69,4));assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
        t.floors.add(new BlockPos(0,69,4));t.floors.remove(new BlockPos(0,69,3));assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
    }
    @Test void fluidOrHazardAtFloorBodyOrPrefixDeniesEarlyReversal(){
        for(var hazard:List.of(new BlockPos(0,69,3),new BlockPos(0,70,3),new BlockPos(0,71,3))){
            var t=new Terrain();t.column(new BlockPos(0,70,4));t.unsafe.add(hazard);assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
        }
    }
    @Test void unsafeReverseLandingNeverTurnsAGenericSafetyFailureIntoPermission(){
        var t=new Terrain();t.column(new BlockPos(0,70,4));t.floors.remove(new BlockPos(0,69,1));assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
        t.floors.add(new BlockPos(0,69,1));t.unsafe.add(new BlockPos(0,69,1));assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
    }
    @Test void unloadedWallHeadFloorAndPrefixAreDenied(){
        for(var cell:List.of(new BlockPos(0,70,4),new BlockPos(0,71,4),new BlockPos(0,69,4),new BlockPos(0,70,3),new BlockPos(0,69,3))){
            var t=new Terrain();t.column(new BlockPos(0,70,4));t.unloaded.add(cell);assertFalse(early(t,new Vec3(.5,70,2.95),Direction.SOUTH));
        }
    }
    @Test void lateralBodyOverlapWithAnotherColumnAndUnsupportedDimensionsAreDenied(){
        var t=new Terrain();t.column(new BlockPos(0,70,4));assertFalse(early(t,new Vec3(.9,70,2.95),Direction.SOUTH));
        assertFalse(CobbleEarlyWallReversal.blockedByEndWall(new Vec3(.5,70,2.95),Direction.SOUTH,.4,1.8,t));
        assertFalse(CobbleEarlyWallReversal.blockedByEndWall(new Vec3(.5,70,2.95),Direction.SOUTH,.3,2.4,t));
        assertFalse(early(t,new Vec3(.5,70.5,2.95),Direction.SOUTH));assertFalse(early(t,new Vec3(Double.NaN,70,2.95),Direction.SOUTH));
    }
    @Test void actualControllerReversesBeforeTheForwardSweepStallsAndKeepsOtherGuards(){
        var terrain=new Terrain();terrain.column(new BlockPos(0,70,4));terrain.column(new BlockPos(0,70,0));
        class Services extends MiningControllerTest.Services {
            @Override public boolean endWall(boolean forward){var d=forward?Direction.SOUTH:Direction.NORTH;return early(terrain,player.position,d);}
            @Override public boolean safeStep(boolean forward){var p=player.position;return terrain.walkClear(p,new Vec3(p.x(),p.y(),p.z()+(forward?.8:-.8)));}
        }
        var s=new Services();s.player.position=new Vec3(.5,70,2.95);s.player.blockPos=new BlockPos(0,70,2);s.player.facing=Direction.SOUTH;
        var cfg=new dev.veltraph.client.config.MapConfigService();cfg.set(dev.veltraph.client.config.MiningConfig.COBBLE_AUTO_WALK,true);
        var c=new MiningController(MiningMode.COBBLESTONE,s,cfg);c.start();c.tick();assertFalse(c.forward());assertTrue(s.input.back);assertFalse(s.input.forward);assertTrue(s.breaking.isBreaking());
        s.problem="Lava next to target";c.tick();assertEquals(MiningController.State.PAUSED,c.state());assertFalse(s.input.back);assertFalse(s.breaking.isBreaking());
    }
}
