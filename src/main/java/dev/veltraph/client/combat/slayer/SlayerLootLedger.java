package dev.veltraph.client.combat.slayer;

import java.util.*;

/** Exact tracked-drop receipt plus full-content inventory delta; disappearance cannot confirm delivery. */
public final class SlayerLootLedger {
    private static final class Drop {
        final Object content;final int units,baseline;int received;boolean delivered;
        Drop(Object content,int units,int baseline){this.content=content;this.units=units;this.baseline=baseline;}
    }
    private final Map<UUID,Drop> drops=new LinkedHashMap<>();
    private final Map<Object,Integer> firstBaseline=new HashMap<>(),credited=new HashMap<>();
    private int delivered;private boolean invalid;
    public void reset(){drops.clear();firstBaseline.clear();credited.clear();delivered=0;invalid=false;}
    public void track(UUID id,Object fullContent,int units,int inventoryBaseline){
        if(id==null||fullContent==null||units<=0||inventoryBaseline<0)return;
        firstBaseline.putIfAbsent(fullContent,inventoryBaseline);drops.putIfAbsent(id,new Drop(fullContent,units,inventoryBaseline));
    }
    public void receipt(UUID item,Object fullContent,int amount){
        var drop=drops.get(item);if(drop==null||drop.delivered||!drop.content.equals(fullContent)||amount<=0)return;
        if(amount>drop.units-drop.received){invalid=true;return;}drop.received+=amount;
    }
    public boolean inventory(UUID item,int matchingFullContentCount){
        var drop=drops.get(item);if(invalid||drop==null||drop.delivered||drop.received!=drop.units)return false;
        if(matchingFullContentCount-drop.baseline<drop.received
                ||matchingFullContentCount-firstBaseline.get(drop.content)-credited.getOrDefault(drop.content,0)<drop.received)return false;
        drop.delivered=true;credited.merge(drop.content,drop.received,Integer::sum);delivered+=drop.received;return true;
    }
    public boolean delivered(UUID id){var d=drops.get(id);return d!=null&&d.delivered;}
    public boolean complete(){return !invalid&&!drops.isEmpty()&&drops.values().stream().allMatch(d->d.delivered);}
    public boolean invalid(){return invalid;}
    public int delivered(){return delivered;}
}
