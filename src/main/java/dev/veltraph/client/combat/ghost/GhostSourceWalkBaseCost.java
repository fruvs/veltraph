package dev.veltraph.client.combat.ghost;

/** GRAPH1204–1225 base cost only. This does not replace later native block/turn/config/region modifiers. */
public final class GhostSourceWalkBaseCost {
    private GhostSourceWalkBaseCost() {}
    public static double observedBase(int dx,int dy,int dz,double destinationStandingHeightMinusOrigin,int fromY,int toY){
        if(!Double.isFinite(destinationStandingHeightMinusOrigin))throw new IllegalArgumentException("Observed standing height difference required");
        int rise=destinationStandingHeightMinusOrigin<0?0:(int)destinationStandingHeightMinusOrigin;
        int sum=Math.abs(dx)+rise+Math.abs(dz);
        double cost=sum>=3?1.73:sum==2?1.41:1.0;
        double drop=(double)fromY-toY;if(drop>1)cost+=drop*2;
        if(dy>1)cost+=dy*2;return cost;
    }
}
