package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FishingConfig;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.fishing.FishingManaParser;
import dev.veltraph.client.fishing.offense.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual offensive source helper, using only normal native item use and observed server outcomes. */
public final class McFishingOffense {
    private final Minecraft client;
    private final ConfigService config;
    private final FarmServices services;
    private final McNavigator navigator;private final dev.veltraph.client.navigation.mc.McScopedWaterTravel waterTravel;private final McFishingTravel dryTravel;
    private final FishingOffenseController runtime=new FishingOffenseController();
    private final FishingOwnedHook ownedHook=new FishingOwnedHook();
    private Module owner;
    private FishingOffenseProfile profile;
    private String contract,names;
    private boolean ignoreSquid;
    private Object player,world,connection;
    private LivingEntity target;
    private UUID targetId,completedTarget;
    private ItemStack rod=ItemStack.EMPTY,tool=ItemStack.EMPTY;
    private List<ItemStack> inventory=List.of(),gear=List.of();
    private int rodSlot,toolSlot;
    private Vec3 anchor;
    private float yaw,pitch;
    private FishingManaParser.Mana mana;
    private long manaAt,manaRevision,abilityRevision;
    private boolean toolOwned,rotationOwned,navigationOwned,revoked;
    private boolean waterReturnPlan,returnPlanArrived;private dev.veltraph.client.world.Vec3 normalReturnGoal;
    private McNavigator.WaterTerminalHold waterHold;
    private static final List<net.minecraft.world.entity.EquipmentSlot> GEAR=List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);

    public McFishingOffense(Minecraft client,ConfigService config,FarmServices services) {
        this.client=client;this.config=config;this.services=services;navigator=new McNavigator(client,services.input(),config);waterTravel=new dev.veltraph.client.navigation.mc.McScopedWaterTravel(client,services,config);dryTravel=new McFishingTravel(client,config,services,this);
    }
    private void stopReturnTravel(){if(waterReturnPlan)waterTravel.stopChecked();else dryTravel.stop();}
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return active()&&navigationOwned&&!waterReturnPlan&&dryTravel.consumeExpectedTeleport(position,now);}
    public void bindOwner(Module owner){if(waterTravel.lifecycleRefused())throw new IllegalStateException("Foreign WATER scope cannot rebind offensive Fishing");if(active())throw new IllegalStateException("Cannot rebind live offensive Fishing owner");this.owner=Objects.requireNonNull(owner);revoked=false;runtime.reset();}
    public static String problem(ConfigService config){if(!config.get(FishingOffenseConfig.FISHING_USE_HYPERION))return "";try{FishingOffenseProfile.parse(config.get(FishingOffenseConfig.FISHING_OFFENSIVE_PROFILE));return "";}catch(RuntimeException failure){return failure.getMessage();}}
    public void mana(String message){if(revoked)return;var parsed=FishingManaParser.detailed(message);if(parsed.isPresent()){mana=parsed.get();manaAt=System.currentTimeMillis();manaRevision++;}}
    public void message(String message){if(!revoked&&active()&&targetId!=null&&plain(message).equals(profile.receipt(targetId)))abilityRevision++;}
    public boolean active(){return runtime.active();}
    public String status(){return runtime.state().name();}
    public String failure(){return runtime.failure();}
    public int acknowledgedUses(){return runtime.uses();}
    private boolean authority(){return owner!=null&&owner.isEnabled()&&McOwnedRotation.installed(client)&&McOwnedRotation.authority(client)==owner;}
    private boolean nativeContext(){return client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.gameMode!=null&&client.player.isAlive()&&client.player.level()==client.level&&!client.level.tickRateManager().isEntityFrozen(client.player)&&!client.isPaused()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()&&!client.player.isScoping()&&client.getCameraEntity()==client.player;}
    private static String plain(String value){return value.replaceAll("§.","");}
    private static ItemStack stableRod(ItemStack value){var copy=value.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    private boolean matchesProfile(ItemStack stack){var lore=stack.get(DataComponents.LORE);return !stack.isEmpty()&&profile.itemId().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())&&profile.itemName().equals(plain(stack.getHoverName().getString()))&&lore!=null&&profile.itemLore().equals(lore.lines().stream().map(line->plain(line.getString())).toList());}
    private int findTool(){int result=-1;for(int i=0;i<9;i++)if(matchesProfile(client.player.getInventory().getItem(i))){if(result>=0)return -1;result=i;}return result;}
    private LivingEntity findTarget(){
        var allowed=Arrays.stream(config.get(FishingConfig.FISHING_KILL_TARGETS).split(",")).map(String::trim).filter(v->!v.isEmpty()).toList();
        var candidates=client.level.getEntitiesOfClass(LivingEntity.class,client.player.getBoundingBox().inflate(6,6,6),e->e!=client.player&&FishingAutoKillerTargets.accepts(e,config.get(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID))&&!e.getUUID().equals(completedTarget)&&e.isAlive()&&!(e instanceof ArmorStand)&&!(e instanceof Blaze)&&profile.targetType().equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())&&allowed.contains(plain(e.getName().getString()))&&Math.hypot(e.getX()-client.player.getX(),e.getZ()-client.player.getZ())<4&&Math.abs(e.getY()-client.player.getY())<6);
        return candidates.size()==1?candidates.getFirst():null;
    }
    private boolean protectedLoadout(){
        for(int i=0;i<inventory.size();i++)if(!ItemStack.matches(inventory.get(i),i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i)))return false;
        for(int i=0;i<GEAR.size();i++)if(!ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return client.player.getInventory().getSelectedSlot()==(toolOwned?toolSlot:rodSlot)
            ||!toolOwned&&navigationOwned&&!waterReturnPlan&&runtime.state()==FishingOffenseController.State.RETURN&&dryTravel.rodSelectionCurrent(rodSlot);
    }
    private FishingOwnedHook.Scope hookScope(){return new FishingOwnedHook.Scope(client.player,client.level,client.getConnection());}
    private FishingOwnedHook.Hook currentHook(){var h=client.player.fishing;if(h==null)return null;return new FishingOwnedHook.Hook(h,h.getPlayerOwner(),h.level(),h.isAlive()&&client.level.getEntity(h.getId())==h?h.getId():-1,h.getUUID());}
    public boolean hookReelIssued(){return ownedHook.reelIssued();}
    public boolean hookRemovalObserved(){return ownedHook.removalObserved();}
    private boolean targetIdentity(){return target!=null&&target.getUUID().equals(targetId)&&target.level()==client.level&&(client.level.getEntity(target.getId())==target||!target.isAlive()&&runtime.state().ordinal()>=FishingOffenseController.State.RESTORE_SLOT.ordinal());}
    /** Each tick constructs a fresh coherent Frame; no previous tick's permission is reused for an action. */
    private FishingOffenseController.Frame frame(long now){
        boolean valid=nativeContext()&&authority()&&client.player==player&&client.level==world&&client.getConnection()==connection&&Objects.equals(contract,config.get(FishingOffenseConfig.FISHING_OFFENSIVE_PROFILE))&&Objects.equals(names,config.get(FishingConfig.FISHING_KILL_TARGETS))&&config.get(FishingOffenseConfig.FISHING_USE_HYPERION)&&ignoreSquid==config.get(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID)&&FishingAutoKillerTargets.accepts(target,ignoreSquid)&&protectedLoadout()&&ownedHook.observe(hookScope(),currentHook());
        if(!nativeContext())return new FishingOffenseController.Frame(now,false,false,false,0,0,false,false,false,false,null,manaRevision,abilityRevision,Float.NaN,0,false,false,false,false,false);
        boolean targetSame=targetIdentity();
        double horizontal=targetSame?Math.hypot(target.getX()-client.player.getX(),target.getZ()-client.player.getZ()):Double.POSITIVE_INFINITY;
        double vertical=targetSame?Math.abs(target.getY()-client.player.getY()):Double.POSITIVE_INFINITY;
        var eyes=client.player.getEyePosition();var hit=client.level.clip(new ClipContext(eyes,eyes.add(0,-.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        boolean clear=hit.getType()==HitResult.Type.MISS&&!client.player.getCooldowns().isOnCooldown(client.player.getInventory().getItem(toolSlot));
        return new FishingOffenseController.Frame(now,valid,targetSame,targetSame&&target.isAlive(),horizontal,vertical,client.player.fishing!=null,
                ItemStack.matches(tool,client.player.getInventory().getItem(toolSlot)),toolOwned&&client.player.getInventory().getSelectedSlot()==toolSlot,
                mana!=null&&now>=manaAt&&now-manaAt<=2000,mana==null?null:mana.current(),manaRevision,abilityRevision,targetSame?target.getHealth():Float.NaN,client.player.getXRot(),clear,!toolOwned&&client.player.getInventory().getSelectedSlot()==rodSlot,
                returnPlanArrived&&returnReady()&&anchor!=null&&client.player.position().distanceTo(anchor)<.2&&client.player.getDeltaMovement().horizontalDistance()<.05,
                Math.abs(WalkController.wrap(yaw-client.player.getYRot()))<1&&Math.abs(pitch-client.player.getXRot())<1,returnReady());
    }
    public boolean groundReturnStarted(){return navigationOwned;}
    public String returnStatus(){return waterReturnPlan?waterTravel.diagnostics():dryTravel.diagnostics();}
    public boolean footingReadyForReturn(){return nativeContext()&&returnReady();}
    private static boolean nativeHazard(net.minecraft.world.level.block.state.BlockState s){return !s.getFluidState().isEmpty()||s.is(net.minecraft.world.level.block.Blocks.FIRE)||s.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)||s.is(net.minecraft.world.level.block.Blocks.COBWEB)||s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)||s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)||s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||s.is(net.minecraft.world.level.block.Blocks.CACTUS)||s.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||s.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);}
    private boolean hasWaterBody(Vec3 position){
        if(position==null||client.player==null||client.level==null)return false;
        var box=new net.minecraft.world.phys.AABB(position.x-.3,position.y+.001,position.z-.3,position.x+.3,position.y+client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height(),position.z+.3);
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int y=(int)Math.floor(box.minY);y<=Math.floor(box.maxY);y++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++)if(client.level.getFluidState(new net.minecraft.core.BlockPos(x,y,z)).is(net.minecraft.tags.FluidTags.WATER))return true;
        return false;
    }
    private FishingReturnMedium returnMedium(){
        if(!nativeContext()||anchor==null)return FishingReturnMedium.WAIT;
        var p=client.player;var current=new dev.veltraph.client.world.Vec3(p.getX(),p.getY(),p.getZ());var goal=new dev.veltraph.client.world.Vec3(anchor.x,anchor.y,anchor.z);
        return FishingReturnMedium.select(profile!=null&&profile.supportedWaterReturn(),true,p.onGround(),p.isInLava(),p.isUnderWater(),p.getDeltaMovement().y,p.getDeltaMovement().horizontalDistance(),
                navigator.standingClear(current),navigator.standingClear(goal),navigator.standingClear(current,true),navigator.standingClear(goal,true),p.isInWater()||hasWaterBody(p.position())||hasWaterBody(anchor));
    }
    private boolean returnReady(){return returnMedium()!=FishingReturnMedium.WAIT;}
    public String returnMediumName(){return returnMedium().name();}
    /** Read-only current native geometry; no navigator start, owner installation or block mutation. */
    public Map<String,Object> footingDetails(){
        var out=new LinkedHashMap<String,Object>();out.put("nativeContext",nativeContext());out.put("returnPlanArrived",returnPlanArrived);out.put("waterReturnPlan",waterReturnPlan);out.put("waterTerminalHoldRetained",waterHold!=null);out.put("originalReturnDeadlineMillis",runtime.deadlineMillis());out.put("returnMedium",returnMedium().name());out.put("waterDeclared",profile!=null&&profile.supportedWaterReturn());
        if(client.player==null||client.level==null)return out;
        var p=client.player;var position=p.position();var point=new dev.veltraph.client.world.Vec3(position.x,position.y,position.z);int ceiling=dev.veltraph.client.navigation.GroundPathfinder.node(point).y();
        out.put("onGround",p.onGround());out.put("isInWater",p.isInWater());out.put("isInLava",p.isInLava());out.put("isUnderWater",p.isUnderWater());out.put("verticalSpeed",p.getDeltaMovement().y);out.put("horizontalSpeed",p.getDeltaMovement().horizontalDistance());out.put("verticalSettled",Math.abs(p.getDeltaMovement().y)<.08);out.put("horizontalSettled",p.getDeltaMovement().horizontalDistance()<.05);out.put("bodyHasNativeWater",hasWaterBody(position));out.put("anchorHasNativeWater",hasWaterBody(anchor));
        out.put("standingClearDry",navigator.standingClear(point));out.put("standingClearWater",navigator.standingClear(point,true));out.put("bodyClearDry",navigator.bodyClear(point,false));out.put("bodyClearWater",navigator.bodyClear(point,true));out.put("supportCeiling",ceiling);
        var corners=new ArrayList<Map<String,Object>>();
        for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3}){int x=(int)Math.floor(position.x+dx),z=(int)Math.floor(position.z+dz);var pos=new net.minecraft.core.BlockPos(x,ceiling-1,z);var state=client.level.getBlockState(pos);var corner=new LinkedHashMap<String,Object>();corner.put("cell",List.of(x,ceiling-1,z));corner.put("loaded",client.level.getChunkSource().hasChunk(x>>4,z>>4));corner.put("state",state.toString());corner.put("dryHazard",nativeHazard(state));corner.put("fluidEmpty",state.getFluidState().isEmpty());corner.put("water",state.getFluidState().is(net.minecraft.tags.FluidTags.WATER));corner.put("boxes",state.getCollisionShape(client.level,pos).toAabbs().stream().map(b->List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList());double top=Double.NaN;for(var b:state.getCollisionShape(client.level,pos).toAabbs())if(position.x+dx-x>=b.minX&&position.x+dx-x<=b.maxX&&position.z+dz-z>=b.minZ&&position.z+dz-z<=b.maxZ)top=Double.isNaN(top)?pos.getY()+b.maxY:Math.max(top,pos.getY()+b.maxY);corner.put("observedSupportTop",Double.isFinite(top)?top:null);corner.put("withinSourceHeightBand",Double.isFinite(top)&&top<=ceiling+.001&&top>=ceiling-1+.001);corners.add(corner);}
        out.put("supportCorners",corners);var body=new net.minecraft.world.phys.AABB(position.x-.3,position.y+.001,position.z-.3,position.x+.3,position.y+p.getDimensions(net.minecraft.world.entity.Pose.STANDING).height(),position.z+.3);out.put("nativeNoCollision",client.level.noCollision(p,body));out.put("nativeBodyBounds",List.of(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ));var cells=new ArrayList<Map<String,Object>>();
        for(int x=(int)Math.floor(body.minX);x<=Math.floor(body.maxX);x++)for(int y=(int)Math.floor(body.minY);y<=Math.floor(body.maxY);y++)for(int z=(int)Math.floor(body.minZ);z<=Math.floor(body.maxZ);z++){var pos=new net.minecraft.core.BlockPos(x,y,z);var state=client.level.getBlockState(pos);cells.add(Map.of("cell",List.of(x,y,z),"loaded",client.level.getChunkSource().hasChunk(x>>4,z>>4),"state",state.toString(),"fluidEmpty",state.getFluidState().isEmpty(),"water",state.getFluidState().is(net.minecraft.tags.FluidTags.WATER),"collisionEmpty",state.getCollisionShape(client.level,pos).isEmpty(),"dryHazard",nativeHazard(state)));}
        out.put("bodyCells",cells);if(anchor!=null){var goal=new dev.veltraph.client.world.Vec3(anchor.x,anchor.y,anchor.z);out.put("savedAnchor",List.of(anchor.x,anchor.y,anchor.z));out.put("anchorStandingClearDry",navigator.standingClear(goal));out.put("anchorStandingClearWater",navigator.standingClear(goal,true));}return out;
    }
    private long pacing(){return java.util.concurrent.ThreadLocalRandom.current().nextLong(profile.pacingMinMillis(),profile.pacingMaxMillis()+1);}
    public boolean tick(){return tick(true);}
    public boolean tick(boolean allowStart){try{return tickWaterPublicationFence(allowStart);}catch(dev.veltraph.client.navigation.WaterPublicationRevokedException refused){waterTravel.refuseLifecycle();revoked=true;runtime.cancel();navigationOwned=false;waterHold=null;returnPlanArrived=false;waterReturnPlan=false;rotationOwned=false;return false;}}
    private boolean tickWaterPublicationFence(boolean allowStart){
        if(waterTravel.lifecycleRefused()){waterTravel.refuseLifecycle();return false;}
        if(revoked)return false;
        long now=System.currentTimeMillis();
        if(!active()) {
            if(!allowStart)return false;
            if(!failure().isEmpty())throw new IllegalStateException(failure());
            if(!config.get(FishingOffenseConfig.FISHING_USE_HYPERION)||!nativeContext()||!authority()||!client.player.getMainHandItem().is(Items.FISHING_ROD))return false;
            profile=FishingOffenseProfile.parse(config.get(FishingOffenseConfig.FISHING_OFFENSIVE_PROFILE));toolSlot=findTool();target=findTarget();if(toolSlot<0||toolSlot==client.player.getInventory().getSelectedSlot()||target==null)return false;
            targetId=target.getUUID();player=client.player;world=client.level;connection=client.getConnection();contract=config.get(FishingOffenseConfig.FISHING_OFFENSIVE_PROFILE);names=config.get(FishingConfig.FISHING_KILL_TARGETS);ignoreSquid=config.get(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID);
            returnPlanArrived=false;waterReturnPlan=false;waterHold=null;
            rodSlot=client.player.getInventory().getSelectedSlot();rod=stableRod(client.player.getMainHandItem());tool=client.player.getInventory().getItem(toolSlot).copy();anchor=client.player.position();yaw=client.player.getYRot();pitch=client.player.getXRot();
            inventory=new ArrayList<>();for(int i=0;i<36;i++)inventory.add(i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i).copy());gear=GEAR.stream().map(slot->client.player.getItemBySlot(slot).copy()).toList();
            if(!ownedHook.begin(hookScope(),currentHook()))return false;
            if(!runtime.start(frame(now),profile,pacing())){ownedHook.cancel();return false;}
        }
        var observation=frame(now);var action=runtime.tick(observation,pacing());
        if(action!=FishingOffenseController.Action.NONE&&action!=FishingOffenseController.Action.RELEASE&&!frame(now).valid()) {runtime.failNative("Offensive Fishing action lost its native reservation");release();throw new IllegalStateException(failure());}
        switch(action){
            case REEL -> {if(!ownedHook.issueReel(hookScope(),currentHook())){runtime.failNative("Offensive reel lacks the exact current player-owned native hook");break;}client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);}
            case SELECT -> {if(!ownedHook.maySelectTool(hookScope(),currentHook())){runtime.failNative("Offensive tool selection preceded actual owned reel acknowledgment");break;}client.player.getInventory().setSelectedSlot(toolSlot);toolOwned=true;}
            case AIM_DOWN -> {services.rotation().track(yaw,90);rotationOwned=true;}
            case USE -> client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
            case RESTORE_SLOT -> {if(!restoreSlot())runtime.failNative("Offensive Fishing rod restoration failed");}
            case WAIT_FOOTING -> {services.input().releaseAllMovement();if(rotationOwned&&McOwnedRotation.authority(client)==owner)services.rotation().cancelRotation();rotationOwned=false;}
            case RETURN -> {
                if(waterHold!=null){holdWaterAndRestoreView();break;}
                // Ordinary travel controls heading until its actual strict terminal arrival receipt.
                if(rotationOwned&&McOwnedRotation.authority(client)==owner)services.rotation().cancelRotation();rotationOwned=false;
                if(!navigationOwned){var medium=returnMedium();if(medium==FishingReturnMedium.WAIT){runtime.failNative("Offensive Fishing return footing changed before plan");break;}navigationOwned=true;waterReturnPlan=medium==FishingReturnMedium.SUPPORTED_WATER;returnPlanArrived=false;var destination=new dev.veltraph.client.world.Vec3(anchor.x,anchor.y,anchor.z);normalReturnGoal=destination;boolean accepted=waterReturnPlan?waterTravel.startWater(owner,destination,.18):dryTravel.start(normalReturnGoal,dev.veltraph.client.fishing.FishingTravelIntent.OFFENSE_DRY_RETURN);if(!accepted)runtime.failNative("Offensive Fishing saved spot is unavailable: "+returnStatus());}
                else {var result=waterReturnPlan?waterTravel.tick():dryTravel.tick();if(result==WalkController.State.BLOCKED)runtime.failNative("Offensive Fishing saved spot return blocked");else if(result==WalkController.State.ARRIVED){
                    returnPlanArrived=true;
                    if(waterReturnPlan){waterHold=waterTravel.retainWaterTerminalHold(owner,runtime.deadlineMillis()).orElse(null);if(waterHold==null)runtime.failNative("Offensive Fishing strict water arrival could not retain its original terminal lease");}
                }}
            }
            case RESTORE_VIEW -> {
                if(waterReturnPlan){holdWaterAndRestoreView();break;}
                if(navigationOwned){stopReturnTravel();navigationOwned=false;}services.rotation().track(yaw,pitch);rotationOwned=true;
            }
            case RELEASE -> release();
            default -> {}
        }
        if(!failure().isEmpty()){release();throw new IllegalStateException(failure());}
        if(runtime.state()==FishingOffenseController.State.COMPLETE)completedTarget=targetId;
        return true;
    }
    private void holdWaterAndRestoreView(){
        if(!navigationOwned||!returnPlanArrived||waterHold==null||waterTravel.tickWaterTerminalHold(waterHold,owner)==WalkController.State.BLOCKED){runtime.failNative("Offensive Fishing retained water terminal lease was revoked");return;}
        // Movement uses the observed camera yaw; only this family trajectory restores the saved view.
        if(!waterTravel.trackWaterTerminalView(waterHold,owner,yaw,pitch)){runtime.failNative("Offensive Fishing saved view lost its WATER publication lineage");return;}rotationOwned=false;
    }
    private boolean restoreSlot(){
        if(waterTravel.lifecycleRefused()&&!waterTravel.parentLifecycleCurrent(owner,true))return false;
        if(!toolOwned)return true;
        if(!nativeContext()||McOwnedRotation.authority(client)!=owner||client.player!=player||client.level!=world||client.getConnection()!=connection||!protectedLoadout()||!ItemStack.matches(rod,stableRod(client.player.getInventory().getItem(rodSlot))))return false;
        client.player.getInventory().setSelectedSlot(rodSlot);toolOwned=false;return true;
    }
    private void release(){
        if(waterTravel.lifecycleRefused()&&!waterTravel.parentLifecycleCurrent(owner,false)){waterTravel.refuseLifecycle();waterTravel.stop();navigationOwned=false;waterHold=null;returnPlanArrived=false;waterReturnPlan=false;rotationOwned=false;return;}
        restoreSlot();
        if(navigationOwned){stopReturnTravel();navigationOwned=false;}
        waterHold=null;returnPlanArrived=false;waterReturnPlan=false;
        if(rotationOwned&&McOwnedRotation.authority(client)==owner)services.rotation().cancelRotation();rotationOwned=false;
    }
    public void stop(){revoked=true;runtime.cancel();ownedHook.cancel();try{release();}catch(dev.veltraph.client.navigation.WaterPublicationRevokedException refused){navigationOwned=false;waterHold=null;returnPlanArrived=false;waterReturnPlan=false;rotationOwned=false;}finally{mana=null;manaAt=0;}}
    /** Only a new parent enable/bind may rearm a cancelled lease. */
    public void startSession(){if(waterTravel.lifecycleRefused())throw new IllegalStateException("Foreign WATER retirement cannot start another offensive Fishing job");if(active())throw new IllegalStateException("Offensive Fishing lease already active");revoked=false;runtime.reset();ownedHook.clear();completedTarget=null;mana=null;manaAt=0;}
    public boolean idleForPause(){return !active()&&!toolOwned&&!navigationOwned&&!rotationOwned;}
    public boolean rebindIdle(){if(!idleForPause())return false;runtime.reset();mana=null;manaAt=0;player=world=connection=null;target=null;return true;}
    McFishingTravel.NativeCleanupSource captureNativeCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return dryTravel.captureNativeCleanupSource(exact);}
    McFishingTravel.NativeReturnSource captureNativeReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return dryTravel.captureNativeReturnSource(exact);}
    Object nativeReturnProfileIdentity(){return profile;}
    Object nativeReturnRuntimeIdentity(){return runtime;}
    int nativeReturnRodSlot(){return rodSlot;}
    Object nativeReturnCustodyIdentity(){return rod;}
    Object nativeReturnAnchorIdentity(){return anchor;}
    boolean nativeReturnSourceCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(client.player==null||client.level==null||rodSlot<0||rodSlot>8||inventory.size()!=36||gear.size()!=GEAR.size())return false;
        if(!ItemStack.matches(rod,stableRod(client.player.getInventory().getItem(rodSlot))))return false;
        for(int i=0;i<36;i++)if(!ItemStack.matches(inventory.get(i),i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i)))return false;
        for(int i=0;i<GEAR.size();i++)if(!ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return nativeReturnSourceRawCurrent(exact,intent,goal,starting);
    }
    /** Stored private phase/source fields only; no stack comparison or codec visitor. */
    boolean nativeReturnSourceRawCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        return exact==dryTravel&&intent==dev.veltraph.client.fishing.FishingTravelIntent.OFFENSE_DRY_RETURN&&goal==normalReturnGoal&&normalReturnGoal!=null
            &&navigationOwned&&!waterReturnPlan&&waterHold==null&&!returnPlanArrived&&!toolOwned&&!revoked&&runtime.state()==FishingOffenseController.State.RETURN
            &&runtime.failure().isEmpty()&&owner!=null&&profile!=null&&contract!=null&&names!=null&&client.player==player&&client.level==world
            &&client.getConnection()==connection&&anchor!=null&&rodSlot>=0&&rodSlot<9&&inventory.size()==36&&gear.size()==GEAR.size()
            &&rod.is(Items.FISHING_ROD);
    }
}
