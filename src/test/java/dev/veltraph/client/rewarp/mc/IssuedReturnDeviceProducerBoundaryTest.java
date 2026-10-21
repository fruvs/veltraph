package dev.veltraph.client.rewarp.mc;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Authority-boundary tests only. These cannot substitute for original native transit controls. */
final class IssuedReturnDeviceProducerBoundaryTest {
    private static final Class<?> REQUEST=McSavedAnchorRecovery.IssuedReturnTravelRequest.class;
    private static final Class<?> TRANSIT=McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit.class;
    private static final Class<?> CALL=McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall.class;
    @Test void noPublicOrPackageConstructorCanManufactureAuthority(){
        for(var type:new Class<?>[]{REQUEST,TRANSIT,CALL}){
            assertEquals(1,type.getDeclaredConstructors().length);
            for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            assertFalse(Modifier.isStatic(type.getModifiers()),"must retain actual owner instance");
        }
    }
    @Test void canonicalSourceAndPrivateOwnerCannotBeReassigned() throws Exception {
        for(var name:new String[]{"request","admission","originalInstruction","originalSlot"}){
            var field=TRANSIT.getDeclaredField(name);
            assertTrue(Modifier.isPrivate(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));
        }
        for(var name:new String[]{"transit","originalInstruction","inventory","expected","requested","cleanup","thread"}){
            var field=CALL.getDeclaredField(name);
            assertTrue(Modifier.isPrivate(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));
        }
    }
    @Test void issueAndSelectionLifetimeAreNotPubliclyWritable() throws Exception {
        for(var name:new String[]{"issue","ownedSource","ackTransit"})assertTrue(Modifier.isPrivate(TRANSIT.getDeclaredField(name).getModifiers()));
        assertTrue(Modifier.isPrivate(CALL.getDeclaredField("closed").getModifiers()));
        assertFalse(AutoCloseable.class.isAssignableFrom(CALL));
        assertTrue(Arrays.stream(CALL.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).noneMatch(m->m.getReturnType()==void.class));
    }
    @Test void transitMintAndSelectionExecuteRequireAuthenticAdmissionType() throws Exception {
        var admission=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission.class;
        var capture=REQUEST.getMethod("captureDeviceTransit",admission);assertEquals(TRANSIT,capture.getReturnType());
        var selection=REQUEST.getMethod("withDeviceSelection",admission,dev.veltraph.client.service.mc.McInventoryService.class,
            int.class,int.class,boolean.class,java.util.function.Function.class);
        assertEquals(1,selection.getTypeParameters().length);
        var bind=REQUEST.getMethod("bindDeviceIssue",admission,dev.veltraph.client.movement.device.DeviceUseReceipt.Issue.class,Object.class);
        assertEquals(boolean.class,bind.getReturnType());
        assertEquals(McSuspendedFarmReturnCoordinator.DeviceAckTransit.class,TRANSIT.getMethod("ackTransitIdentity").getReturnType());
    }
    @Test void finalEntryRequiresTypedCapsuleAndRetainsPrivateOriginalInstruction() throws Exception {
        var continuation=McSuspendedFarmReturnCoordinator.FinalEntryContinuation.class;
        var shared=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class;
        var goal=dev.veltraph.client.world.Vec3.class;
        assertEquals(boolean.class,REQUEST.getMethod("claimFinalEntryInstruction",continuation,Object.class,shared,goal,double.class).getReturnType());
        assertEquals(boolean.class,REQUEST.getMethod("currentFinalEntryInstructionCurrent",continuation,Object.class,shared,goal,double.class).getReturnType());
        assertTrue(Modifier.isPrivate(REQUEST.getDeclaredField("finalEntryContinuation").getModifiers()));
        assertTrue(Modifier.isPrivate(REQUEST.getDeclaredField("finalEntryInstruction").getModifiers()));
        assertEquals(boolean.class,TRANSIT.getMethod("terminalSourceFieldsCurrent",
            dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt.class).getReturnType());
        assertEquals(McSuspendedFarmReturnCoordinator.MovementView.class,TRANSIT.getMethod("terminalViewIdentity",
            dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt.class).getReturnType());
    }

}
