package dev.veltraph.client.garden.jacob;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class JacobContestTrackerTest {
    private final Object player=new Object(),world=new Object(),connection=new Object();
    private final JacobContestTracker.Scope scope=new JacobContestTracker.Scope(player,world,connection,"actual-wheat-profile");
    private final JacobContestTracker tracker=new JacobContestTracker();
    private static List<String> rows(String clock){return List.of("Garden","Jacob's Contest",clock,"Gold with 1,234","+100 over gold","Next contest: 40m0s");}
    /** Twenty actual callbacks, inclusive bounds. Source increments before checking == 20. */
    private void callbacks(JacobContestTracker.Scope owner,List<String> sidebar,long first,long last){for(long tick=first;tick<=last;tick++)tracker.tick(owner,true,sidebar,tick);}
    @Test void onlyImmediateCropRowProvesClock(){
        var observed=JacobContestObservation.read(rows("§aSugar Cane 2m35s"));
        assertTrue(observed.clockKnown());assertEquals("Sugar Cane",observed.crop());assertEquals(155,observed.remainingSeconds());
        assertEquals(List.of("Gold with 1,234","+100 over gold"),observed.rankingRows());
        assertEquals(155,observed.scheduled().remainingSeconds());
        for(var bad:List.of("Wheat","Time Left: 1m30s","Wheat 2m60s","Unknown Crop 0m10s","Next contest: 0m10s")){
            var unknown=JacobContestObservation.read(rows(bad));assertTrue(unknown.active());assertFalse(unknown.clockKnown());assertEquals(-1,unknown.remainingSeconds());
        }
    }
    @Test void missingOrDuplicateHeadersNeverProveTiming(){
        assertFalse(JacobContestObservation.read(List.of("Wheat 0m10s")).active());
        assertFalse(JacobContestObservation.read(null).active());
        assertFalse(JacobContestObservation.read(List.of("Jacob's Contest","Wheat 0m10s","Jacob's Contest","Wheat 0m5s")).clockKnown());
        assertFalse(JacobContestObservation.read(List.of("Jacob's Contest")).clockKnown());
    }
    @Test void twentiethActualCallbackCreatesFirstRecordAndEachLaterTwentiethReplacesIt(){
        for(int callback=1;callback<=19;callback++){
            tracker.tick(scope,true,rows("Wheat 2m35s"),callback-1);
            assertFalse(tracker.observation(scope,callback-1).active());
            assertFalse(tracker.consumeTrapWindow(scope,callback-1,true,true,1,200));
        }
        tracker.tick(scope,true,rows("Wheat 2m35s"),19);
        assertEquals(155,tracker.observation(scope,19).remainingSeconds());
        callbacks(scope,rows("Wheat 0m01s"),20,38);
        assertEquals(155,tracker.observation(scope,38).remainingSeconds());
        assertFalse(tracker.observation(scope,39).active());
        tracker.tick(scope,true,rows("Wheat 0m01s"),39);
        assertEquals(1,tracker.observation(scope,39).remainingSeconds());
        assertFalse(tracker.observation(scope,-1).active());
    }
    @Test void globalTickGapsAndDuplicateTicksCannotFabricateBookkeepingCallbacks(){
        for(int callback=1;callback<=19;callback++){
            long tick=(callback-1)*100L;
            tracker.tick(scope,true,rows("Wheat 0m10s"),tick);
            tracker.tick(scope,true,rows("Wheat 0m10s"),tick);
            assertFalse(tracker.observation(scope,tick).active());
        }
        tracker.tick(scope,true,rows("Wheat 0m10s"),1900);
        assertTrue(tracker.observation(scope,1900).active());
        assertFalse(tracker.observation(scope,1920).active());
    }
    @Test void strictExplicitTrapGateAndOncePerSampleObject(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);
        assertFalse(tracker.consumeTrapWindow(scope,19,true,true,1,-1));
        assertFalse(tracker.consumeTrapWindow(scope,19,true,true,1,10));
        assertFalse(tracker.consumeTrapWindow(scope,19,false,true,1,11));
        assertFalse(tracker.consumeTrapWindow(scope,19,true,false,1,11));
        assertFalse(tracker.consumeTrapWindow(scope,19,true,true,0,11));
        assertTrue(tracker.consumeTrapWindow(scope,19,true,true,1,11));
        callbacks(scope,rows("Wheat 0m09s"),20,38);
        assertFalse(tracker.consumeTrapWindow(scope,38,true,true,1,11));
        tracker.tick(scope,true,rows("Wheat 0m09s"),39);
        assertTrue(tracker.consumeTrapWindow(scope,39,true,true,1,11));
    }
    @Test void malformedClockBlocksReadinessAndNextValidSampleGetsFreshGate(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);
        assertTrue(tracker.consumeTrapWindow(scope,19,true,true,1,11));
        callbacks(scope,rows("Wheat"),20,39);
        assertFalse(tracker.consumeTrapWindow(scope,39,true,true,1,11));
        callbacks(scope,rows("Carrot 0m09s"),40,59);
        assertTrue(tracker.consumeTrapWindow(scope,59,true,true,1,11));
    }
    @Test void headerEndProducesExactlyOneLocalEventAndNextSampleGetsFreshGate(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);
        assertTrue(tracker.consumeTrapWindow(scope,19,true,true,1,11));
        callbacks(scope,List.of("Garden"),20,39);
        var event=tracker.takeEnded().orElseThrow();assertEquals(JacobContestTracker.Reason.HEADER_ENDED,event.reason());assertEquals("Wheat",event.lastObservation().crop());
        assertTrue(tracker.takeEnded().isEmpty());
        callbacks(scope,rows("Carrot 0m05s"),40,59);
        assertTrue(tracker.consumeTrapWindow(scope,59,true,true,1,11));
    }
    @Test void sourceEndMarkerInsideNativeSystemMessageWaitsForAnAbsentHeader(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);
        tracker.message(new JacobContestTracker.Scope(player,world,new Object(),scope.profile()),JacobContestTracker.ENDED_MESSAGE);
        assertTrue(tracker.takeEnded().isEmpty());
        tracker.message(scope,"§e[Contest] "+JacobContestTracker.ENDED_MESSAGE+" §7Thanks!");
        assertEquals(JacobContestTracker.Reason.SERVER_MESSAGE,tracker.takeEnded().orElseThrow().reason());
        callbacks(scope,rows("Wheat 0m09s"),20,39);
        assertFalse(tracker.observation(scope,39).active());
        callbacks(scope,List.of("Garden"),40,59);
        callbacks(scope,rows("Carrot 0m05s"),60,79);
        assertTrue(tracker.observation(scope,79).active());
    }
    @Test void plainPrefixedAndSuffixedMarkersKeepTheAttributedCropAndClock(){
        for(String message:List.of(JacobContestTracker.ENDED_MESSAGE,"[NPC] Jacob: "+JacobContestTracker.ENDED_MESSAGE,JacobContestTracker.ENDED_MESSAGE+" Rewards are listed below.","§e[Contest] "+JacobContestTracker.ENDED_MESSAGE+" §7Thanks!")){
            tracker.reset();callbacks(scope,rows("Carrot 1m30s"),0,19);tracker.message(scope,message);
            var event=tracker.takeEnded().orElseThrow();assertEquals(JacobContestTracker.Reason.SERVER_MESSAGE,event.reason());assertEquals("Carrot",event.lastObservation().crop());assertEquals(90,event.lastObservation().remainingSeconds());assertTrue(tracker.takeEnded().isEmpty());assertFalse(tracker.observation(scope,19).active());
        }
    }
    @Test void absentPartialAndDifferentCaseMarkersNeverEndAnActiveRecord(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);
        for(String message:List.of("","[Contest] Keep farming!","The Farming Contest is over","the farming contest is over!","The farming Contest is over!")){
            tracker.message(scope,message);assertTrue(tracker.takeEnded().isEmpty());assertTrue(tracker.observation(scope,19).active());assertFalse(tracker.trapConsumed());
        }
        tracker.message(scope,null);assertTrue(tracker.takeEnded().isEmpty());assertTrue(tracker.observation(scope,19).active());
    }
    @Test void substringCannotEndARecordFromAnotherNativePlayerWorldConnectionOrFarm(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);
        for(var changed:List.of(new JacobContestTracker.Scope(new Object(),world,connection,scope.profile()),new JacobContestTracker.Scope(player,new Object(),connection,scope.profile()),new JacobContestTracker.Scope(player,world,new Object(),scope.profile()),new JacobContestTracker.Scope(player,world,connection,"different-profile"))){
            tracker.message(changed,"[Contest] "+JacobContestTracker.ENDED_MESSAGE);assertTrue(tracker.takeEnded().isEmpty());assertTrue(tracker.observation(scope,19).active());
        }
        tracker.message(null,JacobContestTracker.ENDED_MESSAGE);assertTrue(tracker.takeEnded().isEmpty());
    }
    @Test void stoppedFarmAndUnsampledReplacementScopeRejectStaleEndMessages(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);tracker.tick(scope,false,rows("Wheat 0m09s"),20);tracker.message(scope,"[Contest] "+JacobContestTracker.ENDED_MESSAGE);assertTrue(tracker.takeEnded().isEmpty());
        callbacks(scope,rows("Wheat 0m08s"),21,40);var replaced=new JacobContestTracker.Scope(player,world,new Object(),scope.profile());tracker.tick(replaced,true,rows("Carrot 0m07s"),41);
        tracker.message(scope,JacobContestTracker.ENDED_MESSAGE);tracker.message(replaced,JacobContestTracker.ENDED_MESSAGE);assertTrue(tracker.takeEnded().isEmpty());assertFalse(tracker.observation(replaced,41).active());
    }
    @Test void duplicateAndStaleHeaderMessagesProduceOneEndEvent(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);tracker.message(scope,"[Contest] "+JacobContestTracker.ENDED_MESSAGE);tracker.message(scope,JacobContestTracker.ENDED_MESSAGE+" Thanks!");
        var first=tracker.takeEnded().orElseThrow();assertEquals("Wheat",first.lastObservation().crop());assertTrue(tracker.takeEnded().isEmpty());
        callbacks(scope,rows("Wheat 0m09s"),20,39);tracker.message(scope,"[Contest] "+JacobContestTracker.ENDED_MESSAGE);assertTrue(tracker.takeEnded().isEmpty());assertFalse(tracker.observation(scope,39).active());
        callbacks(scope,List.of("Garden"),40,59);callbacks(scope,rows("Melon 0m05s"),60,79);tracker.message(scope,JacobContestTracker.ENDED_MESSAGE);
        var next=tracker.takeEnded().orElseThrow();assertTrue(next.contest()>first.contest());assertEquals("Melon",next.lastObservation().crop());
    }
    @Test void profilePlayerWorldConnectionChangesResetActualCallbackCount(){
        for(var replacement:List.of(new JacobContestTracker.Scope(new Object(),world,connection,scope.profile()),new JacobContestTracker.Scope(player,new Object(),connection,scope.profile()),new JacobContestTracker.Scope(player,world,new Object(),scope.profile()),new JacobContestTracker.Scope(player,world,connection,"different-profile"))){
            callbacks(scope,rows("Wheat 0m10s"),0,19);assertTrue(tracker.consumeTrapWindow(scope,19,true,true,1,11));
            tracker.message(scope,JacobContestTracker.ENDED_MESSAGE);
            callbacks(replacement,rows("Carrot 0m05s"),20,38);
            assertTrue(tracker.takeEnded().isEmpty());assertFalse(tracker.observation(scope,38).active());assertFalse(tracker.observation(replacement,38).active());
            tracker.tick(replacement,true,rows("Carrot 0m05s"),39);
            assertTrue(tracker.consumeTrapWindow(replacement,39,true,true,1,11));tracker.reset();
        }
    }
    @Test void stoppedFarmAndClockRollbackResetActualCallbackCount(){
        callbacks(scope,rows("Wheat 0m10s"),0,19);assertTrue(tracker.consumeTrapWindow(scope,19,true,true,1,11));
        tracker.tick(scope,false,rows("Wheat 0m09s"),20);assertFalse(tracker.observation(scope,20).active());assertTrue(tracker.takeEnded().isEmpty());
        callbacks(scope,rows("Wheat 0m08s"),100,118);assertFalse(tracker.observation(scope,118).active());
        tracker.tick(scope,true,rows("Wheat 0m08s"),119);assertTrue(tracker.consumeTrapWindow(scope,119,true,true,1,11));
        callbacks(scope,rows("Wheat 0m07s"),1,19);assertFalse(tracker.observation(scope,19).active());
        tracker.tick(scope,true,rows("Wheat 0m07s"),20);assertTrue(tracker.consumeTrapWindow(scope,20,true,true,1,11));
    }
}
