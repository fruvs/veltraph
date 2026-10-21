package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.FishingWormController;
import dev.veltraph.client.fishing.worm.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.mc.McInputService;
import dev.veltraph.client.service.mc.McInventoryService;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.Locale;

/** Narrow local adapter: permitted mobs already within vanilla reach and clear water return. */
final class McFishingWorm implements FishingWormController.Actions {
    private final Minecraft client;
    private final ConfigService config;
    private final RotationService rotation;
    private final McInputService input;
    private final McInventoryService inventory;
    private final Runnable reel;
    private final FishingWormController controller=new FishingWormController();
    private final Vec3 anchor;
    private final float yaw,pitch;
    private final int rodSlot;
    private net.minecraft.client.multiplayer.ClientLevel level;
    private LivingEntity target;
    private final WormAdmissionSession admission=new WormAdmissionSession();
    private final Object player,connection;
    private WormHuntingMode mode;
    private boolean sourceCycleAdmitted;
    private final McFishingWormDreadlord dreadlord;

    private String boundaryFailure="";
    private boolean movementOwned,rotationOwned,weaponOwned;
    McFishingWorm(Minecraft client,ConfigService config,RotationService rotation,Runnable reel,dev.veltraph.client.core.Module owner) {
        this.client=client;this.config=config;this.rotation=rotation;this.reel=reel;
        input=new McInputService(client);inventory=new McInventoryService(client);
        dreadlord=new McFishingWormDreadlord(client,config,rotation,owner);
        anchor=client.player==null?null:client.player.position();
        yaw=client.player==null?0:client.player.getYRot();pitch=client.player==null?0:client.player.getXRot();
        rodSlot=inventory.selectedSlot();level=client.level;
        player=client.player;connection=client.getConnection();
    }
    boolean tick(boolean allowStart) {
        if(anchor==null||client.player==null||client.player!=player||client.level!=level||client.getConnection()!=connection) {
            if(active()) {stop();boundaryFailure="Underwater fishing world changed";}
            return false;
        }
        boolean enabled=config.get(FishingConfig.FISHING_WORM_HUNTING);
        if(!enabled){dreadlord.stop();admission.clear();mode=null;sourceCycleAdmitted=false;
            return controller.tick(false,allowStart,System.currentTimeMillis(),Math.round(config.get(FishingConfig.FISHING_CAST_TIMEOUT_SECONDS)*1000),this);}
        var observedMode=config.get(FishingConfig.FISHING_WORM_MODE);
        if(mode!=null&&mode!=observedMode){stop();boundaryFailure="Underwater fishing mode changed";return false;}
        if(!client.isSameThread()||!client.player.isAlive()||client.isPaused()||client.gui.screen()!=null
                ||client.getCameraEntity()!=client.player||client.player.containerMenu!=client.player.inventoryMenu
                ||!client.player.inventoryMenu.getCarried().isEmpty()) {
            if(active()){stop();boundaryFailure="Underwater fishing native context changed";}return false;
        }
        if(controller.active()&&weaponOwned&&inventory.selectedSlot()!=weaponSlot()) {
            stop();boundaryFailure="Underwater fishing weapon was changed";return false;
        }
        if(config.get(FishingConfig.FISHING_WORM_HUNTING)&&allowStart&&submerged()
                &&!client.level.getFluidState(net.minecraft.core.BlockPos.containing(anchor)).is(Fluids.WATER)) {
            boundaryFailure="Underwater hunting needs a saved underwater start position";return false;
        }
        if(dreadlord.active()) {
            if(submerged()&&bubbles()<=1.5)dreadlord.stop();
            else {dreadlord.tick();if(!dreadlord.failure().isEmpty())boundaryFailure=dreadlord.failure();return true;}
        }
        long now=System.currentTimeMillis();
        // Idle land/paused-helper observations cannot parse, sample, count or publish a source failure.
        boolean eligible=allowStart&&submerged()&&client.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD);
        if(!controller.active()&&!eligible)return false;
        if(mode==null)mode=observedMode;
        try {
            if(mode==WormHuntingMode.SOURCE_THRESHOLD)admission.observe(true,eligible,
                    config.get(FishingConfig.WORM_ENTITY_THRESHOLD_RANGE_2),config.get(FishingConfig.WORM_TIME_THRESHOLD_RANGE_2),now,
                    java.util.concurrent.ThreadLocalRandom.current());
        } catch(IllegalArgumentException invalid){stop();boundaryFailure=invalid.getMessage();return false;}
        target=findTarget();
        boolean admitted=mode==WormHuntingMode.LEGACY_MELEE||admission.ready(nearbyWorms(),now);
        if(mode==WormHuntingMode.SOURCE_THRESHOLD&&eligible&&admitted&&!controller.active()&&bubbles()>1.5) {
            if(dreadlord.start()){admission.accepted(now,java.util.concurrent.ThreadLocalRandom.current());return true;}
            boundaryFailure=dreadlord.failure().isEmpty()?"Source worm admission reached; exact Dreadlord tool or native owner is unavailable":dreadlord.failure();return false;
        }
        if(controller.state()==FishingWormController.State.COMPLETE)sourceCycleAdmitted=false;
        boolean started=controller.active();
        if(!started&&mode==WormHuntingMode.LEGACY_MELEE&&admitted&&allowStart&&submerged()&&targetPresent())sourceCycleAdmitted=true;
        // Emergency air recovery keeps its original priority over both admission ratios.
        boolean result=controller.tick(config.get(FishingConfig.FISHING_WORM_HUNTING),allowStart&&(admitted||bubbles()<=1.5),
                now,Math.round(config.get(FishingConfig.FISHING_CAST_TIMEOUT_SECONDS)*1000),this);
        if(!started&&controller.active()&&sourceCycleAdmitted&&mode==WormHuntingMode.SOURCE_THRESHOLD)admission.accepted(now,java.util.concurrent.ThreadLocalRandom.current());
        return result;
    }
    private int nearbyWorms() {
        var origin=client.player.position();
        // Source counts native Silverfish, without name, line-of-sight or alive filtering.
        return client.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Silverfish.class,
                client.player.getBoundingBox().inflate(15),fish->{var delta=fish.position().subtract(origin);
                    return WormAdmission.nearby(delta.x,delta.y,delta.z);}).size();
    }
    private boolean active(){return controller.active()||dreadlord.active();}
    String status() {return dreadlord.active()?"DREADLORD_"+dreadlord.status():controller.state().name();}
    String failure() {return boundaryFailure.isEmpty()?controller.failure():boundaryFailure;}
    void stop() {dreadlord.stop();controller.stop(this);admission.clear();mode=null;sourceCycleAdmitted=false;boundaryFailure="";}
    private LivingEntity findTarget() {
        if(!weaponReady())return null;
        return client.level.getEntitiesOfClass(LivingEntity.class,client.player.getBoundingBox().inflate(3),e->
                e!=client.player&&!(e instanceof Player)&&!(e instanceof ArmorStand)&&e.isAlive()
                &&(mode==WormHuntingMode.LEGACY_MELEE||e instanceof net.minecraft.world.entity.monster.Silverfish)
                && permitted(e.getName().getString())&&client.player.hasLineOfSight(e)
                &&client.player.getEyePosition().distanceTo(e.getBoundingBox().getCenter())<=3)
                .stream().min(Comparator.comparingDouble(e->client.player.distanceToSqr(e))).orElse(null);
    }
    private boolean permitted(String name) {
        String cleaned=name.replaceAll("§.","").strip().toLowerCase(Locale.ROOT);
        for(String entry:config.get(FishingConfig.FISHING_KILL_TARGETS).split(","))
            if(!entry.isBlank()&&cleaned.equals(entry.strip().toLowerCase(Locale.ROOT)))return true;
        return false;
    }
    private int weaponSlot() {return config.get(CombatConfig.COMBAT_WEAPON_SLOT)-1;}
    private boolean weaponReady() {
        int slot=weaponSlot();if(slot<0||slot>8)return false;
        var item=client.player.getInventory().getItem(slot);
        return !item.isEmpty()&&(item.is(ItemTags.SWORDS)||item.is(ItemTags.AXES));
    }
    @Override public boolean killingAdmitted(){return mode==WormHuntingMode.LEGACY_MELEE||sourceCycleAdmitted;}
    @Override public boolean targetPresent() {return target!=null;}
    @Override public boolean hookPresent() {return client.player.fishing!=null;}
    @Override public boolean submerged() {return client.player.isUnderWater();}
    @Override public double bubbles() {return 10d*client.player.getAirSupply()/Math.max(1,client.player.getMaxAirSupply());}
    private boolean clearAt(net.minecraft.core.BlockPos p) {
        return client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4)
                &&client.level.getBlockState(p).getCollisionShape(client.level,p).isEmpty();
    }
    @Override public boolean surfaceReachable() {
        var start=client.player.blockPosition();
        if(!submerged())return clearAt(start)&&clearAt(start.above());
        for(int dy=0;dy<=10;dy++) {
            var p=start.above(dy);if(!clearAt(p)||!clearAt(p.above()))return false;
            if(client.level.getFluidState(p).is(Fluids.WATER)&&client.level.getFluidState(p.above()).isEmpty())return true;
        }
        return false;
    }
    @Override public boolean returnClear() {
        Vec3 here=client.player.position();double length=here.distanceTo(anchor);
        if(length>12||Math.hypot(here.x-anchor.x,here.z-anchor.z)>2)return false;
        // Loaded, collision-free body-height corridor; no navigation across dry gaps.
        for(int i=0;i<=Math.ceil(length*4);i++) {
            Vec3 at=here.lerp(anchor,i/Math.max(1,Math.ceil(length*4)));
            var p=net.minecraft.core.BlockPos.containing(at);
            if(!clearAt(p)||!clearAt(p.above())||!client.level.getFluidState(p).is(Fluids.WATER))return false;
        }
        return true;
    }
    @Override public boolean atAnchor() {
        var p=client.player.position();
        return Math.hypot(p.x-anchor.x,p.z-anchor.z)<.35&&Math.abs(p.y-anchor.y)<.25;
    }
    private void movement(boolean up,boolean down,boolean forward) {
        movementOwned=true;input.releaseAllMovement();input.setJumpHeld(up);input.setSneakHeld(down);input.setForwardHeld(forward);
    }
    @Override public void reel() {reel.run();}
    @Override public void kill() {
        if(target==null||!weaponReady())return;
        inventory.selectSlot(weaponSlot());weaponOwned=true;
        var eye=client.player.getEyePosition();var point=target.getBoundingBox().getCenter();var delta=point.subtract(eye);
        rotationOwned=true;rotation.track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),
                (float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(client.gameMode!=null&&client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==target
                &&hit.getLocation().distanceTo(eye)<=3&&client.player.hasLineOfSight(target)
                &&client.player.getAttackStrengthScale(0)>=.95) {
            client.gameMode.attack(client.player,target);
            client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
        }
    }
    @Override public void ascend() {restoreRod();movement(true,false,false);}
    @Override public void refill() {restoreRod();movement(true,false,false);}
    @Override public void returnToAnchor() {
        restoreRod();var p=client.player.position();double dx=anchor.x-p.x,dz=anchor.z-p.z;
        boolean lateral=Math.hypot(dx,dz)>.25;
        rotationOwned=true;rotation.track(lateral?(float)Math.toDegrees(Math.atan2(-dx,dz)):yaw,0);
        boolean aligned=!lateral||Math.abs(WalkController.wrap((float)Math.toDegrees(Math.atan2(-dx,dz))-client.player.getYRot()))<12;
        movement(p.y<anchor.y-.15,p.y>anchor.y+.15,lateral&&aligned);
    }
    @Override public boolean restoreAim() {
        rotationOwned=true;rotation.track(yaw,pitch);
        return Math.abs(WalkController.wrap(yaw-client.player.getYRot()))<1&&Math.abs(pitch-client.player.getXRot())<1;
    }
    private void restoreRod() {if(weaponOwned){inventory.selectSlot(rodSlot);weaponOwned=false;}}
    @Override public void release() {
        if(movementOwned){input.releaseAllMovement();movementOwned=false;}
        if(rotationOwned){rotation.cancelRotation();rotationOwned=false;}
        restoreRod();
    }
    boolean idleForPause(){return !controller.active()&&!dreadlord.active()&&!movementOwned&&!rotationOwned&&!weaponOwned&&failure().isEmpty();}
    boolean rebindIdle(){if(!idleForPause()||client.level==null)return false;level=client.level;target=null;return true;}
}
