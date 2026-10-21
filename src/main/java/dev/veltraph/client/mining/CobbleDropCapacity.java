package dev.veltraph.client.mining;

import java.util.List;

/** Capacity for a genuinely attributed cobblestone drop; compatibility is native item+components. */
public final class CobbleDropCapacity {
    private CobbleDropCapacity(){}
    public record Cell(boolean empty,boolean sameDefaultCobble,int count,int max){}
    public static boolean available(boolean enchantedTool,List<Cell> cells){
        return cells.stream().anyMatch(c->c.empty()||!enchantedTool&&c.sameDefaultCobble()&&c.count()>=0&&c.count()<c.max());
    }
}
