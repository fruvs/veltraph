package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.garden.pests.philip.SourceContactCommand;
import dev.veltraph.client.metrics.FinancialActionReceipt;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Real loaded RemotePlayer approach/ordinary interaction. Root must first admit the exact source Hub arrival. */
public final class McGeorgeBatchNpcPort implements GeorgeBatchRuntime.Port {
    private final Map<UUID,GeorgeSaleIdentity> acknowledgedIdentities=new HashMap<>();
    private final java.util.function.Supplier<dev.veltraph.client.core.Module> parent;
    private final Minecraft client;private final FarmServices services;private final SellingMenuAccess access;private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;
    private final GeorgeSourceHubProfile hub;private final SourceContactCommand.Settings dialogue;private final SellingRuntime.Settings settings;private final String prefix;private final BooleanSupplier authorized;
    private final Object player,world,connection;private final int selected;private final Map<EquipmentSlot,ItemStack> gear=new EnumMap<>(EquipmentSlot.class);
    private GeorgeBatchRuntime runtime;private RemotePlayer npc;private Object menuOwner;private long contactDeadline;private boolean walking,interacted,receipt,rejected;private Map<Integer,String> initialLedger=Map.of();private java.math.BigDecimal purse;
    public McGeorgeBatchNpcPort(Minecraft client,FarmServices services,ConfigService config,GeorgeSourceHubProfile hub,SourceContactCommand.Settings dialogue,SellingRuntime.Settings settings,String prefix,BooleanSupplier authorized){this(client,services,config,hub,dialogue,settings,prefix,authorized,()->null);}
    public McGeorgeBatchNpcPort(Minecraft client,FarmServices services,ConfigService config,GeorgeSourceHubProfile hub,SourceContactCommand.Settings dialogue,SellingRuntime.Settings settings,String prefix,BooleanSupplier authorized,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){this.parent=parent;
        this.client=client;this.services=services;this.hub=hub;this.dialogue=dialogue;this.settings=settings;this.prefix=prefix;this.authorized=authorized;
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||!dialogue.title().equals(settings.menuTitle())||prefix.isBlank())throw new IllegalArgumentException("Root-admitted native Hub owner and exact NPC dialogue required");
        player=client.player;world=client.level;connection=client.getConnection();selected=client.player.getInventory().getSelectedSlot();for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))gear.put(slot,client.player.getItemBySlot(slot).copy());access=new SellingMenuAccess(client);navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,parent);
    }
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return walking&&valid()&&mobility.consumeExpectedTeleport(position,now);}
    public void bind(GeorgeBatchRuntime runtime){if(this.runtime!=null)throw new IllegalStateException("NPC port already bound");this.runtime=Objects.requireNonNull(runtime);}
    public boolean owner(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player.isAlive()&&(client.player.getInventory().getSelectedSlot()==selected||mobility.permitsSelectedSlot(selected))&&gear.entrySet().stream().allMatch(e->ItemStack.matches(e.getValue(),client.player.getItemBySlot(e.getKey())));}
    public boolean valid(){var modules=dev.veltraph.client.VeltraphBootstrap.moduleManager();var expected=parent.get();return owner()&&authorized.getAsBoolean()&&modules!=null&&expected!=null&&modules.get(expected.id()).orElse(null)==expected&&expected.isEnabled()&&modules.inputOwner().orElse(null)==expected;}public boolean closed(){return owner()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}public SellingObservation read(){return access.read(prefix);}
    private List<RemotePlayer> npcs(){var result=new ArrayList<RemotePlayer>();if(owner())for(var entity:client.level.entitiesForRendering())if(entity instanceof RemotePlayer remote&&remote.isAlive()&&remote.getUUID().equals(hub.npcUuid())&&remote.getName().getString().equals(hub.npcName())&&remote.distanceTo(client.player)<=16)result.add(remote);return result;}
    public boolean beginContact(long tick){if(!valid()||!closed())return false;var matches=npcs();if(matches.size()!=1)return false;npc=matches.getFirst();menuOwner=null;contactDeadline=tick+hub.timeoutTicks();walking=interacted=receipt=rejected=false;var o=read();initialLedger=o.ledger();purse=o.coins();return purse!=null;}
    public FarmAutoSellRuntime.Progress contact(long tick){
        if(!valid()||tick>contactDeadline||rejected||npc==null||client.level.getEntity(npc.getId())!=npc||npcs().size()!=1||npcs().getFirst()!=npc){release();return FarmAutoSellRuntime.Progress.FAILED;}
        var o=read();if(!o.cursorEmpty()||!o.ledger().equals(initialLedger)||o.coins()==null||o.coins().compareTo(purse)!=0){release();return FarmAutoSellRuntime.Progress.FAILED;}
        if(o.menu()!=null){
            boolean exact=interacted&&o.menu().title().equals(dialogue.title())&&o.slots().values().stream().filter(s->s.inventoryIndex()<0&&s.registryId().equals(dialogue.marker().registryId())&&s.name().equals(dialogue.marker().name())&&s.components().equals(dialogue.marker().components())&&s.count()==dialogue.marker().count()).count()==1;
            if(!exact||menuOwner!=null&&menuOwner!=client.player.containerMenu){release();return FarmAutoSellRuntime.Progress.FAILED;}menuOwner=client.player.containerMenu;runtime.bindMenu(menuOwner);release();return receipt?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.ACTIVE;
        }
        if(client.gui.screen()!=null){release();return FarmAutoSellRuntime.Progress.FAILED;}if(interacted)return FarmAutoSellRuntime.Progress.ACTIVE;
        if(walking||client.player.distanceTo(npc)>2.8||!client.player.hasLineOfSight(npc)){
            if(!walking){var p=npc.blockPosition();boolean found=false;for(int[] d:new int[][]{{2,0},{-2,0},{0,2},{0,-2},{1,0},{-1,0},{0,1},{0,-1}})if(mobility.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+d[0],p.getY(),p.getZ()+d[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){found=true;break;}if(!found){release();return FarmAutoSellRuntime.Progress.FAILED;}walking=true;}
            var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){release();return FarmAutoSellRuntime.Progress.FAILED;}if(state==McTravelNavigator.State.ARRIVED)walking=false;return FarmAutoSellRuntime.Progress.ACTIVE;
        }
        mobility.stop();var delta=npc.getBoundingBox().getCenter().subtract(client.player.getEyePosition());if(motionCleanup.beforePublication(2)){services.rotation().track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));motionCleanup.remember();}
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity()==npc&&client.gameMode!=null){client.gameMode.interact(client.player,npc,hit,InteractionHand.MAIN_HAND);interacted=true;}return FarmAutoSellRuntime.Progress.ACTIVE;
    }
    public boolean releaseContact(){release();return closed();}
    public boolean act(SellingRuntime.Action action,SellingRule rule){boolean restoring=action.kind()==SellingRuntime.Kind.RETURN_DEPOSIT||action.kind()==SellingRuntime.Kind.RETURN_ORIGIN;if(!owner()||!restoring&&!valid())return false;
        if(client.player.containerMenu!=menuOwner){if(runtime.saleState()!=SellingRuntime.State.READY_CONFIRM||!action.observation().menu().title().equals(settings.confirmTitle()))return false;menuOwner=client.player.containerMenu;runtime.bindMenu(menuOwner);}return access.act(action,rule,prefix);}
    private void release(){motionCleanup.release(mobility);walking=false;}
    public void closeContact(){release();if(!owner()||runtime.cleaning()||runtime.escrowHeld())return;var o=read();if(o.menu()!=null&&client.player.containerMenu==menuOwner&&o.cursorEmpty()&&o.ledger().equals(initialLedger)&&o.coins()!=null&&o.coins().compareTo(purse)==0&&(o.menu().title().equals(settings.menuTitle())||o.menu().title().equals(settings.confirmTitle())))access.closeOwned(o.menu().id(),o.menu().title());}
    public void message(String line){if(runtime.state()==GeorgeBatchRuntime.State.CONTACT){String clean=dev.veltraph.client.gui.MenuSnapshot.clean(line);if(clean.equals(dialogue.receipt()))receipt=true;if(dialogue.rejections().contains(clean))rejected=true;}else runtime.message(line);}
    public List<SellingAcknowledgment> acknowledgments(String outcomeId,SellingAcknowledgment.Denomination denomination){if(!client.isSameThread())throw new IllegalStateException("Native receipts require client thread");return runtime.receipts().stream().map(r->{var p=r.proof();var identity=acknowledgedIdentity(outcomeId,r.petUuid());return new SellingAcknowledgment(identity.outcomeId(),p.channel(),p.itemIdentity(),p.inventoryIndex(),p.quantity(),p.proceeds(),p.purseBefore(),p.purseAfter(),prefix,denomination,player,world,connection,new FinancialActionReceipt.Evidence(r.menu(),p.menuId(),p.requestRevision(),p.receiptRevision(),p.matchedReceiptSerial(),p.channel().name()));}).toList();}
    private GeorgeSaleIdentity acknowledgedIdentity(String activation,UUID petUuid){
        var identity=acknowledgedIdentities.computeIfAbsent(petUuid,id->GeorgeSaleIdentity.acknowledged(activation,id));
        if(!identity.activationUuid().toString().equals(activation))throw new IllegalStateException("Acknowledged George activation changed");
        return identity;
    }
    /** Raw source attribution is retained separately from the canonical financial outcome ID. */
    public List<GeorgeSaleIdentity> acknowledgedSourceIdentities(String activation){
        if(!client.isSameThread())throw new IllegalStateException("Native George source attribution requires client thread");
        return runtime.receipts().stream().map(r->acknowledgedIdentity(activation,r.petUuid())).toList();
    }

}
