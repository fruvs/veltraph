package dev.veltraph.client.core;

import dev.veltraph.client.game.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant;
import dev.veltraph.client.testutil.FakeChatService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual guard re-entry order; native issued-token authority remains in the coordinator, not this fixture. */
class DeferredMaintenanceReadinessOrderTest {
    private static final class Helper extends AbstractModule implements Participant {
        boolean arming=true,suspended,prepared,tokenCurrent=true,contextCurrent=true,fullReady=true;
        int initialChecks,preparedChecks,fullChecks,enables;
        Helper(){super("greenhouse_guard_order","Private deferred helper",ModuleCategory.INTERMEDIARY);}
        public Optional<String> preReservationReadiness(){
            if(prepared&&tokenCurrent&&contextCurrent){preparedChecks++;return fullReady?Optional.empty():Optional.of("Current plan unavailable");}
            initialChecks++;return arming&&!suspended&&contextCurrent?Optional.empty():Optional.of("Initial active farm required");
        }
        public Optional<String> preStartCheck(){fullChecks++;return tokenCurrent&&contextCurrent&&fullReady?Optional.empty():Optional.of("Full readiness changed");}
        public void onEnable(){enables++;}
    }
    private static final class Setup {
        final ModuleManager manager=new ModuleManager();final Helper helper=new Helper();final FakeChatService chat=new FakeChatService();
        final ModuleActivationGuard guard=new ModuleActivationGuard(manager,new FixedGameContextService(new GameContext(true,true,GameArea.GARDEN)),chat);
        int reservations;boolean dispatching;
        Setup(){manager.register(helper);guard.activationTransaction((module,activation)->{
            if(dispatching)return activation.getAsBoolean();reservations++;helper.prepared=true;helper.suspended=true;return true;
        });}
        boolean reserve(){boolean result=guard.start(helper);helper.arming=false;return result;}
        boolean deferred(){dispatching=true;try{return guard.start(helper);}finally{dispatching=false;}}
    }
    @Test void deferredGuardRechecksParticipantBeforeFullReadinessAndThenEnables(){
        var s=new Setup();assertTrue(s.reserve());assertFalse(s.helper.isEnabled());assertEquals(1,s.reservations);assertEquals(1,s.helper.initialChecks);
        assertTrue(s.deferred());assertEquals(1,s.helper.preparedChecks);assertEquals(1,s.helper.fullChecks);assertEquals(1,s.helper.enables);assertEquals(1,s.reservations);
    }
    @Test void absentPreparedTokenDoesNotReopenInitialArmingForSuspendedFarm(){var s=new Setup();assertTrue(s.reserve());s.helper.prepared=false;assertFalse(s.deferred());assertEquals(0,s.helper.fullChecks);assertEquals(0,s.helper.enables);}
    @Test void changedTokenRefusesBeforeFullReadinessOrEnable(){var s=new Setup();assertTrue(s.reserve());s.helper.tokenCurrent=false;assertFalse(s.deferred());assertEquals(0,s.helper.fullChecks);assertEquals(0,s.helper.enables);}
    @Test void changedNativeContextRefusesBeforeFullReadinessOrEnable(){var s=new Setup();assertTrue(s.reserve());s.helper.contextCurrent=false;assertFalse(s.deferred());assertEquals(0,s.helper.fullChecks);assertEquals(0,s.helper.enables);}
    @Test void preparedCapabilityDoesNotBypassCurrentPlanReadiness(){var s=new Setup();assertTrue(s.reserve());s.helper.fullReady=false;assertFalse(s.deferred());assertEquals(1,s.helper.preparedChecks);assertEquals(0,s.helper.enables);}
}
