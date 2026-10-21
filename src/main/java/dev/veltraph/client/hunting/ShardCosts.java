package dev.veltraph.client.hunting;
import java.util.*;

/** Literal recovered SkyblockNameTable COST_MAP and intValue106 partial-progress formula. */
public final class ShardCosts {
    private static final int[][] DEFAULTS={
        {0,1,3,5,6,7,8,10,14,18,24},{0,1,2,3,4,5,6,7,8,12,16},
        {0,1,2,3,3,4,4,5,6,8,12},{0,1,1,2,2,3,3,4,4,5,7},{0,1,1,1,2,2,2,3,3,4,5}};
    private ShardCosts(){}
    public static int needed(ShardCatalog.Rarity rarity,int current,int wanted,Integer progress){
        if(current<0||current>10||wanted<0||wanted>10)throw new IllegalArgumentException("Attribute levels must be 0..10");
        if(wanted<=current)return 0;
        int[] costs=DEFAULTS[Objects.requireNonNull(rarity).ordinal()];int total=0;
        for(int level=current+1;level<=wanted;level++)total=Math.addExact(total,costs[level]);
        if(progress!=null)total+=Math.clamp(progress,0,costs[current+1])-costs[current+1];
        return total;
    }
    public static int[] table(ShardCatalog.Rarity rarity){return DEFAULTS[rarity.ordinal()].clone();}
}
