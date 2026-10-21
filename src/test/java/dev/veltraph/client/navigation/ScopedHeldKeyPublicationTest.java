package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScopedHeldKeyPublicationTest {
 static final class F implements ScopedHeldKeyPublication.Effects {
  long generation=1,serial=4,admission=7;Object parent=new Object(),capturedParent=parent;boolean down,toggle,restore=true;
  int observes,sets,resets,reads;Runnable observer=()->{},read=()->{},setter=()->{};
  public void observe(){observes++;observer.run();}public boolean current(){return generation==1&&serial==4&&admission==7&&parent==capturedParent;}
  public boolean down(){reads++;read.run();return down;}public void setDown(boolean held){sets++;if(!toggle)down=held;else if(held)down=!down;setter.run();}
  public boolean toggle(){return toggle;}public void clearRestore(){resets++;restore=false;}
 }
 @Test void observerReplacesScopeBeforePressAndNoKeyIsMutated(){var f=new F();f.observer=()->f.parent=new Object();assertFalse(ScopedHeldKeyPublication.apply(true,f));assertEquals(1,f.observes);assertEquals(0,f.sets);assertFalse(f.down);}
 @Test void observerNewSameParentRequestBeforeReleaseCannotTouchHeldForeignKey(){var f=new F();f.down=true;f.observer=()->f.generation++;assertFalse(ScopedHeldKeyPublication.apply(false,f));assertEquals(0,f.sets);assertTrue(f.down);}
 @Test void observerRawSameEpochPlanSerialReplacementRefusesPress(){var f=new F();f.observer=()->f.serial++;assertFalse(ScopedHeldKeyPublication.apply(true,f));assertEquals(0,f.sets);}
 @Test void observerNoEffectAdmissionAbaRefusesNativeSetter(){var f=new F();f.observer=()->f.admission+=2;assertFalse(ScopedHeldKeyPublication.apply(true,f));assertEquals(0,f.sets);}
 @Test void downReadCallbackReplacementIsCheckedImmediatelyBeforeSetter(){var f=new F();f.read=()->f.generation++;assertFalse(ScopedHeldKeyPublication.apply(true,f));assertEquals(0,f.sets);}
 @Test void replacementAfterFirstToggleSetterRefusesFallbackAndReset(){var f=new F();f.toggle=true;f.down=true;f.setter=()->f.generation++;assertFalse(ScopedHeldKeyPublication.apply(false,f));assertEquals(1,f.sets);assertEquals(0,f.resets);assertTrue(f.down);}
 @Test void observerReplacementBeforeAlreadyUpToggleResetCannotClearForeignRestore(){var f=new F();f.toggle=true;f.observer=()->f.serial++;assertFalse(ScopedHeldKeyPublication.apply(false,f));assertEquals(0,f.sets);assertEquals(0,f.resets);assertTrue(f.restore);}
 @Test void ownedToggleReleasePreservesAbsoluteFallbackAndRestoreClear(){var f=new F();f.toggle=true;f.down=true;assertTrue(ScopedHeldKeyPublication.apply(false,f));assertEquals(2,f.sets);assertFalse(f.down);assertEquals(1,f.resets);assertFalse(f.restore);assertEquals(1,f.observes);}
 @Test void ownedNormalPressPublishesOnceAfterObserver(){var f=new F();assertTrue(ScopedHeldKeyPublication.apply(true,f));assertEquals(1,f.observes);assertEquals(1,f.sets);assertTrue(f.down);}
 @Test void everyOneOfNineObserverReplacementsPreventsActualMutation(){for(int i=0;i<9;i++){var f=new F();f.down=true;f.observer=()->f.generation++;assertFalse(ScopedHeldKeyPublication.apply(false,f));assertEquals(0,f.sets);assertTrue(f.down);}}
 @Test void refusalCallbackCanReturnTrueButFinalNativeLeafRejectsReentry(){var f=new F();assertFalse(ScopedHeldKeyPublication.afterRefusal(()->{f.admission+=2;return true;},f::current));assertEquals(0,f.sets);}
 @Test void nativeAttemptPresenceAfterForeignCallbackCannotHideOutstandingMutation(){var s=new LegacyTravelPublicationIdentity.State(new Object(),new Object(),null,null,new Object(),new Object(),new Object(),new Object(),1,1,new Object(),1,1,1,new Object(),true,true,true);var parent=s.parent();s=new LegacyTravelPublicationIdentity.State(s.root(),parent,parent,parent,s.player(),s.world(),s.connection(),s.config(),1,1,s.service(),1,1,1,s.activeLeg(),true,true,true);var lease=LegacyTravelPublicationIdentity.Lease.capture(s);assertNotNull(lease);lease.inputNativeAttemptRecorded();assertTrue(lease.keysPublished());var changed=new LegacyTravelPublicationIdentity.State(s.root(),parent,parent,parent,s.player(),s.world(),s.connection(),s.config(),1,1,s.service(),2,1,1,s.activeLeg(),true,true,true);assertFalse(lease.cleanupCurrent(changed));assertTrue(lease.keysPublished());}
}
