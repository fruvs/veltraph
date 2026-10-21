package dev.veltraph.client.garden.pests;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PestMaintenanceRuntimeTest {
    static class Actions implements PestMaintenanceRuntime.Actions {
        List<String> events=new ArrayList<>();boolean valid=true,resume=true;PestMaintenanceRuntime.Progress progress=PestMaintenanceRuntime.Progress.COMPLETE;
        public boolean acquire(){events.add("acquire");return true;}public boolean valid(){return valid;}
        public boolean apply(String n){events.add("apply:"+n);return true;}public PestMaintenanceRuntime.Progress loadout(boolean r){events.add(r?"restored":"applied");return progress;}
        public boolean clean(){events.add("clean");return true;}public PestMaintenanceRuntime.Progress cleaned(){events.add("cleaned");return progress;}
        public boolean restore(){events.add("restore");return true;}public boolean beginReturn(){events.add("return");return true;}
        public PestMaintenanceRuntime.Progress returned(){return progress;}public boolean resume(){events.add("resume");return resume;}public void cancel(){events.add("cancel");}
    }
    @Test void fullBatchOrdersLoadoutCleanRestoreReturnAndResume(){var a=new Actions();var r=new PestMaintenanceRuntime(a);assertTrue(r.start(new PestMaintenanceRuntime.Settings(true,"Killing",100),0));
        for(int i=1;i<8;i++)r.tick(i);assertEquals(PestMaintenanceRuntime.State.COMPLETE,r.state());assertEquals(List.of("acquire","apply:Killing","applied","clean","cleaned","restore","restored","return","resume"),a.events);}
    @Test void silentOutcomeNeverRestoresOrResumes(){var a=new Actions();a.progress=PestMaintenanceRuntime.Progress.ACTIVE;var r=new PestMaintenanceRuntime(a);r.start(new PestMaintenanceRuntime.Settings(false,"",10),0);r.tick(1);r.tick(10);
        assertEquals(PestMaintenanceRuntime.State.FAILED,r.state());assertFalse(a.events.contains("return"));assertFalse(a.events.contains("resume"));assertEquals(1,Collections.frequency(a.events,"clean"));}
    @Test void ownershipChangeCancelsExactPairWithoutResume(){var a=new Actions();var r=new PestMaintenanceRuntime(a);r.start(new PestMaintenanceRuntime.Settings(true,"Pest",10),0);a.valid=false;r.tick(1);
        assertEquals(PestMaintenanceRuntime.State.FAILED,r.state());assertEquals(List.of("acquire","apply:Pest","cancel"),a.events);}
    @Test void refusedFinalRestorationFailsStopped(){var a=new Actions();a.resume=false;var r=new PestMaintenanceRuntime(a);r.start(new PestMaintenanceRuntime.Settings(false,"",10),0);r.tick(1);r.tick(2);
        assertEquals(PestMaintenanceRuntime.State.FAILED,r.state());assertEquals("cancel",a.events.getLast());}
}
