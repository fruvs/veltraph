package dev.veltraph.client.combat.sven;

import dev.veltraph.client.combat.grind.CombatLoop;
import dev.veltraph.client.combat.grind.MeleeGrindModule;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;

/**
 * Register only this parent with ModuleManager. Its ingress, patrol and configured combat child
 * borrow its single input owner; no global Bestiary-start hook and no independently registered child.
 */
public final class SvenParentModule extends AbstractModule {
    public static final String ID="sven_cave";
    private final Minecraft client;
    private final ConfigService config;
    private final FarmServices services;
    private final SvenSourceBindings source;
    private final SvenParentPolicy quest;
    private final SvenNativeIngressPort port;
    private final SvenIngressController ingress;
    private final McGameContextService area;
    private McSharedMovementNavigator patrol;
    private final McNavigator terrain;
    private MeleeGrindModule combat;
    private SvenCaveProfile profile;
    private String names="",setup="",status="Idle";
    private boolean combatOwned,patrolling,questPending;
    private int waypoint;
    private long generation;

    public SvenParentModule(Minecraft client,ConfigService config,FarmServices services){
        this(client,config,services,new SvenConfiguredBindings(client,config,services),null);
    }
    public SvenParentModule(Minecraft client,ConfigService config,FarmServices services,SvenSourceBindings source,SvenParentPolicy quest){
        super(ID,"Sven Cave",ModuleCategory.COMBAT);this.client=client;this.config=config;this.services=services;
        this.source=Objects.requireNonNull(source);this.quest=quest;port=new SvenNativeIngressPort(client,services,config,source);
        area=new McGameContextService(client);
        ingress=new SvenIngressController(port,new Random(),()->{combatOwned=true;waypoint=0;status="Source preparation/inside-arrival handoff; owned cave combat/patrol child";});
        patrol=new McSharedMovementNavigator(client,services,config);terrain=new McNavigator(client,services.input(),config);
    }
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.THE_PARK);}
    @Override public Set<GameArea> ingressStartAreas(){
        return SvenIngressActivation.startAreas(config.get(SvenConfig.SVEN_SOURCE_BINDING_PROFILE),config.get(SvenConfig.SVEN_CAVE_TYPE));
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean hasTravelSetupPhase(){return !combatOwned;}
    @Override public Optional<String> preStartCheck(){
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive())return Optional.of("Sven parent requires a live native player/world/connection");
        var admission=SvenIngressActivation.readiness(area.current(),config.get(SvenConfig.SVEN_SOURCE_BINDING_PROFILE),config.get(SvenConfig.SVEN_CAVE_TYPE));
        if(admission.isPresent())return admission;
        if(config.get(SvenConfig.SVEN_TARGET_NAMES).isBlank())return Optional.of("Set Sven Target Names before starting the dedicated Sven parent");
        if(config.get(SvenConfig.SVEN_AUTO_SLAYER)){
            if(quest==null)return Optional.of("QUEST: recovered shared Auto Slayer scoreboard/quest policy is unbound; no generic Batphone policy is attached");
            var error=quest.readiness();if(error.isPresent())return error;
        }
        // Typed child readiness only stages a passive native weapon proposal.
        final SvenCaveProfile selectedProfile=config.get(SvenConfig.SVEN_CAVE_TYPE);
        var probe=new MeleeGrindModule("sven_owned_combat","Sven Owned Combat",GameArea.THE_PARK,
                config.get(SvenConfig.SVEN_TARGET_NAMES),client,services,config,null,target->selectedProfile.contains(target.position()),
                dev.veltraph.client.combat.grind.CombatWeaponPolicy.Family.SVEN_CAVE,child->isEnabled()&&combat==child&&port.contextValid(),()->this);
        return probe.preStartCheck();
    }
    @Override public void onEnable(){
        generation++;profile=config.get(SvenConfig.SVEN_CAVE_TYPE);names=config.get(SvenConfig.SVEN_TARGET_NAMES);setup=signature();
        port.bind();patrol.stop();patrol=new McSharedMovementNavigator(client,services,config);combatOwned=patrolling=questPending=false;waypoint=0;
        final SvenCaveProfile selectedProfile=profile;
        combat=new MeleeGrindModule("sven_owned_combat","Sven Owned Combat",GameArea.THE_PARK,names,client,services,config,null,target->selectedProfile.contains(target.position()),
                dev.veltraph.client.combat.grind.CombatWeaponPolicy.Family.SVEN_CAVE,child->isEnabled()&&combat==child&&port.contextValid(),()->this);
        ingress.start(profile,config.get(SvenConfig.SVEN_AUTO_TOOL_SWAP),config.get(SvenConfig.SVEN_PURCHASE_RECEIPT_PREFIX),
                config.get(SvenConfig.SVEN_PURCHASE_RECEIPT_ITEM),config.get(SvenConfig.SVEN_TRAVEL_DENIED_MESSAGE),
                new HashSet<>(Arrays.stream(config.get(SvenConfig.SVEN_BAZAAR_RETRY_MESSAGES).split("\\n")).map(String::strip).filter(s->!s.isBlank()).toList()));
        status="Preparation of dedicated "+profile+" Sven parent";
        if(config.get(SvenConfig.SVEN_AUTO_SLAYER)){
            if(quest==null){stopWith("QUEST: recovered shared policy unavailable");return;}
            if(quest.pickQuestBeforePreparation()){
                questPending=true;long run=generation;
                quest.beginQuest(()->{if(isEnabled()&&run==generation&&port.contextValid()){questPending=false;status="Quest continuation returned to owned preparation";}},new SvenIngressPort.Ticket(run,0));
            }
        }
    }
    @Override public void onTick(){
        if(!port.contextValid()){if(source.awaitingNativeArrival()){services.input().releaseAllMovement();services.breaking().setBreaking(false);status="Waiting for owned native Park warp arrival";return;}stopWith("Native Sven player/world/connection changed without an owned acknowledged Park warp");return;}
        if(!setup.equals(signature())){stopWith("Sven profile/helper/combat settings changed during owned flow");return;}
        var context=area.current();
        if(!SvenIngressActivation.knownSkyBlock(context)||combatOwned&&context.area()!=GameArea.THE_PARK){stopWith("Sven parent lost its known SkyBlock source area or Park combat area");return;}
        if(questPending){quest.tick();return;}
        if(combatOwned){tickCombat();return;}
        ingress.tick();status=ingress.status();
        if(ingress.blocker().isPresent()||ingress.failure().isPresent()){String reason=status;setEnabled(false);status=reason;}
    }
    private void tickCombat(){
        if(client.gui.screen()!=null){stopWith("Screen opened during owned cave combat");return;}
        Vec3 position=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        var matches=combat.targets(config.get(CombatConfig.COMBAT_SEARCH_RADIUS)).stream()
                .filter(t->t.alive()&&!t.protectedTarget()&&profile.contains(t.position())&&CombatLoop.matches(t.name(),names)).toList();
        if(profile.contains(position)&&!matches.isEmpty()){
            patrol.stop();patrolling=false;
            if(!combat.isEnabled()){
                var ready=combat.preStartCheck();if(ready.isPresent()){stopWith(ready.get());return;}combat.setEnabled(true);
            }
            combat.onTick();status="Owned bounded combat | "+combat.diagnostics();
            if(!combat.isEnabled())stopWith(status);return;
        }
        stopCombat();
        if(!patrolling){
            var target=profile.combatWaypoints().get(waypoint%profile.combatWaypoints().size());
            if(!patrol.startGround(terrain.position(target),2500,32,.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){stopWith("Configured cave combat waypoint has no ordinary physical path: "+target+" | "+patrol.status());return;}
            patrolling=true;
        }
        var result=patrol.tick();status="Owned cave combat patrol | "+profile+" waypoint="+waypoint+" | "+patrol.status();
        if(result==McTravelNavigator.State.ARRIVED){patrol.stop();patrolling=false;waypoint=(waypoint+1)%profile.combatWaypoints().size();}
        else if(result==McTravelNavigator.State.FAILED)stopWith("Physical cave combat patrol failed: "+patrol.status());
    }
    private void stopCombat(){if(combat!=null&&combat.isEnabled())combat.setEnabled(false);}
    @Override public void onDisable(){
        generation++;questPending=false;stopCombat();patrol.stop();if(quest!=null)quest.cancel();ingress.stop();combatOwned=patrolling=false;
        services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);
    }
    private void stopWith(String reason){setEnabled(false);status=reason;}
    @Override public void onServerMessage(String line){if(!isEnabled())return;if(questPending)quest.message(line);else ingress.message(line);}
    @Override public boolean allowsScreen(String title){return !combatOwned&&port.allowsScreen(title);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(combatOwned?patrolling&&patrol.consumeExpectedTeleport(position,now):port.consumeExpectedTeleport(position,now));}
    /** Query before root's player/world stop branches; requires this same controller operation. */
    public Optional<SvenWarpPermit> pendingNativeWarp(){
        if(!isEnabled()||combatOwned||!setup.equals(signature()))return Optional.empty();
        return source.pendingWarpPermit().filter(permit->ingress.pendingTicket().filter(permit.request()::equals).isPresent());
    }
    /** Explicit root handoff; an unchanged same-owner lease and the identical active permit are required. */
    public boolean commitNativeWarp(SvenWarpPermit permit,Object nativeInputOwner){
        if(nativeInputOwner!=this||pendingNativeWarp().filter(current->current==permit).isEmpty())return false;
        return port.commitNativeWarp(permit);
    }
    public SvenIngressController ingress(){return ingress;}
    public boolean ownsCombatChild(){return combatOwned;}
    /** Read-only parent-owned physical selection, including the child's fresh body predicate. */
    public Optional<net.minecraft.world.entity.LivingEntity> selectedCombatTarget(){return isEnabled()&&combatOwned&&combat!=null?combat.selectedCombatTarget():Optional.empty();}
    public int combatKills(){return combat==null?0:combat.confirmedKills();}
    public SvenCaveProfile profile(){return profile;}
    @Override public String diagnostics(){return ID+" | "+status;}
    private String signature(){
        Map<String,String> values=new TreeMap<>();
        for(var key:PersistableKeys.collect(List.of(SvenConfig.class,CombatConfig.class,RotationConfig.class,TravelConfig.class)))values.put(key.name(),String.valueOf(config.get(key)));
        return values.toString();
    }
}
