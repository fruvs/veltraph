package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundDropCoastEnvelopeTest {
    @Test void nativeAxisOrderCoastCanHitPartialShapeMissedByStandingEndpointsAndSampledChord(){
        var landing=new Vec3(.06,70,.92);var coast=new Vec3(.17,70,1.03);
        var rod=new GroundLandingBodyEnvelope.Bounds(.375,70,.375,.625,71,.625);
        assertFalse(GroundLandingBodyEnvelope.bounds(landing,landing,.6,1.8).orElseThrow().intersects(rod));
        assertFalse(GroundLandingBodyEnvelope.bounds(coast,coast,.6,1.8).orElseThrow().intersects(rod));
        for(int i=0;i<=100;i++){var p=TravelPath.lerp(landing,coast,i/100.0);assertFalse(GroundLandingBodyEnvelope.bounds(p,p,.6,1.8).orElseThrow().intersects(rod));}
        var xFirst=new Vec3(coast.x(),landing.y(),landing.z());
        assertTrue(GroundLandingBodyEnvelope.bounds(xFirst,xFirst,.6,1.8).orElseThrow().intersects(rod));
        assertTrue(GroundLandingBodyEnvelope.bounds(landing,coast,.6,1.8).orElseThrow().intersects(rod));
    }
}
