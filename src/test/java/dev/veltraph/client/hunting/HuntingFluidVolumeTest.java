package dev.veltraph.client.hunting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class HuntingFluidVolumeTest {
    private static HuntingFluidVolume.Cells pool(){return (x,y,z)->new HuntingFluidVolume.Cell(true,true,x<15&&(y==70||y==71),y+(y==70?1:(double)(8f/9f)),x>=15?"stone":"source water/air");}
    @Test void literalP2PartialSnowballBodyTouchingRealSurfaceIsWater(){
        double x=.2883414985188511,y=71.81565664501206-HuntingFlight.EYE_OFFSET,z=2.979948479267716;
        var body=new HuntingFlight.Box(x-.124,y+.001,z-.124,x+.124,y+.249,z+.124);
        assertTrue(body.maxY()>71+(double)(8f/9f));var state=HuntingFluidVolume.inspect(body,pool());
        assertTrue(state.supported());assertTrue(state.water());
    }
    @Test void PartialNativeBodyAtStoneWallStillTouchesRealWater(){
        var state=HuntingFluidVolume.inspect(new HuntingFlight.Box(14.78,71.401,3.9,15.028,71.649,4.148),pool());
        assertTrue(state.supported());assertTrue(state.water());
    }
    @Test void CorrectNativeSurfaceMakesRecordedL3CodBodyGenuinelyDry(){
        double y=71.9178466796875;
        var state=HuntingFluidVolume.inspect(new HuntingFlight.Box(6.123,y+.001,-2.781,6.621,y+(double).3f-.001,-2.283),pool());
        assertTrue(state.supported());assertFalse(state.water());
    }
    @Test void UnknownFlowBubbleOrUnloadedGeometryRemainsDenied(){
        var body=new HuntingFlight.Box(0,70,0,.25,70.25,.25);
        assertFalse(HuntingFluidVolume.inspect(body,(x,y,z)->new HuntingFluidVolume.Cell(false,false,false,0,"unloaded")).supported());
        assertFalse(HuntingFluidVolume.inspect(body,(x,y,z)->new HuntingFluidVolume.Cell(true,false,true,71,"bubble or nonzero current")).supported());
    }
}
