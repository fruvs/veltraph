package dev.veltraph.client.foraging.supply;

import java.util.*;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.inventory.bottle.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.skills.SkillContracts;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Normal client requests, exact full native ledgers, and fresh server container verification. */
public final class McForagingSupplyPort implements ForagingSupplyRuntime.Port {
    private final Minecraft c;private final FarmServices services;private final McNavigator navigator;
    private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;
    private final java.util.function.BooleanSupplier parentAuthority;
    private final java.util.function.Supplier<dev.veltraph.client.core.Module> parent;private final McMenuAccess menus;private final McGameContextService location;
    private final ForagingSupplyBindings bindings;private final ForagingSupplyBindings.Resource resource;
    private record Bound(Object player,Object world,Object connection,Object screen,Object menu,List<ItemStack> inventory,List<ItemStack> rows,int selected,List<ItemStack> gear) {}
    private Bound bound;private ForagingSupplyRuntime.Observation observed;private Object ownerMenu,oldStorageMenu,acquisitionMenu;
    private boolean acquisitionOpened,acquisitionClicked,openIssued,walking,reopening,closeIssued;private long receiptAfter,storageReceiptAfter;private List<ItemStack> purchaseBefore;private long purchasePurse,quote;
    public record AcquisitionProof(Object menu,int menuId,int requestRevision,List<BottleFillReceipts.Receipt> receipts,ForagingSupplyRuntime.Scope scope,long amount) {public AcquisitionProof{receipts=List.copyOf(receipts);}}
    private AcquisitionProof acceptedProof;private int purchaseMenuId,purchaseRequestRevision;private List<BottleFillReceipts.Receipt> acceptedPacketReceipts=List.of();
    public Optional<AcquisitionProof> acceptedAcquisition(){return Optional.ofNullable(acceptedProof);}
    private boolean acquisitionVerified;private List<ItemStack> acquiredInventory;private ForagingPurchaseCursor prediction;private Object player,world,connection;private List<ItemStack> originalGear;private int selected;
    public McForagingSupplyPort(Minecraft c,ConfigService config,FarmServices services,ForagingSupplyBindings bindings,ForagingSupplyConfig.Type type){this(c,config,services,bindings,type,()->null);}
    public McForagingSupplyPort(Minecraft c,ConfigService config,FarmServices services,ForagingSupplyBindings bindings,ForagingSupplyConfig.Type type,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){this(c,config,services,bindings,type,parent,()->true);}
    public McForagingSupplyPort(Minecraft c,ConfigService config,FarmServices services,ForagingSupplyBindings bindings,ForagingSupplyConfig.Type type,java.util.function.Supplier<dev.veltraph.client.core.Module> parent,java.util.function.BooleanSupplier authority){this.parentAuthority=java.util.Objects.requireNonNull(authority);this.parent=parent;mobility=new McSharedMovementNavigator(c,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(c,services,parent);this.c=c;this.services=services;this.bindings=bindings;resource=bindings.resources().get(type);navigator=new McNavigator(c,services.input(),config);menus=new McMenuAccess(c);location=new McGameContextService(c);}
    public void bindOwner(){player=c.player;world=c.level;connection=c.getConnection();originalGear=gear();selected=c.player.getInventory().getSelectedSlot();}
    public boolean ownerValid(){return c.player==player&&c.level==world&&c.getConnection()==connection&&c.player!=null&&selected==c.player.getInventory().getSelectedSlot()&&equal(originalGear,gear());}
    private List<ItemStack> gear(){var result=new ArrayList<ItemStack>();if(c.player!=null)for(var slot:List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND))result.add(c.player.getItemBySlot(slot).copy());return result;}
    private static boolean equal(List<ItemStack> a,List<ItemStack> b){if(a==null||a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}
    private static ForagingSupplyRuntime.Stack stack(ItemStack s){var d=McBottleFillPort.describe(s);return s.isEmpty()?ForagingSupplyRuntime.Stack.EMPTY:new ForagingSupplyRuntime.Stack(d.identity().id(),d.identity().name(),d.identity().components(),d.count(),d.tool());}
    public ForagingSupplyRuntime.Observation read(){
        if(!c.isSameThread())throw new IllegalStateException("Client thread required");
        if(c.player==null||c.level==null||c.getConnection()==null)return new ForagingSupplyRuntime.Observation(null,services.clock().nowMillis(),List.of(),false,false,-1,null,"",List.of(),false,false);
        var inventory=new ArrayList<ItemStack>();var descriptions=new ArrayList<ForagingSupplyRuntime.Stack>();for(int i=0;i<36;i++){var s=c.player.getInventory().getItem(i).copy();inventory.add(s);descriptions.add(stack(s));}
        var view=menus.read();var rows=new ArrayList<ItemStack>();var chest=new ArrayList<ForagingSupplyRuntime.Stack>();for(var row:c.player.containerMenu.slots){rows.add(row.getItem().copy());if(row.container!=c.player.getInventory())chest.add(stack(row.getItem()));}
        location.resolution();long purse=PestBindings.amount(location.signals().sidebarRowsRaw(),resource.pursePrefix(),"").map(n->{try{return n.longValueExact();}catch(ArithmeticException e){return -1L;}}).orElse(-1L);
        var pos=new net.minecraft.core.BlockPos(bindings.chest().x(),bindings.chest().y(),bindings.chest().z());boolean chestValid=c.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4)&&(c.level.getBlockState(pos).is(Blocks.CHEST)||c.level.getBlockState(pos).is(Blocks.TRAPPED_CHEST));
        boolean ack=acquisitionVerified&&equal(acquiredInventory,inventory)&&purse==purchasePurse-quote;if(acquisitionClicked&&!acquisitionVerified){var changes=new HashMap<Integer,BottleFillReceipts.Cell>();for(int i=0;i<36;i++)if(!ItemStack.matches(purchaseBefore.get(i),inventory.get(i)))changes.put(i,BottleFillPacketObserver.cell(c,inventory.get(i)));ack=!changes.isEmpty()&&BottleFillPacketObserver.journal().confirms(BottleFillPacketObserver.scope(c),receiptAfter,changes,Map.of(),-1);
            if(ack){acceptedPacketReceipts=BottleFillPacketObserver.journal().containerWitness(BottleFillPacketObserver.scope(c),receiptAfter,changes,Map.of(),purchaseRequestRevision).map(BottleFillReceipts.Match::receipts).orElse(List.of());}
        }
        if(!reopening&&openIssued&&ownerMenu==null&&view!=null&&c.player.containerMenu!=oldStorageMenu&&view.title().equals(bindings.chestTitle())){
            var playerCells=new HashMap<Integer,BottleFillReceipts.Cell>();for(int i=0;i<36;i++)playerCells.put(i,BottleFillPacketObserver.cell(c,inventory.get(i)));
            var chestCells=new HashMap<Integer,BottleFillReceipts.Cell>();for(var row:c.player.containerMenu.slots)if(row.container!=c.player.getInventory())chestCells.put(row.index,BottleFillPacketObserver.cell(c,row.getItem()));
            if(BottleFillPacketObserver.journal().confirms(BottleFillPacketObserver.scope(c),storageReceiptAfter,playerCells,chestCells,0))ownerMenu=c.player.containerMenu;
        }
        if(reopening&&view!=null&&openIssued&&c.player.containerMenu!=oldStorageMenu&&view.title().equals(bindings.chestTitle())){var playerCells=new HashMap<Integer,BottleFillReceipts.Cell>();for(int i=0;i<36;i++)playerCells.put(i,BottleFillPacketObserver.cell(c,inventory.get(i)));var chestCells=new HashMap<Integer,BottleFillReceipts.Cell>();for(var row:c.player.containerMenu.slots)if(row.container!=c.player.getInventory())chestCells.put(row.index,BottleFillPacketObserver.cell(c,row.getItem()));ack=BottleFillPacketObserver.journal().confirms(BottleFillPacketObserver.scope(c),storageReceiptAfter,playerCells,chestCells,0);if(ack){ownerMenu=c.player.containerMenu;reopening=false;closeIssued=false;}}
        observed=new ForagingSupplyRuntime.Observation(new ForagingSupplyRuntime.Scope(c.player,c.level,c.getConnection()),services.clock().nowMillis(),descriptions,c.player.containerMenu.getCarried().isEmpty(),c.player.isAlive(),purse,view!=null?c.player.containerMenu:c.gui.screen(),view==null?"":view.title(),view==null?List.of():chest,chestValid,ack);
        bound=new Bound(c.player,c.level,c.getConnection(),c.gui.screen(),c.player.containerMenu,inventory,rows,c.player.getInventory().getSelectedSlot(),gear());
        return observed;
    }
    private boolean valid(ForagingSupplyRuntime.Observation o){if(o!=observed||bound==null||c.player!=bound.player()||c.level!=bound.world()||c.getConnection()!=bound.connection()||c.gui.screen()!=bound.screen()||c.player.containerMenu!=bound.menu()||c.player.getInventory().getSelectedSlot()!=bound.selected()||!c.player.containerMenu.getCarried().isEmpty()||!equal(bound.gear(),gear()))return false;for(int i=0;i<36;i++)if(!ItemStack.matches(bound.inventory().get(i),c.player.getInventory().getItem(i)))return false;int i=0;for(var row:c.player.containerMenu.slots)if(!ItemStack.matches(bound.rows().get(i++),row.getItem()))return false;return true;}
    private boolean purchaseOffer(MenuSnapshot view){if(!view.title().equals(resource.offerTitle()))return false;var offers=view.slots().stream().filter(s->!s.playerInventory()&&s.name().equals(resource.offerName())&&s.itemId().equals(resource.id())&&!s.locked()).toList();if(offers.size()!=1)return false;var row=c.player.containerMenu.getSlot(offers.getFirst().id());if(!McBottleFillPort.describe(row.getItem()).identity().components().equals(resource.offerComponents()))return false;var cost=SkillContracts.quotedCost(offers.getFirst().lore(),resource.quantity());if(cost.isEmpty()||cost.getAsLong()>resource.maximumCost()||cost.getAsLong()>observed.purse())return false;purchaseMenuId=view.id();purchaseRequestRevision=view.revision();quote=cost.getAsLong();purchasePurse=observed.purse();purchaseBefore=bound.inventory();ownerMenu=c.player.containerMenu;receiptAfter=BottleFillPacketObserver.journal().sequence();prediction=new ForagingPurchaseCursor(new ForagingSupplyRuntime.Scope(c.player,c.level,c.getConnection()),ownerMenu,purchaseMenuId,stack(row.getItem()),purchaseBefore.stream().map(McForagingSupplyPort::stack).toList(),purchasePurse);if(actionAllowed(observed)&&menus.click(view,offers.getFirst().id())){acquisitionClicked=true;acquisitionMenu=ownerMenu;return true;}return false;}
    @Override public boolean acquire(ForagingSupplyRuntime.Observation o,ForagingSupplyBindings.Resource r){if(!actionAllowed(o)||acquisitionOpened||o.menu()!=null||!r.equals(resource))return false;acquisitionOpened=new McServerCommandService(c).sendCommand(r.openCommand());return acquisitionOpened;}
    private boolean exactPurchaseLedger(ForagingSupplyRuntime.Observation o){int added=0;for(int i=0;i<36;i++){var before=stack(purchaseBefore.get(i));var after=o.inventory().get(i);if(before.equals(after))continue;if(!after.matches(resource)||before.count()>0&&(!before.matches(resource)||after.count()<before.count()))return false;added+=after.count()-before.count();}return added==resource.quantity();}
    @Override public boolean ownedPurchasePrediction(ForagingSupplyRuntime.Observation o){return o==observed&&prediction!=null&&acquisitionClicked&&!acquisitionVerified&&o.title().equals(resource.offerTitle())&&ownerValid()&&c.player.containerMenu==acquisitionMenu&&prediction.permits(o,c.player.containerMenu,c.player.containerMenu.containerId,stack(c.player.containerMenu.getCarried()));}
    @Override public boolean acquisitionProgress(ForagingSupplyRuntime.Observation o){if(!valid(o))return false;var view=menus.read();if(acquisitionClicked){if(view!=null&&c.player.containerMenu!=acquisitionMenu)return false;if(view!=null&&o.nativeAck()&&o.purse()==purchasePurse-quote&&exactPurchaseLedger(o)){if(!acceptedPacketReceipts.isEmpty())acceptedProof=new AcquisitionProof(acquisitionMenu,purchaseMenuId,purchaseRequestRevision,acceptedPacketReceipts,o.scope(),quote);acquisitionVerified=true;acquiredInventory=bound.inventory();if(!actionAllowed(o))return false;c.player.closeContainer();ownerMenu=null;}return true;}if(view==null)return true;if(acquisitionMenu!=null&&c.player.containerMenu!=acquisitionMenu)return false;acquisitionMenu=c.player.containerMenu;ownerMenu=acquisitionMenu;return purchaseOffer(view);}
    @Override public boolean storageProgress(ForagingSupplyRuntime.Observation o){return reopening&&open(o);}
    private boolean liveParent(){
        var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();var expected=parent.get();
        return c.isSameThread()&&expected!=null&&expected.isEnabled()&&manager!=null
                &&manager.get(expected.id()).orElse(null)==expected&&manager.inputOwner().orElse(null)==expected;
    }
    private boolean actionAllowed(ForagingSupplyRuntime.Observation o){return liveParent()&&valid(o)&&parentAuthority.getAsBoolean()&&valid(o);}
    @Override public boolean open(ForagingSupplyRuntime.Observation o){if(!actionAllowed(o)||o.menu()!=null||c.gameMode==null)return false;if(openIssued)return true;
        var stand=bindings.stand();var standingFeet=navigator.position(stand);
        if(walking||c.player.position().distanceTo(new Vec3(standingFeet.x(),standingFeet.y(),standingFeet.z()))>.85){
            if(!actionAllowed(o))return false;
            if(!walking){if(!mobility.startPreferLevel(standingFeet,.35,SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL))return false;walking=true;}
            var state=mobility.tick();
            if(state==McTravelNavigator.State.FAILED)return false;
            if(state==McTravelNavigator.State.ACTIVE)return true;
            // Crossing the interaction threshold cannot supersede an active child.
            // Only its actual supported ARRIVED result permits the chest ray/interaction phase.
            mobility.stop();walking=false;
            if(!actionAllowed(o))return false;
        }
        mobility.stop();walking=false;var target=bindings.chest();if(!motionCleanup.beforePublication(1)||!actionAllowed(o))return false;
        try{services.rotation().lookAt(new dev.veltraph.client.world.Vec3(target.x()+.5,target.y()+.5,target.z()+.5));}
        finally{motionCleanup.remember();}
        if(c.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(new net.minecraft.core.BlockPos(target.x(),target.y(),target.z()))&&c.player.getEyePosition().distanceTo(hit.getLocation())<=c.player.blockInteractionRange()) {if(!actionAllowed(o))return false;if(!reopening){storageReceiptAfter=BottleFillPacketObserver.journal().sequence();oldStorageMenu=c.player.containerMenu;}c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit);openIssued=true;}return true;}
    @Override public boolean storageMenuAwaiting(ForagingSupplyRuntime.Observation o){return valid(o)&&openIssued&&!walking&&!reopening&&ownerMenu==null&&o.title().equals(bindings.chestTitle());}
    @Override public boolean storageMenuOwned(ForagingSupplyRuntime.Observation o){return valid(o)&&openIssued&&!walking&&c.player.containerMenu==ownerMenu;}
    @Override public boolean store(ForagingSupplyRuntime.Observation o,int index){if(!actionAllowed(o)||!storageMenuOwned(o)||o.menu()==null||!o.title().equals(bindings.chestTitle())||index<0||index>=36||!o.inventory().get(index).matches(resource))return false;var slots=c.player.containerMenu.slots.stream().filter(s->s.container==c.player.getInventory()&&s.getContainerSlot()==index).toList();if(slots.size()!=1)return false;ownerMenu=c.player.containerMenu;closeIssued=false;c.gameMode.handleContainerInput(c.player.containerMenu.containerId,slots.getFirst().index,0,ContainerInput.QUICK_MOVE,c.player);return true;}
    @Override public boolean withdraw(ForagingSupplyRuntime.Observation o,int index){
        if(!actionAllowed(o)||!storageMenuOwned(o)||o.menu()==null||!o.title().equals(bindings.chestTitle())||index<0||index>=o.chest().size()||!o.chest().get(index).matches(resource))return false;
        var slots=c.player.containerMenu.slots.stream().filter(s->s.container!=c.player.getInventory()).toList();
        if(index>=slots.size()||!stack(slots.get(index).getItem()).equals(o.chest().get(index)))return false;
        ownerMenu=c.player.containerMenu;closeIssued=false;
        c.gameMode.handleContainerInput(c.player.containerMenu.containerId,slots.get(index).index,0,ContainerInput.QUICK_MOVE,c.player);return true;
    }
    @Override public boolean verifyStorage(ForagingSupplyRuntime.Observation o){if(!actionAllowed(o)||c.player.containerMenu!=ownerMenu)return false;if(!closeIssued){storageReceiptAfter=BottleFillPacketObserver.journal().sequence();oldStorageMenu=ownerMenu;c.player.closeContainer();ownerMenu=null;reopening=true;openIssued=false;closeIssued=true;}return true;}
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();var expected=parent.get();
        return walking&&c.isSameThread()&&expected!=null&&expected.isEnabled()&&manager!=null
                &&manager.get(expected.id()).orElse(null)==expected&&manager.inputOwner().orElse(null)==expected
                &&ownerValid()&&mobility.consumeExpectedTeleport(position,now);
    }
    @Override public void release(){motionCleanup.release(mobility);walking=false;if(c.player==player&&c.level==world&&c.getConnection()==connection&&c.player.containerMenu==ownerMenu&&c.player.containerMenu.getCarried().isEmpty())c.player.closeContainer();ownerMenu=null;}
}
