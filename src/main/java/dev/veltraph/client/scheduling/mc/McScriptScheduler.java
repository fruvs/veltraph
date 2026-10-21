package dev.veltraph.client.scheduling.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.game.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.rewarp.FarmReturnPoint;
import dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery;
import dev.veltraph.client.scheduling.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import java.util.*;
import java.util.function.*;
import java.util.random.RandomGenerator;

/** Exact owner scheduling and an explicitly owned vanilla rest departure/rejoin ticket. */
public final class McScriptScheduler {
    public record Contest(boolean active,long remainingSeconds){}
    public enum ArrivalStatus { ACTIVE,READY,FAILED }
    /** Different-area arrival requires an explicitly authorized source/local recovery port. */
    public interface ArrivalRecovery {
        boolean begin(FarmReturnPoint target,GameArea currentArea);
        ArrivalStatus tick(GameArea currentArea);
        boolean arrived(GameArea currentArea);
        void cancel();
        default void onServerMessage(String message){}
        default boolean consumeExpectedTeleport(Vec3 position,long now){return false;}
        default String diagnostics(){return "Arrival diagnostics unavailable";}
    }
    public record Binding(Supplier<ScheduledSkill> skillSupplier,ScheduledPausePort pause,Supplier<String> profile,Supplier<String> settings,
                          Supplier<Optional<String>> readiness,boolean boundaryCapable,BooleanSupplier atRewarpBoundary,
                          ArrivalRecovery arrival){
        public Binding{Objects.requireNonNull(skillSupplier);Objects.requireNonNull(pause);Objects.requireNonNull(profile);Objects.requireNonNull(settings);Objects.requireNonNull(readiness);Objects.requireNonNull(atRewarpBoundary);Objects.requireNonNull(arrival);}
        /** Existing fixed-skill ports keep their original construction and access contract. */
        public Binding(ScheduledSkill skill,ScheduledPausePort pause,Supplier<String> profile,Supplier<String> settings,
                       Supplier<Optional<String>> readiness,boolean boundaryCapable,BooleanSupplier atRewarpBoundary,ArrivalRecovery arrival){
            this(fixedSkill(skill),pause,profile,settings,readiness,boundaryCapable,atRewarpBoundary,arrival);
        }
        private static Supplier<ScheduledSkill> fixedSkill(ScheduledSkill skill){Objects.requireNonNull(skill);return ()->skill;}
        public ScheduledSkill skill(){return Objects.requireNonNull(skillSupplier.get(),"Scheduled skill unavailable");}
    }
    private static final List<EquipmentSlot> GEAR=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND);
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final ModuleManager modules;private final GameContextService context;
    private final BooleanSupplier higherPriorityOwned;private final LongSupplier lastRewarpMillis;private final Supplier<Contest> contest;
    private final RandomGenerator rangeRandom;private final IntSupplier delayRandom;private final NativeRestTransport transport;
    private final boolean restDispatchEnabled;
    private final Map<Module,Binding> bindings=new IdentityHashMap<>();private final Map<Module,Long> cooldowns=new IdentityHashMap<>();
    private record TimerIdentity(Supplier<String> profile,Supplier<String> settings){}
    private final Map<Module,TimerIdentity> timerBindings=new IdentityHashMap<>();
    private final Map<Module,Supplier<ScheduledSkill>> timerSkills=new IdentityHashMap<>();
    private final SourceStopTimer timer=new SourceStopTimer();private final SourceRestRuntime rest=new SourceRestRuntime();
    private record TimerPin(Module owner,TimerIdentity binding,String profile,String settings,Object player,Object world,Object connection){}
    private static final class Ticket{}
    private record Capture(Module owner,Binding binding,Ticket token,ScheduledSkill skill,String profile,String settings,String scheduleSettings,UUID playerId,
                           Object player,Object world,Object connection,FarmReturnPoint pose,Pose posture,int slot,NativeStackIdentity.Snapshot tool,Map<EquipmentSlot,NativeStackIdentity.Snapshot> gear,ScheduledMovementKeys keys){}
    private TimerRetention issuedFarmTimerRetention;
    private GeorgeTimerContinuation georgeTimerContinuation;
    private BottleTimerContinuation bottleTimerContinuation;
    private dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel bottleTimerTravel;
    private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeParticipant georgeTimerHelper;
    private dev.veltraph.client.rewarp.FarmMaintenanceLease.Token georgeTimerToken;
    private dev.veltraph.client.inventory.selling.GeorgeWarpPermit georgeTimerPermit;
    private GhostTimerContinuation ghostTimerContinuation;
    private dev.veltraph.client.combat.ghost.GhostWarpPermit ghostTimerPermit;
    private dev.veltraph.client.combat.ghost.GhostTransportLoop.Attempt ghostTimerAttempt;
    private TimerPin timerPin;private Capture captured;private Capture committing;private boolean internalStop,arrivalStarted,arrivalReady,rebound;private int timerStops,completedRests;
    private final dev.veltraph.client.core.AbstractModule inputDriver=new dev.veltraph.client.core.AbstractModule("scheduled_rest_return","Scheduled rest return",dev.veltraph.client.core.ModuleCategory.INTERMEDIARY){
        @Override public boolean requiresSkyBlock(){return true;}
        @Override public boolean hasTravelSetupPhase(){return ownsInput();}
        @Override public void onDisable(){if(!internalStop&&captured!=null)cancel("Owned scheduled return stopped");}
    };
    private Object joinedPlayer,joinedWorld,joinedConnection;private long postureSettled=-1;
    public McScriptScheduler(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context,
                             BooleanSupplier higherPriorityOwned,LongSupplier lastRewarpMillis,Supplier<Contest> contest){
        this(client,services,config,modules,context,higherPriorityOwned,lastRewarpMillis,contest,new SplittableRandom(),()->java.util.concurrent.ThreadLocalRandom.current().nextInt(20,120));
    }
    public McScriptScheduler(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context,
                             BooleanSupplier higherPriorityOwned,LongSupplier lastRewarpMillis,Supplier<Contest> contest,RandomGenerator rangeRandom,IntSupplier delayRandom){
        this(client,services,config,modules,context,higherPriorityOwned,lastRewarpMillis,contest,rangeRandom,delayRandom,true);
    }
    private McScriptScheduler(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context,
                             BooleanSupplier higherPriorityOwned,LongSupplier lastRewarpMillis,Supplier<Contest> contest,RandomGenerator rangeRandom,IntSupplier delayRandom,boolean restDispatchEnabled){
        this.client=client;this.services=services;this.config=config;this.modules=modules;this.context=context;this.higherPriorityOwned=higherPriorityOwned;this.lastRewarpMillis=lastRewarpMillis;this.contest=contest;
        this.rangeRandom=rangeRandom;this.delayRandom=delayRandom;transport=new NativeRestTransport(client);
        this.restDispatchEnabled=restDispatchEnabled;if(restDispatchEnabled)modules.register(inputDriver);
    }
    public static McScriptScheduler timerOnly(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context){
        return new McScriptScheduler(client,services,config,modules,context,()->false,()->0,()->new Contest(false,0),new SplittableRandom(),()->20,false);
    }
    public void register(Module owner,Binding binding){if(!restDispatchEnabled)throw new IllegalStateException("Full rest dispatch is not registered");if(bindings.putIfAbsent(Objects.requireNonNull(owner),Objects.requireNonNull(binding))!=null)throw new IllegalArgumentException("Duplicate scheduler capability");registerTimer(owner,binding.skillSupplier(),binding.profile(),binding.settings());}
    /** Terminal stop requires identity metadata, not a fictitious rest-capable engine adapter. */
    public void registerTimer(Module owner,Supplier<String> profile,Supplier<String> settings){if(timerBindings.putIfAbsent(Objects.requireNonNull(owner),new TimerIdentity(Objects.requireNonNull(profile),Objects.requireNonNull(settings)))!=null)throw new IllegalArgumentException("Duplicate timer owner");}
    public void registerTimer(Module owner,ScheduledSkill skill,Supplier<String> profile,Supplier<String> settings){registerTimer(owner,()->skill,profile,settings);}
    public void registerTimer(Module owner,Supplier<ScheduledSkill> skill,Supplier<String> profile,Supplier<String> settings){registerTimer(owner,profile,settings);timerSkills.put(owner,Objects.requireNonNull(skill));}
    public boolean registeredOwner(Module owner){return timerBindings.containsKey(owner);}
    /** Standard activation must reject enabled options whose actual owner port/config is incomplete. */
    public Optional<String> activationReadiness(Module owner){
        thread();if(!registeredOwner(owner))return Optional.empty();
        try{if(config.get(SchedulingConfig.TIMER_ENABLED))new SourceStopTimer().arm(config.get(SchedulingConfig.TIMER_DURATION),config.get(SchedulingConfig.TIMER_UNIT));
            var skillSupplier=timerSkills.get(owner);var skill=skillSupplier==null?null:skillSupplier.get();if(config.get(MiscConfig.DYNAMIC_REST_ENABLED)&&skill!=null&&skill.enabled(config)){
                if(!bindings.containsKey(owner))return Optional.of("Dynamic rest has no verified engine-preserving port for "+skill);
                if(!NativeRestTransport.cancellationAvailable())return Optional.of("Native scheduled connection cancellation is unavailable");
                SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN));SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_BREAK));
                if(config.get(SchedulingConfig.REST_MIN_CONTEST_REMAINING_SECONDS)<0||config.get(SchedulingConfig.REST_RECENT_REWARP_GUARD_MS)<0)return Optional.of("Configure the explicit contest/recent-rewarp readiness policy before dynamic rest");
            }return Optional.empty();
        }catch(IllegalArgumentException error){return Optional.of(error.getMessage());}
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Scheduler requires client thread");}
    private boolean known(){var ctx=context.current();return ctx.onHypixel()&&ctx.inSkyBlock()&&ctx.area().isSkyBlock()&&ctx.area()!=GameArea.UNKNOWN&&ctx.area()!=GameArea.LIMBO;}
    /** Called by root after an actual fresh, successful standard activation, never on resume. */
    public void ownerStarted(Module owner){thread();var binding=bindings.get(owner);
        if(binding!=null&&config.get(MiscConfig.DYNAMIC_REST_ENABLED)&&binding.skill().enabled(config))
            cooldowns.put(owner,Math.addExact(services.clock().nowMillis(),SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN)).cooldownMillis(rangeRandom)));
        else cooldowns.remove(owner);
        if(config.get(SchedulingConfig.TIMER_ENABLED))armTimer(owner,config.get(SchedulingConfig.TIMER_DURATION),config.get(SchedulingConfig.TIMER_UNIT));
    }
    public boolean armTimer(Module owner,double amount,ScheduleTimeUnit unit){
        thread();var binding=timerBindings.get(owner);if(binding==null||!owner.isEnabled()||client.player==null||client.level==null||client.getConnection()==null)return false;
        clearIssuedFarmTimer();clearGhostTimerContinuation();timer.arm(amount,unit);timerPin=new TimerPin(owner,binding,binding.profile().get(),binding.settings().get(),client.player,client.level,client.getConnection());return true;
    }
    /** Private scheduler capture of the pre-pause countdown; callers cannot construct or replace it. */
    public final class TimerRetention {
        private final McScriptScheduler scheduler=McScriptScheduler.this;
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall pause;
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.SourceHeader source;
        private final dev.veltraph.client.farming.module.FarmModule farm;
        private final TimerPin originalPin;
        private final SourceStopTimer.State originalState;
        private final int originalRemaining;
        private final IssuedFarmTimerCadence cadence;
        private final ModuleManager.LifecycleSnapshot originalLifecycle;
        private final MapConfigService.StoredKeysSnapshot settings;
        private final Object originalClock;
        private final long admissionTick;
        private boolean updated;
        private final Object player,world,connection;
        private boolean retired;
        private TimerRetention(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall pause,
                TimerPin pin,ModuleManager.LifecycleSnapshot lifecycle,MapConfigService.StoredKeysSnapshot settings,
                Object clock,long tick,SourceStopTimer.State state,int remaining,Object player,Object world,Object connection){
            this.pause=pause;source=pause.sourceHeader();farm=pause.farm();originalPin=pin;originalLifecycle=lifecycle;this.settings=settings;originalClock=clock;admissionTick=tick;
            originalState=state;originalRemaining=remaining;cadence=new IssuedFarmTimerCadence(timer,originalRemaining);
            this.player=player;this.world=world;this.connection=connection;
        }
    }
    /** Selected before the actual family pause. No new countdown is armed by this capture. */
    public TimerRetention captureIssuedFarmTimer(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall pause){
        thread();
        if(pause==null||issuedFarmTimerRetention!=null||!canonicalScheduler()||!pause.current(pause.farm())
                ||pause.sourceHeader().registryIdentity()!=modules||pause.sourceHeader().configIdentity()!=config
                ||pause.sourceHeader().farmServicesIdentity()!=services||captured!=null||rest.active()
                ||ghostTimerContinuation!=null||georgeTimerContinuation!=null||bottleTimerContinuation!=null)return null;
        var clock=services.clock();long tick=clock.currentTick();if(tick<0)return null;
        var settings=timerSettingsSnapshot();if(settings==null)return null;
        var farm=pause.farm();var pin=timerPin;
        var p=client.player;var w=client.level;var c=client.getConnection();
        var lifecycle=modules.lifecycleSnapshot();
        var state=timer.state();int remaining=timer.remainingTicks();
        boolean armed=state==SourceStopTimer.State.ARMED;
        if(!lifecycle.known()||client.player==null||client.level==null||client.getConnection()==null
                ||pause.sourceHeader().originalPlayer()!=client.player||pause.sourceHeader().originalWorld()!=client.level
                ||pause.sourceHeader().originalConnection()!=client.getConnection()
                ||armed&&(pin==null||pin.owner()!=farm||!same(pin)||pin.player()!=client.player||pin.world()!=client.level||pin.connection()!=client.getConnection())
                ||!armed&&pin!=null)return null;
        // Clock and profile/settings suppliers run before the final source and registry leaves.
        if(services.clock()!=clock||clock.currentTick()!=tick)return null;
        if(!pause.current(farm)||!modules.lifecycleCurrent(lifecycle)||!canonicalScheduler()||timerPin!=pin
                ||state!=timer.state()||remaining!=timer.remainingTicks()||client.player!=p||client.level!=w||client.getConnection()!=c
                ||pause.sourceHeader().originalPlayer()!=p||pause.sourceHeader().originalWorld()!=w||pause.sourceHeader().originalConnection()!=c
                ||!settings.current())return null;
        var retention=new TimerRetention(pause,pin,lifecycle,settings,clock,tick,state,remaining,p,w,c);issuedFarmTimerRetention=retention;return retention;
    }
    private MapConfigService.StoredKeysSnapshot timerSettingsSnapshot(){
        var keys=java.util.List.<ConfigKey<?>>of(SchedulingConfig.TIMER_ENABLED,SchedulingConfig.TIMER_DURATION,SchedulingConfig.TIMER_UNIT);
        return config instanceof MapConfigService map?map.storedKeysSnapshot(keys):config instanceof JsonConfigService json?json.storedKeysSnapshot(keys):null;
    }
    /** Typed old-source disposal only; it preserves the exact original timer and count. */
    public boolean retireIssuedFarmTimer(TimerRetention retained,dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.MetadataCloseCall call){
        thread();if(retained==null||retained.scheduler!=this||call==null||!canonicalScheduler()
            ||call.client()!=client||call.sourceHeader()!=retained.source||!call.currentMetadata())return false;
        if(retained.retired)return true;
        if(retained!=issuedFarmTimerRetention)return false;clearIssuedFarmTimer();return true;
    }
    /** The actual Root close cause can dispose the captured timer continuation before driver disable. */
    public boolean retireIssuedFarmTimer(TimerRetention retained,dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.CloseCall call){
        thread();if(retained==null||retained.scheduler!=this||call==null||!canonicalScheduler()
            ||call.client()!=client||call.sourceHeader()!=retained.source||!call.current())return false;
        if(retained.retired)return true;
        if(retained!=issuedFarmTimerRetention)return false;clearIssuedFarmTimer();return true;
    }
    private boolean canonicalScheduler(){return dev.veltraph.client.VeltraphBootstrap.scriptScheduler()==this;}
    private void clearIssuedFarmTimer(){var old=issuedFarmTimerRetention;issuedFarmTimerRetention=null;if(old!=null){old.retired=true;old.cadence.revoke();}}
    /** Only the real Root dispatch supplies this private Core call; this never runs dynamic-rest work. */
    public boolean tickIssuedFarmTimer(TimerRetention retained,
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.TimerCall call){
        thread();
        if(retained==null||call==null||retained!=issuedFarmTimerRetention||retained.retired||!canonicalScheduler())return false;
        var frame=call.rootTickIdentity();
        boolean armed=retained.originalState==SourceStopTimer.State.ARMED;
        // All callback-bearing metadata observations precede the final original native proof.
        boolean metadata=!armed||same(retained.originalPin);
        if(!metadata||call.farm()!=retained.farm||call.sourceHeader()!=retained.source||call.originalPauseCall()!=retained.pause
                ||call.client()!=client||call.coordinatorIdentity()!=dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator()||call.originalPlayer()!=retained.player||call.originalWorld()!=retained.world
                ||call.originalConnection()!=retained.connection||timerPin!=retained.originalPin||!retained.settings.current()
                ||armed&&timer.state()!=SourceStopTimer.State.ARMED
                ||!armed&&(timer.state()!=retained.originalState||timer.remainingTicks()!=retained.originalRemaining)
                ||frame==null||!call.currentFinalNative()||!canonicalScheduler())return rejectIssuedFarmTimer(retained);
        if(services.clock()!=retained.originalClock||!retained.updated&&!IssuedFarmTimerCadence.firstFrameFits(retained.admissionTick,frame.clockTick()))return rejectIssuedFarmTimer(retained);
        var update=retained.cadence.observe(timer,timer.remainingTicks(),frame,frame.clockTick());
        if(update==IssuedFarmTimerCadence.Update.REFUSED)return rejectIssuedFarmTimer(retained);
        if(update==IssuedFarmTimerCadence.Update.DUPLICATE)return true;
        retained.updated=true;
        // Recheck after metadata and cadence observation. Native TimerCall never invokes providers here.
        if(!call.currentFinalNative()||timerPin!=retained.originalPin||!canonicalScheduler())return rejectIssuedFarmTimer(retained);
        if(!armed){
            if(!retained.cadence.recordWithoutTick(timer,frame,frame.clockTick(),timer.remainingTicks()))return rejectIssuedFarmTimer(retained);
            if(call.resumeCommitted()){retained.cadence.finishWithoutTick(timer,frame,frame.clockTick(),timer.remainingTicks());clearIssuedFarmTimer();}
            return true;
        }
        boolean expired=timer.tick();
        if(!retained.cadence.record(timer,frame,frame.clockTick(),timer.remainingTicks(),expired))return rejectIssuedFarmTimer(retained);
        if(expired){
            var owner=retained.farm;timerPin=null;clearGhostTimerContinuation();clearIssuedFarmTimer();timerStops++;
            if(owner.isEnabled())modules.disable(owner.id());return true;
        }
        if(call.resumeCommitted()){
            // Only the private normally completed same-engine resume may replace native pin references.
            if(!call.currentFinalNative()||client.player!=call.currentPlayer()||client.level!=call.currentWorld()
                    ||client.getConnection()!=call.currentConnection()||timerPin!=retained.originalPin)return rejectIssuedFarmTimer(retained);
            var pin=retained.originalPin;timerPin=new TimerPin(pin.owner(),pin.binding(),pin.profile(),pin.settings(),
                call.currentPlayer(),call.currentWorld(),call.currentConnection());clearIssuedFarmTimer();
        }
        return true;
    }
    private boolean rejectIssuedFarmTimer(TimerRetention retained){
        if(retained==issuedFarmTimerRetention){
            if(retained.originalState==SourceStopTimer.State.ARMED&&timerPin==retained.originalPin){timer.cancel();timerPin=null;clearGhostTimerContinuation();}
            clearIssuedFarmTimer();
        }
        return false;
    }
    private boolean same(TimerPin pin){return pin!=null&&pin.owner().isEnabled()&&Objects.equals(pin.profile(),pin.binding().profile().get())&&Objects.equals(pin.settings(),pin.binding().settings().get());}
    /** Root calls after its exact issued-warp safety decision, before this update's TIMER tick. */
    public boolean retainGhostTimer(dev.veltraph.client.combat.ghost.GhostGrindModule owner,dev.veltraph.client.combat.ghost.GhostWarpPermit permit){
        return retainGhostTimer(owner,permit,permit,permit==null?null:permit.attempt());
    }
    public boolean retainGhostTimer(dev.veltraph.client.combat.ghost.GhostGrindModule owner,dev.veltraph.client.combat.ghost.GhostWarpPermit admittedOrigin,
            dev.veltraph.client.combat.ghost.GhostWarpPermit current,dev.veltraph.client.combat.ghost.GhostTransportLoop.Attempt attempt){
        thread();if(timer.state()!=SourceStopTimer.State.ARMED)return true;
        if(owner==null||admittedOrigin==null||attempt==null||timerPin==null||timerPin.owner()!=owner||!same(timerPin)
                ||!admittedOrigin.previousContext(timerPin.player(),timerPin.world(),timerPin.connection())
                ||owner.pendingNativeWarp().orElse(null)!=current||owner.pendingTransportAttempt().orElse(null)!=attempt||moduleOwner()!=owner){clearGhostTimerContinuation();return false;}
        if(ghostTimerContinuation==null)ghostTimerContinuation=new GhostTimerContinuation(owner,timerPin,admittedOrigin);
        boolean granted=ghostTimerContinuation.grant(owner,timerPin,current,attempt,client.getConnection(),client.player==null?null:client.player.getUUID(),
                owner.isEnabled(),same(timerPin),services.clock().nowMillis());
        if(granted){ghostTimerPermit=current;ghostTimerAttempt=attempt;}return granted;
    }
    /** Arrival changes only the native pin; the original timer instance/count and rest cooldown survive. */
    public boolean commitGhostTimer(dev.veltraph.client.combat.ghost.GhostGrindModule owner,dev.veltraph.client.combat.ghost.GhostWarpPermit permit){
        thread();if(timer.state()!=SourceStopTimer.State.ARMED){clearGhostTimerContinuation();return owner!=null&&owner.isEnabled();}
        if(timerPin==null||timerPin.owner()!=owner||!same(timerPin)||moduleOwner()!=owner||ghostTimerContinuation==null
                ||!ghostTimerContinuation.owns(owner,timerPin,permit)||client.player==null||client.level==null)return false;
        var receipt=dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(client);
        if(!permit.arrival(client.player,client.level,client.getConnection(),client.player.getUUID(),client.player.isAlive(),
                client.player.level()==client.level,client.level.getChunkSource().hasChunk(client.player.blockPosition().getX()>>4,client.player.blockPosition().getZ()>>4),
                context.current().area(),client.level.dimension().identifier().toString(),position(),receipt.serial(),receipt.position(),services.clock().nowMillis()))return false;
        var pin=timerPin;timerPin=new TimerPin(owner,pin.binding(),pin.profile(),pin.settings(),client.player,client.level,client.getConnection());
        clearGhostTimerContinuation();return true;
    }
    private Module moduleOwner(){var owners=modules.enabledControllers();return owners.size()==1?modules.inputOwner().orElse(null):null;}
    private void clearGhostTimerContinuation(){clearGeorgeTimerContinuation();clearBottleTimerContinuation();if(ghostTimerContinuation!=null)ghostTimerContinuation.revoke();ghostTimerContinuation=null;ghostTimerPermit=null;ghostTimerAttempt=null;}
    public boolean retainBottleTimer(dev.veltraph.client.inventory.bottle.BottleAcquisitionModule owner,dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel travel){
        thread();if(timer.state()!=SourceStopTimer.State.ARMED)return true;
        if(owner==null||travel==null||timerPin==null||timerPin.owner()!=owner||!same(timerPin)||moduleOwner()!=owner||owner.pendingNativeWarp().orElse(null)!=travel
                ||travel.player()!=timerPin.player()||travel.world()!=timerPin.world()||travel.connection()!=timerPin.connection())return false;
        if(bottleTimerContinuation==null)bottleTimerContinuation=new BottleTimerContinuation(owner,timerPin,travel);
        bottleTimerTravel=travel;return bottleTimerContinuation.grant(owner,timerPin,travel,client.getConnection(),client.player==null?null:client.player.getUUID(),same(timerPin),services.clock().nowMillis());
    }
    public boolean commitBottleTimer(dev.veltraph.client.inventory.bottle.BottleAcquisitionModule owner,dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel travel){
        thread();if(timer.state()!=SourceStopTimer.State.ARMED){clearBottleTimerContinuation();return owner!=null&&owner.isEnabled();}
        if(timerPin==null||timerPin.owner()!=owner||!same(timerPin)||moduleOwner()!=owner||bottleTimerContinuation==null||!bottleTimerContinuation.owns(owner,timerPin,travel)
                ||owner.pendingNativeWarp().isPresent()||client.player==null||client.level==null||client.getConnection()!=travel.connection()||!client.player.getUUID().equals(travel.uuid()))return false;
        var receipt=dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(client);
        if(receipt.serial()<=travel.serial()||receipt.position()==null||receipt.position().distanceTo(travel.landing().position())>.9
                ||context.current().area()!=travel.landing().area()||!client.level.dimension().identifier().toString().equals(travel.landing().dimension()))return false;
        var pin=timerPin;timerPin=new TimerPin(owner,pin.binding(),pin.profile(),pin.settings(),client.player,client.level,client.getConnection());clearBottleTimerContinuation();return true;
    }
    private boolean consumeBottleTimerUpdate(){return bottleTimerContinuation!=null&&timerPin!=null&&timerPin.owner() instanceof dev.veltraph.client.inventory.bottle.BottleAcquisitionModule owner
            &&owner.pendingNativeWarp().orElse(null)==bottleTimerTravel&&moduleOwner()==owner&&bottleTimerContinuation.consume(owner,timerPin,bottleTimerTravel,client.getConnection(),
            client.player==null?null:client.player.getUUID(),same(timerPin),services.clock().nowMillis());}
    private void clearBottleTimerContinuation(){if(bottleTimerContinuation!=null)bottleTimerContinuation.revoke();bottleTimerContinuation=null;bottleTimerTravel=null;}
    private boolean consumeGhostTimerUpdate(){
        return ghostTimerContinuation!=null&&timerPin!=null&&timerPin.owner() instanceof dev.veltraph.client.combat.ghost.GhostGrindModule owner
                &&owner.pendingNativeWarp().orElse(null)==ghostTimerPermit&&owner.pendingTransportAttempt().orElse(null)==ghostTimerAttempt&&moduleOwner()==owner
                &&ghostTimerContinuation.consume(owner,timerPin,ghostTimerPermit,ghostTimerAttempt,client.getConnection(),client.player==null?null:client.player.getUUID(),
                owner.isEnabled(),same(timerPin),services.clock().nowMillis());
    }
    public boolean retainGeorgeTimer(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeCapture capture,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeParticipant helper,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit){
        thread();if(timer.state()!=SourceStopTimer.State.ARMED)return true;
        if(capture==null||helper==null||permit==null||timerPin==null||timerPin.owner()!=capture.farm()||!same(timerPin)
                ||!capture.farm().maintenanceOwnedBy(capture.token())||!helper.isEnabled()||helper.pendingGeorgeWarp().orElse(null)!=permit
                ||!georgeOwner(capture.farm(),helper)||permit.pending().player()!=timerPin.player()||permit.pending().previousWorld()!=timerPin.world()
                ||permit.pending().connection()!=timerPin.connection()||!permit.pending().uuid().equals(capture.playerId()))return false;
        if(georgeTimerContinuation==null)georgeTimerContinuation=new GeorgeTimerContinuation(capture.farm(),helper,capture.token(),timerPin,permit);
        georgeTimerHelper=helper;georgeTimerToken=capture.token();georgeTimerPermit=permit;
        return georgeTimerContinuation.grant(capture.farm(),helper,capture.token(),timerPin,permit,client.getConnection(),client.player==null?null:client.player.getUUID(),
                same(timerPin),services.clock().currentTick());
    }
    public boolean commitGeorgeTimer(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeCapture capture,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeParticipant helper,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit){
        thread();if(timer.state()!=SourceStopTimer.State.ARMED){clearGeorgeTimerContinuation();return capture!=null&&capture.farm().isEnabled();}
        if(capture==null||timerPin==null||!same(timerPin)||georgeTimerContinuation==null||permit.state()!=dev.veltraph.client.inventory.selling.GeorgeWarpPermit.State.COMMITTED
                ||!georgeTimerContinuation.owns(capture.farm(),helper,capture.token(),timerPin,permit)||!capture.farm().maintenanceOwnedBy(capture.token())
                ||!helper.isEnabled()||client.player!=capture.player()||client.level!=capture.world()||client.getConnection()!=capture.connection())return false;
        var pin=timerPin;timerPin=new TimerPin(pin.owner(),pin.binding(),pin.profile(),pin.settings(),client.player,client.level,client.getConnection());clearGeorgeTimerContinuation();return true;
    }
    private boolean georgeOwner(dev.veltraph.client.farming.module.FarmModule farm,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeParticipant helper){
        var controllers=modules.enabledControllers();return controllers.size()==2&&controllers.contains(farm)&&controllers.contains(helper)&&modules.inputOwner().orElse(null)==helper;
    }
    private boolean consumeGeorgeTimerUpdate(){return georgeTimerContinuation!=null&&timerPin!=null&&timerPin.owner() instanceof dev.veltraph.client.farming.module.FarmModule farm
            &&farm.maintenanceOwnedBy(georgeTimerToken)&&georgeTimerHelper.isEnabled()&&georgeTimerHelper.pendingGeorgeWarp().orElse(null)==georgeTimerPermit
            &&georgeOwner(farm,georgeTimerHelper)&&georgeTimerContinuation.consume(farm,georgeTimerHelper,georgeTimerToken,timerPin,georgeTimerPermit,client.getConnection(),
            client.player==null?null:client.player.getUUID(),same(timerPin),services.clock().currentTick());}
    private void clearGeorgeTimerContinuation(){if(georgeTimerContinuation!=null)georgeTimerContinuation.revoke();georgeTimerContinuation=null;georgeTimerHelper=null;georgeTimerToken=null;georgeTimerPermit=null;}
    private void timerTick(){
        if(issuedFarmTimerRetention!=null)clearIssuedFarmTimer();
        if(timer.state()!=SourceStopTimer.State.ARMED)return;
        boolean ghostUpdate=consumeGhostTimerUpdate()|consumeGeorgeTimerUpdate()|consumeBottleTimerUpdate();
        boolean contextMatches=ghostUpdate||timerPin!=null&&(client.player==timerPin.player()&&client.level==timerPin.world()&&client.getConnection()==timerPin.connection()
                ||captured!=null&&captured.owner()==timerPin.owner()&&rest.active());
        if(!same(timerPin)||!contextMatches){timer.cancel();timerPin=null;clearGhostTimerContinuation();return;}
        if(timer.tick()){var owner=timerPin.owner();timerPin=null;clearGhostTimerContinuation();timerStops++;if(captured!=null)cancel("Terminal TIMER expired during rest");if(owner.isEnabled())modules.disable(owner.id());}
    }
    private Module preparingOwner;private Binding preparingBinding;private Object preparingCause;private boolean preparationRenewed,preparingStopDispatch,preparationVersionExhausted;private long preparationVersion,stopDispatchVersion;private BooleanSupplier miningRestAdmissionFinal;private Predicate<Capture> miningRestPausedAdmissionFinal;private Predicate<Capture> miningOriginalCleanupCurrent;private boolean miningRestBeginAllowed;
    private Capture miningCaptureInFlight;private NativeRestTransport.IssuedFarmSnapshot miningTransportInFlight;private boolean miningRevoking,miningPauseInProgress,miningStartInProgress;private Capture miningStartReturned;
    private Throwable miningRevocationFailure,miningCommitFailure;
    private void clearRestPreparation(){
        // Cancellation is observable even before the first cause was minted.
        // Never reset/wrap this history during a callback-bearing admission.
        if(preparationVersion==Long.MAX_VALUE)preparationVersionExhausted=true;else preparationVersion++;
        var binding=preparingBinding;var cause=preparingCause;preparingOwner=null;preparingBinding=null;preparingCause=null;
        if(binding!=null&&cause!=null)binding.pause().cancelDueRestPreparation(cause);
    }
    private MapConfigService.StoredKeysSnapshot restStopSettings(){
        var keys=java.util.List.<ConfigKey<?>>of(MiscConfig.DYNAMIC_REST_ENABLED,SchedulingConfig.AUTO_BREAK_MINING,
                SchedulingConfig.AUTOBREAK_COOLDOWN,SchedulingConfig.AUTOBREAK_BREAK,SchedulingConfig.REST_DURING_REWARP_ONLY,
                SchedulingConfig.REST_MIN_CONTEST_REMAINING_SECONDS,SchedulingConfig.REST_RECENT_REWARP_GUARD_MS,SchedulingConfig.REST_REJOIN_TIMEOUT_MS,
                MiningConfig.AUTO_BREAK_MINING,MiningConfig.COBBLE_AUTO_WALK,MiningConfig.COBBLE_AUTO_SPRINT,MiningConfig.COBBLE_AUTO_CHANGE_DIR,
                MiningConfig.COBBLE_HOLD_RIGHT_CLICK,MiningConfig.COBBLE_CRAFT_STORE_ENABLED,MiningConfig.COBBLE_CRAFT_STORE_PROFILE,
                MiningConfig.MINING_MIN_Y,MiningConfig.MINING_BREAK_TIMEOUT_MS,MiningConfig.MINING_DURABILITY_RESERVE);
        return config instanceof MapConfigService map?map.storedKeysSnapshot(keys):config instanceof JsonConfigService json?json.storedKeysSnapshot(keys):null;
    }
    public Optional<String> restReadiness(Module owner){return restReadiness(owner,true);}
    private Optional<String> restReadiness(Module owner,boolean engineReady){
        thread();if(!restDispatchEnabled)return Optional.of("Full dynamic rest dispatch is not registered");var binding=bindings.get(owner);if(binding==null)return Optional.of("No explicit engine-preserving scheduler port for this owner");
        if(rest.active())return Optional.of("Another scheduled rest ticket owns control");
        if(!NativeRestTransport.cancellationAvailable())return Optional.of("Native scheduled connection cancellation is unavailable");
        if(!config.get(MiscConfig.DYNAMIC_REST_ENABLED)||!binding.skill().enabled(config))return Optional.of("Dynamic rest/skill is disabled");
        if(modules.enabledControllers().size()!=1||modules.enabledControllers().getFirst()!=owner||!owner.isEnabled()||higherPriorityOwned.getAsBoolean())return Optional.of("One exact healthy owner with no higher-priority operation is required");
        if(!known()||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Healthy known-area native context with closed menu is required");
        if(!client.player.onGround()||!new McNavigator(client,services.input(),config).standingClear(position()))return Optional.of("Loaded supported rest pose is required");
        if(client.getCurrentServer()==null||client.hasSingleplayerServer())return Optional.of("Scheduled rest requires a native multiplayer server entry");
        try{SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN));SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_BREAK));}catch(IllegalArgumentException error){return Optional.of(error.getMessage());}
        var c=contest.get();long since=Math.max(0,services.clock().nowMillis()-lastRewarpMillis.getAsLong());
        var sourceGate=new RestReadiness(true,binding.boundaryCapable(),config.get(SchedulingConfig.REST_DURING_REWARP_ONLY),binding.atRewarpBoundary().getAsBoolean(),false,c.active(),c.remainingSeconds(),config.get(SchedulingConfig.REST_MIN_CONTEST_REMAINING_SECONDS),since,config.get(SchedulingConfig.REST_RECENT_REWARP_GUARD_MS));
        if(!sourceGate.permits())return Optional.of("Source boundary/contest/recent-rewarp policy is not ready or not configured");
        return engineReady?binding.readiness().get():Optional.empty();
    }
    /** Read-only owned Ghost SEARCH guard; due work waits for Root.tick(), never emits transport here. */
    public dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result sourceGhostSearchGuard(Module owner){
        thread();
        if(owner==null||modules.get(owner.id()).orElse(null)!=owner||!owner.isEnabled())return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNKNOWN;
        if(ownsPaused(owner))return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.CLAIMED;
        if(rest.active())return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNKNOWN;
        var binding=bindings.get(owner);
        if(!config.get(MiscConfig.DYNAMIC_REST_ENABLED))return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNHANDLED;
        if(binding==null)return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNKNOWN;
        if(!binding.skill().enabled(config))return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNHANDLED;
        if(activationReadiness(owner).isPresent())return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNKNOWN;
        var deadline=cooldowns.get(owner);
        if(deadline==null)return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNKNOWN;
        if(services.clock().nowMillis()<=deadline||restReadiness(owner).isPresent())return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNHANDLED;
        return dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.Result.UNKNOWN;
    }
    /** Root calls only after the source's higher-priority helper/contest dispatch has had its opportunity. */
    public void tick(){
        thread();if(preparingStopDispatch)return;
        if(miningRevocationFailure!=null){if(miningRevocationFailure instanceof Error error)throw error;throw (RuntimeException)miningRevocationFailure;}
        preparationRenewed=false;try{tickSchedule();}catch(RuntimeException failure){if(miningCommitFailure==failure)throw failure;cancel("Scheduler action failed; captured owner stopped");}
        finally{if(!preparationRenewed)clearRestPreparation();}
    }
    private void tickSchedule(){
        // A resume callback can reenter dispatch; it cannot issue another resume or consume TIMER twice.
        if(committing!=null){if(!hasCapturedArrival(committing.owner(),committing.token()))cancel("Arrival lost during synchronous resume");return;}
        timerTick();if(ghostTimerContinuation!=null||georgeTimerContinuation!=null)return;if(rest.active()){advanceRest();return;}
        if(!restDispatchEnabled)return;
        if(!config.get(MiscConfig.DYNAMIC_REST_ENABLED))return;
        var controllers=modules.enabledControllers();if(controllers.size()!=1)return;var owner=controllers.getFirst();var deadline=cooldowns.get(owner);
        var binding=bindings.get(owner);
        if(deadline==null&&binding!=null&&binding.skill().enabled(config)&&activationReadiness(owner).isEmpty()){
            cooldowns.put(owner,Math.addExact(services.clock().nowMillis(),SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN)).cooldownMillis(rangeRandom)));return;
        }
        if(deadline!=null&&binding!=null&&binding.pause() instanceof dev.veltraph.client.mining.MiningScheduledPausePort){
            // Guard the entire due admission, including its callback-bearing
            // source-policy observers, before any nested dispatch can tick TIMER.
            preparingStopDispatch=true;
            try{
                long originalPreparationVersion=preparationVersion;stopDispatchVersion=originalPreparationVersion;
                var settings=restStopSettings();var lifecycle=modules.lifecycleSnapshot();
                var originalPlayer=client.player;var originalWorld=client.level;var originalConnection=client.getConnection();
                var originalServer=client.getCurrentServer();var originalNative=RestCommitNativeSnapshot.capture(client);
                var nativeHeaders=context instanceof dev.veltraph.client.game.mc.McGameContextService actual?actual.gameplaySignalsSnapshot():null;
                if(services.clock().nowMillis()<=deadline)return;
                if(binding.pause().supportsDueRestPreparation()){
                    boolean policy=restReadiness(owner,false).isEmpty();
                    // Priority and clock are the final callback-bearing reads.
                    // Every original native/config/lifecycle leaf follows them.
                    boolean priority=higherPriorityOwned.getAsBoolean();long now=services.clock().nowMillis();
                    var actualOwner=modules.inputOwner().orElse(null);
                    if(preparationVersionExhausted||preparationVersion!=originalPreparationVersion)return;
                    boolean current=policy&&!priority&&now>deadline&&!preparationVersionExhausted&&preparationVersion==originalPreparationVersion&&originalNative!=null&&originalNative.current()&&nativeHeaders!=null&&nativeHeaders.current()&&settings!=null&&settings.current()&&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)
                            &&bindings.get(owner)==binding&&cooldowns.get(owner)==deadline&&actualOwner==owner&&!rest.active()
                            &&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==originalConnection&&client.getCurrentServer()==originalServer
                            &&originalPlayer!=null&&originalWorld!=null&&originalConnection!=null
                            &&originalPlayer.isAlive()&&originalPlayer.onGround()&&client.gui.screen()==null
                            &&originalPlayer.containerMenu==originalPlayer.inventoryMenu&&originalPlayer.inventoryMenu.getCarried().isEmpty();
                    if(!current)return;
                    {
                        if(preparingOwner!=owner||preparingBinding!=binding)clearRestPreparation();
                        if(preparationVersionExhausted)return;
                        if(preparingCause==null){preparingOwner=owner;preparingBinding=binding;preparingCause=new Object();}
                        stopDispatchVersion=preparationVersion;
                        var exactCause=preparingCause;long admittedPreparationVersion=preparationVersion;
                        boolean prepared=binding.pause().prepareForDueRest(exactCause);
                        preparationRenewed=prepared&&!preparationVersionExhausted&&preparationVersion==admittedPreparationVersion&&preparingOwner==owner&&preparingBinding==binding&&preparingCause==exactCause
                                &&nativeHeaders.current()&&settings.current()&&modules.lifecycleCurrent(lifecycle);
                        if(!preparationRenewed)return;
                    }
                }
                // Carry the actual private admission snapshots through the final
                // readiness callback inside beginRest, before transport capture.
                miningRestAdmissionFinal=()->{
                    boolean priority=higherPriorityOwned.getAsBoolean();var actualOwner=modules.inputOwner().orElse(null);
                    return !priority&&actualOwner==owner&&!preparationVersionExhausted&&preparationVersion==stopDispatchVersion
                        &&originalNative!=null&&originalNative.current()&&nativeHeaders!=null&&nativeHeaders.current()&&settings!=null&&settings.current()&&modules.lifecycleCurrent(lifecycle)
                        &&bindings.get(owner)==binding&&cooldowns.get(owner)==deadline&&!rest.active()
                        &&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==originalConnection&&client.getCurrentServer()==originalServer
                        &&originalPlayer!=null&&originalPlayer.isAlive()&&originalPlayer.onGround()&&client.gui.screen()==null
                        &&originalPlayer.containerMenu==originalPlayer.inventoryMenu&&originalPlayer.inventoryMenu.getCarried().isEmpty();
                };
                miningRestPausedAdmissionFinal=old->{
                    // Ownership is the actual token consumed by the normal pause.
                    // No current owner scan or fresh lifecycle baseline may adopt it.
                    boolean priority=higherPriorityOwned.getAsBoolean();
                    boolean owns=old!=null&&old.owner()==owner&&old.binding()==binding&&old.binding().pause().owns(old.token());
                    return !priority&&owns&&!preparationVersionExhausted&&preparationVersion==stopDispatchVersion
                        &&originalNative!=null&&originalNative.current()&&nativeHeaders!=null&&nativeHeaders.current()&&settings!=null&&settings.current()
                        &&owner instanceof dev.veltraph.client.core.AbstractModule actual&&modules.lifecyclePausedCurrent(lifecycle,actual)
                        &&bindings.get(owner)==binding&&cooldowns.get(owner)==deadline&&captured==old&&(!rest.active()||miningStartReturned==old&&rest.state()==SourceRestRuntime.State.WAITING_TO_DISCONNECT)
                        &&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==originalConnection&&client.getCurrentServer()==originalServer;
                };
                miningOriginalCleanupCurrent=old->old!=null&&old.owner()==owner&&old.binding()==binding&&owner instanceof dev.veltraph.client.core.AbstractModule actual
                        &&modules.originalEnableLifetimeCurrent(lifecycle,actual);
                boolean ready=restReadiness(owner).isEmpty();
                boolean stillAdmitted=validateMiningRestAdmission();
                if(ready&&stillAdmitted){
                    miningRestBeginAllowed=true;try{beginRest(owner);}finally{miningRestBeginAllowed=false;}
                }
            }finally{miningRestAdmissionFinal=null;miningRestPausedAdmissionFinal=null;miningOriginalCleanupCurrent=null;miningRestBeginAllowed=false;preparingStopDispatch=false;}
        }else if(deadline!=null&&services.clock().nowMillis()>deadline&&restReadiness(owner).isEmpty())beginRest(owner);
    }
    private boolean validateMiningRestAdmission(){
        if(!preparingStopDispatch)return true;
        boolean current=miningRestAdmissionFinal!=null&&miningRestAdmissionFinal.getAsBoolean();
        if(!current)preparationRenewed=false;
        return current;
    }
    public boolean beginRest(Module owner){
        thread();
        if(preparingStopDispatch){
            if(!miningRestBeginAllowed||miningRestAdmissionFinal==null)return false;
            miningRestBeginAllowed=false; // Consume the actual Root call before any callback-bearing validation.
            if(!validateMiningRestAdmission())return false;
        }
        var deadline=cooldowns.get(owner);
        boolean due=deadline!=null&&services.clock().nowMillis()>deadline;
        if(!validateMiningRestAdmission())return false;
        if(!due){if(preparingStopDispatch)preparationRenewed=false;return false;}
        boolean unavailable=restReadiness(owner).isPresent();
        if(!validateMiningRestAdmission()||unavailable)return false;
        var binding=bindings.get(owner);var p=client.player;
        if(preparingStopDispatch)return beginMiningRest(owner,binding,p);
        if(!transport.capture(()->cancel("Rest screen manually cancelled"),this::screenStatus))return false;
        var token=new Ticket();var gear=new EnumMap<EquipmentSlot,NativeStackIdentity.Snapshot>(EquipmentSlot.class);
        var originalRegistry=client.level.registryAccess();GEAR.forEach(slot->gear.put(slot,NativeStackIdentity.capture(p.getItemBySlot(slot),originalRegistry)));
        captured=new Capture(owner,binding,token,binding.skill(),binding.profile().get(),binding.settings().get(),scheduleSignature(),p.getUUID(),p,client.level,client.getConnection(),new FarmReturnPoint(client.level.dimension().identifier().toString(),context.current().area(),position(),p.getYRot(),p.getXRot()),p.getPose(),p.getInventory().getSelectedSlot(),NativeStackIdentity.capture(p.getMainHandItem(),originalRegistry),Map.copyOf(gear),keys());
        if(!binding.pause().pause(token)){captured=null;return false;}
        try{
            long now=services.clock().nowMillis();long duration=SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_BREAK)).restMillis(rangeRandom);
            rest.start(now,duration,delayRandom.getAsInt(),config.get(SchedulingConfig.REST_REJOIN_TIMEOUT_MS));
            cooldowns.put(owner,Math.addExact(now,SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN)).cooldownMillis(rangeRandom)));
            services.rotation().cancelRotation();release();arrivalStarted=arrivalReady=rebound=false;committing=null;joinedPlayer=joinedWorld=joinedConnection=null;postureSettled=-1;return true;
        }catch(RuntimeException failure){cancel("Invalid scheduled rest: "+failure.getMessage());return false;}
    }
    private record RestStartArguments(long now,long duration,int delay,long timeout,long nextCooldown){
        private RestStartArguments{
            if(duration<60_000||delay<20||delay>=120||timeout<=0)throw new IllegalArgumentException("Invalid source rest schedule");
            Math.addExact(now,duration);
        }
    }
    /** Same immutable original admission throughout acquisition, normal pause and primitive start. */
    private boolean beginMiningRest(Module owner,Binding binding,net.minecraft.client.player.LocalPlayer player){
        if(miningCaptureInFlight!=null||miningRevoking||miningRevocationFailure!=null)return false;
        NativeRestTransport.IssuedFarmSnapshot ownedTransport=null;Capture old=null;
        try{
            if(!transport.capture(()->cancel("Rest screen manually cancelled"),this::screenStatus))return false;
            ownedTransport=transport.issuedFarmSnapshot();
            var token=new Ticket();var gear=new EnumMap<EquipmentSlot,NativeStackIdentity.Snapshot>(EquipmentSlot.class);
            var originalRegistry=client.level.registryAccess();GEAR.forEach(slot->gear.put(slot,NativeStackIdentity.capture(player.getItemBySlot(slot),originalRegistry)));
            var candidate=new Capture(owner,binding,token,binding.skill(),binding.profile().get(),binding.settings().get(),scheduleSignature(),player.getUUID(),player,client.level,client.getConnection(),new FarmReturnPoint(client.level.dimension().identifier().toString(),context.current().area(),position(),player.getYRot(),player.getXRot()),player.getPose(),player.getInventory().getSelectedSlot(),NativeStackIdentity.capture(player.getMainHandItem(),originalRegistry),Map.copyOf(gear),keys());
            // All providers and arithmetic precede the effect. Samples are never
            // redrawn after a refusal or used to rebase the original admission.
            long now=services.clock().nowMillis();long duration=SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_BREAK)).restMillis(rangeRandom);
            int delay=delayRandom.getAsInt();long timeout=config.get(SchedulingConfig.REST_REJOIN_TIMEOUT_MS);
            long next=Math.addExact(now,SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN)).cooldownMillis(rangeRandom));
            var start=new RestStartArguments(now,duration,delay,timeout,next);
            if(!validateMiningRestAdmission()||!ownedTransport.current()){ownedTransport.discardUnissuedCapture();return false;}
            old=candidate;miningCaptureInFlight=old;miningTransportInFlight=ownedTransport;captured=old;
            boolean paused;
            miningPauseInProgress=true;try{paused=binding.pause().pause(token);}finally{miningPauseInProgress=false;}
            if(!paused){revokeMiningCapture(old,ownedTransport,null);return false;}
            // The actual mining pause already performs its ticket-owned stop.
            // A late visitor may install a newer actor; no global release follows.
            // pause itself visits native tool codecs and changes only its actual
            // stored PAUSED state. Its original policy/history/native proof follows
            // every such visitor immediately before the irreversible continuation.
            boolean pausedCurrent=miningRestPausedAdmissionFinal!=null&&miningRestPausedAdmissionFinal.test(old);
            if(!pausedCurrent||!ownedTransport.current()){preparationRenewed=false;revokeMiningCapture(old,ownedTransport,null);return false;}
            miningStartInProgress=true;try{rest.start(start.now(),start.duration(),start.delay(),start.timeout());}finally{miningStartInProgress=false;}
            if(miningCaptureInFlight==old&&captured==old)miningStartReturned=old;
            if(miningRestPausedAdmissionFinal==null||!miningRestPausedAdmissionFinal.test(old)||!ownedTransport.current()){preparationRenewed=false;revokeMiningCapture(old,ownedTransport,null);return false;}
            cooldowns.put(owner,start.nextCooldown());
            miningCaptureInFlight=null;miningTransportInFlight=null;miningStartReturned=null;
            arrivalStarted=arrivalReady=rebound=false;committing=null;joinedPlayer=joinedWorld=joinedConnection=null;postureSettled=-1;return true;
        }catch(RuntimeException|Error primary){
            miningCommitFailure=primary;
            if(old!=null)revokeMiningCapture(old,ownedTransport,primary);
            else if(ownedTransport!=null)try{ownedTransport.discardUnissuedCapture();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}
            throw primary;
        }
    }
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)try{primary.addSuppressed(cleanup);}catch(Throwable ignored){/* Preserve original error identity. */}}
    private void revokeMiningCapture(Capture old,NativeRestTransport.IssuedFarmSnapshot ownedTransport,Throwable primary){
        if(miningRevoking)return;
        if(miningRevocationFailure!=null){
            if(primary!=null){suppress(primary,miningRevocationFailure);return;}
            if(miningRevocationFailure instanceof Error error)throw error;throw (RuntimeException)miningRevocationFailure;
        }
        miningRevoking=true;miningCaptureInFlight=null;miningTransportInFlight=null;miningStartReturned=null;preparationRenewed=false;
        Throwable failed=null;
        try{
            if(captured==old){rest.cancel("Original mining rest admission was revoked");if(committing==old)committing=null;}
            // This exact token can stop only its original pause. Never fall back
            // to owner id or disable a newer activation after token custody loss.
            try{if(miningOriginalCleanupCurrent!=null&&miningOriginalCleanupCurrent.test(old))old.binding().pause().stop(old.token());}catch(RuntimeException|Error failure){failed=failure;}
            try{if(ownedTransport!=null)ownedTransport.discardUnissuedCapture();}catch(RuntimeException|Error failure){if(failed==null)failed=failure;else suppress(failed,failure);}
            if(failed==null){if(captured==old)captured=null;}
            else {miningRevocationFailure=failed;if(primary!=null)suppress(primary,failed);}
        }finally{miningRevoking=false;}
        if(primary==null&&failed!=null){if(failed instanceof Error error)throw error;throw (RuntimeException)failed;}
    }
    private String scheduleSignature(){return config.get(MiscConfig.DYNAMIC_REST_ENABLED)+"|"+config.get(SchedulingConfig.AUTOBREAK_COOLDOWN)+"|"+config.get(SchedulingConfig.AUTOBREAK_BREAK)+"|"+config.get(SchedulingConfig.REST_DURING_REWARP_ONLY)+"|"+config.get(SchedulingConfig.REST_REJOIN_TIMEOUT_MS)+"|"+config.get(SchedulingConfig.REST_MIN_CONTEST_REMAINING_SECONDS)+"|"+config.get(SchedulingConfig.REST_RECENT_REWARP_GUARD_MS);}
    private boolean captureMetadataValid(){return captured!=null&&captured.owner().isEnabled()&&captured.skill()==captured.binding().skill()&&captured.skill().enabled(config)
            &&Objects.equals(captured.profile(),captured.binding().profile().get())&&Objects.equals(captured.settings(),captured.binding().settings().get())&&Objects.equals(captured.scheduleSettings(),scheduleSignature())
            &&transport.originalTargetUnchanged()&&!higherPriorityOwned.getAsBoolean()&&modules.enabledControllers().stream().allMatch(owner->owner==captured.owner()||owner==inputDriver);}
    private boolean metadataValid(){return captureMetadataValid()&&captured.binding().pause().owns(captured.token());}
    private boolean ticketValid(){
        if(!metadataValid()||!rest.withinDeadline(services.clock().nowMillis()))return false;
        if(rest.state()==SourceRestRuntime.State.WAITING_TO_DISCONNECT)return client.player==captured.player()&&client.level==captured.world()&&client.getConnection()==captured.connection()&&client.player.isAlive()&&client.gui.screen()==null;
        if(rest.state()==SourceRestRuntime.State.DISCONNECTED)return client.player==null&&client.level==null&&client.getConnection()==null&&transport.restingScreenOwned();
        if(!transport.connectionScreenAllowed(arrivalStarted))return false;
        if((client.getCurrentServer()!=null||client.getConnection()!=null)&&!transport.matchingServer())return false;
        if(client.player!=null&&(!client.player.isAlive()||!client.player.getUUID().equals(captured.playerId())))return false;
        if(joinedWorld!=null)return client.player==joinedPlayer&&client.level==joinedWorld&&client.getConnection()==joinedConnection;
        return true;
    }
    private boolean matchingArrival(){return client.player!=null&&client.level!=null&&client.getConnection()!=null&&transport.matchingServer()
            &&client.player!=captured.player()&&client.level!=captured.world()&&client.getConnection()!=captured.connection()&&client.player.getUUID().equals(captured.playerId())&&client.player.isAlive();}
    private void advanceRest(){
        boolean valid=ticketValid(),disconnected=client.player==null||client.level==null;boolean arrival=valid&&matchingArrival();boolean ready=false;
        if(arrival&&rest.state()==SourceRestRuntime.State.VERIFYING_ARRIVAL)ready=arrivalProof();
        var action=rest.tick(new SourceRestRuntime.Observation(valid,disconnected,arrival,ready,services.clock().nowMillis()));
        switch(action){
            case DISCONNECT->{if(!transport.disconnect())cancel("Native disconnect refused");}
            case REJOIN->{if(!transport.rejoin())cancel("Native rejoin refused");}
            case VERIFY_ARRIVAL->{joinedPlayer=client.player;joinedWorld=client.level;joinedConnection=client.getConnection();}
            case RESTART->completeRest();
            case CANCEL->cancel(rest.status());
            case NONE->{}
        }
    }
    private boolean protectedRestored(){return captured!=null&&client.player!=null&&client.level!=null
            &&captured.tool().matches(client.player.getInventory().getItem(captured.slot()),client.level.registryAccess())
            &&GEAR.stream().allMatch(slot->captured.gear().get(slot).matches(client.player.getItemBySlot(slot),client.level.registryAccess()));}
    private boolean arrivalProof(){
        arrivalReady=false;
        if(!known()){if(arrivalStarted)cancel("Known arrival area was lost during saved return");return false;}
        if(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return false;
        if(!protectedRestored()){if(arrivalStarted)cancel("Protected tool/gear changed during saved return");return false;}
        if(!arrivalStarted){services.inventory().selectSlot(captured.slot());if(!captured.binding().arrival().begin(captured.pose(),context.current().area())){cancel("No authorized arrival recovery for saved area/pose");return false;}arrivalStarted=true;inputDriver.setEnabled(true);}
        var result=captured.binding().arrival().tick(context.current().area());services.input().setSneakHeld(captured.posture()==Pose.CROUCHING);
        if(result==ArrivalStatus.FAILED){cancel("Saved pose return failed after rejoin");return false;}
        if(result!=ArrivalStatus.READY||!captured.binding().arrival().arrived(context.current().area())||client.player.getPose()!=captured.posture()||client.player.getInventory().getSelectedSlot()!=captured.slot()){postureSettled=-1;return false;}
        arrivalReady=true;
        long now=services.clock().nowMillis();if(postureSettled<0)postureSettled=now;
        if(now-postureSettled<250||!hasCapturedArrival(captured.owner(),captured.token())||captured.binding().readiness().get().isPresent())return false;
        if(!rebound){
            if(!captured.binding().pause().rebindAfterOwnedArrival(captured.token())){cancel("Owner could not rebind fresh native world");return false;}rebound=true;
            if(!hasCapturedArrival(captured.owner(),captured.token())||!captured.binding().arrival().arrived(context.current().area())){
                cancel("Native-ref rebinding changed saved pose/loadout proof");return false;
            }
        }
        return true;
    }
    private void completeRest(){
        if(captured==null||committing!=null||!rest.restartPending()||!metadataValid()||!matchingArrival()||!arrivalProof()){cancel("Saved owner readiness changed before resume");return;}
        var old=captured;committing=old;
        try{
            internalStop=true;try{inputDriver.setEnabled(false);}finally{internalStop=false;}
            var handoff=new ScheduledResumeHandoff();
            if(!handoff.complete(()->hasCapturedArrival(old.owner(),old.token())&&old.binding().pause().resume(old.token())&&resumedStateValid(old),
                    ()->{if(!resumedStateValid(old))throw new IllegalStateException("Arrival changed before movement restore");old.keys().restore(client);},
                    ()->ScheduledMovementKeys.clearInteractions(client),()->cancel("Exact scheduler resume failed; no captured inputs replayed")))return;
            long now=services.clock().nowMillis();
            if(!rest.commitRestart(new SourceRestRuntime.Observation(resumedStateValid(old),false,matchingArrival(),true,now))){cancel("Exact scheduler resume commit failed");return;}
        }finally{committing=null;}
        completedRests++;cooldowns.put(old.owner(),Math.addExact(services.clock().nowMillis(),SourceScheduleRange.parse(config.get(SchedulingConfig.AUTOBREAK_COOLDOWN)).cooldownMillis(rangeRandom)));
        if(timerPin!=null&&timerPin.owner()==old.owner()&&timer.state()==SourceStopTimer.State.ARMED)
            timerPin=new TimerPin(old.owner(),timerPin.binding(),timerPin.profile(),timerPin.settings(),client.player,client.level,client.getConnection());
        transport.complete();
        captured=null;joinedPlayer=joinedWorld=joinedConnection=null;arrivalStarted=arrivalReady=rebound=false;
    }
    private boolean resumedStateValid(Capture old){
        return captured==old&&hasCapturedArrival(old.owner(),old.token())&&old.owner().isEnabled()&&client.player==joinedPlayer&&client.level==joinedWorld&&client.getConnection()==joinedConnection
                &&client.player.isAlive()&&known()&&context.current().area()==old.pose().area()&&client.gui.screen()==null
                &&Objects.equals(old.profile(),old.binding().profile().get())&&Objects.equals(old.settings(),old.binding().settings().get())
                &&Objects.equals(old.scheduleSettings(),scheduleSignature())&&protectedRestored()
                &&!higherPriorityOwned.getAsBoolean()&&modules.enabledControllers().stream().allMatch(owner->owner==old.owner())
                &&client.player.getInventory().getSelectedSlot()==old.slot()&&client.player.getPose()==old.posture()
                &&old.binding().arrival().arrived(context.current().area())&&old.binding().readiness().get().isEmpty();
    }
    public void cancel(String reason){
        thread();clearRestPreparation();
        if(miningRevoking)return;
        if(miningRevocationFailure!=null){if(miningRevocationFailure instanceof Error error)throw error;throw (RuntimeException)miningRevocationFailure;}
        if(miningCaptureInFlight!=null&&(miningPauseInProgress||miningStartInProgress)){preparationRenewed=false;return;} // Original pause closes before its exact token is revoked.
        if(miningCaptureInFlight!=null){revokeMiningCapture(miningCaptureInFlight,miningTransportInFlight,null);return;}
        var old=captured;boolean restOwned=old!=null||rest.active();captured=null;committing=null;arrivalReady=false;timer.cancel();timerPin=null;clearGhostTimerContinuation();clearIssuedFarmTimer();
        if(!restOwned)return; // A stop timer owns expiry authority, never another workflow's inputs.
        rest.cancel(reason);
        try{transport.cancelConnect();}catch(RuntimeException ignored){/* Owner/input stop remains mandatory. */}
        internalStop=true;
        try{
            inputDriver.setEnabled(false);
            if(old!=null){
                try{old.binding().arrival().cancel();}catch(RuntimeException ignored){}
                try{old.binding().pause().stop(old.token());}catch(RuntimeException ignored){}
                // A port may already have consumed its token before a later handoff failure.
                if(old.owner().isEnabled())try{modules.disable(old.owner().id());}catch(RuntimeException ignored){}
            }
        }finally{internalStop=false;release();}
    }
    public void beforeManualStop(Module owner){thread();if(!internalStop&&(captured!=null&&(captured.owner()==owner||owner==inputDriver)||timerPin!=null&&timerPin.owner()==owner))cancel("Captured owner manually stopped");}
    public void beforeActivation(Module owner){thread();if(rest.active())cancel("Manual/controller activation superseded scheduled rest");}
    /**
     * Read-only exact captured arrival authority for family port factories. Does not call family
     * readiness, rebind, resume, or recovery callbacks. Valid during the synchronous resume commit,
     * including after the port consumes its pause token, and revoked as soon as capture is cleared.
     */
    public boolean hasCapturedArrival(Module owner){thread();return captured!=null&&hasCapturedArrival(owner,captured.token());}
    /** Token-aware form for ports whose callback receives the scheduler's opaque pause token. */
    public boolean hasCapturedArrival(Module owner,Object ticket){
        thread();var old=captured;
        if(old==null||owner!=old.owner()||ticket!=old.token()||!captureMetadataValid()
                ||rest.state()!=SourceRestRuntime.State.VERIFYING_ARRIVAL||!rest.withinDeadline(services.clock().nowMillis())
                ||!(committing==old&&rest.restartPending()||old.binding().pause().owns(old.token()))
                ||client.player==null||client.level==null||client.getConnection()==null||joinedPlayer==null||joinedWorld==null||joinedConnection==null)return false;
        var p=client.player;var area=context.current().area();var target=old.pose();
        var saved=new ScheduledArrivalProof.Saved(old.owner(),old.token(),new ScheduledArrivalProof.Metadata(old.profile(),old.settings(),old.scheduleSettings()),
                new ScheduledArrivalProof.NativeContext(old.player(),old.world(),old.connection(),old.playerId(),target.dimension(),target.area()),
                new ScheduledArrivalProof.Pose(target.position(),target.yaw(),target.pitch(),old.posture(),old.slot()));
        var current=new ScheduledArrivalProof.NativeContext(p,client.level,client.getConnection(),p.getUUID(),client.level.dimension().identifier().toString(),area);
        var joined=new ScheduledArrivalProof.NativeContext(joinedPlayer,joinedWorld,joinedConnection,p.getUUID(),current.dimension(),area);
        var feet=p.blockPosition();var destination=target.position().toBlockPos();
        boolean loaded=client.level.getChunkSource().hasChunk(feet.getX()>>4,feet.getZ()>>4)
                &&client.level.getChunkSource().hasChunk(destination.x()>>4,destination.z()>>4);
        long now=services.clock().nowMillis();
        var observation=new ScheduledArrivalProof.Observation(new ScheduledArrivalProof.Metadata(old.binding().profile().get(),old.binding().settings().get(),scheduleSignature()),
                current,new ScheduledArrivalProof.Pose(position(),p.getYRot(),p.getXRot(),p.getPose(),p.getInventory().getSelectedSlot()),true,old.owner().isEnabled(),
                transport.originalTargetUnchanged()&&transport.matchingServer(),p.isAlive()&&known(),
                client.gui.screen()==null&&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty(),protectedRestored(),loaded,
                p.onGround()&&p.getDeltaMovement().length()<.1,arrivalStarted&&arrivalReady,postureSettled>=0&&now-postureSettled>=250,
                !higherPriorityOwned.getAsBoolean()&&modules.enabledControllers().stream().allMatch(active->active==old.owner()||active==inputDriver));
        return captured==old&&ScheduledArrivalProof.permits(saved,joined,observation,owner,ticket);
    }
    /** Native safety/identity validation only, before parent guard/engine/helper updates. No dispatch. */
    public void validateBeforeUpdates(){
        thread();if(rest.active()&&(!ticketValid()||!InstalledRestLifecycle.permitsReturnUpdate(arrivalStarted,known(),protectedRestored(),
                client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty())))
            cancel("Scheduled ticket invalid before updates");
    }
    /** Exact pause token only; parent keeps normal world stops for every other owner and event. */
    public boolean ownsPaused(Module owner){thread();return captured!=null&&owner==captured.owner()&&rest.active()&&metadataValid()&&rest.withinDeadline(services.clock().nowMillis());}
    public boolean expectedWorldTransition(){thread();return rest.active()&&rest.state()!=SourceRestRuntime.State.WAITING_TO_DISCONNECT&&ticketValid();}
    public boolean awaitingArrival(){thread();return rest.state()==SourceRestRuntime.State.RECONNECTING||rest.state()==SourceRestRuntime.State.VERIFYING_ARRIVAL;}
    public boolean ownsInput(){thread();return rest.state()==SourceRestRuntime.State.VERIFYING_ARRIVAL&&arrivalStarted&&ticketValid();}
    public Module inputOwner(){thread();return ownsInput()&&inputDriver.isEnabled()?inputDriver:null;}
    /** Renderer identity only. Tick validation and ownsInput retain all gameplay/ticket authority. */
    public Module rotationInputOwner(){thread();return captured!=null&&rest.state()==SourceRestRuntime.State.VERIFYING_ARRIVAL
            &&arrivalStarted&&inputDriver.isEnabled()?inputDriver:null;}
    /** Cached joined native references and the exact rest epoch; no readiness or ticket scans. */
    public Object rotationAuthorityToken(){thread();return rotationInputOwner()!=null
            &&client.player==joinedPlayer&&client.level==joinedWorld&&client.getConnection()==joinedConnection
            ?captured.token():null;}
    public void onServerMessage(String message){thread();if(captured!=null&&ticketValid()&&arrivalStarted)captured.binding().arrival().onServerMessage(message);}
    public boolean consumeExpectedTeleport(Vec3 position,long now){thread();return captured!=null&&ticketValid()&&arrivalStarted&&captured.binding().arrival().consumeExpectedTeleport(position,now);}
    public int completedRests(){return completedRests;}public int timerStops(){return timerStops;}
    public int remainingTimerTicks(){return timer.remainingTicks();}
    public SourceRestRuntime.State state(){return rest.state();}public SourceStopTimer.State timerState(){return timer.state();}
    public int timerRemainingTicks(){return timer.remainingTicks();}
    public int nativeDisconnects(){return transport.disconnects();}public int nativeRejoins(){return transport.rejoins();}
    public String status(){return "TIMER="+timer.state()+" ticks="+timer.remainingTicks()+" | DYNAMIC_REST="+rest.status();}
    private String screenStatus(){long seconds=Math.max(0,rest.restEndsMillis()-services.clock().nowMillis())/1000;return rest.state()==SourceRestRuntime.State.DISCONNECTED?"Rest remaining "+String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60):"Connecting to the saved server…";}
    public static ArrivalRecovery sameAreaPoseReturn(Minecraft client,FarmServices services,ConfigService config){
        var recovery=new McSavedAnchorRecovery(client,services,config);
        return new ArrivalRecovery(){public boolean begin(FarmReturnPoint target,GameArea area){return recovery.start(target,area);}public ArrivalStatus tick(GameArea area){recovery.tick(area);return recovery.arrived(area)?ArrivalStatus.READY:recovery.active()?ArrivalStatus.ACTIVE:ArrivalStatus.FAILED;}public boolean arrived(GameArea area){return recovery.arrived(area);}public String diagnostics(){return recovery.diagnostics();}public void cancel(){recovery.cancel();}public void onServerMessage(String message){recovery.onServerMessage(message);}public boolean consumeExpectedTeleport(Vec3 position,long now){return recovery.consumeExpectedTeleport(position,now);}};
    }
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private ScheduledMovementKeys keys(){return ScheduledMovementKeys.capture(client);}
    private void release(){services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);McHeldKeys.setHeld(client.options.keyUse,false);}
}
