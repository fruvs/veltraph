package dev.veltraph.client.combat.ghost;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceObservedSearchRanking.*;
class GhostSourceObservedSearchRankingTest {
    Candidate c(int id,double distance,int sample){return new Candidate(new UUID(0,id),76,distance,sample,true,Observed.YES,Observed.YES,Observed.NO);}
    @Test void nearestFifteenLimitPrecedesMetricRankingAndRankFiveLimit(){
        var all=new ArrayList<Candidate>();for(int i=1;i<=15;i++)all.add(c(i,i,20));all.add(c(16,16,0));
        var result=GhostSourceObservedSearchRanking.choose(all,Randomization.OFF,()->{throw new AssertionError("OFF used RNG");});
        assertTrue(result.observed());assertEquals(5,result.candidates().size());assertEquals(c(1,1,20),result.candidates().getFirst());
        assertFalse(result.candidates().stream().anyMatch(x->x.id().equals(new UUID(0,16))));
    }
    @Test void lookIndexPenaltyCanReorderNearestCandidatesWithoutEntityCensus(){
        var result=GhostSourceObservedSearchRanking.choose(List.of(c(1,4,4),c(2,5,0),c(3,6,3)),Randomization.OFF,()->1);
        assertEquals(List.of(new UUID(0,2),new UUID(0,3),new UUID(0,1)),result.candidates().stream().map(Candidate::id).toList());
    }
    @Test void strictYPoweredAndIdentityPredicatesFilterOnlyKnownEligibleBodies(){
        var boundary=new Candidate(new UUID(0,1),78.5,1,0,true,Observed.YES,Observed.YES,Observed.NO);
        var unpowered=new Candidate(new UUID(0,2),76,1,0,false,Observed.UNKNOWN,Observed.UNKNOWN,Observed.UNKNOWN);
        var ignored=new Candidate(new UUID(0,3),76,1,0,true,Observed.YES,Observed.YES,Observed.YES);
        var result=GhostSourceObservedSearchRanking.choose(List.of(boundary,unpowered,ignored,c(4,4,0)),Randomization.OFF,()->1);
        assertTrue(result.observed());assertEquals(1,result.candidates().size());assertEquals(new UUID(0,4),result.candidates().getFirst().id());
    }
    @Test void missingApplicableFlagOrBodyObservationDoesNotBecomeDefaultOffOrEmptyKnownCohort(){
        assertFalse(GhostSourceObservedSearchRanking.choose(List.of(c(1,1,0)),Randomization.UNKNOWN,()->1).observed());
        var unknown=new Candidate(new UUID(0,1),76,1,0,true,Observed.UNKNOWN,Observed.YES,Observed.NO);
        assertFalse(GhostSourceObservedSearchRanking.choose(List.of(unknown),Randomization.OFF,()->1).observed());
        assertThrows(IllegalArgumentException.class,()->GhostSourceObservedSearchRanking.choose(List.of(c(1,1,0),c(2,2,0)),Randomization.ON,()->30));
    }
}
