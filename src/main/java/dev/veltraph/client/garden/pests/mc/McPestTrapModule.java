package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Actual OPEN_TRAP/CLICK_RELEASE_TRAP and PICK_UP_BAIT/PLACE_BAIT/LEAVE_TRAP native transaction. */
public final class McPestTrapModule extends GardenActionModule {
    public static final String ID="garden_pest_traps";
    private PestBindings.Trap b;private Entity npc;private Object trapMenu;private int bagBefore,trapped,materialBefore,sourceSlot=-1,pestsBefore;
    private boolean releaseAck,baitAck,released,picked,placed;private ItemStack cursor=ItemStack.EMPTY;private final PestSupplyAcquire acquire=new PestSupplyAcquire(this);
    public McPestTrapModule(Minecraft c,FarmServices s,ConfigService cfg){super(ID,"Collect and bait Garden trap",c,s,cfg);}
    protected boolean optedIn(){return config.get(PestConfig.COLLECT_PEST_TRAPS)||config.get(PestConfig.PEST_REFILL_TRAP_BAIT);}
    protected Optional<String> ready(){b=PestBindings.read(config.get(PestConfig.PEST_TRAP_BINDINGS),PestBindings.Trap.class);
        if(client.gui.screen()!=null)return Optional.of("Start beside the configured trap with menus closed");
        npc=PestItems.uniqueEntity(client,b.npc(),plot(),16);if(npc==null)return Optional.of("Configure one exact loaded trap entity in this plot");
        if(PestItems.counter(client,b.vacuum(),b.bagPrefix()).isEmpty())return Optional.of("Configured vacuum bag counter is unreadable/ambiguous");
        if(config.get(PestConfig.PEST_REFILL_TRAP_BAIT)&&PestItems.count(client,b.bait(),false)<b.baitUnits()
                &&(!config.get(PestConfig.PEST_SUPPLY_REFILL_ENABLED)||config.get(PestConfig.PEST_SUPPLY_MAX_COST)<1||config.get(PestConfig.PEST_SUPPLY_MAX_UNIT_COST)<1))return Optional.of("Missing bait requires explicit supply-refill and positive caps");
        return Optional.empty();}
    protected void begin(){bagBefore=PestItems.counter(client,b.vacuum(),b.bagPrefix()).orElseThrow();materialBefore=PestItems.count(client,b.bait(),true);
        releaseAck=baitAck=released=picked=placed=false;cursor=ItemStack.EMPTY;trapMenu=null;phase=Phase.APPROACH;reason="Opening exact trap entity";}
    protected boolean screenAllowed(String title){return title.equals(b.menuTitle())||(phase==Phase.BUY&&acquire.allows(title));}
    private boolean trap(MenuSnapshot menu){return menu!=null&&menu.title().equals(b.menuTitle())&&menu.slots().stream().anyMatch(s->!s.playerInventory()&&s.lore().contains(b.marker()));}
    protected void advance(){
        if(client.level.getEntity(npc.getId())!=npc||!PestItems.entityMatches(npc,b.npc())){finish(Phase.CANCELLED,"Trap entity disappeared or changed");return;}
        if(phase==Phase.BUY){acquire.tick();if(acquire.state()==PestSupplyAcquire.State.FAILED){finish(Phase.FAILED,acquire.status());return;}
            if(acquire.state()==PestSupplyAcquire.State.COMPLETE){materialBefore=PestItems.count(client,b.bait(),true);phase=Phase.APPROACH;}return;}
        if(phase==Phase.APPROACH){if(client.gui.screen()!=null){finish(Phase.CANCELLED,"Unexpected screen before trap interaction");return;}
            if(interact(npc)){phase=Phase.OPEN_MENU;reason="Waiting for configured trap menu";}return;}
        var menu=menu();
        if(!trap(menu)){if(phase==Phase.OPEN_MENU&&menu==null)return;finish(Phase.CANCELLED,"Trap menu closed, replaced or identity marker changed");return;}
        if(trapMenu==null){trapMenu=owner.containerMenu;own(menu);}else if(trapMenu!=owner.containerMenu){finish(Phase.CANCELLED,"Trap container object replaced");return;}
        var release=button(menu,b.releaseButton());
        if(phase==Phase.OPEN_MENU){
            if(release==null){finish(Phase.FAILED,"Release button missing/ambiguous");return;}trapped=PestBindings.count(release.lore(),b.trappedPrefix()).orElse(-1);
            if(trapped<0||trapped>64){finish(Phase.FAILED,"Unreadable/excessive trapped pest count");return;}
            if(config.get(PestConfig.COLLECT_PEST_TRAPS)&&!released&&trapped>0){
                phase=Phase.RELEASE;releaseAck=false;pestsBefore=pestCount();if(!menus.click(menu,release.id()))finish(Phase.FAILED,"Release menu guard refused request");return;}
            released=true;beginBait(menu);return;
        }
        if(phase==Phase.RELEASE){
            int count=release==null?-1:PestBindings.count(release.lore(),b.trappedPrefix()).orElse(-1);
            int bag=PestItems.counter(client,b.vacuum(),b.bagPrefix()).orElse(-1);
            if(releaseAck&&count==0&&bag==bagBefore&&pestCount()==pestsBefore+trapped){released=true;beginBait(menu);}return;
        }
        if(phase==Phase.PICKUP_BAIT){
            if(!PestItems.matches(owner.containerMenu.getCarried(),b.bait()))return;
            cursor=owner.containerMenu.getCarried().copy();picked=true;
            var destination=owner.containerMenu.getSlot(b.baitSlot());
            if(destination.container==owner.getInventory()||!destination.getItem().isEmpty()||PestItems.count(client,b.bait(),true)!=materialBefore){finish(Phase.FAILED,"Bait destination or carried resource changed");return;}
            phase=Phase.PLACE_BAIT;placed=true;client.gameMode.handleContainerInput(menu.id(),b.baitSlot(),cursor.getCount()==b.baitUnits()?0:1,ContainerInput.PICKUP,owner);return;
        }
        if(phase==Phase.PLACE_BAIT){
            var stored=owner.containerMenu.getSlot(b.baitSlot()).getItem();
            if(!stored.isEmpty()&&!PestItems.matches(stored,b.bait())){finish(Phase.FAILED,"Wrong bait identity in trap destination");return;}
            int placedCount=stored.isEmpty()?0:stored.getCount();
            if(placedCount>b.baitUnits()||PestItems.count(client,b.bait(),true)!=materialBefore-placedCount){finish(Phase.FAILED,"Unexpected bait resource/destination delta");return;}
            if(placedCount<b.baitUnits()&&PestItems.matches(owner.containerMenu.getCarried(),b.bait())&&placedCount>0){
                client.gameMode.handleContainerInput(menu.id(),b.baitSlot(),1,ContainerInput.PICKUP,owner);return;
            }
            if(baitAck&&PestItems.matches(stored,b.bait())&&stored.getCount()==b.baitUnits()
                    &&PestItems.count(client,b.bait(),true)==materialBefore-b.baitUnits())finish(Phase.COMPLETE,"Trap released exact pest models and exact bait placement observed");
        }
    }
    private int pestCount(){int count=0;for(var e:client.level.entitiesForRendering())if(PestItems.entityMatches(e,b.releasedPest())&&GardenPlotBounds.contains(plot(),e.getX(),e.getZ()))count++;return count;}
    private void beginBait(MenuSnapshot menu){
        if(!config.get(PestConfig.PEST_REFILL_TRAP_BAIT)){finish(Phase.COMPLETE,"Trap count cleared and exact pest model increase verified");return;}
        if(PestItems.count(client,b.bait(),false)<b.baitUnits()){
            closeOwned();trapMenu=null;var supply=PestBindings.read(config.get(PestConfig.PEST_SUPPLY_BINDINGS),PestBindings.Supply.class);
            if(!supply.item().equals(b.bait())||supply.quantity()!=b.baitUnits()-PestItems.count(client,b.bait(),false)){finish(Phase.FAILED,"Supply binding must request exact missing trap bait");return;}
            phase=Phase.BUY;if(!acquire.begin(supply,config.get(PestConfig.SPRAYONATOR_TRY_SACKS_FIRST),config.get(PestConfig.PEST_SUPPLY_MAX_COST),config.get(PestConfig.PEST_SUPPLY_MAX_UNIT_COST)))finish(Phase.FAILED,acquire.status());return;
        }
        // Source picks a stack >= amount; right-click one-per-step when oversized, preserving the remainder.
        var candidates=owner.containerMenu.slots.stream().filter(s->s.container==owner.getInventory()&&PestItems.matches(s.getItem(),b.bait())&&s.getItem().getCount()>=b.baitUnits()).toList();
        if(candidates.isEmpty()||!owner.containerMenu.getCarried().isEmpty()||b.baitSlot()>=owner.containerMenu.slots.size()
                ||owner.containerMenu.getSlot(b.baitSlot()).container==owner.getInventory()||!owner.containerMenu.getSlot(b.baitSlot()).getItem().isEmpty()){
            finish(Phase.FAILED,"Provide an adequate bait stack and empty configured trap destination");return;}
        materialBefore=PestItems.count(client,b.bait(),true);sourceSlot=candidates.getFirst().index;cursor=candidates.getFirst().getItem().copy();phase=Phase.PICKUP_BAIT;
        client.gameMode.handleContainerInput(menu.id(),sourceSlot,0,ContainerInput.PICKUP,owner);
    }
    @Override public void onServerMessage(String line){super.onServerMessage(line);if(!isEnabled()||line==null)return;String text=dev.veltraph.client.game.LocationText.stripColorCodes(line);
        if(phase==Phase.RELEASE&&text.equals(b.releaseAck()))releaseAck=true;if(phase==Phase.PLACE_BAIT&&text.equals(b.baitAck()))baitAck=true;if(phase==Phase.BUY)acquire.message(text);}
    @Override public void onDisable(){
        // Preserve a picked cursor on manual stop: return only our exact stack to its still-empty original slot.
        if(client.player==owner&&client.level==level&&owner!=null&&owner.containerMenu==trapMenu&&sourceSlot>=0
                &&ItemStack.isSameItemSameComponents(cursor,owner.containerMenu.getCarried())&&owner.containerMenu.getCarried().getCount()<=cursor.getCount()&&owner.containerMenu.getSlot(sourceSlot).getItem().isEmpty()&&client.gameMode!=null)
            client.gameMode.handleContainerInput(owner.containerMenu.containerId,sourceSlot,0,ContainerInput.PICKUP,owner);
        super.onDisable();npc=null;trapMenu=null;sourceSlot=-1;cursor=ItemStack.EMPTY;
    }
    /** Positive only after native acknowledgment, zero menu count, unchanged bag and model increase. */
    public int releasedPests(){return releaseAck&&released&&trapped>0?trapped:0;}
    /** Exact acknowledged destination amount; completion additionally verifies resource delta. */
    public int placedBait(){return phase==Phase.COMPLETE&&placed&&baitAck?b.baitUnits():0;}
}
