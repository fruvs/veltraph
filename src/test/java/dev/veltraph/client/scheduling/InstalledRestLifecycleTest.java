package dev.veltraph.client.scheduling;
import dev.veltraph.client.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class InstalledRestLifecycleTest {
    private static AbstractModule owner(String id){return new AbstractModule(id,id,ModuleCategory.FARMING){};}
    @Test void exactEnabledPausedOwnerIsRequired(){
        var captured=owner("captured");var other=owner("other");captured.setEnabled(true);other.setEnabled(true);
        assertSame(captured,InstalledRestLifecycle.exactPausedOwner(List.of(captured,other),m->m==captured));
        assertNull(InstalledRestLifecycle.exactPausedOwner(List.of(captured,other),m->true));
        captured.setEnabled(false);
        assertNull(InstalledRestLifecycle.exactPausedOwner(List.of(captured),m->true));
    }
    @Test void delayedAndUnownedDeparturesRetainDefaultStopAuthority(){
        var captured=owner("captured");captured.setEnabled(true);
        assertFalse(InstalledRestLifecycle.permitsNativeTransition(captured,false));
        assertFalse(InstalledRestLifecycle.permitsNativeTransition(null,true));
        assertTrue(InstalledRestLifecycle.permitsNativeTransition(captured,true));
        captured.setEnabled(false);assertFalse(InstalledRestLifecycle.permitsNativeTransition(captured,true));
    }
    @Test void populationCanWaitButReturnStartedGearOrCursorLossRevokesAuthority(){
        assertTrue(InstalledRestLifecycle.permitsReturnUpdate(false,false,false,false));
        assertTrue(InstalledRestLifecycle.permitsReturnUpdate(true,true,true,true));
        for(boolean[] lost:new boolean[][]{{false,true,true},{true,false,true},{true,true,false}}){
            assertFalse(InstalledRestLifecycle.permitsReturnUpdate(true,lost[0],lost[1],lost[2]));
            var rest=new SourceRestRuntime();rest.start(100,60000,20,1000);
            for(int tick=0;tick<22;tick++)rest.tick(new SourceRestRuntime.Observation(true,false,false,false,100+tick));
            rest.tick(new SourceRestRuntime.Observation(true,true,false,false,60101));
            rest.tick(new SourceRestRuntime.Observation(true,false,true,false,60102));
            assertEquals(SourceRestRuntime.Action.CANCEL,rest.tick(new SourceRestRuntime.Observation(
                    InstalledRestLifecycle.permitsReturnUpdate(true,lost[0],lost[1],lost[2]),false,true,true,60103)));
            assertFalse(rest.restartPending());assertEquals(SourceRestRuntime.State.CANCELLED,rest.state());
        }
    }
    @Test void managerManualStopRevokesPreservationAndInputOwnerImmediately(){
        var captured=owner("captured");var manager=new ModuleManager();manager.register(captured);manager.enable(captured.id());
        Object ticket=new Object();Object[] live={ticket};manager.beforeDisable(m->live[0]=null);
        assertSame(captured,InstalledRestLifecycle.exactPausedOwner(manager.all(),m->live[0]==ticket));
        manager.disable(captured.id());
        assertNull(InstalledRestLifecycle.exactPausedOwner(manager.all(),m->live[0]==ticket));assertTrue(manager.inputOwner().isEmpty());
    }
}
