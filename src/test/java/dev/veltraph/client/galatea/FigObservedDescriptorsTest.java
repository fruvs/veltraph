package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigObservedDescriptorsTest {
    private final BlockPos root=new BlockPos(0,70,0);
    @Test void explicitBaseAndOrderRemainDistinctFromMinimumLoadedRoot() {
        var observed=new FigObservedDescriptors.Descriptor(root,root.up(1),List.of(root.up(2),root,root.up()),"Authored native counterpart observation");
        var bindings=new FigObservedDescriptors(List.of(observed));
        var tree=new GalateaPlanner.Tree(root,List.of(root,root.up(),root.up(2)),0,0,false);
        assertEquals(observed,bindings.find(tree).orElseThrow());assertNotEquals(tree.root(),observed.base());
    }
    @Test void staleCaptureSetStopsOverridingAnAutomaticallyObservedPartialTree() {
        var bindings=new FigObservedDescriptors(List.of(new FigObservedDescriptors.Descriptor(root,root,List.of(root,root.up()),"Observed source geometry")));
        assertTrue(bindings.find(new GalateaPlanner.Tree(root,List.of(root),0,0,false)).isEmpty());
    }
    @Test void absentDescriptorRemainsUnboundAndDuplicateRootRefuses() {
        assertTrue(FigObservedDescriptors.parse("").find(new GalateaPlanner.Tree(root,List.of(root),0,0,false)).isEmpty());
        var d=new FigObservedDescriptors.Descriptor(root,root,List.of(root),"Observed source geometry");
        assertThrows(IllegalArgumentException.class,()->new FigObservedDescriptors(List.of(d,d)));
    }
}
