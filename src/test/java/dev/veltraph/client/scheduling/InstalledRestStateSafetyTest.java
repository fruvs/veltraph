package dev.veltraph.client.scheduling;
import dev.veltraph.client.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class InstalledRestStateSafetyTest {
    private SourceRestRuntime runtime(){var rest=new SourceRestRuntime();rest.start(100,60000,20,1000);return rest;}
    private SourceRestRuntime.Observation observation(boolean valid,boolean disconnected,boolean arrival,boolean ready,long now){return new SourceRestRuntime.Observation(valid,disconnected,arrival,ready,now);}
    private void depart(SourceRestRuntime rest){for(int i=0;i<21;i++)assertEquals(SourceRestRuntime.Action.NONE,rest.tick(observation(true,false,false,false,100+i)));assertEquals(SourceRestRuntime.Action.DISCONNECT,rest.tick(observation(true,false,false,false,121)));}
    @Test void onlyIssuedDisconnectThenMatchedArrivalAllowsOneResume(){
        var rest=runtime();depart(rest);
        assertEquals(SourceRestRuntime.Action.NONE,rest.tick(observation(true,true,false,false,60100)));
        assertEquals(SourceRestRuntime.Action.REJOIN,rest.tick(observation(true,true,false,false,60101)));
        assertEquals(SourceRestRuntime.Action.VERIFY_ARRIVAL,rest.tick(observation(true,false,true,false,60102)));
        assertEquals(SourceRestRuntime.Action.NONE,rest.tick(observation(true,false,true,false,60103)));
        assertEquals(SourceRestRuntime.Action.RESTART,rest.tick(observation(true,false,true,true,60104)));
        assertEquals(SourceRestRuntime.Action.NONE,rest.tick(observation(true,false,true,true,60104)));
        assertTrue(rest.commitRestart(observation(true,false,true,true,60104)));
        assertFalse(rest.commitRestart(observation(true,false,true,true,60104)));
    }
    @Test void foreignDepartureAddedOwnerOrTimeoutCannotKeepPauseAlive(){
        var early=runtime();assertEquals(SourceRestRuntime.Action.CANCEL,early.tick(observation(true,true,false,false,101)));
        var contested=runtime();depart(contested);
        var captured=new AbstractModule("captured","Captured",ModuleCategory.FARMING){};captured.setEnabled(true);
        var other=new AbstractModule("other","Other",ModuleCategory.COMBAT){};other.setEnabled(true);
        assertNull(InstalledRestLifecycle.exactPausedOwner(List.of(captured,other),m->true));
        assertEquals(SourceRestRuntime.Action.CANCEL,contested.tick(observation(false,true,false,false,200)));
        var timed=runtime();depart(timed);timed.tick(observation(true,true,false,false,60101));
        assertEquals(SourceRestRuntime.Action.CANCEL,timed.tick(observation(true,true,false,false,61101)));
        assertFalse(timed.withinDeadline(61101));
    }
    @Test void pinnedArrivalLostDuringCommitNeverCompletes(){
        var rest=runtime();depart(rest);rest.tick(observation(true,true,false,false,60101));rest.tick(observation(true,false,true,false,60102));
        rest.tick(observation(true,false,true,true,60103));
        assertFalse(rest.commitRestart(observation(false,false,true,true,60103)));
        assertEquals(SourceRestRuntime.State.CANCELLED,rest.state());assertFalse(rest.restartPending());
    }
}
