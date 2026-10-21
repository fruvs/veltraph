package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildSetupRuntimeTest {
    private static BuildSetupRuntime.Facts facts(String block,boolean source,boolean item,long now){return new BuildSetupRuntime.Facts(true,block,source,false,true,item,true,true,now);}
    @Test void completeBranchPreservesAllEighteenSourceSitesAndFourStageOrder(){
        var p=BuildSetupPlan.recovered95Irrigation("minecraft:dirt");assertEquals(198,p.steps().size());assertEquals(Map.of("minecraft:water_bucket",18L),p.requiredMaterials());
        assertEquals(new BlockPos(93,1,1),p.steps().getFirst().pos());assertEquals(new BlockPos(90,1,1),p.steps().get(3).pos());
        assertEquals(new BlockPos(92,0,1),p.steps().get(4).pos());assertEquals(new BlockPos(91,0,1),p.steps().get(5).pos());
        assertEquals(BuildSetupPlan.Operation.WATER,p.steps().get(6).operation());assertEquals(new BlockPos(92,0,1),p.steps().get(6).pos());
        assertEquals(new BlockPos(93,0,1),p.steps().get(7).pos());assertEquals(new BlockPos(83,1,1),p.steps().get(11).pos());
        assertEquals(new BlockPos(88,1,286),p.steps().get(99).pos());assertEquals(new BlockPos(5,0,286),p.steps().getLast().pos());
        for(int turns=0;turns<4;turns++){var t=new SourceBuilderTransform(95,1,287,new BlockPos(-47,70,-143),turns);var world=p.transformed(t);assertEquals(t.apply(p.steps().getFirst().pos()),world.steps().getFirst().pos());assertEquals(p.requiredMaterials(),world.requiredMaterials());}
    }
    @Test void actualAcknowledgementsAdvanceAndEmptyBucketIsNotPrematureFailure(){
        var step=new BuildSetupPlan.Step("water",BuildSetupPlan.Operation.WATER,new BlockPos(0,70,0),"minecraft:water");var p=new BuildSetupPlan("fixture",List.of(step));var r=new BuildSetupRuntime();
        assertEquals(BuildSetupRuntime.Action.WATER,r.tick(p,facts("minecraft:air",false,true,0)));assertEquals(0,r.index());
        assertEquals(BuildSetupRuntime.Action.NONE,r.tick(p,facts("minecraft:air",false,false,200)));assertEquals(0,r.index());
        assertEquals(BuildSetupRuntime.Action.COMPLETE,r.tick(p,facts("minecraft:water",true,false,300)));assertEquals(1,r.index());
    }
    @Test void boundedRetriesAndFlowingWaterCannotPretendToBeSourceAcknowledgement(){
        var p=new BuildSetupPlan("water",List.of(new BuildSetupPlan.Step("water",BuildSetupPlan.Operation.WATER,new BlockPos(0,70,0),"minecraft:water")));var r=new BuildSetupRuntime();
        for(long now:new long[]{0,1500,3000})assertEquals(BuildSetupRuntime.Action.WATER,r.tick(p,facts("minecraft:water",false,true,now)));
        assertEquals(BuildSetupRuntime.Action.STOP,r.tick(p,facts("minecraft:water",false,true,4500)));assertEquals(0,r.index());r.resume();assertEquals(BuildSetupRuntime.Action.COMPLETE,r.tick(p,facts("minecraft:water",true,false,5000)));
    }
    @Test void removalGuardsKeepCheckpointAndResumeDoesNotRepeatConfirmedRemoval(){
        var p=BuildSetupPlan.recovered95Irrigation("minecraft:dirt");var r=new BuildSetupRuntime();
        assertEquals(BuildSetupRuntime.Action.BREAK,r.tick(p,facts("minecraft:dirt",false,true,0)));assertEquals(BuildSetupRuntime.Action.NONE,r.tick(p,facts("minecraft:air",false,true,300)));assertEquals(1,r.index());
        assertEquals(BuildSetupRuntime.Action.STOP,r.tick(p,facts("minecraft:stone",false,true,400)));r.resume();assertEquals(1,r.index());
        assertEquals(BuildSetupRuntime.Action.STOP,r.tick(p,new BuildSetupRuntime.Facts(true,"minecraft:dirt",false,true,true,true,true,true,500)));
        assertEquals(BuildSetupRuntime.Action.STOP,r.tick(p,new BuildSetupRuntime.Facts(false,"minecraft:dirt",false,false,true,true,true,true,600)));
        assertEquals(BuildSetupRuntime.Action.BREAK,r.tick(p,facts("minecraft:dirt",false,true,700)));
    }
}
