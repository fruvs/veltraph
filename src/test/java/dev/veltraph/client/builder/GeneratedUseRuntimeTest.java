package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedUseRuntimeTest {
    private GeneratedBuildPlan.Process till(){var p=new BlockPos(0,70,0);return new GeneratedBuildPlan.Process("source till",GeneratedBuildPlan.Operation.TILL,p,p,"minecraft:dirt","minecraft:farmland","minecraft:diamond_hoe","");}
    private GeneratedUseRuntime.Facts f(String block,boolean support,boolean item,long now){return new GeneratedUseRuntime.Facts(true,false,block,true,support,item,true,true,true,now);}
    @Test void useAndConsumedItemCannotAdvanceBeforeWorldAcknowledgement(){var r=new GeneratedUseRuntime();assertEquals(GeneratedUseRuntime.Action.USE,r.tick(till(),f("minecraft:dirt",true,true,0)));assertEquals(GeneratedUseRuntime.Action.NONE,r.tick(till(),f("minecraft:dirt",true,false,100)));assertEquals(GeneratedUseRuntime.Action.COMPLETE,r.tick(till(),f("minecraft:farmland",true,false,300)));}
    @Test void noBlindUseOnChangedTargetOrBlockedSupport(){assertEquals(GeneratedUseRuntime.Action.STOP,new GeneratedUseRuntime().tick(till(),f("minecraft:stone",true,true,0)));assertEquals(GeneratedUseRuntime.Action.STOP,new GeneratedUseRuntime().tick(till(),f("minecraft:dirt",false,true,0)));assertEquals(GeneratedUseRuntime.Action.STOP,new GeneratedUseRuntime().tick(till(),new GeneratedUseRuntime.Facts(false,false,"minecraft:dirt",true,true,true,true,true,true,0)));assertEquals(GeneratedUseRuntime.Action.STOP,new GeneratedUseRuntime().tick(till(),new GeneratedUseRuntime.Facts(true,true,"minecraft:dirt",true,true,true,true,true,true,0)));}
    @Test void predictedFarmlandCannotAcknowledgeUse(){var r=new GeneratedUseRuntime();assertEquals(GeneratedUseRuntime.Action.USE,r.tick(till(),f("minecraft:dirt",true,true,0)));assertEquals(GeneratedUseRuntime.Action.NONE,r.tick(till(),new GeneratedUseRuntime.Facts(true,false,"minecraft:farmland",false,true,true,true,true,true,300)));assertEquals(GeneratedUseRuntime.Action.COMPLETE,r.tick(till(),f("minecraft:farmland",true,true,400)));}
    @Test void failedUsesAreBoundedAndMissingMaterialCannotIssue(){var r=new GeneratedUseRuntime();for(long now:new long[]{0,1500,3000})assertEquals(GeneratedUseRuntime.Action.USE,r.tick(till(),f("minecraft:dirt",true,true,now)));assertEquals(GeneratedUseRuntime.Action.STOP,r.tick(till(),f("minecraft:dirt",true,true,4500)));assertEquals(GeneratedUseRuntime.Action.STOP,new GeneratedUseRuntime().tick(till(),f("minecraft:dirt",true,false,0)));}
}
