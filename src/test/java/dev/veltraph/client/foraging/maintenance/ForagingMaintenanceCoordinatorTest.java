package dev.veltraph.client.foraging.maintenance;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForagingMaintenanceCoordinatorTest {
    private static final class NativeProof implements ForagingMaintenanceCoordinator.Port {
        Object parent=new Object(),helper=new Object(),invitation=new Object(),engine=new Object(),player=new Object(),world=new Object(),connection=new Object(),profile=new Object(),protection=new Object(),pose=new Object();
        boolean enabled,valid=true,unchanged=true,paused,completed,cleaned,deny,foreign;
        int captures,pauses,restores,stops,rollbacks,claims;ForagingMaintenanceLease.Token owner;
        ForagingMaintenanceCoordinator.ReturnStatus returning=ForagingMaintenanceCoordinator.ReturnStatus.ACTIVE;
        public Optional<String> readiness(){return deny?Optional.of("not eligible"):Optional.empty();}
        public Object invitation(){return invitation;}
        public ForagingMaintenanceCoordinator.Capture capture(){captures++;return new ForagingMaintenanceCoordinator.Capture(parent,helper,engine,player,world,connection,profile,protection,pose,invitation);}
        public boolean helperEnabled(){return enabled;}public boolean parentEnabled(){return !foreign;}
        public boolean valid(ForagingMaintenanceCoordinator.Capture c){return valid&&!foreign&&c.parent()==parent&&c.helper()==helper&&c.engine()==engine&&c.player()==player&&c.world()==world&&c.connection()==connection&&c.profile()==profile&&c.protection()==protection;}
        public boolean activationUnchanged(ForagingMaintenanceCoordinator.Capture c){return unchanged;}
        public boolean pause(ForagingMaintenanceLease.Token token){pauses++;owner=token;paused=true;return true;}
        public boolean ownsPause(ForagingMaintenanceLease.Token token){return paused&&owner==token;}
        public boolean completed(ForagingMaintenanceCoordinator.Capture c){return completed;}
        public boolean cleanupFinished(ForagingMaintenanceCoordinator.Capture c){return cleaned;}
        public ForagingMaintenanceCoordinator.ReturnStatus returnToPose(ForagingMaintenanceCoordinator.Capture c){return returning;}
        public boolean resume(ForagingMaintenanceCoordinator.Capture c,ForagingMaintenanceLease.Token token){restores++;if(!ownsPause(token)||!valid(c)||!completed||!cleaned)return false;paused=false;return true;}
        public void rollback(ForagingMaintenanceCoordinator.Capture c){rollbacks++;enabled=false;}
        public void stopBoth(ForagingMaintenanceCoordinator.Capture c){stops++;enabled=false;paused=false;}
        boolean enable(){enabled=true;return true;}
    }
    @Test void eligibilityDoesNotCapturePauseEnableOrConsume(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.readiness().isEmpty());assertEquals(0,p.captures);assertEquals(0,p.pauses);assertFalse(p.enabled);assertFalse(c.consumed(p.invitation));p.deny=true;assertTrue(c.readiness().isPresent());}
    @Test void standardGuardRunsInsideExactReservationAndPausePrecedesHelperAction(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(()->{assertTrue(c.preparedActivation(p.helper));assertTrue(c.permitsActivationConflict(p.parent,p.helper));assertFalse(c.permitsActivationConflict(new Object(),p.helper));assertFalse(p.paused);return p.enable();},0,1000));assertTrue(p.paused);assertTrue(c.consumed(p.invitation));p.claims++;assertTrue(c.active());assertFalse(c.permitsActivationConflict(p.parent,p.helper));}
    @Test void failedGuardRollsBackWithoutPausingOrConsuming(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertFalse(c.startTransaction(()->false,0,1000));assertFalse(c.active());assertFalse(c.consumed(p.invitation));assertEquals(0,p.pauses);assertEquals(1,p.rollbacks);assertEquals(0,p.stops);}
    @Test void nonDeferredEnableCannotCommit(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertFalse(c.startTransaction(()->{p.enable();p.unchanged=false;return true;},0,1000));assertEquals(0,p.pauses);assertEquals(1,p.rollbacks);assertFalse(c.active());}
    @Test void successRequiresRewardCleanupAndActualReturnBeforeSameEngineResume(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));Object engine=p.engine;p.completed=true;p.enabled=false;c.tick(1);assertEquals(ForagingMaintenanceLease.State.CLEANUP,c.state());assertEquals(0,p.restores);p.cleaned=true;c.tick(2);assertEquals(ForagingMaintenanceLease.State.RETURNING,c.state());assertEquals(0,p.restores);p.returning=ForagingMaintenanceCoordinator.ReturnStatus.READY;c.tick(3);assertEquals(ForagingMaintenanceLease.State.RESUMED,c.state());assertEquals(1,p.restores);assertSame(engine,p.engine);assertFalse(c.active());assertTrue(c.readiness().isPresent());assertEquals(1,p.pauses);}
    @Test void ordinaryHelperStopIsNotCompletionAndCannotRestartSameInvitation(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));p.enabled=false;c.tick(1);assertEquals(1,p.stops);assertFalse(c.active());assertTrue(c.readiness().isPresent());assertEquals(0,p.restores);}
    @Test void exactCompletedSelfStopPermitsCleanupWhileManualParentStopCancelsBoth(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));p.completed=true;c.beforeDisable(p.helper);assertEquals(ForagingMaintenanceLease.State.CLEANUP,c.state());assertEquals(0,p.stops);c.beforeDisable(p.parent);assertEquals(1,p.stops);assertFalse(c.active());}
    @Test void everyCapturedNativeReferenceEngineProfileAndProtectionChangeCancels(){for(int field=0;field<7;field++){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));switch(field){case 0->p.player=new Object();case 1->p.world=new Object();case 2->p.connection=new Object();case 3->p.engine=new Object();case 4->p.profile=new Object();case 5->p.protection=new Object();case 6->p.helper=new Object();}c.tick(1);assertFalse(c.active(),"field "+field);assertEquals(1,p.stops);assertEquals(0,p.restores);}}
    @Test void higherPriorityResponseInvalidationStopsBothBeforeForeignAction(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));p.foreign=true;c.tick(1);assertFalse(c.active());assertFalse(p.enabled);assertFalse(p.paused);assertEquals(1,p.stops);}
    @Test void changedProofDuringReturnAndFailedReturnNeverResume(){for(boolean lost:new boolean[]{false,true}){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));p.completed=p.cleaned=true;p.enabled=false;c.tick(1);if(lost)p.completed=false;else p.returning=ForagingMaintenanceCoordinator.ReturnStatus.FAILED;c.tick(2);assertEquals(1,p.stops);assertEquals(0,p.restores);}}
    @Test void expiredLeaseAndReversedClockCancelBeforeResume(){for(long now:new long[]{1001,99}){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,100,900));p.completed=p.cleaned=true;p.enabled=false;p.returning=ForagingMaintenanceCoordinator.ReturnStatus.READY;c.tick(now);assertFalse(c.active());assertEquals(1,p.stops);assertEquals(0,p.restores);}}
    @Test void onlyNewOriginalInvitationCanAcquireAfterCompletedOrCancelledRun(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));Object old=p.invitation;c.cancelActive("manual stop");assertTrue(c.consumed(old));assertFalse(c.startTransaction(p::enable,1,1000));p.invitation=new Object();assertTrue(c.startTransaction(p::enable,2,1000));assertEquals(2,p.pauses);}
    @Test void foreignDisableDoesNotCancelPair(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));c.beforeDisable(new Object());assertTrue(c.active());assertEquals(0,p.stops);}
    @Test void readonlyInputReservationRequiresCommittedPauseAndCurrentNativeProof(){var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(()->{assertNull(c.validatedParent());return p.enable();},0,1000));assertSame(p.parent,c.validatedParent());p.valid=false;assertNull(c.validatedParent());p.valid=true;p.owner=null;assertNull(c.validatedParent());}
    @Test void leaseTokenAndStateCannotBeReusedAcrossTransactions(){var l=new ForagingMaintenanceLease();Object p=new Object(),h=new Object();var t=l.prepare(p,h,0,1000);assertFalse(l.resume(t));assertTrue(l.commit(t));assertFalse(l.returning(t));assertTrue(l.cleanup(t));assertTrue(l.returning(t));assertTrue(l.resume(t));var next=l.prepare(p,h,1,1000);assertNotSame(t,next);assertFalse(l.commit(t));assertTrue(l.commit(next));}
    @Test void cameraLeaseContinuityRevokesAfterReturnResumeAndEveryNewEpoch(){
        var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);
        assertTrue(c.startTransaction(()->{assertNull(c.rotationAuthorityToken(p.player,p.world,p.connection,true));return p.enable();},0,1000));
        Object token=c.rotationAuthorityToken(p.player,p.world,p.connection,true);assertSame(p.owner,token);
        p.completed=true;c.beforeDisable(p.helper);p.enabled=false;
        assertSame(token,c.rotationAuthorityToken(p.player,p.world,p.connection,false));
        p.cleaned=true;c.tick(1);assertSame(token,c.rotationAuthorityToken(p.player,p.world,p.connection,false));
        p.returning=ForagingMaintenanceCoordinator.ReturnStatus.READY;c.tick(2);
        assertNull(c.rotationAuthorityToken(p.player,p.world,p.connection,false));
        p.invitation=new Object();p.completed=p.cleaned=false;p.returning=ForagingMaintenanceCoordinator.ReturnStatus.ACTIVE;
        assertTrue(c.startTransaction(p::enable,3,1000));
        assertNotSame(token,c.rotationAuthorityToken(p.player,p.world,p.connection,true));
        c.cancelActive("manual stop");assertNull(c.rotationAuthorityToken(p.player,p.world,p.connection,true));
    }
    @Test void cameraLeaseChecksNativeIdentityWithoutInvokingPortProof(){
        var p=new NativeProof();var c=new ForagingMaintenanceCoordinator(p);assertTrue(c.startTransaction(p::enable,0,1000));
        Object token=p.owner;p.valid=false;p.deny=true;
        assertSame(token,c.rotationAuthorityToken(p.player,p.world,p.connection,true));
        assertNull(c.rotationAuthorityToken(new Object(),p.world,p.connection,true));
        assertNull(c.rotationAuthorityToken(p.player,new Object(),p.connection,true));
        assertNull(c.rotationAuthorityToken(p.player,p.world,new Object(),true));
        assertNull(c.rotationAuthorityToken(p.player,p.world,p.connection,false));
        assertTrue(c.active());assertEquals(0,p.stops);
        c.tick(1);assertNull(c.rotationAuthorityToken(p.player,p.world,p.connection,true));
    }
}
