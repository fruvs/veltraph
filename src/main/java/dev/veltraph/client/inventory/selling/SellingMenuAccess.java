package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;

/** Narrow exact-stack operations. There is deliberately no general player-slot click/drop surface. */
public final class SellingMenuAccess {
    private record Binding(Object player,Object world,AbstractContainerMenu menu) {}
    private final Minecraft client;private final McMenuAccess buttons;
    private final Map<SellingObservation,Binding> bindings=new IdentityHashMap<>();
    private final Deque<SellingObservation> retained=new ArrayDeque<>();
    private ItemStack predictedCursor=ItemStack.EMPTY;private Object predictionMenu;
    private net.minecraft.world.phys.Vec3 dropOrigin;
    public SellingMenuAccess(Minecraft client){this.client=client;buttons=new McMenuAccess(client);}
    public static String components(ItemStack item){return item.getComponents().stream().map(Object::toString).sorted().toList().toString();}
    private static boolean protectedItem(ItemStack stack){return stack.isDamageableItem()||stack.has(DataComponents.TOOL)
            ||stack.has(DataComponents.WEAPON)||stack.has(DataComponents.BLOCKS_ATTACKS);}
    private static Map<String,String> customData(ItemStack item){
        var data=item.get(DataComponents.CUSTOM_DATA);if(data==null)return Map.of();Map<String,String> result=new HashMap<>();flatten(data.copyTag(),"",0,result);return result;
    }
    private static void flatten(net.minecraft.nbt.CompoundTag tag,String prefix,int depth,Map<String,String> result){
        if(depth>4)return;for(String key:tag.keySet()){
            if(result.size()>=64)return;String path=prefix.isEmpty()?key:prefix+"."+key;var value=tag.get(key);if(value==null)continue;
            if(value instanceof net.minecraft.nbt.CompoundTag child)flatten(child,path,depth+1,result);else result.put(path,value.toString());
        }
    }
    private static SellingObservation.Stack stack(ItemStack item,int slot,int index){
        return new SellingObservation.Stack(slot,index,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                item.isEmpty()?"":item.getHoverName().getString(),components(item),item.getCount(),protectedItem(item),item.has(DataComponents.EQUIPPABLE),customData(item));
    }
    /** Copy/describe a main stack for inspection; does not register a click binding or change state. */
    public static SellingObservation.Stack describeInventoryStack(ItemStack item,int index){return stack(item.copy(),-1,index);}
    public SellingObservation read(String pursePrefix){
        if(client.player==null||client.level==null)return new SellingObservation(null,List.of(),Map.of(),null,null,List.of(),false);
        MenuSnapshot menu=buttons.read();var handler=client.player.containerMenu;
        Map<Integer,SellingObservation.Stack> slots=new HashMap<>();Map<Integer,Integer> inventorySlots=new HashMap<>();
        if(menu!=null)for(var slot:handler.slots){int index=slot.container==client.player.getInventory()?slot.getContainerSlot():-1;
            slots.put(slot.index,stack(slot.getItem(),slot.index,index));if(index>=0)inventorySlots.put(index,slot.index);}
        List<SellingObservation.Stack> inventory=new ArrayList<>();
        for(int i=0;i<client.player.getInventory().getContainerSize();i++)inventory.add(stack(client.player.getInventory().getItem(i),inventorySlots.getOrDefault(i,-1),i));
        var cursor=stack(handler.getCarried(),-1,-1);
        List<SellingObservation.DroppedItem> dropped=new ArrayList<>();
        for(var entity:client.level.entitiesForRendering())if(entity instanceof ItemEntity item&&!item.getItem().isEmpty()){
            var value=stack(item.getItem(),-1,-1);double distance=dropOrigin==null?Double.POSITIVE_INFINITY:entity.position().distanceTo(dropOrigin);
            dropped.add(new SellingObservation.DroppedItem(entity.getId(),value.identity(),value.count(),distance));
        }
        boolean prediction=!predictedCursor.isEmpty()&&predictionMenu==handler&&ItemStack.matches(predictedCursor,handler.getCarried());
        var observed=new SellingObservation(menu,inventory,slots,cursor,currency(pursePrefix),dropped,prediction);
        bindings.put(observed,new Binding(client.player,client.level,handler));retained.addLast(observed);
        while(retained.size()>32)bindings.remove(retained.removeFirst());return observed;
    }
    public static BigDecimal parseCurrency(List<String> lines,String requiredPrefix){
        return SellingCurrency.parse(lines,requiredPrefix);
    }
    private BigDecimal currency(String prefix){
        var scoreboard=client.level.getScoreboard();var objective=scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);if(objective==null)return null;
        List<String> lines=new ArrayList<>();for(var entry:scoreboard.listPlayerScores(objective)){
            var team=scoreboard.getPlayersTeam(entry.owner());lines.add(team==null?entry.owner():team.getPlayerPrefix().getString()+entry.owner()+team.getPlayerSuffix().getString());}
        return parseCurrency(lines,prefix);
    }
    public boolean act(SellingRuntime.Action action,SellingRule rule,String prefix){
        if(!client.isSameThread())throw new IllegalStateException("Selling requires client thread");
        var bound=bindings.get(action.observation());
        if(bound==null||client.player==null||!client.player.isAlive()||client.gameMode==null||client.level!=bound.world||client.player!=bound.player
                ||client.player.containerMenu!=bound.menu)return false;
        var fresh=read(prefix);var expected=action.observation();
        if(fresh.menu()==null||expected.menu()==null||fresh.menu().id()!=expected.menu().id()||fresh.menu().revision()!=expected.menu().revision()
                ||!fresh.menu().title().equals(expected.menu().title())||!fresh.ledger().equals(expected.ledger())
                ||!Objects.equals(fresh.cursor(),expected.cursor())||!Objects.equals(fresh.slots().get(action.slot()),expected.slots().get(action.slot())))return false;
        var slot=fresh.slots().get(action.slot());
        switch(action.kind()){
            case CLOSE -> {if(!fresh.cursorEmpty())return false;return buttons.close(fresh.menu().id());}
            case ACCEPT,CONFIRM -> {
                if(!fresh.cursorEmpty())return false;
                boolean accepted=buttons.click(fresh.menu(),action.slot());if(accepted)prediction();return accepted;
            }
            case SELL,PICKUP,DROP -> {
                if(!fresh.cursorEmpty()||slot==null||slot.inventoryIndex()<9||slot.inventoryIndex()>35||!rule.matches(slot))return false;
                if(action.kind()==SellingRuntime.Kind.DROP&&(!(client.gui.screen() instanceof InventoryScreen)||bound.menu!=client.player.inventoryMenu))return false;
            }
            case DEPOSIT -> {if(slot==null||slot.inventoryIndex()>=0||!slot.empty()||fresh.cursor()==null||!fresh.cursor().sameItem(rule))return false;}
            case RETURN_DEPOSIT -> {if(!fresh.cursorEmpty()||slot==null||slot.inventoryIndex()>=0||!slot.sameItem(rule))return false;}
            case RETURN_ORIGIN -> {if(slot==null||slot.inventoryIndex()!=rule.inventoryIndex()||!slot.empty()||fresh.cursor()==null||!fresh.cursor().sameItem(rule))return false;}
        }
        if(action.kind()==SellingRuntime.Kind.DROP)dropOrigin=client.player.position();
        client.gameMode.handleContainerInput(fresh.menu().id(),action.slot(),action.kind()==SellingRuntime.Kind.DROP?1:0,
                action.kind()==SellingRuntime.Kind.DROP?ContainerInput.THROW:ContainerInput.PICKUP,client.player);
        prediction();return true;
    }
    private void prediction(){predictionMenu=client.player.containerMenu;predictedCursor=client.player.containerMenu.getCarried().copy();}
    public void clear(){bindings.clear();retained.clear();predictedCursor=ItemStack.EMPTY;predictionMenu=null;dropOrigin=null;}
    public boolean closeOwned(int id,String title){var current=buttons.read();return current!=null&&current.id()==id&&current.title().equals(title)
            &&client.player.containerMenu.getCarried().isEmpty()&&buttons.close(id);}
}
