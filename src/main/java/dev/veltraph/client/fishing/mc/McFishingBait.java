package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.bait.*;
import dev.veltraph.client.gui.MenuSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Ordinary game menu/item actions with world, menu, revision, exact stack and cursor ownership. */
final class McFishingBait {
    private final Minecraft client;private final ConfigService config;private final Runnable useRod;
    private final BaitRuntime runtime=new BaitRuntime();
    private record Binding(Object player,Object world,Object menu,Map<Integer,ItemStack> stacks) {}
    private final IdentityHashMap<BaitRuntime.Snapshot,Binding> bindings=new IdentityHashMap<>();
    private final ArrayDeque<BaitRuntime.Snapshot> observations=new ArrayDeque<>();
    private BaitProfile profile;private BaitRuntime.Settings options;private String profileJson;
    private Object player,world,predictionMenu;
    private ItemStack predictedCursor=ItemStack.EMPTY,menuItem=ItemStack.EMPTY,rodItem=ItemStack.EMPTY,rodIdentity=ItemStack.EMPTY;
    private int rodSlot=-1;private boolean slotOwned;private long lastCheck;
    McFishingBait(Minecraft client,ConfigService config,Runnable useRod){this.client=client;this.config=config;this.useRod=useRod;}
    static String configurationError(ConfigService cfg){
        if(!cfg.get(FishingConfig.FISHING_AUTO_BAIT)||cfg.get(FishingConfig.FISHING_BAIT_IRONMAN))return "";
        try{BaitProfile.parse(cfg.get(FishingConfig.FISHING_BAIT_PROFILE));settings(cfg);if(cfg.get(FishingConfig.FISHING_BAIT_CHECK_INTERVAL_MS)<1000)throw new IllegalArgumentException("Bait check interval must be at least one second");return "";}
        catch(RuntimeException e){return e.getMessage()==null?"Invalid bait configuration":e.getMessage();}
    }
    private static BaitRuntime.Settings settings(ConfigService cfg){return new BaitRuntime.Settings(cfg.get(FishingConfig.FISHING_BAIT_LOCATION),cfg.get(FishingConfig.FISHING_BAIT_MAX_STACKS),cfg.get(FishingConfig.FISHING_BAIT_MAX_UNIT_PRICE),cfg.get(FishingConfig.FISHING_BAIT_MAX_COST));}
    boolean active(){return runtime.active();}
    String status(){return runtime.state()+": "+runtime.reason();}
    String failure(){return runtime.state()==BaitRuntime.State.FAILED||runtime.state()==BaitRuntime.State.CANCELLED?runtime.reason():"";}
    boolean allowsScreen(String title){return runtime.allowsTitle(title);}
    void message(String message){runtime.message(message);}
    boolean tick(boolean allowStart){
        boolean was=active();long now=System.currentTimeMillis();
        if(client.player==null||client.level==null)return was;
        if(!was){
            if(!allowStart||!config.get(FishingConfig.FISHING_AUTO_BAIT)||config.get(FishingConfig.FISHING_BAIT_IRONMAN)
                    ||client.gui.screen()!=null||client.player.isUnderWater()||now-lastCheck<config.get(FishingConfig.FISHING_BAIT_CHECK_INTERVAL_MS))return false;
            String invalid=configurationError(config);if(!invalid.isEmpty()){runtime.fail(invalid);return true;}
            profileJson=config.get(FishingConfig.FISHING_BAIT_PROFILE);profile=BaitProfile.parse(profileJson);options=settings(config);
            var snapshot=observe();if(snapshot.inventory().stream().filter(s->s.count()==0).count()<4)return false;
            if(options.location()==BaitLocation.INVENTORY&&snapshot.inventory().stream().filter(s->profile.bait(s.itemId(),s.name())).mapToInt(BaitRuntime.Stock::count).sum()>profile.stackSize())return false;
            player=client.player;world=client.level;rodSlot=client.player.getInventory().getSelectedSlot();rodItem=client.player.getMainHandItem().copy();rodIdentity=withoutDamage(rodItem);slotOwned=false;
            lastCheck=now;runtime.start(snapshot,profile,options,now);
        }
        try{
            if(!config.get(FishingConfig.FISHING_AUTO_BAIT)||config.get(FishingConfig.FISHING_BAIT_IRONMAN)||!config.get(FishingConfig.FISHING_BAIT_PROFILE).equals(profileJson)||!settings(config).equals(options)){
                runtime.cancel("Bait settings changed during restocking");cleanup();return true;
            }
        }catch(IllegalArgumentException e){runtime.cancel("Bait settings became invalid");cleanup();return true;}
        if(client.player!=player||client.level!=world||!client.player.isAlive()){
            runtime.cancel("Player/world changed during bait restocking");cleanup();return true;
        }
        if(client.player.getInventory().getSelectedSlot()!=(slotOwned?8:rodSlot)){
            runtime.cancel("Selected slot changed during bait restocking");cleanup();return true;
        }
        if(!ItemStack.matches(rodIdentity,withoutDamage(client.player.getInventory().getItem(rodSlot)))){runtime.cancel("Fishing rod identity changed during bait restocking");cleanup();return true;}
        if(slotOwned&&!ItemStack.matches(menuItem,client.player.getInventory().getItem(8))){runtime.cancel("Owned menu item changed during bait restocking");cleanup();return true;}
        var action=runtime.tick(observe(),now);
        if(action.isPresent()){
            if(!apply(action.get(),now))runtime.fail("Bait request rejected by menu/cursor/stack ownership guard");
        }
        if(runtime.terminal())cleanup();
        return was||runtime.active()||runtime.terminal();
    }
    private BaitRuntime.Snapshot observe(){
        if(!client.isSameThread())throw new IllegalStateException("Bait observation requires client thread");
        var p=client.player;var raw=p.containerMenu;MenuSnapshot menu=null;Map<Integer,BaitRuntime.Stock> storage=new HashMap<>();Map<Integer,ItemStack> stacks=new HashMap<>();
        boolean empty=raw.getCarried().isEmpty();if(empty||raw!=predictionMenu){predictedCursor=ItemStack.EMPTY;predictionMenu=null;}
        boolean prediction=!predictedCursor.isEmpty()&&raw==predictionMenu&&ItemStack.matches(predictedCursor,raw.getCarried());
        if(client.gui.screen() instanceof AbstractContainerScreen<?> screen&&screen.getMenu()==raw){
            List<MenuSnapshot.Slot> slots=new ArrayList<>();for(var slot:raw.slots){
                var stack=slot.getItem();var lore=stack.get(DataComponents.LORE);boolean inventory=slot.container==p.getInventory();
                slots.add(new MenuSnapshot.Slot(slot.index,stack.isEmpty()?"":MenuSnapshot.clean(stack.getHoverName().getString()),lore==null?List.of():lore.lines().stream().map(line->MenuSnapshot.clean(line.getString())).toList(),inventory,id(stack),stack.getCount(),stack.hasFoil()));
                stacks.put(slot.index,stack.copy());
                if(profile!=null&&MenuSnapshot.clean(screen.getTitle().getString()).equals(profile.titles().get("bag"))&&profile.bagSlots().contains(slot.index)&&!inventory)storage.put(slot.index,stock(slot.index,stack));
            }
            menu=new MenuSnapshot(raw.containerId,MenuSnapshot.clean(screen.getTitle().getString()),slots,raw.getStateId());
        }
        List<BaitRuntime.Stock> inventory=new ArrayList<>();for(int i=0;i<36;i++)inventory.add(stock(i,p.getInventory().getItem(i)));
        var snapshot=new BaitRuntime.Snapshot(p,client.level,raw,menu,inventory,storage,empty,prediction,p.fishing!=null);
        bindings.put(snapshot,new Binding(p,client.level,raw,stacks));observations.addLast(snapshot);while(observations.size()>64)bindings.remove(observations.removeFirst());return snapshot;
    }
    private static ItemStack withoutDamage(ItemStack stack){var copy=stack.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    private static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private static BaitRuntime.Stock stock(int index,ItemStack stack){return new BaitRuntime.Stock(index,id(stack),stack.isEmpty()?"":MenuSnapshot.clean(stack.getHoverName().getString()),stack.getCount(),stack.getComponents().stream().map(Object::toString).sorted().toList().toString());}
    private boolean apply(BaitRuntime.Action action,long now){
        var binding=bindings.get(action.expected());if(binding==null||client.player!=binding.player()||client.level!=binding.world()||client.player.containerMenu!=binding.menu()||client.gameMode==null||!client.player.containerMenu.getCarried().isEmpty())return false;
        switch(action.kind()){
            case REEL -> {if(client.gui.screen()!=null||client.player.fishing==null)return false;useRod.run();return true;}
            case USE_MENU -> {
                if(client.gui.screen()!=null||client.player.fishing!=null)return false;
                var stack=client.player.getInventory().getItem(8);if(!id(stack).equals(profile.menuItemId())||!MenuSnapshot.clean(stack.getHoverName().getString()).equals(profile.menuItemName()))return false;
                if(!slotOwned){rodItem=client.player.getInventory().getItem(rodSlot).copy();menuItem=stack.copy();slotOwned=true;client.player.getInventory().setSelectedSlot(8);}
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);return true;
            }
            case COMMAND -> {if(client.gui.screen()!=null||client.player.fishing!=null)return false;client.player.connection.sendCommand(profile.bazaarCommand());return true;}
            case RESTORE -> {return restore();}
            case CLOSE -> {if(client.gui.screen()==null||runtime.ownedMenu()!=client.player.containerMenu)return false;client.player.closeContainer();return true;}
            case CLICK,PURCHASE,MOVE -> {
                if(!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu||action.expected().menu()==null)return false;
                var raw=client.player.containerMenu;var expected=action.expected().menu();
                if(raw.containerId!=expected.id()||raw.getStateId()!=expected.revision()||!MenuSnapshot.clean(screen.getTitle().getString()).equals(expected.title())||action.slot()<0||action.slot()>=raw.slots.size())return false;
                var slot=raw.getSlot(action.slot());var original=binding.stacks().get(action.slot());if(original==null||!ItemStack.matches(original,slot.getItem()))return false;
                boolean transfer=action.kind()==BaitRuntime.Kind.MOVE;
                if(transfer){if(slot.container!=client.player.getInventory()||!profile.bait(id(slot.getItem()),slot.getItem().getHoverName().getString())||slot.getItem().getCount()!=action.quantity())return false;}
                else if(slot.container==client.player.getInventory()||slot.getItem().isEmpty())return false;
                client.gameMode.handleContainerInput(raw.containerId,action.slot(),0,transfer?ContainerInput.QUICK_MOVE:ContainerInput.PICKUP,client.player);
                predictionMenu=raw;predictedCursor=raw.getCarried().copy();return true;
            }
        }
        return false;
    }
    private boolean restore(){
        if(client.player!=player||client.level!=world)return false;
        if(slotOwned){if(client.player.getInventory().getSelectedSlot()!=8||!ItemStack.matches(menuItem,client.player.getInventory().getItem(8))||!ItemStack.matches(rodIdentity,withoutDamage(client.player.getInventory().getItem(rodSlot))))return false;
            client.player.getInventory().setSelectedSlot(rodSlot);slotOwned=false;}
        return true;
    }
    private void cleanup(){
        if(client.player==player&&client.level==world){
            if(client.gui.screen() instanceof AbstractContainerScreen<?> screen&&screen.getMenu()==runtime.ownedMenu()&&client.player.containerMenu==runtime.ownedMenu()&&client.player.containerMenu.getCarried().isEmpty())client.player.closeContainer();
            restore();
        }
    }
    void stop(){
        if(runtime.active())runtime.cancel("Bait restocking cancelled");cleanup();
        runtime.releaseContext();bindings.clear();observations.clear();player=world=predictionMenu=null;
        predictedCursor=menuItem=rodItem=rodIdentity=ItemStack.EMPTY;slotOwned=false;
    }
    boolean rebindIdle(){if(active()||slotOwned||!failure().isEmpty())return false;runtime.releaseContext();bindings.clear();observations.clear();player=world=predictionMenu=null;predictedCursor=menuItem=rodItem=rodIdentity=ItemStack.EMPTY;return true;}
}
