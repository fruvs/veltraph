package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.Locale;
import dev.veltraph.client.movement.device.DeviceUseReceipt;
import dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt;
import net.minecraft.world.item.ItemStack;

/** Ether Transmission using the held item's normal use action and server-confirmed arrival. */
public final class McEtherwarpNavigator {
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final EtherwarpController controller=new EtherwarpController();
    private final McNavigator terrain;
    private final ExpectedTeleport expectedTeleport=new ExpectedTeleport();
    private net.minecraft.client.multiplayer.ClientLevel ownerLevel,expectedLevel;
    private net.minecraft.client.multiplayer.ClientLevel arrivalLevel;
    private LocalPlayer arrivalOwner;
    private LocalPlayer owner;private int previousSlot,toolSlot;private BlockPos target;private Direction face;private Vec3 landing;
    private String status="Idle";
    private boolean useDispatched,useAttempted;private OwnedArrivalRelease ownedArrivalRelease;
    private LegacyTravelPublicationLease.Leg publication;private boolean standalone;
    private java.util.List<ItemStack> inventorySnapshot=java.util.List.of();
    private DeviceUseReceipt operation;private DeviceUseReceipt.Applied appliedReceipt;
    private static final Object TELEPORT_DOMAIN=new Object();
    private Runnable cancelIntent=()->{};
    private boolean fresh() { return publication!=null?publication.current():standalone&&!dev.veltraph.client.service.mc.McOwnedRotation.installed(client); }
    private boolean inventoryCurrent() {
        if(client.player!=owner||owner==null||owner.getInventory().getContainerSize()!=inventorySnapshot.size())return false;
        for(int i=0;i<inventorySnapshot.size();i++)if(!ItemStack.matches(inventorySnapshot.get(i),owner.getInventory().getItem(i)))return false;
        return true;
    }
    /** Exact temporary selection permission; a caller must still check its captured Root/PWC scene. */
    public boolean permitsSelectedSlot(int original) { return owner!=null&&original==previousSlot&&services.inventory().selectedSlot()==toolSlot&&inventoryCurrent(); }
    public DeviceUseReceipt.Issue issue(){return operation==null?null:operation.issue();}
    public DeviceUseReceipt.Applied appliedReceipt(){return appliedReceipt;}
    /** Private synchronous receipt for this navigator's actual terminal arrival and own camera cancellation. */
    public static final class OwnedArrivalRelease {
        private final McEtherwarpNavigator producer;private final Object player,world,connection,authority;private final Vec3 landing;
        private final long beforeEpoch,afterEpoch,generation,journey;private final int tick;private final boolean issuedUse;
        private final LegacyTravelPublicationLease.Leg leg;
        private OwnedArrivalRelease(McEtherwarpNavigator producer,Object player,Object world,Object connection,Object authority,Vec3 landing,long beforeEpoch,long afterEpoch,int tick,boolean issuedUse,LegacyTravelPublicationLease.Leg leg){this.producer=producer;this.player=player;this.world=world;this.connection=connection;this.authority=authority;this.landing=landing;this.beforeEpoch=beforeEpoch;this.afterEpoch=afterEpoch;this.tick=tick;this.issuedUse=issuedUse;this.leg=leg;generation=leg==null?-1:leg.requestGeneration();journey=leg==null?-1:leg.journeyGeneration();}
        public boolean current(Minecraft actual,Object expectedOwner,long expectedBeforeEpoch){
            return (leg==null||producer.publication==leg&&leg.current()&&leg.requestGeneration()==generation&&leg.journeyGeneration()==journey)
                &&producer.ownedArrivalRelease==this&&producer.controller.state()==EtherwarpController.State.ARRIVED&&actual.isSameThread()
                &&actual.player==player&&actual.level==world&&actual.getConnection()==connection&&actual.player.tickCount==tick
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(actual)==authority&&authority==expectedOwner
                &&dev.veltraph.client.combat.ghost.GhostSourceOwnedArrivalEpoch.matches(expectedBeforeEpoch,beforeEpoch,afterEpoch,dev.veltraph.client.service.mc.McOwnedRotation.epoch(actual))
                &&new Vec3(actual.player.getX(),actual.player.getY(),actual.player.getZ()).distanceTo(landing)<.9&&actual.player.isAlive()&&actual.gui.screen()==null;
        }
        public boolean issuedUse(){return issuedUse;}
    }
    public java.util.Optional<OwnedArrivalRelease> ownedArrivalRelease(){return java.util.Optional.ofNullable(ownedArrivalRelease);}

