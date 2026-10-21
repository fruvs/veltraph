package dev.veltraph.client.combat.ghost;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.ghost.GhostSourcePathResultScoring.*;
class GhostSourcePathResultScoringTest {
    GhostSourceCrowdPressureCounter.Observation known(long value){var counter=new GhostSourceCrowdPressureCounter();counter.assignFromMappedLifecycle(value);return counter.snapshot();}
    Candidate candidate(double distance,double yaw,List<Edge> edges,Observed ray){return new Candidate(new Object(),UUID.randomUUID(),new Object(),true,edges,distance,(float)distance,yaw,2,4,5,ray);}
    @Test void allTermsMatchAndDrawBoundsAreInSourceEvaluationOrder(){
        var draws=new ArrayList<Double>();
        var c=candidate(8,-70,List.of(new Edge(10,2),new Edge(11,4),new Edge(14,6)),Observed.NO);
        double score=score(c,new Context(Observed.YES,known(2L),true),upper->{draws.add(upper);return upper/2;});
        assertEquals(List.of(2.0,6.0,6.0),draws);
        assertEquals(37.0,score); // 4+6+1+3+3+2+6+4+5+3
    }
    @Test void zeroOrNegativeHelperDoesNotConsumeAnExtraDraw(){
        for(long helper:List.of(0L,-1L)){var draws=new ArrayList<Double>();
            score(candidate(1,0,List.of(new Edge(1,0)),Observed.YES),new Context(Observed.NO,known(helper),true),upper->{draws.add(upper);return 0;});
            assertEquals(List.of(2.0),draws);}
    }
    @Test void drawsAreOncePerEntryBeforeSortNeverInsideComparator(){
        var a=candidate(9,0,List.of(new Edge(0,0)),Observed.NO);var b=candidate(1,0,List.of(new Edge(0,0)),Observed.YES);
        var drawTargets=new ArrayList<Double>();
        var chosen=choose(List.of(a,b),new Context(Observed.YES,known(1L),true),upper->{drawTargets.add(upper);return 0;});
        assertEquals(List.of(2.0,6.0,3.0,2.0,6.0,3.0),drawTargets);assertSame(b,chosen.selected().candidate());
    }
    @Test void preferredCandidateOverridesLowerScoreOnlyWithinStrictDistanceYawAndActorRay(){
        var lower=candidate(8,0,List.of(new Edge(0,0)),Observed.YES);
        var preferred=candidate(6,39.99,List.of(new Edge(0,100)),Observed.YES);
        assertSame(preferred,choose(List.of(lower,preferred),new Context(Observed.NO,known(0L),true),u->0).selected().candidate());
        for(var boundary:List.of(candidate(7,0,List.of(new Edge(0,100)),Observed.YES),candidate(6,40,List.of(new Edge(0,100)),Observed.YES),
                candidate(6,0,List.of(new Edge(0,100)),Observed.NO)))
            assertSame(lower,choose(List.of(lower,boundary),new Context(Observed.NO,known(0L),true),u->0).selected().candidate());
    }
    @Test void unknownRayForEligiblePreferredCandidateCannotBecomeOrdinaryFallback(){
        var result=choose(List.of(candidate(6,0,List.of(new Edge(0,0)),Observed.UNKNOWN)),new Context(Observed.NO,known(0L),true),u->0);
        assertFalse(result.observed());assertNull(result.selected());
    }
    @Test void missingHelperOrCrowdObservationDoesNotDrawOrSelect(){
        var c=candidate(6,0,List.of(new Edge(0,0)),Observed.YES);
        for(var context:List.of(new Context(Observed.UNKNOWN,known(0L),true),new Context(Observed.NO,null,true)))
            assertFalse(choose(List.of(c),context,u->{fail("unobserved context must not draw");return 0;}).observed());
    }
    @Test void verticalPenaltyUsesConsecutiveFromYIncludingRepeatedEdgesAndStrictGreaterThanOne(){
        var c=candidate(0,0,List.of(new Edge(5,0),new Edge(6,0),new Edge(8,0),new Edge(8,0),new Edge(5,0)),Observed.NO);
        assertEquals(6,score(c,new Context(Observed.NO,known(0L),true),u->0)-score(c,new Context(Observed.NO,known(0L),false),u->0));
    }
    @Test void nonWalkingFailureResultHasNoInventedPathCostOrVerticalPenalty(){
        var c=new Candidate(new Object(),UUID.randomUUID(),null,false,List.of(),4,4f,0,0,0,0,Observed.NO);
        assertEquals(2,score(c,new Context(Observed.NO,known(0L),true),u->0));
    }
    @Test void equalScoresRetainSuppliedSourceMapIterationOrder(){
        var a=candidate(8,0,List.of(new Edge(0,0)),Observed.NO);var b=candidate(8,0,List.of(new Edge(0,0)),Observed.NO);
        var result=choose(List.of(a,b),new Context(Observed.NO,known(0L),true),u->0);
        assertSame(a,result.sorted().getFirst().candidate());assertSame(a,result.selected().candidate());
    }
    @Test void preferredDistanceUsesActualNativeFloatRatherThanDoubleScoreDistance(){
        var c=candidate(6.99999999,0,List.of(new Edge(0,100)),Observed.YES);
        var fallback=candidate(8,0,List.of(new Edge(0,0)),Observed.YES);
        assertEquals(7f,c.nativePreferredDistance());
        assertSame(fallback,choose(List.of(fallback,c),new Context(Observed.NO,known(0L),true),u->0).selected().candidate());
    }
    @Test void snapshotAfterUnknownCannotBeScoredOrSelectedThroughTypedContext(){
        var counter=new GhostSourceCrowdPressureCounter();counter.assignFromMappedLifecycle(7);
        counter.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.UNKNOWN);
        var uncertain=counter.snapshot();assertFalse(uncertain.observed());
        var c=candidate(6,0,List.of(new Edge(0,0)),Observed.YES);var context=new Context(Observed.NO,uncertain,true);
        assertFalse(choose(List.of(c),context,u->{fail("uncertain value must not consume RNG");return 0;}).observed());
        assertThrows(IllegalStateException.class,()->score(c,context,u->{fail("uncertain value must not consume RNG");return 0;}));
    }
    @Test void oldKnownContextIsRevokedByUnknownAndDoesNotReviveAfterAssignment(){
        var counter=new GhostSourceCrowdPressureCounter();var old=counter.snapshot();var context=new Context(Observed.NO,old,true);
        var c=candidate(6,0,List.of(new Edge(0,0)),Observed.YES);
        counter.updateFromMappedHook(0,GhostSourceCrowdPressureCounter.SkipUpdate.UNKNOWN);
        counter.assignFromMappedLifecycle(0);assertFalse(old.observed());
        assertFalse(choose(List.of(c),context,u->{fail("stale observation must not draw");return 0;}).observed());
        assertTrue(choose(List.of(c),new Context(Observed.NO,counter.snapshot(),true),u->0).observed());
    }
    @Test void invalidDrawsRejectInsteadOfClamp(){
        var c=candidate(1,0,List.of(new Edge(0,0)),Observed.YES);
        for(double v:new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->score(c,new Context(Observed.NO,known(0L),true),u->v));
    }
}
