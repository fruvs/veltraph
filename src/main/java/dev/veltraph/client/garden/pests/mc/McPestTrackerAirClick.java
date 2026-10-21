package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.mixin.GardenTrackerAttackInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import java.util.function.BooleanSupplier;

/** Immediate ordinary vanilla LEFT_CLICK with an exact native tool/scope and concrete lease gate. */
public final class McPestTrackerAirClick {
    public record Binding(Object player,Object world,Object connection,int slot,int plot,PestBindings.Item vacuum,ItemStack stack) {
        public Binding {stack=stack.copy();}}
    private McPestTrackerAirClick(){}
    public static boolean click(Minecraft client,Binding binding,BooleanSupplier concreteLeaseValid){
        if(!client.isSameThread())throw new IllegalStateException("Tracker air click requires client thread");
        if(binding==null||!concreteLeaseValid.getAsBoolean()||client.player!=binding.player()||client.level!=binding.world()||client.getConnection()!=binding.connection()
            ||client.player==null||client.level==null||client.gameMode==null||!client.player.isAlive()||client.player.isSpectator()||client.gui.screen()!=null
            ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()||binding.slot()<0||binding.slot()>8
            ||client.player.getInventory().getSelectedSlot()!=binding.slot()||!GardenPlotBounds.contains(binding.plot(),client.player.getX(),client.player.getZ())
            ||!ItemStack.matches(binding.stack(),client.player.getInventory().getItem(binding.slot()))||!PestItems.matches(client.player.getInventory().getItem(binding.slot()),binding.vacuum())
            ||client.hitResult==null||client.hitResult.getType()!=HitResult.Type.MISS||((GardenTrackerAttackInvoker)client).veltraph$gardenTrackerMissTime()>0)return false;
        ((GardenTrackerAttackInvoker)client).veltraph$startGardenTrackerAttack();return true;
    }
}
