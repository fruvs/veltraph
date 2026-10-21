package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.*;
import dev.veltraph.client.fishing.support.OrbProfile;
import dev.veltraph.client.service.RotationService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Source support placement, acknowledged by native effect + exact declared consumption, never marker names. */
final class McFishingOrb {
    private final Minecraft client;private final ConfigService config;private final RotationService rotation;private final Runnable useRod;
    private final FishingTotemController runtime=new FishingTotemController(100,4000);
    private FishingManaParser.Mana mana;private long manaAt,useAt,lastUse;private OrbProfile selected;
    private String contract;private Object player,world;private int rodSlot,itemSlot,beforeMana,beforeCount,beforeEffect;
    private ItemStack rod=ItemStack.EMPTY,item=ItemStack.EMPTY;private Holder<MobEffect> effect;
    private BlockHitResult place;private Vec3 anchor;private float yaw,pitch;private boolean owned,rotating,prepared,restored,used,submerged;
    McFishingOrb(Minecraft c,ConfigService cfg,RotationService r,Runnable use){client=c;config=cfg;rotation=r;useRod=use;}
    static String problem(ConfigService cfg){if(!cfg.get(FishingConfig.FISHING_AUTO_POWER_ORB))return "";try{OrbProfile.parse(cfg.get(FishingConfig.FISHING_ORB_PROFILES));return "";}catch(RuntimeException e){return e.getMessage();}}
    void mana(String line){var parsed=FishingManaParser.detailed(line);if(parsed.isPresent()){mana=parsed.get();manaAt=System.currentTimeMillis();}}
    boolean active(){return runtime.active();}String status(){return runtime.state().name();}String failure(){return runtime.failure();}
    private int ticks(){var value=client.player.getEffect(effect);return value==null?0:value.isInfiniteDuration()?Integer.MAX_VALUE:value.getDuration();}
    private int find(OrbProfile row){int match=-1;for(int i=0;i<9;i++){var stack=client.player.getInventory().getItem(i);if(row.itemId().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())&&row.itemName().equals(stack.getHoverName().getString().replaceAll("§.",""))){if(match>=0)return -1;match=i;}}return match;}
    boolean tick(boolean allow){
        if(client.player==null||client.level==null||client.gameMode==null)return false;long now=System.currentTimeMillis();boolean was=active();
        if(!was){
            if(!allow||!config.get(FishingConfig.FISHING_AUTO_POWER_ORB)||mana==null||now-manaAt>2000||client.gui.screen()!=null)return false;
            try{
                for(var row:OrbProfile.parse(config.get(FishingConfig.FISHING_ORB_PROFILES))){
                    int slot=find(row);if(slot<0)continue;var holder=BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(row.effectId()));if(holder.isEmpty())throw new IllegalArgumentException("Unknown native orb effect");
                    selected=row;effect=holder.get();itemSlot=slot;if(!row.ready(mana.current(),mana.maximum())||now-lastUse<row.minUseIntervalMillis()||now-lastUse<(row.durationSeconds()-2)*1000L||ticks()>40)return false;
                    contract=config.get(FishingConfig.FISHING_ORB_PROFILES);player=client.player;world=client.level;anchor=client.player.position();yaw=client.player.getYRot();pitch=client.player.getXRot();rodSlot=client.player.getInventory().getSelectedSlot();prepared=restored=used=false;break;
                }
            }catch(RuntimeException e){runtime.fail(e.getMessage());return true;}
            if(selected==null||find(selected)<0||ticks()>40)return false;
        }else if(client.player!=player||client.level!=world||!client.player.isAlive()||!config.get(FishingConfig.FISHING_AUTO_POWER_ORB)||!Objects.equals(contract,config.get(FishingConfig.FISHING_ORB_PROFILES))){runtime.fail("Orb context or contract changed");release();return true;}
        if(owned){
            int expected=used?beforeCount-selected.itemConsumption():beforeCount;var stack=client.player.getMainHandItem();
            if(client.player.getInventory().getSelectedSlot()!=itemSlot||!(expected==0&&stack.isEmpty()||ItemStack.isSameItemSameComponents(item,stack))||stack.getCount()!=expected&&stack.getCount()!=beforeCount){runtime.fail("Orb slot/item ownership changed");release();return true;}
        }
        boolean aligned=prepared&&submerged;
        if(prepared&&!submerged&&(runtime.state()==FishingTotemController.State.AIM||runtime.state()==FishingTotemController.State.BUFFER)){
            var d=place.getLocation().subtract(client.player.getEyePosition());float y=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),p=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
            rotation.track(y,p);rotating=true;aligned=Math.hypot(net.minecraft.util.Mth.wrapDegrees(y-client.player.getYRot()),p-client.player.getXRot())<1&&ray();
        }
        boolean ack=used&&manaAt>=useAt&&mana!=null&&mana.current()==beforeMana-selected.manaCost()&&client.player.getInventory().getItem(itemSlot).getCount()==beforeCount-selected.itemConsumption()
                &&ticks()>beforeEffect&&ticks()>=selected.durationSeconds()*20-8&&client.player.getEffect(effect).getAmplifier()==selected.amplifier();
        var action=runtime.tick(new FishingTotemController.Input(!was,client.player.fishing!=null,prepared,aligned,ack,restored,now));
        switch(action){
            case REEL -> useRod.run();
            case PREPARE -> prepare();
            case USE -> {
                if(mana==null||now-manaAt>2000||!selected.ready(mana.current(),mana.maximum())||(!submerged&&!ray())){runtime.fail("Orb mana or placement changed");break;}
                beforeMana=mana.current();beforeCount=client.player.getMainHandItem().getCount();beforeEffect=ticks();useAt=lastUse=now;used=true;
                if(submerged)client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);else client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,place);
            }
            case RESTORE -> {if(!restoreSlot()){runtime.fail("Rod changed during orb placement");break;}rotation.track(yaw,pitch);rotating=true;restored=Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1;}
            default -> {}
        }
        if(runtime.state()==FishingTotemController.State.COMPLETE||runtime.state()==FishingTotemController.State.FAILED)release();
        return true;
    }
    private static ItemStack rodIdentity(ItemStack stack){var copy=stack.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    private void prepare(){
        itemSlot=find(selected);submerged=client.player.isUnderWater();place=submerged?null:placement();
        if(itemSlot<0||!submerged&&place==null){runtime.fail("No safe orb placement");return;}
        rod=rodIdentity(client.player.getInventory().getItem(rodSlot));item=client.player.getInventory().getItem(itemSlot).copy();beforeCount=item.getCount();client.player.getInventory().setSelectedSlot(itemSlot);owned=prepared=true;
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
    private boolean restoreSlot(){if(!owned)return true;if(client.player!=player||client.level!=world||client.player.getInventory().getSelectedSlot()!=itemSlot||!ItemStack.matches(rod,rodIdentity(client.player.getInventory().getItem(rodSlot))))return false;client.player.getInventory().setSelectedSlot(rodSlot);owned=false;return true;}
    private void release(){if(owned&&client.player==player&&client.level==world&&client.player.getInventory().getSelectedSlot()==itemSlot&&(ItemStack.isSameItemSameComponents(item,client.player.getMainHandItem())||used&&selected.itemConsumption()==beforeCount&&client.player.getMainHandItem().isEmpty()))restoreSlot();if(rotating)rotation.cancelRotation();rotating=false;}
    void stop(){release();runtime.reset();selected=null;player=world=null;prepared=restored=used=false;}
    boolean rebindIdle(){if(active()||owned||rotating||!failure().isEmpty())return false;selected=null;effect=null;place=null;player=world=null;rod=item=ItemStack.EMPTY;mana=null;manaAt=0;beforeEffect=beforeMana=beforeCount=0;prepared=restored=used=false;return true;}
}
