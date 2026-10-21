package dev.veltraph.client.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Normal container requests with complete slot/cursor/revision identity checks, including owned cursor transfers. */
final class SkillMenuSession {
    record Item(String id,String name,String components,int count) {
        boolean matches(String expectedId,String expectedName){return count>0&&id.equals(expectedId)&&name.equals(expectedName);}
        Item withCount(int n){return new Item(id,name,components,n);}
    }
    record Slot(int id,boolean player,Item item,List<String> lore) {}
    record View(Object menu,int id,int revision,String title,Item cursor,List<Slot> slots) {
        List<Slot> matching(String id,String name,boolean player){return slots.stream().filter(s->s.player()==player&&s.item().matches(id,name)).toList();}
        Slot slot(int id){return slots.stream().filter(s->s.id()==id).findFirst().orElse(null);}
    }
    private final Minecraft client;
    SkillMenuSession(Minecraft client){this.client=client;}
    static String clean(String value){return value.replaceAll("§.","").strip();}
    static Item item(ItemStack stack){return new Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.isEmpty()?"":clean(stack.getHoverName().getString()),stack.getComponents().toString(),stack.getCount());}
    View read(){
        if(client.player==null||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu)return null;
        var menu=screen.getMenu();var slots=new ArrayList<Slot>();
        for(var s:menu.slots){var lore=s.getItem().get(DataComponents.LORE);slots.add(new Slot(s.index,s.container==client.player.getInventory(),item(s.getItem()),lore==null?List.of():lore.lines().stream().map(t->clean(t.getString())).toList()));}
        return new View(menu,menu.containerId,menu.getStateId(),clean(screen.getTitle().getString()),item(menu.getCarried()),List.copyOf(slots));
    }
    boolean request(View expected,int slot,ContainerInput input){
        if(expected==null||slot<0||client.gameMode==null||client.player==null||!client.player.isAlive()||!expected.equals(read()))return false;
        var target=expected.slot(slot);if(target==null)return false;
        if(target.item().id().equals("minecraft:barrier")||target.item().id().equals("minecraft:bedrock"))return false;
        client.gameMode.handleContainerInput(expected.id(),slot,0,input,client.player);return true;
    }
    Map<String,Integer> inventory(String id,String name){
        var variants=new HashMap<String,Integer>();if(client.player==null)return variants;
        for(int i=0;i<36;i++){var s=item(client.player.getInventory().getItem(i));if(s.matches(id,name))variants.merge(s.components(),s.count(),Math::addExact);}
        return Map.copyOf(variants);
    }
    int count(String id,String name){var variants=inventory(id,name);if(variants.size()>1)throw new IllegalStateException("Ambiguous item components for "+name);return variants.values().stream().mapToInt(Integer::intValue).sum();}
    boolean capacity(int quantity){
        if(client.player==null)return false;int free=0;for(int i=0;i<36;i++)if(client.player.getInventory().getItem(i).isEmpty())free++;
        return free>=Math.max(1,quantity); // Conservative empty-slot reserve also accommodates unstackable potion outputs.
    }
    int stackLimit(String id){var key=net.minecraft.resources.Identifier.tryParse(id);if(key==null||!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown local item registry ID");var item=new ItemStack(BuiltInRegistries.ITEM.getValue(key));if(item.isEmpty())throw new IllegalArgumentException("Air is not a skill resource");return item.getMaxStackSize();}
}
