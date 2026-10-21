package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLandingBodyEnvelopeTest {
    @Test void partialRodCornerMissedByChordAndEndpointsIsInsideNativeAxisOrderUnion(){
        var from=new Vec3(.06,71.024424088,.92);var to=new Vec3(.17,71,1.03);var rod=new GroundLandingBodyEnvelope.Bounds(.375,71,.375,.625,72,.625);
        assertFalse(GroundLandingBodyEnvelope.bounds(from,from,.6,1.8).orElseThrow().intersects(rod));
        assertFalse(GroundLandingBodyEnvelope.bounds(to,to,.6,1.8).orElseThrow().intersects(rod));
        for(int i=0;i<=100;i++){var p=TravelPath.lerp(from,to,i/100.0);assertFalse(GroundLandingBodyEnvelope.bounds(p,p,.6,1.8).orElseThrow().intersects(rod));}
        var xFirst=new Vec3(to.x(),from.y(),from.z());assertTrue(GroundLandingBodyEnvelope.bounds(xFirst,xFirst,.6,1.8).orElseThrow().intersects(rod));
        assertTrue(GroundLandingBodyEnvelope.bounds(from,to,.6,1.8).orElseThrow().intersects(rod));
    }
    @Test void smallVerticalDescentDoesNotRemoveTheCurrentElevatedHeadEnvelope(){var from=new Vec3(.5,71.024424088,4.571429363);var to=new Vec3(.5,71,4.728);var b=GroundLandingBodyEnvelope.bounds(from,to,.6,1.8).orElseThrow();assertEquals(71.001,b.minY(),1e-12);assertEquals(72.824424088,b.maxY(),1e-12);}
    @Test void unionUsesActualStandingDimensionsAndRejectsUnknownDimensions(){var from=new Vec3(.5,71,4);var to=new Vec3(.5,71,4.1);assertEquals(.199999988079071,bounds(from,to,.6000000238418579).minX(),1e-12);assertTrue(GroundLandingBodyEnvelope.bounds(from,to,Double.NaN,1.8).isEmpty());assertTrue(GroundLandingBodyEnvelope.bounds(from,to,.6,0).isEmpty());}
    private static GroundLandingBodyEnvelope.Bounds bounds(Vec3 from,Vec3 to,double width){return GroundLandingBodyEnvelope.bounds(from,to,width,1.8).orElseThrow();}
}
