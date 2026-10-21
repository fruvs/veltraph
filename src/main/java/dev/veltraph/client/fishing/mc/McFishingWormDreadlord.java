package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.fishing.offense.FishingOwnedHook;
import dev.veltraph.client.fishing.worm.WormDreadlordController;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.mc.McOwnedRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Source one-use Dreadlord child. Completion proves dispatch and actual saved-view restoration, not kills. */
final class McFishingWormDreadlord {
    private final Minecraft client;private final ConfigService config;private final RotationService rotation;private final Module owner;
    private WormDreadlordController flow=new WormDreadlordController();private final FishingOwnedHook hook=new FishingOwnedHook();
    private Object player,world,connection;private List<ItemStack> items=List.of(),gear=List.of();
    private int rodSlot,toolSlot;private float yaw,pitch;private boolean turn,aimed,returned,rotationOwned,toolOwned;
    private long cameraEpoch=-1,admissionEpoch=-1,leaseSerial;
    private dev.veltraph.client.service.mc.McRotationService.CleanupReceipt cameraReceipt;
    private WormCatchPacketBridge catchReceipts;
    private long reelDeadline;private boolean reeling;private String failure="";private long dispatched;
    private static final List<net.minecraft.world.entity.EquipmentSlot> GEAR=List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    McFishingWormDreadlord(Minecraft client,ConfigService config,RotationService rotation,Module owner){this.client=client;this.config=config;this.rotation=rotation;this.owner=owner;}
    private boolean context(){return client.isSameThread()&&owner!=null&&owner.isEnabled()&&McOwnedRotation.installed(client)&&McOwnedRotation.authority(client)==owner&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.gameMode!=null&&client.player.isAlive()&&client.player.level()==client.level&&!client.level.tickRateManager().isEntityFrozen(client.player)&&!client.isPaused()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()&&!client.player.isScoping()&&client.getCameraEntity()==client.player;}
    private static ItemStack stableRod(ItemStack s){var copy=s.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    private boolean dreadlord(ItemStack item){var data=item.get(DataComponents.CUSTOM_DATA);if(data==null||item.isEmpty())return false;var tag=data.copyTag();String id=tag.getString("id").orElseGet(()->tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id",""));return id.equals("CRYPT_DREADLORD_SWORD");}
    private FishingOwnedHook.Scope scope(){return new FishingOwnedHook.Scope(client.player,client.level,client.getConnection());}
    private FishingOwnedHook.Hook currentHook(){var h=client.player.fishing;return h==null?null:new FishingOwnedHook.Hook(h,h.getPlayerOwner(),h.level(),h.isAlive()&&client.level.getEntity(h.getId())==h?h.getId():-1,h.getUUID());}
    boolean start(){
        if(active()||!context()||!client.player.getMainHandItem().is(Items.FISHING_ROD))return false;
        toolSlot=-1;for(int i=0;i<9;i++)if(dreadlord(client.player.getInventory().getItem(i))){if(toolSlot>=0){failure="Ambiguous Dreadlord hotbar tools";return false;}toolSlot=i;}
        if(toolSlot<0)return false;
        rodSlot=client.player.getInventory().getSelectedSlot();player=client.player;world=client.level;connection=client.getConnection();yaw=client.player.getYRot();pitch=client.player.getXRot();turn=config.get(FishingConfig.WORM_DREADLORD_TURN);
        var snapshot=new ArrayList<ItemStack>();for(int i=0;i<36;i++)snapshot.add(i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i).copy());items=List.copyOf(snapshot);gear=GEAR.stream().map(s->client.player.getItemBySlot(s).copy()).toList();
        aimed=returned=rotationOwned=toolOwned=false;cameraEpoch=-1;cameraReceipt=null;admissionEpoch=McOwnedRotation.epoch(client);leaseSerial++;failure="";flow=new WormDreadlordController();
        if(!hook.begin(scope(),currentHook())){failure="Dreadlord native hook ownership unavailable";return false;}
        if(currentHook()!=null){if(!hook.issueReel(scope(),currentHook()))return false;
            long issued=System.currentTimeMillis();reelDeadline=issued+Math.round(config.get(FishingConfig.FISHING_CAST_TIMEOUT_SECONDS)*1000);
            var catchScope=new dev.veltraph.client.fishing.worm.WormOwnedCatch.Scope(owner,player,world,connection,leaseSerial);
            catchReceipts=WormCatchPacketBridge.begin(client,catchScope,issued,reelDeadline,rodSlot,toolSlot,client.player.fishing.position(),this::catchContext);
            client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);reeling=true;}
        else begin();return true;
    }
    private void begin(){reeling=false;var r=java.util.concurrent.ThreadLocalRandom.current();flow.start(client.player.tickCount,r.nextInt(2,21),r.nextInt(2,4),client.player.tickCount+1200);}
    private boolean catchContext(){
        if(!context()||client.player!=player||client.level!=world||client.getConnection()!=connection||turn!=config.get(FishingConfig.WORM_DREADLORD_TURN)||config.get(FishingConfig.FISHING_WORM_MODE)!=dev.veltraph.client.fishing.worm.WormHuntingMode.SOURCE_THRESHOLD||!config.get(FishingConfig.FISHING_WORM_HUNTING))return false;
        if(rotationOwned&&(!cameraCurrent()||cameraEpoch!=McOwnedRotation.epoch(client)))return false;
        if(client.player.getInventory().getSelectedSlot()!=(toolOwned?toolSlot:rodSlot))return false;
        for(int i:new int[]{rodSlot,toolSlot})if(!ItemStack.matches(items.get(i),i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i)))return false;
        for(int i=0;i<GEAR.size();i++)if(!ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return true;
    }
    private boolean reserved(){
        if(!catchContext())return false;
        // Observe current native removal exactly once before joining already-applied pickup/inventory packets.
        boolean hookCurrent=hook.observe(scope(),currentHook());
        if(!hookCurrent)return false;
        if(catchReceipts!=null){var adopted=catchReceipts.adopt(items,hook.removalObserved());if(adopted.isPresent())items=adopted.get();}
        for(int i=0;i<items.size();i++)if(!ItemStack.matches(items.get(i),i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i)))return false;
        for(int i=0;i<GEAR.size();i++)if(!ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return hookCurrent;
    }
    private boolean cameraCurrent(){return rotation instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation&&nativeRotation.currentRequest(cameraReceipt);}
    void tick(){
        if(!active())return;
        if(!reserved()){failure="Dreadlord owner, native context, configuration or loadout changed";stop();return;}
        if(reeling){if(hook.maySelectTool(scope(),currentHook()))begin();else if(System.currentTimeMillis()>reelDeadline){failure="Dreadlord reel acknowledgment expired";stop();}return;}
        var action=flow.tick(new WormDreadlordController.Frame(client.player.tickCount,true,toolOwned,aimed,returned));
        if(action!=WormDreadlordController.Action.NONE&&!reserved()){failure="Dreadlord action reservation revoked";stop();return;}
        switch(action){
            case SELECT_TOOL -> {if(!hook.maySelectTool(scope(),currentHook())){failure="Dreadlord tool selection lacks actual reel receipt";stop();break;}dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,toolSlot);client.player.getInventory().setSelectedSlot(toolSlot);toolOwned=true;}
            case AIM_BEHIND -> {
                float targetYaw=yaw,targetPitch=90;var r=java.util.concurrent.ThreadLocalRandom.current();
                if(turn){var facing=client.player.getDirection().getOpposite();var point=client.player.position().add(facing.getStepX()*1.5+r.nextDouble(-.1,.1),-2.5+r.nextDouble(-.1,.1),facing.getStepZ()*1.5+r.nextDouble(-.1,.1));var d=point.subtract(client.player.getEyePosition());targetYaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));targetPitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
                else targetYaw+=r.nextDouble(-2,2);
                rotationOwned=true;long serial=leaseSerial;var expectedFlow=flow;
                rotation.rotateTo(targetYaw,targetPitch,()->{if(leaseSerial==serial&&flow==expectedFlow&&reserved()&&flow.state()==WormDreadlordController.State.LOOK_BEHIND)aimed=true;});cameraEpoch=McOwnedRotation.epoch(client);
                cameraReceipt=rotation instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation?nativeRotation.cleanupReceipt(owner):null;
            }
            case USE_ITEM -> {if(!cameraCurrent()){failure="Dreadlord aimed camera request was superseded before use";stop();break;}client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);dispatched++;}
            case AIM_BACK -> {rotationOwned=true;long serial=leaseSerial;var expectedFlow=flow;
                rotation.rotateTo(yaw,pitch,()->{if(leaseSerial==serial&&flow==expectedFlow&&reserved()&&flow.state()==WormDreadlordController.State.LOOK_BACK)returned=true;});cameraEpoch=McOwnedRotation.epoch(client);
                cameraReceipt=rotation instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation?nativeRotation.cleanupReceipt(owner):null;}
            case RETURN_PARENT -> {if(Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()))>=1||Math.abs(pitch-client.player.getXRot())>=1){failure="Dreadlord saved view not observed at return";}release();}
            case RELEASE -> {failure="Dreadlord native lifecycle deadline or callback revoked";release();}
            default -> {}
        }
    }
    private boolean loadoutMatchesForCleanup(){
        if(client.player==null||items.size()!=36||gear.size()!=GEAR.size())return false;
        for(int i=0;i<items.size();i++)if(!ItemStack.matches(items.get(i),i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i)))return false;
        for(int i=0;i<GEAR.size();i++)if(!ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return true;
    }
    private void release(){
        // Invalidate old callbacks first; cleanup is not permission to issue any fresh gameplay action.
        leaseSerial++;aimed=returned=false;if(catchReceipts!=null){catchReceipts.close();catchReceipts=null;}
        boolean same=client.player!=null&&client.level!=null&&client.player==player&&client.level==world&&client.getConnection()==connection;
        Object actualAuthority=McOwnedRotation.authority(client);
        boolean typedAbsent=owner!=null&&actualAuthority==null&&(!owner.isEnabled()||same&&client.gui.screen()!=null);
        boolean cameraHeld=rotation instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation&&nativeRotation.ownsCleanup(cameraReceipt,typedAbsent);
        boolean admittedCleanup=typedAbsent&&admissionEpoch==McOwnedRotation.epoch(client);
        boolean held=owner!=null&&McOwnedRotation.installed(client)&&(actualAuthority==owner||typedAbsent&&cameraHeld||admittedCleanup);
        boolean menu=same&&client.player.containerMenu==client.player.inventoryMenu;
        boolean rodMatches=same&&rodSlot>=0&&rodSlot<items.size()&&ItemStack.matches(items.get(rodSlot),stableRod(client.player.getInventory().getItem(rodSlot)));
        boolean toolMatches=same&&toolSlot>=0&&toolSlot<items.size()&&ItemStack.matches(items.get(toolSlot),client.player.getInventory().getItem(toolSlot));
        var decision=dev.veltraph.client.fishing.worm.WormCleanupAuthority.decide(new dev.veltraph.client.fishing.worm.WormCleanupAuthority.Observation(
                client.isSameThread(),same,held,cameraEpoch>=0&&cameraEpoch==McOwnedRotation.epoch(client),same&&client.getCameraEntity()==client.player,
                menu,menu&&client.player.inventoryMenu.getCarried().isEmpty(),same&&client.player.isAlive(),
                same&&toolOwned&&client.player.getInventory().getSelectedSlot()==toolSlot,rodMatches,toolMatches,same&&loadoutMatchesForCleanup()));
        if(rotationOwned&&decision.cancelCamera()&&rotation instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation)nativeRotation.cancelOwned(cameraReceipt,typedAbsent);
        rotationOwned=false;cameraEpoch=-1;cameraReceipt=null;
        if(toolOwned&&decision.restoreRod()){dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,rodSlot);client.player.getInventory().setSelectedSlot(rodSlot);}
        toolOwned=false;hook.cancel();reeling=false;
    }
    void stop(){flow.cancel();release();}
    boolean active(){return reeling||flow.active();}String status(){return reeling?"REELING":flow.state().name();}String failure(){return failure;}
    long dispatchedUses(){return dispatched;}
}
