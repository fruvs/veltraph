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
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Exact Tara child port. Parent supplies the declared EGGS task; source boxes never authorize a hit. */
public final class McTaraSoulWhip {
    public record Result(boolean handled,boolean completed,boolean failed,String status) {}
    private record Body(Entity actor,int nativeId,UUID uuid,Vec3 lastPosition) {}
    private final Minecraft client;private final FarmServices services;private final TaraSoulWhip controller=new TaraSoulWhip();
    private TaraSoulWhip.Profile profile,frozenProfile;private LocalPlayer player;private Object world,connection,loadoutToken;
    private LivingEntity boss;private SlayerEntityOwnership.Owner bossOwner;private UUID objective;
    private List<ItemStack> inventory=List.of(),equipment=List.of();private int previousSlot,toolSlot=-1,bowSlot=-1,arrowSlot=-1,releases;
    private boolean borrowingBow,usingBow;
    private final Map<UUID,Body> bodies=new LinkedHashMap<>();private final Set<UUID> completed=new HashSet<>();
    private String failure="";
    private McTaraWaitingWhip preparedWaiting;
    public McTaraSoulWhip(Minecraft client,FarmServices services){this.client=client;this.services=services;}
    public void profile(TaraSoulWhip.Profile value){if(pending())throw new IllegalStateException("Stop Tara egg transaction before changing its profile");profile=value;}
    public boolean configured(){return profile!=null;}public boolean pending(){return controller.pending();}
    public void preparedBy(McTaraWaitingWhip waiting){if(pending())throw new IllegalStateException("Cannot replace an owned Tara callback handoff");preparedWaiting=waiting;}
    public TaraSoulWhip.Phase phase(){return controller.phase();}public String status(){return failure.isEmpty()?controller.status():failure;}
    public int sentUses(){return controller.sentUses();}
    public Optional<String> validation(){
        if(profile==null)return Optional.empty();if(client.player==null)return Optional.of("Tara native player unavailable");
        if(findTool(profile)<0||findBow(profile)<0||findArrow()<0)return Optional.of("Supply exact Tara primary/fallback Whip, separate native Bow and one ordinary arrow stack");return Optional.empty();
    }
    /** Root calls this before the ordinary egg/bow/melee action so the exact Tara child owns its phase. */
    public Result tick(LivingEntity selected,SlayerObjectives.Task task,boolean safetyHold,long now){
        if(profile==null)return result(false);
        if(!failure.isEmpty())return result(true);
        if(!pending()){
            if(task==null||task.kind()!=SlayerObjectives.Kind.EGGS||task.removed()||completed.contains(task.identity()))return result(false);
            if(!arm(selected,task,safetyHold,now))return result(true);
        }
        if(selected!=boss||task==null||!task.identity().equals(objective)||task.kind()!=SlayerObjectives.Kind.EGGS
                ||!Objects.equals(profile,frozenProfile)||!nativeCustody()||safetyHold)return fail("Tara egg parent lost selected boss/task/profile/loadout/native custody");
        var visible=SlayerEntityOwnership.visible(client);var currentOwner=SlayerEntityOwnership.own(client,boss,visible);
        if(currentOwner.isEmpty()||!currentOwner.get().equals(bossOwner)||!uniqueSourceBoss(visible))return fail("Tara egg group lost independently owned boss or has a competing Spider boss");
        if(!bodies.keySet().containsAll(task.bodies()))return fail("Declared egg group gained a new body during its frozen phase");
        var live=new ArrayList<UUID>();var removed=new HashSet<UUID>();
        for(var entry:bodies.entrySet()){
            var old=entry.getValue();var actor=client.level.getEntity(old.nativeId());
            if(!client.level.isLoaded(net.minecraft.core.BlockPos.containing(old.lastPosition())))return fail("Egg disappearance occurred outside loaded native chunks");
            if(actor!=null&&actor!=old.actor())return fail("Egg native entity id was replaced");
            if(actor==null||actor.isRemoved()){
                var box=old.actor().getBoundingBox();var eye=player.getEyePosition();
                var nearest=new Vec3(Math.clamp(eye.x,box.minX,box.maxX),Math.clamp(eye.y,box.minY,box.maxY),Math.clamp(eye.z,box.minZ,box.maxZ));
                if(eye.distanceToSqr(nearest)>frozenProfile.nativeRayLimit()*frozenProfile.nativeRayLimit())return fail("Egg disappearance moved outside the frozen native observation range");
                removed.add(old.uuid());continue;
            }
            if(!eligibleBody(actor)||!insideSourceBox(actor))return fail("Exact egg body lost physical source-box proof");
            entry.setValue(new Body(actor,actor.getId(),actor.getUUID(),actor.getClientPosition()));live.add(actor.getUUID());
        }
        var target=controller.target().map(bodies::get).map(Body::actor).orElse(null);
        boolean ray=target!=null&&!target.isRemoved()&&nativeRay(target,frozenProfile.nativeRayLimit());
        var frame=new TaraSoulWhip.Frame(custody(),objective,live,removed,true,true,ray,false,player.tickCount,now);
        var decision=controller.tick(frame);
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(decision.action()==TaraSoulWhip.Action.FAIL)return fail(decision.detail());
        if(decision.action()==TaraSoulWhip.Action.CONTINUE){completed.add(objective);releaseAim();return result(true);}
        if(target!=null&&!target.isRemoved()){
            // Track through the existing parent camera. Only the actually applied native pose gates use.
            var delta=target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            services.rotation().track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        }
        if(decision.action()==TaraSoulWhip.Action.PREPARE_WHIP){
            services.inventory().selectSlot(toolSlot);
            try{
                if(!same(inventory.get(toolSlot),player.getMainHandItem()))return fail("Temporary Tara tool selection changed full native components");
            }finally{
                // Restore only the exact native owner that borrowed the slot. Never overwrite a new owner.
                if(client.player==player&&client.level==world&&client.getConnection()==connection&&services.inventory().selectedSlot()==toolSlot)
                    services.inventory().selectSlot(previousSlot);
            }
        }
        if(decision.action()==TaraSoulWhip.Action.PREPARE_BOW){
            borrowingBow=true;services.inventory().selectSlot(bowSlot);
        }
        if(decision.action()==TaraSoulWhip.Action.DRAW_BOW){
            if(client.gameMode==null||!nativeCustody()||!nativeRay(target,frozenProfile.nativeRayLimit()))return fail("Native Tara Bow draw lost exact tool/ray custody");
            if(!borrowingBow){borrowingBow=true;services.inventory().selectSlot(bowSlot);}
            client.gameMode.useItem(player,InteractionHand.MAIN_HAND);
            if(!player.isUsingItem()||!player.getUseItem().is(Items.BOW))return fail("Ordinary native Bow did not start its draw");
            usingBow=true;dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,true);
        }
        if(decision.action()==TaraSoulWhip.Action.RELEASE_BOW){
            if(!usingBow||client.gameMode==null||!nativeCustody()||!nativeRay(target,frozenProfile.nativeRayLimit()))return fail("Tara Bow release lost its owned native draw/ray");
            releases++;client.gameMode.releaseUsingItem(player);usingBow=false;
            dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);
            controller.sent(decision.target(),now);
            if(client.player==player&&client.level==world&&client.getConnection()==connection&&services.inventory().selectedSlot()==bowSlot)services.inventory().selectSlot(previousSlot);
            borrowingBow=false;
            if(!nativeCustody())return fail("Native Tara Bow release changed undeclared loadout components");
        }
        return result(true);
    }
    private boolean arm(LivingEntity selected,SlayerObjectives.Task task,boolean safetyHold,long now){
        if(client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||selected==null||!selected.isAlive()
                ||task.bodies().isEmpty()||task.bodies().size()>64||!task.present()||!task.actionable()||safetyHold||client.options.keyUse.isDown()||client.player.isUsingItem()){fail("Declared Tara phase lacks native actionable body or idle use-control proof");return false;}
        player=client.player;world=client.level;connection=client.getConnection();boss=selected;objective=task.identity();frozenProfile=profile;bodies.clear();releases=0;usingBow=borrowingBow=false;
        var entities=SlayerEntityOwnership.visible(client);var owned=SlayerEntityOwnership.own(client,boss,entities);
        if(owned.isEmpty()){fail("Tara selected boss lacks an independent exact own-player plate");return false;}bossOwner=owned.get();
        toolSlot=findTool(profile);bowSlot=findBow(profile);arrowSlot=findArrow();if(toolSlot<0||bowSlot<0||arrowSlot<0){fail("Tara requires separate exact Whip/Bow and ordinary arrow identities");return false;}
        previousSlot=services.inventory().selectedSlot();inventory=snapshotInventory();equipment=snapshotEquipment();loadoutToken=new Object();bodies.clear();
        var eligible=new ArrayList<TaraEggGroups.Stand>();
        for(var id:task.bodies()){
            Entity actor=entities.stream().filter(entity->entity.getUUID().equals(id)).findFirst().orElse(null);
            if(actor==null||!eligibleBody(actor)||!insideSourceBox(actor)||!client.level.isLoaded(actor.blockPosition())){fail("Declared Tara egg is not an exact loaded physical source-box body");return false;}
            bodies.put(id,new Body(actor,actor.getId(),id,actor.getClientPosition()));eligible.add(new TaraEggGroups.Stand(id,actor.getX(),actor.getY(),actor.getZ()));
        }
        var groups=TaraEggGroups.plan(boss.getX(),boss.getY(),boss.getZ(),eligible);
        if(groups.isEmpty()){fail("Declared Tara egg group has no source planning column");return false;}
        if(!uniqueSourceBoss(entities)){fail("Tara declared egg group is ambiguous between Spider bosses");return false;}
        // Preserve group membership and source Y order; do not pick a nearby unrelated stand.
        var ordered=groups.stream().flatMap(group->group.members().stream()).toList();var sorted=new LinkedHashMap<UUID,Body>();for(var id:ordered)sorted.put(id,bodies.get(id));bodies.clear();bodies.putAll(sorted);
        var frame=new TaraSoulWhip.Frame(custody(),objective,ordered,Set.of(),true,true,false,false,player.tickCount,now);
        if(preparedWaiting!=null&&!preparedWaiting.preparedFor(selected,profile)){fail("WAITING Whip callback handoff lost its exact parent/native/loadout proof");return false;}
        var action=preparedWaiting!=null?controller.beginAfterOwnedWhip(profile,frame):controller.begin(profile,frame);
        if(action.action()==TaraSoulWhip.Action.FAIL){fail(action.detail());return false;}return true;
    }
    private int findTool(TaraSoulWhip.Profile chosen){
        if(client.player==null)return -1;
        for(var tool:List.of(chosen.primary(),chosen.fallback())){int found=-1;
            for(int slot=0;slot<9;slot++){var item=client.player.getInventory().getItem(slot);
                if(!item.isEmpty()&&item.getHoverName().getString().equals(tool.exactName())&&BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(tool.itemId())){
                    if(found>=0)return -1;found=slot;
                }
            }if(found>=0)return found;
        }return -1;
    }
    private int findBow(TaraSoulWhip.Profile chosen){if(client.player==null)return -1;int found=-1;for(int slot=0;slot<9;slot++){var item=client.player.getInventory().getItem(slot);
        if(item.is(Items.BOW)&&item.getHoverName().getString().equals(chosen.bow().exactName())){if(found>=0)return -1;found=slot;}}return found;}
    private int findArrow(){if(client.player==null||client.player.getOffhandItem().is(Items.ARROW))return -1;int found=-1;
        for(int slot=0;slot<client.player.getInventory().getContainerSize();slot++){var item=client.player.getInventory().getItem(slot);if(item.is(Items.ARROW)){if(found>=0)return -1;found=slot;}
            else if(item.is(Items.TIPPED_ARROW)||item.is(Items.SPECTRAL_ARROW))return -1;}return found;}
    private boolean uniqueSourceBoss(List<Entity> visible){
        if(SlayerSignals.family(boss.getName().getString())!=SlayerSignals.Family.SPIDER
                &&SlayerEntityOwnership.name(client,boss,visible,name->SlayerSignals.family(name)==SlayerSignals.Family.SPIDER).isEmpty())return false;
        for(var actor:visible)if(actor!=boss&&actor instanceof LivingEntity living&&!(actor instanceof ArmorStand)&&actor!=player&&actor.isAlive()
                &&(SlayerSignals.family(actor.getName().getString())==SlayerSignals.Family.SPIDER||SlayerEntityOwnership.name(client,living,visible,name->SlayerSignals.family(name)==SlayerSignals.Family.SPIDER).isPresent())){
            for(var body:bodies.values())if(Math.abs(body.lastPosition().x-actor.getClientPosition().x)<=5&&Math.abs(body.lastPosition().z-actor.getClientPosition().z)<=5
                    &&body.lastPosition().y>=actor.getClientPosition().y&&body.lastPosition().y<=actor.getClientPosition().y+10)return false;
        }return true;
    }
    private boolean insideSourceBox(Entity actor){var origin=boss.getClientPosition();var p=actor.getClientPosition();return Math.abs(p.x-origin.x)<=5&&Math.abs(p.z-origin.z)<=5&&p.y>=origin.y&&p.y<=origin.y+10;}
    private static boolean eligibleBody(Entity actor){return actor instanceof ArmorStand stand&&actor.isAlive()&&!stand.isMarker()&&actor.isPickable()&&!actor.hasCustomName();}
    private List<ItemStack> snapshotInventory(){var result=new ArrayList<ItemStack>();for(int slot=0;slot<player.getInventory().getContainerSize();slot++)result.add(player.getInventory().getItem(slot).copy());return List.copyOf(result);}
    private List<ItemStack> snapshotEquipment(){var result=new ArrayList<ItemStack>();for(var slot:EquipmentSlot.values())result.add(player.getItemBySlot(slot).copy());return List.copyOf(result);}
    private static boolean same(ItemStack a,ItemStack b){return a.getCount()==b.getCount()&&ItemStack.isSameItemSameComponents(a,b);}
    private boolean nativeCustody(){
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||!player.isAlive()||client.gui.screen()!=null
                ||player.containerMenu!=player.inventoryMenu||!player.containerMenu.getCarried().isEmpty()||services.inventory().selectedSlot()!=(borrowingBow?bowSlot:previousSlot)
                ||client.level.getEntity(boss.getId())!=boss||!boss.isAlive())return false;
        if(inventory.size()!=player.getInventory().getContainerSize())return false;
        for(int slot=0;slot<inventory.size();slot++){var original=inventory.get(slot);var actual=player.getInventory().getItem(slot);
            if(same(original,actual))continue;
            if(slot==bowSlot&&actual.getCount()==original.getCount()&&actual.getDamageValue()>=original.getDamageValue()&&actual.getDamageValue()-original.getDamageValue()<=releases){var expected=original.copy();expected.setDamageValue(actual.getDamageValue());if(same(expected,actual))continue;}
            if(slot==arrowSlot&&actual.getCount()<=original.getCount()&&original.getCount()-actual.getCount()<=releases){var expected=original.copy();expected.setCount(actual.getCount());if(same(expected,actual))continue;}
            return false;
        }
        int index=0;for(var slot:EquipmentSlot.values()){var original=equipment.get(index++);if(slot==EquipmentSlot.MAINHAND){if(!same(player.getItemBySlot(slot),player.getInventory().getItem(borrowingBow?bowSlot:previousSlot)))return false;}
            else if(!same(original,player.getItemBySlot(slot)))return false;}
        return !usingBow||player.isUsingItem()&&player.getUseItem().is(Items.BOW)&&client.options.keyUse.isDown();
    }
    private TaraSoulWhip.Custody custody(){return new TaraSoulWhip.Custody(player.getUUID(),boss.getUUID(),bossOwner.plate(),world,connection,loadoutToken);}
    private boolean nativeRay(Entity target,double limit){
        if(target==null||!eligibleBody(target)||client.level.getEntity(target.getId())!=target||!player.hasLineOfSight(target))return false;
        var eye=player.getEyePosition();var end=eye.add(player.getViewVector(1).scale(limit));
        var crossing=target.getBoundingBox().contains(eye)?Optional.of(eye):target.getBoundingBox().clip(eye,end);if(crossing.isEmpty())return false;
        var point=crossing.get();if(client.level.clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)return false;
        double distance=eye.distanceToSqr(point);
        for(var other:client.level.entitiesForRendering())if(other!=target&&other!=player&&!other.isRemoved()&&other.isPickable()){
            var blocked=other.getBoundingBox().contains(eye)?Optional.of(eye):other.getBoundingBox().clip(eye,end);
            if(blocked.isPresent()&&eye.distanceToSqr(blocked.get())<=distance+1e-7)return false;
        }return true;
    }
    private void releaseAim(){
        if(usingBow){dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);
            if(client.player==player&&client.level==world&&client.getConnection()==connection&&services.inventory().selectedSlot()==bowSlot){player.stopUsingItem();services.inventory().selectSlot(previousSlot);}}
        else if(borrowingBow&&client.player==player&&client.level==world&&client.getConnection()==connection&&services.inventory().selectedSlot()==bowSlot)services.inventory().selectSlot(previousSlot);
        usingBow=borrowingBow=false;services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);}
    private Result fail(String message){failure=message;controller.stop(message);releaseAim();return result(true);}
    private Result result(boolean handled){return new Result(handled,handled&&controller.complete(),!failure.isEmpty()||controller.failed(),status());}
    public void stop(){failure="Tara parent stopped; native use canceled";controller.stop(failure);releaseAim();}
    public void reset(){stop();controller.reset();failure="";frozenProfile=null;player=null;world=connection=loadoutToken=null;boss=null;bossOwner=null;objective=null;
        inventory=equipment=List.of();bodies.clear();completed.clear();toolSlot=bowSlot=arrowSlot=-1;releases=0;usingBow=borrowingBow=false;preparedWaiting=null;}
}
