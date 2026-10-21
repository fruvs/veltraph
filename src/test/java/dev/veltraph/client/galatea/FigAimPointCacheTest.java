package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class FigAimPointCacheTest {
 @Test void stationaryAgeBoundaryAndTouchExtendSamePoint() {
  var cache=new FigAimPointCache();var samples=new AtomicInteger();var block=new BlockPos(1,2,3);var player=new Vec3(0,0,0);
  java.util.function.Supplier<Vec3> sample=()->new Vec3(samples.incrementAndGet(),0,0);
  assertEquals(1,cache.point(block,player,0,sample).x());assertEquals(1,cache.point(block,player,1820,sample).x());
  cache.touch(block,1820);assertEquals(1,cache.point(block,player,3640,sample).x());assertEquals(2,cache.point(block,player,3641,sample).x());
 }
 @Test void movementNeedsBothStrictAgeAndStrictEuclideanDistance() {
  var cache=new FigAimPointCache();var samples=new AtomicInteger();var block=new BlockPos(1,2,3);var player=new Vec3(0,0,0);
  java.util.function.Supplier<Vec3> sample=()->new Vec3(samples.incrementAndGet(),0,0);
  cache.point(block,player,0,sample);assertEquals(1,cache.point(block,new Vec3(2,0,0),457,sample).x());
  assertEquals(1,cache.point(block,new Vec3(1,0,0),458,sample).x());assertEquals(2,cache.point(block,new Vec3(1.001,0,0),458,sample).x());
  cache.clear();assertEquals(3,cache.point(block,player,459,sample).x());
 }
}
