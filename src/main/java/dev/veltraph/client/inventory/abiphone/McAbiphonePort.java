package dev.veltraph.client.inventory.abiphone;

import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.mc.McInventoryService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Native client-thread port. Every mutation rechecks the complete exact observation binding. */
public final class McAbiphonePort implements AbiphoneCall.Port {
    private final Minecraft client;private final McMenuAccess menus;private final McInventoryService inventory;
    private final IdentityHashMap<AbiphoneCall.Observation,Binding> bindings=new IdentityHashMap<>();
    private record Binding(Object player,Object world,AbstractContainerMenu menu,int revision,int selected,List<ItemStack> inventory,Map<Integer,ItemStack> rows) {}
    private int transferTick=Integer.MIN_VALUE,transferRevision=-1;private Object transferMenu,predictionMenu;private ItemStack predictedCursor=ItemStack.EMPTY;
    public McAbiphonePort(Minecraft client){this.client=client;menus=new McMenuAccess(client);inventory=new McInventoryService(client);}
    public static AbiphoneCall.Item describe(ItemStack item){return item.isEmpty()?AbiphoneCall.Item.EMPTY:new AbiphoneCall.Item(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
            MenuSnapshot.clean(item.getHoverName().getString()),item.getCount(),item.getComponents().stream().map(Object::toString).sorted().toList().toString());}
    @Override public AbiphoneCall.Observation read(){
        if(!client.isSameThread())throw new IllegalStateException("Abiphone observation requires client thread");bindings.clear();
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null||client.getConnection()==null)
            return new AbiphoneCall.Observation(null,null,null,"",0,List.of(),List.of(),0,false,false,false,false);
        var player=client.player;var current=player.containerMenu;var snapshot=menus.read();
        Object observedMenu=snapshot!=null?current:client.gui.screen()!=null?client.gui.screen():current!=player.inventoryMenu?current:null;
        var stacks=new ArrayList<ItemStack>();var items=new ArrayList<AbiphoneCall.Item>();for(int i=0;i<36;i++){var stack=player.getInventory().getItem(i);stacks.add(stack.copy());items.add(describe(stack));}
        var rows=new ArrayList<AbiphoneCall.Row>();var copies=new HashMap<Integer,ItemStack>();
        for(var slot:current.slots){var stack=slot.getItem();copies.put(slot.index,stack.copy());var item=describe(stack);
            rows.add(new AbiphoneCall.Row(slot.index,slot.container==player.getInventory(),item.registryId().equals("minecraft:barrier")||item.registryId().equals("minecraft:bedrock"),item));}
        var result=new AbiphoneCall.Observation(player,client.level,observedMenu,snapshot==null?"":snapshot.title(),current.getStateId(),items,rows,
                player.getInventory().getSelectedSlot(),current.getCarried().isEmpty(),current==predictionMenu&&!predictedCursor.isEmpty()&&ItemStack.matches(predictedCursor,current.getCarried()),player.tickCount>transferTick,
                current==transferMenu&&current.getStateId()!=transferRevision&&player.tickCount>transferTick);
        bindings.put(result,new Binding(player,client.level,current,current.getStateId(),result.selectedSlot(),stacks,copies));return result;
    }
    private Binding valid(AbiphoneCall.Observation expected){
        if(!client.isSameThread())return null;var b=bindings.get(expected);
        if(b==null||client.player==null||!client.player.isAlive()||client.player!=b.player()||client.level!=b.world()||client.player.containerMenu!=b.menu()
                ||b.menu().getStateId()!=b.revision()||!b.menu().getCarried().isEmpty()||client.player.getInventory().getSelectedSlot()!=b.selected()||client.gameMode==null||client.getConnection()==null)return null;
        if(expected.menu()==null&&client.gui.screen()!=null)return null;
        if(expected.menu()!=null&&(!(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)||screen.getMenu()!=b.menu()))return null;
        for(int i=0;i<36;i++)if(!ItemStack.matches(b.inventory().get(i),client.player.getInventory().getItem(i)))return null;
        for(var slot:b.menu().slots)if(!ItemStack.matches(b.rows().get(slot.index),slot.getItem()))return null;
        return b;
    }
    @Override public boolean swap(AbiphoneCall.Observation expected,int mainIndex,int hotbarIndex){
        var b=valid(expected);if(b==null||mainIndex<9||mainIndex>=36||hotbarIndex<0||hotbarIndex>=9)return false;
        var main=b.menu().slots.stream().filter(s->s.container==client.player.getInventory()&&s.getContainerSlot()==mainIndex).toList();
        var hotbar=b.menu().slots.stream().filter(s->s.container==client.player.getInventory()&&s.getContainerSlot()==hotbarIndex).toList();
        if(main.size()!=1||hotbar.size()!=1||expected.inventory().get(mainIndex).empty()==expected.inventory().get(hotbarIndex).empty())return false;
        transferMenu=b.menu();transferRevision=b.revision();client.gameMode.handleContainerInput(b.menu().containerId,main.getFirst().index,hotbarIndex,ContainerInput.SWAP,client.player);transferTick=client.player.tickCount;return true;
    }
    @Override public boolean select(AbiphoneCall.Observation expected,int hotbarSlot){if(valid(expected)==null||hotbarSlot<0||hotbarSlot>=9)return false;inventory.selectSlot(hotbarSlot);return client.player.getInventory().getSelectedSlot()==hotbarSlot;}
    /** Native useItem is the explicit air-use route, so no block/entity interaction or view mutation is needed. */
    @Override public boolean use(AbiphoneCall.Observation expected){if(valid(expected)==null||expected.menu()!=null||client.gui.screen()!=null)return false;client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);return true;}
    @Override public boolean click(AbiphoneCall.Observation expected,int slotId){
        if(valid(expected)==null||expected.menu()==null)return false;var rows=expected.rows().stream().filter(r->r.slot()==slotId&&!r.playerInventory()&&!r.locked()&&!r.item().empty()).toList();
        if(rows.size()!=1)return false;var snapshot=menus.read();if(snapshot==null||snapshot.id()!=client.player.containerMenu.containerId||snapshot.revision()!=expected.revision()||!snapshot.title().equals(expected.title()))return false;
        if(!menus.click(snapshot,slotId))return false;predictionMenu=client.player.containerMenu;predictedCursor=client.player.containerMenu.getCarried().copy();return true;
    }
    @Override public boolean close(AbiphoneCall.Observation expected){if(valid(expected)==null||expected.menu()==null)return false;return menus.close(client.player.containerMenu.containerId);}
}
