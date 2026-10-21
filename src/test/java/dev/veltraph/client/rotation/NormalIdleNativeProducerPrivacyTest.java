package dev.veltraph.client.rotation;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleNativeProducerPrivacyTest {
 @Test void onlyActualNativeOperationCanConstructCancelCertificate(){assertEquals(0,McOwnedRotation.NormalOperation.class.getConstructors().length);assertEquals(0,McOwnedRotation.NormalIdleCertificate.class.getConstructors().length);}
 @Test void rootDropCannotBeConstructedFromNullBooleanOrNumbers(){assertEquals(0,VeltraphBootstrap.RootInputDropCall.class.getConstructors().length);assertEquals(0,McOwnedRotation.NormalRootInputDrop.class.getConstructors().length);}
 @Test void onlyActualNormalTokenCanConstructPermitOrWitness(){assertEquals(0,LegacyTravelPublicationLease.NormalIdlePermit.class.getConstructors().length);assertEquals(0,LegacyTravelPublicationLease.NormalIdleWitness.class.getConstructors().length);}
}
