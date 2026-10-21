package dev.veltraph.client.mining.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.mining.MiningServices;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Checks live vanilla survival state; never writes position, velocity, gamemode, blocks or inventory. */
public final class McMiningServices implements MiningServices {
    private final Minecraft client;
    private final FarmServices farm;
    private final McNavigator navigation;
    private final dev.veltraph.client.mining.crafting.CobbleCraftStorePort craftStore;
    private final VeinCollectionMovement collectionMovement;
    private BlockPos destination;
    private dev.veltraph.client.mining.vein.VeinCollectionCleanupDispatch.Outcome<VeinCollectionMovement.CleanupScope> collectionCleanup;
    private int originalSlot=-1, toolSlot=-1;
    private net.minecraft.client.player.LocalPlayer toolOwner;
    private net.minecraft.client.multiplayer.ClientLevel toolWorld;
    private static final class ScheduledLoan {
        final Object ticket;final java.util.UUID playerId;final String dimension;final dev.veltraph.client.world.Vec3 position;
        final float yaw,pitch;final net.minecraft.world.entity.Pose posture;final int slot,originalSlot;
        final BlockPos column;final String support;
        final dev.veltraph.client.inventory.NativeStackIdentity.Snapshot tool;String boundIdentity;boolean released;
        ScheduledLoan(Object ticket,Minecraft c,int originalSlot,String identity,BlockPos column,String support){
            this.ticket=ticket;this.column=column;this.support=support;playerId=c.player.getUUID();dimension=c.level.dimension().identifier().toString();
            position=new dev.veltraph.client.world.Vec3(c.player.getX(),c.player.getY(),c.player.getZ());
            yaw=c.player.getYRot();pitch=c.player.getXRot();posture=c.player.getPose();slot=c.player.getInventory().getSelectedSlot();
            tool=dev.veltraph.client.inventory.NativeStackIdentity.capture(c.player.getMainHandItem(),c.level.registryAccess());this.originalSlot=originalSlot;boundIdentity=identity;
        }
    }
    private ScheduledLoan scheduledLoan;
    private dev.veltraph.client.mining.module.MiningModule useOwner;
    private final dev.veltraph.client.mining.RootUseKeyEffects<net.minecraft.client.KeyMapping> useEffects=
            new dev.veltraph.client.mining.RootUseKeyEffects<>(dev.veltraph.client.service.mc.McHeldKeys::setHeld,net.minecraft.client.KeyMapping::isDown);
    /** Cobblestone alone acquires; a stale controller cannot release a newer input owner. */
    @Override public boolean setUseHeld(Object caller,int activation,boolean held) {
        var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        var current=manager==null?null:manager.inputOwner().orElse(null);
        if(held) {
            if(!available() || scheduledLoan!=null || craftStore.active()
                    || !(current instanceof dev.veltraph.client.mining.module.MiningModule mining)
                    || mining.miningMode()!=dev.veltraph.client.mining.MiningMode.COBBLESTONE
                    || !dev.veltraph.client.mining.module.MiningModule.COBBLESTONE_ID.equals(mining.id())
                    || !dev.veltraph.client.input.ScriptingInputLock.isActive()
                    || !dev.veltraph.client.mining.MiningUseHoldLease.mayAcquire(caller,activation,mining,mining.enableRuns(),manager.get(mining.id()).orElse(null),
                        dev.veltraph.client.input.ScriptingInputLock.ownerModuleId(),dev.veltraph.client.service.mc.McOwnedRotation.authority(client))){
                useEffects.retireOwned(caller,activation);return false;
            }
            if(!useEffects.acquire(new dev.veltraph.client.mining.MiningUseHoldLease(mining,mining.enableRuns(),client.player,client.level,client.gameMode,mining.id()),client.options.keyUse))return false;
            useOwner=mining;return true;
        }
        if(useOwner==null)return true;
        if(!useEffects.ownedBy(caller,activation))return false;
        boolean released=useEffects.release(current,useOwner.enableRuns(),client.player,client.level,client.gameMode,
                manager==null?null:manager.get(useOwner.id()).orElse(null),
                dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=null&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=useOwner
                        ? "foreign_camera_authority" : dev.veltraph.client.input.ScriptingInputLock.isActive()?dev.veltraph.client.input.ScriptingInputLock.ownerModuleId():null);
        if(!useEffects.hasLease())useOwner=null;
        return released;
    }
    @Override public boolean ownedUseHeld(Object caller,int activation){return useEffects.ownedHeld(caller,activation);}
    public McMiningServices(Minecraft client,FarmServices farm,ConfigService config) {
        this.client=client;this.farm=farm;navigation=new McNavigator(client,farm.input(),config);collectionMovement=new VeinCollectionMovement(client,this,farm,config);craftStore=new dev.veltraph.client.mining.crafting.McCobbleCraftStore(client,farm,config,()->scheduledLoan==null);
    }
    @Override public java.util.Optional<dev.veltraph.client.mining.crafting.CobbleCraftStorePort> craftStorePort(){return java.util.Optional.of(craftStore);}
    @Override public FarmServices farm(){return farm;}
    @Override public boolean available(){return client.player!=null && client.level!=null && client.gameMode!=null
            && client.gui.screen()==null && client.player.isAlive() && !client.player.isSpectator();}
    @Override public String worldIdentity(){return client.level==null?"unavailable":System.identityHashCode(client.level)+":"+System.identityHashCode(client.player)+":"+System.identityHashCode(client.gameMode);}
    private static net.minecraft.core.BlockPos mc(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(BlockPos p){return client.level!=null && client.level.isInWorldBounds(mc(p)) && client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    @Override public String selectTool(BlockPos target,int reserve) {
        if(scheduledLoan!=null||craftStore.active())return "Scheduled pause or craft/store owns mining resources";
        if(!loaded(target)||client.player==null)return "Tool check unavailable";
        var state=client.level.getBlockState(mc(target));
        int best=-1;float speed=-1;
        for(int i=0;i<9;i++) {
            var item=client.player.getInventory().getItem(i);
            if(!item.is(ItemTags.PICKAXES) || (state.requiresCorrectToolForDrops() && !item.isCorrectToolForDrops(state)))continue;
            if(item.isDamageableItem() && item.getMaxDamage()-item.getDamageValue()<=reserve)continue;
            float candidate=item.getDestroySpeed(state);
            if(candidate>speed){best=i;speed=candidate;}
        }
        if(best<0)return "No durable harvest-capable hotbar pickaxe";
        if(originalSlot<0){originalSlot=farm.inventory().selectedSlot();toolOwner=client.player;toolWorld=client.level;}
        toolSlot=best;if(farm.inventory().selectedSlot()!=best)farm.inventory().selectSlot(best);return "";
    }
    @Override public boolean heldToolReady(BlockPos target,int reserve) {
        if(craftStore.active()||!available() || !loaded(target))return false;
        var state=client.level.getBlockState(mc(target));var item=client.player.getMainHandItem();
        return item.is(ItemTags.PICKAXES) && (!state.requiresCorrectToolForDrops() || item.isCorrectToolForDrops(state))
                && (!item.isDamageableItem() || item.getMaxDamage()-item.getDamageValue()>reserve);
    }
    @Override public void cleanup() {
        if(scheduledLoan!=null||craftStore.active())return; // Only exact cancellation may release a paused family's resource lease.
        stopWalking();
        if(collectionCleanup!=null&&collectionCleanup.route()==dev.veltraph.client.mining.vein.VeinCollectionCleanupDispatch.Route.TYPED){
            // The actual Leg has already accounted for owned keys/camera. No raw legacy effect
            // may run after typed retirement, including a refusal or foreign supersession.
            if(collectionCleanup.typed()!=null)collectionCleanup.typed().cleanupRemaining();
            originalSlot=-1;toolSlot=-1;toolOwner=null;toolWorld=null;return;
        }
        farm.breaking().setBreaking(false);farm.input().releaseAllMovement();farm.rotation().cancelRotation();
        // Preserve a subsequent manual selection; restore only the slot still owned by this workflow.
        if(originalSlot>=0 && client.player==toolOwner && client.level==toolWorld
                && farm.inventory().selectedSlot()==toolSlot)farm.inventory().selectSlot(originalSlot);
        originalSlot=-1;toolSlot=-1;toolOwner=null;toolWorld=null;
    }
    @Override public boolean hasCapacity(BlockPos target) {
        if(client.player==null || !loaded(target))return false;
        // Known single-material blocks can merge. Ore/random/multiple drops require an empty slot.
        var state=client.level.getBlockState(mc(target));
        var item=state.is(Blocks.STONE)||state.is(Blocks.COBBLESTONE)?Items.COBBLESTONE:
                state.is(Blocks.DEEPSLATE)||state.is(Blocks.COBBLED_DEEPSLATE)?Items.COBBLED_DEEPSLATE:null;
        if(client.player.getMainHandItem().isEnchanted())item=null; // Silk Touch/fortune may change drops.
        for(int i=0;i<36;i++) {
            var stack=client.player.getInventory().getItem(i);
            if(stack.isEmpty())return true;
            if(item!=null && net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack,new net.minecraft.world.item.ItemStack(item))
                    && stack.getCount()<stack.getMaxStackSize())return true;
        }
        return false;
    }
    @Override public boolean hasCobbleRegenerationCapacity(BlockPos removed) {
        if(client.player==null||!loaded(removed)||!client.level.getBlockState(mc(removed)).isAir())return false;
        var rows=new java.util.ArrayList<dev.veltraph.client.mining.CobbleDropCapacity.Cell>();
        var unit=new net.minecraft.world.item.ItemStack(Items.COBBLESTONE);
        for(int i=0;i<36;i++){
            var stack=client.player.getInventory().getItem(i);
            rows.add(new dev.veltraph.client.mining.CobbleDropCapacity.Cell(stack.isEmpty(),
                    !stack.isEmpty()&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack,unit),stack.getCount(),stack.getMaxStackSize()));
        }
        // Enchantments can change the drop identity; preserve the generic path's
        // empty-slot-only rule rather than assuming cobblestone can merge.
        return dev.veltraph.client.mining.CobbleDropCapacity.available(client.player.getMainHandItem().isEnchanted(),rows);
    }
    private boolean hazardous(BlockPos pos) {
        if(!loaded(pos))return true;
        var s=client.level.getBlockState(mc(pos));
        return !s.getFluidState().isEmpty() || s.is(Blocks.FIRE)||s.is(Blocks.SOUL_FIRE)
                || s.is(Blocks.MAGMA_BLOCK)||s.is(Blocks.CACTUS)||s.is(Blocks.CAMPFIRE)
                || s.is(Blocks.SOUL_CAMPFIRE)||s.is(Blocks.POWDER_SNOW)||s.is(Blocks.SWEET_BERRY_BUSH);
    }
    private boolean wall(BlockPos p) {
        if(!loaded(p)||hazardous(p))return false;
        var boxes=client.level.getBlockState(mc(p)).getCollisionShape(client.level,mc(p)).toAabbs();
        if(boxes.size()!=1)return false;var b=boxes.getFirst();
        return b.minX==0 && b.minY==0 && b.minZ==0 && b.maxX==1 && b.maxY==1 && b.maxZ==1;
    }
    /** Explicit local sealed generator geometry, not an original native design/permission bypass. */
    private dev.veltraph.client.world.Direction sealedGenerator(BlockPos target,boolean floor) {
        if(floor || !client.level.getBlockState(mc(target)).is(Blocks.COBBLESTONE)
                || farm.player().blockPos().y()<target.y()+1
                || !navigation.floor(farm.player().blockPos().down()))return null;
        var support=target.down();
        if(!loaded(support) || !(wall(support) || client.level.getBlockState(mc(support)).is(Blocks.HOPPER)))return null;
        for(var water:java.util.List.of(dev.veltraph.client.world.Direction.NORTH,dev.veltraph.client.world.Direction.EAST,
                dev.veltraph.client.world.Direction.SOUTH,dev.veltraph.client.world.Direction.WEST)) {
            var w=target.offset(water);var lava=target.offset(water.opposite());var source=w.offset(water);
            if(!loaded(w)||!loaded(lava)||!loaded(source)||!loaded(w.down())
                    || !client.level.getBlockState(mc(w)).is(Blocks.WATER)
                    || !client.level.getBlockState(mc(w.down())).is(Blocks.WATER)
                    || !client.level.getBlockState(mc(source)).is(Blocks.WATER)
                    || !client.level.getBlockState(mc(source)).getFluidState().isSource()
                    || !client.level.getBlockState(mc(lava)).is(Blocks.LAVA)
                    || !client.level.getBlockState(mc(lava)).getFluidState().isSource())continue;
            var side=water.rotateYClockwise();
            boolean closed=wall(source.offset(water)) && wall(lava.offset(water.opposite()))
                    && wall(source.down()) && wall(lava.down()) && wall(w.down(2));
            for(var channel:java.util.List.of(target,w,source,lava))
                closed &= wall(channel.offset(side)) && wall(channel.offset(side.opposite()));
            closed &= wall(w.down().offset(side)) && wall(w.down().offset(side.opposite()));
            if(closed)return water;
        }
        return null;
    }
    @Override public String targetProblem(BlockPos target,boolean floor) {
        if(!available() || !loaded(target))return "Target is outside loaded world";
        var pos=mc(target);var state=client.level.getBlockState(pos);
        if(state.isAir() || state.getDestroySpeed(client.level,pos)<0 || state.getBlock() instanceof FallingBlock)
            return "Target is air, unbreakable or a falling block";
        BlockEntity entity=client.level.getBlockEntity(pos);
        if(entity!=null)return "Target contains stored data/inventory";
        if(client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos))>client.player.blockInteractionRange())
            return "Target out of survival reach";
        var generator=sealedGenerator(target,floor);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            var adjacent=target.add(dx,dy,dz);
            if(!loaded(adjacent))return "Target surroundings unloaded";
            if(hazardous(adjacent)) {
                boolean sealedFeed=generator!=null && (adjacent.equals(target.offset(generator))
                        || adjacent.equals(target.offset(generator).down()) || adjacent.equals(target.offset(generator.opposite())));
                if(!sealedFeed)return "Fluid or damaging block beside target";
            }
        }
        if(client.level.getBlockState(pos.above()).getBlock() instanceof FallingBlock)return "Falling block above target";
        if(floor && (!navigation.floor(target.down()) || !loaded(target.down().down())))return "No safe one-block landing beneath target";
        var playerFloor=farm.player().blockPos().down();
        if(!floor && target.equals(playerFloor))return "Refusing unsupported floor removal";
        return "";
    }
    @Override public boolean aimedAt(BlockPos target) {
        return client.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(mc(target));
    }
    @Override public boolean safeStep(boolean forward) {
        if(!available())return false;
        var p=farm.player().position();var direction=farm.player().facing();if(!forward)direction=direction.opposite();
        return navigation.walkClear(p,new dev.veltraph.client.world.Vec3(p.x()+direction.dx()*dev.veltraph.client.mining.CobbleEarlyWallReversal.STEP,p.y(),p.z()+direction.dz()*dev.veltraph.client.mining.CobbleEarlyWallReversal.STEP));
    }
    @Override public boolean endWall(boolean forward) {
        var dir=farm.player().facing();if(!forward)dir=dir.opposite();
        var at=farm.player().blockPos().offset(dir);
        if(loaded(at)&&loaded(at.up()) && farm.world().isBlock(at,"minecraft:cobblestone") && farm.world().isBlock(at.up(),"minecraft:cobblestone"))return true;
        if(!available())return false;
        var dimensions=client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        return dev.veltraph.client.mining.CobbleEarlyWallReversal.blockedByEndWall(farm.player().position(),dir,
                dimensions.width()/2.0,dimensions.height(),new dev.veltraph.client.mining.CobbleEarlyWallReversal.Terrain(){
                    public boolean walkClear(dev.veltraph.client.world.Vec3 from,dev.veltraph.client.world.Vec3 to){return navigation.walkClear(from,to);}
                    public boolean supportedCobbleColumn(BlockPos cell){return loaded(cell)&&loaded(cell.up())&&loaded(cell.down())
                            &&farm.world().isBlock(cell,"minecraft:cobblestone")&&farm.world().isBlock(cell.up(),"minecraft:cobblestone")
                            &&wall(cell)&&wall(cell.up())&&navigation.floor(cell.down());}
                });
    }
    @Override public boolean walkTo(BlockPos standing) {
        if(scheduledLoan!=null||craftStore.active()||collectionMovement.typedCleanup())return false;
        if(!standing.equals(destination)) {stopWalking();if(!navigation.start(standing))return false;destination=standing;}
        var state=navigation.tick();
        return state!=WalkController.State.BLOCKED;
    }
    @Override public void collectDrops(BlockPos removed) {
        if(scheduledLoan!=null||craftStore.active())return;
        if(removed==null || !available() || !client.player.onGround()) { stopWalking();return; }
        // A bounded, ordinary walk only when the removed block is a supported empty standing cell.
        if(Math.abs(farm.player().position().y()-removed.y())>.12
                || farm.player().position().horizontalDistanceTo(new dev.veltraph.client.world.Vec3(removed.x()+.5,removed.y(),removed.z()+.5))>2.5
                || !navigation.standable(removed)) {stopWalking();return;}
        walkTo(removed);
    }
    /** Existing supported-source branch stays first; only attributed roofed sources gain alternatives. */
    public void collectDrops(BlockPos removed,dev.veltraph.client.mining.vein.VeinNativePickupAdapter pickup){
        if(scheduledLoan!=null||craftStore.active())return;
        if(removed==null||pickup==null||!available()||!client.player.onGround()
            ||Math.abs(farm.player().position().y()-removed.y())>.12
            ||farm.player().position().horizontalDistanceTo(new dev.veltraph.client.world.Vec3(removed.x()+.5,removed.y(),removed.z()+.5))>2.5){stopWalking();return;}
        if(!collectionMovement.typedCleanup()&&navigation.standable(removed)){collectDrops(removed);return;}
        // An already typed service admits any new supported source through a fresh real Leg.
        // Its old cleanup outcome grants neither raw navigation nor a new gameplay admission.
        collectionMovement.tick(removed,pickup);
    }
    void collectionDestination(BlockPos actualStanding){destination=actualStanding;}
    boolean collectionDestinationIs(BlockPos actualStanding){return actualStanding.equals(destination);}
    public void retireTakenCollection(dev.veltraph.client.mining.vein.VeinNativePickupAdapter exact){collectionMovement.retireTaken(exact);}
    public boolean collectionSourceRevoked(){return collectionMovement.sourceRevoked();}
    public boolean collectionFailed(dev.veltraph.client.mining.vein.VeinNativePickupAdapter exact){return collectionMovement.failedFor(exact);}
    public Object collectionTravelSnapshot(Object exactLeg){return collectionMovement.snapshot(exactLeg);}
    public boolean collectionTravelSnapshotCurrent(Object exactLeg,Object exact,long actualNow){return collectionMovement.snapshotCurrent(exactLeg,exact,actualNow);}
    public VeinCollectionMovement.AssociationObservation collectionAssociation(BlockPos actualStanding){return collectionMovement.observation(actualStanding);}
    @Override public void stopWalking(){if(scheduledLoan!=null||craftStore.active())return;
        collectionCleanup=collectionMovement.stop(()->{navigation.stop();destination=null;});
    }
    /** Branch observation only; it cannot admit movement or transfer a typed service to legacy. */
    public boolean collectionCleanupTyped(){return collectionMovement.typedCleanup();}
    /** Cleanup-only action on the retained private actual association; no caller slots or grant. */
    public boolean restoreCollectionOriginalSlot(Object exactOwner){
        return collectionCleanup!=null&&collectionCleanup.route()==dev.veltraph.client.mining.vein.VeinCollectionCleanupDispatch.Route.TYPED
            &&collectionCleanup.typed()!=null&&collectionCleanup.typed().restoreOriginalSlot(exactOwner);
    }
    public boolean startCollectionApproach(Object exactOwner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentApproach source){return collectionMovement.startApproach(exactOwner,source);}
    public WalkController.State tickCollectionApproach(){return collectionMovement.tickApproach();}
    public void stopCollectionApproach(){collectionMovement.stopApproach();}
    public boolean collectionActivationCurrent(Object exactOwner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionActivation source){return collectionMovement.activationCurrent(exactOwner,source);}
    public boolean startCollectionActivation(Object exactOwner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionActivation source){return collectionMovement.startActivation(exactOwner,source);}
    /** Uses the actual retained native parent interaction and authentic current operation veto. */
    public boolean publishCollectionParentCamera(dev.veltraph.client.world.Vec3 goal,Object exactOwner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentOperation source){
        return collectionMovement.publishParentCamera(goal,exactOwner,source);
    }
    private String supportSignature(BlockPos pos){
        if(!loaded(pos))return "unloaded";var state=client.level.getBlockState(mc(pos));
        return state.toString()+"|"+state.getCollisionShape(client.level,mc(pos)).toAabbs();
    }
    private boolean restBodyReady(int reserve){
        if(!client.isSameThread()||!available()||destination!=null||client.player.containerMenu!=client.player.inventoryMenu
                ||!client.player.containerMenu.getCarried().isEmpty()||client.player.isUsingItem()||client.options.keyUse.isDown()
                ||!client.player.onGround()||client.player.getDeltaMovement().length()>=.1)return false;
        var position=farm.player().position();if(!navigation.standingClear(position))return false;
        var item=client.player.getMainHandItem();
        return item.is(ItemTags.PICKAXES)&&(!item.isDamageableItem()||item.getMaxDamage()-item.getDamageValue()>reserve);
    }
    @Override public boolean scheduledPauseReady(int reserve){
        if(craftStore.active())return false;
        return scheduledLoan==null&&restBodyReady(reserve)&&(originalSlot<0
                ||client.player==toolOwner&&client.level==toolWorld&&farm.inventory().selectedSlot()==toolSlot);
    }
    @Override public boolean captureScheduledPause(Object ticket,int reserve){
        if(ticket==null||!scheduledPauseReady(reserve))return false;
        try{scheduledLoan=new ScheduledLoan(ticket,client,originalSlot,worldIdentity(),farm.player().blockPos(),supportSignature(farm.player().blockPos().down()));return true;}
        catch(RuntimeException unsupportedIdentity){return false;}
    }
    @Override public void releaseScheduledPause(Object ticket){
        if(scheduledLoan==null||ticket!=scheduledLoan.ticket||scheduledLoan.released)return;
        scheduledLoan.released=true;
        if(collectionMovement.typedCleanup()){collectionCleanup=collectionMovement.stop(()->{});return;}
        farm.breaking().setBreaking(false);navigation.stop();destination=null;farm.rotation().cancelRotation();
    }
    private static float angularDistance(float a,float b){float delta=(a-b)%360;if(delta>180)delta-=360;if(delta< -180)delta+=360;return Math.abs(delta);}
    @Override public boolean scheduledArrivalReady(Object ticket,int reserve){
        var loan=scheduledLoan;
        return loan!=null&&ticket==loan.ticket&&loan.released&&restBodyReady(reserve)
                &&client.player.getUUID().equals(loan.playerId)&&client.level.dimension().identifier().toString().equals(loan.dimension)
                &&farm.player().position().distanceTo(loan.position)<.35
                &&farm.player().blockPos().equals(loan.column)
                &&loan.support.equals(supportSignature(loan.column.down()))
                &&angularDistance(client.player.getYRot(),loan.yaw)<1.5&&Math.abs(client.player.getXRot()-loan.pitch)<1.5
                &&client.player.getPose()==loan.posture&&farm.inventory().selectedSlot()==loan.slot
                &&loan.tool.matches(client.player.getMainHandItem(),client.level.registryAccess());
    }
    @Override public boolean rebindScheduledPause(Object ticket,int reserve){
        if(!scheduledArrivalReady(ticket,reserve))return false;
        var loan=scheduledLoan;loan.boundIdentity=worldIdentity();originalSlot=loan.originalSlot;toolSlot=loan.slot;toolOwner=client.player;toolWorld=client.level;
        return true; // Navigation was flushed once at pause; never erase the arrival helper's controls here.
    }
    @Override public boolean resumeScheduledPause(Object ticket,int reserve){
        if(!scheduledArrivalReady(ticket,reserve)||!scheduledLoan.boundIdentity.equals(worldIdentity()))return false;
        scheduledLoan=null;return true;
    }
    @Override public void cancelScheduledPause(Object ticket){if(scheduledLoan!=null&&ticket==scheduledLoan.ticket)scheduledLoan=null;}
    @Override public String navigationStatus(){return navigation.status();}
}
