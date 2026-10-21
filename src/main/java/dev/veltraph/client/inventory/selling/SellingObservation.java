package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;

/** Observed client/server state. Cursor and inventory predictions need a changed server revision. */
public record SellingObservation(MenuSnapshot menu,List<Stack> inventory,Map<Integer,Stack> slots,Stack cursor,
                                 BigDecimal coins,List<DroppedItem> droppedItems,boolean ownCursorPrediction) {
    public record Stack(int slot,int inventoryIndex,String registryId,String name,String components,int count,boolean protectedItem,
                        boolean wearable,Map<String,String> customData) {
        public Stack(int slot,int inventoryIndex,String registryId,String name,String components,int count,boolean protectedItem){this(slot,inventoryIndex,registryId,name,components,count,protectedItem,false,Map.of());}
        public Stack { name=MenuSnapshot.clean(name);customData=Map.copyOf(customData); }
        public String identity(){return registryId+"\n"+name+"\n"+components;}
        public boolean empty(){return count==0;}
        public boolean sameItem(SellingRule rule){return identity().equals(rule.identity())&&count==rule.quantity();}
        public String ledgerValue(){return identity()+"\n"+count;}
    }
    public record DroppedItem(int entityId,String identity,int count,double distanceFromDropOrigin) {}
    public SellingObservation {inventory=List.copyOf(inventory);slots=Map.copyOf(slots);droppedItems=List.copyOf(droppedItems);}
    public boolean cursorEmpty(){return cursor==null||cursor.empty();}
    public Map<Integer,String> ledger(){
        Map<Integer,String> result=new HashMap<>();for(var stack:inventory)if(!stack.empty())result.put(stack.inventoryIndex(),stack.ledgerValue());return Map.copyOf(result);
    }
    public Optional<Stack> inventoryStack(int index){return inventory.stream().filter(s->s.inventoryIndex()==index).findFirst();}
}
