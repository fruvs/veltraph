package dev.veltraph.client.builder;

import dev.veltraph.client.config.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedBuildPlanTest {
    private MapConfigService config(){var c=new MapConfigService();c.set(GeneratedBuilderConfig.TEMPORARY_BLOCK,"minecraft:dirt");return c;}
    @Test void source92RetainsEveryOriginalSubprocessAndTemporaryCleanupOrder() {
        var p=SourceGeneratedPlans.source92(config());assertEquals(92,p.processes().size());
        assertEquals(new BlockPos(31,3,1),p.processes().getFirst().first());
        assertEquals(new BlockPos(30,3,1),p.processes().get(1).first());assertEquals(new BlockPos(3,3,1),p.processes().get(1).last());
        assertEquals(28,p.processes().get(1).cells().size());
        assertEquals(GeneratedBuildPlan.Operation.BREAK,p.processes().get(3).operation());assertEquals(p.processes().getFirst().first(),p.processes().get(3).first());
        assertEquals(new BlockPos(29,7,7),p.processes().get(90).first());assertEquals(new BlockPos(4,7,7),p.processes().get(90).last());
        assertEquals(GeneratedBuildPlan.Operation.WATER,p.processes().getLast().operation());assertEquals(new BlockPos(3,7,6),p.processes().getLast().first());
        assertEquals(GeneratedBuildPlan.Operation.LEVER,p.processes().get(27).operation());assertEquals(new BlockPos(17,4,0),p.processes().get(27).first());
        assertEquals(new BlockPos(17,5,7),p.processes().get(42).first());
    }
    @Test void source92DoesNotInventSaltedPaletteOrOrientedStatePredicates() {
        var p=SourceGeneratedPlans.source92(new MapConfigService());assertTrue(p.processes().getFirst().unresolved().isPresent());
        assertTrue(p.processes().get(5).unresolved().orElseThrow().contains("entry 5"));
        assertTrue(p.processes().get(25).unresolved().orElseThrow().contains("entry 25"));assertEquals(new BlockPos(0,0,1),p.processes().get(25).direction());
        assertTrue(p.processes().get(32).unresolved().orElseThrow().contains("helper"));
        assertTrue(p.processes().get(91).unresolved().isEmpty());assertEquals(new BlockPos(1,0,0),p.processes().get(91).direction());
        assertTrue(SourceGeneratedPlans.source92(config()).processes().getFirst().unresolved().isEmpty());
    }
    @Test void boundPaletteReachesActualNativeBoundaryInsteadOfIdentityOnlyStops() {
        var c=config();c.set(GeneratedBuilderConfig.SOURCE92_PALETTE,"{\"source92-a\":\"minecraft:stone\",\"source92-b\":\"minecraft:glass\",\"source92-c\":\"minecraft:dirt\",\"source92-d\":\"minecraft:oak_slab\",\"source92-e\":\"minecraft:oak_planks\",\"source92-f\":\"minecraft:lever\",\"source92-g\":\"minecraft:stone\"}");
        var p=SourceGeneratedPlans.source92(c);for(int i=0;i<32;i++)assertTrue(p.processes().get(i).unresolved().isEmpty(),"Entry "+i);
        assertTrue(p.processes().get(32).unresolved().isPresent());
        assertEquals(new BlockPos(-1,0,0),p.processes().get(15).direction());assertTrue(p.processes().get(41).requireSupport());
        var t=new SourceBuilderTransform(31,7,7,new BlockPos(0,70,0),1);assertEquals(new BlockPos(-1,0,0),p.transformed(t).processes().get(25).direction());
    }
    @Test void configuredSource95RunsReadablePreScanGroupsWithoutFabricatedResourceReady() {
        var c=config();c.set(GeneratedBuilderConfig.CLEAR_BLOCK,"minecraft:dirt");c.set(GeneratedBuilderConfig.SOURCE95_TOOL_BLOCK,"minecraft:dirt");c.set(GeneratedBuilderConfig.SOURCE95_MARKER_BLOCK,"minecraft:dirt");c.set(GeneratedBuilderConfig.TILL_ITEM,"minecraft:diamond_hoe");c.set(GeneratedBuilderConfig.PLANT_ITEM,"minecraft:carrot");c.set(GeneratedBuilderConfig.PLANT_BLOCK,"minecraft:carrots");
        String binding="{\"toolItem\":\"minecraft:blaze_rod\",\"resourceItem\":\"minecraft:dirt\",\"menuTitle\":\"Bound Tool\",\"gaugeLabel\":\"Resources\",\"countPrefix\":\"Stored: \"}";
        c.set(GeneratedBuilderConfig.SOURCE95_RESOURCE_BINDING,binding);c.set(GeneratedBuilderConfig.SOURCE95_PLANT_RESOURCE_BINDING,binding);c.set(BuilderConfig.BUILDER_BUY_RESOURCES,false);
        var p=SourceGeneratedPlans.source95(c);var first=p.processes().stream().filter(s->s.unresolved().isPresent()).findFirst().orElseThrow();assertEquals("source95 group6 chunk scan",first.source());assertTrue(first.contract().contains("native"));
        assertEquals(GeneratedBuildPlan.Operation.RESOURCE_SETUP,p.processes().getFirst().operation());assertTrue(p.processes().stream().noneMatch(s->s.source().equals("source95 group7 conditional sell")));
    }
    @Test void source95GraphUsesActualZ192PreparatoryTargetsThenOrderedEndcapsAndIrrigation() {
        var p=SourceGeneratedPlans.source95(config());
        var clear=p.processes().stream().filter(s->s.source().startsWith("source95 group1 tool placement")).toList();assertEquals(77,clear.size());assertTrue(clear.stream().allMatch(s->s.operation()==GeneratedBuildPlan.Operation.PLACE));
        assertEquals(new BlockPos(0,0,192),clear.getFirst().first());assertEquals(new BlockPos(95,0,192),clear.getLast().first());
        assertFalse(clear.stream().anyMatch(s->s.first().x()==4));
        assertEquals(19,p.processes().stream().filter(s->s.source().startsWith("source95 group2 irrigation")).count());
        assertEquals(76,p.processes().stream().filter(s->s.operation()==GeneratedBuildPlan.Operation.TILL).count());
        assertEquals(76,p.processes().stream().filter(s->s.operation()==GeneratedBuildPlan.Operation.PLANT).count());
        assertEquals(38,p.processes().stream().filter(s->s.source().startsWith("source95 endcap")).count());
        assertEquals(198,p.processes().stream().filter(s->s.source().startsWith("source95 irrigation")).count());
        int near=0,far=0;for(int i=0;i<p.processes().size();i++){var s=p.processes().get(i);if(s.source().equals("source95 endcap 94,0"))near=i;if(s.source().equals("source95 endcap 4,287"))far=i;}
        assertEquals(100,far-near);assertEquals(new BlockPos(93,1,1),p.processes().get(near+1).first());
        assertEquals(new BlockPos(5,0,286),p.processes().getLast().first());
    }
    @Test void preparationOffPreservesItsActualDifferentPairAndToolContract() {
        var c=config();c.set(GeneratedBuilderConfig.PREPARATION,false);var p=SourceGeneratedPlans.source95(c);
        assertFalse(p.processes().stream().anyMatch(s->s.operation()==GeneratedBuildPlan.Operation.TILL));
        var clearance=p.processes().stream().filter(s->s.source().equals("source95 preparation-off clear")).toList();assertEquals(18,clearance.size());
        assertEquals(new BlockPos(93,1,1),clearance.getFirst().first());assertEquals(new BlockPos(95,2,1),clearance.getFirst().last());assertEquals(6,clearance.getFirst().cells().size());
        assertEquals(18,p.processes().stream().filter(s->s.source().equals("source95 preparation-off tool")).count());
    }
    @Test void allFourSourceTransformsRemainDimensionAwareAndAnchored() {
        var p=SourceGeneratedPlans.source92(config());for(int turns=0;turns<4;turns++) {
            var t=SourceBuilderTransform.centered(31,7,7,new BlockPos(0,70,0),turns);var w=p.transformed(t);
            assertEquals(t.apply(p.processes().get(90).first()),w.processes().get(90).first());assertEquals(t.apply(p.processes().get(90).last()),w.processes().get(90).last());
            assertEquals(p.requiredMaterials(),w.requiredMaterials());assertEquals(p.unresolvedContracts(),w.unresolvedContracts());
        }
        assertThrows(IllegalArgumentException.class,()->p.transformed(SourceBuilderTransform.centered(31,7,7,new BlockPos(239,70,0),0)));
    }
    @Test void checkpointRequiresConfirmationAndRetainsCellAcrossBlockingAndResume() {
        var p=SourceGeneratedPlans.source92(config());var r=new GeneratedBuildRuntime();
        r.state(GeneratedBuildRuntime.State.RESTOCK,"issued transfer");assertEquals(0,r.process());r.resume();assertEquals(0,r.cell());
        r.confirmed(p);assertEquals(1,r.process());r.confirmed(p);assertEquals(1,r.cell());
        r.state(GeneratedBuildRuntime.State.BLOCKED,"missing");r.resume();assertEquals(1,r.process());assertEquals(1,r.cell());
        for(int i=1;i<28;i++)r.confirmed(p);assertEquals(2,r.process());assertEquals(0,r.cell());
    }
    @Test void capAndBindingValidationRejectUnsafePlansBeforeNativeWork() {
        assertThrows(IllegalArgumentException.class,()->new GeneratedBuildPlan.Process("huge",GeneratedBuildPlan.Operation.PLACE,new BlockPos(0,0,0),new BlockPos(480,0,480),"minecraft:air","minecraft:dirt","minecraft:dirt",""));
        assertThrows(IllegalArgumentException.class,()->new GeneratedBuildPlan.Process("bad",GeneratedBuildPlan.Operation.PLACE,new BlockPos(0,0,0),new BlockPos(0,0,0),"minecraft:air","not a block","",""));
        assertEquals("",GeneratedBuilderConfig.TEMPORARY_BLOCK.defaultValue());assertEquals("",GeneratedBuilderConfig.TILL_ITEM.defaultValue());assertEquals("",GeneratedBuilderConfig.PLANT_ITEM.defaultValue());
    }
}
