package dev.veltraph.client.galatea.workflow;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.foraging.maintenance.*;
import dev.veltraph.client.galatea.GalateaForagingModule;
import dev.veltraph.client.galatea.trash.LushlilacTrashModule;
import dev.veltraph.client.galatea.GalateaPlanner;
import dev.veltraph.client.inventory.selling.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.mixin.AgathaChatAccessor;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.rewarp.FarmReturnPoint;
import dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;

/** Source command/material intermediary using the accepted maintenance transaction and same parent engine. */
public final class McGalateaWorkflowCoordinator implements ForagingMaintenanceCoordinator.Port {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final ModuleManager modules;private final GameContextService context;
    private final ForagingMaintenanceCoordinator coordinator=new ForagingMaintenanceCoordinator(this);
    private final McSavedAnchorRecovery returning;
    private final Set<GalateaForagingModule> parents=Collections.newSetFromMap(new IdentityHashMap<>());
    private Module helper;
    private ResetRequest request;
    private record ResetRequest(GalateaForagingModule parent,Object engine,Object player,Object world,Object connection,Object profile){}
    private SellingRule trashRule;
    private BooleanSupplier externalControlOwned=()->false;
    private record Protection(int slot,Map<Integer,NativeStackIdentity.Snapshot> inventory,Map<EquipmentSlot,NativeStackIdentity.Snapshot> gear,List<Boolean> keys,Vec3 origin,float yaw,float pitch,Pose posture){}
    private record SavedPose(FarmReturnPoint point,Pose posture){}
    private GalateaForagingModule candidate;private Object invitation;
    private boolean returnStarted;private long settledAt=-1;
    public McGalateaWorkflowCoordinator(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context){
        this.client=Objects.requireNonNull(client);this.services=Objects.requireNonNull(services);this.config=Objects.requireNonNull(config);
        this.modules=Objects.requireNonNull(modules);this.context=Objects.requireNonNull(context);returning=new McSavedAnchorRecovery(client,services,config);
    }
    /** Root must register only actual live instances, with an explicit parent/helper family match. */
    public void register(GalateaForagingModule parent,Module helper){
        Objects.requireNonNull(parent);Objects.requireNonNull(helper);
        if(this.helper!=null&&this.helper!=helper)throw new IllegalArgumentException("One exact workflow helper per coordinator");
        if(helper instanceof LushlilacTrashModule&&parent.kind()!=GalateaPlanner.Kind.MATERIALS
                ||helper instanceof GalateaSourceCommandModule c&&c.kind()==GalateaSourceTravelCycle.Kind.MURKWATER&&parent.kind()!=GalateaPlanner.Kind.MANGROVE
                ||!(helper instanceof LushlilacTrashModule||helper instanceof GalateaSourceCommandModule))throw new IllegalArgumentException("Explicit source workflow family mismatch");
        if(!parents.add(parent))throw new IllegalArgumentException("Parent already registered");this.helper=helper;
    }
    /** Reset entry metadata/automatic trigger is opaque: Root may request a single bounded idle reset. */
    public boolean requestReset(){
        thread();if(coordinator.active()||request!=null||!(helper instanceof GalateaSourceCommandModule c)||c.kind()!=GalateaSourceTravelCycle.Kind.RESET||externalControlOwned.getAsBoolean()||!registered(helper)||helper.isEnabled()||!supported()||helperReadiness().isPresent())return false;
        var running=modules.enabledControllers();if(running.size()!=1||!(running.getFirst() instanceof GalateaForagingModule parent)||!parents.contains(parent)||!registered(parent)||parent.scheduledRestReadiness().isPresent())return false;
        request=new ResetRequest(parent,parent.scheduledEngineIdentity(),client.player,client.level,client.getConnection(),profile());return true;
    }
    private Optional<String> helperReadiness(){return helper instanceof GalateaSourceCommandModule c?c.activationReadiness():helper.preStartCheck();}
    private boolean completedHelper(){return helper instanceof LushlilacTrashModule t?t.stage()==LushlilacTrashModule.Stage.COMPLETE&&trashRule!=null&&t.discarded()==trashRule.quantity():helper instanceof GalateaSourceCommandModule c&&c.completed();}
    public void externalControlOwned(BooleanSupplier supplier){externalControlOwned=Objects.requireNonNull(supplier);}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Foraging maintenance requires client thread");}
    private boolean registered(Module module){return module!=null&&modules.get(module.id()).orElse(null)==module;}
    private boolean known(){var c=context.current();return c.onHypixel()&&c.inSkyBlock()&&c.area()==GameArea.GALATEA;}
    /** Current-source Murkwater dispatch is restricted to MOONGLADE_MARSH. The exact received
     * display line is explicit local metadata because the current source caption is encrypted. */
    private boolean moonglade(){
        if(!known())return false;String expected=text(GalateaWorkflowConfig.GALATEA_MARSH_REGION_LINE);
        if(expected.isEmpty())return false;var signals=context.signals();
        var lines=new ArrayList<String>(signals.sidebarRowsRaw());lines.addAll(signals.tabLinesRaw());
        signals.tabFooterRaw().lines().forEach(lines::add);
        return lines.stream().map(McGalateaWorkflowCoordinator::clean).map(String::strip).anyMatch(expected::equals);
    }
    private boolean nativeHealthy(){return known()&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.gameMode!=null&&client.player.isAlive();}
    private boolean closed(){return nativeHealthy()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean supported(){return closed()&&client.player.onGround()&&(client.player.getPose()==Pose.STANDING||client.player.getPose()==Pose.CROUCHING)
            &&client.player.getDeltaMovement().length()<.1&&new McNavigator(client,services.input(),config).standingClear(position());}
    private String text(ConfigKey<String> key){return config.get(key).strip();}
    private static String clean(String text){return text.replaceAll("§.","");}
    /** Read-only capture candidate probe, including complete codec support for selected tool and gear. */
    @Override public Optional<String> readiness(){
        thread();candidate=null;invitation=null;
        if(externalControlOwned.getAsBoolean())return Optional.of("Higher priority response owns gameplay");
        if(helper instanceof LushlilacTrashModule&&!config.get(GalateaWorkflowConfig.LUSHLILAC_AUTO_TRASH_HANDOFF))return Optional.of("Material trash handoff permission is disabled");
        if(helper instanceof GalateaSourceCommandModule c&&c.kind()==GalateaSourceTravelCycle.Kind.RESET&&request==null)return Optional.of("Explicit source reset request required");
        if(!registered(helper)||helper.isEnabled())return Optional.of("Exact workflow helper must be registered and stopped");
        var enabled=modules.enabledControllers();
        if(enabled.size()!=1||!(enabled.getFirst() instanceof GalateaForagingModule parent)||!parents.contains(parent)||!registered(parent))return Optional.of("One explicitly registered Galatea parent is required");
        if(!supported())return Optional.of("Healthy closed Galatea menu and supported stationary pose are required");
        if(helper instanceof GalateaSourceCommandModule c&&c.kind()==GalateaSourceTravelCycle.Kind.MURKWATER&&!moonglade())return Optional.of("Source Murkwater dispatch requires actual Marsh region evidence");
        if(keys().stream().anyMatch(Boolean::booleanValue))return Optional.of("Release ordinary keys before workflow handoff");
        if(request!=null&&(request.parent()!=parent||request.engine()!=parent.scheduledEngineIdentity()||request.player()!=client.player||request.world()!=client.level||request.connection()!=client.getConnection()||!request.profile().equals(profile()))){request=null;return Optional.of("Explicit reset request context changed");}
        var boundary=parent.scheduledRestReadiness();if(boundary.isPresent())return boundary;
        var readiness=helperReadiness();if(readiness.isPresent())return readiness;
        try{
            trashRule=null;
            if(helper instanceof LushlilacTrashModule){var observed=new SellingMenuAccess(client).read("");var rules=SellingRule.parse(text(LushlilacTrashConfig.LUSHLILAC_TRASH_RULES_JSON)).stream().filter(r->r.matches(observed.inventoryStack(r.inventoryIndex()).orElse(null))).toList();if(rules.size()!=1)return Optional.of("One exact trash stack is required");trashRule=rules.getFirst();}
            protection();
        }catch(RuntimeException unsupported){return Optional.of("Full native tool/gear identity cannot be captured");}
        candidate=parent;invitation=request==null?new Object():request;return Optional.empty();
    }
    @Override public Object invitation(){return invitation;}
    private Map<String,Object> profile(){
        var values=new TreeMap<String,Object>();
        try{for(var family:List.of(GalateaConfig.class,ForagingConfig.class,FigRecoveryConfig.class,GalateaWorkflowConfig.class,LushlilacTrashConfig.class,TravelConfig.class,RotationConfig.class))
            for(var field:family.getFields())if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),config.get(key));}
        catch(IllegalAccessException error){throw new IllegalStateException(error);}return Map.copyOf(values);
    }
    private List<Boolean> keys(){var o=client.options;return List.of(o.keyUp.isDown(),o.keyDown.isDown(),o.keyLeft.isDown(),o.keyRight.isDown(),o.keyJump.isDown(),o.keyShift.isDown(),o.keySprint.isDown(),o.keyAttack.isDown(),o.keyUse.isDown());}
    private Protection protection(){
        var p=client.player;var gear=new EnumMap<EquipmentSlot,NativeStackIdentity.Snapshot>(EquipmentSlot.class);
        for(var slot:EquipmentSlot.values())if(slot!=EquipmentSlot.MAINHAND)gear.put(slot,NativeStackIdentity.capture(p.getItemBySlot(slot),client.level.registryAccess()));
        var inventory=new TreeMap<Integer,NativeStackIdentity.Snapshot>();
        for(int i=0;i<36;i++)inventory.put(i,NativeStackIdentity.capture(p.getInventory().getItem(i),client.level.registryAccess()));
        return new Protection(p.getInventory().getSelectedSlot(),Map.copyOf(inventory),Map.copyOf(gear),keys(),position(),p.getYRot(),p.getXRot(),p.getPose());
    }
    @Override public ForagingMaintenanceCoordinator.Capture capture(){
        thread();if(candidate==null||invitation==null)return null;
        returnStarted=false;settledAt=-1;
        var point=helper instanceof GalateaSourceCommandModule command?command.destination():new FarmReturnPoint(client.level.dimension().identifier().toString(),GameArea.GALATEA,position(),client.player.getYRot(),client.player.getXRot());
        var pose=new SavedPose(point,client.player.getPose());
        return new ForagingMaintenanceCoordinator.Capture(candidate,helper,candidate.scheduledEngineIdentity(),client.player,client.level,client.getConnection(),profile(),protection(),pose,invitation);
    }
    @Override public boolean parentEnabled(){return coordinator.parent() instanceof GalateaForagingModule parent&&parent.isEnabled();}
    @Override public boolean helperEnabled(){return helper!=null&&helper.isEnabled();}
    private boolean protectedRestored(ForagingMaintenanceCoordinator.Capture capture){
        if(!nativeHealthy())return false;var saved=(Protection)capture.protection();var p=client.player;
        if(saved.slot()!=p.getInventory().getSelectedSlot())return false;
        for(var item:saved.inventory().entrySet()){
            var current=p.getInventory().getItem(item.getKey());
            // Only the captured exact main-inventory trash stack may become empty. Completion
            // still requires the accepted helper's item-entity, decrement and authoritative revision.
            if(helper instanceof LushlilacTrashModule&&trashRule!=null&&item.getKey()==trashRule.inventoryIndex()&&current.isEmpty())continue;
            if(!item.getValue().matches(current,client.level.registryAccess()))return false;
        }
        return saved.gear().entrySet().stream().allMatch(entry->entry.getValue().matches(p.getItemBySlot(entry.getKey()),client.level.registryAccess()));
    }
    @Override public boolean valid(ForagingMaintenanceCoordinator.Capture capture){
        thread();if(externalControlOwned.getAsBoolean()||!nativeHealthy()||client.player!=capture.player()||client.level!=capture.world()||client.getConnection()!=capture.connection()
                ||!registered((Module)capture.parent())||!registered((Module)capture.helper())||!parents.contains(capture.parent())||capture.helper()!=helper
                ||!((GalateaForagingModule)capture.parent()).isEnabled()||((GalateaForagingModule)capture.parent()).scheduledEngineIdentity()!=capture.engine()
                ||!profile().equals(capture.profile())||!protectedRestored(capture))return false;
        for(var controller:modules.enabledControllers())if(controller!=capture.parent()&&controller!=capture.helper())return false;
        return closed()||helper.allowsScreen(client.gui.screen()==null?"":client.gui.screen().getTitle().getString());
    }
    private static float angle(float current,float saved){return Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(current-saved));}
    private boolean atPose(ForagingMaintenanceCoordinator.Capture capture,double distance,float view){
        if(!supported())return false;var saved=(SavedPose)capture.pose();var point=saved.point();
        return position().distanceTo(point.position())<=distance&&client.player.getPose()==saved.posture()
                &&angle(client.player.getYRot(),point.yaw())<=view&&Math.abs(client.player.getXRot()-point.pitch())<=view;
    }
    @Override public boolean activationUnchanged(ForagingMaintenanceCoordinator.Capture capture){var saved=(Protection)capture.protection();return supported()&&position().distanceTo(saved.origin())<=.001&&client.player.getPose()==saved.posture()&&angle(client.player.getYRot(),saved.yaw())<=.001f&&Math.abs(client.player.getXRot()-saved.pitch())<=.001f&&keys().equals(saved.keys());}
    @Override public boolean pause(ForagingMaintenanceLease.Token token){return candidate!=null&&candidate.pauseForScheduledRest(token);}
    @Override public boolean ownsPause(ForagingMaintenanceLease.Token token){return coordinator.parent() instanceof GalateaForagingModule parent&&parent.scheduledRestOwnedBy(token);}
    @Override public boolean completed(ForagingMaintenanceCoordinator.Capture capture){return capture.helper()==helper&&completedHelper();}
    @Override public boolean cleanupFinished(ForagingMaintenanceCoordinator.Capture capture){return !helper.isEnabled()&&closed();}
    @Override public ForagingMaintenanceCoordinator.ReturnStatus returnToPose(ForagingMaintenanceCoordinator.Capture capture){
        if(!valid(capture)||!closed())return ForagingMaintenanceCoordinator.ReturnStatus.FAILED;
        if(!atPose(capture,.35,1.5f)){
            if(!returnStarted){returnStarted=true;if(!returning.start(((SavedPose)capture.pose()).point(),GameArea.GALATEA))return ForagingMaintenanceCoordinator.ReturnStatus.FAILED;}
            returning.tick(GameArea.GALATEA);
            services.input().setSneakHeld(((SavedPose)capture.pose()).posture()==Pose.CROUCHING);
            if(!returning.active()&&!returning.arrived(GameArea.GALATEA))return ForagingMaintenanceCoordinator.ReturnStatus.FAILED;
            settledAt=-1;return ForagingMaintenanceCoordinator.ReturnStatus.ACTIVE;
        }
        long now=services.clock().nowMillis();if(settledAt<0)settledAt=now;
        return now-settledAt>=250?ForagingMaintenanceCoordinator.ReturnStatus.READY:ForagingMaintenanceCoordinator.ReturnStatus.ACTIVE;
    }
    @Override public boolean resume(ForagingMaintenanceCoordinator.Capture capture,ForagingMaintenanceLease.Token token){
        if(!valid(capture)||!completed(capture)||!cleanupFinished(capture)||!atPose(capture,.35,1.5f))return false;
        var parent=(GalateaForagingModule)capture.parent();returning.cancel();
        // Gate rebind is required even in the same session; all original native references and
        // harvest engine are still pinned above, with no onEnable or fresh engine construction.
        if(!parent.rebindForScheduledRest(token)||parent.scheduledEngineIdentity()!=capture.engine()||!parent.resumeFromScheduledRest(token))return false;
        restoreKeys(((Protection)capture.protection()).keys());if(capture.invitation()==request)request=null;return true;
    }
    @Override public void rollback(ForagingMaintenanceCoordinator.Capture capture){
        try{if(helper.isEnabled())helper.setEnabled(false);}finally{
            if(capture!=null&&valid(capture)&&closed()&&activationUnchanged(capture))restoreKeys(((Protection)capture.protection()).keys());
            else if(capture!=null)stopBoth(capture);
        }
    }
    private void restoreKeys(List<Boolean> saved){var o=client.options;o.keyUp.setDown(saved.get(0));o.keyDown.setDown(saved.get(1));o.keyLeft.setDown(saved.get(2));o.keyRight.setDown(saved.get(3));o.keyJump.setDown(saved.get(4));McHeldKeys.setHeld(o.keyShift,saved.get(5));McHeldKeys.setHeld(o.keySprint,saved.get(6));McHeldKeys.setHeld(o.keyAttack,saved.get(7));McHeldKeys.setHeld(o.keyUse,saved.get(8));}
    @Override public void stopBoth(ForagingMaintenanceCoordinator.Capture capture){
        returning.cancel();request=null;try{if(((Module)capture.helper()).isEnabled())((Module)capture.helper()).setEnabled(false);}
        catch(RuntimeException ignored){/* The exact parent must still stop before foreign input. */}
        finally{try{if(((Module)capture.parent()).isEnabled())((Module)capture.parent()).setEnabled(false);}
            catch(RuntimeException ignored){/* Inputs are always released after both enabled flags stop. */}
            finally{services.breaking().setBreaking(false);McHeldKeys.setHeld(client.options.keyUse,false);services.input().releaseAllMovement();services.rotation().cancelRotation();}}
    }
    public Optional<String> startEligibility(){thread();return coordinator.readiness();}
    /** Native admission facts for diagnostics; no gameplay input or settings are changed. */
    public String admissionDiagnostics(){
        thread();var denied=startEligibility();var current=context.current();var signals=context.signals();
        var parent=modules.enabledControllers().stream().filter(parents::contains).findFirst().orElse(null);
        return "admission="+denied.orElse("eligible")+" context="+current+" sidebar="+signals.sidebarRowsRaw().stream().limit(8).toList()
                +" controllers="+modules.enabledControllers().stream().map(Module::id).toList()+" parentRest="+(parent instanceof GalateaForagingModule p?p.scheduledRestReadiness():"missing")
                +" helper="+(helper==null?"missing":helperReadiness())+" pose="+(client.player==null?"missing":position()+"/"+client.player.getPose()+" alive="+client.player.isAlive()+" ground="+client.player.onGround()+" velocity="+client.player.getDeltaMovement()+" supported="+supported());
    }
    /** Called by Root's activation transaction wrapper; ordinary non-helper starts retain all guards. */
    public boolean activate(Module module,BooleanSupplier standardActivation){
        thread();if(module!=helper)return standardActivation.getAsBoolean();
        if(coordinator.preparedActivation(module))return standardActivation.getAsBoolean();
        if(modules.enabledControllers().stream().noneMatch(parents::contains))return standardActivation.getAsBoolean();
        return coordinator.startTransaction(standardActivation,services.clock().nowMillis(),timeout());
    }
    private long timeout(){return (helper instanceof LushlilacTrashModule?Math.clamp(config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_TIMEOUT_TICKS),40,1200)*100:Math.clamp(config.get(GalateaWorkflowConfig.GALATEA_COMMAND_TIMEOUT_MS),1000,60000))+45000;}
    /** Run before enabled module ticks, and before foreign response input is allowed. */
    public void tick(BooleanSupplier guardedHelperActivation){
        thread();if(coordinator.active()){coordinator.tick(services.clock().nowMillis());return;}
        if(coordinator.readiness().isEmpty())coordinator.startTransaction(guardedHelperActivation,services.clock().nowMillis(),timeout());
    }
    public boolean permitsActivationConflict(Module running,Module starting){thread();return coordinator.permitsActivationConflict(running,starting);}
    public boolean permitsScreenForParent(Module module,String title){thread();return coordinator.owns(module)&&module==coordinator.parent()&&client.gui.screen()!=null&&Objects.equals(title,client.gui.screen().getTitle().getString())&&helper.allowsScreen(title);}
    public Module effectiveInputOwner(){thread();return coordinator.active()&&helper.isEnabled()?helper:null;}
    /** During cleanup/return Root must retain this exclusive control reservation despite helper self-stop. */
    public boolean ownsControl(){thread();return coordinator.active();}
    public String controlOwnerId(){thread();var owner=coordinator.validatedParent();return owner instanceof Module module?module.id():null;}
    public boolean consumeExpectedTeleport(Vec3 position,long now){thread();if(controlOwnerId()==null)return false;
        if(helper instanceof GalateaSourceCommandModule c&&coordinator.active()&&c.consumeExpectedTeleport(position,now))return true;
        return coordinator.state()==ForagingMaintenanceLease.State.RETURNING&&returning.consumeExpectedTeleport(position,now);}
    public boolean ownsParent(Module module){thread();return coordinator.active()&&coordinator.parent()==module;}
    public void beforeDisable(Module module){thread();if(request!=null&&request.parent()==module&&!coordinator.active())request=null;coordinator.beforeDisable(module);}
    public void cancelActive(String reason){thread();request=null;coordinator.cancelActive(reason);}
    public boolean active(){thread();return coordinator.active();}
    /** Exact stable transaction identity for frames; gameplay validation remains tick owned. */
    public Object rotationAuthorityToken(){thread();return coordinator.rotationAuthorityToken(
            client.player,client.level,client.getConnection(),helper!=null&&helper.isEnabled());}

    public ForagingMaintenanceLease.State state(){thread();return coordinator.state();}
    public String status(){return coordinator.status()+" | "+returning.status();}
}
