package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.galatea.FigJumpAimPolicy.*;
class FigJumpAimPolicyTest {
 @Test void subtractionClampsNegativeAndHugeCountersWithoutOrdinalOverflow() {
  assertEquals(Mode.LAZY,effective(Mode.LAZY,-5));assertEquals(Mode.LAZY,effective(Mode.LAZY,Integer.MIN_VALUE));
  assertEquals(Mode.AVERAGE,effective(Mode.LAZY,1));assertEquals(Mode.STRONG,effective(Mode.LAZY,2));
  assertEquals(Mode.STRONG,effective(Mode.AVERAGE,Integer.MAX_VALUE));
 }
 @Test void strongRetainsSuppliedEndpointAndBlendOperandOrderIsDistinct() {
  var base=new Angle(30,-10);var center=new Angle(90,50);
  assertEquals(base,endpoint(Mode.STRONG,0,base,center,.25));
  assertEquals(5,endpoint(Mode.AVERAGE,0,base,center,.25).pitch());
  assertEquals(35,endpoint(Mode.LAZY,0,base,center,.25).pitch());
  assertNotEquals(endpoint(Mode.AVERAGE,0,base,center,.25).yaw(),endpoint(Mode.LAZY,0,base,center,.25).yaw());
  assertEquals(base,endpoint(Mode.AVERAGE,1,base,center,.25));
 }
 @Test void yawCrossesCircularBoundaryWithoutAveragingAcrossZero() {
  var angle=blend(new Angle(179,-10),new Angle(-179,30),.25);
  assertTrue(Math.abs(angle.yaw())>179);assertEquals(0,angle.pitch());
  assertEquals(Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(179))+.75*Math.sin(Math.toRadians(-179)),Math.cos(Math.toRadians(179))+.75*Math.cos(Math.toRadians(-179)))),angle.yaw(),1e-12);
 }
 @Test void sourceWeightBoundsAndSingleProcessSample() {
  double weight=processWeight();assertTrue(weight>=.2&&weight<.4);assertEquals(weight,processWeight());
  for(double invalid:new double[]{.199,.4,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->blend(new Angle(0,0),new Angle(1,1),invalid));
 }
 @Test void counterUsesOnlyExplicitSourceSignals() {
  assertEquals(-1,observeCounter(7,true,false,false,false));
  assertEquals(1,observeCounter(7,false,true,true,true));
  assertEquals(0,observeCounter(7,true,false,true,false));
  assertEquals(8,observeCounter(7,false,false,true,true));
  assertEquals(7,observeCounter(7,false,false,false,true));
 }
 @Test void invalidAnglesCannotBecomeCameraTargets() {
  assertThrows(IllegalArgumentException.class,()->new Angle(Double.NaN,0));assertThrows(IllegalArgumentException.class,()->new Angle(0,91));
 }
}
