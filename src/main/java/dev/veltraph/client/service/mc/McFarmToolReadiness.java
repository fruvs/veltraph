package dev.veltraph.client.service.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.input.ScriptingInputLock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Actual start-item identity only. This private handout grants no gameplay or cleanup authority. */
public final class McFarmToolReadiness {
    private final Minecraft client;
    private final McInventoryService service;
    private final ModuleManager root;
    private final FarmModule farm;
    private final int slot;
    private final ItemStack intended;

    private McFarmToolReadiness(Minecraft client, McInventoryService service, ModuleManager root,
                                FarmModule farm, int slot, ItemStack intended) {
        this.client=client;this.service=service;this.root=root;this.farm=farm;this.slot=slot;this.intended=identity(intended);
    }
    /** Package-private: only the actual native inventory producer supplies its stored client. */
    static McFarmToolReadiness capture(Minecraft client, McInventoryService service, FarmModule farm, int slot, String expectedName) {
        if(client==null||!client.isSameThread()||slot<0||slot>8||farm==null||expectedName==null||expectedName.isEmpty())return null;
        var root=VeltraphBootstrap.moduleManager();
        if(root==null||root.get(farm.id()).orElse(null)!=farm||!McInputService.canonicalFarmInventoryCurrent(service,client)
                ||!scene(client))return null;
        var actual=client.player.getInventory().getItem(slot);
        if(actual.isEmpty()||!actual.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).equals(expectedName))return null;
        var result=new McFarmToolReadiness(client,service,root,farm,slot,actual);
        return result.itemCurrent(farm,service,client,slot)?result:null;
    }
    /** Copy immutable native item/components; count is not identity and DAMAGE is the sole wear exception. */
    private static ItemStack identity(ItemStack stack) {
        var copy=stack.copyWithCount(1);copy.remove(DataComponents.DAMAGE);return copy;
    }
    private static boolean scene(Minecraft client) {
        var p=client.player;
        return client==Minecraft.getInstance()&&client.isSameThread()&&p!=null&&client.level!=null&&client.getConnection()!=null
                &&p.level()==client.level&&p.isAlive()&&!p.isPassenger()&&!p.isScoping()&&!client.isPaused()
                &&client.gui.screen()==null&&client.getCameraEntity()==p&&!client.level.tickRateManager().isEntityFrozen(p)
                &&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty();
    }
    /** Fresh callback-free native item fact. It neither selects a slot nor adopts a replacement handout. */
    private boolean itemCurrent(FarmModule expectedFarm, McInventoryService expectedService, Minecraft actualClient, int expectedSlot) {
        if(expectedFarm!=farm||expectedService!=service||actualClient!=client||expectedSlot!=slot
                ||VeltraphBootstrap.moduleManager()!=root||root.get(farm.id()).orElse(null)!=farm
                ||!McInputService.canonicalFarmInventoryCurrent(service,client)||!scene(client))return false;
        var actual=client.player.getInventory().getItem(slot);
        return !actual.isEmpty()&&(!actual.isDamageableItem()
                ||actual.getDamageValue()>=0&&actual.getDamageValue()<actual.getMaxDamage())
                &&ItemStack.isSameItemSameComponents(intended,identity(actual));
    }
    /** Recovery adds this negative identity check before its existing independently owned restore path. */
    public boolean itemPresent(FarmModule expectedFarm,McInventoryService expectedService,int expectedSlot) {
        return itemCurrent(expectedFarm,expectedService,client,expectedSlot);
    }
    public boolean selectedCurrent(FarmModule expectedFarm, McInventoryService expectedService, Minecraft actualClient, int expectedSlot) {
        return itemCurrent(expectedFarm,expectedService,actualClient,expectedSlot)&&client.player.getInventory().getSelectedSlot()==slot;
    }
    /** Recovery without a closed issued source is read-only; no raw suspended flag or name can select. */
    public boolean selectForRecovery(FarmModule expectedFarm,McInventoryService expectedService,int expectedSlot) {
        if(!itemCurrent(expectedFarm,expectedService,client,expectedSlot))return false;
        var inventory=client.player.getInventory();int before=inventory.getSelectedSlot();
        if(before==slot)return selectedCurrent(expectedFarm,expectedService,client,expectedSlot);
        var source=McFarmToolRecoveryContext.claim(expectedFarm,expectedService);
        var nativeLeaf=McScopedInputReadFence.capture(client);
        if(source==null||nativeLeaf==null)return false;
        var player=client.player;var world=client.level;var connection=client.getConnection();
        java.util.function.BooleanSupplier original=()->client.player==player&&client.level==world&&client.getConnection()==connection
                &&player.getInventory()==inventory&&nativeLeaf.current()&&itemCurrent(expectedFarm,expectedService,client,expectedSlot);
        java.util.function.BooleanSupplier sourceCurrent=()->McFarmToolRecoveryContext.current(source,expectedFarm,expectedService)&&original.getAsBoolean();
        if(!sourceCurrent.getAsBoolean())return false;
        var receipt=service.selectSlotScoped(before,slot,
                ()->sourceCurrent.getAsBoolean()&&inventory.getSelectedSlot()==before,
                ()->sourceCurrent.getAsBoolean()&&inventory.getSelectedSlot()==slot);
        return receipt.confirmed()&&receipt.packetSubmitted()&&sourceCurrent.getAsBoolean()
                &&selectedCurrent(expectedFarm,expectedService,client,expectedSlot);
    }

    /** Start may use a genuine original None; a normal tick must have this exact farm's actual control. */
    public boolean selectIfCurrent(FarmModule expectedFarm, McInventoryService expectedService, int expectedSlot, boolean start) {
        if(!itemCurrent(expectedFarm,expectedService,client,expectedSlot)||start&&farm.isEnabled()||!start&&(!farm.isEnabled()||farm.isSuspended()))return false;
        var lifecycle=root.lifecycleSnapshot();var control=ScriptingInputLock.controlSnapshot();
        var nativeLeaf=McScopedInputReadFence.capture(client);
        if(lifecycle==null||!lifecycle.known()||control==null||nativeLeaf==null)return false;
        var player=client.player;var world=client.level;var connection=client.getConnection();var inventory=player.getInventory();
        int before=inventory.getSelectedSlot();
        java.util.function.BooleanSupplier original=()->client.player==player&&client.level==world&&client.getConnection()==connection
                &&player.getInventory()==inventory&&root.lifecycleCurrent(lifecycle)&&control.current()&&nativeLeaf.current()
                &&itemCurrent(expectedFarm,expectedService,client,expectedSlot)
                &&(start?!farm.isEnabled():farm.isEnabled()&&!farm.isSuspended());
        // A prepared actual restart already holding the exact item needs metadata readiness only.
        // This no-op neither selects nor releases the retained lock; G7 remains sole reentry authority.
        if(start&&before==slot)return selectedCurrent(expectedFarm,expectedService,client,expectedSlot)&&original.getAsBoolean();
        Object input=root.inputOwner().orElse(null),camera=McOwnedRotation.authority(client);
        if(start?(input!=null||camera!=null||control.active()):(input!=farm||camera!=farm||!control.active()||!farm.id().equals(control.ownerModuleId())))return false;
        // These genuine providers may only refuse. The final original/native item leaf follows both.
        java.util.function.BooleanSupplier custody=()->original.getAsBoolean()&&root.inputOwner().orElse(null)==input
                &&McOwnedRotation.authority(client)==camera&&original.getAsBoolean();
        if(!custody.getAsBoolean())return false;
        if(before==slot)return selectedCurrent(expectedFarm,expectedService,client,expectedSlot)&&original.getAsBoolean();
        var receipt=service.selectSlotScoped(before,slot,
                ()->custody.getAsBoolean()&&inventory.getSelectedSlot()==before,
                ()->custody.getAsBoolean()&&inventory.getSelectedSlot()==slot);
        return receipt.confirmed()&&receipt.packetSubmitted()&&selectedCurrent(expectedFarm,expectedService,client,expectedSlot)
                &&original.getAsBoolean();
    }
}
