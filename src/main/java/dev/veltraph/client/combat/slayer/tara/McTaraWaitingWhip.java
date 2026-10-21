package dev.veltraph.client.combat.slayer.tara;

import dev.veltraph.client.combat.slayer.*;
import dev.veltraph.client.service.FarmServices;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Declared WAITING skill port. Profile text/effect binds a local contract, never universal source skill semantics. */
public final class McTaraWaitingWhip {
    private final Minecraft client;private final FarmServices services;private final TaraWaitingWhip loop=new TaraWaitingWhip();
    private TaraWaitingWhip.Profile profile,frozen;private LocalPlayer player;private LivingEntity boss;private SlayerEntityOwnership.Owner owner;
    private Object world,connection,loadout;private List<ItemStack> inventory=List.of(),equipment=List.of();private int previousSlot,toolSlot=-1;private String failure="";
    public McTaraWaitingWhip(Minecraft client,FarmServices services){this.client=client;this.services=services;}
    public void profile(TaraWaitingWhip.Profile value){if(pending())throw new IllegalStateException("Stop WAITING Whip before profile changes");profile=value;}
    public boolean configured(){return profile!=null;}public boolean pending(){return loop.pending();}public boolean complete(){return loop.complete();}public boolean failed(){return !failure.isEmpty()||loop.failed();}public String status(){return failure.isEmpty()?loop.status():failure;}public int sentUses(){return loop.uses();}
    public void message(String text,long now){loop.message(text,now);}
    public boolean preparedFor(LivingEntity selected,TaraSoulWhip.Profile eggProfile){return complete()&&!failed()&&selected==boss&&nativeCustody()
            &&frozen.primary().equals(eggProfile.primary())&&frozen.fallback().equals(eggProfile.fallback());}
    public boolean tick(LivingEntity selected,boolean safetyHold,long now){
        if(profile==null)return false;if(failed()||complete())return true;
        if(!pending()){
            if(client.player==null||client.level==null||client.gameMode==null||client.getConnection()==null||selected==null||!selected.isAlive()||safetyHold)return fail("Declared WAITING Whip native owner unavailable");
            player=client.player;boss=selected;world=client.level;connection=client.getConnection();frozen=profile;
            var proof=SlayerEntityOwnership.own(client,boss,SlayerEntityOwnership.visible(client));if(proof.isEmpty())return fail("WAITING Whip lacks independently owned boss");owner=proof.get();
            if(SlayerSignals.family(boss.getName().getString())!=SlayerSignals.Family.SPIDER&&SlayerEntityOwnership.name(client,boss,SlayerEntityOwnership.visible(client),s->SlayerSignals.family(s)==SlayerSignals.Family.SPIDER).isEmpty())return fail("WAITING Whip selected body is not an observed Spider boss");
            toolSlot=findTool();if(toolSlot<0||client.options.keyUse.isDown())return fail("Exact named WAITING Whip tool or idle native use control unavailable");previousSlot=services.inventory().selectedSlot();
            var stacks=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++)stacks.add(player.getInventory().getItem(i).copy());inventory=List.copyOf(stacks);
            stacks.clear();for(var slot:EquipmentSlot.values())stacks.add(player.getItemBySlot(slot).copy());equipment=List.copyOf(stacks);loadout=new Object();
            loop.begin(profile,new TaraWaitingWhip.Frame(custody(),boss.getHealth(),ready(),false,true,false,player.tickCount,now));
        }
        if(selected!=boss||!Objects.equals(frozen,profile)||!nativeCustody()||safetyHold)return fail("WAITING Whip lost exact native parent/profile/loadout custody");
        var action=loop.tick(new TaraWaitingWhip.Frame(custody(),boss.getHealth(),ready(),ray(),client.level.isLoaded(boss.blockPosition()),false,player.tickCount,now));
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(action==TaraWaitingWhip.Action.FAIL)return fail(loop.status());if(action==TaraWaitingWhip.Action.COMPLETE){services.rotation().cancelRotation();return true;}
        var delta=boss.getBoundingBox().getCenter().subtract(player.getEyePosition());services.rotation().track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(action==TaraWaitingWhip.Action.PREPARE_WHIP||action==TaraWaitingWhip.Action.USE_WHIP){
            if(action==TaraWaitingWhip.Action.USE_WHIP&&(!ray()||client.gameMode==null))return fail("WAITING Whip native ray changed before ordinary use");
            services.inventory().selectSlot(toolSlot);try{if(!same(inventory.get(toolSlot),player.getMainHandItem()))return fail("WAITING temporary tool components changed");
                if(action==TaraWaitingWhip.Action.USE_WHIP){client.gameMode.useItem(player,InteractionHand.MAIN_HAND);loop.sent(now);}}
            finally{if(client.player==player&&client.level==world&&client.getConnection()==connection&&services.inventory().selectedSlot()==toolSlot)services.inventory().selectSlot(previousSlot);}
        }return true;
    }
    private int findTool(){for(var tool:List.of(profile.primary(),profile.fallback())){int found=-1;for(int slot=0;slot<9;slot++){var item=player.getInventory().getItem(slot);if(!item.isEmpty()&&item.getHoverName().getString().equals(tool.exactName())&&BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(tool.itemId())){if(found>=0)return -1;found=slot;}}if(found>=0)return found;}return -1;}
    private boolean ready(){var visible=SlayerEntityOwnership.visible(client);var cues=visible.stream().filter(e->e instanceof ArmorStand&&e.isAlive()&&e.hasCustomName()&&e.getName().getString().equals(frozen.exactReadyPlate())&&client.level.isLoaded(e.blockPosition())&&Math.abs(e.getX()-boss.getX())<=5&&Math.abs(e.getZ()-boss.getZ())<=5&&e.getY()>=boss.getY()&&e.getY()<=boss.getY()+10).toList();if(cues.size()!=1)return false;var cue=cues.getFirst();
        return visible.stream().noneMatch(e->e!=boss&&e instanceof LivingEntity living&&!(e instanceof ArmorStand)&&e!=player&&e.isAlive()
                &&(SlayerSignals.family(e.getName().getString())==SlayerSignals.Family.SPIDER||SlayerEntityOwnership.name(client,living,visible,s->SlayerSignals.family(s)==SlayerSignals.Family.SPIDER).isPresent())
                &&Math.abs(e.getX()-cue.getX())<=5&&Math.abs(e.getZ()-cue.getZ())<=5&&cue.getY()>=e.getY()&&cue.getY()<=e.getY()+10);}
    private TaraSoulWhip.Custody custody(){return new TaraSoulWhip.Custody(player.getUUID(),boss.getUUID(),owner.plate(),world,connection,loadout);}
    private static boolean same(ItemStack a,ItemStack b){return a.getCount()==b.getCount()&&ItemStack.isSameItemSameComponents(a,b);}
    private boolean nativeCustody(){if(client.player!=player||client.level!=world||client.getConnection()!=connection||client.gameMode==null||!player.isAlive()||!boss.isAlive()||client.level.getEntity(boss.getId())!=boss||client.gui.screen()!=null||player.containerMenu!=player.inventoryMenu||!player.containerMenu.getCarried().isEmpty()||services.inventory().selectedSlot()!=previousSlot)return false;
        var proof=SlayerEntityOwnership.own(client,boss,SlayerEntityOwnership.visible(client));if(proof.isEmpty()||!proof.get().equals(owner)||inventory.size()!=player.getInventory().getContainerSize())return false;
        for(int i=0;i<inventory.size();i++)if(!same(inventory.get(i),player.getInventory().getItem(i)))return false;int i=0;for(var slot:EquipmentSlot.values())if(!same(equipment.get(i++),player.getItemBySlot(slot)))return false;return true;}
    private boolean ray(){var eye=player.getEyePosition();var end=eye.add(player.getViewVector(1).scale(frozen.nativeRayLimit()));var crossing=boss.getBoundingBox().clip(eye,end);if(crossing.isEmpty()||!player.hasLineOfSight(boss)||client.level.clip(new ClipContext(eye,crossing.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)return false;
        for(var e:client.level.entitiesForRendering())if(e!=player&&e!=boss&&!e.isRemoved()&&e.isPickable()){var hit=e.getBoundingBox().clip(eye,end);if(hit.isPresent()&&eye.distanceToSqr(hit.get())<=eye.distanceToSqr(crossing.get())+1e-7)return false;}return true;}
    private boolean fail(String reason){failure=reason;loop.stop(reason);services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);return true;}
    public void stop(){fail("WAITING Whip parent stopped");}public void reset(){stop();loop.reset();failure="";frozen=null;player=null;boss=null;owner=null;world=connection=loadout=null;inventory=equipment=List.of();toolSlot=-1;}
}
