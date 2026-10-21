package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class VeinPickupEnvelopeTest {
    private static VeinPickupEnvelope.Box player(double x,double y,double z){return new VeinPickupEnvelope.Box(x-.30000001192092896,y,z-.30000001192092896,x+.30000001192092896,y+1.7999999523162842,z+.30000001192092896);}
    private static VeinPickupEnvelope.Box item(double x,double y,double z){return new VeinPickupEnvelope.Box(x-.125,y,z-.125,x+.125,y+.25,z+.125);}
    @Test void recordedUncollectedS11bItemOutsidePlayerButReachableBesideRoof(){var drop=item(112.875,96,128.125);assertFalse(VeinPickupEnvelope.overlaps(player(111.34623784256914,96,128.62576952415083),drop));assertTrue(VeinPickupEnvelope.overlaps(player(111.6,96,128.4),drop));}
    @Test void EntireStrictTerminalSquareRetainsNativeOverlap(){var drop=item(112.875,96,128.125);for(double dx:new double[]{-.05,0,.05})for(double dz:new double[]{-.05,0,.05})assertTrue(VeinPickupEnvelope.overlaps(player(111.6+dx,96,128.4+dz),drop));}
    @Test void OriginalPointThreeFiveToleranceWouldAdmitAnUnreachablePose(){assertFalse(VeinPickupEnvelope.overlaps(player(111.25,96,128.4),item(112.875,96,128.125)));}
    @Test void NativeVerticalHalfBlockMatters(){assertTrue(VeinPickupEnvelope.overlaps(player(0,96,0),item(0,98.1,0)));assertFalse(VeinPickupEnvelope.overlaps(player(0,96,0),item(0,98.31,0)));}
    @Test void TouchingNativeBoundaryIsNotPickupOverlap(){var p=new VeinPickupEnvelope.Box(0,0,0,1,1,1);assertFalse(VeinPickupEnvelope.overlaps(p,new VeinPickupEnvelope.Box(2,0,0,2.25,.25,.25)));assertTrue(VeinPickupEnvelope.overlaps(p,new VeinPickupEnvelope.Box(Math.nextDown(2d),0,0,2.25,.25,.25)));}
    @Test void InvalidAndUnknownBoxesRefuse(){var good=player(0,0,0);assertFalse(VeinPickupEnvelope.overlaps(good,null));assertFalse(VeinPickupEnvelope.overlaps(good,new VeinPickupEnvelope.Box(Double.NaN,0,0,1,1,1)));assertFalse(VeinPickupEnvelope.overlaps(good,new VeinPickupEnvelope.Box(1,0,0,0,1,1)));}
}
