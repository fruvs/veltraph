package dev.veltraph.client.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Menu object/revision/complete slot and cursor snapshot guards for owned recipe/results. */
final class CarpentryFlowMenus {
    record Item(String id,String name,String components,int count){boolean matches(CarpentryFlowProfile.Item item){return count>0&&id.equals(item.id())&&name.equals(item.name())&&components.equals(item.components());}}
    record Slot(int id,int inventoryIndex,Item item,List<String> lore){boolean player(){return inventoryIndex>=0;}}
    record View(Object menu,int id,int revision,String title,Item cursor,List<Slot> slots){Slot slot(int id){return slots.stream().filter(s->s.id()==id).findFirst().orElse(null);}Slot inventory(int index){return slots.stream().filter(s->s.inventoryIndex()==index).findFirst().orElse(null);}}
    final Minecraft client;
    CarpentryFlowMenus(Minecraft client){this.client=client;}
    static String clean(String value){return value.replaceAll("§.","").strip();}
    static Item item(ItemStack stack){return new Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.isEmpty()?"":clean(stack.getHoverName().getString()),stack.getComponents().toString(),stack.getCount());}
    View read(){
        if(!client.isSameThread())throw new IllegalStateException("Crafting requires client thread");
        if(client.player==null||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu)return null;
        var menu=screen.getMenu();var slots=new ArrayList<Slot>();for(var slot:menu.slots){var lore=slot.getItem().get(DataComponents.LORE);slots.add(new Slot(slot.index,slot.container==client.player.getInventory()?slot.getContainerSlot():-1,item(slot.getItem()),lore==null?List.of():lore.lines().stream().map(c->clean(c.getString())).toList()));}
        return new View(menu,menu.containerId,menu.getStateId(),clean(screen.getTitle().getString()),item(menu.getCarried()),List.copyOf(slots));
    }
    boolean request(View observed,int slot,ContainerInput action){if(observed==null||client.player==null||client.gameMode==null||!client.player.isAlive()||!observed.equals(read()))return false;var target=observed.slot(slot);if(target==null||target.item().id().equals("minecraft:barrier")||target.item().id().equals("minecraft:bedrock"))return false;client.gameMode.handleContainerInput(observed.id(),slot,0,action,client.player);return true;}
    int count(CarpentryFlowProfile.Item expected){int sum=0;for(int i=0;i<36;i++){var s=item(client.player.getInventory().getItem(i));if(s.count()>0&&s.id().equals(expected.id())&&s.name().equals(expected.name())&&!s.components().equals(expected.components()))throw new IllegalStateException("Ambiguous components for "+expected.name());if(s.matches(expected))sum=Math.addExact(sum,s.count());}return sum;}
    int free(){int result=0;for(int i=0;i<36;i++)if(client.player.getInventory().getItem(i).isEmpty())result++;return result;}
}
