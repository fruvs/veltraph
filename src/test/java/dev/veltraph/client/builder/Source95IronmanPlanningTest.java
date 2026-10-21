package dev.veltraph.client.builder;

import dev.veltraph.client.config.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** This class compiles against unchanged M: its independent canonical key exposes M's ignored policy. */
class Source95IronmanPlanningTest {
    private static final ConfigKey<Boolean> IRONMAN=ConfigKey.ofBool("IRONMAN_MODE",false);
    private static MapConfigService config(boolean prepare,boolean buy,Boolean ironman){var c=new MapConfigService();c.set(GeneratedBuilderConfig.PREPARATION,prepare);c.set(BuilderConfig.BUILDER_BUY_RESOURCES,buy);if(ironman!=null)c.set(IRONMAN,ironman);return c;}
    private static boolean scopedMarketHelper(GeneratedBuildPlan.Process p){return p.source().startsWith("source95 group4 resource ")||p.source().equals("source95 group5 configured resource128")||p.source().equals("source95 group7 conditional sell");}
    @Test void ironmanOmitsOnlyTheThreeReadableConditionalHelperFamilies(){
        for(boolean prepare:List.of(false,true))for(boolean buy:List.of(false,true)){
            var ordinary=SourceGeneratedPlans.source95(config(prepare,buy,false));var ironman=SourceGeneratedPlans.source95(config(prepare,buy,true));
            assertEquals(ordinary.processes().stream().filter(p->!scopedMarketHelper(p)).toList(),ironman.processes(),"Ironman must omit only source-proven group4/5/7 helpers, preserving every remaining subprocess and endpoint");
            assertEquals(buy?5:3,ordinary.processes().size()-ironman.processes().size());
            assertEquals(76,ironman.processes().stream().filter(p->p.operation()==GeneratedBuildPlan.Operation.PLANT).count());
            assertEquals(ordinary.requiredMaterials(),ironman.requiredMaterials());
        }
    }
    @Test void canonicalDefaultAndExplicitFalseKeepNonIronmanGraphExactly(){
        for(boolean prepare:List.of(false,true))for(boolean buy:List.of(false,true)){
            assertEquals(SourceGeneratedPlans.source95(config(prepare,buy,false)),SourceGeneratedPlans.source95(config(prepare,buy,null)));
            var ordinary=SourceGeneratedPlans.source95(config(prepare,buy,false));assertEquals(3,ordinary.processes().stream().filter(p->p.source().startsWith("source95 group4 resource ")).count());
            assertEquals(buy?1:0,ordinary.processes().stream().filter(p->p.source().equals("source95 group5 configured resource128")).count());
            assertEquals(buy?1:0,ordinary.processes().stream().filter(p->p.source().equals("source95 group7 conditional sell")).count());
        }
    }
    @Test void ironmanEmitsNoGroup4PurchaseOrGroup5ProcurementOrGroup7SellStage(){
        var graph=SourceGeneratedPlans.source95(config(false,true,true));assertFalse(graph.processes().stream().anyMatch(Source95IronmanPlanningTest::scopedMarketHelper));
        // Group1 tool loading is a separate readable helper. Do not invent broader Ironman omissions.
        assertEquals(3,graph.processes().stream().filter(p->p.source().startsWith("source95 group1 resource ")).count());
        assertTrue(graph.processes().stream().anyMatch(p->p.source().equals("source95 group6 chunk scan")&&p.unresolved().isPresent()));
        assertEquals(18,graph.processes().stream().filter(p->p.source().equals("source95 preparation-off tool")).count());
    }
    @Test void source92AndAllSourceTransformsRetainTheirPhysicalProcesses(){
        var a=config(false,true,false);var b=config(false,true,true);assertEquals(SourceGeneratedPlans.source92(a),SourceGeneratedPlans.source92(b));
        var ordinary=SourceGeneratedPlans.source95(a);var ironman=SourceGeneratedPlans.source95(b);
        for(int turn=0;turn<4;turn++){
            var transform=new SourceBuilderTransform(95,1,287,new dev.veltraph.client.world.BlockPos(-143,70,-143),turn);
            assertEquals(ordinary.transformed(transform).processes().stream().filter(p->!scopedMarketHelper(p)).toList(),ironman.transformed(transform).processes());
        }
    }
}
