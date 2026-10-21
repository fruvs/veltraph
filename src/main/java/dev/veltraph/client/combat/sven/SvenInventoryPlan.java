package dev.veltraph.client.combat.sven;

import java.util.*;
import java.util.random.RandomGenerator;

/** Readable FreeHotbarSlot helper selection; source excludes slot 44 and never drops an item. */
public final class SvenInventoryPlan {
    private SvenInventoryPlan(){}
    public record Slot(int index,int count,boolean playerInventory,boolean protectedTag,String registryPath){}
    public enum Kind { READY, QUICK_MOVE, FAILED }
    public record Decision(Kind kind,int slot,String reason){}
    public static Decision choose(List<Slot> inventory,String protectedPathToken,RandomGenerator random){
        if(protectedPathToken==null||protectedPathToken.isBlank())return new Decision(Kind.FAILED,-1,"Current protected registry-path token is unbound");
        var hotbar=inventory.stream().filter(s->s.playerInventory()&&s.index()>=36&&s.index()<44).toList();
        if(hotbar.size()!=8)return new Decision(Kind.FAILED,-1,"Actual source hotbar slot topology 36..43 unavailable");
        if(hotbar.stream().anyMatch(s->s.count()==0))return new Decision(Kind.READY,-1,"");
        var movable=hotbar.stream().filter(s->!s.protectedTag()&&!s.registryPath().contains(protectedPathToken)).toList();
        if(movable.isEmpty())return new Decision(Kind.FAILED,-1,"No source-eligible unprotected hotbar slot");
        return new Decision(Kind.QUICK_MOVE,movable.get(random.nextInt(movable.size())).index(),"");
    }
}
