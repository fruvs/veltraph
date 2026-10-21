package dev.veltraph.client.navigation;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.rewarp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Real backing-store/core/lease comparators used by the native factory; no native permission manufactured here. */
class OwnedUseGameplayBoundaryTest {
 @Test void actualKnownPolicyAbaRefusesFinalStoreLeaf(){var c=new MapConfigService();var old=c.storedStateSnapshot();double before=c.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);c.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,before+1);c.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,before);assertFalse(old.current());}
 @Test void unknownMutableStoredValueHasNoScalarCapsule(){var c=new MapConfigService();var key=new ConfigKey<Object>("unknown_owned_use_value",new Object());c.set(key,new java.util.ArrayList<>());assertNull(c.storedStateSnapshot());}
 @Test void sourceCallbackEnablingAnotherControllerRevokesActualCoreSnapshot(){var m=new ModuleManager();var parent=new Actor("parent");var other=new Actor("other");m.register(parent);m.register(other);parent.setEnabled(true);var before=m.lifecycleSnapshot();Runnable callback=()->other.setEnabled(true);callback.run();assertFalse(m.lifecycleCurrent(before));}
 @Test void lifecycleAbaRevokesEvenWhenBothActorsFinishInOriginalState(){var m=new ModuleManager();var parent=new Actor("parent");var other=new Actor("other");m.register(parent);m.register(other);parent.setEnabled(true);var before=m.lifecycleSnapshot();other.setEnabled(true);other.setEnabled(false);assertFalse(m.lifecycleCurrent(before));}
 @Test void sameEnabledRosterWithoutMutationRetainsRealCoreLeaf(){var m=new ModuleManager();var parent=new Actor("parent");m.register(parent);parent.setEnabled(true);var before=m.lifecycleSnapshot();assertTrue(before.known());assertTrue(m.lifecycleCurrent(before));}
 @Test void currentConfigValueMutationRevokesAllKeysNotJustTravelRevision(){var c=new MapConfigService();var old=c.storedStateSnapshot();c.set(PestPhilipConfig.PEST_PHILIP_ENABLED,!c.get(PestPhilipConfig.PEST_PHILIP_ENABLED));assertFalse(old.current());}
 @Test void privateAdmissionAbaIsNotHiddenBySamePwcControl(){Object leg=new Object(),identity=new Object(),root=new Object(),p=new Object(),w=new Object(),connection=new Object(),store=new Object();var before=new LegacyPublicationSnapshotIdentity.State(leg,identity,root,p,w,connection,store,3,8,12,14);var after=new LegacyPublicationSnapshotIdentity.State(leg,identity,root,p,w,connection,store,5,8,12,14);assertFalse(LegacyPublicationSnapshotIdentity.same(before,after));}
 @Test void nativeCameraPublicationRevokesReservationWithSameEpoch(){Object leg=new Object(),identity=new Object(),root=new Object(),p=new Object(),w=new Object(),connection=new Object(),store=new Object();var before=new LegacyPublicationSnapshotIdentity.State(leg,identity,root,p,w,connection,store,3,8,12,14);var after=new LegacyPublicationSnapshotIdentity.State(leg,identity,root,p,w,connection,store,3,8,12,15);assertFalse(LegacyPublicationSnapshotIdentity.same(before,after));}
 @Test void arbitraryMutableClosureNeverBecomesKnownPolicyAndIsNotInvoked(){var calls=new int[1];java.util.function.Supplier<String> unknown=()->{calls[0]++;return "same signature";};assertFalse(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.hasKnownGameplaySettings(unknown));assertEquals(0,calls[0]);}
 @Test void copiedSignatureSupplierIsStillUnqualified(){assertFalse(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.hasKnownGameplaySettings(()->"production-shaped signature"));assertFalse(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.hasKnownGameplaySettings(null));}
 private static final class Actor extends AbstractModule {Actor(String id){super(id,id,ModuleCategory.INTERMEDIARY);}@Override public void onEnable(){}@Override public void onDisable(){}@Override public void onTick(){}}
}
