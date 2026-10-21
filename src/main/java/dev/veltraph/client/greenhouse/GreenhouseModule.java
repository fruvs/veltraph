package dev.veltraph.client.greenhouse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Native read-only diagnostics plus explicitly prepared ordinary block cultivation. */
public class GreenhouseModule extends AbstractModule {
    public static final String ID="greenhouse";
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final McNavigator navigator;private final McSharedMovementNavigator travel;private final McMenuAccess menus;private final GreenhouseRuntime runtime;private final GreenhouseMenuLedger capturedMenus=new GreenhouseMenuLedger();
    private McGreenhouseDiagnosticAdapter diagnosticAdapter;private boolean diagnosticMode;
    private final GreenhouseInspectionEstimates associatedCare=new GreenhouseInspectionEstimates();private Cell associatedCareCell;
    private GreenhouseBindings bindings;private GreenhouseRuntime.Plan plan;private Snapshot observed;
    private Object observedWorld,observedPlayer,observedConnection,ownerWorld,ownerPlayer,ownerConnection;
    private Snapshot archived;private MenuSnapshot lastAutomaticMenu;private long lastAutomaticAt;private Optional<GreenhouseCapturedMenus.Observation> lastAutomaticObservation=Optional.empty();private MenuSnapshot cachedNativeMenu,lastMenu;private long cachedMenuAt;private Object cachedWorld,cachedPlayer,cachedConnection;private java.util.function.BooleanSupplier persistence=()->false;private boolean persistenceBound;private long revision,observedAt,issuedAt;private int savedSlot=-1;
    private float savedYaw,savedPitch;private String configuredPlan="",configuredBindings="",diagnostic="No real plant diagnostics captured; geometry does not establish plant state";
    private String cachedReturnArrival="NOT_EVALUATED",cachedReturnRestore="NOT_EVALUATED";
    public String cachedOriginalReturnChecks(){return "arrival="+cachedReturnArrival+" | restore="+cachedReturnRestore;}
    private GreenhouseRuntime.Action nativeAction;private boolean inspectionRequested;private Object inspectionMenu;
    public GreenhouseModule(Minecraft client,ConfigService config,FarmServices services){this(ID,"Greenhouse",client,config,services);}
    protected GreenhouseModule(String moduleId,String name,Minecraft client,ConfigService config,FarmServices services){
        super(moduleId,name,ModuleCategory.INTERMEDIARY);this.client=client;this.config=config;this.services=services;environmentCensus=new McGreenhouseEnvironmentCensus(client);
        navigator=new McNavigator(client,services.input(),config);travel=new McSharedMovementNavigator(client,services,config);menus=new McMenuAccess(client);
        runtime=new GreenhouseRuntime(new GreenhouseRuntime.Port(){
            public boolean approach(Position p){return travel.start(vec(p),.35,runtime.state()==GreenhouseRuntime.State.RETURN?SharedMovementPolicy.Purpose.EXACT_RETURN:SharedMovementPolicy.Purpose.CROP_WORK);}
            public boolean arrived(Position p){
                var originalState=travel.tick();if(originalState!=McTravelNavigator.State.ARRIVED){cachedReturnArrival="travelState="+originalState+",player/distance=SKIPPED";return false;}
                if(client.player==null){cachedReturnArrival="travelState=ARRIVED,playerPresent=false,distance=SKIPPED";return false;}
                double originalDistance=position().distanceSquared(p);boolean accepted=originalDistance<.1225;
                cachedReturnArrival="travelState=ARRIVED,playerPresent=true,distanceSquared="+originalDistance+",accepted="+accepted;return accepted;
            }
            public boolean select(int slot,String identity){if(!nativeOwner()||!identity.equals(stackIdentity(client.player.getInventory().getItem(slot))))return false;return selectCultivationSlot(slot);}
            public boolean aim(GreenhouseRuntime.Action a){Cell target=interactionTarget(a);aimCultivationPoint(new dev.veltraph.client.world.Vec3(target.x()+.5,target.y()+(a.kind()==GreenhouseRuntime.Kind.REPLANT?.94:.5),target.z()+.5));return actionRay(a)!=null;}
            public boolean issue(GreenhouseRuntime.Action a,Snapshot expected){
                if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()&&!diagnosticAdapter.preparedBeforeIssue(a))return false;
                if(!nativeOwner()||!expected.equals(snapshot())||actionRay(a)==null||!worldState(a.cell()).equals(a.before().fingerprint()))return false;
                nativeAction=a;issuedAt=now();inspectionRequested=false;inspectionMenu=null;
                // Unknown transport outcomes are persisted before the packet-producing call.
                persistUnresolved();
                if(!persistence.getAsBoolean())return false;
                if(a.kind()==GreenhouseRuntime.Kind.HARVEST){setCultivationBreaking(true);return true;}
                client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,actionRay(a));return true;
            }
            public boolean restoreSelectionAndView(){
                if(!nativeOwner()){cachedReturnRestore="nativeOwner=false,remaining=SKIPPED";return false;}
                if(savedSlot<0){cachedReturnRestore="nativeOwner=true,savedSlot="+savedSlot+",remaining=SKIPPED";return false;}
                boolean selected=restoreOriginalCultivationSlot(savedSlot);if(!selected){cachedReturnRestore="nativeOwner=true,savedSlot="+savedSlot+",originalRestore=false,view=SKIPPED";return false;}aimCultivationView(savedYaw,savedPitch);
                float yawError=Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(client.player.getYRot()-savedYaw));
                int originalSlot=services.inventory().selectedSlot();if(originalSlot!=savedSlot){cachedReturnRestore="originalRestore=true,selected="+originalSlot+",saved="+savedSlot+",yawError="+yawError+",pitch=SKIPPED";return false;}
                if(!(yawError<.05f)){cachedReturnRestore="originalRestore=true,selectedMatches=true,yawError="+yawError+",pitch=SKIPPED";return false;}
                float pitchError=Math.abs(client.player.getXRot()-savedPitch);boolean accepted=pitchError<.05f;
                cachedReturnRestore="originalRestore=true,selectedMatches=true,yawError="+yawError+",pitchError="+pitchError+",accepted="+accepted;return accepted;
            }
            public void stopAction(){nativeAction=null;inspectionRequested=false;inspectionMenu=null;setCultivationBreaking(false);releaseCultivationUse();}
            public void release(){cleanup();}
        });
        capturedMenus.restore(config.get(GreenhouseConfig.GREENHOUSE_CAPTURED_MENUS));
        String savedObservation=config.get(GreenhouseConfig.GREENHOUSE_OBSERVATIONS);
        if(!savedObservation.isBlank())try{archived=new GsonBuilder().enableComplexMapKeySerialization().create().fromJson(savedObservation,Snapshot.class);}catch(RuntimeException ignored){archived=null;}
        String unknown=config.get(GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS);
        if(!unknown.isBlank())try{runtime.mergeSharedUnresolved(Arrays.asList(new Gson().fromJson(unknown,String[].class)),now(),config.revision(GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS).orElse(-1));}catch(RuntimeException invalid){diagnostic="Unresolved-action journal is invalid; Greenhouse disabled";runtime.restoreUnresolved(List.of("invalid-journal"));}
    }
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&runtime.active()&&travel.consumeExpectedTeleport(position,now);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    public void bindPersistence(java.util.function.BooleanSupplier flushAndConfirm){if(isEnabled())throw new IllegalStateException("Stop Greenhouse before binding persistence");persistence=Objects.requireNonNull(flushAndConfirm);persistenceBound=true;}
    public GreenhouseRuntime runtime(){return runtime;}
    private McGreenhouseDiagnosticAdapter diagnosticAdapter(){if(diagnosticAdapter==null)diagnosticAdapter=new McGreenhouseDiagnosticAdapter(client,this,config,services,()->persistence.getAsBoolean());return diagnosticAdapter;}
    /** Selection records one explicit native interaction or an observed legacy echo contract; it does not issue or prepare care. */
    public void selectDiagnostic(GreenhouseDiagnosticAssociation.Selection s){
        if(isEnabled())throw new IllegalStateException("Stop Greenhouse before selecting an inspection");
        diagnosticAdapter().select(s);bindings=s.bindings();config.set(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS,new Gson().toJson(bindings));observed=null;lastMenu=null;diagnosticMode=true;
    }
    public String diagnosticAssociationStatus(){return diagnosticAdapter==null?"No selected native diagnostic association":diagnosticAdapter.status();}
    public Optional<Plant> associatedPlant(Cell cell){return diagnosticAdapter==null?Optional.empty():diagnosticAdapter.fresh(cell,settings().freshnessMs());}
    private void acceptDiagnostic(GreenhouseDiagnosticAssociation.Associated a){
        observedWorld=client.level;observedPlayer=client.player;observedConnection=client.getConnection();observedAt=a.evidence().receivedAt();revision++;
        observed=new Snapshot(a.evidence().context(),observedAt,revision,Map.of(a.evidence().cell(),a.evidence().plant()),inventory(),"",-1,services.inventory().selectedSlot(),position(),false,client.player.isAlive(),true);
        String saved=new GsonBuilder().enableComplexMapKeySerialization().create().toJson(observed);
        config.set(GreenhouseConfig.GREENHOUSE_OBSERVATIONS,diagnosticAdapter().addProvenance(saved));
        if(!diagnosticAdapter().confirmSaved(a)){observed=null;associatedCare.invalidate("Association durability failed");associatedCareCell=null;diagnostic="UNKNOWN: diagnostic association durability failed";}else {
            a.diagnostic().ifPresentOrElse(d->{associatedCareCell=a.evidence().cell();var c=a.evidence().context();associatedCare.observe(d,new GreenhouseInspectionEstimates.Scope(c.profile(),c.plot(),c.world(),Optional.of(a.evidence().cell()),GreenhouseInspectionEstimates.ClockBasis.LOCAL_OBSERVATION_MILLIS));},()->{associatedCare.invalidate("This receipt has no captured full diagnostic clock fields");associatedCareCell=null;});
            diagnostic="Associated one exact submitted diagnostic request with selected cell; archived provenance is not live after restart";
        }
    }
    public boolean inspectVisibleMenu(){return ingest(false);}
    public Optional<GreenhouseCapturedMenus.Observation> inspectGuide(){var menu=readVisibleMenu(false);return captureAutomaticMenu(menu);}
    public GreenhouseMenuLedger capturedMenus(){return capturedMenus;}
    public GreenhouseInspectionEstimates.View associatedCareEstimates(){
        if(client.level==null||client.player==null||observed==null||observedWorld!=client.level||observedPlayer!=client.player||observedConnection!=client.getConnection()||!observed.context().equals(context())){
            associatedCare.invalidate("Native actor/world/connection or expected profile/plot was replaced");associatedCareCell=null;
        }
        if(associatedCareCell==null||diagnosticAdapter==null||diagnosticAdapter.fresh(associatedCareCell,settings().freshnessMs()).isEmpty()){
            var unavailable=new GreenhouseInspectionEstimates();unavailable.invalidate("No fresh same-P/W/C native associated plant; estimates require refreshed inspection");return unavailable.view(now(),settings().freshnessMs());
        }
        return associatedCare.view(now(),settings().freshnessMs());
    }
    private final McGreenhouseEnvironmentCensus environmentCensus;
    public GreenhouseEnvironmentCensus.Sample inspectEnvironment(GreenhouseEnvironmentCensus.Request request){return environmentCensus.capture(request,now());}
    public Optional<GreenhouseEnvironmentCensus.Sample> currentEnvironment(){return environmentCensus.fresh(now(),settings().freshnessMs());}
    public String currentLifecycleSummary(){return GreenhouseLifecyclePolicy.summary();}
    /** Bind advisory policy to the fresh native associated diagnostics; newly introduced fields stay UNKNOWN until captured. */
    public GreenhouseLifecyclePolicy.Advice currentLifecycleAdvice(){
        var view=associatedCareEstimates();long currentTime=now();
        if(view.state()!=GreenhouseInspectionEstimates.State.FRESH||view.observation().isEmpty()||view.scope().target().isEmpty())
            return GreenhouseLifecyclePolicy.evaluate(null,currentTime,settings().freshnessMs());
        var cell=view.scope().target().get();var plant=associatedPlant(cell);var diagnostic=view.observation().get();
        if(plant.isEmpty()||!plant.get().crop().equals(diagnostic.definition().name()))return GreenhouseLifecyclePolicy.evaluate(null,currentTime,settings().freshnessMs());
        OptionalLong decay=OptionalLong.empty();
        try{if(diagnostic.decay().isPresent())decay=OptionalLong.of(Math.multiplyExact(diagnostic.decay().get().seconds(),1000L));}catch(ArithmeticException unsupported){/* Unknown duration remains unknown. */}
        var scope=view.scope();var observation=new GreenhouseLifecyclePolicy.Observation(new Context(scope.profile(),scope.plot(),scope.dimension()),cell,
            plant.get().crop(),diagnostic.source().observedAtMillis(),diagnostic.source().provenance(),plant.get().state(),
            GreenhouseLifecyclePolicy.Observed.UNKNOWN,GreenhouseLifecyclePolicy.Observed.UNKNOWN,decay,Optional.empty());
        // 0.27.2 group/charge/drought grammar is absent from the historical native rows. Never borrow release minimums.
        return GreenhouseLifecyclePolicy.evaluate(observation,currentTime,settings().freshnessMs());
    }

    public String capturedCareSummary(){return currentLifecycleSummary()+" | dated menu observations: "+capturedMenus.careSummary(now(),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS))+" | associated inspection: "+associatedCareEstimates().summary();}
    public String capturedEffectsSummary(){
        var scoped=associatedCareEstimates();
        if(scoped.state()==GreenhouseInspectionEstimates.State.FRESH&&scoped.observation().isPresent())return GreenhouseEffectDisplay.summary(scoped.observation().get(),now(),settings().freshnessMs(),true);
        return GreenhouseEffectDisplay.summary(capturedMenus.diagnostic().orElse(null),now(),settings().freshnessMs(),false);
    }
    public GreenhouseCombinations.Validation validateCapturedRequirements(String mutation,Map<String,Integer> ingredients){return capturedMenus.validateCounts(mutation,ingredients);}

    public List<GreenhouseCareScheduler.Recommendation> care(){
        var scoped=snapshot();if(scoped==null&&capturedMenus.diagnostic().isPresent())return List.of(new GreenhouseCareScheduler.Recommendation(null,GreenhouseCareScheduler.Care.INSPECT,capturedCareSummary(),OptionalLong.empty()));
        if(associatedCareCell!=null){
            var view=associatedCareEstimates();
            return List.of(GreenhouseCareSchedule.associated(associatedCareCell,view,currentLifecycleAdvice(),now()));
        }
        return new GreenhouseCareScheduler().evaluate(scoped,now(),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS),OptionalLong.empty());
    }
    public Optional<GreenhouseRuntime.Plan> preparedPlan(){try{reload();return Optional.ofNullable(plan);}catch(RuntimeException invalid){return Optional.empty();}}
    private GreenhouseGuide.Recipe guide;
    public Optional<GreenhouseGuide.Recipe> inspectGuide(GreenhouseGuide.Binding binding){
        if(client.player==null||client.level==null)return Optional.empty();
        var m=readVisibleMenu(false);var result=GreenhouseGuide.read(m,binding,context(),cachedMenuAt);if(result.isPresent())guide=result.get();return result;
    }
    public GreenhouseCombinations.Validation validateCombination(GreenhouseCombinations.ObservedPattern pattern){
        Snapshot selected=snapshot();
        if(diagnosticAdapter!=null&&pattern!=null){var cells=new HashSet<Cell>(pattern.ingredientCells());cells.add(pattern.emptyTarget());var plants=diagnosticAdapter.freshCells(cells,settings().freshnessMs());
            selected=plants.isEmpty()?null:new Snapshot(context(),diagnosticAdapter.oldestReceipt(cells),revision,plants,inventory(),"",-1,services.inventory().selectedSlot(),position(),client.gui.screen()==null,client.player.isAlive(),true);}
        return capturedMenus.validateAssociatedPattern(guide,selected,pattern,now(),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS));
    }
    public void setNativeBindings(String json){if(isEnabled())throw new IllegalStateException("Stop Greenhouse first");diagnosticMode=false;if(diagnosticAdapter!=null){diagnosticAdapter.cancel();diagnosticAdapter=null;}GreenhouseBindings.parse(json);config.set(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS,json);observed=null;lastMenu=null;}
    public void setContext(String profile,String plot){if(isEnabled())throw new IllegalStateException("Stop Greenhouse first");config.set(GreenhouseConfig.GREENHOUSE_PROFILE,profile);config.set(GreenhouseConfig.GREENHOUSE_PLOT,plot);observed=null;lastMenu=null;}
    public String visibleNativeMenu(){var m=readVisibleMenu(false);if(m==null)return "No native container menu open";return "Native title="+m.title()+" container="+m.id()+" revision="+m.revision()+"\n"+m.slots().stream().filter(slot->!slot.playerInventory()&&slot.count()>0).map(slot->"slot="+slot.id()+" item="+slot.itemId()+" name="+slot.name()).limit(100).toList();}
    public String visibleNativeSlot(int slot){var m=readVisibleMenu(false);if(m==null)return "No native container menu open";var row=m.slots().stream().filter(s->s.id()==slot&&!s.playerInventory()).findFirst();return row.map(r->"Native title="+m.title()+" container="+m.id()+" revision="+m.revision()+"\n"+r.name()+" / "+r.itemId()+"\n"+r.lore()).orElse("Not a native menu slot");}
    public String canonicalInventoryFingerprint(int slot){if(client.player==null||client.level==null||slot<0||slot>=client.player.getInventory().getContainerSize())throw new IllegalArgumentException("Invalid loaded native inventory slot");return diagnosticAdapter().canonicalSlot(slot);}
    public String inventoryFingerprint(int slot){if(client.player==null||slot<0||slot>=client.player.getInventory().getContainerSize())throw new IllegalArgumentException("Invalid loaded inventory slot");return stackIdentity(client.player.getInventory().getItem(slot));}
    /** Prepare only; no selection, input, interaction, or movement takes place. */
    public void prepareWater(Cell cell,int toolSlot,int resourceSlot,int units){
        if(isEnabled())throw new IllegalStateException("Stop Greenhouse before preparing care");
        if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()){
            var actual=associatedPlant(cell);if(actual.isEmpty())throw new IllegalArgumentException("Refresh the actual selected diagnostic request; an unissued or stale menu cannot prepare care");
            observedAt=diagnosticAdapter.oldestReceipt(Set.of(cell));
            observed=new Snapshot(context(),observedAt,revision,Map.of(cell,actual.get()),inventory(),"",-1,services.inventory().selectedSlot(),position(),true,client.player.isAlive(),true);
        }
        var s=snapshot();if(s==null||s.knowledge(now(),settings().freshnessMs())!=Knowledge.KNOWN||!s.screenFree())throw new IllegalArgumentException("Refresh native diagnostics and close the menu first");
        Plant p=s.plants().get(cell);requireWaterablePlant(p);
        var selectedResources=GreenhouseSelectedWaterResources.select(diagnosticAdapter().currentNativeInventory(),toolSlot,resourceSlot,units);
        String tool=inventoryFingerprint(selectedResources.toolSlot()),resource=inventoryFingerprint(selectedResources.resourceSlot());
        if(tool.isBlank()||resource.isBlank()||tool.equals(resource))throw new IllegalArgumentException("Exact distinct existing tool/resource required");
        Plant after=new Plant(p.crop(),p.state(),WaterState.WATERED,p.growthStage(),p.nextGrowth(),Clock.unknown(),p.decayExpiry(),p.fingerprint(),p.source());
        String id="water-inspected-"+revision+"-"+cell;
        var prepared=new GreenhouseRuntime.Plan(id,s.context(),List.of(new GreenhouseRuntime.Action(id,GreenhouseRuntime.Kind.WATER,cell,position(),tool,toolSlot,p,after,Map.of(resource,-units))),"User selected one observed dry cell and exact existing watering resources");
        if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent())diagnosticAdapter.prepareWaterIntegrity(prepared,toolSlot,resourceSlot,units);
        selectPlan(prepared);
    }
    /** Shared actual plan-builder admission; DEAD never creates a proposed water transaction. */
    static void requireWaterablePlant(Plant p){
        if(p==null||!p.identified()||(p.state()!=PlantState.GROWING&&p.state()!=PlantState.HARVEST_READY)||p.water()!=WaterState.DRY)
            throw new IllegalArgumentException("Selected cell must be an observed dry living plant");
    }
    public boolean reconcile(Cell cell,String humanDecision){if(isEnabled())return false;mergeSharedPending();var s=snapshot();boolean result=s!=null&&runtime.reconcileInspectedCell(s.context(),cell,s,now(),settings().freshnessMs(),humanDecision);if(result){persistUnresolved();clearPlan();}return result;}
    public String recipeDetails(String name){return GreenhouseRecipeBrowser.describe(capturedMenus,GreenhouseIdentityCatalog.bundled(),name).text();}
    public String recipeList(String filter){return GreenhouseRecipeBrowser.list(capturedMenus,GreenhouseIdentityCatalog.bundled(),filter).text();}
    public String historicalRecipeDiagnostic(){return "Dated captured recipe observations (2026-10-01; refresh Guide/Sanity Check after 0.27.2): "+capturedMenus.recipeSummary()+" | historical four-Wheat Dustgrain example is superseded by captured2-Wheat condition";}
    public void clearPlan(){if(isEnabled())throw new IllegalStateException("Stop Greenhouse first");config.set(GreenhouseConfig.GREENHOUSE_PLAN,"");plan=null;}

    public Optional<Snapshot> observation(){return Optional.ofNullable(observed);}
    /** Exact plan may be prepared by a local UI; serializing retains the user's explicit selection. */
    public void selectPlan(GreenhouseRuntime.Plan p){if(isEnabled())throw new IllegalStateException("Stop Greenhouse before replacing the plan");diagnosticMode=false;config.set(GreenhouseConfig.GREENHOUSE_PLAN,new Gson().toJson(Objects.requireNonNull(p)));}
    private GreenhouseRuntime.Settings settings(){return new GreenhouseRuntime.Settings(config.get(GreenhouseConfig.GREENHOUSE_ENABLED),config.get(GreenhouseConfig.GREENHOUSE_MAX_ACTIONS),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS),config.get(GreenhouseConfig.GREENHOUSE_ACTION_TIMEOUT_MS),config.get(GreenhouseConfig.GREENHOUSE_RETURN_TIMEOUT_MS));}
    private long now(){return services.clock().nowMillis();}
    private void reload(){
        mergeSharedPending();
        bindings=GreenhouseBindings.parse(config.get(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS));String json=config.get(GreenhouseConfig.GREENHOUSE_PLAN);
        if(json.isBlank())throw new IllegalArgumentException("Select a bounded cultivation plan with observed cells and exact receipts");plan=new Gson().fromJson(json,GreenhouseRuntime.Plan.class);}
    @Override public Optional<String> preStartCheck(){
        try{mergeSharedPending();}catch(RuntimeException invalid){return Optional.of("Invalid shared unresolved Greenhouse journal");}
        if(diagnosticMode){if(!settings().enabled())return Optional.of("Enable Greenhouse before issuing an explicit diagnostic-tool use");if(!persistenceBound)return Optional.of("Durable diagnostic association persistence must be connected");return diagnosticAdapter().readiness();}
        try{reload();
            if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()&&!diagnosticAdapter.preparedStartMatches(plan))return Optional.of("A new associated route needs its exact native slot/tool/resource/cursor prepared-water contract; manual inferred receipts are unavailable");ingest(false);if(!persistenceBound)return Optional.of("Durable unresolved-action persistence must be connected before cultivation");if(client.player==null||client.level==null||client.gameMode==null||!client.player.onGround())return Optional.of("Stand on a loaded supported Greenhouse approach");
            if(!runtime.unresolvedActions().isEmpty())return Optional.of("Unresolved cultivation action journal requires manual inspection; no automatic replay");
            for(var a:plan.actions())if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()&&associatedPlant(a.cell()).isEmpty())return Optional.of("Every selected action needs a fresh confirmed issued diagnostic association");
            for(var a:plan.actions()){var cb=bindings.cells().stream().filter(c->c.cell().equals(a.cell())).findFirst();if(cb.isEmpty()||cb.get().diagnosticTargetCell()==null||!loaded(cb.get().diagnosticTargetCell()))return Optional.of("Exact observed Diagnostic Tool target block required for every action");}
            for(var a:plan.actions())if(!stackIdentity(client.player.getInventory().getItem(a.toolSlot())).equals(a.toolIdentity())||!loaded(a.cell())||!loaded(interactionTarget(a)))return Optional.of("Exact plan tool or loaded cell missing");
            if(!(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()?canonicalInventoryFingerprint(bindings.diagnosticToolSlot()):stackIdentity(client.player.getInventory().getItem(bindings.diagnosticToolSlot()))).equals(bindings.diagnosticToolIdentity()))return Optional.of("Exact observed Diagnostic Tool hotbar fingerprint required");
            if(!navigator.standingClear(vec(position())))return Optional.of("Original physical return pose must have unchanged support and collision clearance");
            return runtime.validate(plan,snapshot(),settings(),now());
        }catch(RuntimeException invalid){return Optional.of(invalid.getMessage()==null?"Invalid Greenhouse plan/bindings":invalid.getMessage());}
    }
    @Override public void onEnable(){var invalid=preStartCheck();if(invalid.isPresent()){diagnostic=invalid.get();setEnabled(false);return;}
        ownerWorld=client.level;ownerPlayer=client.player;ownerConnection=client.getConnection();savedSlot=services.inventory().selectedSlot();savedYaw=client.player.getYRot();savedPitch=client.player.getXRot();captureCultivationSelection(savedSlot);
        configuredPlan=config.get(GreenhouseConfig.GREENHOUSE_PLAN);configuredBindings=config.get(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS);
        if(diagnosticMode){
            configuredBindings=config.get(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS);cleanupInput();diagnosticAdapter().activation();
            if(!diagnosticAdapter().issue()){diagnostic=diagnosticAdapter().status();setEnabled(false);}return;
        }
        cleanupInput();if(diagnosticAdapter!=null)diagnosticAdapter.activation();
        java.util.function.Predicate<dev.veltraph.client.world.BlockPos> originalGuard=p->nativeAction!=null&&nativeAction.kind()==GreenhouseRuntime.Kind.HARVEST&&nativeOwner()
            &&p.x()==nativeAction.cell().x()&&p.y()==nativeAction.cell().y()&&p.z()==nativeAction.cell().z()&&ray(nativeAction.cell())!=null
            &&worldState(nativeAction.cell()).equals(nativeAction.before().fingerprint())&&services.inventory().selectedSlot()==nativeAction.toolSlot()
            &&stackIdentity(client.player.getMainHandItem()).equals(nativeAction.toolIdentity());
        if(this instanceof GreenhouseMaintenanceModule){
            var actual=new CultivationGuardPredicate(this,originalGuard);currentCultivationGuardPredicate=actual;installCultivationGuard(actual);
        }else installCultivationGuard(originalGuard);
        installedCultivationGuard();
        if(!runtime.start(plan,snapshot(),settings(),now()))setEnabled(false);
    }
    @Override public void maintenanceTick(){if(!isEnabled()){readVisibleMenu(false);ingest(false);}}
    @Override public void onTick(){
        if(!isEnabled())return;
        if(diagnosticMode){
            if(!nativeOwner()||!settings().enabled()||!configuredBindings.equals(config.get(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS))){diagnosticAdapter().cancel();setEnabled(false);return;}
            var received=diagnosticAdapter().tick();received.ifPresent(this::acceptDiagnostic);diagnostic=diagnosticAdapter().status();
            if(received.isPresent()||diagnosticAdapter().state()==GreenhouseDiagnosticAssociation.State.UNKNOWN)setEnabled(false);return;
        }
        if(!nativeOwner()||!configuredPlan.equals(config.get(GreenhouseConfig.GREENHOUSE_PLAN))||!configuredBindings.equals(config.get(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS))){runtime.cancel();persistUnresolved();setEnabled(false);return;}
        if(nativeAction!=null&&runtime.state()==GreenhouseRuntime.State.WAIT_ACK){
            if(!worldState(nativeAction.cell()).equals(nativeAction.before().fingerprint()))setCultivationBreaking(false);
            // One independent diagnostic inspection, after the action's native state has had a chance to arrive.
            if(!inspectionRequested&&now()-issuedAt>=250){
                if(nativeAction.kind()==GreenhouseRuntime.Kind.HARVEST&&worldState(nativeAction.cell()).equals(nativeAction.before().fingerprint())){
                    runtime.tick(snapshot(),settings(),now());persistUnresolved();if(!runtime.active())cultivationTerminal();return;}
                setCultivationBreaking(false);selectCultivationSlot(bindings.diagnosticToolSlot());
                Cell inspectionCell=bindings.cells().stream().filter(b->b.cell().equals(nativeAction.cell())).findFirst().orElseThrow().diagnosticTargetCell();
                aimCultivationPoint(new dev.veltraph.client.world.Vec3(inspectionCell.x()+.5,inspectionCell.y()+.5,inspectionCell.z()+.5));
                var hit=ray(inspectionCell);if(hit==null){runtime.tick(snapshot(),settings(),now());return;}
                if(!(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()?canonicalInventoryFingerprint(services.inventory().selectedSlot()):stackIdentity(client.player.getMainHandItem())).equals(bindings.diagnosticToolIdentity())){runtime.cancel();persistUnresolved();setEnabled(false);return;}
                beforeDiagnosticIssue();
                inspectionRequested=true;
                if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()){
                    var original=diagnosticAdapter.selected().orElseThrow();diagnosticAdapter.selectForActiveAction(new GreenhouseDiagnosticAssociation.Selection(bindings,nativeAction.cell(),original.receiptSlot(),original.receiptItemId(),original.sequencePrefix(),original.nativeTargetPrefix(),original.timeoutMs(),original.fullCapturedDiagnostics(),original.isolatedNativeMenu()));
                    if(!diagnosticAdapter.issue()){runtime.cancel();persistUnresolved();setEnabled(false);return;}
                }else client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);
            }
            if(inspectionRequested&&diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()){
                var a=diagnosticAdapter.tick();if(a.isPresent()&&!diagnosticAdapter.preparedAfterMatches(nativeAction)){runtime.cancel();persistUnresolved();setEnabled(false);return;}a.ifPresent(this::acceptDiagnostic);
                if(diagnosticAdapter.state()==GreenhouseDiagnosticAssociation.State.UNKNOWN){runtime.cancel();persistUnresolved();setEnabled(false);return;}
                if(a.isPresent()&&diagnosticAdapter.state()==GreenhouseDiagnosticAssociation.State.ASSOCIATED){int slot=nativeAction.toolSlot();menus.close(client.player.containerMenu.containerId);selectCultivationSlot(slot);}
            }else if(client.gui.screen()!=null&&allowsScreen(client.gui.screen().getTitle().getString())){
                inspectionMenu=client.player.containerMenu;boolean accepted=ingest(true);
                if(accepted){int slot=nativeAction.toolSlot();menus.close(client.player.containerMenu.containerId);selectCultivationSlot(slot);}
                else if(now()-issuedAt<settings().actionTimeoutMs())return;
            }
        }
        if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent()&&runtime.active()){
            var a=runtime.currentAction().orElseThrow();boolean waiting=runtime.state()==GreenhouseRuntime.State.WAIT_ACK||runtime.state()==GreenhouseRuntime.State.RETURN;
            if(waiting&&!diagnosticAdapter.preparedWaitingMatches(a,runtime.state()==GreenhouseRuntime.State.RETURN)){runtime.cancel();persistUnresolved();setEnabled(false);return;}
        }
        runtime.tick(snapshot(),settings(),now());persistUnresolved();diagnostic=runtime.status();if(!runtime.active())cultivationTerminal();
    }
    /** Terminal extension receives only the existing independently acknowledged runtime result. */
    protected void cultivationTerminal(){setEnabled(false);}
    protected final Snapshot currentCultivationSnapshot(){return snapshot();}
    protected final boolean diagnosticOnlySelected(){return diagnosticMode;}
    private boolean ingest(boolean actionInspection){
        if(client.level==null||client.player==null){observed=null;return false;}
        if(observedWorld!=client.level||observedPlayer!=client.player||observedConnection!=client.getConnection()){observed=null;lastMenu=null;capturedMenus.invalidateCurrent();lastAutomaticMenu=null;lastAutomaticObservation=Optional.empty();observedWorld=client.level;observedPlayer=client.player;observedConnection=client.getConnection();}
        try{var menu=readVisibleMenu(actionInspection);if(menu==null)return false;boolean automatic=captureAutomaticMenu(menu).isPresent();
            if(diagnosticAdapter!=null&&diagnosticAdapter.selected().isPresent())return automatic;
            String explicitBindings=config.get(GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS);if(explicitBindings.isBlank())return automatic;
            var b=GreenhouseBindings.parse(explicitBindings);if(menu.equals(lastMenu))return automatic;
            var states=new HashMap<Cell,String>();for(var c:b.cells())if(loaded(c.cell()))states.put(c.cell(),worldState(c.cell()));
            var plants=b.read(menu,states);if(plants.isEmpty())return false;
            if(!config.get(GreenhouseConfig.GREENHOUSE_PROFILE).equals(b.profileId())||!config.get(GreenhouseConfig.GREENHOUSE_PLOT).equals(b.plotId()))return false;
            lastMenu=menu;observedAt=cachedMenuAt;revision++;bindings=b;
            observed=new Snapshot(context(),observedAt,revision,plants.get(),inventory(),"",-1,services.inventory().selectedSlot(),position(),false,client.player.isAlive(),true);
            config.set(GreenhouseConfig.GREENHOUSE_OBSERVATIONS,new GsonBuilder().enableComplexMapKeySerialization().create().toJson(observed));
            diagnostic="KNOWN: "+plants.get().size()+" native diagnostic cells; growth-cycle clocks and server epochs kept separate";return true;
        }catch(RuntimeException invalid){diagnostic="UNKNOWN: "+(invalid.getMessage()==null?"Invalid native diagnostic bindings":invalid.getMessage());return false;}
    }
    private Optional<GreenhouseCapturedMenus.Observation> captureAutomaticMenu(MenuSnapshot menu){
        if(menu==null)return Optional.empty();
        if(menu.equals(lastAutomaticMenu)&&cachedMenuAt==lastAutomaticAt)return lastAutomaticObservation;
        var parsed=capturedMenus.observe(menu,cachedMenuAt,"Genuinely visible native container");lastAutomaticMenu=menu;lastAutomaticAt=cachedMenuAt;lastAutomaticObservation=parsed;
        if(parsed.isPresent()){
            config.set(GreenhouseConfig.GREENHOUSE_CAPTURED_MENUS,capturedMenus.export());
            diagnostic="Automatic captured Greenhouse menu: "+parsed.get().kind()+" (read-only; world target unbound)";
        }return parsed;
    }
    /** Cache only a genuinely visible native container on the client thread; chat commands consume read-only evidence. */
    private MenuSnapshot readVisibleMenu(boolean requireCurrent){
        var current=menus.read();
        if(current!=null){if(cachedNativeMenu==null||!cachedNativeMenu.equals(current)||cachedWorld!=client.level||cachedPlayer!=client.player||cachedConnection!=client.getConnection()){
            cachedNativeMenu=current;cachedMenuAt=now();cachedWorld=client.level;cachedPlayer=client.player;cachedConnection=client.getConnection();
        }return current;}
        if(requireCurrent)return null;
        if(cachedNativeMenu!=null&&cachedWorld==client.level&&cachedPlayer==client.player&&cachedConnection==client.getConnection()&&now()>=cachedMenuAt&&now()-cachedMenuAt<=config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS))return cachedNativeMenu;
        return null;
    }
    private Context context(){return new Context(config.get(GreenhouseConfig.GREENHOUSE_PROFILE),config.get(GreenhouseConfig.GREENHOUSE_PLOT),client.level.dimension().identifier().toString());}
    private Snapshot snapshot(){if(observed==null||client.player==null||client.level==null||observedWorld!=client.level||observedPlayer!=client.player||observedConnection!=client.getConnection()||!observed.context().equals(context()))return null;
        var a=runtime.active()?runtime.currentAction().orElse(null):plan==null||plan.actions().isEmpty()?null:plan.actions().getFirst();int slot=a==null?-1:a.toolSlot();
        boolean allLoaded=observed.plants().keySet().stream().allMatch(this::loaded);
        return new Snapshot(context(),observedAt,revision,observed.plants(),inventory(),slot<0?"":stackIdentity(client.player.getInventory().getItem(slot)),slot,
            services.inventory().selectedSlot(),position(),client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty(),client.player.isAlive(),allLoaded);}
    public static String stackIdentity(ItemStack s){if(s.isEmpty())return "";return BuiltInRegistries.ITEM.getKey(s.getItem())+"|"+s.getComponents();}
    private Map<String,Integer> inventory(){var m=new HashMap<String,Integer>();for(int i=0;i<client.player.getInventory().getContainerSize();i++){var s=client.player.getInventory().getItem(i);if(!s.isEmpty())m.merge(stackIdentity(s),s.getCount(),Math::addExact);}return Map.copyOf(m);}
    private boolean nativeOwner(){return client.player!=null&&client.player==ownerPlayer&&client.level==ownerWorld&&client.getConnection()==ownerConnection&&client.player.isAlive()&&client.gameMode!=null;}
    private boolean loaded(Cell c){return McGreenhouseLoadedCell.readable(client.level,c);}
    private String worldState(Cell c){return loaded(c)?client.level.getBlockState(new net.minecraft.core.BlockPos(c.x(),c.y(),c.z())).toString():"";}
    private BlockHitResult ray(Cell c){if(!loaded(c)||client.player==null||!(client.hitResult instanceof BlockHitResult h)||h.getType()!=HitResult.Type.BLOCK||!h.getBlockPos().equals(new net.minecraft.core.BlockPos(c.x(),c.y(),c.z()))||client.player.getEyePosition().distanceTo(h.getLocation())>3.5)return null;return h;}
    /** Ordinary seed placement targets the support's top face; the plant cell itself is empty air. */
    static Cell interactionTarget(GreenhouseRuntime.Action a){return a.kind()==GreenhouseRuntime.Kind.REPLANT?new Cell(a.cell().x(),Math.subtractExact(a.cell().y(),1),a.cell().z()):a.cell();}
    private BlockHitResult actionRay(GreenhouseRuntime.Action a){var hit=ray(interactionTarget(a));return hit!=null&&(a.kind()!=GreenhouseRuntime.Kind.REPLANT||hit.getDirection()==net.minecraft.core.Direction.UP)?hit:null;}
    private Position position(){return new Position(client.player.getX(),client.player.getY(),client.player.getZ());}
    private static dev.veltraph.client.world.Vec3 vec(Position p){return new dev.veltraph.client.world.Vec3(p.x(),p.y(),p.z());}
    private void mergeSharedPending(){if(!runtime.active())runtime.mergeSharedUnresolved(GreenhouseSharedJournal.read(config.get(GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS)),now(),config.revision(GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS).orElse(-1));}
    private void persistUnresolved(){
        var shared=GreenhouseSharedJournal.read(config.get(GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS));
        var merged=GreenhouseSharedJournal.merge(shared,runtime.unresolvedActions(),runtime.takeJournalResolutions());
        config.set(GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS,new Gson().toJson(merged));
    }
    protected void beforeDiagnosticIssue(){}
    private CultivationGuardPredicate currentCultivationGuardPredicate;
    private static final class CultivationGuardPredicate implements java.util.function.Predicate<dev.veltraph.client.world.BlockPos> {
        private final GreenhouseModule owner;private final java.util.function.Predicate<dev.veltraph.client.world.BlockPos> original;
        private CultivationGuardPredicate(GreenhouseModule owner,java.util.function.Predicate<dev.veltraph.client.world.BlockPos> original){this.owner=owner;this.original=original;}
        @Override public boolean test(dev.veltraph.client.world.BlockPos target){return original.test(target);}
    }
    /** Exact private original-lambda provenance only; never evaluates the predicate or grants native use. */
    public final boolean cultivationGuardPredicateCurrent(java.util.function.Predicate<dev.veltraph.client.world.BlockPos> exact){
        return exact instanceof CultivationGuardPredicate actual&&actual==currentCultivationGuardPredicate&&actual.owner==this;
    }
    protected void installCultivationGuard(java.util.function.Predicate<dev.veltraph.client.world.BlockPos> target){services.breaking().installTargetGuard(this,target);}
    protected void installedCultivationGuard(){}
    protected void clearCultivationGuard(){services.breaking().clearTargetGuard(this);}
    protected void aimCultivationView(float yaw,float pitch){services.rotation().aim(yaw,pitch);}
    protected void aimCultivationPoint(dev.veltraph.client.world.Vec3 target){services.rotation().lookAt(target);}
    protected void setCultivationBreaking(boolean held){services.breaking().setBreaking(held);}
    protected void releaseCultivationUse(){McHeldKeys.setHeld(client.options.keyUse,false);}
    protected final void stopCultivationTravel(){travel.stop();}
    /** Local terminal custody only. This observes no native setter, provider, clock, or renewed travel authority. */
    protected final boolean completedCultivationCleanupQuiescent(){
        return runtime.state()==GreenhouseRuntime.State.COMPLETE&&!runtime.active()&&runtime.unresolvedActions().isEmpty()
            &&savedSlot==-1&&nativeAction==null&&!travel.hasLeg();
    }
    protected void cleanupInput(){travel.stop();services.rotation().cancelRotation();setCultivationBreaking(false);services.input().releaseAllMovement();if(client.options!=null){releaseCultivationUse();McHeldKeys.setHeld(client.options.keyAttack,false);}}
    protected void captureCultivationSelection(int originalSlot){}
    protected boolean selectCultivationSlot(int slot){services.inventory().selectSlot(slot);return services.inventory().selectedSlot()==slot;}
    protected boolean restoreOriginalCultivationSlot(int originalSlot){services.inventory().selectSlot(originalSlot);return true;}
    private void cleanup(){cleanupInput();clearCultivationGuard();currentCultivationGuardPredicate=null;if(savedSlot>=0&&nativeOwner()){restoreOriginalCultivationSlot(savedSlot);}savedSlot=-1;}
    @Override public void onDisable(){
        if(diagnosticMode){
            // This inspection never moved/selected/held input. Preserve any intervening manual or replacement-owner pose/slot.
            if(diagnosticAdapter!=null)diagnosticAdapter.cancel();persistUnresolved();savedSlot=-1;ownerWorld=ownerPlayer=ownerConnection=null;nativeAction=null;return;
        }
        if(diagnosticAdapter!=null)diagnosticAdapter.cancel();runtime.cancel();persistUnresolved();cleanup();ownerWorld=ownerPlayer=ownerConnection=null;nativeAction=null;}
    @Override public boolean allowsScreen(String title){return bindings!=null&&MenuSnapshot.clean(title).equals(bindings.menuTitle());}
    @Override public String diagnostics(){return diagnostic+"\n"+diagnosticAssociationStatus()+"\n"+runtime.status()+"\n"+(observed==null?"Plant state UNKNOWN; captured geometry proves no growth or readiness":observed.knowledge(now(),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS))+": observed "+observed.plants().size()+" cells, revision "+revision)+"\n"+GreenhouseIdentityCatalog.bundled().diagnostic()+"\n"+capturedMenus.catalogSummary()+"\n"+capturedMenus.diagnosticSummary(now(),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS))+"\nArchived diagnostic cells="+(archived==null?0:archived.plants().size())+" (not current readiness)";}
}
