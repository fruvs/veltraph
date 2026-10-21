package dev.veltraph.client.observation.unexpected;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
final class UnexpectedStopPolicyTest {
    private Finding event(long serial,Classification classification) { return new Finding(serial,Kind.ROTATION,classification,Set.of(),100,"Local evidence"); }
    @Test void freshUnexpectedStopsBeforeNotification() {
        var order=new ArrayList<String>();var policy=new UnexpectedStopPolicy(()->true,()->order.add("stop"),f->order.add("notify"));
        policy.accept(event(1,Classification.UNEXPECTED));assertEquals(List.of("stop","notify"),order);
    }
    @Test void explainedAndUncertainDoNotStopOrNotify() {
        var calls=new AtomicInteger();var policy=new UnexpectedStopPolicy(()->true,calls::incrementAndGet,f->calls.incrementAndGet());
        policy.accept(event(1,Classification.EXPLAINED));policy.accept(event(2,Classification.UNCERTAIN));assertEquals(0,calls.get());
    }
    @Test void enablingDoesNotReplayOldUnexpectedHistory() {
        var enabled=new AtomicBoolean();var stops=new AtomicInteger();var alerts=new AtomicInteger();
        var policy=new UnexpectedStopPolicy(enabled::get,stops::incrementAndGet,f->alerts.incrementAndGet());
        var old=event(1,Classification.UNEXPECTED);policy.accept(old);enabled.set(true);policy.accept(old);
        assertEquals(0,stops.get());assertEquals(1,alerts.get());policy.accept(event(2,Classification.UNEXPECTED));assertEquals(1,stops.get());
    }
    @Test void reorderedOrRepeatedReceiptCannotStopAnotherRun() {
        var stops=new AtomicInteger();var policy=new UnexpectedStopPolicy(()->true,stops::incrementAndGet,f->{});
        policy.accept(event(5,Classification.UNEXPECTED));policy.accept(event(5,Classification.UNEXPECTED));policy.accept(event(4,Classification.UNEXPECTED));assertEquals(1,stops.get());
    }
    @Test void notificationFailureStillStops() {
        var stopped=new AtomicBoolean();var policy=new UnexpectedStopPolicy(()->true,()->stopped.set(true),f->{throw new IllegalStateException("Local UI unavailable");});
        assertThrows(IllegalStateException.class,()->policy.accept(event(1,Classification.UNEXPECTED)));assertTrue(stopped.get());
    }
    @Test void optInDefaultIsPreserved() { assertFalse(UnexpectedBehaviorConfig.AUTO_STOP.defaultValue()); }
}
