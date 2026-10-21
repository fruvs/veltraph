package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import java.util.*;

/** Original equipment/context proof survives only the caller's owned seller menu, independently of final closed-menu return. */
public final class VisitorPostQueueWitness {
    private VisitorPostQueueWitness(){}
    public static boolean equipment(NamedLoadoutFlow.Observation current,Object player,Object world,Object connection,
            Map<String,NamedLoadoutFlow.Gear> originalGear,NamedLoadoutFlow.Gear originalTool,NamedLoadoutFlow.Gear currentTool,
            boolean frozenSettings,boolean ownedMenu,boolean swapped,NamedLoadoutFlow.Receipt receipt,NamedLoadoutFlow.Selection original){
        if(current==null||player==null||world==null||connection==null||current.player()!=player||current.world()!=world||current.connection()!=connection
                ||!frozenSettings||!ownedMenu||originalTool==null||!originalTool.equals(currentTool)||!originalGear.equals(current.gear()))return false;
        if(!swapped)return true;
        return receipt!=null&&original!=null&&receipt.restoring()&&receipt.name().equals(original.name())&&receipt.gear().equals(original.gear())
                &&receipt.player()==player&&receipt.world()==world&&receipt.connection()==connection&&current.gear().equals(original.gear());
    }
}
