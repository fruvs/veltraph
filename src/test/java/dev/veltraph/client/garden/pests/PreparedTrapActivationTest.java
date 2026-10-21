package dev.veltraph.client.garden.pests;
import dev.veltraph.client.garden.jacob.*;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreparedTrapActivationTest {
    final Object player=new Object(),world=new Object(),connection=new Object(),farm=new Object(),helper=new Object();
    final JacobContestTracker.Scope scope=new JacobContestTracker.Scope(player,world,connection,"actual-profile");
    final JacobContestTracker tracker=new JacobContestTracker();
    final FarmMaintenanceLease lease=new FarmMaintenanceLease();
    final FarmMaintenanceLease.Token token=lease.prepare(farm,helper,0,1000);
    PreparedTrapActivation prepared(){for(int tick=0;tick<20;tick++)tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m10s"),tick);assertTrue(tracker.consumeTrapWindow(scope,19,true,true,2,11));return new PreparedTrapActivation(token,scope,tracker.observation(scope,19));}
    @Test void delayedValidCommitUsesOriginalConsumedSampleWithoutRearming(){var p=prepared();tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m09s"),20);assertTrue(p.consume(token,scope,tracker.observation(scope,20),true));assertTrue(tracker.trapConsumed());}
    @Test void newSourceSampleCannotReplaceOriginalAuthorizationEvenWithSameClock(){var p=prepared();for(int tick=20;tick<40;tick++)tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m10s"),tick);assertFalse(p.consume(token,scope,tracker.observation(scope,39),true));assertFalse(tracker.trapConsumed());}
    @Test void sourceEndAndStaleSampleNeverAuthorizeDelayedHelper(){var p=prepared();tracker.message(scope,JacobContestTracker.ENDED_MESSAGE);assertFalse(p.consume(token,scope,tracker.observation(scope,20),true));var q=prepared();assertFalse(q.consume(token,scope,tracker.observation(scope,39),true));}
    @Test void exactPlayerWorldConnectionAndFarmProfileAreMandatory(){var p=prepared();var current=new JacobContestTracker.Scope(player,new Object(),connection,"actual-profile");assertFalse(p.consume(token,current,tracker.observation(scope,19),true));var q=prepared();assertFalse(q.consume(token,new JacobContestTracker.Scope(player,world,connection,"other-profile"),tracker.observation(scope,19),true));}
    @Test void replacementTokenPermanentlyRetiresOriginalPermission(){var p=prepared();var other=new FarmMaintenanceLease().prepare(farm,helper,0,1000);assertFalse(p.consume(other,scope,tracker.observation(scope,19),true));assertFalse(p.consume(token,scope,tracker.observation(scope,19),true));}
    @Test void cancelledOrStoppedCapturedLeaseCannotLateCommit(){var p=prepared();p.cancel(token);assertFalse(p.consume(token,scope,tracker.observation(scope,19),true));var q=prepared();assertFalse(q.consume(token,scope,tracker.observation(scope,19),false));assertFalse(q.consume(token,scope,tracker.observation(scope,19),true));}
    @Test void foreignCancellationCannotRetireTheCurrentOwnedPermission(){var p=prepared();var other=new FarmMaintenanceLease().prepare(farm,helper,0,1000);p.cancel(other);assertTrue(p.owns(token));assertFalse(p.boundTo(other));assertTrue(p.consume(token,scope,tracker.observation(scope,19),true));}
    @Test void successfulPermissionCannotBeConsumedTwice(){var p=prepared();assertTrue(p.consume(token,scope,tracker.observation(scope,19),true));assertFalse(p.consume(token,scope,tracker.observation(scope,19),true));}
}
