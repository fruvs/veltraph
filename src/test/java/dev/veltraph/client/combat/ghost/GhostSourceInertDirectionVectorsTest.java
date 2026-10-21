package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceInertDirectionVectorsTest {
    @Test void independentNonCardinalNegativePositionVectorMatchesFloatBits(){
        var actual=GhostSourceFloatDirection.between(new GhostSourceSampledRay.Point(-2.25,76.625,3.75),new GhostSourceSampledRay.Point(6.125,73.5,-4.875),-33);
        assertEquals(1059886830,Float.floatToRawIntBits((float)actual.x()));
        assertEquals((int)3196110875L,Float.floatToRawIntBits((float)actual.y()));
        assertEquals((int)3207708424L,Float.floatToRawIntBits((float)actual.z()));
    }
    @Test void independentVerticalYawVectorsRetainLookupResidualBits(){
        var first=GhostSourceFloatDirection.between(new GhostSourceSampledRay.Point(0,0,0),new GhostSourceSampledRay.Point(0,4,0),0);
        assertEquals((int)2325462645L,Float.floatToRawIntBits((float)first.x()));
        assertEquals(1065353216,Float.floatToRawIntBits((float)first.y()));
        assertEquals(621621554,Float.floatToRawIntBits((float)first.z()));
        var second=GhostSourceFloatDirection.between(new GhostSourceSampledRay.Point(0,0,0),new GhostSourceSampledRay.Point(0,4,0),90);
        assertEquals((int)2769105202L,Float.floatToRawIntBits((float)second.x()));
        assertEquals((int)2325462645L,Float.floatToRawIntBits((float)second.z()));
    }
}
