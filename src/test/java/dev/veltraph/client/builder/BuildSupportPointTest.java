package dev.veltraph.client.builder;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildSupportPointTest {
    @Test void farmlandTopIsFifteenSixteenthsAndProbeLiesInsideItsActualOutline(){var top=BuildSupportPoint.top(-4,70,4,0,0,1,15.0/16,1);assertEquals(new Vec3(-3.5,70.9375,4.5),top);assertTrue(top.y()-.001<70.9375);assertTrue(71-.001>top.y());}
    @Test void fullCubeAndLowerSlabKeepTheirDistinctNativeTop(){assertEquals(71,BuildSupportPoint.top(0,70,0,0,0,1,1,1).y());assertEquals(70.5,BuildSupportPoint.top(0,70,0,0,0,1,.5,1).y());assertThrows(IllegalArgumentException.class,()->BuildSupportPoint.top(0,70,0,0,0,0,0,0));}
}
