package dev.veltraph.client.service.mc;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardCustodySwapTest {
    static final class Rig {
        final Object original=new Object(),helper=new Object(),foreign=new Object();
        final AtomicReference<Object> value=new AtomicReference<>(original);
        final GuardCustodyStore<Object> store=new GuardCustodyStore<>(value);
        final GuardCustodySwap<Object> swap=new GuardCustodySwap<>(store,original,helper,store.generation());
        void install(){assertTrue(swap.install(()->true,()->true));assertSame(helper,value.get());}
    }
    @Test void snapshotCouplesExactValueAndGeneration(){var r=new Rig();var before=r.store.snapshot();assertSame(r.original,before.value());assertEquals(0,before.generation());r.store.set(r.foreign);var after=r.store.snapshot();assertSame(r.foreign,after.value());assertEquals(1,after.generation());}
    @Test void sameOriginalReturnsExactlyOnce(){var r=new Rig();r.install();assertTrue(r.swap.restore(()->true,()->true));assertSame(r.original,r.value.get());assertFalse(r.swap.restore(()->true,()->true));}
    @Test void foreignGuardIsPreserved(){var r=new Rig();r.install();r.store.set(r.foreign);assertFalse(r.swap.restore(()->true,()->true));assertSame(r.foreign,r.value.get());}
    @Test void removedHelperCannotBeRestoredFromNull(){var r=new Rig();r.install();assertTrue(r.store.compareAndSet(r.helper,null));assertFalse(r.swap.restore(()->true,()->true));assertNull(r.value.get());}
    @Test void sameReferenceWriteInvalidatesCustody(){var r=new Rig();r.install();r.store.set(r.helper);assertFalse(r.swap.restore(()->true,()->true));assertSame(r.helper,r.value.get());}
    @Test void foreignGuardAbaCannotBeAdopted(){var r=new Rig();r.install();r.store.set(r.foreign);r.store.set(r.helper);assertFalse(r.swap.restore(()->true,()->true));assertSame(r.helper,r.value.get());}
    @Test void farmGuardAbaDuringCaptureRefusesInstall(){var r=new Rig();r.store.set(r.foreign);r.store.set(r.original);assertFalse(r.swap.install(()->true,()->true));assertSame(r.original,r.value.get());}
    @Test void refusedSourceCannotRetryAtNewHistory(){var r=new Rig();r.install();assertFalse(r.swap.restore(()->false,()->true));assertFalse(r.swap.restore(()->true,()->true));assertSame(r.helper,r.value.get());}
    @Test void thrownSourcePreservesCurrentAndConsumesAttempt(){var r=new Rig();r.install();assertFalse(r.swap.restore(()->{throw new IllegalStateException();},()->true));assertFalse(r.swap.restore(()->true,()->true));assertSame(r.helper,r.value.get());}
    @Test void pendingNativeWorkRefusesRestore(){var r=new Rig();r.install();assertFalse(r.swap.restore(()->true,()->false));assertSame(r.helper,r.value.get());}
    @Test void finalSourceCallbackReplacingGuardCannotCommit(){var r=new Rig();r.install();var calls=new AtomicInteger();assertFalse(r.swap.restore(()->{if(calls.incrementAndGet()==2)r.store.set(r.foreign);return true;},()->true));assertEquals(2,calls.get());assertSame(r.foreign,r.value.get());}
    @Test void finalSourceCallbackChangingNativeStateIsRechecked(){var r=new Rig();r.install();var calls=new AtomicInteger();boolean[] idle={true};assertFalse(r.swap.restore(()->{if(calls.incrementAndGet()==2)idle[0]=false;return true;},()->idle[0]));assertSame(r.helper,r.value.get());}
    @Test void finalNativeCallbackAbaCannotCommit(){var r=new Rig();r.install();var calls=new AtomicInteger();assertFalse(r.swap.restore(()->true,()->{if(calls.incrementAndGet()==2){r.store.set(r.foreign);r.store.set(r.helper);}return true;}));assertSame(r.helper,r.value.get());}
    @Test void failedCasDoesNotInventMutationGeneration(){var r=new Rig();long before=r.store.generation();assertFalse(r.store.compareAndSet(r.foreign,r.helper));assertEquals(before,r.store.generation());r.install();assertTrue(r.swap.restore(()->true,()->true));}
    @Test void staleSnapshotBeforeProviderCannotInstall(){var r=new Rig();assertFalse(r.swap.install(()->{r.store.set(r.original);return true;},()->true));assertSame(r.original,r.value.get());}
}
