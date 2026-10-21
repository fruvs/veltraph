package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceScopeCostsTest {
    @Test void noProfileStillEvaluatesOriginalCallbackExactlyOnce(){var calls=new AtomicInteger();assertEquals(7,GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.PARENT_STAMP_FIRST,()->{calls.incrementAndGet();return 7;}).intValue());assertEquals(1,calls.get());}
    @Test void actualShortCircuitSkipsUnreachableOperations(){var costs=new GhostSourceScopeCosts();var old=GhostSourceScopeCosts.attach(costs);var calls=new AtomicInteger();
        try{boolean actual=GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.REGISTRATION,()->false)&&GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.AREA_CURRENT,()->{calls.incrementAndGet();return true;});assertFalse(actual);assertEquals(0,calls.get());
            assertEquals(1,costs.cached().samples().get(GhostSourceScopeCosts.Part.REGISTRATION).calls());assertEquals(0,costs.cached().samples().get(GhostSourceScopeCosts.Part.AREA_CURRENT).calls());}
        finally{GhostSourceScopeCosts.restore(old);}}
    @Test void nullAndThrownResultsPreserveOriginalReadOrderAndEffects(){var costs=new GhostSourceScopeCosts();var old=GhostSourceScopeCosts.attach(costs);var order=new ArrayList<String>();
        try{assertNull(GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.AREA_RECEIPT,()->{order.add("area");return null;}));
            assertThrows(IllegalStateException.class,()->GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.CHILD_HANDOFF,()->{order.add("child");throw new IllegalStateException("original callback");}));
            assertEquals(List.of("area","child"),order);assertEquals(1,costs.cached().samples().get(GhostSourceScopeCosts.Part.AREA_RECEIPT).calls());assertEquals(1,costs.cached().samples().get(GhostSourceScopeCosts.Part.CHILD_HANDOFF).calls());}
        finally{GhostSourceScopeCosts.restore(old);}}
    @Test void nestedContextRestoresPriorCollectorAndCachedGetterRunsNoCallbacks(){var first=new GhostSourceScopeCosts();var second=new GhostSourceScopeCosts();var original=GhostSourceScopeCosts.attach(first);
        try{GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.INPUT_OWNER,()->true);var prior=GhostSourceScopeCosts.attach(second);try{GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.INPUT_OWNER,()->true);}finally{GhostSourceScopeCosts.restore(prior);}
            GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.INPUT_OWNER,()->true);var before=first.cached();assertEquals(before,first.cached());assertEquals(2,before.samples().get(GhostSourceScopeCosts.Part.INPUT_OWNER).calls());assertEquals(1,second.cached().samples().get(GhostSourceScopeCosts.Part.INPUT_OWNER).calls());assertThrows(UnsupportedOperationException.class,()->before.samples().clear());}
        finally{GhostSourceScopeCosts.restore(original);}}
}
