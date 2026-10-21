package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.FarmServices;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Follow typed travel checkpoints, restoring each checkpoint's view on arrival. */
public final class RouteModule extends AbstractModule {
    public static final String ID="local_route";
    private final Minecraft client;
    private final McTravelNavigator navigator;
    private final FarmServices services;
    private LocalRoute route;
    private int index;
    private boolean walking;
    private String firstStop="";
    private final dev.veltraph.client.navigation.RouteWorkflowEpoch workflow=new dev.veltraph.client.navigation.RouteWorkflowEpoch();
    private void advanceWorkflowEpoch(){workflow.advance();}
    /** Privately captured caller state; current() is a refusal-only callback-free leaf. */
    private final class RouteWorkflowSnapshot implements dev.veltraph.client.service.mc.McRotationService.FinalWorkflowLeaf {
        private final dev.veltraph.client.navigation.RouteWorkflowEpoch.Snapshot epoch=workflow.snapshot();private final LocalRoute expectedRoute=route;
        private final int expectedIndex=index;private final boolean expectedWalking=walking,expectedEnabled=isEnabled();
        @Override public boolean current(){return workflow.current(epoch)&&route==expectedRoute
            &&index==expectedIndex&&walking==expectedWalking&&isEnabled()==expectedEnabled;}
    }
    private dev.veltraph.client.service.mc.McRotationService.DirectPublicationReceipt viewPublication;
    private dev.veltraph.client.service.mc.McRotationService.DirectCancellationReceipt viewCancellation;
    private String status="Load or record a route with /vroute";
    public RouteModule(Minecraft client,FarmServices services,dev.veltraph.client.config.ConfigService config) {
        super(ID,"Local Route",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;
        navigator=new McTravelNavigator(client,services,config);
    }
    public void setRoute(LocalRoute route) {
        if(isEnabled()) throw new IllegalStateException("Stop the route before loading another");
        advanceWorkflowEpoch();this.route=route; status="Ready: "+route.nodes().size()+" checkpoints";
    }
    @Override public boolean requiresSkyBlock() { return true; }
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now) { return navigator.consumeExpectedTeleport(position,now); }
    @Override public Optional<GameArea> requiredArea() { return route==null?Optional.empty():Optional.of(route.area()); }
    @Override public Optional<String> preStartCheck() {
        if(route==null) return Optional.of("Record or load a route with /vroute first.");
        if(client.level==null||!client.level.dimension().identifier().toString().equals(route.dimension()))
            return Optional.of("Route belongs to a different dimension.");
        if(route.nodes().stream().anyMatch(n->n.kind()==LocalRoute.Kind.FLY) && (client.player==null||!client.player.getAbilities().mayfly))
            return Optional.of("This route needs flight permission from the server.");
        if(route.nodes().stream().anyMatch(n->n.kind()==LocalRoute.Kind.ETHERWARP) && !navigator.hasEtherwarpTool())
            return Optional.of("This route needs an Ether Transmission item in the hotbar.");
        return Optional.empty();
    }
    @Override public void onEnable() { advanceWorkflowEpoch();index=0; walking=false; firstStop=""; viewPublication=null;viewCancellation=null; }
    @Override public void onDisable() {
        advanceWorkflowEpoch();final var stopping=new RouteWorkflowSnapshot();final var publication=viewPublication;
        observeStop();
        try{navigator.stop();}
        finally{try{if(stopping.current())cancelOwnedView(publication,stopping);}
            finally{if(stopping.current()){walking=false;viewPublication=null;}}}
    }
    /** Retire movement with its existing receipts first; never supersede its generation with a raw cancel. */
    private void cancelOwnedView(dev.veltraph.client.service.mc.McRotationService.DirectPublicationReceipt publication,RouteWorkflowSnapshot stopping){
        if(publication==null||publication.after().parent()!=this||!stopping.current()
                ||!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation))return;
        var cancellation=rotation.cancelDirectOwned(publication,()->publication.after().parent()==this&&stopping.current(),true,stopping);
        if(stopping.current())viewCancellation=cancellation;
    }
    public dev.veltraph.client.service.mc.McRotationService.DirectCancellationReceipt viewCancellation(){return viewCancellation;}
    private boolean viewPublicationCurrent(LocalRoute expectedRoute,int expectedIndex){
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return isEnabled()&&walking&&route==expectedRoute&&index==expectedIndex&&root!=null
            &&root.get(id()).orElse(null)==this&&root.inputOwner().orElse(null)==this;
    }
    /** Preserve the first disabling call before the original cancellation and cleanup erase context. */
    private void observeStop(){
        if(!firstStop.isEmpty())return;
        try{firstStop="status="+status+" navigator="+navigator.status()+" sharedFirst="+navigator.sharedFirstFailure()+" stack="+java.util.Arrays.toString(Thread.currentThread().getStackTrace());}
        catch(Throwable unavailable){if(firstStop.isEmpty())firstStop="capture unavailable="+unavailable.getClass().getName();}
        try{System.out.println("ROUTE_FIRST_DISABLE "+firstStop);}catch(Throwable reportUnavailable){}
    }
    public String firstStop(){return firstStop;}
    public String prearmFailureDiagnostic(){return "firstStop="+firstStop+" sharedFirst="+navigator.sharedFirstFailure()+" retirement="+navigator.sharedRetirementDiagnostic();}
    @Override public void onTick() {
        if(client.player==null||client.level==null) { setEnabled(false);return; }
        if(client.gui.screen()!=null){
            // GUI provenance was captured while this actual Root workflow was still enabled.
            final var closing=new RouteWorkflowSnapshot();
            try{navigator.stop();}catch(RuntimeException|Error failure){if(!closing.current())return;throw failure;}
            if(!closing.current())return;
            setEnabled(false);return;
        }
        var node=route.nodes().get(index);
        if(!walking) {
            final var starting=new RouteWorkflowSnapshot();boolean started=navigator.start(node);
            if(!starting.current())return;
            if(!started) { var failedStatus="Checkpoint "+(index+1)+": "+navigator.status();if(!starting.current())return;status=failedStatus;setEnabled(false);return; }
            walking=true;
        }
        final var running=new RouteWorkflowSnapshot();var state=navigator.tick();
        if(!running.current())return;
        var progressStatus="Checkpoint "+(index+1)+"/"+route.nodes().size()+": "+node.kind()+" / "+navigator.status();
        if(!running.current())return;status=progressStatus;
        if(state==McTravelNavigator.State.FAILED) { setEnabled(false);return; }
        if(state==McTravelNavigator.State.ARRIVED) {
            final var arrivalWorkflow=new RouteWorkflowSnapshot();
            float delta=WalkController.wrap(node.yaw()-client.player.getYRot());float pitch=node.pitch()-client.player.getXRot();
            if(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation){
                final var expectedRoute=route;final int expectedIndex=index;final var workflow=new RouteWorkflowSnapshot();
                var receipt=rotation.trackWithReceipt(node.yaw(),node.pitch(),()->viewPublicationCurrent(expectedRoute,expectedIndex),workflow);
                if(!workflow.current())return;
                if(receipt==null||receipt.after().parent()!=this){status="Checkpoint view publication refused";setEnabled(false);return;}
                viewPublication=receipt;
            }else services.rotation().track(node.yaw(),node.pitch());
            if(!arrivalWorkflow.current())return;
            if(Math.abs(delta)>1||Math.abs(pitch)>1) return;
            walking=false;
            if(++index>=route.nodes().size()) { if(route.loop()) index=0;else { status="Route complete";setEnabled(false); } }
        }
    }
    @Override public String diagnostics() { return status; }
}
