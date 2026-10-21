package dev.veltraph.client.mining.vein;
/** Additional present-value refusal only; no prediction or native-velocity history claim. */
public final class VeinCollectionMotion {
    private VeinCollectionMotion(){}
    public static boolean decay(double before,double current){return Double.isFinite(before)&&Double.isFinite(current)
        &&(current==0||before!=0&&Math.signum(before)==Math.signum(current)&&Math.abs(current)<=Math.abs(before));}
    public static boolean unchangedOrDecaying(double x,double y,double z,double currentX,double currentY,double currentZ){
        return decay(x,currentX)&&decay(y,currentY)&&decay(z,currentZ)&&currentY<=0;
    }
}
