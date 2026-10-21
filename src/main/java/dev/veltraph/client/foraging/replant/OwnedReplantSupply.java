package dev.veltraph.client.foraging.replant;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.foraging.supply.*;
import dev.veltraph.client.metrics.*;
import dev.veltraph.client.service.FarmServices;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Exact original-parent child: existing supply FSM, then natural return to original home. */
final class OwnedReplantSupply {
    enum State { PREPARING, SUPPLYING, RETURNING, RESUMED, FAILED }
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final ForagingReplantModule parent;private final McForagingReplant original;private final ForagingReplantCycle cycle;
    private final ForagingReplantCycle.Resource resource;private final long started,deadline;private long lastSeen;
    private final ForagingSupplyBindings bindings;
    private final List<ForagingReplantCycle.Cell> cells;private final MapConfigService.StoredStateSnapshot profile;
    private final ModuleManager manager;private final ModuleManager.LifecycleSnapshot lifecycle;
    private final UUID activation=UUID.randomUUID();private final List<FinancialActionReceipt> receipts=new ArrayList<>();
    private ForagingSupplyRuntime supply;private McForagingSupplyPort port;private State state=State.PREPARING;private String reason="Preparing original inventory loans";
    private List<ForagingSupplyRuntime.Stack> retained=List.of();private boolean returnStarted;
    OwnedReplantSupply(Minecraft client,ConfigService config,FarmServices services,ForagingReplantModule parent,McForagingReplant original,ForagingReplantCycle cycle){
        this.client=client;this.config=config;this.services=services;this.parent=parent;this.original=original;this.cycle=cycle;
        resource=cycle.supplyRequest().orElseThrow();started=services.clock().nowMillis();bindings=ForagingSupplyBindings.parse(config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_BINDINGS));deadline=Math.addExact(started,bindings.timeoutMs());lastSeen=started;cells=original.read().cells();profile=config instanceof MapConfigService map?map.storedStateSnapshot():config instanceof JsonConfigService json?json.storedStateSnapshot():null;
        if(profile==null)throw new IllegalStateException("Exact scalar configuration mutation history is unavailable");
        manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();lifecycle=manager==null?null:manager.lifecycleSnapshot();
    }
    private boolean valid(){
        long now=services.clock().nowMillis();if(now<lastSeen||now>=deadline)return false;lastSeen=now;
        if(!client.isSameThread()||manager==null||manager!=dev.veltraph.client.VeltraphBootstrap.moduleManager()||!manager.lifecycleCurrent(lifecycle)
            ||manager.get(parent.id()).orElse(null)!=parent||manager.inputOwner().orElse(null)!=parent||!manager.lifecycleCurrent(lifecycle)||!parent.isEnabled()
            ||parent.scheduledEngineIdentity()!=cycle||!cycle.supplyBoundary()||!original.nativeOwnerBound()||!profile.current())return false;
        var o=original.read();return o.available()&&o.loaded()&&o.soilValid()&&!o.wrongSpecies()&&(o.cursorEmpty()||state==State.SUPPLYING&&port!=null&&port.ownedPurchasePrediction(port.read()))&&o.cells().equals(cells)&&manager.lifecycleCurrent(lifecycle)&&profile.current()&&clockCurrent();
    }
    private boolean clockCurrent(){long now=services.clock().nowMillis();if(now<lastSeen||now>=deadline)return false;lastSeen=now;return true;}
    private void fail(String why){reason=why;state=State.FAILED;if(port!=null){if(supply!=null&&!supply.terminal())supply.cancel(port);else port.release();}cycle.rejectSupply(why,original);}
    void tick(){
        if(state==State.FAILED||state==State.RESUMED)return;
        if(!valid()){fail("Original parent, engine, profile, resource footprint or native scope changed during supply");return;}
        long now=services.clock().nowMillis();
        if(now<started){fail("Supply clock moved backwards");return;}
        // The configured transaction bound covers preparation, supply and return; no renewed travel budget.
        if(!config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_ENABLED)||now>=deadline){fail("Automatic supply disabled or original transaction deadline exceeded");return;}
        if(state==State.PREPARING){
            if(!original.prepareForSupply())return;if(!valid()){fail("Owner changed while settling original inventory loans");return;}
            var type=resource==ForagingReplantCycle.Resource.SAPLING?ForagingSupplyConfig.Type.SAPLING:ForagingSupplyConfig.Type.BONEMEAL;
            var expected=resource==ForagingReplantCycle.Resource.SAPLING?"minecraft:dark_oak_sapling":"minecraft:bone_meal";
            if(!bindings.resources().get(type).id().equals(expected)){fail("Supply binding differs from original Dark Oak resource");return;}
            port=new McForagingSupplyPort(client,config,services,bindings,type,()->parent,this::valid);supply=new ForagingSupplyRuntime();
            if(!supply.startReplenishment(port.read(),bindings,type,resource==ForagingReplantCycle.Resource.SAPLING?4:1)){fail("Parent replenishment admission refused: "+supply.reason());return;}
            port.bindOwner();state=State.SUPPLYING;reason="Original "+resource+" shortage; existing supply child active";
        }
        if(!valid()||!port.ownerValid()){fail("Native protection or original owner changed after supply admission");return;}
        if(state==State.SUPPLYING){
            supply.tick(port.read(),port);
            if(!valid()||!port.ownerValid()){fail("Original context changed during supply callback");return;}
            if(supply.acquiredCost()>0&&receipts.isEmpty()){
                var proof=port.acceptedAcquisition().orElse(null);
                if(proof==null||proof.amount()!=supply.acquiredCost()){supply.unattributedAcquisition(port);fail("Native purchase lacks exact financial witness");return;}
                var receipt=ForagingSupplyFinancialProof.accept(parent,activation,proof.menu(),proof.menuId(),proof.requestRevision(),
                    new FinancialOutcomeBindings.Scope(proof.scope().player(),proof.scope().world(),proof.scope().connection()),proof.amount(),proof.receipts()).orElse(null);
                if(receipt==null){supply.unattributedAcquisition(port);fail("Native purchase financial witness rejected");return;}receipts.add(receipt);
            }
            if(!supply.terminal())return;
            if(supply.stage()!=ForagingSupplyRuntime.Stage.COMPLETE){fail("Supply stopped: "+supply.reason());return;}
            retained=supply.acknowledgedInventory();state=State.RETURNING;reason="Native supply retained; returning original parent home";
        }
        if(state==State.RETURNING){
            if(!valid()){fail("Original authority changed before return");return;}
            var inventory=port.read();if(!manager.lifecycleCurrent(lifecycle)||!profile.current()||!original.nativeOwnerBound()||!clockCurrent()||!port.ownerValid()||!inventory.inventory().equals(retained)||inventory.menu()!=null||!inventory.cursorEmpty()){fail("Retained supply ledger/menu changed before return");return;}
            if(returnStarted||!original.read().home()){returnStarted=true;if(!original.returnHome())return;}
            if(!original.read().home()){fail("Natural return did not reach original parent home");return;}
            if(!valid()||!port.ownerValid()||!port.read().inventory().equals(retained)){fail("Original context changed during return callback");return;}
            original.release();
            if(!valid()){fail("Original authority changed during return release");return;}
            var finalInventory=port.read();var finalObservation=original.read();long finished=services.clock().nowMillis();
            if(!manager.lifecycleCurrent(lifecycle)||!profile.current()||!original.nativeOwnerBound()||!port.ownerValid()||!finalInventory.inventory().equals(retained)||finalInventory.menu()!=null||!finalInventory.cursorEmpty()
                ||!finalObservation.home()||finished<lastSeen||finished>=deadline||!cycle.resumeAfterSupply(finalObservation,finished-started)){
                fail("Same original replant stage lost native ledger, protection, home, closed menu or deadline during release");return;
            }
            state=State.RESUMED;reason="Same original parent/cycle resumed after native supply and home return";
        }
    }
    void cancel(){if(port!=null){if(supply!=null&&!supply.terminal())supply.cancel(port);else port.release();}state=State.FAILED;reason="Parent stopped during owned supply";}
    boolean resumed(){return state==State.RESUMED;}boolean terminal(){return state==State.FAILED||state==State.RESUMED;}
    boolean allowsScreen(String title){if(state!=State.SUPPLYING)return false;try{var b=ForagingSupplyBindings.parse(config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_BINDINGS));var t=resource==ForagingReplantCycle.Resource.SAPLING?ForagingSupplyConfig.Type.SAPLING:ForagingSupplyConfig.Type.BONEMEAL;return title.equals(b.chestTitle())||title.equals(b.resources().get(t).offerTitle());}catch(RuntimeException bad){return false;}}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return state==State.SUPPLYING&&port!=null&&port.consumeExpectedTeleport(position,now);}
    List<FinancialActionReceipt> receipts(){return List.copyOf(receipts);}String diagnostics(){return state+": "+reason+(supply==null?"":" | "+supply.stage()+": "+supply.reason());}
}
