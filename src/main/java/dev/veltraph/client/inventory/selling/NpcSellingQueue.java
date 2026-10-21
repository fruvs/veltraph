package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import java.util.*;

/** Source ascending/descending native row order, restricted to explicit exact full-stack authorizations. */
public final class NpcSellingQueue {
    private NpcSellingQueue(){}
    public static List<SellingRule> freeze(List<SellingRule> rules,SellingObservation observed,boolean ascending){
        var result=new ArrayList<SellingRule>();var indices=new HashSet<Integer>();
        for(var rule:rules)if((rule.channel()==AutoSellMode.NPC||rule.channel()==AutoSellMode.TRASH)&&rule.matches(observed.inventoryStack(rule.inventoryIndex()).orElse(null))){if(!indices.add(rule.inventoryIndex()))throw new IllegalArgumentException("Duplicate native queue slot");result.add(rule);}
        if(result.isEmpty())throw new IllegalArgumentException("No authorized actual nonempty native rows");
        result.sort(Comparator.comparingInt(SellingRule::inventoryIndex));if(!ascending)Collections.reverse(result);return List.copyOf(result);
    }
}
