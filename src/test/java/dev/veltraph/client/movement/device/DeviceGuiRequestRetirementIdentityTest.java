package dev.veltraph.client.movement.device;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeviceGuiRequestRetirementIdentityTest {
 private final Object source=new Object();
 private DeviceGuiRequestRetirementIdentity.Input actual(){return new DeviceGuiRequestRetirementIdentity.Input(source,source,156,156,157,2493,2493,2494,2494,2494,2494,1325,1325);}
 @Test void actualGUIRequestRetirementHasNoAdditionalCameraEpoch(){assertTrue(DeviceGuiRequestRetirementIdentity.valid(actual()));}
 @Test void copiedSourceShapeCannotServeAsExactHandedPointer(){var i=actual();assertFalse(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,new Object(),156,156,157,2493,2493,2494,2494,2494,2494,1325,1325)));}
 @Test void standardNativeCancelPlusOneEpochIsNotGUIRequestRetirement(){assertFalse(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,source,156,156,157,2493,2493,2494,2495,2494,2495,1325,1325)));}
 @Test void laterActualGUIDescendantShapePreservesImmutableRetirementEndpoint(){assertTrue(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,source,156,156,157,2493,2493,2494,2494,2494,2501,1325,1325)));}
 @Test void differentRequestGenerationRefuses(){assertFalse(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,source,156,158,159,2493,2493,2494,2494,2494,2494,1325,1325)));}
 @Test void foreignPublicationWithSamePoseRefuses(){assertFalse(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,source,156,156,157,2493,2493,2494,2494,2494,2494,1325,1326)));}
 @Test void unknownOriginalGUIEpochRefuses(){assertFalse(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,source,156,156,157,2493,2494,2495,2495,2495,2495,1325,1325)));}
 @Test void exhaustedGenerationCannotClaimRetirement(){assertFalse(DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,source,Long.MAX_VALUE,Long.MAX_VALUE,Long.MIN_VALUE,2493,2493,2494,2494,2494,2494,1325,1325)));}
}
