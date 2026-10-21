package dev.veltraph.client.mining.vein;

import dev.veltraph.client.galatea.FigHarvestPickupLedger.Cell;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Native producer codec: all effective components, original registry, exactly 36 ordinary inventory cells. */
public final class VeinPickupNativeSnapshots {
    private VeinPickupNativeSnapshots() {}
    public static Cell cell(int index,ItemStack stack,HolderLookup.Provider registry){
        if(index<0||index>35)throw new IllegalArgumentException("Not an ordinary inventory cell");
        Objects.requireNonNull(stack);Objects.requireNonNull(registry);
        var copy=stack.copy();int count=copy.isEmpty()?0:copy.getCount();
        var unit=NativeStackIdentity.capture(count==0?ItemStack.EMPTY:copy.copyWithCount(1),registry);
        return new Cell(index,unit,count,NativeStackIdentity.capture(copy,registry));
    }
    public static List<Cell> inventory(net.minecraft.world.entity.player.Inventory inventory,HolderLookup.Provider registry){
        Objects.requireNonNull(inventory);var cells=new ArrayList<Cell>(36);
        for(int index=0;index<36;index++)cells.add(cell(index,inventory.getItem(index),registry));return List.copyOf(cells);
    }
    public static NativeStackIdentity.Snapshot unit(ItemStack item,HolderLookup.Provider registry){
        Objects.requireNonNull(item);if(item.isEmpty())throw new IllegalArgumentException("Missing actual drop stack");
        return NativeStackIdentity.capture(item.copyWithCount(1),Objects.requireNonNull(registry));
    }
}
