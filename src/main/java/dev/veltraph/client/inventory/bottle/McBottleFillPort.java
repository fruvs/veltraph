package dev.veltraph.client.inventory.bottle;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.*;
import java.util.*;

/** Exact native stack/menu port and normal source-water/chest approach. No synthetic conversion/storage. */
public final class McBottleFillPort implements BottleFillWorkflow.Port {
    private final java.util.function.Supplier<dev.veltraph.client.core.Module> parent;private Object activationPlayer,activationWorld,activationConnection;
    private final Minecraft client;private final FarmServices services;private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;private final McInventoryService inventory;private final McMenuAccess menus;
    private record Binding(Object player,Object world,AbstractContainerMenu menu,int revision,int selected,List<ItemStack> inventory,Map<Integer,ItemStack> rows) {}
    private final IdentityHashMap<BottleFillWorkflow.Observation,Binding> bindings=new IdentityHashMap<>();private int transferTick=Integer.MIN_VALUE;private long fillReceiptAfter=-1,storeReceiptAfter=-1;private int storeReceiptRevision;private BottleFillReceipts.Scope fillReceiptScope,storeReceiptScope;private BottleFillWorkflow.Position walking;
    public McBottleFillPort(Minecraft client,ConfigService config,FarmServices services){this(client,config,services,()->null);}
    public McBottleFillPort(Minecraft client,ConfigService config,FarmServices services,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){this.parent=parent;this.client=client;this.services=services;navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,parent);inventory=new McInventoryService(client);menus=new McMenuAccess(client);}
    public void beginOwner(){activationPlayer=activationWorld=activationConnection=null;}
    private boolean liveParent(){var modules=dev.veltraph.client.VeltraphBootstrap.moduleManager();var expected=parent.get();return client.isSameThread()&&modules!=null&&expected!=null&&modules.get(expected.id()).orElse(null)==expected&&expected.isEnabled()&&modules.inputOwner().orElse(null)==expected;}
    private boolean absentRestorationParent(){var modules=dev.veltraph.client.VeltraphBootstrap.moduleManager();var expected=parent.get();return client.isSameThread()&&modules!=null&&expected!=null&&!expected.isEnabled()&&modules.get(expected.id()).orElse(null)==expected&&modules.inputOwner().isEmpty()&&client.player==activationPlayer&&client.level==activationWorld&&client.getConnection()==activationConnection;}
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return walking!=null&&mobility.consumeExpectedTeleport(position,now);}
    public static BottleFillWorkflow.Stack describe(ItemStack stack){if(stack.isEmpty())return BottleFillWorkflow.Stack.EMPTY;return new BottleFillWorkflow.Stack(new BottleFillWorkflow.Identity(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),MenuSnapshot.clean(stack.getHoverName().getString()),stack.getComponents().stream().map(Object::toString).sorted().toList().toString()),stack.getCount(),
            stack.isDamageableItem()||stack.has(DataComponents.TOOL)||stack.has(DataComponents.WEAPON)||stack.has(DataComponents.BLOCKS_ATTACKS));}
    private static net.minecraft.core.BlockPos pos(BottleFillWorkflow.Position p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(BottleFillWorkflow.Position p){return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private boolean water(BottleFillWorkflow.Position p){return loaded(p)&&client.level.getFluidState(pos(p)).is(FluidTags.WATER)&&client.level.getFluidState(pos(p)).isSource();}
    private boolean chest(BottleFillWorkflow.Position p){if(!loaded(p))return false;var b=client.level.getBlockState(pos(p)).getBlock();return b==Blocks.CHEST||b==Blocks.TRAPPED_CHEST;}
    @Override public BottleFillWorkflow.Observation read(BottleFillWorkflow.Request request){
        if(!client.isSameThread())throw new IllegalStateException("Bottle filling requires client thread");bindings.clear();
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null||client.getConnection()==null)return new BottleFillWorkflow.Observation(null,null,null,"",0,"",List.of(),List.of(),0,false,false,false,false);
        if(activationPlayer==null&&liveParent()){activationPlayer=client.player;activationWorld=client.level;activationConnection=client.getConnection();}
        var p=client.player;var menu=p.containerMenu;var snapshot=menus.read();Object menuIdentity=snapshot!=null?menu:client.gui.screen()!=null?client.gui.screen():menu!=p.inventoryMenu?menu:null;
        var items=new ArrayList<BottleFillWorkflow.Stack>();var copies=new ArrayList<ItemStack>();for(int i=0;i<36;i++){var s=p.getInventory().getItem(i);items.add(describe(s));copies.add(s.copy());}
        var rows=new ArrayList<BottleFillWorkflow.Row>();var slots=new HashMap<Integer,ItemStack>();for(var s:menu.slots){slots.put(s.index,s.getItem().copy());if(snapshot!=null)rows.add(new BottleFillWorkflow.Row(s.index,s.container==p.getInventory()?s.getContainerSlot():-1,describe(s.getItem())));}
        var result=new BottleFillWorkflow.Observation(p,client.level,menuIdentity,snapshot==null?"":snapshot.title(),menu.getStateId(),client.level.dimension().identifier().toString(),items,rows,p.getInventory().getSelectedSlot(),menu.getCarried().isEmpty(),p.tickCount>transferTick,water(request.source()),chest(request.chest()));
        bindings.put(result,new Binding(p,client.level,menu,menu.getStateId(),result.selected(),copies,slots));return result;
    }
    private Binding valid(BottleFillWorkflow.Observation o){var b=bindings.get(o);if(!client.isSameThread()||b==null||client.player==null||!client.player.isAlive()||client.player!=b.player()||client.level!=b.world()||client.player.containerMenu!=b.menu()||b.menu().getStateId()!=b.revision()||client.player.getInventory().getSelectedSlot()!=b.selected()||!b.menu().getCarried().isEmpty()||client.gameMode==null||client.getConnection()==null)return null;
        if(o.menu()==null&&client.gui.screen()!=null)return null;if(o.menu()!=null&&(!(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)||screen.getMenu()!=b.menu()))return null;
        for(int i=0;i<36;i++)if(!ItemStack.matches(b.inventory().get(i),client.player.getInventory().getItem(i)))return null;for(var s:b.menu().slots)if(!ItemStack.matches(b.rows().get(s.index),s.getItem()))return null;return b;}
    @Override public boolean swap(BottleFillWorkflow.Observation o,int main,int hotbar){var b=valid(o);if(b==null||main<9||main>=36||hotbar<0||hotbar>=9||o.inventory().get(main).empty()==o.inventory().get(hotbar).empty())return false;
        var slots=b.menu().slots.stream().filter(s->s.container==client.player.getInventory()&&s.getContainerSlot()==main).toList();if(slots.size()!=1)return false;
        client.gameMode.handleContainerInput(b.menu().containerId,slots.getFirst().index,hotbar,ContainerInput.SWAP,client.player);transferTick=client.player.tickCount;return true;}
    @Override public boolean select(BottleFillWorkflow.Observation o,int hotbar){if(valid(o)==null||hotbar<0||hotbar>=9)return false;inventory.selectSlot(hotbar);return client.player.getInventory().getSelectedSlot()==hotbar;}
    @Override public BottleFillWorkflow.Approach approach(BottleFillWorkflow.Observation o,BottleFillWorkflow.Position target,BottleFillWorkflow.Position stand){
        if(valid(o)==null||o.menu()!=null||!loaded(target)||!loaded(stand))return BottleFillWorkflow.Approach.BLOCKED;
        if(!liveParent()){
            // Disabled cleanup can verify only an already reachable restored chest. It never borrows a new Root owner.
            if(!absentRestorationParent()||!chest(target)||client.player.position().distanceTo(new Vec3(stand.x()+.5,stand.y(),stand.z()+.5))>.85)return BottleFillWorkflow.Approach.BLOCKED;
            var eye=client.player.getEyePosition();if(eye.distanceTo(Vec3.atCenterOf(pos(target)))>4.5)return BottleFillWorkflow.Approach.BLOCKED;
            var hit=client.level.clip(new ClipContext(eye,eye.add(client.player.getViewVector(1).scale(4.5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
            return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos(target))?BottleFillWorkflow.Approach.READY:BottleFillWorkflow.Approach.BLOCKED;
        }
        if(walking!=null||client.player.position().distanceTo(new Vec3(stand.x()+.5,stand.y(),stand.z()+.5))>.85){
            if(!stand.equals(walking)){mobility.stop();if(!mobility.startPreferLevel(navigator.position(new dev.veltraph.client.world.BlockPos(stand.x(),stand.y(),stand.z())),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL))return BottleFillWorkflow.Approach.BLOCKED;walking=stand;}
            var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){mobility.stop();walking=null;return BottleFillWorkflow.Approach.BLOCKED;}if(state==McTravelNavigator.State.ARRIVED){mobility.stop();walking=null;}return BottleFillWorkflow.Approach.MOVING;
        }
        mobility.stop();walking=null;if(motionCleanup.beforePublication(1)){services.rotation().lookAt(new dev.veltraph.client.world.Vec3(target.x()+.5,target.y()+(water(target)?.9:.5),target.z()+.5));motionCleanup.remember();}
        var eye=client.player.getEyePosition();if(eye.distanceTo(Vec3.atCenterOf(pos(target)))>4.5)return BottleFillWorkflow.Approach.BLOCKED;
        var hit=client.level.clip(new ClipContext(eye,eye.add(client.player.getViewVector(1).scale(4.5)),ClipContext.Block.OUTLINE,water(target)?ClipContext.Fluid.SOURCE_ONLY:ClipContext.Fluid.NONE,client.player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos(target))?BottleFillWorkflow.Approach.READY:BottleFillWorkflow.Approach.MOVING;
    }
    @Override public boolean fill(BottleFillWorkflow.Observation o,BottleFillWorkflow.Position source){if(valid(o)==null||o.menu()!=null||!water(source)||!client.player.getMainHandItem().is(Items.GLASS_BOTTLE))return false;
        var eye=client.player.getEyePosition();var hit=client.level.clip(new ClipContext(eye,eye.add(client.player.getViewVector(1).scale(4.5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,client.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(pos(source)))return false;fillReceiptScope=BottleFillPacketObserver.scope(client);fillReceiptAfter=BottleFillPacketObserver.journal().sequence();client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);return true;}
    @Override public boolean open(BottleFillWorkflow.Observation o,BottleFillWorkflow.Position target){if(valid(o)==null||o.menu()!=null||!chest(target)||describe(client.player.getMainHandItem()).tool())return false;
        var eye=client.player.getEyePosition();var hit=client.level.clip(new ClipContext(eye,eye.add(client.player.getViewVector(1).scale(4.5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(pos(target)))return false;client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);return true;}
    @Override public boolean store(BottleFillWorkflow.Observation o,int inventoryIndex){var b=valid(o);if(b==null||o.menu()==null||inventoryIndex<0||inventoryIndex>=36||o.inventory().get(inventoryIndex).count()!=1||o.inventory().get(inventoryIndex).tool())return false;
        var target=b.menu().slots.stream().filter(s->s.container==client.player.getInventory()&&s.getContainerSlot()==inventoryIndex).toList();if(target.size()!=1)return false;
        storeReceiptScope=BottleFillPacketObserver.scope(client);storeReceiptAfter=BottleFillPacketObserver.journal().sequence();storeReceiptRevision=b.revision();client.gameMode.handleContainerInput(b.menu().containerId,target.getFirst().index,0,ContainerInput.QUICK_MOVE,client.player);return true;}
    @Override public boolean fillAcknowledged(BottleFillWorkflow.Observation o,List<BottleFillWorkflow.Stack> before,List<BottleFillWorkflow.Stack> after){var b=valid(o);if(b==null||o.menu()!=null||fillReceiptAfter<0||fillReceiptScope==null||!fillReceiptScope.same(BottleFillPacketObserver.scope(client))||!o.inventory().equals(after))return false;var changed=new HashMap<Integer,BottleFillReceipts.Cell>();for(int i=0;i<36;i++)if(!before.get(i).equals(after.get(i)))changed.put(i,BottleFillPacketObserver.cell(client,b.inventory().get(i)));return BottleFillPacketObserver.journal().confirms(BottleFillPacketObserver.scope(client),fillReceiptAfter,changed,Map.of(),-1);}
    @Override public boolean storageAcknowledged(BottleFillWorkflow.Observation o,List<BottleFillWorkflow.Stack> after,List<BottleFillWorkflow.Stack> chestBefore,int issuedOutput){var binding=valid(o);if(binding==null||o.menu()==null||storeReceiptAfter<0||storeReceiptScope==null||!storeReceiptScope.same(BottleFillPacketObserver.scope(client))||issuedOutput<0||issuedOutput>=36||!o.inventory().equals(after)||!after.get(issuedOutput).empty())return false;var chest=new HashMap<Integer,BottleFillReceipts.Cell>();int index=0;for(var row:binding.menu().slots)if(row.container!=client.player.getInventory()){if(index>=chestBefore.size())return false;if(!chestBefore.get(index).equals(describe(row.getItem())))chest.put(row.index,BottleFillPacketObserver.cell(client,row.getItem()));index++;}if(chest.size()!=1)return false;return BottleFillPacketObserver.journal().confirms(BottleFillPacketObserver.scope(client),storeReceiptAfter,Map.of(issuedOutput,BottleFillPacketObserver.cell(client,binding.inventory().get(issuedOutput))),chest,storeReceiptRevision);}
    @Override public boolean close(BottleFillWorkflow.Observation o){if(valid(o)==null||o.menu()==null)return false;return menus.close(client.player.containerMenu.containerId);}
    @Override public void release(){motionCleanup.release(mobility);walking=null;}
    /** Validate configured output against actual vanilla water potion metadata, not an XP-bottle guess. */
    public static boolean vanillaWater(BottleFillWorkflow.Identity identity){var stack=net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,Potions.WATER);return describe(stack).identity().equals(identity);}
}
