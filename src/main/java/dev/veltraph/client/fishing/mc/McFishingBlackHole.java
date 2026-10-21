package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.*;
import dev.veltraph.client.fishing.support.BlackHoleProfile;
import dev.veltraph.client.fishing.support.SourceBlackHolePresence;
import dev.veltraph.client.service.RotationService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Source Black Hole placement. Fresh actual native stands plus declared consumption acknowledge use. */
final class McFishingBlackHole {
    private final Minecraft client;
    private final ConfigService config;
    private final RotationService rotation;
    private final Runnable useRod;
    private FishingTotemController runtime=new FishingTotemController(100,4000);
    private BlackHoleProfile selected;
    private FishingManaParser.Mana mana;
    private long manaAt,useAt,lastUse;
    private String contract;
    private Object player,world;
    private int rodSlot,itemSlot,beforeMana,beforeCount;
    private ItemStack rod=ItemStack.EMPTY,item=ItemStack.EMPTY;
    private BlockHitResult place;
    private Vec3 anchor;
    private float yaw,pitch;
    private boolean owned,rotating,prepared,restored,used,submerged;
    private Set<UUID> previousMarkers=Set.of();

    McFishingBlackHole(Minecraft c,ConfigService cfg,RotationService r,Runnable use) {
        client=c;config=cfg;rotation=r;useRod=use;
    }
    static String problem(ConfigService cfg) {
        if(!cfg.get(FishingConfig.FISHING_BLACK_HOLE))return "";
        try { BlackHoleProfile.parse(cfg.get(FishingConfig.FISHING_BLACK_HOLE_PROFILE));return ""; }
        catch(RuntimeException e) { return e.getMessage(); }
    }
    void mana(String line) {var parsed=FishingManaParser.detailed(line);if(parsed.isPresent()){mana=parsed.get();manaAt=System.currentTimeMillis();}}
    boolean active(){return runtime.active();}
    String status(){return runtime.state().name();}
    String failure(){return runtime.failure();}
    private int find() {
        int result=-1;
        for(int i=0;i<9;i++) {
            var stack=client.player.getInventory().getItem(i);
            if(!stack.isEmpty()&&selected.itemId().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    &&selected.itemName().equals(plain(stack.getHoverName().getString()))) {
                if(result>=0)return -1;result=i;
            }
        }
        return result;
    }
    private static String plain(String text){return text.replaceAll("§.","");}
    private static ItemStack rodIdentity(ItemStack stack){var copy=stack.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    private List<ArmorStand> markers() {
        return client.level.getEntitiesOfClass(ArmorStand.class,client.player.getBoundingBox().inflate(2,3,2),
                entity->entity.isAlive()&&entity.hasCustomName());
    }
    private boolean markerPair(boolean fresh) {
        return selected.markerPair(markers().stream().map(stand->new BlackHoleProfile.Marker(stand.getUUID(),stand.getName().getString())).toList(),
                client.player.getGameProfile().name(),fresh?previousMarkers:Set.of());
    }
    /** The source availability rule is separate from the stricter configured fresh-use receipt. */
    private boolean sourceEffectPresent() {
        if (!SourceBlackHolePresence.applies(selected)) return false;
        var observations = client.level.getEntitiesOfClass(ArmorStand.class,
                client.player.getBoundingBox().inflate(2,3,2), entity -> true).stream()
                .map(stand -> new SourceBlackHolePresence.Observation(true, true, stand.getName().getString()))
                .toList();
        return SourceBlackHolePresence.present(observations, client.player.getGameProfile().name());
    }
    private boolean freshMana(long now){return mana!=null&&now>=manaAt&&now-manaAt<=2000&&mana.current()>=selected.manaCost();}
    boolean tick(boolean allow) {
        boolean was=active();long now=System.currentTimeMillis();
        if(client.player==null||client.level==null||client.gameMode==null) {
            if(was){runtime.fail("Black Hole native context disappeared");release();return true;}return false;
        }
        if(!failure().isEmpty())return true;
        if(!was) {
            if(!allow||!config.get(FishingConfig.FISHING_BLACK_HOLE)||client.gui.screen()!=null)return false;
            try {
                selected=BlackHoleProfile.parse(config.get(FishingConfig.FISHING_BLACK_HOLE_PROFILE));
                itemSlot=find();
                if(itemSlot<0||sourceEffectPresent()||markerPair(false)||now-lastUse<selected.renewalIntervalMillis()||!freshMana(now)
                        ||client.player.getCooldowns().isOnCooldown(client.player.getInventory().getItem(itemSlot)))return false;
                if(runtime.state()==FishingTotemController.State.COMPLETE)runtime.reset();
                runtime=new FishingTotemController(100,selected.requestTimeoutMillis());
                contract=config.get(FishingConfig.FISHING_BLACK_HOLE_PROFILE);player=client.player;world=client.level;
                anchor=client.player.position();yaw=client.player.getYRot();pitch=client.player.getXRot();
                rodSlot=client.player.getInventory().getSelectedSlot();rod=rodIdentity(client.player.getMainHandItem());
                if(!client.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)){runtime.fail("Black Hole requires native fishing rod ownership");return true;}
                prepared=restored=used=false;
            } catch(RuntimeException e){runtime.fail(e.getMessage());return true;}
        } else if(client.player!=player||client.level!=world||!client.player.isAlive()||client.gui.screen()!=null
                ||!config.get(FishingConfig.FISHING_BLACK_HOLE)||!Objects.equals(contract,config.get(FishingConfig.FISHING_BLACK_HOLE_PROFILE))) {
            runtime.fail("Black Hole context or contract changed");release();return true;
        }
        if(!owned&&!restored&&(client.player.getInventory().getSelectedSlot()!=rodSlot
                ||!ItemStack.matches(rod,rodIdentity(client.player.getInventory().getItem(rodSlot))))) {
            runtime.fail("Black Hole initial rod ownership changed");release();return true;
        }
        if(owned) {
            int expected=used?beforeCount-selected.itemConsumption():beforeCount;
            var held=client.player.getMainHandItem();
            if(client.player.getInventory().getSelectedSlot()!=itemSlot
                    ||!(expected==0&&held.isEmpty()||ItemStack.isSameItemSameComponents(item,held))
                    ||held.getCount()!=beforeCount&&held.getCount()!=expected) {
                runtime.fail("Black Hole selected tool ownership changed");release();return true;
            }
        }
        boolean aligned=prepared&&submerged;
        if(prepared&&!submerged&&(runtime.state()==FishingTotemController.State.AIM||runtime.state()==FishingTotemController.State.BUFFER)) {
            var delta=place.getLocation().subtract(client.player.getEyePosition());
            float targetYaw=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),targetPitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            rotation.track(targetYaw,targetPitch);rotating=true;
            aligned=Math.hypot(net.minecraft.util.Mth.wrapDegrees(targetYaw-client.player.getYRot()),targetPitch-client.player.getXRot())<1&&ray();
        }
        boolean ack=used&&markerPair(true)&&mana!=null&&manaAt>useAt&&now-manaAt<=2000
                &&mana.current()==beforeMana-selected.manaCost()
                &&client.player.getInventory().getItem(itemSlot).getCount()==beforeCount-selected.itemConsumption();
        var action=runtime.tick(new FishingTotemController.Input(!was,client.player.fishing!=null,prepared,aligned,ack,restored,now));
        switch(action) {
            case REEL -> useRod.run();
            case PREPARE -> prepare();
            case USE -> {
                var held=client.player.getMainHandItem();
                if(!freshMana(now)||client.player.getCooldowns().isOnCooldown(held)||(!submerged&&!ray())
                        ||client.player.isUnderWater()!=submerged||held.getCount()<selected.itemConsumption()) {
                    runtime.fail("Black Hole mana, cooldown or placement changed");break;
                }
                previousMarkers=markers().stream().map(ArmorStand::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet());
                beforeMana=mana.current();beforeCount=held.getCount();useAt=lastUse=now;used=true;
                if(submerged)client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                else client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,place);
            }
            case RESTORE -> {
                if(!restoreSlot()){runtime.fail("Rod changed during Black Hole placement");break;}
                rotation.track(yaw,pitch);rotating=true;
                restored=Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1;
            }
            default -> {}
        }
        if(runtime.state()==FishingTotemController.State.COMPLETE||runtime.state()==FishingTotemController.State.FAILED)release();
        return true;
    }
    private void prepare() {
        itemSlot=find();submerged=client.player.isUnderWater();place=submerged?null:placement();
        if(itemSlot<0||!submerged&&place==null){runtime.fail("No safe Black Hole placement");return;}
        item=client.player.getInventory().getItem(itemSlot).copy();beforeCount=item.getCount();
        dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,itemSlot);
        client.player.getInventory().setSelectedSlot(itemSlot);owned=prepared=true;
    }
    private BlockHitResult placement(){
        var eyes=client.player.getEyePosition();var origin=BlockPos.containing(eyes);double best=Double.MAX_VALUE;BlockHitResult result=null;
        for(int x=-3;x<=3;x++)for(int y=-2;y<=0;y++)for(int z=-3;z<=3;z++){
            var p=origin.offset(x,y,z);if(!client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4))continue;
            var ground=p.below();var shape=client.level.getBlockState(ground).getCollisionShape(client.level,ground);
            if(shape.isEmpty()||!client.level.getBlockState(p).getCollisionShape(client.level,p).isEmpty())continue;
            var point=new Vec3(p.getX()+.5,ground.getY()+shape.max(Direction.Axis.Y)-.001,p.getZ()+.5);double horizontal=Math.hypot(point.x-eyes.x,point.z-eyes.z);
            if(point.distanceTo(eyes)>4||horizontal<1.5||point.distanceTo(anchor)<1.5)continue;
            if(!client.level.getEntitiesOfClass(LivingEntity.class,client.player.getBoundingBox().inflate(5),e->Math.hypot(e.getX()-point.x,e.getZ()-point.z)<1.5).isEmpty())continue;
            var d=point.subtract(eyes);float target=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90);if(Math.abs(net.minecraft.util.Mth.wrapDegrees(target-yaw))<60)continue;
            var hit=client.level.clip(new ClipContext(eyes,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(ground)||hit.getDirection()!=Direction.UP)continue;
            double score=Math.abs(3-horizontal)+Math.abs(net.minecraft.util.Mth.wrapDegrees(target-client.player.getYRot()))/80+(point.distanceTo(anchor)<2.5?2:0);if(score<best){best=score;result=hit;}
        }return result;
    }
    private boolean ray(){var eyes=client.player.getEyePosition();var hit=client.level.clip(new ClipContext(eyes,eyes.add(client.player.getLookAngle().scale(4.5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));return place!=null&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(place.getBlockPos())&&hit.getDirection()==Direction.UP;}
    private boolean restoreSlot() {
        if(!owned)return true;
        if(client.player!=player||client.level!=world||client.player.getInventory().getSelectedSlot()!=itemSlot
                ||!ItemStack.matches(rod,rodIdentity(client.player.getInventory().getItem(rodSlot))))return false;
        dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,rodSlot);
        client.player.getInventory().setSelectedSlot(rodSlot);owned=false;return true;
    }
    private void release() {
        if(owned&&client.player==player&&client.level==world&&client.player!=null
                &&client.player.getInventory().getSelectedSlot()==itemSlot
                &&(ItemStack.isSameItemSameComponents(item,client.player.getMainHandItem())
                   ||used&&selected.itemConsumption()==beforeCount&&client.player.getMainHandItem().isEmpty()))restoreSlot();
        if(rotating)rotation.cancelRotation();rotating=false;
    }
    void stop(){release();runtime.reset();selected=null;player=world=null;prepared=restored=used=false;previousMarkers=Set.of();}
    boolean rebindIdle() {
        if(active()||owned||rotating||!failure().isEmpty())return false;
        selected=null;place=null;player=world=null;rod=item=ItemStack.EMPTY;mana=null;manaAt=0;
        previousMarkers=Set.of();beforeMana=beforeCount=0;prepared=restored=used=false;return true;
    }
}
