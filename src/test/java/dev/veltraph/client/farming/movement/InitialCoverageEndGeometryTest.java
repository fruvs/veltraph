package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class InitialCoverageEndGeometryTest {
    static final class World implements WorldService {
        final Map<BlockPos,String> blocks=new HashMap<>();
        final Set<BlockPos> unloaded=new HashSet<>();
        public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
        public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
        public boolean isPassable(BlockPos p){return !Set.of("minecraft:stone","minecraft:dirt","minecraft:farmland").contains(blockId(p));}
        public boolean isStandable(BlockPos p){return !isPassable(p);}
    }
    record Rig(FarmGeometry geometry,World world){}
    Rig rig(){
        var world=new World();
        var player=new FakePlayerService();
        player.position=new Vec3(.5,70,.5);
        player.blockPos=new BlockPos(0,70,0);
        world.blocks.put(new BlockPos(0,69,0),"minecraft:dirt");
        world.blocks.put(new BlockPos(0,69,-1),"minecraft:dirt");
        world.blocks.put(new BlockPos(0,70,1),"minecraft:stone");
        for(int x=1;x<=4;x++)world.blocks.put(new BlockPos(x,70,0),"minecraft:farmland");
        var geometry=new FarmGeometry(new MapConfigService(),player,world);
        geometry.setHeading(Direction.EAST);
        geometry.setSelectedCrop("minecraft:wheat");
        geometry.setCollisionProbe(new FarmCollisionProbe(){
            public boolean loaded(Vec3 p){return world.isLoaded(p.toBlockPos());}
            public boolean clear(Vec3 p,boolean water){return world.isPassable(p.toBlockPos());}
            public OptionalDouble supportY(double x,double z,double highest,int down){return world.isStandable(new BlockPos((int)Math.floor(x),69,(int)Math.floor(z)))?OptionalDouble.of(70):OptionalDouble.empty();}
            public boolean water(Vec3 p){return false;}
        });
        return new Rig(geometry,world);
    }
    @Test void knownFarCropBoundaryReversesWithoutForwardConnector(){var r=rig();assertTrue(r.geometry.initialCoverageEndProved(TurnDirection.RIGHT,false));assertFalse(r.geometry.initialCoverageEndProved(TurnDirection.RIGHT,true));}
    @Test void arbitraryObstacleInContinuingCropBedIsNotAnEndpoint(){var r=rig();for(int x=1;x<=4;x++)r.world.blocks.put(new BlockPos(x,70,1),"minecraft:farmland");assertFalse(r.geometry.initialCoverageEndProved(TurnDirection.RIGHT,false));}
    @Test void unknownBoundaryOrMissingReturnSupportFailsClosed(){var r=rig();r.world.unloaded.add(new BlockPos(1,70,1));assertFalse(r.geometry.initialCoverageEndProved(TurnDirection.RIGHT,false));r.world.unloaded.clear();r.world.blocks.remove(new BlockPos(0,69,-1));assertFalse(r.geometry.initialCoverageEndProved(TurnDirection.RIGHT,false));}
    @Test void phaseSpecificConnectorRequirementIsPinnedToOriginalNearExit(){var coverage=new InitialLaneCoverage();coverage.start(true,Direction.EAST,TurnDirection.LEFT);assertTrue(coverage.requiresForwardExitProof());coverage.end(TurnDirection.LEFT,new Vec3(.5,70,-20.5),true,true);assertFalse(coverage.requiresForwardExitProof());coverage.end(TurnDirection.RIGHT,new Vec3(.5,70,30.5),true,true);assertTrue(coverage.requiresForwardExitProof());}
    @Test void mixedContinuingBedCannotBeMisclassifiedByOneEndingColumn(){var r=rig();for(int x=1;x<=3;x++)r.world.blocks.put(new BlockPos(x,70,1),"minecraft:farmland");assertFalse(r.geometry.initialCoverageEndProved(TurnDirection.RIGHT,false));}
    @Test void farEndpointMustStayAtOriginalInitialLaneHeight(){var c=new InitialLaneCoverage();c.start(true,Direction.EAST,TurnDirection.LEFT);c.end(TurnDirection.LEFT,new Vec3(.5,70,-20.5),true,true);assertEquals(InitialLaneCoverage.Decision.DENY,c.end(TurnDirection.RIGHT,new Vec3(.5,71,30.5),true,true));assertEquals(InitialLaneCoverage.Decision.DENY,c.end(TurnDirection.RIGHT,new Vec3(.5,Double.NaN,30.5),true,true));}

}
