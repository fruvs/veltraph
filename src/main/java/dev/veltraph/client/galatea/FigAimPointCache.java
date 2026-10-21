package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
import java.util.function.Supplier;
/** Source point-cache age/distance policy. Touch must represent source contact, not a frame. */
public final class FigAimPointCache {
 private record Entry(Vec3 point,Vec3 player,long touched) {}
 private final Map<BlockPos,Entry> points=new HashMap<>();
 public Vec3 point(BlockPos block,Vec3 player,long now,Supplier<Vec3> sample) {
  points.entrySet().removeIf(e->expired(e.getValue(),player,now));
  return points.computeIfAbsent(block,p->new Entry(Objects.requireNonNull(sample.get()),player,now)).point();
 }
 private static boolean expired(Entry e,Vec3 player,long now) {
  long age=now-e.touched();return age>1820||(age>457&&e.player().distanceTo(player)>1);
 }
 public void touch(BlockPos block,long now) {var e=points.get(block);if(e!=null)points.put(block,new Entry(e.point(),e.player(),now));}
 public void clear(){points.clear();}
}
