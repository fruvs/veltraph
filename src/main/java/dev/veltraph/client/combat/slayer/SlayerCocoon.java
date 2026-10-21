package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.combat.grind.*;
import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Own-boss receipt -> configured exact native cocoon cues -> fresh independently owned replacement body. */
public final class SlayerCocoon {
    public enum State { WATCHING, WAITING_HEADS, WAITING_REPLACEMENT, READY, FAILED }
    private final Minecraft client;private final ConfigService config;private final FarmServices services;private final McNavigator nav;private final McSharedMovementNavigator travel;
    private LivingEntity boss;private Object world,connection;private UUID player;private SlayerEntityOwnership.Owner owner;
    private long lastObserved,deadline;private State state=State.WATCHING;private String status="No own-boss cocoon receipt";
    private final Set<UUID> prior=new HashSet<>();private Vec3 origin,goal;private boolean walking;private MeleeGrindModule.SelectedTarget replacement;
    private final Set<UUID> rejectedOwnerBodies=new HashSet<>();
    private final CocoonBodyCoherence coherence=new CocoonBodyCoherence();private UUID replacementPlate;
    public SlayerCocoon(Minecraft client,ConfigService config,FarmServices services){this.client=client;this.config=config;this.services=services;nav=new McNavigator(client,services.input(),config);travel=new McSharedMovementNavigator(client,services,config);}
    public static boolean receipt(String text){return dev.veltraph.client.gui.MenuSnapshot.clean(text).lines().anyMatch(line->line.strip().startsWith("YOU COCOONED YOUR SLAYER BOSS"));}
    public static Optional<String> validation(ConfigService c){
        if(!c.get(SlayerConfig.SLAYER_COCOON_RECOVERY))return Optional.empty();
        if(!c.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS)||SlayerQuestLoot.validation(c).isPresent())return Optional.of("Cocoon recovery requires configured exact quest drop delivery before reward/restart.");
        String hashes=c.get(SlayerConfig.SLAYER_COCOON_HEAD_HASHES);long timeout=c.get(SlayerConfig.SLAYER_COCOON_TIMEOUT_MS),recent=c.get(SlayerConfig.SLAYER_COCOON_RECEIPT_WINDOW_MS);double radius=c.get(SlayerConfig.SLAYER_COCOON_REPLACEMENT_RADIUS);
        if(c.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES).isBlank()||hashes.isBlank()||Arrays.stream(hashes.split(",",-1)).anyMatch(hash->!hash.strip().matches("[a-f0-9]{64}")))return Optional.of("Configure exact cocoon head hashes and quest boss names before enabling recovery.");
        if(timeout<500||timeout>60000||recent<50||recent>5000||!Double.isFinite(radius)||radius<=0||radius>16)return Optional.of("Configure source-unresolved cocoon timeout, receipt window and replacement radius.");
        return Optional.empty();
    }
    public void reset(){travel.stop();boss=null;owner=null;world=connection=null;player=null;prior.clear();rejectedOwnerBodies.clear();coherence.clear();replacementPlate=null;replacement=null;origin=goal=null;walking=false;state=State.WATCHING;status="No own-boss cocoon receipt";}
    public void watch(MeleeGrindModule.SelectedTarget selected,long now){
        if(!config.get(SlayerConfig.SLAYER_COCOON_RECOVERY)||state!=State.WATCHING||selected==null||!selected.body().isAlive()
                ||!CombatLoop.matches(selected.observedName(),config.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES)))return;
        if(boss!=null&&boss!=selected.body())return;
        var entities=SlayerEntityOwnership.visible(client);var proof=SlayerEntityOwnership.own(client,selected.body(),entities);if(proof.isEmpty()){if(SlayerEntityOwnership.hasOwnerLabel(selected.body(),entities))owner=null;return;}
        boss=selected.body();owner=proof.get();world=client.level;connection=client.getConnection();player=client.player.getUUID();lastObserved=now;
    }
    public boolean begin(long now){
        if(!config.get(SlayerConfig.SLAYER_COCOON_RECOVERY)){fail("Own boss cocoon observed; dedicated recovery is not configured");return false;}
        if(state!=State.WATCHING||boss==null||owner==null||world!=client.level||connection!=client.getConnection()||client.player==null
                ||!client.player.getUUID().equals(player)||now<lastObserved||now-lastObserved>config.get(SlayerConfig.SLAYER_COCOON_RECEIPT_WINDOW_MS)){fail("Cocoon receipt has no recent independently owned selected boss");return false;}
        prior.clear();for(var entity:client.level.entitiesForRendering())prior.add(entity.getUUID());
        origin=new Vec3(boss.getX(),boss.getY(),boss.getZ());deadline=now+config.get(SlayerConfig.SLAYER_COCOON_TIMEOUT_MS);state=State.WAITING_HEADS;
        status="Own cocoon receipt; waiting for exact new heads and owned replacement";return true;
    }
    public boolean pending(){return state==State.WAITING_HEADS||state==State.WAITING_REPLACEMENT;}
    public boolean activeCycle(){return pending()||state==State.READY;}
    public boolean receiptWindowOpen(long now){return state==State.WATCHING&&boss!=null&&owner!=null&&world==client.level&&connection==client.getConnection()
            &&client.player!=null&&client.player.getUUID().equals(player)&&now>=lastObserved&&now-lastObserved<=config.get(SlayerConfig.SLAYER_COCOON_RECEIPT_WINDOW_MS);}
    public boolean transactionPending(){return boss!=null||pending()||state==State.READY;}public State state(){return state;}public String status(){return status;}
    public boolean replacementDeathObserved(){return state==State.READY&&replacement!=null&&replacement.body().getHealth()<=0;}
    public Optional<MeleeGrindModule.SelectedTarget> replacement(){return state==State.READY?Optional.ofNullable(replacement):Optional.empty();}
    public Optional<LivingEntity> observedBoss(){return Optional.ofNullable(boss);}
    public void tick(long now,List<CombatTarget> candidates){
        if(!pending())return;
        if(client.player==null||client.level!=world||client.getConnection()!=connection||!client.player.getUUID().equals(player)||!client.player.isAlive()
                ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()){fail("Cocoon lost exact native owner or closed-menu proof");return;}
        if(now>=deadline){fail("Cocoon replacement deadline expired without native ownership proof");return;}
        var entities=SlayerEntityOwnership.visible(client);var heads=entities.stream().filter(entity->entity instanceof ArmorStand&&entity.isAlive()
                &&!prior.contains(entity.getUUID())&&insideSourceBox(entity)&&headMatches((ArmorStand)entity)).toList();
        if(heads.size()>3){fail("Ambiguous cocoon head group; do not choose a nearest origin");return;}
        var bodies=new ArrayList<MeleeGrindModule.SelectedTarget>();
        double radius=config.get(SlayerConfig.SLAYER_COCOON_REPLACEMENT_RADIUS);
        for(var candidate:candidates)if(candidate.alive()&&!candidate.protectedTarget()&&CombatLoop.matches(candidate.name(),config.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES))
                &&candidate.position().distanceTo(origin)<=radius){var entity=client.level.getEntity(candidate.id());if(entity instanceof LivingEntity living&&living!=boss){
            if(prior.contains(living.getUUID())){fail("Preexisting competing boss cannot become a cocoon replacement");return;}bodies.add(new MeleeGrindModule.SelectedTarget(living,candidate.name()));}}
        if(bodies.size()>1){fail("Ambiguous cocoon replacement bodies");return;}
        if(heads.size()!=3){status="Waiting for three exact newly observed configured cocoon heads";return;}
        state=State.WAITING_REPLACEMENT;
        if(!bodies.isEmpty()&&(boss.getHealth()<=0||boss.isRemoved())){
            var target=bodies.getFirst();var proof=SlayerEntityOwnership.own(client,target.body(),entities);
            if(proof.isEmpty()&&SlayerEntityOwnership.hasOwnerLabel(target.body(),entities))rejectedOwnerBodies.add(target.body().getUUID());
            if(proof.isPresent()&&!rejectedOwnerBodies.contains(target.body().getUUID())&&!prior.contains(proof.get().plate())&&proof.get().body().equals(target.body().getUUID())){
                if(coherence.observe(target.body().getUUID(),proof.get().plate(),client.player.tickCount,target.body().getType().updateInterval())){
                    travel.stop();walking=false;replacementPlate=proof.get().plate();replacement=target;state=State.READY;status="Same quest replacement has coherent fresh body and independent own-player plate";return;
                }
            }else coherence.clear();
        }else coherence.clear();
        // Recovered state walks at least five blocks from its classified cocoon origin while waiting.
        if(goal==null){Vec3 start=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());double nearest=Double.POSITIVE_INFINITY;
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){var point=new Vec3(Math.floor(origin.x())+x+.5,Math.floor(origin.y()),Math.floor(origin.z())+z+.5);
                double distance=start.horizontalDistanceTo(point);if(point.horizontalDistanceTo(origin)>=5&&distance<nearest&&nav.standingClear(point)&&nav.walkClear(start,point)){goal=point;nearest=distance;}}
            if(goal==null){fail("No loaded supported cocoon standoff route");return;}services.rotation().cancelRotation();walking=travel.startGround(goal,2500,32,.35,SharedMovementPolicy.Purpose.EXACT_RETURN);
        }
        if(walking){var result=travel.tick();walking=result==McTravelNavigator.State.ACTIVE;if(result==McTravelNavigator.State.FAILED){fail("Cocoon standoff route became blocked");return;}}
        status="Holding own cocoon standoff; fresh independently owned replacement pending";
    }
    private boolean insideSourceBox(Entity entity){return Math.abs(entity.getX()-origin.x())<=5&&Math.abs(entity.getY()-origin.y())<=5&&Math.abs(entity.getZ()-origin.z())<=5;}
    private boolean headMatches(ArmorStand stand){
        var profile=stand.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);if(profile==null)return false;var values=new HashSet<String>();
        for(var property:profile.partialProfile().properties().get("textures")){String hash=SkullTexture.hash(property.value());if(hash.isEmpty())return false;values.add(hash);}
        if(values.size()!=1)return false;String hash=values.iterator().next();return Arrays.stream(config.get(SlayerConfig.SLAYER_COCOON_HEAD_HASHES).split(",")).anyMatch(value->value.strip().equals(hash));
    }
    public void stop(){travel.stop();walking=false;}
    public boolean boundProof(List<CombatTarget> candidates){
        if(state!=State.READY||replacement==null)return false;
        if(replacementDeathObserved())return true;
        var body=replacement.body();
        if(client.level!=world||client.getConnection()!=connection||client.player==null||!client.player.getUUID().equals(player)||client.level.getEntity(body.getId())!=body){fail("Bound cocoon replacement lost exact native identity");return false;}
        for(var candidate:candidates)if(candidate.alive()&&!candidate.protectedTarget()&&candidate.id()!=body.getId()
                &&CombatLoop.matches(candidate.name(),config.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES))&&candidate.position().distanceTo(origin)<=config.get(SlayerConfig.SLAYER_COCOON_REPLACEMENT_RADIUS)){
            fail("Bound cocoon replacement gained a competing configured body");return false;
        }
        var proof=SlayerEntityOwnership.own(client,body,SlayerEntityOwnership.visible(client));
        if(proof.isEmpty()||!proof.get().plate().equals(replacementPlate)){fail("Bound cocoon replacement lost its exact own-player plate");return false;}return true;
    }
    private void fail(String reason){travel.stop();walking=false;state=State.FAILED;status=reason;}
}
