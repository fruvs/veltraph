package dev.veltraph.client.combat.grind;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** End ranged grinding: typed local patrol, moving ranged targets and acknowledged eye pickup. */
public final class ZealotRangedModule extends AbstractModule {
    public static final String ID="zealot_ranged";
    private record Target(LivingEntity mob,String name,boolean protectedMob) {}
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final LocalRouteStore routes;private McTravelNavigator patrol;private McNavigator walking;
    private final CombatScheduledPause scheduledPause;
    private final dev.veltraph.client.game.GameContextService gameContext;private ZealotArenaWorkflow arena;private int preferredSlot=-1;private long nextArenaShot;
    private final ZealotEncounterLedger encounters=new ZealotEncounterLedger();
    private final RangedShotController shots=new RangedShotController();
    private final ZealotLootTracker loot=new ZealotLootTracker();
    private final CombatAim aim=new CombatAim();private final CombatMovement movement=new CombatMovement();
    private final AngularRateLimiter yawHandoff=new AngularRateLimiter(),pitchHandoff=new AngularRateLimiter();
    private final dev.veltraph.client.combat.quiver.McQuiverRefill quiver;
    private boolean refillRequested,arenaHeldView;private int refills;
    private Object enabledPlayer,enabledWorld;
    private LocalRoute route;private int routeIndex,target=-1,previousSlot,lootId=-1,shotCount;
    private boolean patrolActive,walkingActive,sneaking,eyeBeam;private long nextPath,lootDeadline,nextSneak;private Vec3 lastGoal;
    private String status="Ready";
    public ZealotRangedModule(Minecraft client,FarmServices services,ConfigService config,LocalRouteStore routes) {
        this(client,services,config,routes,new dev.veltraph.client.game.mc.McGameContextService(client));
    }
    public ZealotRangedModule(Minecraft client,FarmServices services,ConfigService config,LocalRouteStore routes,dev.veltraph.client.game.GameContextService gameContext) {
        super(ID,"Zealot Ranged",ModuleCategory.COMBAT);this.gameContext=java.util.Objects.requireNonNull(gameContext);this.client=client;this.services=services;this.config=config;this.routes=routes;
        patrol=new McTravelNavigator(client,services,config);walking=new McNavigator(client,services.input(),config);
        quiver=new dev.veltraph.client.combat.quiver.McQuiverRefill(client,services,config);
        scheduledPause=new CombatScheduledPause(client,this::isEnabled,this::scheduledReadiness,this::scheduledSignature,
                this::releaseForScheduledPause,this::rebindForScheduledPause,()->status="Ranged rest complete; route/combat resumed",()->setEnabled(false));
    }
    @Override public Optional<GameArea> requiredArea(){return config.get(ZealotConfig.ZEALOT_ARENA_ENABLED)?Optional.empty():Optional.of(GameArea.THE_END);}
    @Override public boolean hasTravelSetupPhase(){return arena!=null&&arena.stage()!=ZealotArenaWorkflow.Stage.FARMING&&arena.stage()!=ZealotArenaWorkflow.Stage.LOOT&&arena.stage()!=ZealotArenaWorkflow.Stage.FAILED;}
    public boolean ownsExpectedEndIngress(){return isEnabled()&&arena!=null&&arena.expectedTransfer();}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return !scheduledPause.paused()&&(quiver.active()&&quiver.consumeExpectedTeleport(position,now)||arena!=null&&arena.consumeExpectedTeleport(position,now)||patrol.consumeExpectedTeleport(position,now));}
    @Override public boolean ownsGameplayInput(){return !scheduledPause.paused();}
    public dev.veltraph.client.scheduling.ScheduledPausePort scheduledPausePort(){return scheduledPause;}
    public Optional<String> schedulingReadiness(){return scheduledPause.paused()?scheduledPause.arrivalReadiness():scheduledReadiness();}
    public Optional<String> scheduledReadiness(){
        if(!isEnabled()||scheduledPause.paused()||client.player!=enabledPlayer||client.level!=enabledWorld||!weaponReady()||services.inventory().selectedSlot()!=slot())return Optional.of("Exact enabled ranged weapon owner required");
        if(arena!=null&&!arena.safePause())return Optional.of("Arena ingress/beam/loot/dragon phase must settle before rest");
        if(refillRequested||quiver.active()||eyeBeam||lootId>=0||client.player.isUsingItem()&&!shots.drawing())return Optional.of("Ranged quiver/eye/drop/ability work must settle before rest");
        if(patrolActive&&route!=null&&route.nodes().get(routeIndex).kind()!=LocalRoute.Kind.WALK)return Optional.of("Ranged ability travel must finish before rest");
        return Optional.empty();
    }
    public String scheduledSignature(){
        var values=new TreeMap<String,String>();for(var key:PersistableKeys.collect(List.of(ZealotConfig.class,QuiverConfig.class,RotationConfig.class,TravelConfig.class)))values.put(key.name(),String.valueOf(config.get(key)));
        String name=config.get(ZealotConfig.ZEALOT_RANGED_ROUTE);try{return values+"|"+(name.isBlank()?"no route":routes.load(name))+"|"+(arena==null?"legacy":arena.routeSignature());}catch(Exception failure){return values+"|unavailable route proof";}
    }
    private void releaseForScheduledPause(){cancelDraw();patrol.stop();walking.stop();patrolActive=walkingActive=false;services.rotation().cancelRotation();services.breaking().setBreaking(false);CombatScheduledPause.releaseKeys(client);target=-1;loot.clearNativeContext();status="Ranged engine paused for owned scheduled rest";}
    private void rebindForScheduledPause(){enabledPlayer=client.player;enabledWorld=client.level;if(arena!=null)arena.rebindRest();encounters.rebind();patrol=new McTravelNavigator(client,services,config);walking=new McNavigator(client,services.input(),config);target=lootId=-1;loot.clearNativeContext();lastGoal=null;nextPath=nextSneak=0;sneaking=false;aim.reset();movement.reset();yawHandoff.reset();pitchHandoff.reset();}
    private int slot(){return preferredSlot>=0?preferredSlot:config.get(ZealotConfig.ZEALOT_WEAPON_SLOT)-1;}
    private boolean weaponReady(){return client.player!=null&&slot()>=0&&slot()<9&&(arena!=null&&arena.precursor()?!client.player.getInventory().getItem(slot()).isEmpty():client.player.getInventory().getItem(slot()).is(Items.BOW));}
    @Override public Optional<String> preStartCheck() {
        if(scheduledPause.paused())return Optional.of("Owned scheduled combat pause is awaiting arrival");
        if(arena!=null&&arena.hasCleanup())return Optional.of("Owned Precursor beam OFF acknowledgment/manual recovery remains pending");
        preferredSlot=-1;arena=null;
        if(config.get(ZealotConfig.ZEALOT_ARENA_ENABLED))try{arena=new ZealotArenaWorkflow(client,services,config,routes,gameContext,this::arenaQuiesce);var notReady=arena.readiness();if(notReady.isPresent())return notReady;preferredSlot=arena.preferredSlot();}catch(RuntimeException error){return Optional.of(error.getMessage());}

        if(!weaponReady())return Optional.of("Set Zealot Weapon Slot (1-9) to a bow or Shortbow.");
        if((arena==null||!arena.precursor())&&!shortbow(client.player.getInventory().getItem(slot()).getHoverName().getString())&&!client.player.getAbilities().instabuild
                &&client.player.getProjectile(client.player.getInventory().getItem(slot())).isEmpty()) {
            if(!config.get(ZealotConfig.ZEALOT_REFILL_QUIVER))return Optional.of("An ordinary bow needs arrows before starting.");
            if(config.get(QuiverConfig.QUIVER_SUPPLY)!=dev.veltraph.client.combat.quiver.QuiverSupply.PLAYER_INVENTORY)return Optional.of("Vanilla bow refill requires Player Inventory supply mode.");
            var error=quiver.configError();if(error.isPresent())return error;
        }
        route=null;String name=config.get(ZealotConfig.ZEALOT_RANGED_ROUTE);
        if(!name.isBlank())try {
            route=routes.load(name);
            if(route.area()!=GameArea.THE_END||client.level==null||!route.dimension().equals(client.level.dimension().identifier().toString()))
                return Optional.of("Zealot route belongs to a different area or dimension.");
            if(route.nodes().stream().anyMatch(n->n.kind()==LocalRoute.Kind.FLY)&&!client.player.getAbilities().mayfly)
                return Optional.of("Zealot route needs server flight permission.");
            if(route.nodes().stream().anyMatch(n->n.kind()==LocalRoute.Kind.ETHERWARP)&&!patrol.hasEtherwarpTool())
                return Optional.of("Zealot route needs Ether Transmission in the hotbar.");
        } catch(Exception e){return Optional.of("Zealot route: "+e.getMessage());}
        return Optional.empty();
    }
    @Override public void onEnable(){encounters.reset();nextArenaShot=0;if(arena!=null)arena.start();enabledPlayer=client.player;enabledWorld=client.level;previousSlot=services.inventory().selectedSlot();services.inventory().selectSlot(slot());routeIndex=0;target=lootId=-1;shotCount=refills=0;refillRequested=false;patrolActive=walkingActive=sneaking=eyeBeam=false;nextPath=nextSneak=0;lastGoal=null;shots.reset();loot.reset();aim.reset();movement.reset();yawHandoff.reset();pitchHandoff.reset();services.rotation().cancelRotation();status="LOCATE";if((arena==null||!arena.precursor())&&config.get(ZealotConfig.ZEALOT_REFILL_QUIVER)&&!shortbow(client.player.getInventory().getItem(slot()).getHoverName().getString())&&!client.player.getAbilities().instabuild&&client.player.getProjectile(client.player.getInventory().getItem(slot())).isEmpty())refillRequested=true;}
    @Override public void onDisable(){scheduledPause.invalidate();if(arena!=null)arena.cancel();quiver.cancel("Ranged grinding stopped");refillRequested=false;cancelDraw();services.rotation().cancelRotation();patrol.stop();walking.stop();services.input().releaseAllMovement();services.breaking().setBreaking(false);if(client.player==enabledPlayer&&client.level==enabledWorld&&(arena==null||services.inventory().selectedSlot()==slot()))services.inventory().selectSlot(previousSlot);shots.reset();loot.abandon();}
    public void onChat(String text){if(scheduledPause.paused()||text==null)return;if(arena!=null)arena.message(text);if(!isEnabled())return;loot.onChat(text,System.currentTimeMillis());
        if((arena==null||!arena.precursor())&&text.contains("Eye Beam Activated"))eyeBeam=true;
        if((arena==null||!arena.precursor())&&text.contains("Eye Beam De-activated!"))eyeBeam=false;
        quiver.onMessage(text);
        if((arena==null||!arena.precursor())&&(text.equals("Out of arrows.")||text.equals("Your quiver is now completely empty!"))) {
            if(config.get(ZealotConfig.ZEALOT_REFILL_QUIVER)&&refills<config.get(QuiverConfig.QUIVER_MAX_REFILLS_PER_RUN))refillRequested=true;
            else {status="Out of arrows; refill disabled or run allowance exhausted";setEnabled(false);}
        }
    }
    @Override public void onActionBar(String text){if(arena!=null&&!scheduledPause.paused())arena.actionBar(text);}
    @Override public boolean hasPendingCleanup(){return arena!=null&&arena.hasCleanup();}
    @Override public void maintenanceTick(){if(!isEnabled()&&arena!=null)arena.cleanupTick();}
    private void arenaQuiesce(){cancelDraw();patrol.stop();walking.stop();patrolActive=walkingActive=false;services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);}
    public int observedKills(){return encounters.kills();}public int specialKills(){return encounters.specialKills();}
    public String arenaStage(){return arena==null?"LEGACY":arena.stage().name();}public int arenaArrivals(){return arena==null?0:arena.arrivals();}public int dragonYields(){return arena==null?0:arena.dragonYields();}
    public int shots(){return shotCount;}
    public int confirmedEyes(){return loot.confirmedEyes();}
    public int refills(){return refills;}
    @Override public boolean allowsScreen(String title){if(scheduledPause.paused())return false;return quiver.allowsScreen(title)||refillRequested&&dev.veltraph.client.gui.MenuSnapshot.clean(title).equals(config.get(QuiverConfig.QUIVER_MENU_TITLE));}
    @Override public void onTick(){
        arenaHeldView=false;
        if(scheduledPause.paused())return;
        if(arena!=null&&arena.ownedWarpArrival()&&(client.player!=enabledPlayer||client.level!=enabledWorld)){enabledPlayer=client.player;enabledWorld=client.level;patrol=new McTravelNavigator(client,services,config);walking=new McNavigator(client,services.input(),config);arena.rebindExpectedWarp();}
        var p=client.player;if(p==null||client.level==null||client.gameMode==null||p!=enabledPlayer||client.level!=enabledWorld||p.isDeadOrDying()||!weaponReady()){setEnabled(false);return;}
        if(arena!=null&&(refillRequested||quiver.active())&&!arena.beforeQuiver()){arena.step();if(arena.failed())setEnabled(false);return;}
        if(refillRequested||quiver.active()) {
            cancelDraw();patrol.stop();walking.stop();patrolActive=walkingActive=false;services.rotation().cancelRotation();
            if(refillRequested&&!quiver.active()) {refillRequested=false;
                if(!shortbow(p.getInventory().getItem(slot()).getHoverName().getString())&&config.get(QuiverConfig.QUIVER_SUPPLY)!=dev.veltraph.client.combat.quiver.QuiverSupply.PLAYER_INVENTORY){status="Vanilla bow refill requires Player Inventory supply mode";setEnabled(false);return;}
                if(!quiver.request(true)){status=quiver.status();setEnabled(false);return;}}
            refillRequested=false;quiver.tick();status="Quiver: "+quiver.status();
            if(!quiver.active()) {
                if(quiver.state()==dev.veltraph.client.combat.quiver.QuiverRefillRuntime.State.COMPLETE){refills++;aim.reset();movement.reset();yawHandoff.reset();pitchHandoff.reset();services.inventory().selectSlot(slot());status="Refill acknowledged; ranged grinding resumed";}
                else setEnabled(false);
            }return;
        }
        if(client.gui.screen()!=null){setEnabled(false);return;}
        if(arena!=null&&!arena.step()){status=arena.status();if(arena.failed())setEnabled(false);return;}
        float yaw=p.getYRot(),pitch=p.getXRot();tick();
        if(isEnabled()&&!arenaHeldView) {double speed=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);dev.veltraph.client.service.mc.McOwnedRotation.plan(client,yawHandoff.apply(yaw,dev.veltraph.client.service.mc.McOwnedRotation.requestedYaw(client),speed),pitchHandoff.apply(pitch,dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client),speed));}
    }
    private void tick(){
        var p=client.player;long now=System.currentTimeMillis();double radius=arena==null?bounded(config.get(ZealotConfig.ZEALOT_RANGED_RADIUS),6,48,24):arena.profile().range();
        if(eyeBeam){cancelDraw();patrol.stop();walking.stop();patrolActive=walkingActive=false;status="Eye Beam active; waiting for server deactivation";return;}
        if(loot.observe(eyeCount())) {lootId=-1;walking.stop();walkingActive=false;if(arena!=null)arena.finishLoot();status="Summoning Eye pickup acknowledged";return;}
        ItemEntity drop=null;
        if(config.get(ZealotConfig.ZEALOT_COLLECT_EYES))for(var e:client.level.entitiesForRendering())
            if(e instanceof ItemEntity item&&item.isAlive()&&loot.eligible(item.getId(),now)&&ZealotLootTracker.eye(item.getItem().getHoverName().getString())&&item.distanceToSqr(p)<radius*radius
                    &&(item.getOwner()==null||item.getOwner()==p)&&(drop==null||item.distanceToSqr(p)<drop.distanceToSqr(p)))drop=item;
        if(drop!=null){
            if(arena!=null&&!arena.beginLoot()){status=arena.status();return;}
            cancelDraw();patrol.stop();patrolActive=false;
            if(lootId!=drop.getId()){lootId=drop.getId();lootDeadline=now+12000;loot.begin(eyeCount());walkingActive=false;}
            if(now<lootDeadline){approach(point(drop),now);status="Collecting Summoning Eye; waiting for inventory acknowledgment";return;}
            loot.abandon(lootId,now);lootId=-1;walking.stop();walkingActive=false;if(arena!=null)arena.finishLoot();status="Eye pickup timed out";return;
        }
        if(lootId>=0){cancelDraw();services.input().releaseAllMovement();if(now<lootDeadline){status="Drop disappeared; awaiting pickup acknowledgment";return;}loot.abandon(lootId,now);lootId=-1;if(arena!=null)arena.finishLoot();}
        var candidates=targets(Math.max(radius,80));
        for(var candidate:candidates)encounters.observe(candidate.mob().getUUID(),candidate.mob().getHealth(),candidate.mob().isAlive(),candidate.protectedMob(),special(candidate.mob()),now);
        var valid=candidates.stream().filter(t->t.mob().isAlive()&&!t.protectedMob()&&(arena==null
                ?t.mob().distanceToSqr(p)<=radius*radius&&t.name().toLowerCase(Locale.ROOT).contains("zealot")
                :t.mob() instanceof net.minecraft.world.entity.monster.Enderman&&!t.mob().isInvisible()&&p.hasLineOfSight(t.mob())
                    &&(t.mob().distanceToSqr(p)<=radius*radius&&t.mob().getY()<33
                        ||special(t.mob())&&p.getBoundingBox().inflate(5,8,5).intersects(t.mob().getBoundingBox())))).toList();
        Target chosen=valid.stream().filter(t->special(t.mob())||loot.special(now)&&t.name().toLowerCase(Locale.ROOT).contains("special")).min(Comparator.comparingDouble(t->t.mob().distanceToSqr(p))).orElse(null);
        if(chosen==null)chosen=valid.stream().filter(t->t.mob().getId()==target).findFirst().orElseGet(()->valid.stream().min(Comparator.comparingDouble(t->t.mob().distanceToSqr(p))).orElse(null));
        if(chosen==null){cancelDraw();target=-1;walking.stop();walkingActive=false;if(arena!=null){arena.beamTarget(false);arenaHeldView=arena.idleLookAround(lootId>=0||quiver.active()||refillRequested);status="FIND_FIRST_ENTITY";}else patrol();return;}
        if(arena!=null)arena.targetSelected();
        if(target!=chosen.mob().getId()){cancelDraw();aim.reset();target=chosen.mob().getId();}
        if(arena==null||patrolActive)patrol.stop();patrolActive=false;if(services.inventory().selectedSlot()!=slot())services.inventory().selectSlot(slot());
        var mob=chosen.mob();var eye=p.getEyePosition();var center=mob.getBoundingBox().getCenter();var velocity=mob.getDeltaMovement();
        boolean shortbow=shortbow(p.getInventory().getItem(slot()).getHoverName().getString());
        Vec3 lead=arena!=null&&arena.precursor()?new Vec3(center.x,center.y,center.z):RangedBallistics.aim(new Vec3(eye.x,eye.y,eye.z),new Vec3(center.x,center.y,center.z),new Vec3(velocity.x,velocity.y,velocity.z),shortbow);
        var end=new net.minecraft.world.phys.Vec3(lead.x(),lead.y(),lead.z());double dx=end.x-eye.x,dy=end.y-eye.y,dz=end.z-eye.z;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        float observedYaw=p.getYRot(),observedPitch=p.getXRot();
        var angles=aim.step(observedYaw,observedPitch,yaw,pitch,.15,.15,config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER));
        boolean sight=client.level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
        boolean precursor=arena!=null&&arena.precursor();
        boolean blocked=precursor
                ?protectedBeamRay(candidates,observedYaw,observedPitch,radius)||protectedBeamRay(candidates,angles.yaw(),angles.pitch(),radius)
                :protectedFlight(candidates,observedYaw,observedPitch,shortbow)||protectedFlight(candidates,angles.yaw(),angles.pitch(),shortbow);
        // An active effect must hold its current view until OFF is received, avoiding a protected sweep.
        if(precursor&&blocked&&!arena.safelyOff()){arenaHeldView=true;arena.beamTarget(false);status="Protected native beam ray; awaiting OFF";return;}
        services.rotation().aim(angles.yaw(),angles.pitch());
        double desired=bounded(config.get(ZealotConfig.ZEALOT_RANGED_DISTANCE),5,20,10);
        Vec3 position=point(p),goal=point(mob);
        if(arena==null&&!sight&&position.horizontalDistanceTo(goal)>desired&&!TravelPath.walkClear(walking,position,goal)){cancelDraw();approach(goal,now);status="Approaching ranged sightline";return;}
        if(walkingActive){walking.stop();walkingActive=false;}
        if(arena==null){
        var keys=movement.choose(position,goal,angles.yaw(),desired,false,false,next->TravelPath.walkClear(walking,position,next),List.of());
        services.input().releaseAllMovement();services.input().setForwardHeld(keys.forward());services.input().setBackHeld(keys.back());services.input().setLeftHeld(keys.left());services.input().setRightHeld(keys.right());
        if(config.get(ZealotConfig.ZEALOT_ALWAYS_BE_JUMPING)&&walking.clear(position.toBlockPos().up(2)))services.input().setJumpHeld(true);
        if(shortbow&&config.get(ZealotConfig.JUJU_RANDOMLY_SNEAK)) {
            if(now>=nextSneak){nextSneak=now+2000;sneaking=!sneaking&&java.util.concurrent.ThreadLocalRandom.current().nextDouble()<.3;}
            services.input().setSneakHeld(sneaking);
        }
        }
        // Judge the current rendered view, before this tick's smooth correction reaches the server.
        boolean aligned=Math.abs(WalkController.wrap(yaw-observedYaw))<1.4&&Math.abs(pitch-observedPitch)<1.4;
        if(arena!=null&&arena.precursor()){
            // Activation retains the center gate. An owned pulse may change eye height; continuation
            // requires the actual native ray to retain this eligible target and safe corridor.
            boolean safe=sight&&!blocked&&(aligned||arena.beamMaintaining())&&beamRayTarget(mob,observedYaw,observedPitch,radius);
            arena.beamTarget(safe);if(safe&&arena.beamActive())encounters.attack(mob.getUUID(),mob.getHealth(),now);status="Precursor "+(safe?"NOW_FARMING":"FIND_FIRST_ENTITY/blocked ray");return;
        }
        long interval;try{interval=arena==null?config.get(ZealotConfig.ZEALOT_SHOT_INTERVAL_MS):arena.cooldown(p.getInventory().getItem(slot()));}catch(RuntimeException invalid){status=invalid.getMessage();setEnabled(false);return;}
        var action=shots.tick(target,sight&&!blocked&&aligned&&(arena==null||now>=nextArenaShot),shortbow,now,interval);
        status=blocked?"Protected target blocks firing corridor":!sight?"Waiting for clear sightline":shortbow?"Shortbow tracking":"Bow tracking/drawing";
        switch(action){
            case USE -> {encounters.attack(mob.getUUID(),mob.getHealth(),now);client.gameMode.useItem(p,InteractionHand.MAIN_HAND);if(shortbow){p.stopUsingItem();shotCount++;if(arena!=null)nextArenaShot=now+interval;}else dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,true);}
            case RELEASE -> {encounters.attack(mob.getUUID(),mob.getHealth(),now);client.gameMode.releaseUsingItem(p);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);shotCount++;if(arena!=null)nextArenaShot=now+interval;}
            case CANCEL -> cancelDraw();
            case NONE -> {}
        }
    }
    private static boolean special(LivingEntity entity){return entity instanceof net.minecraft.world.entity.monster.Enderman e&&e.getCarriedBlock()!=null&&e.getCarriedBlock().is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME);}
    private boolean beamRayTarget(LivingEntity wanted,float yaw,float pitch,double range){
        var p=client.player;var eye=p.getEyePosition();var direction=net.minecraft.world.phys.Vec3.directionFromRotation(pitch,yaw);var end=eye.add(direction.scale(range));
        var block=client.level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));double limit=block.getType()==HitResult.Type.MISS?range:block.getLocation().distanceTo(eye);LivingEntity first=null;
        for(var e:client.level.entitiesForRendering())if(e instanceof LivingEntity l&&l!=p&&!(l instanceof ArmorStand)&&l.isAlive()){var hit=l.getBoundingBox().inflate(.1).clip(eye,end);if(hit.isPresent()){double distance=hit.get().distanceTo(eye);if(distance<limit){limit=distance;first=l;}}}return first==wanted;
    }
    private boolean protectedBeamRay(List<Target> candidates,float yaw,float pitch,double range){
        var p=client.player;var eye=p.getEyePosition();var end=eye.add(net.minecraft.world.phys.Vec3.directionFromRotation(pitch,yaw).scale(range));
        var block=client.level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double limit=block.getType()==HitResult.Type.MISS?range:block.getLocation().distanceTo(eye);
        // Include actors beyond the current mob: its death can expose them before OFF acknowledgment.
        for(var target:candidates)if(target.protectedMob()&&target.mob().isAlive()){
            var hit=target.mob().getBoundingBox().inflate(.1).clip(eye,end);
            if(hit.isPresent()&&hit.get().distanceTo(eye)<limit)return true;
        }return false;
    }
    private boolean protectedFlight(List<Target> candidates,float yaw,float pitch,boolean shortbow){
        var player=client.player;var eye=player.getEyePosition();var motion=player.getDeltaMovement();
        var inheritance=shortbow?new Vec3(0,0,0):new Vec3(motion.x,player.onGround()?0:motion.y,motion.z);
        var path=RangedBallistics.flight(new Vec3(eye.x,eye.y,eye.z),yaw,pitch,inheritance,shortbow);
        for(int tick=1;tick<path.size();tick++){
            var a=path.get(tick-1);var b=path.get(tick);var from=new net.minecraft.world.phys.Vec3(a.x(),a.y(),a.z());var to=new net.minecraft.world.phys.Vec3(b.x(),b.y(),b.z());
            for(var target:candidates)if(target.protectedMob()&&target.mob().isAlive()){
                var movement=target.mob().getDeltaMovement();var box=target.mob().getBoundingBox().expandTowards(movement.scale(tick));
                // Cover bow spread, packet/camera error and modest movement during arrow flight.
                if(box.inflate(.8+tick*.025).clip(from,to).isPresent())return true;
            }
            if(client.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)break;
        }return false;
    }
    private void cancelDraw(){
        dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);
        if(client.player!=enabledPlayer||client.level!=enabledWorld){shots.reset();return;}
        if(client.player!=null&&client.player.isUsingItem()) {
            // Switching carried slot cancels server bow use without releasing an arrow at a lost/protected target.
            int current=services.inventory().selectedSlot();services.inventory().selectSlot((current+1)%9);client.player.stopUsingItem();services.inventory().selectSlot(current);
        }
        shots.reset();
    }
    private void approach(Vec3 goal,long now){
        if(!walkingActive||now>=nextPath&&(lastGoal==null||lastGoal.distanceTo(goal)>1)){lastGoal=goal;nextPath=now+800;walkingActive=walking.start(goal);}
        if(walkingActive&&walking.tick()!=WalkController.State.WALKING)walkingActive=false;
    }
    private void patrol(){
        if(route==null){services.input().releaseAllMovement();status="No target; set Zealot Ranged Route for area patrol";return;}
        if(!patrolActive){patrolActive=patrol.start(route.nodes().get(routeIndex));if(!patrolActive){status="Patrol failed: "+patrol.status();setEnabled(false);return;}}
        var state=patrol.tick();status="Patrol "+(routeIndex+1)+"/"+route.nodes().size()+": "+patrol.status();
        if(state==McTravelNavigator.State.FAILED){setEnabled(false);return;}
        if(state==McTravelNavigator.State.ARRIVED){patrolActive=false;routeIndex=(routeIndex+1)%route.nodes().size();}
    }
    private List<Target> targets(double radius){
        List<LivingEntity> mobs=new ArrayList<>();List<ArmorStand> plates=new ArrayList<>();
        for(var e:client.level.entitiesForRendering())if(e!=client.player&&e.distanceToSqr(client.player)<(radius+3)*(radius+3)){if(e instanceof ArmorStand a&&a.hasCustomName())plates.add(a);else if(e instanceof LivingEntity l)mobs.add(l);}
        List<Target> out=new ArrayList<>();
        for(var mob:mobs){
            String name=mob.getName().getString();boolean protectedMob=mob instanceof Player||protectedName(name);
            var nearby=plates.stream().filter(a->a.getBoundingBox().inflate(.6,2,.6).intersects(mob.getBoundingBox())).toList();
            // Any nearby ownership warning blocks this mob even when the remaining labels are ambiguous.
            protectedMob|=nearby.stream().anyMatch(a->protectedName(a.getName().getString()));
            var labels=nearby.stream().filter(a->a.getName().getString().toLowerCase(Locale.ROOT).contains("zealot")).toList();
            if(labels.size()==1){var plate=labels.getFirst();long owners=mobs.stream().filter(m->plate.getBoundingBox().inflate(.6,2,.6).intersects(m.getBoundingBox())).count();if(owners==1)name=plate.getName().getString();else protectedMob=true;}
            out.add(new Target(mob,name,protectedMob));
        }
        return out;
    }
    public static boolean protectedName(String name){String n=name.toLowerCase(Locale.ROOT);return n.contains("[npc]")||n.contains("hunting this monster")||n.contains("belongs to");}
    public static boolean shortbow(String name){String n=name.toLowerCase(Locale.ROOT);return n.contains("shortbow")||n.contains("juju")||n.contains("terminator");}
    private int eyeCount(){int count=0;for(int i=0;i<36;i++){var item=client.player.getInventory().getItem(i);if(ZealotLootTracker.eye(item.getHoverName().getString()))count+=item.getCount();}return count;}
    private static Vec3 point(net.minecraft.world.entity.Entity e){return new Vec3(e.getX(),e.getY(),e.getZ());}
    private static double bounded(double v,double min,double max,double fallback){return Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
    @Override public String diagnostics(){return status+" | target="+target+" | shots="+shotCount+" | acknowledged eyes="+loot.confirmedEyes()+" | owned kills="+encounters.kills()+" | special kills="+encounters.specialKills()+" | arena="+(arena==null?"legacy":arena.status());}
}
