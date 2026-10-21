package dev.veltraph.client.rotation;
import dev.veltraph.client.service.mc.McRotationService;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class FinalWorkflowPublicationLeafTest {
 @Test void lateProviderWorkflowResetRefusesEvenWhenNativePoseIdentityUnchanged(){var workflow=new AtomicLong(4);long captured=workflow.get();java.util.function.Supplier<Object> provider=()->{workflow.incrementAndGet();return new Object();};provider.get();assertFalse(McRotationService.finalWorkflowAllowed(()->workflow.get()==captured,()->true));}
 @Test void finalWorkflowReadPrecedesIndependentNativeLeaf(){var order=new StringBuilder();assertTrue(McRotationService.finalWorkflowAllowed(()->{order.append('W');return true;},()->{order.append('N');return true;}));assertEquals("WN",order.toString());}
 @Test void aTrueWorkflowCannotAuthorizeChangedNativeScope(){var nativeVersion=new AtomicLong(5);assertFalse(McRotationService.finalWorkflowAllowed(()->{nativeVersion.incrementAndGet();return true;},()->nativeVersion.get()==5));}
 @Test void refusalDoesNotRunNativePermissionOrPublication(){var reads=new AtomicInteger();assertFalse(McRotationService.finalWorkflowAllowed(()->false,()->{reads.incrementAndGet();return true;}));assertEquals(0,reads.get());}
 @Test void sameTickReceiptRenewalAlsoRefusesNewWorkflow(){var workflow=new AtomicLong(1);long captured=1;workflow.set(2);assertFalse(McRotationService.finalWorkflowAllowed(()->workflow.get()==captured,()->true));}
 @Test void postPublicationProviderResetDeniesReceiptReturn(){var workflow=new AtomicLong(1);long captured=1;assertTrue(McRotationService.finalWorkflowAllowed(()->workflow.get()==captured,()->true));workflow.incrementAndGet();assertFalse(McRotationService.finalWorkflowAllowed(()->workflow.get()==captured,()->true));}
 @Test void cancellationStopFrameResetDeniesOldStopCleanup(){var workflow=new AtomicLong(8);long stopFrame=8;workflow.incrementAndGet();assertFalse(McRotationService.finalWorkflowAllowed(()->workflow.get()==stopFrame,()->true));}
 @Test void originalVetoExceptionPropagatesWithoutNativeEffect(){var failure=new IllegalStateException("workflow failure");var nativeCalls=new AtomicInteger();assertSame(failure,assertThrows(IllegalStateException.class,()->McRotationService.finalWorkflowAllowed(()->{throw failure;},()->{nativeCalls.incrementAndGet();return true;})));assertEquals(0,nativeCalls.get());}
}
