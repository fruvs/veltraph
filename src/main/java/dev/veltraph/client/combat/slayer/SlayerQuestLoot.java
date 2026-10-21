package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import dev.veltraph.client.combat.grind.CombatLoop;
import dev.veltraph.client.combat.grind.CombatTarget;
import dev.veltraph.client.combat.grind.MeleeGrindModule;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Source-scoped opt-in quest loot, with explicit unresolved labels/limits and native delivery proof. */
public final class SlayerQuestLoot {
    public enum State { WATCHING, WAITING, WALKING, COMPLETE, FAILED }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final McNavigator nav;private final McSharedMovementNavigator travel;private LivingEntity boss;private Object world,connection;private UUID player,bossId;
    private Vec3 deathOrigin,goal;private final Set<UUID> prior=new HashSet<>();
    private final Map<UUID,ItemStack> contents=new LinkedHashMap<>();private final SlayerLootLedger ledger=new SlayerLootLedger();
    private final Map<Integer,String> observedNames=new HashMap<>();
    private State state=State.WATCHING;private String status="No observed quest boss";private long after,deadline;private boolean awaiting,dead,walking;
    public SlayerQuestLoot(Minecraft client,FarmServices services,ConfigService config){this.client=client;this.services=services;this.config=config;nav=new McNavigator(client,services.input(),config);travel=new McSharedMovementNavigator(client,services,config);}
    public static Optional<String> validation(ConfigService c){
        if(!c.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS))return Optional.empty();
        if(c.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES).isBlank()||c.get(SlayerConfig.SLAYER_QUEST_DROP_NAMES).isBlank())return Optional.of("Configure exact Slayer quest boss/drop names before enabling quest loot.");
        long timeout=c.get(SlayerConfig.SLAYER_QUEST_LOOT_TIMEOUT_MS);double radius=c.get(SlayerConfig.SLAYER_QUEST_DROP_RADIUS);
        if(timeout<500||timeout>60000||!Double.isFinite(radius)||radius<=0||radius>16)return Optional.of("Configure source-unresolved Slayer loot timeout (500-60000ms) and radius (0-16 blocks).");
        return Optional.empty();
    }
    public void reset(){travel.stop();boss=null;world=connection=null;player=bossId=null;deathOrigin=goal=null;prior.clear();contents.clear();observedNames.clear();ledger.reset();state=State.WATCHING;awaiting=dead=walking=false;status="No observed quest boss";}
    public void watch(MeleeGrindModule.SelectedTarget target){
        LivingEntity selected=target.body();
        if(awaiting||selected==null||!selected.isAlive()||!CombatLoop.matches(target.observedName(),config.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES)))return;
        if(boss!=null)return;
        boss=selected;bossId=selected.getUUID();world=client.level;connection=client.getConnection();player=client.player.getUUID();after=SlayerPickupSignal.serial(world,connection);
        for(var entity:client.level.entitiesForRendering())if(entity instanceof ItemEntity)prior.add(entity.getUUID());status="Observed configured quest boss "+target.observedName();
    }
    public boolean begin(long now){if(state==State.FAILED)return false;if(boss==null||world!=client.level||connection!=client.getConnection()){state=State.FAILED;status="Quest completion has no observed configured boss ownership";return false;}awaiting=true;deadline=now+config.get(SlayerConfig.SLAYER_QUEST_LOOT_TIMEOUT_MS);travel.stop();walking=false;state=State.WAITING;return true;}
    public boolean transactionPending(){return boss!=null;}
    public boolean pending(){return awaiting&&state!=State.COMPLETE&&state!=State.FAILED;}
    public State state(){return state;}public String status(){return status;}public int delivered(){return ledger.delivered();}
    private static Vec3 point(net.minecraft.world.entity.Entity entity){return new Vec3(entity.getX(),entity.getY(),entity.getZ());}
    private int count(ItemStack prototype){int n=0;for(int slot=0;slot<36;slot++){var stack=client.player.getInventory().getItem(slot);if(ItemStack.isSameItemSameComponents(stack,prototype))n+=stack.getCount();}return n;}
    public void observe(List<CombatTarget> targets){
        if(state==State.FAILED)return;
        observedNames.clear();for(var target:targets)if(target.alive()&&!target.protectedTarget())observedNames.put(target.id(),target.name());
        if(boss==null||world!=client.level||connection!=client.getConnection()||client.player==null||!client.player.getUUID().equals(player))return;
        if(!boss.getUUID().equals(bossId)||!client.level.isLoaded(boss.blockPosition()))return;
        if(!dead){deathOrigin=point(boss);dead=boss.getHealth()<=0;
            if(!dead){for(var e:client.level.entitiesForRendering())if(e instanceof ItemEntity)prior.add(e.getUUID());return;}}
        // Nearby competing configured bosses make this unlabelled native drop origin ambiguous.
        boolean ambiguous=false;double radius=config.get(SlayerConfig.SLAYER_QUEST_DROP_RADIUS);
        for(var e:client.level.entitiesForRendering())if(e instanceof LivingEntity other&&!(other instanceof net.minecraft.world.entity.decoration.ArmorStand)&&other!=boss&&other.isAlive()
                &&CombatLoop.matches(observedNames.getOrDefault(other.getId(),other.getName().getString()),config.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES))&&point(other).horizontalDistanceTo(deathOrigin)<=radius)ambiguous=true;
        if(ambiguous){fail("Observed quest death/drop origin has competing configured bosses; ownership is ambiguous");return;}
        if(!ambiguous)for(var e:client.level.entitiesForRendering())if(e instanceof ItemEntity item&&item.isAlive()&&!prior.contains(item.getUUID())
                &&CombatLoop.matches(item.getItem().getHoverName().getString(),config.get(SlayerConfig.SLAYER_QUEST_DROP_NAMES))
                &&point(item).horizontalDistanceTo(deathOrigin)<=radius&&Math.abs(item.getY()-deathOrigin.y())<3
                &&client.level.isLoaded(item.blockPosition())&&(item.getOwner()==null||item.getOwner()==client.player)){
            if(!contents.containsKey(item.getUUID()))try{var prototype=item.getItem().copyWithCount(1);ledger.track(item.getUUID(),NativeStackIdentity.capture(prototype,client.level.registryAccess()),item.getItem().getCount(),count(prototype));contents.put(item.getUUID(),prototype);}catch(RuntimeException unsupported){fail("Unsupported full native drop identity");return;}
        }
        for(var receipt:SlayerPickupSignal.read(world,connection,player,after)){after=Math.max(after,receipt.serial());ledger.receipt(receipt.item(),receipt.content(),receipt.amount());}
        for(var entry:contents.entrySet())ledger.inventory(entry.getKey(),count(entry.getValue()));
        if(ledger.invalid())fail("Tracked native drop count changed or receipt exceeded observed stack");
    }
    public void tick(long now,List<CombatTarget> targets){
        if(!pending())return;
        if(world!=client.level||connection!=client.getConnection()||client.player==null||!client.player.getUUID().equals(player)||!client.player.isAlive()
                ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()){fail("Quest loot lost native owner or closed-menu proof");return;}
        observe(targets);if(state==State.FAILED)return;
        if(ledger.complete()){travel.stop();walking=false;state=State.COMPLETE;awaiting=false;status="Exact native quest drop and full inventory delivery acknowledged: "+ledger.delivered();return;}
        if(now>=deadline){fail("Quest loot delivery timed out; disappearance is not pickup");return;}
        ItemEntity target=null;
        for(var e:client.level.entitiesForRendering())if(e instanceof ItemEntity item&&contents.containsKey(item.getUUID())&&!ledger.delivered(item.getUUID())&&item.isAlive()
                &&(target==null||item.distanceToSqr(client.player)<target.distanceToSqr(client.player)))target=item;
        if(target==null){travel.stop();walking=false;status=dead?"Waiting for exact native drop/receipt/inventory delivery":"Waiting for observed boss death after quest completion";return;}
        var next=new Vec3(Math.floor(target.getX())+.5,Math.floor(target.getY()),Math.floor(target.getZ())+.5);
        if(!safeRoute(next)){travel.stop();walking=false;status="Quest drop route is unloaded, unsupported or still hazardous";return;}
        if(!walking||goal==null||goal.horizontalDistanceTo(next)>.5){travel.stop();services.rotation().cancelRotation();goal=next;walking=travel.startGround(next,2500,32,.35,SharedMovementPolicy.Purpose.EXACT_RETURN);}
        if(walking){var result=travel.tick();walking=result==McTravelNavigator.State.ACTIVE;state=State.WALKING;status="Walking to tracked quest drop; native delivery pending";}
        else status="No safe loaded route to tracked quest drop";
    }
    private boolean safeRoute(Vec3 target){
        if(!nav.standingClear(target))return false;var start=point(client.player);int steps=Math.max(1,(int)Math.ceil(start.horizontalDistanceTo(target)*4));
        for(int i=0;i<=steps;i++){double t=(double)i/steps;var p=new Vec3(start.x()+(target.x()-start.x())*t,start.y()+(target.y()-start.y())*t,start.z()+(target.z()-start.z())*t);var feet=p.toBlockPos();
            var cell=new net.minecraft.core.BlockPos(feet.x(),feet.y(),feet.z());if(!client.level.isLoaded(cell)||!client.level.isLoaded(cell.below()))return false;var floor=client.level.getBlockState(cell.below());
            if(floor.is(Blocks.BEDROCK)||floor.is(Blocks.TNT)||floor.is(Blocks.LAVA)||client.level.getBlockState(cell).is(Blocks.FIRE))return false;}
        for(var e:client.level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.item.PrimedTnt&&point(e).horizontalDistanceTo(start)<8||e.hasCustomName()&&SlayerSignals.response(SlayerSignals.Family.REVENANT,e.getName().getString())==SlayerSignals.Response.RETREAT&&point(e).horizontalDistanceTo(start)<12)return false;
        return true;
    }
    public void stop(){travel.stop();walking=false;}
    private void fail(String reason){travel.stop();walking=false;state=State.FAILED;status=reason;}
}
