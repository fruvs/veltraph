package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.util.Mth;

final class GhostSourceFloatDirectionTest {
    @Test void eastCardinalUsesFloatYawAndMthLookupRatherThanDoubleNormalization(){
        var actual=GhostSourceFloatDirection.between(new GhostSourceSampledRay.Point(0,0,0),new GhostSourceSampledRay.Point(4,0,0),17);
        float yaw=-90,pitch=0;
        float y=Mth.sin(-pitch*((float)Math.PI/180));
        float x=Mth.sin(-yaw*((float)Math.PI/180)-(float)Math.PI)*-Mth.cos(-pitch*((float)Math.PI/180));
        float z=Mth.cos(-yaw*((float)Math.PI/180)-(float)Math.PI)*-Mth.cos(-pitch*((float)Math.PI/180));
        assertEquals((double)x,actual.x());assertEquals((double)y,actual.y());assertEquals((double)z,actual.z());
    }
    @Test void verticalPitchUsesActualNativeYawAtRecoveredLimit(){
        var first=GhostSourceFloatDirection.between(new GhostSourceSampledRay.Point(0,0,0),new GhostSourceSampledRay.Point(0,4,0),0);
        var second=GhostSourceFloatDirection.between(new GhostSourceSampledRay.Point(0,0,0),new GhostSourceSampledRay.Point(0,4,0),90);
        assertTrue(first.y()>.999);assertEquals(first.y(),second.y());
        // Finite table residual X/Z remains yaw-dependent; it is not replaced with mathematical zero.
        assertNotEquals(first,second);
    }
    @Test void invalidBoxesAreRejectedBeforeSnapshotCanReadWorld(){
        assertThrows(IllegalArgumentException.class,()->new GhostSourceSampledRay.Box(2,0,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new GhostSourceSampledRay.Box(Double.NaN,0,0,1,1,1));
    }
}
