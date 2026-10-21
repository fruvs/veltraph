package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
import java.util.function.Predicate;
/** Readable five-point test and outline-visible grid fallback. Never relaxes an attack guard. */
public final class FigAimPointResolver {
 public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
  public boolean contains(Vec3 p){return p.x()>=minX&&p.y()>=minY&&p.z()>=minZ&&p.x()<maxX&&p.y()<maxY&&p.z()<maxZ;}
 }
 private FigAimPointResolver() {}
 private static Vec3 add(Vec3 p,double x,double y,double z){return new Vec3(p.x()+x,p.y()+y,p.z()+z);}
 public static Vec3 resolve(BlockPos block,Bounds box,Vec3 sampled,Predicate<Vec3> hitsTarget) {
  var probes=List.of(sampled,add(sampled,-.15,-.15,-.15),add(sampled,.15,-.15,-.15),add(sampled,-.15,.15,-.15),add(sampled,.15,.15,.15));
  if(probes.stream().allMatch(hitsTarget))return sampled;
  var visible=new ArrayList<Vec3>();
  for(int x=0;x<=3;x++)for(int y=0;y<=3;y++)for(int z=0;z<=3;z++) {
   var p=new Vec3(block.x()+Math.max(.06125,Math.min(.93875,x/3.0)),block.y()+Math.max(.06125,Math.min(.93875,y/3.0)),block.z()+Math.max(.06125,Math.min(.93875,z/3.0)));
   if(box.contains(p)&&hitsTarget.test(p))visible.add(p);
  }
  if(visible.isEmpty())return sampled;
  var mean=new Vec3(visible.stream().mapToDouble(Vec3::x).average().orElseThrow(),visible.stream().mapToDouble(Vec3::y).average().orElseThrow(),visible.stream().mapToDouble(Vec3::z).average().orElseThrow());
  return hitsTarget.test(mean)?mean:visible.get(visible.size()/2);
 }
}
