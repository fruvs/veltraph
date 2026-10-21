package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EntityEquipment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Inventory.class)
public interface RetainedNamedLoadoutEquipmentAccessor extends McNamedLoadoutPort.EquipmentCarrier {
    @Accessor("equipment") EntityEquipment namedLoadoutEquipment();
}
