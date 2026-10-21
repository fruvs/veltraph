package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/** Local catalog -> nearest unclaimed -> normal travel/interact -> recovered message ACK -> return/save. */
public final class FairySoulModule extends GuardedSkillModule {
    public static final String ID="fairy_souls_local";
    private final Path directory;private final Supplier<GameArea> area;private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;
    private List<FairySoulCatalog.Soul> souls=List.of();private List<FairySoulCatalog.Descriptor> descriptors=List.of();private final Set<FairySoulCatalog.Soul> skipped=new HashSet<>();
    private FairySoulTraversal traversal;private McFairySoulTraversal routePort;private Object connection;private final Set<FairySoulCatalog.Claim> claimed=new HashSet<>();
    private FairySoulCatalog.Soul target;private ArmorStand marker;private Vec3 anchor;private String dimension;private Path file;
    private boolean walking,requested;private int collected;private GameArea startArea;
    private FairyReturnView returnView;private dev.veltraph.client.navigation.mc.McParentRotationService returnRotation;private float savedYaw,savedPitch;private int savedSlot;
    private Object cameraAuthority;private long cameraEpoch;private net.minecraft.world.item.ItemStack[] returnInventory;
    private boolean cameraInstalled,suppressForeignCameraRelease;private int returnSlot;private java.util.List<Object> returnTuning;
    public FairySoulModule(Minecraft client,ConfigService config,FarmServices services,Path directory){this(client,config,services,directory,()->new McGameContextService(client).current().area());}
    public FairySoulModule(Minecraft client,ConfigService config,FarmServices services,Path directory,Supplier<GameArea> area){super(ID,"Local Fairy Souls",client,config,services);this.directory=directory;this.area=area;navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,()->this);}
    @Override public Optional<GameArea> requiredArea(){return Optional.empty();}
    @Override public Optional<String> preStartCheck(){return invalid(()->{
        if(!config.get(SkillAutomationConfig.FAIRY_ENABLED))throw new IllegalArgumentException("Enable local fairy collection explicitly");
        label(config.get(SkillAutomationConfig.FAIRY_MARKER_NAME));var catalog=FairySoulCatalog.parse(config.get(SkillAutomationConfig.FAIRY_LOCAL_CATALOG));
        FairySoulCatalog.file(directory,config.get(SkillAutomationConfig.FAIRY_PROFILE));if(catalog.stream().noneMatch(s->s.area()==area.get()))throw new IllegalArgumentException("No local souls for current area");
        if(client.gui.screen()!=null||!client.player.inventoryMenu.getCarried().isEmpty())throw new IllegalArgumentException("Close menus and empty cursor before fairy travel");
    });}
    @Override public void onEnable(){begin();var error=preStartCheck();if(error.isPresent()){fail(error.get());return;}try{
        descriptors=FairySoulCatalog.descriptors(config.get(SkillAutomationConfig.FAIRY_LOCAL_CATALOG));souls=descriptors.stream().map(FairySoulCatalog.Descriptor::soul).toList();skipped.clear();traversal=null;routePort=null;connection=client.getConnection();file=FairySoulCatalog.file(directory,config.get(SkillAutomationConfig.FAIRY_PROFILE));claimed.clear();claimed.addAll(FairySoulCatalog.load(file));
        returnView=null;returnRotation=null;returnInventory=null;savedYaw=client.player.getYRot();savedPitch=client.player.getXRot();savedSlot=services.inventory().selectedSlot();
        dimension=client.level.dimension().identifier().toString();startArea=area.get();anchor=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());target=null;marker=null;walking=requested=false;collected=0;phase="SEARCHING";
    }catch(Exception e){fail("Cannot read local fairy claims: "+e.getMessage());}}
    @Override public void onDisable(){suppressForeignCameraRelease=returnView!=null&&!ownsReturnCamera();if(traversal!=null)traversal.cancel();if(returnView!=null)returnView.cancel();if(returnRotation!=null)returnRotation.cancelRotation();returnView=null;returnRotation=null;returnInventory=null;if(routePort!=null)routePort.stop();
        var cleanup=beginNativeNormalReturnCleanupInvocation();
        try{super.onDisable();}finally{if(cleanup!=null){cleanup.open=false;if(nativeNormalReturnCleanupInvocation==cleanup)nativeNormalReturnCleanupInvocation=null;}}
        marker=null;target=null;routePort=null;traversal=null;suppressForeignCameraRelease=false;}

    private NativeNormalReturnCleanupAdmission nativeNormalReturnCleanupAdmission;
    private NativeNormalReturnCleanupInvocation nativeNormalReturnCleanupInvocation;
    /** Actual retained normal Shared Fairy return, captured before its original selected-slot loan. */
    public final class NativeNormalReturnCleanupAdmission {
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;
        private final McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory;
        private final int originalSlot;
        private final OriginalSkillRuntimeSnapshot runtime;
        private final Object originalConnection=connection,originalAnchor=anchor,originalArea=startArea,originalDimension=dimension,originalFile=file;
        private final Object originalSouls=souls,originalDescriptors=descriptors,originalTarget=target,originalMarker=marker;
        private final Set<FairySoulCatalog.Claim> originalClaimed=Set.copyOf(claimed);private final Set<FairySoulCatalog.Soul> originalSkipped=Set.copyOf(skipped);
        private final float originalYaw=savedYaw,originalPitch=savedPitch;private final int originalSavedSlot=savedSlot;
        private boolean expectedWalking=walking;
        private final Object originalPlayer=client.player,originalWorld=client.level,originalInventory=client.player.getInventory(),originalOptions=client.options,originalGameMode=client.gameMode;
        private final java.util.List<net.minecraft.world.item.ItemStack> gear;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private NativeNormalReturnCleanupAdmission(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,
                McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation,OriginalSkillRuntimeSnapshot runtime,int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory){
            this.leg=leg;this.affiliation=affiliation;this.runtime=runtime;this.originalSlot=originalSlot;this.originalHistory=originalHistory;
            settings=config instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot()
                :config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
            gear=java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                .stream().map(slot->client.player.getItemBySlot(slot).copy()).toList();
        }
        /** Original session identities only; FAILED/COMPLETE/explicit routes do not enter this producer. */
        public boolean rawSessionCurrent(){return nativeNormalReturnCleanupAdmission==this&&client.isSameThread()
            &&runtime!=null&&runtime.rawCurrent()&&phase.equals("TRAVELLING_BACK")&&walking==expectedWalking
            &&traversal==null&&routePort==null&&returnView==null&&returnRotation==null&&returnInventory==null
            &&connection==originalConnection&&anchor==originalAnchor&&startArea==originalArea&&dimension==originalDimension&&file==originalFile
            &&souls==originalSouls&&descriptors==originalDescriptors&&target==originalTarget&&marker==originalMarker
            &&Float.floatToIntBits(savedYaw)==Float.floatToIntBits(originalYaw)&&Float.floatToIntBits(savedPitch)==Float.floatToIntBits(originalPitch)
            &&savedSlot==originalSavedSlot&&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==originalConnection
            &&client.player!=null&&client.level!=null&&client.player.getInventory()==originalInventory&&client.options==originalOptions&&client.gameMode==originalGameMode;}
        private boolean semanticSessionCurrent(){
            if(!rawSessionCurrent()||settings==null||!settings.current()||!originalClaimed.equals(claimed)||!originalSkipped.equals(skipped)||!rawSessionCurrent())return false;
            var slots=java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            for(int i=0;i<slots.size();i++)if(!net.minecraft.world.item.ItemStack.matches(gear.get(i),client.player.getItemBySlot(slots.get(i)))||!rawSessionCurrent())return false;
            return rawSessionCurrent();
        }
        public boolean initialCurrent(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return exact==leg&&isEnabled()
            &&semanticSessionCurrent()&&affiliation.matchesActualLeg(exact)&&affiliation.selectedSlotCurrent(originalSlot,originalHistory)&&rawSessionCurrent();}
        public boolean cleanupCurrent(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
            var call=nativeNormalReturnCleanupInvocation;
            return exact==leg&&call!=null&&call.original==this&&call.current()&&semanticSessionCurrent()
                &&affiliation.matchesActualLeg(exact)&&affiliation.cleanupSelectedSlotCurrent(originalSlot,originalHistory)&&call.current()
                &&rawSessionCurrent()&&affiliation.cleanupRawSelectedSlotCurrent(originalSlot,originalHistory);
        }
        public boolean cleanupRawCurrent(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){var call=nativeNormalReturnCleanupInvocation;
            return exact==leg&&call!=null&&call.original==this&&call.current()&&rawSessionCurrent()&&affiliation.matchesActualLeg(exact)&&affiliation.cleanupRawSelectedSlotCurrent(originalSlot,originalHistory);}
        private void observeOriginalWalkingStarted(){if(nativeNormalReturnCleanupAdmission==this&&!expectedWalking&&walking&&phase.equals("TRAVELLING_BACK"))expectedWalking=true;}
    }
    private final class NativeNormalReturnCleanupInvocation {
        private final NativeNormalReturnCleanupAdmission original;private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.core.ModuleManager.PendingOwnDisable pending;private final Thread thread=Thread.currentThread();private boolean open=true;
        private NativeNormalReturnCleanupInvocation(NativeNormalReturnCleanupAdmission original,dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.PendingOwnDisable pending){this.original=original;this.root=root;this.pending=pending;}
        private boolean current(){return open&&nativeNormalReturnCleanupInvocation==this&&Thread.currentThread()==thread&&original==nativeNormalReturnCleanupAdmission
            &&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&pending.registryIdentity()==root&&pending.current(FairySoulModule.this)
            &&root.pendingActualOwnDisable(FairySoulModule.this).orElse(null)==pending;}
    }
    private NativeNormalReturnCleanupInvocation beginNativeNormalReturnCleanupInvocation(){
        var original=nativeNormalReturnCleanupAdmission;var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        var pending=root==null?null:root.pendingActualOwnDisable(this).orElse(null);
        if(original==null||pending==null||nativeNormalReturnCleanupInvocation!=null||!original.rawSessionCurrent())return null;
        var call=new NativeNormalReturnCleanupInvocation(original,root,pending);nativeNormalReturnCleanupInvocation=call;return call.current()?call:null;
    }
    public NativeNormalReturnCleanupAdmission captureNativeNormalReturnCleanupAdmission(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact,
            int originalSlot,dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=FairySoulModule.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("selectNormalReturnDeviceSlotLoanOwned");});
        if(!caller||exact==null||originalSlot!=savedSlot||originalHistory==null||!isEnabled()||!phase.equals("TRAVELLING_BACK")||anchor==null||traversal!=null||routePort!=null
            ||returnView!=null||returnRotation!=null||returnInventory!=null||client.player==null||config!=dev.veltraph.client.VeltraphBootstrap.config())return null;
        var affiliation=mobility.captureNativeNormalReturnAffiliation(exact,this,config,anchor);
        if(affiliation==null||!affiliation.selectedSlotCurrent(originalSlot,originalHistory))return null;
        var runtime=originalFairyRuntimeSnapshot();if(runtime==null)return null;
        var actual=new NativeNormalReturnCleanupAdmission(exact,affiliation,runtime,originalSlot,originalHistory);nativeNormalReturnCleanupAdmission=actual;
        return actual.initialCurrent(exact)?actual:null;
    }
    @Override protected void release(){motionCleanup.release(mobility);}
    @Override public void onServerMessage(String message){
        if(!isEnabled()||!phase.equals("COLLECTING")||!requested||target==null||!context()||client.getConnection()!=connection||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()||area.get()!=startArea||marker==null||!marker.isAlive()||client.level.getEntity(marker.getId())!=marker)return;
        String line=message.replaceAll("§.","").strip();if(!line.equals("You found a Fairy Soul!")&&!line.equals("You have already found that Fairy Soul!"))return;
        try{if(traversal!=null&&!traversal.acknowledge())return;claimed.add(new FairySoulCatalog.Claim(dimension,target));FairySoulCatalog.save(file,claimed);collected++;target=null;marker=null;walking=requested=false;
            motionCleanup.release(mobility);
            phase=traversal!=null||config.get(SkillAutomationConfig.FAIRY_RETURN_TO_START)?"TRAVELLING_BACK":"SEARCHING";reason="Server acknowledged fairy soul; local claim saved";
        }catch(Exception e){fail("Failed to persist acknowledged fairy soul: "+e.getMessage());}
    }
    @Override public void onTick(){try{
        if(!context()||client.getConnection()!=connection||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()||area.get()!=startArea){fail("Fairy world/player/area/config changed or timed out");return;}
        if(routePort!=null&&!routePort.current()){fail("Fairy native route custody changed");return;}
        if(phase.equals("RESTORING_VIEW")){
            var state=returnView.tick();
            if(state==FairyReturnView.State.FAILED||state==FairyReturnView.State.CANCELLED){fail("Fairy return camera custody lost");return;}
            if(state!=FairyReturnView.State.COMPLETE)return;
            returnView=null;returnRotation=null;returnInventory=null;traversal=null;routePort=null;walking=false;phase="SEARCHING";return;
        }
        if(phase.equals("SEARCHING")){
            target=souls.stream().filter(s->s.area()==startArea&&!skipped.contains(s)&&!claimed.contains(new FairySoulCatalog.Claim(dimension,s))).min(Comparator.comparingDouble(s->new Vec3(s.position().x()+.5,s.position().y(),s.position().z()+.5).distanceTo(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ())))).orElse(null);
            if(target==null){complete("Current local area finished; acknowledged souls="+collected+" skipped="+skipped.size());return;}
            if(!services.world().isLoaded(target.position())){skip("Soul coordinate chunk unloaded");return;}
            var candidates=new ArrayList<ArmorStand>();for(var e:client.level.entitiesForRendering())if(e instanceof ArmorStand stand&&stand.isAlive()&&SkillMenuSession.clean(stand.getName().getString()).equals(config.get(SkillAutomationConfig.FAIRY_MARKER_NAME))&&Math.abs(stand.getX()-target.position().x())<=5&&Math.abs(stand.getY()-target.position().y())<=5&&Math.abs(stand.getZ()-target.position().z())<=5)candidates.add(stand);
            if(candidates.size()!=1){skip("Missing or ambiguous configured fairy marker within source five-block box");return;}marker=candidates.getFirst();walking=requested=false;
            var descriptor=descriptors.stream().filter(d->d.soul().equals(target)).findFirst().orElseThrow();traversal=null;routePort=null;
            if(descriptor.route()!=null){
                routePort=new McFairySoulTraversal(client,config,services,()->context()&&area.get()==startArea,()->traversal!=null&&traversal.phase()==FairySoulTraversal.Phase.RETURNING);
                if(routePort.position().distanceTo(FairySoulRouteCompiler.feet(descriptor.start()))>.35||!routePort.supported(descriptor.route())){skip("Explicit soul route start or loaded geometry unavailable");return;}
                traversal=new FairySoulTraversal(routePort,descriptor.route());
            }phase="TRAVELLING_TO";
        }
        if(phase.equals("TRAVELLING_TO")){
            if(marker==null||!marker.isAlive()||client.level.getEntity(marker.getId())!=marker){fail("Fairy marker changed during approach");return;}
            if(traversal!=null){
                traversal.tick(now());if(traversal.phase()==FairySoulTraversal.Phase.FAILED){fail(traversal.reason());return;}
                if(traversal.phase()!=FairySoulTraversal.Phase.COLLECTING)return;
                if(client.player.distanceTo(marker)>2.8||!client.player.hasLineOfSight(marker)){fail("Explicit Fairy route ended outside visible interaction reach");return;}
                phase="COLLECTING";
            }
            if(traversal==null&&(walking||client.player.distanceTo(marker)>2.8||!client.player.hasLineOfSight(marker))){
                if(!walking){motionCleanup.release(mobility);var p=marker.blockPosition();for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(mobility.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+d[0],p.getY(),p.getZ()+d[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){walking=true;break;}if(!walking){fail("No safe fairy approach");return;}}
                var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){fail("Fairy route blocked: "+travelDetails());return;}if(state==McTravelNavigator.State.ARRIVED)walking=false;return;
            }
            mobility.stop();phase="COLLECTING";
        }
        if(phase.equals("COLLECTING")){
            if(marker==null||!marker.isAlive()||client.level.getEntity(marker.getId())!=marker){fail("Fairy marker changed before acknowledgment");return;}
            var p=marker.getEyePosition();if(motionCleanup.beforePublication(1)){services.rotation().lookAt(new Vec3(p.x,p.y,p.z));motionCleanup.remember();}
            if(!requested&&delay()&&client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==marker&&client.player.distanceTo(marker)<=3&&client.player.hasLineOfSight(marker)){
                if(traversal!=null&&!traversal.interactionRequested()){fail("Fairy interaction custody lost");return;}client.gameMode.interact(client.player,marker,hit,InteractionHand.MAIN_HAND);requested=true;nextClick=now()+config.get(SkillAutomationConfig.SKILL_CLICK_DELAY_MS);reason="Awaiting exact server fairy acknowledgment";}
        }
        if(phase.equals("TRAVELLING_BACK")){
            if(traversal!=null){
                traversal.tick(now());if(traversal.phase()==FairySoulTraversal.Phase.FAILED){fail(traversal.reason());return;}
                if(traversal.phase()!=FairySoulTraversal.Phase.COMPLETE)return;
                beginViewReturn(routePort.savedYaw(),routePort.savedPitch(),routePort.originalSlotRestored());return;
            }
            if(!walking){motionCleanup.release(mobility);if(!mobility.start(anchor,.35,SharedMovementPolicy.Purpose.EXACT_RETURN)){fail("Cannot return to exact fairy start position: "+travelDetails());return;}walking=true;if(nativeNormalReturnCleanupAdmission!=null)nativeNormalReturnCleanupAdmission.observeOriginalWalkingStarted();}
            var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){fail("Fairy return blocked: "+travelDetails());return;}if(state==McTravelNavigator.State.ARRIVED){walking=false;beginViewReturn(savedYaw,savedPitch,services.inventory().selectedSlot()==savedSlot);}
        }
    }catch(RuntimeException e){fail("Fairy guard: "+e.getMessage());}}
    private void beginViewReturn(float yaw,float pitch,boolean slotRestored){
        if(!slotRestored){fail("Fairy original selected slot unavailable at return");return;}
        mobility.stop();returnSlot=services.inventory().selectedSlot();returnTuning=rotationTuning();
        dev.veltraph.client.service.mc.McOwnedRotation.validate(client);
        cameraInstalled=dev.veltraph.client.service.mc.McOwnedRotation.installed(client);
        cameraAuthority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
        cameraEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        if(cameraInstalled&&cameraAuthority!=this){fail("Fairy return camera authority unavailable");return;}
        var inventory=client.player.getInventory();returnInventory=new net.minecraft.world.item.ItemStack[inventory.getContainerSize()];
        for(int i=0;i<returnInventory.length;i++)returnInventory[i]=inventory.getItem(i).copy();
        returnRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,()->this);
        returnView=new FairyReturnView(returnRotation,this::returnCustody,this::ownsReturnCamera,yaw,pitch,dev.veltraph.client.rotation.SourceRotationStepper.mouseStep(client.options.sensitivity().get())+.001f,services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation?rotation::requestGeneration:null);
        phase="RESTORING_VIEW";reason="Physical return complete; awaiting applied saved view";
        if(!returnView.start())fail("Fairy return camera could not start");
    }
    private java.util.List<Object> rotationTuning(){return java.util.List.of(config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER),client.options.sensitivity().get());}
    private boolean ownsReturnCamera(){
        return client.player==player&&client.level==world&&client.getConnection()==connection
                &&cameraInstalled==dev.veltraph.client.service.mc.McOwnedRotation.installed(client)
                &&(!cameraInstalled||cameraAuthority==dev.veltraph.client.service.mc.McOwnedRotation.authority(client)
                &&(returnView==null||returnView.state()==FairyReturnView.State.IDLE
                    ?cameraEpoch==dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)
                    :returnRotation!=null&&returnRotation.currentRequest()));
    }
    private boolean returnCustody(){
        dev.veltraph.client.service.mc.McOwnedRotation.validate(client);
        if(!isEnabled()||!context()||!ownsReturnCamera()||area.get()!=startArea||client.gui.screen()!=null
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()
                ||services.inventory().selectedSlot()!=returnSlot||returnInventory==null||!returnTuning.equals(rotationTuning()))return false;
        var inventory=client.player.getInventory();if(inventory.getContainerSize()!=returnInventory.length)return false;
        for(int i=0;i<returnInventory.length;i++)if(!net.minecraft.world.item.ItemStack.matches(returnInventory[i],inventory.getItem(i)))return false;
        return routePort==null||routePort.current();
    }
    private void skip(String why){if(routePort!=null)routePort.stop();skipped.add(target);target=null;marker=null;traversal=null;routePort=null;phase="SEARCHING";reason=why;}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&context()&&(walking&&mobility.consumeExpectedTeleport(position,now)||routePort!=null&&routePort.consumeExpectedTeleport(position,now));}
    public int collected(){return collected;}
    public int skipped(){return skipped.size();}
    private String travelDetails(){return "navigation="+mobility.status()+" position="+(client.player==null?"unavailable":client.player.position())+" anchor="+anchor+" velocity="+(client.player==null?"unavailable":client.player.getDeltaMovement())+" yaw="+(client.player==null?"unavailable":client.player.getYRot())+" screen="+client.gui.screen();}
}
