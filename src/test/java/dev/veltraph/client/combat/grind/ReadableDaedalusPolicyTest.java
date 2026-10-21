package dev.veltraph.client.combat.grind;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.grind.ReadableDaedalusPolicy.*;

class ReadableDaedalusPolicyTest {
    private final Object parent=new Object(), player=new Object(), world=new Object(), connection=new Object(), target=new Object();
    private final Object[] items={new Object(),new Object(),new Object(),new Object(),new Object(),new Object(),new Object(),new Object(),new Object()};
    private Scope scope() { return new Scope(parent,player,world,connection,target,"proven-tier",1,2,3,0,
            Known.YES,Known.YES,Known.YES,Known.YES,Known.YES,Known.YES); }
    private List<Tool> tools(int match) {
        var result=new ArrayList<Tool>();
        for(int i=0;i<9;i++) result.add(new Tool(i,items[i],i==0||i==match?"components":"",i==0||i==match?1:0,
                i==match?Known.YES:Known.NO,i==0||i==match?Known.YES:Known.NO));
        return result;
    }
    private Observation observation(float hp,float reference,int pct,int match) {
        return new Observation(new Settings(true,pct),scope(),new Health(target,hp,reference,Known.YES),tools(match),0);
    }
    private Observation withScope(Observation o,Scope s) { return new Observation(o.settings(),s,o.health(),o.hotbar(),o.primarySlot()); }
    private Scope changed(Object p,Object w,Object c,Object t,long cfg,long owner,int slot,Known reachable,Known eligible) {
        return new Scope(parent,p,w,c,t,"proven-tier",1,cfg,owner,slot,Known.YES,Known.YES,eligible,Known.YES,reachable,Known.YES);
    }
    @Test void sourceDefaultsAreOptOutAndFivePercent() { assertEquals(new Settings(false,5),Settings.defaults()); }
    @Test void lowHpMatchesToolAndFloatThresholdTruncates() {
        var o=observation(5,119,5,4);var result=propose(o).orElseThrow();
        assertEquals(5,result.integerThreshold());assertEquals(4,result.slot());assertFalse(result.primaryFallback());
        assertTrue(propose(observation(5.1f,119,5,4)).isEmpty());
    }
    @Test void cutoverIncludesExactBoundary() { assertTrue(propose(observation(50,1000,5,2)).isPresent());assertTrue(propose(observation(50.01f,1000,5,2)).isEmpty()); }
    @Test void hundredPercentSpecialCaseSurvivesHpAboveFirstObservedReference() { assertTrue(propose(observation(200,100,100,2)).isPresent()); }
    @Test void zeroThresholdDoesNotSwapLivingTarget() { assertTrue(propose(observation(.1f,100,0,2)).isEmpty()); }
    @Test void sourceMissingToolFallsBackToPrimary() { var p=propose(observation(1,100,5,-1)).orElseThrow();assertEquals(0,p.slot());assertTrue(p.primaryFallback()); }
    @Test void alreadySelectedToolRemainsAdvisoryNoReceipt() {
        var o=observation(1,100,5,0);assertEquals(0,propose(o).orElseThrow().slot());
        assertTrue(stillAdvisoryCurrent(propose(o).orElseThrow(),o));
        // The type has no commit, select, attack or restore API; no actual receipt can be fabricated here.
    }
    @Test void firstMatchedUnusableToolDeniesRatherThanChoosingSecond() {
        var o=observation(1,100,5,2);var list=new ArrayList<>(o.hotbar());
        list.set(2,new Tool(2,items[2],"protected",1,Known.YES,Known.NO));
        list.set(5,new Tool(5,items[5],"second",1,Known.YES,Known.YES));
        assertTrue(propose(new Observation(o.settings(),o.scope(),o.health(),list,0)).isEmpty());
    }
    @Test void unknownToolPredicateDoesNotBecomeMissingToolFallback() {
        var o=observation(1,100,5,-1);var list=new ArrayList<>(o.hotbar());list.set(3,new Tool(3,items[3],"",0,Known.UNKNOWN,Known.NO));
        assertTrue(propose(new Observation(o.settings(),o.scope(),o.health(),list,0)).isEmpty());
    }
    @Test void unorderedHotbarStillUsesLowestSourceMatchingIndex() {
        var o=observation(1,100,5,2);var list=new ArrayList<>(o.hotbar());list.set(7,new Tool(7,items[7],"second",1,Known.YES,Known.YES));java.util.Collections.reverse(list);
        assertEquals(2,propose(new Observation(o.settings(),o.scope(),o.health(),list,0)).orElseThrow().slot());
    }
    @Test void malformedOrDuplicateHotbarDenies() {
        var o=observation(1,100,5,2);var list=new ArrayList<>(o.hotbar());list.set(8,list.get(0));
        assertTrue(propose(new Observation(o.settings(),o.scope(),o.health(),list,0)).isEmpty());
        assertTrue(propose(new Observation(o.settings(),o.scope(),o.health(),o.hotbar().subList(0,8),0)).isEmpty());
    }
    @Test void unknownNonFiniteZeroAndDeadHealthDenyIncludingHundredSpecial() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0,-1}) {
            assertTrue(propose(observation(bad,100,100,2)).isEmpty());assertTrue(propose(observation(1,bad,100,2)).isEmpty());
        }
        var o=observation(1,100,100,2);assertTrue(propose(new Observation(o.settings(),o.scope(),new Health(target,1,100,Known.UNKNOWN),o.hotbar(),0)).isEmpty());
    }
    @Test void unrelatedTargetHealthNeverGrantsSwap() {
        var o=observation(1,100,5,2);assertTrue(propose(new Observation(o.settings(),o.scope(),new Health(new Object(),1,100,Known.YES),o.hotbar(),0)).isEmpty());
    }
    @Test void optOutInvalidPercentageOutOfReachAndUnknownEligibilityDeny() {
        var o=observation(1,100,5,2);
        for(var s:List.of(new Settings(false,5),new Settings(true,-1),new Settings(true,101)))assertTrue(propose(new Observation(s,o.scope(),o.health(),o.hotbar(),0)).isEmpty());
        assertTrue(propose(withScope(o,changed(player,world,connection,target,2,3,0,Known.NO,Known.YES))).isEmpty());
        assertTrue(propose(withScope(o,changed(player,world,connection,target,2,3,0,Known.YES,Known.UNKNOWN))).isEmpty());
    }
    @Test void manualSlotWorldPlayerConnectionTargetOwnerAndConfigurationAbaRevoke() {
        var o=observation(1,100,5,2);var p=propose(o).orElseThrow();
        for(var s:List.of(changed(new Object(),world,connection,target,2,3,0,Known.YES,Known.YES),
                changed(player,new Object(),connection,target,2,3,0,Known.YES,Known.YES),
                changed(player,world,new Object(),target,2,3,0,Known.YES,Known.YES),
                changed(player,world,connection,new Object(),2,3,0,Known.YES,Known.YES),
                changed(player,world,connection,target,4,3,0,Known.YES,Known.YES),
                changed(player,world,connection,target,2,5,0,Known.YES,Known.YES),
                changed(player,world,connection,target,2,3,8,Known.YES,Known.YES)))assertFalse(stillAdvisoryCurrent(p,withScope(o,s)));
    }
    @Test void healthHealingBeyondThresholdRevokesButLowerHpKeepsAdvisory() {
        var o=observation(5,100,5,2);var p=propose(o).orElseThrow();assertTrue(stillAdvisoryCurrent(p,observation(4,100,5,2)));assertFalse(stillAdvisoryCurrent(p,observation(6,100,5,2)));
    }
    @Test void referenceHealthChangingEvenSameTruncatedThresholdRevokes() {
        var o=observation(1,100,5,2);assertFalse(stillAdvisoryCurrent(propose(o).orElseThrow(),observation(1,101,5,2)));
    }
    @Test void fullHotbarComponentsCountsAndIdentityReplacementRevoke() {
        var o=observation(1,100,5,2);var p=propose(o).orElseThrow();
        for(var replacement:List.of(new Tool(7,new Object(),"",0,Known.NO,Known.NO),new Tool(7,items[7],"changed",0,Known.NO,Known.NO),new Tool(7,items[7],"",1,Known.NO,Known.NO))) {
            var list=new ArrayList<>(o.hotbar());list.set(7,replacement);assertFalse(stillAdvisoryCurrent(p,new Observation(o.settings(),o.scope(),o.health(),list,0)));
        }
    }
    @Test void unavailableOrCounterfeitAdvisoryCannotThrowOrGrantFreshness() {
        var o=observation(1,100,5,2);assertFalse(stillAdvisoryCurrent(null,o));assertFalse(stillAdvisoryCurrent(new Advisory(8,false,5,o),o));assertFalse(stillAdvisoryCurrent(new Advisory(2,false,5,null),o));
    }
}
