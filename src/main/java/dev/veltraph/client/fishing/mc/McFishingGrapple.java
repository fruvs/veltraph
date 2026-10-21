package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.setup.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.mc.McInputService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.Supplier;

/** Source Crimson Blaze pull: no player pose/velocity writes or production marker-name acknowledgments. */
final class McFishingGrapple {
    private final Minecraft client;private final ConfigService config;private final RotationService rotation;private final Runnable useRod;private final Supplier<GameArea> area;private final McFishingTravel nav;
    private final GrappleRuntime.Eligibility eligibility=new GrappleRuntime.Eligibility();private GrappleRuntime runtime;
    private GrappleProfile profile;private String contract,error="",ackDetail="";private Object world,player,connection;private Blaze target;private UUID targetId;
    private Vec3 anchor,beforeTarget;private float yaw,pitch;private int rodSlot,toolSlot,beforeCount;private ItemStack rod=ItemStack.EMPTY,tool=ItemStack.EMPTY;
    private dev.veltraph.client.world.Vec3 normalReturnGoal;private boolean owned,navOwned,rotOwned,prepared,restored,returned;private long lastUse;private final Set<UUID> existingTethers=new HashSet<>();
    McFishingGrapple(Minecraft c,ConfigService cfg,RotationService r,Runnable rodUse,Supplier<GameArea> a,dev.veltraph.client.service.FarmServices services){client=c;config=cfg;rotation=r;useRod=rodUse;area=a;nav=new McFishingTravel(c,cfg,services,this);if(c.player!=null){anchor=c.player.position();yaw=c.player.getYRot();pitch=c.player.getXRot();}}
    static String problem(ConfigService cfg){if(!cfg.get(FishingConfig.FISHING_GRAPPLESHOT_BLAZES))return "";try{GrappleProfile.parse(cfg.get(FishingConfig.FISHING_GRAPPLE_PROFILE));return "";}catch(RuntimeException e){return e.getMessage();}}
    boolean active(){return runtime!=null&&runtime.active();}String status(){return runtime==null?"IDLE":runtime.state().name();}String failure(){return error.isEmpty()&&runtime!=null?runtime.failure():error;}
    private int find(){int slot=-1;for(int i=0;i<9;i++){var s=client.player.getInventory().getItem(i);if(s.getHoverName().getString().replaceAll("§.","").equals(profile.itemName())&&BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(profile.itemId())){if(slot>=0)return -1;slot=i;}}return slot;}
    private boolean loaded(Vec3 p){return client.level.getChunkSource().hasChunk((int)Math.floor(p.x)>>4,(int)Math.floor(p.z)>>4);}
    private boolean healthy(){return client.player.isAlive()&&client.player.getHealth()>0&&client.player.getHealth()/client.player.getMaxHealth()>=profile.minimumHealthFraction();}
    private boolean hazard(Blaze b){var p=b.blockPosition();if(!loaded(b.position()))return true;boolean floor=false;for(int dy=0;dy<=3;dy++){var s=client.level.getBlockState(p.below(dy));if(!s.getCollisionShape(client.level,p.below(dy)).isEmpty()){floor=true;break;}if(profile.avoidLava()&&s.getFluidState().is(FluidTags.LAVA))return true;}return !floor;}
    private static ItemStack rodIdentity(ItemStack s){var copy=s.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    private boolean tether(Bat b,boolean fresh){return b.isAlive()&&loaded(b.position())&&b.getVehicle() instanceof ArmorStand stand&&stand.isAlive()&&loaded(stand.position())&&(!fresh||!existingTethers.contains(stand.getUUID()))&&b.getLeashHolder()==client.player;}
    boolean tick(boolean allow){
        if(client.player==null||client.level==null||client.gameMode==null)return active();long now=System.currentTimeMillis();boolean was=active();
        if(!was){
            if(!allow||!config.get(FishingConfig.FISHING_GRAPPLESHOT_BLAZES)||area.get()!=GameArea.CRIMSON_ISLE||client.gui.screen()!=null)return false;
            try{profile=GrappleProfile.parse(config.get(FishingConfig.FISHING_GRAPPLE_PROFILE));}catch(RuntimeException e){error=e.getMessage();return true;}
            toolSlot=find();if(toolSlot<0||!healthy()||now-lastUse<profile.minimumUseIntervalMillis()||client.player.getCooldowns().isOnCooldown(client.player.getInventory().getItem(toolSlot)))return false;
            var candidates=new ArrayList<Blaze>();for(var e:client.level.entitiesForRendering())if(e instanceof Blaze b&&b.isAlive()&&b.getHealth()>0&&(profile.targetName().isEmpty()||b.getName().getString().equals(profile.targetName()))&&loaded(b.position())&&client.player.hasLineOfSight(b)&&b.distanceTo(client.player)<=profile.maximumUseDistance())candidates.add(b);
            candidates.sort(Comparator.comparingDouble(b->b.distanceTo(client.player)));if(candidates.isEmpty())return false;var selected=candidates.getFirst();if(!eligibility.test(selected.distanceTo(client.player),hazard(selected),false))return false;
            for(var e:client.level.entitiesForRendering())if(e instanceof Bat b&&tether(b,false))return false;
            contract=config.get(FishingConfig.FISHING_GRAPPLE_PROFILE);world=client.level;player=client.player;connection=client.getConnection();target=selected;targetId=target.getUUID();rodSlot=client.player.getInventory().getSelectedSlot();rod=rodIdentity(client.player.getMainHandItem());prepared=restored=returned=false;runtime=new GrappleRuntime(profile.requestTimeoutMillis(),profile.stageTimeoutMillis());
        }
        if(client.level!=world||client.player!=player||client.getConnection()!=connection||client.gui.screen()!=null||area.get()!=GameArea.CRIMSON_ISLE||!config.get(FishingConfig.FISHING_GRAPPLESHOT_BLAZES)||!Objects.equals(contract,config.get(FishingConfig.FISHING_GRAPPLE_PROFILE))||!healthy()||target==null||target.isRemoved()||!targetId.equals(target.getUUID())||!target.isAlive()||target.getHealth()<=0||!loaded(target.position())||!loaded(client.player.position())){fail("Grapple context/target/health changed");return true;}
        if(!owned&&((client.player.getInventory().getSelectedSlot()!=rodSlot&&!(navOwned&&runtime.state()==GrappleRuntime.State.RETURN&&nav.rodSelectionCurrent(rodSlot)))||!ItemStack.matches(rod,rodIdentity(client.player.getInventory().getItem(rodSlot))))){fail("Grapple rod slot ownership changed");return true;}
        if(owned&&client.player.getInventory().getItem(toolSlot).getCount()!=beforeCount&&(!(runtime.state()==GrappleRuntime.State.WAIT_ACK||runtime.state()==GrappleRuntime.State.RESTORE)||client.player.getInventory().getItem(toolSlot).getCount()!=beforeCount-profile.itemConsumption())){fail("Grapple item count ownership changed");return true;}
        if(owned&&((client.player.getInventory().getSelectedSlot()!=toolSlot&&!(navOwned&&runtime.state()==GrappleRuntime.State.WALK_BACK&&nav.rodSelectionCurrent(toolSlot)))||!(client.player.getInventory().getItem(toolSlot).isEmpty()&&profile.itemConsumption()==beforeCount||ItemStack.isSameItemSameComponents(tool,client.player.getInventory().getItem(toolSlot))))){fail("Grapple slot ownership changed");return true;}
        boolean aligned=false,castAligned=false;
        if(runtime.state()==GrappleRuntime.State.WALK_BACK||runtime.state()==GrappleRuntime.State.RETURN){var s=nav.tick();if(s==WalkController.State.BLOCKED){fail("Grapple standing-position path blocked");return true;}if(s==WalkController.State.ARRIVED){nav.stop();navOwned=false;returned=true;}}
        if(runtime.state()==GrappleRuntime.State.AIM){var p=target.getEyePosition();var d=p.subtract(client.player.getEyePosition());float y=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),x=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));rotation.track(y,x);rotOwned=true;aligned=Math.hypot(net.minecraft.util.Mth.wrapDegrees(y-client.player.getYRot()),x-client.player.getXRot())<=3&&client.player.hasLineOfSight(target)&&target.distanceTo(client.player)<=profile.maximumUseDistance()&&!client.player.getCooldowns().isOnCooldown(client.player.getMainHandItem());}
        if(runtime.state()==GrappleRuntime.State.AIM_CAST){rotation.track(yaw,pitch);rotOwned=true;castAligned=Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1;}
        boolean ack=false;if(runtime.state()==GrappleRuntime.State.WAIT_ACK&&beforeTarget!=null){boolean newTether=false;for(var e:client.level.entitiesForRendering())if(e instanceof Bat b&&!existingTethers.contains(b.getUUID())&&tether(b,true)&&b.position().distanceTo(target.position())<3)newTether=true;ackDetail="tether="+newTether+" moved="+target.position().distanceTo(beforeTarget)+" distance="+target.distanceTo(client.player)+" count="+client.player.getInventory().getItem(toolSlot).getCount();ack=newTether&&target.position().distanceTo(beforeTarget)>1&&target.distanceTo(client.player)<=14&&client.player.getInventory().getItem(toolSlot).getCount()==beforeCount-profile.itemConsumption();}
        var action=runtime.tick(new GrappleRuntime.Input(!was,client.player.fishing!=null,prepared,client.player.position().distanceTo(anchor)>8&&target.position().distanceTo(anchor)<14,returned,aligned,ack,restored,returned,castAligned,now));
        switch(action){
            case REEL -> useRod.run();
            case PREPARE -> {toolSlot=find();if(toolSlot<0){fail("Grapple tool disappeared");break;}tool=client.player.getInventory().getItem(toolSlot).copy();beforeCount=tool.getCount();client.player.getInventory().setSelectedSlot(toolSlot);owned=prepared=true;}
            case WALK_BACK -> walkBack();
            case USE -> {if(!aligned||!healthy()||!client.player.hasLineOfSight(target)||client.player.getCooldowns().isOnCooldown(client.player.getMainHandItem())){fail("Grapple action no longer permitted");break;}existingTethers.clear();for(var e:client.level.entitiesForRendering())if(e instanceof Bat||e instanceof ArmorStand)existingTethers.add(e.getUUID());beforeTarget=target.position();lastUse=now;client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);}
            case RESTORE -> {restored=restoreSlot();if(!restored)fail("Grapple rod identity changed");}
            case RETURN -> {returned=false;walkBack();}
            default -> {}
        }
        if(runtime.state()==GrappleRuntime.State.FAILED&&error.isEmpty())error=runtime.failure()+" ("+ackDetail+")";if(runtime.state()==GrappleRuntime.State.COMPLETE||runtime.state()==GrappleRuntime.State.FAILED)release();return true;
    }
    private void walkBack(){navOwned=true;returned=false;normalReturnGoal=new dev.veltraph.client.world.Vec3(anchor.x,anchor.y,anchor.z);if(!nav.start(normalReturnGoal,dev.veltraph.client.fishing.FishingTravelIntent.GRAPPLE_RETURN))fail("Cannot calculate grapple standing-position return");}
    private boolean restoreSlot(){if(!owned)return true;if(client.player!=player||client.level!=world||client.player.getInventory().getSelectedSlot()!=toolSlot||!ItemStack.matches(rod,rodIdentity(client.player.getInventory().getItem(rodSlot))))return false;client.player.getInventory().setSelectedSlot(rodSlot);owned=false;return true;}
    private void release(){if(owned&&client.player!=null&&client.level!=null&&client.player==player&&client.level==world&&client.player.getInventory().getSelectedSlot()==toolSlot&&(ItemStack.isSameItemSameComponents(tool,client.player.getMainHandItem())||profile.itemConsumption()==beforeCount&&client.player.getMainHandItem().isEmpty()))restoreSlot();if(navOwned)nav.stop();navOwned=false;if(rotOwned)rotation.cancelRotation();rotOwned=false;}
    private void fail(String s){error=s;if(runtime!=null)runtime.fail(s);release();}
    void stop(){release();if(runtime!=null)runtime.reset();target=null;targetId=null;world=player=null;existingTethers.clear();}
    boolean idleForPause(){if(active()||owned||navOwned||rotOwned||!failure().isEmpty())return false;if(profile!=null&&client.player!=null&&client.level!=null)for(var e:client.level.entitiesForRendering())if(e instanceof Bat b&&tether(b,false))return false;return true;}
    boolean rebindIdle(){if(!idleForPause())return false;target=null;targetId=null;player=world=null;beforeTarget=null;rod=tool=ItemStack.EMPTY;existingTethers.clear();eligibility.clear();return true;}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return active()&&nav.consumeExpectedTeleport(position,now);}
    McFishingTravel.NativeCleanupSource captureNativeCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return nav.captureNativeCleanupSource(exact);}
    McFishingTravel.NativeReturnSource captureNativeReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return nav.captureNativeReturnSource(exact);}
    Object nativeReturnProfileIdentity(){return profile;}
    Object nativeReturnRuntimeIdentity(){return runtime;}
    int nativeReturnRodSlot(){return rodSlot;}
    Object nativeReturnCustodyIdentity(){return rod;}
    Object nativeReturnAnchorIdentity(){return anchor;}
    boolean nativeReturnSourceCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(client.player==null||client.level==null||rodSlot<0||rodSlot>8)return false;
        boolean rodCurrent=ItemStack.matches(rod,rodIdentity(client.player.getInventory().getItem(rodSlot)));
        boolean toolCurrent=runtime!=null&&runtime.state()==GrappleRuntime.State.RETURN
            ||toolSlot>=0&&toolSlot<9&&ItemStack.matches(tool,client.player.getInventory().getItem(toolSlot))&&tool.getCount()==beforeCount;
        return rodCurrent&&toolCurrent&&nativeReturnSourceRawCurrent(exact,intent,goal,starting);
    }
    /** Stored private phase/source fields only; no stack comparison or codec visitor. */
    boolean nativeReturnSourceRawCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(exact!=nav||intent!=dev.veltraph.client.fishing.FishingTravelIntent.GRAPPLE_RETURN||goal!=normalReturnGoal||normalReturnGoal==null
            ||!navOwned||runtime==null||!error.isEmpty()||!runtime.failure().isEmpty()||profile==null||contract==null
            ||client.player!=player||client.level!=world||client.getConnection()!=connection||anchor==null||target==null||targetId==null||!targetId.equals(target.getUUID())
            ||rodSlot<0||rodSlot>8||!rod.is(net.minecraft.world.item.Items.FISHING_ROD))return false;
        if(runtime.state()==GrappleRuntime.State.RETURN)return !owned&&restored;
        return runtime.state()==GrappleRuntime.State.WALK_BACK&&owned&&prepared&&toolSlot>=0&&toolSlot<9&&tool.getCount()==beforeCount;
    }
}
