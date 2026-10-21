package dev.veltraph.client.metrics;
import dev.veltraph.client.core.*;
import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel;
import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Landing;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class BottleFinancialPendingTransitTest {
 static final class Owner extends AbstractModule implements BottleFinancialOwner {Travel travel;final List<FinancialOutcomeBindings.Outcome> outcomes=new ArrayList<>();Owner(){super("bottle","bottle",ModuleCategory.MISC);}@Override public void onEnable(){}@Override public void onDisable(){}@Override public void onTick(){}public Optional<Travel> pendingBottleFinancialTravel(){return Optional.ofNullable(travel);}}
 static final class Sink implements FinancialOutcomeBindings.Sink {final List<String> ids=new ArrayList<>();public boolean confirmed(String id,boolean expense,java.math.BigDecimal amount,long now){ids.add(id);return true;}}
 final ModuleManager manager=new ModuleManager();final Owner owner=new Owner();final Object connection=new Object();final FinancialOutcomeBindings.Scope original=new FinancialOutcomeBindings.Scope(new Object(),new Object(),connection);final AtomicReference<FinancialOutcomeBindings.Scope> scope=new AtomicReference<>(original);final AtomicReference<Sink> sink=new AtomicReference<>(new Sink());final FinancialOutcomeBindings bridge=new FinancialOutcomeBindings(manager,m->Optional.of(new FinancialOutcomeBindings.Binding(()->owner.outcomes,false)),scope::get,sink::get,()->true);
 BottleFinancialPendingTransitTest(){manager.register(owner);manager.enable(owner.id());bridge.afterFreshStart(owner,0);}
 Travel travel(){return new Travel(1,original.player(),original.world(),connection,UUID.randomUUID(),new Landing("hub",GameArea.HUB,"minecraft:overworld",new Vec3(.5,70,.5)),3,0,500,List.of());}
 FinancialOutcomeBindings.Scope replacement(){return new FinancialOutcomeBindings.Scope(new Object(),new Object(),connection);}
 boolean admit(Travel travel,FinancialOutcomeBindings.Scope current){owner.travel=null;return bridge.continueBottle(owner,travel,original,current,new BottleFinancialContinuation(owner,travel,original,current));}
 @Test void receivedButWaitingWorldDoesNotEraseOriginalActivation(){var t=travel();owner.travel=t;var current=replacement();scope.set(current);bridge.poll(100);bridge.poll(200);assertTrue(admit(t,current));owner.outcomes.add(new FinancialOutcomeBindings.Outcome(UUID.randomUUID().toString(),true,null,current));bridge.poll(201);assertEquals(1,sink.get().ids.size());}
 @Test void collectorReplacementDuringWaitPreservesSeenIdsUntilExactAdmission(){var oldId=UUID.randomUUID().toString();owner.outcomes.add(new FinancialOutcomeBindings.Outcome(oldId,true,null,original));bridge.poll(1);var t=travel();owner.travel=t;var current=replacement();scope.set(current);sink.set(new Sink());bridge.poll(100);assertTrue(admit(t,current));bridge.poll(101);assertTrue(sink.get().ids.isEmpty(),"Prior purchase cannot be recredited to the replacement collector");}
 @Test void wrongConnectionStopOrExpiryCannotJoinOldActivation(){for(int which=0;which<3;which++){var harness=new BottleFinancialPendingTransitTest();var t=harness.travel();harness.owner.travel=t;var next=harness.replacement();if(which==0)next=new FinancialOutcomeBindings.Scope(next.player(),next.world(),new Object());harness.scope.set(next);if(which==1)harness.manager.disable(harness.owner.id());harness.bridge.poll(which==2?501:100);harness.scope.set(harness.replacement());assertFalse(harness.admit(t,harness.scope.get()));}}
 @Test void replacedPermitAndClockRollbackCannotRetainOriginalActivation(){for(boolean rollback:new boolean[]{false,true}){var harness=new BottleFinancialPendingTransitTest();var t=harness.travel();harness.owner.travel=t;var next=harness.replacement();harness.scope.set(next);harness.bridge.poll(200);if(!rollback)harness.owner.travel=harness.travel();harness.bridge.poll(rollback?199:201);assertFalse(harness.admit(t,next));}}
 @Test void originalScopeMismatchNeverGrantsPassiveHold(){var wrong=new Travel(1,new Object(),original.world(),connection,UUID.randomUUID(),new Landing("hub",GameArea.HUB,"minecraft:overworld",new Vec3(.5,70,.5)),3,0,500,List.of());owner.travel=wrong;var current=replacement();scope.set(current);bridge.poll(100);owner.travel=null;assertFalse(bridge.continueBottle(owner,wrong,original,current,new BottleFinancialContinuation(owner,wrong,new FinancialOutcomeBindings.Scope(wrong.player(),wrong.world(),connection),current)));}
}
