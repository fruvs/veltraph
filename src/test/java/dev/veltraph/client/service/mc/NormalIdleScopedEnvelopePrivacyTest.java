package dev.veltraph.client.service.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleScopedEnvelopePrivacyTest {
 @Test void noActualScopedNativeInvocationMeansNoOwnedHookClaim(){assertNull(McHeldKeys.claimNativeInput(null,false,false));assertNull(McHeldKeys.claimNativeInput(null,false,true));}
 @Test void callersCannotConstructAnOwnedNativeInvocationOrHardwareSnapshot(){assertEquals(0,McHeldKeys.NativeInputInvocation.class.getConstructors().length);assertEquals(0,NormalIdleHardwareSnapshot.class.getConstructors().length);}
}