    public McEtherwarpNavigator(Minecraft client,FarmServices services,ConfigService config) {
        this.client=client;this.services=services;this.config=config;terrain=new McNavigator(client,services.input(),config);
    }
    public int findTool() {
        if(client.player==null) return -1;
        for(int i=0;i<9;i++) if(hasAbility(i)) return i;
        return -1;
    }
    private boolean hasAbility(int slot) {
        if(client.player==null||slot<0||slot>8) return false;
        String token=config.get(TravelConfig.ETHERWARP_ABILITY_TOKEN).strip().toLowerCase(Locale.ROOT);
        if(token.isEmpty()) return false;
        var stack=client.player.getInventory().getItem(slot);var lore=stack.get(DataComponents.LORE);
        return lore!=null && lore.lines().stream().anyMatch(line->line.getString().replaceAll("§.","").toLowerCase(Locale.ROOT).contains(token));
    }
    public boolean start(BlockPos target,Direction face,Vec3 landing) { return start(target,face,landing,null); }
    /** Root-bound callers must supply the concrete publication leg captured at admission. */
    public boolean start(BlockPos target,Direction face,Vec3 landing,LegacyTravelPublicationLease.Leg supplied) {
        stop();publication=supplied;standalone=supplied==null&&!dev.veltraph.client.service.mc.McOwnedRotation.installed(client);
        if(!fresh()) { status="Captured Etherwarp publication lease required";return false; }
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null
                ||target==null||face==null||landing==null||!Double.isFinite(landing.x())||!Double.isFinite(landing.y())||!Double.isFinite(landing.z())
                ||(toolSlot=findTool())<0) { status="Available world, destination and Ether Transmission item required";return false; }
        if(!terrain.standingClear(landing)||!fresh()) { status="Landing has no loaded supported collision surface";return false; }
        this.target=target;this.face=face;this.landing=landing;owner=client.player;previousSlot=services.inventory().selectedSlot();
        ownerLevel=client.level;
        var saved=new java.util.ArrayList<ItemStack>();for(int i=0;i<owner.getInventory().getContainerSize();i++)saved.add(owner.getInventory().getItem(i).copy());inventorySnapshot=java.util.List.copyOf(saved);
        if(!fresh()||!inventoryCurrent()) { stop();return false; }
        if(publication!=null) { if(!publication.selectSlotOwned(previousSlot,toolSlot)) { stop();return false; } }
        else if(fresh())services.inventory().selectSlot(toolSlot);
        if(!fresh()||!inventoryCurrent()||services.inventory().selectedSlot()!=toolSlot) { stop();return false; }
        controller.start(target,face,landing,System.currentTimeMillis());status="Aiming";return true;
    }
    private static net.minecraft.core.BlockPos mc(BlockPos p) { return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()); }
    private boolean landingClear() {
        if(!fresh()||client.level==null||client.player==null) return false;
        var p=landing.toBlockPos();
        if(!client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4)||!client.level.getChunkSource().hasChunk(target.x()>>4,target.z()>>4)) return false;
        return terrain.standingClear(landing) && !client.level.getBlockState(mc(target)).getCollisionShape(client.level,mc(target)).isEmpty()&&fresh();
    }
    public EtherwarpController.State tick() {
        if(!fresh()) { failCleanup();status="Etherwarp publication lease changed";return EtherwarpController.State.FAILED; }
        if(owner==null) {
            if(controller.state()==EtherwarpController.State.ARRIVED&&client.player==arrivalOwner&&client.level==arrivalLevel
                    &&arrivalOwner!=null&&arrivalOwner.isAlive()&&client.gui.screen()==null)return EtherwarpController.State.ARRIVED;
            failCleanup();status="Travel ownership or world unavailable";return EtherwarpController.State.FAILED;
        }
        boolean available=client.player==owner&&client.level==ownerLevel&&owner.isAlive()&&client.gui.screen()==null;
        if(!available) { status="World changed";failCleanup();return EtherwarpController.State.FAILED; }
        if(publication!=null&&operation!=null&&(NativeDeviceTeleportReceipt.rejected(operation.issue())
            ||!operation.validate(publication.deviceContext(),System.currentTimeMillis()))) {
            failCleanup();status="Etherwarp original issue expired or revoked; no retry";return EtherwarpController.State.FAILED;
        }
        if(publication!=null)publication.releaseAllNineOwned();else if(fresh())services.input().releaseAllMovement();
        var player=client.player;var velocity=player.getDeltaMovement();
        HitResult hit=player.pick(100,1,true);
        EtherwarpGate.Observation observation=null;
        if(hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK) {
            var p=block.getBlockPos();
            observation=new EtherwarpGate.Observation(new BlockPos(p.getX(),p.getY(),p.getZ()),Direction.valueOf(block.getDirection().name()),
                    hit.getLocation().y,Math.hypot(velocity.x,velocity.z),true,landingClear(),player.getEyePosition().distanceTo(hit.getLocation()));
        }
        if(!fresh()||!inventoryCurrent()) { failCleanup();status="Etherwarp lease or full inventory changed during geometry read";return EtherwarpController.State.FAILED; }
        long controllerNow=System.currentTimeMillis();
        var action=controller.tick(new EtherwarpController.Input(new Vec3(player.getX(),player.getY(),player.getZ()),true,
                services.inventory().selectedSlot()==toolSlot && hasAbility(toolSlot),player.isShiftKeyDown(),observation,controllerNow),
                config.get(TravelConfig.ETHERWARP_STABLE_TICKS),config.get(TravelConfig.ETHERWARP_TIMEOUT_MS));
        if(!fresh()||!inventoryCurrent()) { failCleanup();status="Etherwarp lease changed during controller read";return EtherwarpController.State.FAILED; }
        status=controller.status();
        if(action==EtherwarpController.Action.AIM_AND_SNEAK) {
            if(publication!=null) { if(!publication.setSneakHeld(true)) { failCleanup();return EtherwarpController.State.FAILED; } }
            else if(fresh())services.input().setSneakHeld(true);
            var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(target)).add(face.dx()*.499,face.dy()*.499,face.dz()*.499);
            var delta=point.subtract(player.getEyePosition());
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            if(!fresh()||!inventoryCurrent()) { failCleanup();return EtherwarpController.State.FAILED; }
            if(publication!=null) { if(publication.publishTracked(yaw,pitch)<=0||!fresh()) { failCleanup();return EtherwarpController.State.FAILED; } }
            else if(fresh())services.rotation().track(yaw,pitch);
        } else if(action==EtherwarpController.Action.USE && client.gameMode!=null) {
            if(!fresh()||!inventoryCurrent()||services.inventory().selectedSlot()!=toolSlot||!hasAbility(toolSlot)||!landingClear()||!fresh()) { failCleanup();return EtherwarpController.State.FAILED; }
            if(publication!=null) {
                if(!publication.setSneakHeld(true)) { failCleanup();return EtherwarpController.State.FAILED; }
                long now=System.currentTimeMillis(),timeout=Math.max(500,Math.min(30000,config.get(TravelConfig.ETHERWARP_TIMEOUT_MS)));
                if(Long.MAX_VALUE-controllerNow<timeout||now<controllerNow||now>=controllerNow+timeout) { failCleanup();return EtherwarpController.State.FAILED; }
                var context=publication.deviceContext();if(context==null) { failCleanup();return EtherwarpController.State.FAILED; }
                operation=new DeviceUseReceipt();var issue=new DeviceUseReceipt.Issue(context,TELEPORT_DOMAIN,new Object(),NativeDeviceTeleportReceipt.baseline(client),now,controllerNow+timeout,landing,.89);
                if(!operation.arm(issue)||!fresh()||!inventoryCurrent()||!landingClear()||!fresh()
                    ||!NativeDeviceTeleportReceipt.arm(client,issue,()->fresh()&&inventoryCurrent()&&services.inventory().selectedSlot()==toolSlot)) { failCleanup();return EtherwarpController.State.FAILED; }
                cancelIntent=dev.veltraph.client.observation.unexpected.UnexpectedActions.expectOwnedTeleport(client,landing,.89,issue.deadline()-now,()->fresh()&&inventoryCurrent()&&services.inventory().selectedSlot()==toolSlot);
                if(!fresh()||!inventoryCurrent()) { failCleanup();return EtherwarpController.State.FAILED; }
                NativeDeviceTeleportReceipt.openSubmissionWindow(issue);
                try {
                    if(!fresh()||!inventoryCurrent()||services.inventory().selectedSlot()!=toolSlot) { failCleanup();return EtherwarpController.State.FAILED; }
                    useAttempted=true;client.gameMode.useItem(player,InteractionHand.MAIN_HAND);
                }
                catch(RuntimeException failure) { failCleanup();status="Etherwarp native use failed; no retry";throw failure; }
                finally { NativeDeviceTeleportReceipt.closeSubmissionWindow(issue); }
                NativeDeviceTeleportReceipt.returnedNormally(issue);useDispatched=NativeDeviceTeleportReceipt.submitted(issue);
                if(!useDispatched||!fresh()||!inventoryCurrent()) { failCleanup();status="Etherwarp submission lost captured custody";return EtherwarpController.State.FAILED; }
            } else {
                if(!fresh()) { failCleanup();return EtherwarpController.State.FAILED; }
                services.input().setSneakHeld(true);dev.veltraph.client.observation.unexpected.UnexpectedActions.expectTeleport(client,landing,.89,5000);
                useAttempted=true;client.gameMode.useItem(player,InteractionHand.MAIN_HAND);useDispatched=true;
            }
            expectedLevel=client.level;expectedTeleport.arm(landing,System.currentTimeMillis(),config.get(TravelConfig.ETHERWARP_TIMEOUT_MS));
        }
        EtherwarpController.State state=controller.state();
        if(publication!=null&&state==EtherwarpController.State.ARRIVED) {
            if(operation==null||!useDispatched||NativeDeviceTeleportReceipt.rejected(operation.issue())) { failCleanup();status="Etherwarp arrival requires its own submitted use and applied receipt";return EtherwarpController.State.FAILED; }
            long now=System.currentTimeMillis();var receipt=NativeDeviceTeleportReceipt.applied(operation.issue());
            if(receipt==null) {
                if(!operation.validate(publication.deviceContext(),now)) { failCleanup();status="Etherwarp receipt expired or revoked";return EtherwarpController.State.FAILED; }
                return EtherwarpController.State.WAITING_TELEPORT;
            }
            if(receipt.position()==null||!fresh()||!inventoryCurrent()||new Vec3(player.getX(),player.getY(),player.getZ()).distanceTo(receipt.position())>.05
                ||!landingClear()||!fresh()||!operation.observe(publication.deviceContext(),receipt,now)) { failCleanup();status="Etherwarp applied receipt rejected";return EtherwarpController.State.FAILED; }
            appliedReceipt=receipt;
        }
        if(state==EtherwarpController.State.ARRIVED) { arrivalOwner=owner;arrivalLevel=ownerLevel; }
        if(state==EtherwarpController.State.ARRIVED){
            var p=owner;var w=ownerLevel;var c=client.getConnection();var authority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
            long before=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);int tick=p.tickCount;var arrival=landing;boolean issued=useDispatched;
            release();ownedArrivalRelease=new OwnedArrivalRelease(this,p,w,c,authority,arrival,before,dev.veltraph.client.service.mc.McOwnedRotation.epoch(client),tick,issued,publication);
        }else if(state==EtherwarpController.State.FAILED)release();
        return state;
    }
    private void failCleanup() { boolean issued=useDispatched,attempted=useAttempted;stop();useDispatched=issued;useAttempted=attempted; }
    private void release() {
        var own=publication;boolean restore=owner!=null&&inventoryCurrent()&&services.inventory().selectedSlot()==toolSlot;
        cancelIntent.run();cancelIntent=()->{};
        if(operation!=null) { operation.revoke();NativeDeviceTeleportReceipt.retire(operation.issue()); }
        if(own!=null) {
            if(restore&&own.inventoryCleanupCurrent()&&inventoryCurrent()&&services.inventory().selectedSlot()==toolSlot){
                services.inventory().selectSlot(previousSlot);
                if(!own.inventoryCleanupCurrent()||!inventoryCurrent()||services.inventory().selectedSlot()!=previousSlot)status="Owned inventory restoration lost its strict gear scope";
            }
            own.releaseAllNineOwned();own.cancelCameraOwned();
        } else if(owner!=null&&fresh()) {
            services.rotation().cancelRotation();services.input().releaseAllMovement();
            if(restore&&client.player==owner&&client.level==ownerLevel)services.inventory().selectSlot(previousSlot);
        }
        owner=null;ownerLevel=null;
    }
    public boolean consumeExpectedTeleport(Vec3 position,long now) {
        if(publication!=null&&(appliedReceipt==null||!fresh()||appliedReceipt.position().distanceTo(position)>.05))return false;
        if(client.level!=expectedLevel) { expectedTeleport.clear();return false; }
        return expectedTeleport.consume(position,now);
    }
    /** Conservative outcome boundary: an attempted ordinary use with unknown submission must not retry. */
    public boolean useDispatched() { return useDispatched||useAttempted; }
    public void stop() { ownedArrivalRelease=null;release();controller.reset();expectedTeleport.clear();expectedLevel=null;arrivalOwner=null;arrivalLevel=null;useDispatched=false;useAttempted=false;publication=null;standalone=false;inventorySnapshot=java.util.List.of();operation=null;appliedReceipt=null;target=null;face=null;landing=null; }
    public String status() { return status; }
}
