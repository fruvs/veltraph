package dev.veltraph.client.navigation;
/** Official26.3 BlockCollisions Cursor3D domain, including its expanded neighboring-cell halo. */
public final class FlightCollisionDomain {
 public record Domain(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){
  public long cells(){return ((long)maxX-minX+1)*((long)maxY-minY+1)*((long)maxZ-minZ+1);}
 }
 private FlightCollisionDomain(){}
 public static Domain of(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
  if(!valid(minX,maxX)||!valid(minY,maxY)||!valid(minZ,maxZ))return null;
  return new Domain((int)Math.floor(minX-1e-7)-1,(int)Math.floor(minY-1e-7)-1,(int)Math.floor(minZ-1e-7)-1,
      (int)Math.floor(maxX+1e-7)+1,(int)Math.floor(maxY+1e-7)+1,(int)Math.floor(maxZ+1e-7)+1);
 }
 private static boolean valid(double min,double max){return Double.isFinite(min)&&Double.isFinite(max)&&min<=max&&min>=-30_000_128&&max<=30_000_128;}
}
