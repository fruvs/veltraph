package dev.veltraph.client.builder;

import dev.veltraph.client.inventory.equipment.McEquipmentWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/** Native inventory SWAP into an empty hotbar cell, bound to player/world and a later correction tick. */
final class GeneratedInventoryRestock {
    private final Minecraft client;
    private final McEquipmentWorkflow inventory;
    private Object player,world;
    private int source=-1,destination=-1;
    private ItemStack supplied=ItemStack.EMPTY;
    GeneratedInventoryRestock(Minecraft client){this.client=client;inventory=new McEquipmentWorkflow(client);}
    boolean pending(){return source>=0;}
    boolean begin(Predicate<ItemStack> match,int preferred) {
        if(pending()||client.player==null||client.level==null||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return false;
        int target=preferred>=0&&preferred<9&&client.player.getInventory().getItem(preferred).isEmpty()?preferred:-1;
        if(target<0)for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).isEmpty()){target=i;break;}
        if(target<0)return false;
        for(int i=9;i<36;i++)if(match.test(client.player.getInventory().getItem(i))) {
            player=client.player;world=client.level;source=i;destination=target;supplied=client.player.getInventory().getItem(i).copy();
            if(inventory.swap(inventory.read(),i,target))return true;
            source=destination=-1;return false;
        }
        return false;
    }
    boolean ready() {
        return owned()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
                &&inventory.correctionTickPassed()&&client.player.getInventory().getItem(source).isEmpty()
                &&ItemStack.matches(supplied,client.player.getInventory().getItem(destination));
    }
    void release() {
        // The destination began empty. Only return the still-identical supplied stack; consumed or
        // damaged items remain in the hotbar instead of guessing their components or overwriting a slot.
        if(ready())inventory.swap(inventory.read(),source,destination);
        source=destination=-1;supplied=ItemStack.EMPTY;player=world=null;
    }
    void keepTransfer(){source=destination=-1;supplied=ItemStack.EMPTY;player=world=null;}
    private boolean owned(){return source>=0&&client.player==player&&client.level==world&&client.player!=null;}
}
