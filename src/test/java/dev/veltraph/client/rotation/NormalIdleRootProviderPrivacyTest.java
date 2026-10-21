package dev.veltraph.client.rotation;
import dev.veltraph.client.VeltraphBootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleRootProviderPrivacyTest {
 @Test void publicNullCallbackCannotConstructTheActualRootProviderOrCause(){assertEquals(0,VeltraphBootstrap.RootRotationAuthorityProvider.class.getConstructors().length);assertEquals(0,VeltraphBootstrap.RootRotationRead.class.getConstructors().length);assertEquals(0,VeltraphBootstrap.RootNoOwnerObservation.class.getConstructors().length);}
}
