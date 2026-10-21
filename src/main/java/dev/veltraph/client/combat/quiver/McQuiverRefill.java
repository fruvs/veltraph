package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.config.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.FarmServices;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;

/** Client-thread lease and guarded ordinary opener/click actions for one refill request. */
public final class McQuiverRefill {
    private static final IdentityHashMap<Minecraft,McQuiverRefill> LEASES=new IdentityHashMap<>();
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final McMenuAccess menus;
    private QuiverRefillRuntime runtime=new QuiverRefillRuntime();private QuiverRefillRuntime.Settings settings;
    private Object player,level,connection,menuIdentity;private ItemStack predicted=ItemStack.EMPTY;
    private int previousSlot,leasedSlot;private long tick,menuDeadline;private boolean active,waitingMenu,requireEmpty;
    private String status="Idle";
    private McQuiverNpcIngress npcIngress;
    private QuiverIngressConfig.Opener openerMode;
    private QuiverNpcIngressRuntime.Contract npcContract;
    private List<QuiverRefillRuntime.Item> openingInventory=List.of();
    private BigDecimal openingCurrency;
    private QuiverReturnRuntime.Pose callerPose;
    private QuiverReturnRuntime returning;
    private dev.veltraph.client.navigation.mc.McSharedMovementNavigator returnNavigator;
    private List<QuiverRefillRuntime.Item> returnInventory=List.of();
    private BigDecimal returnCurrency;
    private boolean returnFailed;
    public McQuiverRefill(Minecraft client,FarmServices services,ConfigService config){this.client=client;this.services=services;this.config=config;menus=new McMenuAccess(client);}
    private static String fingerprint(ItemStack stack){return stack.getComponents().stream().map(Object::toString).sorted().toList().toString();}
    private QuiverRefillRuntime.Item ammo(){
        String id=config.get(QuiverConfig.QUIVER_AMMO_ID),name=config.get(QuiverConfig.QUIVER_AMMO_NAME);var key=Identifier.tryParse(id);
        if(key==null||!BuiltInRegistries.ITEM.containsKey(key)||name.isBlank())throw new IllegalArgumentException("Configure exact ammo registry ID and name");
        var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(key));if(stack.isEmpty())throw new IllegalArgumentException("Ammo must not be air");
        if(!stack.getHoverName().getString().equals(name))stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(name));
        return new QuiverRefillRuntime.Item(id,name,fingerprint(stack),0);
    }
    private QuiverRefillRuntime.Settings settings(){return new QuiverRefillRuntime.Settings(config.get(QuiverConfig.QUIVER_MENU_TITLE),config.get(QuiverConfig.QUIVER_FILL_BUTTON),ammo(),
            config.get(QuiverConfig.QUIVER_QUANTITY),config.get(QuiverConfig.QUIVER_MAX_COST),config.get(QuiverConfig.QUIVER_QUOTE_TEMPLATE),config.get(QuiverConfig.QUIVER_ACK_TEMPLATE),
            config.get(QuiverConfig.QUIVER_DELAY_TICKS),config.get(QuiverConfig.QUIVER_TIMEOUT_TICKS),config.get(QuiverConfig.QUIVER_SUPPLY),config.get(QuiverConfig.QUIVER_SUPPLY_BUTTON),config.get(QuiverConfig.QUIVER_SUPPLY_TEMPLATE),!config.get(QuiverConfig.QUIVER_CURRENCY_LABEL).isBlank());}
    public Optional<String> configError(){
        if(!config.get(QuiverConfig.QUIVER_ENABLED))return Optional.of("Quiver refill is disabled");
        try{var options=settings();
            if(config.get(QuiverIngressConfig.QUIVER_OPENER_MODE)==QuiverIngressConfig.Opener.NAMED_NPC)
                McQuiverNpcIngress.contract(config,options.title(),options.timeout());
        }
        catch(IllegalArgumentException e){return Optional.of(e.getMessage());}return Optional.empty();
    }
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return active&&waitingMenu&&npcIngress!=null&&npcIngress.consumeExpectedTeleport(position,now);}
    public boolean active(){return active;}public QuiverRefillRuntime.State state(){return returnFailed?QuiverRefillRuntime.State.CANCELLED:runtime.state();}
    public String status(){return status;}
    public boolean allowsScreen(String title){return active&&settings!=null&&MenuSnapshot.clean(title).equals(settings.title());}
    private List<QuiverRefillRuntime.Item> inventory(){
        List<QuiverRefillRuntime.Item> list=new ArrayList<>();for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(!stack.isEmpty())list.add(new QuiverRefillRuntime.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getHoverName().getString(),fingerprint(stack),stack.getCount()));}return list;
    }
    public OptionalInt observedVisibleAmmo(){if(client.player==null)return OptionalInt.empty();try{var ammo=ammo();return OptionalInt.of(inventory().stream().filter(i->i.id().equals(ammo.id())&&i.name().equals(ammo.name())&&i.components().equals(ammo.components())).mapToInt(QuiverRefillRuntime.Item::count).sum());}catch(IllegalArgumentException e){return OptionalInt.empty();}}
    public OptionalInt observedOwnedQuiver(){return active&&!waitingMenu&&client.player!=null&&client.player.containerMenu==menuIdentity?runtime.observedSupply(menus.read()):OptionalInt.empty();}
    private BigDecimal currency(){
        if(client.level==null||config.get(QuiverConfig.QUIVER_CURRENCY_LABEL).isBlank())return null;var board=client.level.getScoreboard();var objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);if(objective==null)return null;
        var pattern=Pattern.compile("^"+Pattern.quote(MenuSnapshot.clean(config.get(QuiverConfig.QUIVER_CURRENCY_LABEL)))+": ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)$");
        BigDecimal amount=null;for(var score:board.listPlayerScores(objective)){
            String name=score.owner();var team=board.getPlayersTeam(name);if(team!=null)name=team.getPlayerPrefix().getString()+name+team.getPlayerSuffix().getString();
            var matcher=pattern.matcher(MenuSnapshot.clean(name));if(matcher.matches()){if(amount!=null)return null;amount=new BigDecimal(matcher.group(1).replace(",",""));}
        }return amount;
    }
    public boolean request(){
        return request(false);
    }
    public boolean request(boolean onlyIfEmpty){
        if(active)return true;runtime=new QuiverRefillRuntime();var error=configError();if(error.isPresent()){runtime.cancel(error.get());status=error.get();return false;}
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gameMode==null){runtime.cancel("Living player/world required");status=runtime.status();return false;}
        if(LEASES.containsKey(client)){runtime.cancel("Another controller owns the refill request");status=runtime.status();return false;}
        returning=null;returnNavigator=null;returnFailed=false;callerPose=new QuiverReturnRuntime.Pose(services.player().position(),services.rotation().yaw(),services.rotation().pitch());
        settings=settings();requireEmpty=onlyIfEmpty;player=client.player;level=client.level;connection=client.getConnection();npcIngress=null;openerMode=config.get(QuiverIngressConfig.QUIVER_OPENER_MODE);npcContract=null;previousSlot=leasedSlot=services.inventory().selectedSlot();tick=0;menuIdentity=null;predicted=ItemStack.EMPTY;
        var existing=menus.read();
        if(!client.player.containerMenu.getCarried().isEmpty()||client.gui.screen()!=null&&(existing==null||!existing.title().equals(settings.title()))){runtime.cancel("Unknown menu or occupied cursor");status=runtime.status();return false;}
        LEASES.put(client,this);active=true;waitingMenu=true;menuDeadline=settings.timeout();release();
        if(existing!=null){begin(existing);return active;}
        openingInventory=inventory();openingCurrency=currency();
        if(openerMode==QuiverIngressConfig.Opener.NAMED_NPC){
            npcContract=McQuiverNpcIngress.contract(config,settings.title(),settings.timeout());
            npcIngress=new McQuiverNpcIngress(client,services,config,npcContract,tick);
            status=npcIngress.status();if(npcIngress.terminal()){cancel(status);return false;}return true;
        }
        int slot=config.get(QuiverConfig.QUIVER_OPENER_SLOT)-1;
        if(slot<0||slot>=9){cancel("Configure a quiver opener hotbar slot");return false;}
        var item=client.player.getInventory().getItem(slot);String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        if(item.isEmpty()||config.get(QuiverConfig.QUIVER_OPENER_ID).isBlank()||config.get(QuiverConfig.QUIVER_OPENER_NAME).isBlank()
                ||!id.equals(config.get(QuiverConfig.QUIVER_OPENER_ID))||!item.getHoverName().getString().equals(config.get(QuiverConfig.QUIVER_OPENER_NAME))){cancel("Configured quiver opener item missing or mismatched");return false;}
        services.inventory().selectSlot(slot);leasedSlot=slot;client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);status="One opener use sent; waiting for configured refill menu";return true;
    }
    private void begin(MenuSnapshot menu){
        if(!menu.title().equals(settings.title())){cancel("Unexpected refill menu");return;}
        if(npcIngress!=null)npcIngress.stop();waitingMenu=false;menuIdentity=client.player.containerMenu;runtime.start(menu,inventory(),currency(),settings,tick,client.player.containerMenu.getCarried().isEmpty());status=runtime.status();
        if(!runtime.terminal()&&requireEmpty&&(settings.supply()==QuiverSupply.CONTAINER?runtime.observedSupply(menu):observedVisibleAmmo()).orElse(-1)!=0){cancel("Ammo is not observably empty; refusing automatic purchase");return;}
        if(runtime.terminal())finish();
    }
    public void tick(){
        if(!active)return;tick++;release();
        if(client.player!=player||client.level!=level||client.getConnection()!=connection||client.player==null||!client.player.isAlive()){cancel("Refill player/world ownership changed");return;}
        if(services.inventory().selectedSlot()!=leasedSlot){cancel("Refill hotbar ownership changed");return;}
        if(returning!=null){tickReturn();return;}
        var menu=menus.read();
        if(waitingMenu){
            boolean unchanged=openingInventory.equals(inventory())&&Objects.equals(openingCurrency,currency());
            if(!unchanged||!client.player.containerMenu.getCarried().isEmpty()){cancel("Refill opener inventory/currency/cursor ownership changed");return;}
            if(config.get(QuiverIngressConfig.QUIVER_OPENER_MODE)!=openerMode){cancel("Quiver opener mode changed");return;}
            if(npcIngress!=null){
                try{if(!npcContract.equals(McQuiverNpcIngress.contract(config,settings.title(),settings.timeout()))){cancel("Quiver NPC contract changed");return;}}
                catch(IllegalArgumentException invalid){cancel("Quiver NPC contract invalidated");return;}
                npcIngress.tick(menu,unchanged,tick);status=npcIngress.status();
                if(npcIngress.open()){begin(menu);return;}
                if(npcIngress.terminal())cancel(status);return;
            }
            if(menu!=null){begin(menu);return;}if(client.gui.screen()!=null||tick>menuDeadline){cancel("Refill opener timeout or unknown screen");}return;
        }
        if(menu!=null&&client.player.containerMenu!=menuIdentity){cancel("Refill container object changed");return;}
        if(menu==null&&client.gui.screen()!=null){cancel("Unexpected non-container screen");return;}
        if(menu==null&&client.player.containerMenu!=menuIdentity&&menuIdentity instanceof net.minecraft.world.inventory.AbstractContainerMenu old){
            // Full-content updates can precede an automatic close in the same network batch.
            // Inspect the retained owned object, never click it or invent a revision/count.
            List<MenuSnapshot.Slot> slots=new ArrayList<>();for(var slot:old.slots){var item=slot.getItem();var lore=item.get(DataComponents.LORE);
                slots.add(new MenuSnapshot.Slot(slot.index,item.isEmpty()?"":MenuSnapshot.clean(item.getHoverName().getString()),lore==null?List.of():lore.lines().stream().map(l->MenuSnapshot.clean(l.getString())).toList(),slot.container==client.player.getInventory(),BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getCount(),item.hasFoil()));}
            runtime.observeClosedSupply(new MenuSnapshot(old.containerId,settings.title(),slots,old.getStateId()));
        }
        boolean ownPrediction=!predicted.isEmpty()&&client.player.containerMenu==menuIdentity&&ItemStack.matches(predicted,client.player.containerMenu.getCarried());
        var action=runtime.tick(menu,inventory(),currency(),tick,client.player.containerMenu.getCarried().isEmpty(),ownPrediction);
        if(action.isPresent()){
            var request=action.get();var fresh=menus.read();boolean accepted=client.player.containerMenu.getCarried().isEmpty()&&runtime.validates(request,fresh)
                    &&(request.close()?menus.close(fresh.id()):menus.click(fresh,request.slot()));
            runtime.applied(request,accepted);if(accepted&&!request.close())predicted=client.player.containerMenu.getCarried().copy();
        }
        status=runtime.status();if(runtime.terminal())finish();
    }
    public void onMessage(String text){if(active)runtime.onMessage(text);}
    public void cancel(String why){
        if(!active&&runtime.terminal())return;
        if(returning!=null){returnFailed=true;returning.fail(why);status=why;}else{runtime.cancel(why);status=runtime.status();}
        if(active)finish();
    }
    private void beginReturn(){
        npcIngress.stop();returning=new QuiverReturnRuntime(callerPose,tick,settings.timeout());
        returnNavigator=new dev.veltraph.client.navigation.mc.McSharedMovementNavigator(client,services,config);
        returnInventory=inventory();returnCurrency=currency();
        if(!returning.atPosition(services.player().position())&&!returnNavigator.start(callerPose.position(),.25,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.EXACT_RETURN)){
            returning.fail("No loaded supported safe path to captured caller position; caller must stop");returnFailed=true;status=returning.reason();finish();return;
        }
        status=returning.reason();
    }
    private void tickReturn(){
        if(client.gui.screen()!=null){cancel("Unexpected screen during quiver physical return");return;}
        boolean unchanged=returnInventory.equals(inventory())&&Objects.equals(returnCurrency,currency())&&client.player.containerMenu.getCarried().isEmpty();
        if(!unchanged){cancel("Refill return inventory/currency/cursor ownership changed");return;}
        boolean blocked=false;
        if(!returning.atPosition(services.player().position()))blocked=returnNavigator.tick()==dev.veltraph.client.navigation.mc.McTravelNavigator.State.FAILED;
        else {returnNavigator.stop();services.rotation().track(callerPose.yaw(),callerPose.pitch());}
        returning.observe(new QuiverReturnRuntime.Pose(services.player().position(),services.rotation().yaw(),services.rotation().pitch()),tick,true,unchanged,blocked);
        status=returning.reason();
        if(returning.state()==QuiverReturnRuntime.State.FAILED){returnFailed=true;finish();}
        else if(returning.state()==QuiverReturnRuntime.State.COMPLETE)finish();
    }
    private void finish(){
        if(active&&runtime.state()==QuiverRefillRuntime.State.COMPLETE&&npcIngress!=null&&returning==null){beginReturn();return;}
        if(returnNavigator!=null)returnNavigator.stop();
        if(returning!=null)services.rotation().cancelRotation();
        if(npcIngress!=null)npcIngress.stop();
        if(LEASES.get(client)!=this){active=false;return;}
        if(client.player==player&&client.level==level&&client.getConnection()==connection){
            var menu=menus.read();if(menu!=null&&client.player.containerMenu==menuIdentity&&runtime.owns(menu)&&client.player.containerMenu.getCarried().isEmpty())menus.close(menu.id());
            if(services.inventory().selectedSlot()==leasedSlot)services.inventory().selectSlot(previousSlot);
        }
        release();LEASES.remove(client);active=false;waitingMenu=false;
    }
    private void release(){services.input().releaseAllMovement();services.breaking().setBreaking(false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);}
}
