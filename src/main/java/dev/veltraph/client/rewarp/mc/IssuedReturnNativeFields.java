package dev.veltraph.client.rewarp.mc;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.EnumMap;
import java.util.List;

/** Exact installed field readers; no caller-populated snapshot or inventory setter. */
public final class IssuedReturnNativeFields {
    private IssuedReturnNativeFields() {}
    public interface Player { Inventory veltraph$returnInventory(); }
    public interface Living { EntityEquipment veltraph$returnEquipment(); }
    public interface Items {
        NonNullList<ItemStack> veltraph$returnItems();
        EntityEquipment veltraph$returnInventoryEquipment();
        int veltraph$returnSelected();
    }
    public interface ListFields {List<?> veltraph$returnList();Object veltraph$returnDefault();}
    public interface Equipment {EnumMap<EquipmentSlot,ItemStack> veltraph$returnEquipmentItems();}
    public interface Menu {ItemStack veltraph$returnCursor();}
    public interface NamedSet {List<?> veltraph$returnContents();net.minecraft.tags.TagKey<?> veltraph$returnTag();}
    public interface DirectSet {List<?> veltraph$returnContents();}
}
