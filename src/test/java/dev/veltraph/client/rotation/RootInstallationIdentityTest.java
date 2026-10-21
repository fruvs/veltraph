package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual equality kernel used by private installRoot handle, not a forged native provider/permission. */
class RootInstallationIdentityTest {
 final Object original=new Object(),client=new Object(),provider=new Object();
 RootInstallationIdentity.State captured(){return new RootInstallationIdentity.State(original,client,provider,false);}
 @Test void constructorThreadFalseCaptureBindsOnlyAfterActualNativePhase(){assertFalse(RootInstallationIdentity.sameOriginal(captured(),new RootInstallationIdentity.State(original,client,provider,false)));assertTrue(RootInstallationIdentity.sameOriginal(captured(),new RootInstallationIdentity.State(original,client,provider,true)));}
 @Test void sameProviderReplacementBeforeNativePhaseNeverBecomesOriginal(){var later=new RootInstallationIdentity.State(new Object(),client,provider,true);assertFalse(RootInstallationIdentity.sameOriginal(captured(),later));assertFalse(RootInstallationIdentity.sameOriginal(captured(),later));}
 @Test void differentProviderOrClientCannotAcquireOriginalAffiliation(){assertFalse(RootInstallationIdentity.sameOriginal(captured(),new RootInstallationIdentity.State(original,client,new Object(),true)));assertFalse(RootInstallationIdentity.sameOriginal(captured(),new RootInstallationIdentity.State(original,new Object(),provider,true)));}
 @Test void missingInstallationStaysUnknownNoLateGetterRepair(){assertFalse(RootInstallationIdentity.sameOriginal(new RootInstallationIdentity.State(null,client,provider,false),new RootInstallationIdentity.State(original,client,provider,true)));assertFalse(RootInstallationIdentity.sameOriginal(captured(),null));}
 @Test void repeatedConstructorAndNativeChecksDoNotReplaceCapturedIdentity(){var captured=captured();for(int i=0;i<12;i++)assertTrue(RootInstallationIdentity.sameOriginal(captured,new RootInstallationIdentity.State(original,client,provider,true)));assertSame(original,captured.installation());assertFalse(captured.nativeThread());}
 @Test void replacingThenRestoringExactOriginalHandleIsIdentityNotEpochAdoption(){var captured=captured();assertFalse(RootInstallationIdentity.sameOriginal(captured,new RootInstallationIdentity.State(new Object(),client,provider,true)));assertTrue(RootInstallationIdentity.sameOriginal(captured,new RootInstallationIdentity.State(original,client,provider,true)));}
}
