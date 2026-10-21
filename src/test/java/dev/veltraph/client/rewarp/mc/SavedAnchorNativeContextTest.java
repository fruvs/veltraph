package dev.veltraph.client.rewarp.mc;
import dev.veltraph.client.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SavedAnchorNativeContextTest {
    private static final class Owner extends AbstractModule {
        int roles;Runnable cleanup=()->{},after=()->{};
        @Override public void onDisable(){cleanup.run();}
        @Override protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt receipt){after.run();}
        void pause(){setState(MacroState.PAUSED);}
        Owner(){super("anchor_owner","Anchor owner",ModuleCategory.INTERNAL);}
        @Override public boolean ownsGameplayInput(){roles++;throw new AssertionError("A final stored lifecycle leaf must not invoke module roles");}
    }
    private record Setup(ModuleManager registry,Owner owner,SavedAnchorNativeContext.Scene scene){}
    private Setup setup(){var r=new ModuleManager();var o=new Owner();r.register(o);r.enable(o.id());return new Setup(r,o,new SavedAnchorNativeContext.Scene(r,r.lifecycleSnapshot(),4,new Object(),new Object(),new Object(),new Object(),new Object()));}
    private SavedAnchorNativeContext.Scene replace(SavedAnchorNativeContext.Scene s,Object player,Object world,Object listener,Object connection){return new SavedAnchorNativeContext.Scene(s.registry(),s.lifecycle(),s.generation(),s.target(),player,world,listener,connection);}
    @Test void exactRegisteredRootAndNativeObjectsRemainCurrent(){var x=setup();assertTrue(SavedAnchorNativeContext.current(x.scene(),x.scene()));assertEquals(0,x.owner().roles);}
    @Test void replacingOnlyListenerRefusesEvenWhenPlayerWorldAndNativeConnectionMatch(){var s=setup().scene();assertFalse(SavedAnchorNativeContext.current(s,replace(s,s.player(),s.world(),new Object(),s.nativeConnection())));}
    @Test void replacingOnlyUnderlyingConnectionRefusesEvenWithSameListener(){var s=setup().scene();assertFalse(SavedAnchorNativeContext.current(s,replace(s,s.player(),s.world(),s.listener(),new Object())));}
    @Test void changingEachNativeIdentityOrLosingItRefuses(){var s=setup().scene();for(var a:new SavedAnchorNativeContext.Scene[]{replace(s,new Object(),s.world(),s.listener(),s.nativeConnection()),replace(s,s.player(),new Object(),s.listener(),s.nativeConnection()),replace(s,null,s.world(),s.listener(),s.nativeConnection()),replace(s,s.player(),null,s.listener(),s.nativeConnection()),replace(s,s.player(),s.world(),null,s.nativeConnection()),replace(s,s.player(),s.world(),s.listener(),null)})assertFalse(SavedAnchorNativeContext.current(s,a));}
    @Test void actualOwnDisableRevokesOldRecoveryWithoutCallingVirtualRoles(){var x=setup();x.registry().disable(x.owner().id());assertFalse(SavedAnchorNativeContext.current(x.scene(),x.scene()));assertEquals(0,x.owner().roles);}
    @Test void disableAndReenableSameActualOwnerCannotAdoptTheOldLifecycle(){var x=setup();x.registry().disable(x.owner().id());x.registry().enable(x.owner().id());assertTrue(x.owner().isEnabled());assertFalse(SavedAnchorNativeContext.current(x.scene(),x.scene()));}
    @Test void registryReplacementWithSameIdsAndEnabledStateDoesNotGrantContinuation(){var x=setup();var y=setup();var s=x.scene();var replacement=new SavedAnchorNativeContext.Scene(y.registry(),y.scene().lifecycle(),s.generation(),s.target(),s.player(),s.world(),s.listener(),s.nativeConnection());assertFalse(SavedAnchorNativeContext.current(s,replacement));}
    @Test void newerPrivateJourneyOrTargetCannotAdoptAnEarlierCapture(){var s=setup().scene();assertFalse(SavedAnchorNativeContext.current(s,new SavedAnchorNativeContext.Scene(s.registry(),s.lifecycle(),5,s.target(),s.player(),s.world(),s.listener(),s.nativeConnection())));assertFalse(SavedAnchorNativeContext.current(s,new SavedAnchorNativeContext.Scene(s.registry(),s.lifecycle(),4,new Object(),s.player(),s.world(),s.listener(),s.nativeConnection())));}
    @Test void unboundContextsDoNotAuthorizeCleanupOrNativeEffects(){var s=setup().scene();assertFalse(SavedAnchorNativeContext.current(null,s));assertFalse(SavedAnchorNativeContext.current(s,null));var unbound=new SavedAnchorNativeContext.Scene(null,null,4,s.target(),s.player(),s.world(),s.listener(),s.nativeConnection());assertFalse(SavedAnchorNativeContext.current(unbound,unbound));}
    @Test void completedExactHelperDisableAllowsOnlyReadonlyArrivalAndKeepsOrdinaryMovementRefused(){
        var x=setup();x.owner().setEnabled(false);
        assertFalse(SavedAnchorNativeContext.current(x.scene(),x.scene()));
        assertTrue(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
        assertTrue(x.registry().completedOwnDisableFrom(x.scene().lifecycle(),x.owner()).orElseThrow().current());
        assertEquals(0,x.owner().roles);
    }
    @Test void inProgressAndPostReturnHookCannotGrantCompletedArrival(){
        var x=setup();int[] observed={0};
        x.owner().cleanup=()->{assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));observed[0]++;};
        x.owner().after=()->{assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));observed[0]++;};
        x.owner().setEnabled(false);assertEquals(2,observed[0]);
        assertTrue(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
    }
    @Test void throwingActualCleanupAndPublicRawCleanupCannotMintArrival(){
        var x=setup();x.owner().onDisable();
        assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
        x.owner().cleanup=()->{throw new IllegalStateException("actual cleanup failed");};
        assertThrows(IllegalStateException.class,()->x.owner().setEnabled(false));
        assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
    }
    @Test void pausedActorCannotSubstituteForNormallyReturnedRunningHelper(){
        var x=setup();x.owner().pause();x.owner().setEnabled(false);
        assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
    }
    @Test void completedDisableCannotAdoptReenabledActorOrSecondDisableABA(){
        var x=setup();x.owner().setEnabled(false);
        assertTrue(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
        x.owner().setEnabled(true);assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
        x.owner().setEnabled(false);assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
    }
    @Test void cleanupReentryOtherActorAndRosterChangesInvalidateExactHandoff(){
        for(int kind=0;kind<3;kind++){
            var x=setup();var other=new AbstractModule("other","Other",ModuleCategory.INTERNAL){};x.registry().register(other);other.setEnabled(true);
            var s=new SavedAnchorNativeContext.Scene(x.registry(),x.registry().lifecycleSnapshot(),4,new Object(),new Object(),new Object(),new Object(),new Object());
            final int change=kind;x.owner().cleanup=()->{if(change==0)other.setEnabled(false);else if(change==1)x.registry().register(new AbstractModule("new","New",ModuleCategory.INTERNAL){});else x.registry().leasedInputOwner(()->null);};
            x.owner().setEnabled(false);assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(s,s,x.owner()));
        }
    }
    @Test void completedReceiptCannotAuthorizeDifferentHelperNativeObjectsRegistryOrJourney(){
        var x=setup();x.owner().setEnabled(false);var s=x.scene();var y=setup();
        assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(s,s,y.owner()));
        assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(s,s,null));
        for(var changed:new SavedAnchorNativeContext.Scene[]{replace(s,new Object(),s.world(),s.listener(),s.nativeConnection()),replace(s,s.player(),new Object(),s.listener(),s.nativeConnection()),replace(s,s.player(),s.world(),new Object(),s.nativeConnection()),replace(s,s.player(),s.world(),s.listener(),new Object()),new SavedAnchorNativeContext.Scene(y.registry(),s.lifecycle(),4,s.target(),s.player(),s.world(),s.listener(),s.nativeConnection()),new SavedAnchorNativeContext.Scene(s.registry(),s.lifecycle(),5,s.target(),s.player(),s.world(),s.listener(),s.nativeConnection()),new SavedAnchorNativeContext.Scene(s.registry(),s.lifecycle(),4,new Object(),s.player(),s.world(),s.listener(),s.nativeConnection())})
            assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(s,changed,x.owner()));
    }
    @Test void staleCompletedReceiptRejectsOtherModuleChangeAfterItsNormalReturn(){
        var x=setup();x.owner().setEnabled(false);assertTrue(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
        x.registry().register(new AbstractModule("later","Later",ModuleCategory.INTERNAL){});
        assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
    }

    @Test void exactUnchangedActualPoseRemainsADataCheckWithoutLifecyclePermission(){
        var posture=new Object();var p=new SavedAnchorNativeContext.DisablePose(1,2,3,-89.5f,4,posture,0);
        assertTrue(SavedAnchorNativeContext.unchangedDisablePose(p,new SavedAnchorNativeContext.DisablePose(1,2,3,-89.5f,4,posture,0)));
        var x=setup();assertFalse(SavedAnchorNativeContext.arrivedAfterOwnDisable(x.scene(),x.scene(),x.owner()));
    }
    @Test void cleanupCannotMoveWithinTargetRadiusOrCameraWithinOriginalTolerance(){
        var posture=new Object();var p=new SavedAnchorNativeContext.DisablePose(1,2,3,-89.5f,4,posture,0);
        assertFalse(SavedAnchorNativeContext.unchangedDisablePose(p,new SavedAnchorNativeContext.DisablePose(1.1,2,3,-89.5f,4,posture,0)));
        assertFalse(SavedAnchorNativeContext.unchangedDisablePose(p,new SavedAnchorNativeContext.DisablePose(1,2,3,-90.5f,4,posture,0)));
        assertFalse(SavedAnchorNativeContext.unchangedDisablePose(p,new SavedAnchorNativeContext.DisablePose(1,2,3,-89.5f,4.1f,posture,0)));
    }
    @Test void eachPositionComponentPostureAndSelectedSlotMutationRefuses(){
        var posture=new Object();var p=new SavedAnchorNativeContext.DisablePose(1,2,3,4,5,posture,0);
        for(var a:new SavedAnchorNativeContext.DisablePose[]{new SavedAnchorNativeContext.DisablePose(1,2.01,3,4,5,posture,0),new SavedAnchorNativeContext.DisablePose(1,2,3.01,4,5,posture,0),new SavedAnchorNativeContext.DisablePose(1,2,3,4,5,new Object(),0),new SavedAnchorNativeContext.DisablePose(1,2,3,4,5,posture,1)})
            assertFalse(SavedAnchorNativeContext.unchangedDisablePose(p,a));
    }
    @Test void unknownNonfiniteNullAndInvalidSlotPoseCannotGrantCustody(){
        var posture=new Object();var p=new SavedAnchorNativeContext.DisablePose(1,2,3,4,5,posture,0);
        assertFalse(SavedAnchorNativeContext.unchangedDisablePose(null,p));assertFalse(SavedAnchorNativeContext.unchangedDisablePose(p,null));
        for(var a:new SavedAnchorNativeContext.DisablePose[]{new SavedAnchorNativeContext.DisablePose(Double.NaN,2,3,4,5,posture,0),new SavedAnchorNativeContext.DisablePose(1,Double.POSITIVE_INFINITY,3,4,5,posture,0),new SavedAnchorNativeContext.DisablePose(1,2,3,Float.NaN,5,posture,0),new SavedAnchorNativeContext.DisablePose(1,2,3,4,Float.NEGATIVE_INFINITY,posture,0),new SavedAnchorNativeContext.DisablePose(1,2,3,4,5,null,0),new SavedAnchorNativeContext.DisablePose(1,2,3,4,5,posture,9)})
            assertFalse(SavedAnchorNativeContext.unchangedDisablePose(a,a));
    }

    @Test void originalFarmSettingsRemainCurrentWhileDurableHelperMetadataChanges(){
        var config=new dev.veltraph.client.config.MapConfigService();var snapshot=SavedAnchorNativeContext.captureFarmSettings(config);
        assertNotNull(snapshot);assertTrue(snapshot.current(config));
        config.set(new dev.veltraph.client.config.ConfigKey<String>("GREENHOUSE_UNRESOLVED_ACTIONS",""),"[]");
        assertTrue(snapshot.current(config));
    }
    @Test void originalFarmGameplaySettingChangeAndEqualValueABACannotGrantArrival(){
        var config=new dev.veltraph.client.config.MapConfigService();var key=dev.veltraph.client.config.FarmingConfig.MUSHROOM_RING_START_FORWARD;
        var snapshot=SavedAnchorNativeContext.captureFarmSettings(config);assertNotNull(snapshot);
        config.set(key,false);assertFalse(snapshot.current(config));config.set(key,true);assertFalse(snapshot.current(config));
    }
    @Test void equalFarmValuesInReplacementStoreDoNotAdoptOriginalConfiguration(){
        var original=new dev.veltraph.client.config.MapConfigService();var snapshot=SavedAnchorNativeContext.captureFarmSettings(original);
        assertNotNull(snapshot);assertFalse(snapshot.current(new dev.veltraph.client.config.MapConfigService()));
    }
    @Test void arbitraryConfigGettersCannotSupplyStoredFarmHistory(){
        var unknown=new dev.veltraph.client.config.ConfigService(){
            public <T>T get(dev.veltraph.client.config.ConfigKey<T> key){throw new AssertionError("Unknown getter called");}
            public <T>void set(dev.veltraph.client.config.ConfigKey<T> key,T value){throw new AssertionError("Unknown setter called");}
            public boolean isSet(dev.veltraph.client.config.ConfigKey<?> key){throw new AssertionError("Unknown getter called");}
            public void reset(dev.veltraph.client.config.ConfigKey<?> key){throw new AssertionError("Unknown reset called");}
        };
        assertNull(SavedAnchorNativeContext.captureFarmSettings(unknown));
    }

}
