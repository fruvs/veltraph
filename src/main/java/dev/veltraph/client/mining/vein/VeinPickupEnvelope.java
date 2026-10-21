package dev.veltraph.client.mining.vein;
/** Literal native non-passenger Player.aiStep query envelope; not pickup or inventory credit. */
public final class VeinPickupEnvelope {
    private VeinPickupEnvelope(){}
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        public boolean valid(){return Double.isFinite(minX)&&Double.isFinite(minY)&&Double.isFinite(minZ)&&Double.isFinite(maxX)&&Double.isFinite(maxY)&&Double.isFinite(maxZ)&&minX<maxX&&minY<maxY&&minZ<maxZ;}
    }
    public static boolean overlaps(Box player,Box item){return player!=null&&item!=null&&player.valid()&&item.valid()
        &&player.minX()-1<item.maxX()&&player.maxX()+1>item.minX()
        &&player.minY()-.5<item.maxY()&&player.maxY()+.5>item.minY()
        &&player.minZ()-1<item.maxZ()&&player.maxZ()+1>item.minZ();}
}
