package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.garden.pests.PestSprayonatorBindings;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant;
import dev.veltraph.client.testutil.FakeChatService;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Guard protocol replay; native inventory/geometry witnesses are modeled, not a native run. */
class SprayonatorPreReservationNativeTraceTest {
    static final Vec3 ORIGIN=new Vec3(.79,70,-96.12);
    static final PestBindings.Entity TRAP=new PestBindings.Entity("minecraft:armor_stand","Spray Trap","");
    static final String REFUSAL="Nearby source trap requires a loaded trap-free escape landing within ±10 and Y≥66";

    /** Resolve the real parent's admission binding while avoiding Minecraft construction in plain JUnit. */
    static boolean actualParentOverridesCapability(){
        try{return McPestSprayonatorMaintenanceModule.class.getMethod("preReservationReadiness").getDeclaringClass()==McPestSprayonatorMaintenanceModule.class;}
        catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
    }
    static final class ReplayParent extends AbstractModule implements Participant {
        Vec3 landing=new Vec3(30.5,70,-96.5);boolean support=true,trapPresent=true,foreignCursor;
        int capabilityReads,fullReads,enables;
        ReplayParent(){super("spray_admission_trace","Spray admission trace",ModuleCategory.INTERMEDIARY);}
        Optional<String> freshCapability(){
            capabilityReads++;
            if(foreignCursor)return Optional.of("Closed live Garden owner and configured contest guard required");
            var escape=new PestSprayonatorBindings.Escape(TRAP,landing,.5);
            return trapPresent&&(!escape.sourceCandidate(ORIGIN)||!support)?Optional.of(REFUSAL):Optional.empty();
        }
        public Optional<String> preReservationReadiness(){return actualParentOverridesCapability()?freshCapability():Participant.super.preReservationReadiness();}
        public Optional<String> preStartCheck(){fullReads++;return freshCapability();}
        public void onEnable(){enables++;}
    }
    static final class Setup {
        final ReplayParent parent=new ReplayParent();final ModuleManager manager=new ModuleManager();final FakeChatService chat=new FakeChatService();
        final ModuleActivationGuard guard=new ModuleActivationGuard(manager,new FixedGameContextService(new GameContext(true,true,GameArea.GARDEN)),chat);
        int captures,reservations,suspensions,inputs,menus,uses,beforeActivation;boolean farmEnabled=true,farmSuspended;Object engine=new Object();java.util.function.BooleanSupplier deferred;
        Setup(){manager.register(parent);guard.beforeActivation(module->beforeActivation++);guard.activationTransaction((module,activation)->{captures++;reservations++;suspensions++;inputs++;farmSuspended=true;deferred=activation;return true;});}
        void assertHealthyUntouched(Object originalEngine){assertEquals(0,captures);assertEquals(0,reservations);assertEquals(0,suspensions);assertEquals(0,inputs);assertEquals(0,menus);assertEquals(0,uses);assertEquals(0,beforeActivation);assertEquals(0,parent.fullReads);assertFalse(parent.isEnabled());assertTrue(farmEnabled);assertFalse(farmSuspended);assertSame(originalEngine,engine);}
    }
    @Test void observedOutsideCubeMustRefuseBeforeActualGuardReservation(){
        var s=new Setup();Object engine=s.engine;
        assertFalse(s.guard.start(s.parent),"Native O guard returned true/PREPARED before deferred preStart; missing actual parent override still reserves invalid escape");
        s.assertHealthyUntouched(engine);assertEquals(1,s.parent.capabilityReads);assertTrue(s.chat.last().contains("within ±10"));
    }
    @Test void unsupportedLandingAndForeignCursorRefuseWithoutFarmOrNativeActions(){
        var s=new Setup();s.parent.landing=new Vec3(9.5,70,-96.5);s.parent.support=false;assertFalse(s.guard.start(s.parent));s.assertHealthyUntouched(s.engine);
        s=new Setup();s.parent.landing=new Vec3(9.5,70,-96.5);s.parent.foreignCursor=true;assertFalse(s.guard.start(s.parent));s.assertHealthyUntouched(s.engine);
    }
    @Test void freshChangedBindingIsRecheckedBeforeAnySecondReservation(){
        var s=new Setup();assertFalse(s.guard.start(s.parent));s.parent.landing=new Vec3(9.5,70,-96.5);s.parent.support=false;assertFalse(s.guard.start(s.parent));s.assertHealthyUntouched(s.engine);assertEquals(2,s.parent.capabilityReads);
    }
    @Test void validCapabilityKeepsDeferredFullCheckAndDriftRefusal(){
        var s=new Setup();s.parent.landing=new Vec3(9.5,70,-96.5);assertTrue(s.guard.start(s.parent));assertEquals(1,s.reservations);assertEquals(0,s.parent.fullReads);assertFalse(s.parent.isEnabled());
        s.parent.landing=new Vec3(30.5,70,-96.5);assertFalse(s.deferred.getAsBoolean());assertEquals(1,s.parent.fullReads);assertEquals(0,s.parent.enables);assertEquals(0,s.menus);assertEquals(0,s.uses);
    }
}
