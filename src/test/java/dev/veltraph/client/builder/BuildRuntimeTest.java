package dev.veltraph.client.builder;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BuildRuntimeTest {
    BuildLayout layout(BuildLayout.Operation operation) { return new BuildLayout("test",List.of(new BuildLayout.Entry(operation,new BlockPos(1,0,0),"minecraft:stone"))); }
    BuildRuntime.Facts facts(String block,long now) { return new BuildRuntime.Facts(true,block,true,true,true,true,now); }
    @Test void doesNotOverwriteUnexpectedBlocksOrDestroyWrongMaterial() {
        assertEquals(BuildRuntime.Action.STOP,new BuildRuntime().tick(layout(BuildLayout.Operation.PLACE),facts("minecraft:chest",1000)));
        assertEquals(BuildRuntime.Action.STOP,new BuildRuntime().tick(layout(BuildLayout.Operation.DESTROY),facts("minecraft:diamond_block",1000)));
    }
    @Test void waitsForObservedPlacementAndDoesNotSpamUse() {
        var runtime=new BuildRuntime();var layout=layout(BuildLayout.Operation.PLACE);
        assertEquals(BuildRuntime.Action.PLACE,runtime.tick(layout,facts("minecraft:air",1000)));
        assertEquals(BuildRuntime.Action.NONE,runtime.tick(layout,facts("minecraft:air",1050)));
        assertEquals(BuildRuntime.Action.NONE,runtime.tick(layout,facts("minecraft:stone",1100)));
        assertEquals(BuildRuntime.Action.COMPLETE,runtime.tick(layout,facts("minecraft:stone",1300)));
    }
    @Test void missingItemAndUnloadedChunkStopBeforeAnyAction() {
        var layout=layout(BuildLayout.Operation.PLACE);
        assertEquals(BuildRuntime.Action.STOP,new BuildRuntime().tick(layout,new BuildRuntime.Facts(true,"minecraft:air",true,false,false,true,1000)));
        assertEquals(BuildRuntime.Action.STOP,new BuildRuntime().tick(layout,new BuildRuntime.Facts(false,"minecraft:air",true,true,true,true,1000)));
    }
    @Test void rotatesAndTranslatesThenChecksGardenBounds() {
        var transformed=layout(BuildLayout.Operation.PLACE).transformed(new BlockPos(10,70,20),1);
        assertEquals(new BlockPos(10,70,21),transformed.entries().get(0).pos());
        assertThrows(IllegalArgumentException.class,()->layout(BuildLayout.Operation.PLACE).transformed(new BlockPos(239,70,0),0));
    }
    @Test void refusesFractionalLayoutCoordinatesAndUnknownOperations() {
        String json="""
                {"version":1,"name":"test","entries":[{"type":"place","pos":{"x":1,"y":0,"z":0},"block":"minecraft:stone"}]}
                """;
        assertEquals(1,BuildLayoutStore.decode(json).entries().size());
        assertThrows(IllegalArgumentException.class,()->BuildLayoutStore.decode(json.replace("\"x\":1","\"x\":1.5")));
        assertThrows(IllegalArgumentException.class,()->BuildLayoutStore.decode(json.replace("place","execute")));
    }
}
