package dev.veltraph.client.core;

import dev.veltraph.client.game.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant;
import dev.veltraph.client.testutil.FakeChatService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MaintenancePreReservationGuardTest {
    private static class Helper extends AbstractModule implements Participant {
        Optional<String> readiness=Optional.empty(); boolean throwsReadiness; int checks,fullChecks,enables;
        Helper(){super("roof_helper","Roof helper",ModuleCategory.FARMING);}
        public Optional<String> preReservationReadiness(){checks++;if(throwsReadiness)throw new IllegalStateException("Missing native capability");return readiness;}
        public Optional<String> preStartCheck(){fullChecks++;return readiness;}
        public void onEnable(){enables++;}
    }
    private static class Setup {
        final ModuleManager manager=new ModuleManager();final Helper helper=new Helper();final FakeChatService chat=new FakeChatService();
        final ModuleActivationGuard guard=new ModuleActivationGuard(manager,new FixedGameContextService(new GameContext(true,true,GameArea.GARDEN)),chat);
        int reservations,captures,suspensions,inputs,before;java.util.function.BooleanSupplier deferred;
        Setup(){manager.register(helper);guard.beforeActivation(module->before++);guard.activationTransaction((module,activation)->{reservations++;captures++;suspensions++;inputs++;deferred=activation;return true;});}
        void assertUntouched(){assertEquals(0,reservations);assertEquals(0,captures);assertEquals(0,suspensions);assertEquals(0,inputs);assertEquals(0,before);assertEquals(0,helper.fullChecks);assertFalse(helper.isEnabled());}
    }
    @Test void missingCapabilityRefusesBeforeAnyReservationOrInput(){var s=new Setup();s.helper.readiness=Optional.of("Configured roof Etherwarp tool is missing");assertFalse(s.guard.start(s.helper));s.assertUntouched();assertTrue(s.chat.last().contains("tool is missing"));}
    @Test void unavailableNativeCapabilityFailsClosedBeforeCapture(){var s=new Setup();s.helper.throwsReadiness=true;assertFalse(s.guard.start(s.helper));s.assertUntouched();}
    @Test void repeatedRefusalCannotSuspendOrReserve(){var s=new Setup();s.helper.readiness=Optional.of("Roof permission disabled");assertFalse(s.guard.start(s.helper));assertFalse(s.guard.start(s.helper));s.assertUntouched();assertEquals(2,s.helper.checks);}
    @Test void readyCapabilityRetainsDeferredFullGuardAndFreshDriftRefusal(){var s=new Setup();assertTrue(s.guard.start(s.helper));assertEquals(1,s.reservations);assertEquals(1,s.helper.checks);assertEquals(0,s.helper.fullChecks);assertFalse(s.helper.isEnabled());s.helper.readiness=Optional.of("Tool changed before commit");assertFalse(s.deferred.getAsBoolean());assertEquals(1,s.helper.fullChecks);assertEquals(0,s.helper.enables);}
    @Test void defaultParticipantKeepsOriginalTransactionAndFullReadiness(){var manager=new ModuleManager();class DefaultHelper extends AbstractModule implements Participant {int full;DefaultHelper(){super("ordinary","Ordinary",ModuleCategory.FARMING);}public Optional<String> preStartCheck(){full++;return Optional.empty();}}var helper=new DefaultHelper();manager.register(helper);var guard=new ModuleActivationGuard(manager,new FixedGameContextService(new GameContext(true,true,GameArea.GARDEN)),new FakeChatService());int[] transactions={0};guard.activationTransaction((module,activation)->{transactions[0]++;return activation.getAsBoolean();});assertTrue(guard.start(helper));assertEquals(1,transactions[0]);assertEquals(1,helper.full);assertTrue(helper.isEnabled());}
}
