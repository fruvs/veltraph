package dev.veltraph.client.navigation;

import dev.veltraph.client.config.*;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pure lease issuance/config epochs plus role predicates; not native producer/Root proof. */
class MaintenanceMovementAuthorityFrameTest {
    private static final Vec3 HOME=new Vec3(.5,70,.5),NPC=new Vec3(5.5,70,.5);
    private static final class F {
        final FarmMaintenanceLease lease=new FarmMaintenanceLease();
        Object provider=new Object(),capture=new Object(),sourcePolicy=new Object(),farm=new Object(),helper=new Object(),pose=new Object();
        Object player=new Object(),world=new Object(),connection=new Object(),restorationWitness;MapConfigService config=new MapConfigService();
        FarmMaintenanceLease.Token token;Object input,camera;String helperId="helper",lockOwner="helper";
        boolean roster=true,farmOwned=true,enabled=true,ownsInput=true,active=true,proved=true,healthy=true,policyCurrent=true,lock=true;
        SharedMovementPolicy.Purpose purpose=SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL;Vec3 goal=NPC;
        F(){token=lease.prepare(farm,helper,100,5000);assertTrue(lease.commit(token));input=helper;camera=token;}
        MaintenanceMovementAuthorityFrame.Observation observation(){
            var pair=new MaintenanceMovementAuthorityFrame.Pair(provider,capture,sourcePolicy,farm,helper,token,pose,restorationWitness);
            var context=new MaintenanceMovementAuthorityFrame.Context(player,world,connection,config,
                config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER).orElse(-1),config.revision(TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));
            return new MaintenanceMovementAuthorityFrame.Observation(pair,context,lease.state(),purpose,goal,HOME,input,camera,
                helperId,lockOwner,roster,farmOwned,enabled,ownsInput,active,proved,healthy,policyCurrent,lock);
        }
        MaintenanceMovementAuthorityFrame issued(){return MaintenanceMovementAuthorityFrame.capture(observation()).orElseThrow();}
        void restoring(){assertTrue(lease.beginRestore(token));restorationWitness=new Object();purpose=SharedMovementPolicy.Purpose.EXACT_RETURN;goal=HOME;}
    }
    @Test void actualCommittedPairKeepsSeparateHelperInputAndIssuedTokenCamera(){var f=new F();var frame=f.issued();assertSame(f.helper,frame.pair().helper());assertSame(f.token,frame.pair().token());assertNotSame(f.helper,f.token);assertTrue(frame.current(f.observation()));}
    @Test void arbitraryOpaqueCameraCannotBecomeMaintenanceAuthority(){var f=new F();f.camera=new Object();assertTrue(MaintenanceMovementAuthorityFrame.capture(f.observation()).isEmpty());}
    @Test void helperModuleCannotReplaceTokenCamera(){var f=new F();f.camera=f.helper;assertTrue(MaintenanceMovementAuthorityFrame.capture(f.observation()).isEmpty());}
    @Test void preparedLeaseNeverAuthorizesGameplayEvenWithAnEnabledClaim(){var f=new F();f.lease.clear();f.token=f.lease.prepare(f.farm,f.helper,200,5000);f.camera=f.token;assertTrue(MaintenanceMovementAuthorityFrame.capture(f.observation()).isEmpty());}
    @Test void foreignInputHelperAndLockOwnerBothReject(){var f=new F();var frame=f.issued();f.input=new Object();assertFalse(frame.current(f.observation()));f.input=f.helper;f.lockOwner="foreign";assertFalse(frame.current(f.observation()));}
    @Test void pausedOrUnhealthyObservationRevokesExistingFrame(){var f=new F();var frame=f.issued();f.healthy=false;assertFalse(frame.current(f.observation()));}
    @Test void disabledHelperPassiveRoleOrMissingLockReject(){var f=new F();var frame=f.issued();f.enabled=false;assertFalse(frame.current(f.observation()));f.enabled=true;f.ownsInput=false;assertFalse(frame.current(f.observation()));f.ownsInput=true;f.lock=false;assertFalse(frame.current(f.observation()));}
    @Test void ThirdControllerOrUnregisteredPairCannotBorrowToken(){var f=new F();var frame=f.issued();f.roster=false;assertFalse(frame.current(f.observation()));}
    @Test void farmMustRemainOwnedByTheActualIssuedToken(){var f=new F();var frame=f.issued();f.farmOwned=false;assertFalse(frame.current(f.observation()));}
    @Test void replacementPlayerWorldAndConnectionEachRevoke(){var f=new F();var frame=f.issued();var original=f.player;f.player=new Object();assertFalse(frame.current(f.observation()));f.player=original;original=f.world;f.world=new Object();assertFalse(frame.current(f.observation()));f.world=original;f.connection=new Object();assertFalse(frame.current(f.observation()));}
    @Test void changedThenRevertedActualTravelConfigEpochRevokes(){var f=new F();var frame=f.issued();var old=f.config.get(TravelConfig.TRAVEL_APPROACH_MODE);f.config.set(TravelConfig.TRAVEL_APPROACH_MODE,ApproachMode.WALK_ONLY);f.config.set(TravelConfig.TRAVEL_APPROACH_MODE,old);assertFalse(frame.current(f.observation()));}
    @Test void changedThenRevertedActualRotationConfigEpochRevokes(){var f=new F();var frame=f.issued();var old=f.config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);f.config.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,old+1);f.config.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,old);assertFalse(frame.current(f.observation()));}
    @Test void replacementConfigStoreAndSourcePolicyRevoke(){var f=new F();var frame=f.issued();var old=f.config;f.config=new MapConfigService();assertFalse(frame.current(f.observation()));f.config=old;f.sourcePolicy=new Object();assertFalse(frame.current(f.observation()));}
    @Test void samePairNewIssuedLeaseAfterCancelCannotReuseOldFrame(){var f=new F();var frame=f.issued();var old=f.token;f.lease.cancel(old,"stop");f.lease.clear();f.token=f.lease.prepare(f.farm,f.helper,200,5000);assertTrue(f.lease.commit(f.token));f.camera=f.token;assertNotSame(old,f.token);assertFalse(frame.current(f.observation()));}
    @Test void newProviderCaptureOrCapturedPoseEachRevoke(){var f=new F();var frame=f.issued();var old=f.provider;f.provider=new Object();assertFalse(frame.current(f.observation()));f.provider=old;old=f.capture;f.capture=new Object();assertFalse(frame.current(f.observation()));f.capture=old;f.pose=new Object();assertFalse(frame.current(f.observation()));}
    @Test void actualProtectedReturnAllowsOnlyRestoringToReturningForwardTransition(){var f=new F();f.restoring();var frame=f.issued();assertTrue(f.lease.beginReturn(f.token));assertTrue(frame.current(f.observation()));assertTrue(f.lease.ready(f.token));assertFalse(frame.current(f.observation()));}
    @Test void replacementPrivateRestorationWitnessCannotRefreshOldReturnFrame(){var f=new F();f.restoring();var frame=f.issued();f.restorationWitness=new Object();assertFalse(frame.current(f.observation()));}
    @Test void privateRestorationProofCannotBeInferredFromPhaseAlone(){var f=new F();f.restoring();f.proved=false;assertTrue(MaintenanceMovementAuthorityFrame.capture(f.observation()).isEmpty());}
    @Test void restoringRejectsOtherGoalAndGeneralMovementPurpose(){var f=new F();f.restoring();f.goal=NPC;assertTrue(MaintenanceMovementAuthorityFrame.capture(f.observation()).isEmpty());f.goal=HOME;f.purpose=SharedMovementPolicy.Purpose.GENERAL_TRAVEL;assertTrue(MaintenanceMovementAuthorityFrame.capture(f.observation()).isEmpty());}
    @Test void helperActiveFrameCannotFollowRestoreOrDifferentPurpose(){var f=new F();var frame=f.issued();f.purpose=SharedMovementPolicy.Purpose.GENERAL_TRAVEL;assertFalse(frame.current(f.observation()));f.purpose=SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL;f.restoring();assertFalse(frame.current(f.observation()));}
    @Test void invalidPolicyProviderInactiveOrCancelledLeaseRevokes(){var f=new F();var frame=f.issued();f.policyCurrent=false;assertFalse(frame.current(f.observation()));f.policyCurrent=true;f.active=false;assertFalse(frame.current(f.observation()));f.active=true;f.lease.cancel(f.token,"revoked");assertFalse(frame.current(f.observation()));}
}
