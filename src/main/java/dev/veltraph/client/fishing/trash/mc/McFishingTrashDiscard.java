package dev.veltraph.client.fishing.trash.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.fishing.trash.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import java.util.*;

/** Only the recovered discard component: the caller must first own the source cliff route and inventory screen. */
public final class McFishingTrashDiscard {
    private final Minecraft client;
    private final ConfigService config;
    private final FishingTrashDiscard runtime=new FishingTrashDiscard();
    private InventoryScreen ownedScreen;
    private String contract;
    private int selected;
    public McFishingTrashDiscard(Minecraft client,ConfigService config){this.client=client;this.config=config;}
    public static String configurationError(ConfigService config) {
        if(!config.get(FishingTrashConfig.FISHING_THROW_TRASH))return "";
        try{FishingTrashProfile.parse(config.get(FishingTrashConfig.FISHING_TRASH_PROFILE));return "";}
        catch(RuntimeException e){return e.getMessage();}
    }
    private FishingTrashDiscard.Observation observe() {
        if(client.player==null||client.level==null||client.getConnection()==null)return null;
        var menu=client.player.containerMenu;var rows=new TreeMap<Integer,FishingTrashProfile.Stack>();var eligible=new TreeSet<Integer>();
        for(var slot:menu.slots) {
            rows.put(slot.index,FishingTrashInventoryReceipts.describe(client,slot.getItem()));
            // Source pre-interruption counts non-equipment stacks. Preserve equipment/crafting/offhand custody.
            if(slot.container==client.player.getInventory()&&slot.getContainerSlot()>=0&&slot.getContainerSlot()<36)eligible.add(slot.index);
        }
        return new FishingTrashDiscard.Observation(new FishingTrashDiscard.Context(client.player,client.level,client.getConnection(),menu,client.gui.screen()),
                menu.containerId,menu.getStateId(),menu.getCarried().isEmpty(),rows,eligible);
    }
    public boolean start(InventoryScreen screen) {
        if(!client.isSameThread()||!config.get(FishingTrashConfig.FISHING_THROW_TRASH)||client.player==null||client.level==null
                ||client.gui.screen()!=screen||screen==null||screen.getMenu()!=client.player.inventoryMenu
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.isAlive()
                ||!client.player.getMainHandItem().is(Items.FISHING_ROD))return false;
        try {
            var profile=FishingTrashProfile.parse(config.get(FishingTrashConfig.FISHING_TRASH_PROFILE));
            if(!runtime.start(observe(),profile,FishingTrashInventoryReceipts.sequence(),System.currentTimeMillis()))return false;
            ownedScreen=screen;selected=client.player.getInventory().getSelectedSlot();contract=config.get(FishingTrashConfig.FISHING_TRASH_PROFILE);return true;
        }catch(RuntimeException invalid){runtime.fail("Fishing trash: "+invalid.getMessage());return false;}
    }
    public void tick() {
        if(!runtime.active())return;
        if(!client.isSameThread()||client.player==null||!client.player.isAlive()||client.gameMode==null
                ||client.gui.screen()!=ownedScreen||client.player.containerMenu!=client.player.inventoryMenu
                ||client.player.getInventory().getSelectedSlot()!=selected||!config.get(FishingTrashConfig.FISHING_THROW_TRASH)
                ||!Objects.equals(contract,config.get(FishingTrashConfig.FISHING_TRASH_PROFILE))) {
            runtime.fail("Fishing trash screen, player, selection or contract changed");return;
        }
        try {
            var observed=observe();var receipt=FishingTrashInventoryReceipts.latest(runtime.pendingSlot(),runtime.beforeReceiptSequence());
            var action=runtime.tick(observed,receipt,System.currentTimeMillis());
            if(action.isEmpty())return;
            // Revalidate the exact entire current inventory and current revision immediately before one native THROW.
            var current=observe();
            if(current==null||!Objects.equals(current,observed)||!current.cursorEmpty()) {
                runtime.fail("Fishing trash native inventory changed before request");return;
            }
            client.gameMode.handleContainerInput(current.menuId(),action.get().slot(),1,ContainerInput.THROW,client.player);
        }catch(RuntimeException failure){runtime.fail("Fishing trash native request refused: "+failure.getMessage());}
    }
    public boolean active(){return runtime.active();}
    public FishingTrashDiscard.State state(){return runtime.state();}
    public String failure(){return runtime.failure();}
    /** Caller decides when to close its owned empty-cursor menu and physically return; this component does neither. */
    public void stop(){runtime.reset();ownedScreen=null;contract=null;}
}
