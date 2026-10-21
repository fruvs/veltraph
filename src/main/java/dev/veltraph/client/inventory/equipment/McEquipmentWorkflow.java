package dev.veltraph.client.inventory.equipment;

import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.mc.McInventoryService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Client-thread native menu/item port. No raw menu/destruction packet generation. */
public final class McEquipmentWorkflow implements EquipmentWorkflow.Port {
    private final Minecraft client;private final McMenuAccess menus;private final McInventoryService inventory;
    private final IdentityHashMap<EquipmentWorkflow.Observation,Binding> bindings=new IdentityHashMap<>();
    private record Binding(Object player,Object world,Object menu,List<ItemStack> inventory,Map<Integer,ItemStack> slots) {}
    private int transferTick=Integer.MIN_VALUE;
    private Object ackMenu;private int issuedRevision=-1;private ItemStack predictedCursor=ItemStack.EMPTY;
    public McEquipmentWorkflow(Minecraft client){this.client=client;menus=new McMenuAccess(client);inventory=new McInventoryService(client);}
    private static String id(ItemStack item){return item.isEmpty()?"":BuiltInRegistries.ITEM.getKey(item.getItem()).toString();}
    private static EquipmentWorkflow.Item item(ItemStack stack){if(stack.isEmpty())return new EquipmentWorkflow.Item("","",0,"");return new EquipmentWorkflow.Item(id(stack),stack.isEmpty()?"":MenuSnapshot.clean(stack.getHoverName().getString()),stack.getCount(),stack.getComponents().stream().map(Object::toString).sorted().toList().toString());}
    @Override public EquipmentWorkflow.Observation read(){
        if(!client.isSameThread())throw new IllegalStateException("Equipment workflow requires client thread");
        bindings.clear();
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null)
            return new EquipmentWorkflow.Observation(null,null,null,null,List.of(),List.of(),0,false,false,false,false);
        var player=client.player;var current=player.containerMenu;var snapshot=menus.read();
        List<EquipmentWorkflow.Item> items=new ArrayList<>();List<ItemStack> copies=new ArrayList<>();
        for(int i=0;i<36;i++){var stack=player.getInventory().getItem(i);items.add(item(stack));copies.add(stack.copy());}
        var armor=Arrays.stream(new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}).map(s->item(player.getItemBySlot(s))).toList();
        boolean empty=current.getCarried().isEmpty();boolean predicted=current==ackMenu&&!predictedCursor.isEmpty()&&ItemStack.matches(predictedCursor,current.getCarried());
        boolean revision=ackMenu==null||current!=ackMenu||current.getStateId()!=issuedRevision;
        var observation=new EquipmentWorkflow.Observation(player,client.level,snapshot==null?null:current,snapshot,items,armor,player.getInventory().getSelectedSlot(),empty,predicted,revision,client.player.tickCount>transferTick);
        Map<Integer,ItemStack> slots=new HashMap<>();for(var slot:current.slots)slots.put(slot.index,slot.getItem().copy());
        bindings.put(observation,new Binding(player,client.level,current,copies,slots));return observation;
    }
    private Binding valid(EquipmentWorkflow.Observation expected){
        if(!client.isSameThread())return null;
        var b=bindings.get(expected);
        if(b==null||(expected.snapshot()==null&&client.gui.screen()!=null)||client.player==null||!client.player.isAlive()||client.level!=b.world()||client.player!=b.player()||client.player.containerMenu!=b.menu()||client.gameMode==null||client.getConnection()==null||!client.player.containerMenu.getCarried().isEmpty())return null;
        for(int i=0;i<36;i++)if(!ItemStack.matches(b.inventory().get(i),client.player.getInventory().getItem(i)))return null;
        return b;
    }
    private void issued(){ackMenu=client.player.containerMenu;issuedRevision=client.player.containerMenu.getStateId();predictedCursor=client.player.containerMenu.getCarried().copy();}
    @Override public boolean swap(EquipmentWorkflow.Observation expected,int index,int hotbar){
        var b=valid(expected);if(b==null||index<9||index>=36||hotbar<0||hotbar>=9||client.player.containerMenu!=client.player.inventoryMenu||expected.snapshot()!=null)return false;
        var candidates=client.player.inventoryMenu.slots.stream().filter(s->s.container==client.player.getInventory()&&s.getContainerSlot()==index).toList();
        if(candidates.size()!=1)return false;
        client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId,candidates.getFirst().index,hotbar,ContainerInput.SWAP,client.player);transferTick=client.player.tickCount;issued();return true;
    }
    /** Accepted native SWAP may be silent. Wait one vanilla correction tick before acting. */
    public boolean correctionTickPassed(){return client.player!=null&&client.player.tickCount>transferTick;}
    @Override public boolean select(EquipmentWorkflow.Observation expected,int slot){if(valid(expected)==null||slot<0||slot>=9)return false;inventory.selectSlot(slot);return client.player.getInventory().getSelectedSlot()==slot;}
    @Override public boolean use(EquipmentWorkflow.Observation expected){if(valid(expected)==null||expected.snapshot()!=null||client.gui.screen()!=null)return false;client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);return true;}
    @Override public boolean command(EquipmentWorkflow.Observation expected,String command){if(valid(expected)==null||client.gui.screen()!=null||!command.matches("/?[a-zA-Z0-9 _:-]+"))return false;client.player.connection.sendCommand(command.startsWith("/")?command.substring(1):command);return true;}
    @Override public boolean click(EquipmentWorkflow.Observation expected,int slot){
        var b=valid(expected);if(b==null||expected.snapshot()==null||!b.slots().containsKey(slot)||slot<0||slot>=client.player.containerMenu.slots.size()||!ItemStack.matches(b.slots().get(slot),client.player.containerMenu.getSlot(slot).getItem()))return false;
        if(!menus.click(expected.snapshot(),slot))return false;issued();return true;
    }
    @Override public boolean close(EquipmentWorkflow.Observation expected){if(valid(expected)==null||expected.snapshot()==null)return false;return menus.close(expected.snapshot().id());}
}
