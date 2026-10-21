package dev.veltraph.client.metrics;

import dev.veltraph.client.core.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnerMetricsBindingsTest {
    private static final class Owner extends AbstractModule {
        long total;int reads,resets;
        Owner(String id){super(id,id,ModuleCategory.FORAGING);}
        @Override public void onEnable(){total=0;resets++;}
        @Override public void onDisable(){}
        @Override public void onTick(){}
        void pause(boolean paused){setState(paused?MacroState.PAUSED:MacroState.RUNNING);}
        long read(){reads++;return total;}
    }
    private static final class Sink implements OwnerMetricsBindings.Sink {
        final OwnerMetricEpochs epochs=new OwnerMetricEpochs();final LocalSessionMetrics model;final LocalSessionMetrics.Context context=new LocalSessionMetrics.Context(new Object(),new Object(),new Object());int starts;
        Sink(){model=new LocalSessionMetrics(MetricContracts.empty(),0,10000,3000,2000);model.bind(context,Map.of(),0);}
        @Override public void freshStart(String id,OwnerMetricEpochs.OwnerCounter kind,long baseline,long now){starts++;var s=epochs.freshStart(id,kind,baseline);model.cumulative(context,s.series(),s.counter(),s.serial(),s.cumulativeTotal(),now);}
        @Override public void total(String id,OwnerMetricEpochs.OwnerCounter kind,long cumulative,long now){var s=epochs.sample(id,kind,cumulative).orElseThrow();model.cumulative(context,s.series(),s.counter(),s.serial(),s.cumulativeTotal(),now);}
        long trees(){return model.snapshot(0).count(LocalSessionMetrics.Counter.CONFIRMED_TREES).orElse(0);}
    }
    private OwnerMetricsBindings bindings(ModuleManager manager,AtomicReference<Sink> sink,AtomicBoolean enabled){return new OwnerMetricsBindings(manager,m->m instanceof Owner owner?Optional.of(new OwnerMetricsBindings.Binding(OwnerMetricEpochs.OwnerCounter.AUTO_FORAGING_TREES,owner::read)):Optional.empty(),sink::get,enabled::get);}
    @Test void defaultDisabledDoesNotReadOrResetEnginesAndEnablingMidActivationBaselinesCurrentValue(){
        var manager=new ModuleManager();var owner=new Owner("tree");manager.register(owner);manager.enable(owner.id());var sink=new Sink();var b=bindings(manager,new AtomicReference<>(sink),new AtomicBoolean(false));
        b.afterFreshStart(owner,0);owner.total=20;b.poll(0);assertEquals(0,owner.reads);assertEquals(0,sink.starts);assertEquals(1,owner.resets);
        var enabled=new AtomicBoolean(false);b=bindings(manager,new AtomicReference<>(sink),enabled);b.afterFreshStart(owner,0);enabled.set(true);b.poll(0);assertEquals(0,sink.trees());owner.total=21;b.poll(0);assertEquals(1,sink.trees());assertEquals(1,owner.resets);
    }
    @Test void multipleSameKindOwnersHaveIndependentEpochsAndResettingOneCannotRecreditTheOther(){
        var manager=new ModuleManager();var a=new Owner("hub_tree");var c=new Owner("park_tree");manager.register(a);manager.register(c);manager.enable(a.id());manager.enable(c.id());var sink=new Sink();var b=bindings(manager,new AtomicReference<>(sink),new AtomicBoolean(true));b.afterFreshStart(a,0);b.afterFreshStart(c,0);
        a.total=2;c.total=3;b.poll(0);b.poll(0);assertEquals(5,sink.trees());
        manager.disable(a.id());manager.enable(a.id());b.afterFreshStart(a,0);c.total=4;b.poll(0);a.total=1;b.poll(0);assertEquals(7,sink.trees());assertEquals(3,sink.starts);assertEquals(2,a.resets);assertEquals(1,c.resets);
        assertTrue(sink.model.snapshot(0).count(LocalSessionMetrics.Counter.SORROWS_COLLECTED).isEmpty());assertTrue(sink.model.snapshot(0).count(LocalSessionMetrics.Counter.SUMMONING_EYES_COLLECTED).isEmpty());
    }
    @Test void preservedSameEnginePauseDoesNotCreateAnotherGeneration(){
        var manager=new ModuleManager();var owner=new Owner("tree");manager.register(owner);manager.enable(owner.id());var sink=new Sink();var b=bindings(manager,new AtomicReference<>(sink),new AtomicBoolean(true));b.afterFreshStart(owner,0);owner.total=2;b.poll(0);owner.pause(true);b.poll(0);owner.pause(false);owner.total=3;b.poll(0);
        assertEquals(3,sink.trees());assertEquals(1,sink.starts);assertEquals(1,owner.resets);
    }
    @Test void replacementCollectorBaselinesActiveOwnerAndUnregisteredOrUnstartedOwnersStayUnavailable(){
        var manager=new ModuleManager();var owner=new Owner("tree");manager.register(owner);manager.enable(owner.id());var target=new AtomicReference<>(new Sink());var b=bindings(manager,target,new AtomicBoolean(true));b.poll(0);assertEquals(0,owner.reads);assertEquals(0,target.get().starts);
        var external=new Owner("tree");external.setEnabled(true);b.afterFreshStart(external,0);assertEquals(0,external.reads);assertEquals(0,target.get().starts);
        b.afterFreshStart(owner,0);owner.total=10;b.poll(0);assertEquals(10,target.get().trees());var next=new Sink();target.set(next);b.poll(0);assertEquals(0,next.trees());owner.total=11;b.poll(0);assertEquals(1,next.trees());assertEquals(1,next.starts);assertEquals(1,owner.resets);
    }
    @Test void terminalDisabledOwnerCreditsItsLastTotalOnceThenRetiresActivation(){
        var manager=new ModuleManager();var owner=new Owner("tree");manager.register(owner);manager.enable(owner.id());
        var sink=new Sink();var b=bindings(manager,new AtomicReference<>(sink),new AtomicBoolean(true));b.afterFreshStart(owner,0);
        owner.total=2;owner.setEnabled(false);b.poll(0);assertEquals(2,sink.trees());int terminalReads=owner.reads;
        owner.total=9;b.poll(0);assertEquals(2,sink.trees());assertEquals(terminalReads,owner.reads);
        manager.enable(owner.id());owner.total=1;b.poll(0);assertEquals(terminalReads,owner.reads);
        b.afterFreshStart(owner,0);owner.total=2;b.poll(0);assertEquals(3,sink.trees());
    }
    @Test void disabledTerminalCannotArmAfterMetricsEnableOrCollectorReplacement(){
        var manager=new ModuleManager();var owner=new Owner("tree");manager.register(owner);manager.enable(owner.id());
        var sink=new AtomicReference<>(new Sink());var enabled=new AtomicBoolean(false);var b=bindings(manager,sink,enabled);b.afterFreshStart(owner,0);
        owner.total=4;owner.setEnabled(false);enabled.set(true);b.poll(0);assertEquals(0,owner.reads);assertEquals(0,sink.get().starts);
        manager.enable(owner.id());b.afterFreshStart(owner,0);owner.total=5;owner.setEnabled(false);
        sink.set(new Sink());int reads=owner.reads;b.poll(0);assertEquals(reads,owner.reads);assertEquals(0,sink.get().starts);
    }
    @Test void changingNativeObservationIdentityRebaselinesAnActiveOwner(){
        var manager=new ModuleManager();var owner=new Owner("tree");manager.register(owner);manager.enable(owner.id());
        var sink=new Sink();var scope=new AtomicReference<>(new Object());var enabled=new AtomicBoolean(true);
        var b=new OwnerMetricsBindings(manager,m->Optional.of(new OwnerMetricsBindings.Binding(OwnerMetricEpochs.OwnerCounter.AUTO_FORAGING_TREES,owner::read)),()->new OwnerMetricsBindings.Sink(){
            @Override public Object identity(){return scope.get();}
            @Override public void freshStart(String id,OwnerMetricEpochs.OwnerCounter kind,long baseline,long now){sink.freshStart(id,kind,baseline,now);}
            @Override public void total(String id,OwnerMetricEpochs.OwnerCounter kind,long total,long now){sink.total(id,kind,total,now);}
        },enabled::get);
        b.afterFreshStart(owner,0);owner.total=2;b.poll(0);assertEquals(2,sink.trees());
        scope.set(new Object());owner.total=100;b.poll(0);assertEquals(2,sink.trees());assertEquals(2,sink.starts);
        owner.total=101;b.poll(0);assertEquals(3,sink.trees());
        enabled.set(false);owner.total=102;owner.setEnabled(false);int reads=owner.reads;b.poll(0);
        enabled.set(true);b.poll(0);assertEquals(reads,owner.reads);assertEquals(3,sink.trees());
    }

}
