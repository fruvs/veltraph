package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Opaque tuple data only: no healthy native Episode, Screen, reset receipt, tracker, key history or completion is constructed. */
final class NativeBirthLoadingUpdateOrderTest {
 private final Object owner=new Object(),tracker=new Object(),player=new Object(),world=new Object(),screen=new Object(),reason=new Object();
 private NativeBirthLoadingUpdateOrder fresh(){return new NativeBirthLoadingUpdateOrder(owner,tracker,player,world);}
 private NativeBirthLoadingUpdateOrder started(){var o=fresh();assertTrue(o.startReturned(tracker,player,world,true));return o;}
 private NativeBirthLoadingUpdateOrder screened(){var o=started();assertTrue(o.screenReturned(screen));return o;}
 private NativeBirthLoadingUpdateOrder entered(){var o=screened();assertTrue(o.updateEntered(screen,tracker,reason));return o;}
 @Test void dataOriginalStartScreenUpdateCanBeConsumedOnlyOnce(){var o=entered();assertTrue(o.updateReturned(screen,tracker,true));assertTrue(o.consume(screen,tracker));assertFalse(o.consume(screen,tracker));}
 @Test void absentNativeTupleCannotStart(){assertFalse(new NativeBirthLoadingUpdateOrder(null,tracker,player,world).startReturned(tracker,player,world,true));}
 @Test void screenBeforeOriginalStartRefuses(){var o=fresh();assertFalse(o.screenReturned(screen));assertFalse(o.startReturned(tracker,player,world,true));}
 @Test void foreignTrackerDoesNotRearm(){var o=fresh();assertFalse(o.startReturned(new Object(),player,world,true));assertFalse(o.startReturned(tracker,player,world,true));}
 @Test void foreignWorldDoesNotRearm(){var o=fresh();assertFalse(o.startReturned(tracker,player,new Object(),true));assertFalse(o.startReturned(tracker,player,world,true));}
 @Test void originalStartErrorRefusesNormalRetry(){var o=fresh();assertFalse(o.startReturned(tracker,player,world,false));assertFalse(o.startReturned(tracker,player,world,true));}
 @Test void secondScreenReadRefusesUpdate(){var o=screened();assertFalse(o.screenReturned(screen));assertFalse(o.updateEntered(screen,tracker,reason));}
 @Test void foreignUpdateScreenRefusesReplay(){var o=screened();assertFalse(o.updateEntered(new Object(),tracker,reason));assertFalse(o.updateEntered(screen,tracker,reason));}
 @Test void nestedUpdateClaimRefusesCompletion(){var o=entered();assertFalse(o.updateEntered(screen,tracker,reason));assertFalse(o.updateReturned(screen,tracker,true));}
 @Test void updateErrorDoesNotBecomeNormalReturn(){var o=entered();assertFalse(o.updateReturned(screen,tracker,false));assertFalse(o.updateReturned(screen,tracker,true));}
 @Test void consumeBeforeOriginalReturnRefusesLaterCompletion(){var o=entered();assertFalse(o.consume(screen,tracker));assertFalse(o.updateReturned(screen,tracker,true));}
 @Test void explicitRefusalInvalidatesOtherwiseCompleteData(){var o=entered();assertTrue(o.updateReturned(screen,tracker,true));o.refuse();assertFalse(o.consume(screen,tracker));}
 @Test void lastSemanticCallbackChangingEarlierVersionIsRejectedByRawVeto(){var earlier=new dev.veltraph.client.rotation.NormalIdleInputVersion();Object retained=earlier.snapshot();assertTrue(dev.veltraph.client.rotation.NormalIdleInputVersion.retainedSnapshotCurrent(retained));Runnable laterSemanticCallback=()->{earlier.begin(null,null,true);earlier.end(false);};laterSemanticCallback.run();assertFalse(dev.veltraph.client.rotation.NormalIdleInputVersion.retainedSnapshotCurrent(retained));}
 @Test void rawVersionRejectsInFlightAndUnknownHistoryWithoutSeeding(){var history=new dev.veltraph.client.rotation.NormalIdleInputVersion();Object retained=history.snapshot();history.begin(null,null,true);assertFalse(dev.veltraph.client.rotation.NormalIdleInputVersion.retainedSnapshotCurrent(retained));history.end(false);history.invalidate();assertFalse(dev.veltraph.client.rotation.NormalIdleInputVersion.retainedSnapshotCurrent(retained));assertFalse(dev.veltraph.client.rotation.NormalIdleInputVersion.retainedSnapshotCurrent(new Object()));}
 @Test void exactNativeKeyClassesCannotDispatchBindingMapHashOrEqualsCallbacks()throws Exception{for(Class<?> type:new Class<?>[]{net.minecraft.client.KeyMapping.class,net.minecraft.client.ToggleKeyMapping.class}){assertEquals(Object.class,type.getMethod("hashCode").getDeclaringClass());assertEquals(Object.class,type.getMethod("equals",Object.class).getDeclaringClass());}}
}
