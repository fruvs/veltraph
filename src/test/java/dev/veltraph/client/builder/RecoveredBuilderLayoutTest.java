package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveredBuilderLayoutTest {
    @Test void explicitSourceEndcapGroupsKeepFiveBlockRhythmAndOrder() {
        var layout=BuildLayouts.recovered95Endcaps("minecraft:dirt");
        assertEquals(38,layout.entries().size());
        assertEquals(new BlockPos(4,0,0),layout.entries().getFirst().pos());
        assertEquals(new BlockPos(94,0,0),layout.entries().get(18).pos());
        assertEquals(new BlockPos(4,0,287),layout.entries().get(19).pos());
        assertEquals(new BlockPos(94,0,287),layout.entries().getLast().pos());
        var positions=new HashSet<BlockPos>();layout.entries().forEach(e->positions.add(e.pos()));
        assertEquals(38,positions.size());assertFalse(positions.contains(new BlockPos(3,0,0)));
        assertFalse(positions.contains(new BlockPos(5,0,0)));assertFalse(positions.contains(new BlockPos(4,0,286)));
        assertEquals(Map.of("minecraft:dirt",38L),layout.requiredMaterials());
    }
    @Test void sourcePaletteChangesMaterialWithoutChangingGeometry() {
        var dirt=BuildLayouts.recovered95Endcaps("minecraft:dirt");var stone=BuildLayouts.recovered95Endcaps("minecraft:stone");
        assertEquals(dirt.entries().stream().map(BuildLayout.Entry::pos).toList(),stone.entries().stream().map(BuildLayout.Entry::pos).toList());
        assertEquals(Map.of("minecraft:stone",38L),stone.requiredMaterials());
        assertFalse(stone.name().contains("MelonKing"));assertTrue(stone.name().contains("partial"));
    }
    @Test void reportedBedsPreserveAlternatingEndOffsetsAndTerminalException() {
        var regions=BuildLayouts.recovered95WorkRegions();assertEquals(22,regions.size());
        assertEquals(new BuildLayouts.Region(new BlockPos(93,0,286),new BlockPos(90,0,2)),regions.get(3));
        assertEquals(new BuildLayouts.Region(new BlockPos(88,0,285),new BlockPos(85,0,1)),regions.get(4));
        assertEquals(new BuildLayouts.Region(new BlockPos(3,0,286),new BlockPos(0,0,1)),regions.getLast());
        assertTrue(regions.get(3).contains(new BlockPos(91,0,286)));assertFalse(regions.get(3).contains(new BlockPos(91,0,1)));
        assertTrue(regions.get(4).contains(new BlockPos(86,0,1)));assertFalse(regions.get(4).contains(new BlockPos(86,0,286)));
    }
    @Test void exactRequestRotationUsesDimensionsRatherThanZeroOriginRotation() {
        var p=new BlockPos(4,0,0);var o=new BlockPos(-47,70,-143);
        assertEquals(new BlockPos(-43,70,-143),new SourceBuilderTransform(95,1,287,o,0).apply(p));
        assertEquals(new BlockPos(239,70,-139),new SourceBuilderTransform(95,1,287,o,1).apply(p));
        assertEquals(new BlockPos(43,70,143),new SourceBuilderTransform(95,1,287,o,2).apply(p));
        assertEquals(new BlockPos(-47,70,-53),new SourceBuilderTransform(95,1,287,o,3).apply(p));
        // Source reports inclusive far coordinate287 despite declared depth287. Keep its -1 result.
        assertEquals(new BlockPos(-48,70,-139),new SourceBuilderTransform(95,1,287,o,1).apply(new BlockPos(4,0,287)));
    }
    @Test void centeredRequestRetainsDeclaredExtentAndGardenClamp() {
        var t=SourceBuilderTransform.centered(95,1,287,new BlockPos(0,70,0),0);
        assertEquals(new BlockPos(-47,70,-143),t.origin());
        var source=t.apply(BuildLayouts.recovered95Endcaps("minecraft:stone"));
        assertEquals(new BlockPos(47,70,144),source.entries().getLast().pos());
        assertThrows(IllegalArgumentException.class,()->new SourceBuilderTransform(482,1,1,new BlockPos(0,70,0),0));
        assertThrows(IllegalArgumentException.class,()->SourceBuilderTransform.centered(95,1,287,new BlockPos(0,70,200),0).apply(BuildLayouts.recovered95Endcaps("minecraft:stone")));
    }
    @Test void materialBudgetExcludesDestroyAndNeverDeduplicatesRepeatedConsumes() {
        var pos=new BlockPos(0,0,0);
        var layout=new BuildLayout("consume twice",List.of(new BuildLayout.Entry(BuildLayout.Operation.PLACE,pos,"minecraft:dirt"),
                new BuildLayout.Entry(BuildLayout.Operation.DESTROY,pos,"minecraft:dirt"),new BuildLayout.Entry(BuildLayout.Operation.PLACE,pos,"minecraft:dirt")));
        assertEquals(Map.of("minecraft:dirt",2L),layout.requiredMaterials());
        assertThrows(UnsupportedOperationException.class,()->layout.requiredMaterials().put("minecraft:stone",1L));
    }
}
