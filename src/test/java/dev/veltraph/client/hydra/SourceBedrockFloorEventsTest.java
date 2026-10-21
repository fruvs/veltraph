package dev.veltraph.client.hydra;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SourceBedrockFloorEventsTest {
    SourceBedrockFloorEvents.Event event(boolean newBedrock,boolean oldBedrock,double x,int blockX){return new SourceBedrockFloorEvents.Event(true,newBedrock,oldBedrock,x,70,0,blockX,70,0);}
    @Test void onlyVerifiedNonBedrockToBedrockQualifies(){var d=new SourceBedrockFloorEvents();assertFalse(d.accept(event(false,false,0,0)));assertFalse(d.accept(event(true,true,0,0)));assertTrue(d.accept(event(true,false,0,0)));assertEquals(0,d.count());d.nextTick(100);assertEquals(1,d.count());}
    @Test void strictThreeDimensionalCornerDistanceNotCenteredOrHorizontal(){var d=new SourceBedrockFloorEvents();assertFalse(d.accept(event(true,false,0,18)));assertTrue(d.accept(event(true,false,.001,18)));assertFalse(d.accept(new SourceBedrockFloorEvents.Event(true,true,false,0,0,0,0,18,0)));}
    @Test void thresholdIsStrictlyGreaterThanTen(){var d=new SourceBedrockFloorEvents();for(int i=0;i<10;i++)assertTrue(d.accept(event(true,false,0,i)));d.nextTick(100);assertFalse(d.detected(100));assertTrue(d.accept(event(true,false,0,0)));d.nextTick(101);assertTrue(d.detected(101));}
    @Test void age450RemainsAnd451Prunes(){var d=new SourceBedrockFloorEvents();for(int i=0;i<11;i++)d.accept(event(true,false,0,0));d.nextTick(100);assertTrue(d.detected(550));assertFalse(d.detected(551));}
    @Test void appendUsesDelayedExecutionClock(){var d=new SourceBedrockFloorEvents();for(int i=0;i<11;i++)d.accept(event(true,false,0,0));assertFalse(d.detected(1000));d.nextTick(1001);assertTrue(d.detected(1451));assertFalse(d.detected(1452));}
    @Test void disabledOrNonfinitePositionRejects(){var d=new SourceBedrockFloorEvents();assertFalse(d.accept(new SourceBedrockFloorEvents.Event(false,true,false,0,0,0,0,0,0)));assertFalse(d.accept(event(true,false,Double.NaN,0)));}
    @Test void cropRemovalAndCollisionFlipAreNotSourceFloorEvents(){var d=new SourceBedrockFloorEvents();for(int i=0;i<36;i++)assertFalse(d.accept(event(false,false,0,0)));d.nextTick(100);assertFalse(d.detected(100));}
}
