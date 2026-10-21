package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigAimPointResolverTest {
 private final BlockPos block=new BlockPos(0,0,0);
 private final FigAimPointResolver.Bounds box=new FigAimPointResolver.Bounds(0,0,0,1,1,1);
 @Test void blockedRandomPointUsesMeanOfSourceGridVisiblePoints() {
  var sampled=new Vec3(.95,.5,.5);
  var actual=FigAimPointResolver.resolve(block,box,sampled,p->p.x()<.7);
  assertEquals((.06125+1.0/3+2.0/3)/3,actual.x(),1e-12);
  assertEquals(.5,actual.y(),1e-12);assertEquals(.5,actual.z(),1e-12);
 }
 @Test void invisibleMeanFallsBackToMiddleVisibleSampleInSourceOrder() {
  var actual=FigAimPointResolver.resolve(block,box,new Vec3(.5,.5,.5),p->p.x()<.1||p.x()>.9);
  assertEquals(.93875,actual.x(),1e-12);assertEquals(.06125,actual.y(),1e-12);assertEquals(.06125,actual.z(),1e-12);
 }
 @Test void allFivePerturbedSamplesMustHitBeforeRetainingRandomPoint() {
  var point=new Vec3(.5,.5,.5);var actual=FigAimPointResolver.resolve(block,box,point,p->p.z()<.6);
  assertNotEquals(point,actual);
 }
 @Test void allFiveClearSamplesPreserveOriginalPoint() {
  var point=new Vec3(.2,.3,.4);assertSame(point,FigAimPointResolver.resolve(block,box,point,p->true));
 }
 @Test void noVisibleGridPointRetainsOriginalPoint() {var point=new Vec3(.2,.3,.4);assertSame(point,FigAimPointResolver.resolve(block,box,point,p->false));}
}
