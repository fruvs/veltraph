package dev.veltraph.client.observation.unexpected;
import java.util.concurrent.atomic.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UnexpectedBaselineCaptureTest {
    private static AbstractModule module(String id){return new AbstractModule(id,id,ModuleCategory.INTERNAL){};}
    @Test void failedSnapshotDisablesOnlyCapturedActualModule(){
        var manager=new ModuleManager();var original=module("original");var unrelated=module("unrelated");manager.register(original);manager.register(unrelated);manager.enable(original.id());manager.enable(unrelated.id());var errors=new AtomicInteger();
        assertFalse(UnexpectedBaselineCapture.capture(()->{throw new IllegalStateException("injected read failure");},()->manager.get(original.id()).orElse(null)==original&&original.isEnabled(),()->manager.disable(original.id()),e->errors.incrementAndGet()));
        assertFalse(original.isEnabled());assertTrue(unrelated.isEnabled());assertEquals(1,errors.get());
    }
    @Test void laterCustodyCannotBeCancelledByFailedOldSnapshot(){
        var manager=new ModuleManager();var original=module("original");var newer=module("newer");manager.register(original);manager.register(newer);manager.enable(original.id());var generation=new AtomicInteger(1);
        assertFalse(UnexpectedBaselineCapture.capture(()->{manager.disable(original.id());manager.enable(newer.id());generation.incrementAndGet();throw new IllegalStateException("failure after handoff");},()->generation.get()==1&&original.isEnabled(),()->manager.disable(original.id()),e->{}));assertTrue(newer.isEnabled());assertFalse(original.isEnabled());
    }
    @Test void exactPreparedReadFailureCancelsActualLease(){
        var lease=new FarmMaintenanceLease();var farm=new Object();var helper=new Object();var token=lease.prepare(farm,helper,100,1000);
        assertFalse(UnexpectedBaselineCapture.capture(()->{throw new IllegalStateException("injected snapshot");},()->lease.owns(token)&&lease.state()==FarmMaintenanceLease.State.PREPARED,()->lease.cancel(token,"Baseline unavailable"),e->{}));
        assertFalse(lease.active());assertEquals(FarmMaintenanceLease.State.CANCELLED,lease.state());assertFalse(lease.commit(token));
    }
    @Test void failedOldPreparedSnapshotPreservesNewLease(){
        var lease=new FarmMaintenanceLease();var first=lease.prepare(new Object(),new Object(),100,1000);var newer=new AtomicReference<FarmMaintenanceLease.Token>();
        assertFalse(UnexpectedBaselineCapture.capture(()->{lease.rollback(first);newer.set(lease.prepare(new Object(),new Object(),101,1000));throw new IllegalStateException("failure after replacement");},()->lease.owns(first),()->lease.rollback(first),e->{}));assertTrue(lease.owns(newer.get()));assertTrue(lease.commit(newer.get()));
    }
    @Test void successfulCaptureDoesNotCancelOrReport(){var called=new AtomicInteger();assertTrue(UnexpectedBaselineCapture.capture(called::incrementAndGet,()->true,()->fail("cancelled"),e->fail("reported")));assertEquals(1,called.get());}
    @Test void cancellationFailureIsContainedAndReportedOnce(){var reported=new AtomicReference<RuntimeException>();assertFalse(UnexpectedBaselineCapture.capture(()->{throw new IllegalStateException("snapshot");},()->true,()->{throw new IllegalStateException("cleanup");},reported::set));assertEquals(1,reported.get().getSuppressed().length);}
}
