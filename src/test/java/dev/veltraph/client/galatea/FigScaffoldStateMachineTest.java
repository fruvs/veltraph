package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.galatea.FigScaffoldStateMachine.*;
class FigScaffoldStateMachineTest {
    private Frame frame(long now,boolean authority,boolean selected,boolean ground,double motion,double y,
                        boolean above,boolean timed) {
        return new Frame(now,authority,selected,true,false,true,true,false,ground,true,above,true,false,motion,y,timed?.049:.5);
    }
    private FigScaffoldStateMachine waiting(){var f=new FigScaffoldStateMachine(new TimeoutContract(2000,500,"Private authored test contract; source caption keys remain unbound"));f.begin(new Metadata(9,3));
        f.prepared(f.generation(),true,new Admission(true,2,6),0);
        f.tick(frame(0,true,true,true,0,70.2,true,false));
        var d=f.tick(frame(1300,true,true,true,0,70.2,true,false));
        assertTrue(d.commands().contains(Command.INVOKE_RIGHT_CLICK_ONCE));return f;}
    @Test void admissionUsesStrictMoreThanThreeAndCeilingVeto(){
        for(var a:new Admission[]{new Admission(true,2,5),new Admission(false,2,99)}){
            var f=new FigScaffoldStateMachine(new TimeoutContract(2000,500,"Private authored test contract; source caption keys remain unbound"));f.begin(new Metadata(5,2));
            assertEquals(State.THROWING_AXE,f.prepared(f.generation(),true,a,0).state());}}
    @Test void oneHelperInvocationDoesNotProveDispatch(){var f=waiting();assertEquals(Dispatch.NOT_ATTEMPTED,f.dispatch());
        f.recordDispatch(f.generation(),Dispatch.INVOKED_WITHOUT_QUEUED_EVENT);
        for(int i=0;i<5;i++)assertFalse(f.tick(frame(1400+i,true,true,true,0,71,true,false)).commands().contains(Command.INVOKE_RIGHT_CLICK_ONCE));
        assertEquals(Dispatch.INVOKED_WITHOUT_QUEUED_EVENT,f.dispatch());}
    @Test void completionRiseDiffersFromGeometryHeight(){var f=waiting();
        assertEquals(State.WAITING_FOR_SCAFFOLDING,f.tick(frame(1500,true,true,true,0,73.99,true,false)).state());
        assertEquals(State.BREAK_ON_SCAFFOLDING,f.tick(frame(1600,true,true,true,0,74,true,false)).state());}
    @Test void groundAndMotionVetoCompletion(){var f=waiting();
        assertEquals(State.WAITING_FOR_SCAFFOLDING,f.tick(frame(1500,true,true,false,0,80,true,false)).state());
        assertEquals(State.WAITING_FOR_SCAFFOLDING,f.tick(frame(1600,true,true,true,.1001,80,true,false)).state());}
    @Test void totalTimeoutBranchSelectsAxeWithoutPretendingFinished(){var f=waiting();
        var d=f.tick(frame(3501,true,true,true,0,80,true,true));
        assertEquals(State.WAITING_FOR_SCAFFOLDING,d.state());assertEquals(java.util.List.of(Command.SELECT_AXE),d.commands());}
    @Test void ceilingAndStationaryAreSeparateCompletionBranches(){
        assertEquals(State.BREAK_ON_SCAFFOLDING,waiting().tick(frame(1500,true,true,true,0,71,false,false)).state());
        assertEquals(State.BREAK_ON_SCAFFOLDING,waiting().tick(frame(1801,true,true,true,0,71,true,false)).state());}
    @Test void authorityRevocationInvalidatesCallbackAndStops(){var f=new FigScaffoldStateMachine(new TimeoutContract(2000,500,"Private authored test contract; source caption keys remain unbound"));var d=f.begin(new Metadata(5,2));
        f.stop();assertEquals(State.STOPPED,f.prepared(d.generation(),true,new Admission(true,0,99),10).state());
        var w=waiting();assertEquals(State.STOPPED,w.tick(frame(1500,false,true,true,0,71,true,false)).state());}
    @Test void groundedWaitTimeoutsAndParentHoldAreIndependent(){
        assertEquals(200,recoveredOrdinaryCallerTimeouts().considerAxeAfterMs());
        assertEquals(1000,recoveredOrdinaryCallerTimeouts().stationaryAfterMs());
        var f=new FigScaffoldStateMachine(recoveredOrdinaryCallerTimeouts());f.begin(new Metadata(5,2));
        f.prepared(f.generation(),true,new Admission(true,0,4),100);
        assertTrue(f.sourceParentEntryHold(2099));assertFalse(f.sourceParentEntryHold(2100));
    }
    @Test void exactFailurePrefixSelectsThrowing(){var f=waiting();
        assertEquals(State.WAITING_FOR_SCAFFOLDING,f.placementFeedback("You can't place a scaffolding here").state());
        assertEquals(State.THROWING_AXE,f.placementFeedback("You can't place a scaffolding here!").state());}
    @Test void periodicAimPrecedesAttackAndCompletion(){var f=waiting();
        var d=f.tick(new Frame(1600,true,true,true,false,true,true,true,true,true,true,true,false,0,74,.5));
        assertEquals(java.util.List.of(Command.AIM,Command.HOLD_ATTACK,Command.CAPTURE_CENTERED_FEET,Command.SELECT_AXE,Command.ENTER_SCAFFOLD_BREAK),d.commands());
        assertEquals(State.BREAK_ON_SCAFFOLDING,d.state());}
    @Test void periodicAimPrecedesRandomAxeBranchWithoutAttack(){var f=waiting();
        var d=f.tick(new Frame(3501,true,true,true,false,true,true,true,true,true,true,true,false,0,80,.049));
        assertEquals(java.util.List.of(Command.AIM,Command.SELECT_AXE),d.commands());
        assertEquals(State.WAITING_FOR_SCAFFOLDING,d.state());}
    @Test void networkGapRefreshVetoesStationaryCompletion(){var f=waiting();
        var d=f.tick(new Frame(1801,true,true,true,false,true,true,true,true,true,true,true,true,0,71,.5));
        assertEquals(State.WAITING_FOR_SCAFFOLDING,d.state());assertEquals(1801,f.lastMotion());
        assertEquals(java.util.List.of(Command.AIM,Command.HOLD_ATTACK),d.commands());}

}
