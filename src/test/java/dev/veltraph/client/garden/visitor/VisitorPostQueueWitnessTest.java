package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorPostQueueWitnessTest {
    final Object player=new Object(),world=new Object(),connection=new Object();
    final NamedLoadoutFlow.Gear tool=new NamedLoadoutFlow.Gear("minecraft:iron_hoe",1,"same exact farm tool components");
    final Map<String,NamedLoadoutFlow.Gear> gear=Map.of("HEAD",new NamedLoadoutFlow.Gear("minecraft:iron_helmet",1,"farm armor"),"OFFHAND",new NamedLoadoutFlow.Gear("minecraft:shield",1,"farm shield"));
    final NamedLoadoutFlow.Selection original=new NamedLoadoutFlow.Selection("Loadout 1",gear);
    NamedLoadoutFlow.Receipt receipt=new NamedLoadoutFlow.Receipt(player,world,connection,"Loadout 1",gear,new Object(),9,17,true);
    NamedLoadoutFlow.Observation observation(Object menu,Object p,Object w,Object c,Map<String,NamedLoadoutFlow.Gear> equipped){return new NamedLoadoutFlow.Observation(p,w,c,menu,menu==null?null:new MenuSnapshot(3,"Owned Seller",List.of(),10),equipped,true,false,true);}
    boolean proof(NamedLoadoutFlow.Observation current,boolean settings,boolean owned,boolean swapped,NamedLoadoutFlow.Gear currentTool){return VisitorPostQueueWitness.equipment(current,player,world,connection,gear,tool,currentTool,settings,owned,swapped,receipt,original);}
    @Test void restoredOriginalNamedGearRemainsValidInOwnedSellerMenu(){assertTrue(proof(observation(new Object(),player,world,connection,gear),true,true,true,tool));}
    @Test void foreignMenuCannotBorrowRestorationReceipt(){assertFalse(proof(observation(new Object(),player,world,connection,gear),true,false,true,tool));}
    @Test void changedActualGearOrExactFarmToolCannotBorrowReceipt(){assertFalse(proof(observation(new Object(),player,world,connection,Map.of()),true,true,true,tool));assertFalse(proof(observation(null,player,world,connection,gear),true,true,true,new NamedLoadoutFlow.Gear(tool.registryId(),1,"replaced tool components")));}
    @Test void replacedNativePlayerWorldOrConnectionCannotBorrowReceipt(){for(int i=0;i<3;i++)assertFalse(proof(observation(null,i==0?new Object():player,i==1?new Object():world,i==2?new Object():connection,gear),true,true,true,tool));}
    @Test void changedSettingsOrUnacknowledgedNamedRestorationCannotBorrowReceipt(){var current=observation(null,player,world,connection,gear);assertFalse(proof(current,false,true,true,tool));receipt=new NamedLoadoutFlow.Receipt(player,world,connection,"Loadout 1",gear,new Object(),9,17,false);assertFalse(proof(current,true,true,true,tool));}
    @Test void noSwapStillRequiresOriginalGearToolNativeContextAndOwnedMenu(){assertTrue(proof(observation(new Object(),player,world,connection,gear),true,true,false,tool));assertFalse(proof(observation(null,new Object(),world,connection,gear),true,true,false,tool));assertFalse(proof(observation(null,player,world,connection,Map.of()),true,true,false,tool));}
    @Test void wrongOriginalReceiptContextCannotBorrowRestoration(){receipt=new NamedLoadoutFlow.Receipt(player,world,new Object(),"Loadout 1",gear,new Object(),9,17,true);assertFalse(proof(observation(null,player,world,connection,gear),true,true,true,tool));}
}
