package dev.veltraph.client.builder;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.*;
import java.util.random.RandomGenerator;

/** Ordinary-input port for the actual resource-helper dispatch, native slot receipts and WIGGLE callbacks. */
final class McSourceResourcePolicy {
    interface FlowPort {int tick(String flow);}
    enum Result { ACTIVE, COMPLETE, BLOCKED }
    private record Pending(SourceResourcePolicy.Action action,Object menu,int revision,int slot,
                           ItemStack before,ItemStack cursor,int inventoryCount,int toolCount,long issued) {}
    private final Minecraft client;private final FarmServices services;private final GeneratedResourceBinding binding;
    private final SourceResourceProfile profile;private final SourceResourcePolicy policy;private final FlowPort flow;
    private final McMenuAccess menus;private final McNavigator navigator;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final GeneratedInventoryRestock restock;private final int restockSlot;private long restockIssued;
    private final Object actor,world,connection;
    private final Object transport;
    private Object toolMenu,craftMenu;private boolean requestedTool,requestedInventory,craftOwned;
    private InventoryScreen inventoryScreen;
    private Pending pending;private ItemStack cursorOwned=ItemStack.EMPTY;private int cursorSource=-1;
    private long recoveryTicket;private String activeFlow="";private SourceResourcePolicy.Action flowAction;
    private String failure="";
    private SourceRecoveryTrace recoveryTrace;
    private final long deadline;
    McSourceResourcePolicy(Minecraft client,FarmServices services,ConfigService config,GeneratedResourceBinding binding,
                          SourceResourceProfile profile,int required,FlowPort flow) {this(client,services,config,binding,profile,required,flow,()->null);}
    McSourceResourcePolicy(Minecraft client,FarmServices services,ConfigService config,GeneratedResourceBinding binding,SourceResourceProfile profile,int required,FlowPort flow,java.util.function.Supplier<dev.veltraph.client.core.Module> parent) {
        interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,parent);this.client=client;this.services=services;this.binding=binding;this.profile=profile;this.flow=flow;
        deadline=System.currentTimeMillis()+profile.timeout();
        policy=new SourceResourcePolicy(profile.mode(),profile.order(),required,profile.moveCooldown(),profile.removeCooldown(),profile.clickMin(),profile.clickMax(),profile.timeout(),RandomGenerator.getDefault());
        if(profile.mode()==SourceResourcePolicy.Mode.EMPTY&&!binding.resourceItem().equals("minecraft:air"))throw new IllegalArgumentException("Source EMPTY resource requires actual AIR/empty item identity");
        if(profile.mode()==SourceResourcePolicy.Mode.BAZAAR_AND_DISOLVE&&profile.packed()==null)throw new IllegalArgumentException("Packed source item identity contract required for BAZAAR_AND_DISOLVE");
        if(profile.packed()!=null&&profile.packed().id().equals(binding.resourceItem())&&(profile.packed().name().isBlank()||binding.resourceName().isBlank()||profile.packed().name().equals(binding.resourceName())))throw new IllegalArgumentException("Packed/raw variants sharing an item id need distinct exact names");
        actor=client.player;world=client.level;connection=client.getConnection();menus=new McMenuAccess(client);navigator=new McNavigator(client,services.input(),config);
        restock=new GeneratedInventoryRestock(client);restockSlot=config.get(dev.veltraph.client.config.GeneratedBuilderConfig.RESTOCK_SLOT);
        transport=client.getConnection()==null?null:client.getConnection().getConnection();if(transport==null)throw new IllegalArgumentException("Source packet clock connection unavailable");BuilderResourcePacketClock.attach(transport,this);
    }
    String status(){return failure.isEmpty()?policy.status()+(recoveryTicket!=0?" | "+navigator.diagnostics():""):failure;}
    SourceRecoveryTrace recoveryTrace(){return recoveryTrace;}
    private boolean context(){return client.player!=null&&client.player==actor&&client.level==world&&client.getConnection()==connection&&connection!=null&&client.gameMode!=null&&client.player.isAlive();}
    boolean ownsCursor(){return context()&&craftMenu==client.player.containerMenu&&!cursorOwned.isEmpty()&&ItemStack.matches(cursorOwned,client.player.containerMenu.getCarried());}
    private Result fail(String text){failure=text;stop();return Result.BLOCKED;}
    Result tick() {
        if(!context())return fail("Source helper player/world/connection changed");
        if(System.currentTimeMillis()>deadline)return fail("Source helper lifetime expired while waiting for packet/slot/provider/path outcome");
        services.breaking().setBreaking(false);
        if(restock.pending()){if(restock.ready()){restock.keepTransfer();return Result.ACTIVE;}return System.currentTimeMillis()-restockIssued>profile.acknowledgementTimeout()?fail("Source tool restock correction not observed"):Result.ACTIVE;}
        if(toolSlot()<0&&client.gui.screen()==null) {
            ItemStack identity=ItemStack.EMPTY;
            for(int i=0;i<36;i++){var s=client.player.getInventory().getItem(i);if(matches(s,binding.toolItem(),binding.toolName())){if(!identity.isEmpty()&&!ItemStack.isSameItemSameComponents(identity,s))return fail("Ambiguous source tool variants");identity=s;}}
            var timing=BuilderResourcePacketClock.sample(transport,this);if(BuilderResourcePacketClock.clickBlocked(timing,System.currentTimeMillis()))return Result.ACTIVE;
            if(!restock.begin(s->matches(s,binding.toolItem(),binding.toolName()),restockSlot))return fail("Source tool missing or no empty native hotbar destination");restockIssued=System.currentTimeMillis();return Result.ACTIVE;
        }
        if(!activeFlow.isEmpty()) {
            int result=flow.tick(activeFlow);if(result<0)return fail("Owned source provider flow failed");
            if(result>0){activeFlow="";policy.acknowledged(flowAction);toolMenu=craftMenu=null;requestedTool=requestedInventory=false;}
            return Result.ACTIVE;
        }
        if(recoveryTicket!=0) {
            if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return fail("WIGGLE interrupted by foreign screen/cursor");
            var progress=navigator.tick();
            var timing=BuilderResourcePacketClock.sample(transport,this);long now=System.currentTimeMillis();
            recoveryTrace=recoveryTrace.observed(services.player().position(),navigator.diagnostics(),timing,now);
            if(progress==WalkController.State.ARRIVED||progress==WalkController.State.BLOCKED){
                long ticket=recoveryTicket;recoveryTicket=0;
                boolean arrived=progress==WalkController.State.ARRIVED;
                if(context()&&policy.recovered(ticket,arrived))recoveryTrace=recoveryTrace.finished(arrived);
                navigator.stop();
            }
            return Result.ACTIVE;
        }
        if(pending!=null)return receipt();
        var menu=menus.read();
        if(client.gui.screen()!=null&&menu==null)return fail("Foreign screen in source resource helper");
        boolean inventory=client.gui.screen()==inventoryScreen&&inventoryScreen!=null&&client.player.containerMenu==client.player.inventoryMenu;
        if(menu!=null&&menu.title().equals(binding.menuTitle())&&!inventory) {
            if(!requestedTool)return fail("Unrequested same-title source tool menu");
            if(toolMenu==null)toolMenu=client.player.containerMenu;else if(toolMenu!=client.player.containerMenu)return fail("Source tool menu replaced");
        }
        if(inventory) {
            if(!requestedInventory)return fail("Unowned source dissolution inventory screen");
            if(craftMenu==null){craftMenu=client.player.containerMenu;craftOwned=client.player.inventoryMenu.getSlot(0).getItem().isEmpty();for(int i=1;i<=4;i++)craftOwned&=client.player.inventoryMenu.getSlot(i).getItem().isEmpty();}
            if(craftMenu!=client.player.containerMenu)return fail("Dissolution menu replaced");
        }
        boolean tool=toolMenu!=null&&toolMenu==client.player.containerMenu&&menu!=null&&menu.title().equals(binding.menuTitle());
        if(tool&&storagePrefix()==0)return fail("Source helper unsupported native container type; no storage count fabricated");
        if(menu!=null&&!tool&&!inventory)return fail("Unbound source resource menu");
        List<SourceResourcePolicy.Slot> resources=new ArrayList<>(),foreign=new ArrayList<>(),packed=new ArrayList<>();
        int stock=tool?stock():-1,others=0;
        for(int i=0;i<36;i++){var item=client.player.getInventory().getItem(i);if(profile.otherResources().stream().anyMatch(spec->matches(item,spec.id(),spec.name()))&&!matchesResource(item))others++;}
        if(menu!=null)for(var slot:client.player.containerMenu.slots) {
            var item=slot.getItem();if(slot.container==client.player.getInventory()) {
                if(matchesResource(item)&&!item.isEmpty())resources.add(new SourceResourcePolicy.Slot(slot.index,item.getCount()));
                if(profile.packed()!=null&&matches(item,profile.packed().id(),profile.packed().name()))packed.add(new SourceResourcePolicy.Slot(slot.index,item.getCount()));
            }else if(tool&&slot.index<storagePrefix()&&!item.isEmpty()&&!matchesResource(item))foreign.add(new SourceResourcePolicy.Slot(slot.index,item.getCount()));
        }
        if(menu==null&&profile.packed()!=null)for(int i=0;i<36;i++){var item=client.player.getInventory().getItem(i);if(matches(item,profile.packed().id(),profile.packed().name()))packed.add(new SourceResourcePolicy.Slot(i,item.getCount()));}
        var empty=new ArrayList<Integer>();if(inventory)for(int i=1;i<=4;i++)if(client.player.inventoryMenu.getSlot(i).getItem().isEmpty())empty.add(i);
        boolean blockLook=client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK;
        int held=toolSlot();if(held==-2)return fail("Ambiguous source tool variants");if(held<0)return fail("Source tool missing from hotbar; owned restock required");
        var carried=client.player.containerMenu.getCarried();boolean packedCursor=ownsCursor()&&profile.packed()!=null&&matches(carried,profile.packed().id(),profile.packed().name());
        var timing=BuilderResourcePacketClock.sample(transport,this);long now=System.currentTimeMillis();
        var decision=policy.tick(new SourceResourcePolicy.Facts(context(),menu!=null,tool,inventory,held==services.inventory().selectedSlot(),blockLook,BuilderResourcePacketClock.lag(timing,now),BuilderResourcePacketClock.clickBlocked(timing,now),stock,resources,foreign,packed,others,empty,
                inventory?client.player.inventoryMenu.getSlot(0).getItem().getCount():0,packedCursor,carried.isEmpty(),craftOwned,System.currentTimeMillis()));
        return act(decision,menu,held);
    }
    private Result act(SourceResourcePolicy.Decision d,MenuSnapshot menu,int tool) {
        switch(d.action()) {
            case NONE -> {return Result.ACTIVE;}
            case STOP -> {return fail(d.reason());}
            case COMPLETE -> {String complete=policy.status();stop();failure=complete;return Result.COMPLETE;}
            case SELECT_TOOL -> services.inventory().selectSlot(tool);
            case LOOK_AWAY -> interactionRotation.track(client.player.getYRot(),-90);
            case USE_TOOL -> {if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return fail("Source tool-use context changed");requestedTool=true;client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);}
            case CLOSE -> {if(menu==null||!client.player.containerMenu.getCarried().isEmpty()||!menus.close(menu.id()))return fail("Source close refused foreign cursor/menu");if(client.gui.screen()==null){toolMenu=craftMenu=null;requestedTool=requestedInventory=false;}}
            case OPEN_INVENTORY -> {if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return fail("Source inventory opening refused");requestedInventory=true;inventoryScreen=new InventoryScreen(client.player);client.gui.setScreen(inventoryScreen);}
            case BUY,SELL_OTHER -> {flowAction=d.action();activeFlow=d.action()==SourceResourcePolicy.Action.BUY?profile.purchaseFlow(d.quantity()):profile.sellFlow();if(activeFlow.isBlank())return fail("Blank source "+d.action()+" provider contract");}
            case RECOVER -> {return recover(d.generation());}
            case QUICK_MOVE_IN,QUICK_MOVE_OUT,PICK_PACKED,PUT_RANDOM_INPUT,QUICK_MOVE_RESULT -> {return click(d,menu);}
        }
        return Result.ACTIVE;
    }
    private Result click(SourceResourcePolicy.Decision d,MenuSnapshot menu) {
        if(menu==null||d.slot()<0||d.slot()>=client.player.containerMenu.slots.size())return fail("Source native slot unavailable");
        var fresh=menus.read();if(!Objects.equals(fresh,menu))return fail("Source native menu changed before input");
        var slot=client.player.containerMenu.getSlot(d.slot());var before=slot.getItem().copy();var carried=client.player.containerMenu.getCarried().copy();
        boolean putting=d.action()==SourceResourcePolicy.Action.PUT_RANDOM_INPUT;
        if(putting) {if(!ownsCursor()||!before.isEmpty()||!slot.mayPlace(carried))return fail("Source crafting destination/cursor changed");}
        else if(!carried.isEmpty()||before.isEmpty())return fail("Source click source/cursor changed");
        if(d.action()==SourceResourcePolicy.Action.QUICK_MOVE_OUT&&slot.container==client.player.getInventory()||d.action()==SourceResourcePolicy.Action.QUICK_MOVE_IN&&slot.container!=client.player.getInventory())return fail("Source transfer role differs");
        if(d.action()==SourceResourcePolicy.Action.PICK_PACKED){cursorOwned=before.copy();cursorSource=d.slot();}
        pending=new Pending(d.action(),client.player.containerMenu,menu.revision(),d.slot(),before,carried,count(before.isEmpty()?carried:before),stock(),System.currentTimeMillis());
        var input=d.action()==SourceResourcePolicy.Action.PICK_PACKED||putting?ContainerInput.PICKUP:ContainerInput.QUICK_MOVE;
        client.gameMode.handleContainerInput(menu.id(),d.slot(),0,input,client.player);return Result.ACTIVE;
    }
    private Result receipt() {
        var p=pending;
        if(!context()||client.player.containerMenu!=p.menu()||menus.read()==null)return fail("Source slot receipt menu/context changed");
        boolean revised=client.player.containerMenu.getStateId()!=p.revision();var slot=client.player.containerMenu.getSlot(p.slot()).getItem();var cursor=client.player.containerMenu.getCarried();
        boolean ack=false;
        switch(p.action()) {
            case QUICK_MOVE_IN -> ack=stock()>p.toolCount()&&count(p.before())<p.inventoryCount();
            case QUICK_MOVE_OUT -> ack=slot.getCount()<p.before().getCount()&&count(p.before())>p.inventoryCount();
            case PICK_PACKED -> ack=ItemStack.matches(cursorOwned,cursor)&&slot.isEmpty();
            case PUT_RANDOM_INPUT -> ack=cursor.isEmpty()&&ItemStack.matches(slot,p.cursor());
            case QUICK_MOVE_RESULT -> ack=cursor.isEmpty()&&count(p.before())>p.inventoryCount();
            default -> {}
        }
        if(revised&&ack){policy.acknowledged(p.action());pending=null;if(p.action()==SourceResourcePolicy.Action.PUT_RANDOM_INPUT){cursorOwned=ItemStack.EMPTY;cursorSource=-1;}return Result.ACTIVE;}
        if(System.currentTimeMillis()-p.issued()>profile.acknowledgementTimeout())return fail("Source native "+p.action()+" lacked later revision/item/cursor receipt");
        return Result.ACTIVE;
    }
    private Result recover(long ticket) {
        if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return fail("Source WIGGLE requires closed GUI and empty cursor");
        var p=client.player.blockPosition();var actorPos=new BlockPos(p.getX(),p.getY(),p.getZ());var facts=new ArrayList<SourceResourcePolicy.Candidate>();
        for(int dx=-3;dx<=3;dx++)for(int dy=-3;dy<=3;dy++)for(int dz=-3;dz<=3;dz++) {
            var pos=actorPos.add(dx,dy,dz);if(pos.x()<-240||pos.x()>239||pos.z()<-240||pos.z()>239||pos.y()<0||pos.y()>255)continue;
            double distance=SourceResourcePolicy.recoveryCenterDistance(pos,services.player().position());facts.add(new SourceResourcePolicy.Candidate(pos,distance,navigator.standable(pos)));
        }
        var candidates=SourceResourcePolicy.recoveryCandidates(actorPos,facts);
        if(candidates.isEmpty())return fail("Source WIGGLE found no valid standing point in7x7x7 cube");
        var nearest=candidates.getFirst();var goal=navigator.position(nearest);
        if(!navigator.start(goal,2000,32))return fail("Source WIGGLE nearest standing point has no permitted path: "+navigator.diagnostics());
        recoveryTrace=SourceRecoveryTrace.started(recoveryTrace,ticket,actorPos,services.player().position(),nearest,goal);
        recoveryTicket=ticket;return Result.ACTIVE;
    }
    private int storagePrefix(){var menu=client.player.containerMenu;return menu instanceof ChestMenu chest?chest.getRowCount()*9:menu instanceof InventoryMenu?9:0;}
    boolean allowsScreen(String title){return context()&&(requestedTool&&title.equals(binding.menuTitle())||requestedInventory&&inventoryScreen!=null&&title.equals(MenuSnapshot.clean(inventoryScreen.getTitle().getString())));}
    private int stock(){if(client.player==null||toolMenu!=client.player.containerMenu||storagePrefix()==0)return -1;int count=0;for(int i=0;i<storagePrefix();i++){var item=client.player.containerMenu.getSlot(i).getItem();if(matchesResource(item))count+=item.getCount();}return count;}
    private int count(ItemStack prototype){int count=0;for(int i=0;i<36;i++){var s=client.player.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(prototype,s))count+=s.getCount();}return count;}
    private boolean matchesResource(ItemStack item){return matches(item,binding.resourceItem(),binding.resourceName());}
    private boolean matches(ItemStack item,String id,String name){return !item.isEmpty()&&BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(id)&&(name.isBlank()||MenuSnapshot.clean(item.getHoverName().getString()).equals(name));}
    private int toolSlot(){int found=-1;ItemStack identity=ItemStack.EMPTY;for(int i=0;i<9;i++){var stack=client.player.getInventory().getItem(i);if(matches(stack,binding.toolItem(),binding.toolName())){if(!identity.isEmpty()&&!ItemStack.isSameItemSameComponents(identity,stack))return -2;identity=stack;if(found<0)found=i;}}return found;}
    void stop(){if(recoveryTicket!=0&&recoveryTrace!=null)recoveryTrace=recoveryTrace.cancelled();policy.cancel();navigator.stop();services.input().releaseAllMovement();services.breaking().setBreaking(false);interactionRotation.cancelRotation();
        if(ownsCursor()&&cursorSource>=0&&cursorSource<client.player.containerMenu.slots.size()){var source=client.player.containerMenu.getSlot(cursorSource);if(source.getItem().isEmpty()&&source.mayPlace(cursorOwned))client.gameMode.handleContainerInput(client.player.containerMenu.containerId,cursorSource,0,ContainerInput.PICKUP,client.player);}
        restock.release();pending=null;cursorOwned=ItemStack.EMPTY;recoveryTicket=0;menus.clear();BuilderResourcePacketClock.detach(transport,this);}
}
