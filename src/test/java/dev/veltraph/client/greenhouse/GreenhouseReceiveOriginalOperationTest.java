package dev.veltraph.client.greenhouse;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import dev.veltraph.client.greenhouse.mixin.GreenhouseDiagnosticReceiveMixin;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the actual three receiver wrappers with failing stores; these are handler-boundary tests, not native packet acceptance. */
class GreenhouseReceiveOriginalOperationTest {
    private static final List<String> HANDLERS=List.of("greenhouse$open","greenhouse$content","greenhouse$close");
    private static GreenhouseDiagnosticReceiveMixin receiver(){return new GreenhouseDiagnosticReceiveMixin(){};}
    private static Field field(String name){try{var f=GreenhouseDiagnosticReceiveMixin.class.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Method handler(String name){var m=Arrays.stream(GreenhouseDiagnosticReceiveMixin.class.getDeclaredMethods()).filter(method->method.getName().equals(name)).findFirst().orElseThrow();m.setAccessible(true);return m;}
    private static void run(GreenhouseDiagnosticReceiveMixin target,String name,Operation<Void> original)throws Throwable{
        try{handler(name).invoke(target,null,original);}catch(InvocationTargetException e){throw e.getCause();}
    }
    private static void install(GreenhouseDiagnosticReceiveMixin target,ThreadLocal<VisitorHistoryNativeCallFrame> store){try{field("greenhouse$receive").set(target,store);}catch(IllegalAccessException e){throw new AssertionError(e);}}
    private static Object poison(){try{var f=McGreenhouseDiagnosticAdapter.class.getDeclaredField("unresolvedListener");f.setAccessible(true);return f.get(null);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void restorePoison(Object saved){try{var f=McGreenhouseDiagnosticAdapter.class.getDeclaredField("unresolvedListener");f.setAccessible(true);f.set(null,saved);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private enum Fault { GET, SET_AFTER_WRITE, REMOVE, RESTORE }
    private static final class Store extends ThreadLocal<VisitorHistoryNativeCallFrame> {
        private final Fault fault;private int sets;private VisitorHistoryNativeCallFrame actual;
        Store(Fault fault,VisitorHistoryNativeCallFrame previous){this.fault=fault;actual=previous;}
        @Override public VisitorHistoryNativeCallFrame get(){if(fault==Fault.GET)throw new ExceptionInInitializerError("observer setup");return actual;}
        @Override public void set(VisitorHistoryNativeCallFrame value){actual=value;sets++;if(fault==Fault.SET_AFTER_WRITE&&sets==1)throw new NoClassDefFoundError("observer entry linkage");if(fault==Fault.RESTORE&&sets==2)throw new LinkageError("observer nested restoration");}
        @Override public void remove(){actual=null;if(fault==Fault.REMOVE)throw new AssertionError("observer cleanup");}
    }
    @Test void allActualWrappersStillCallOriginalOnceAfterSetupClassInitializationError()throws Throwable{
        Object previous=poison();try{for(String name:HANDLERS){var target=receiver();install(target,new Store(Fault.GET,null));var calls=new AtomicInteger();run(target,name,args->{assertEquals(1,args.length);assertNull(args[0]);calls.incrementAndGet();return null;});assertEquals(1,calls.get());assertFalse(target.greenhouseObserverHealthy());assertSame(target,poison());}}finally{restorePoison(previous);}
    }
    @Test void partialFrameInstallationDoesNotSkipOriginalAndAttemptsCleanup()throws Throwable{
        Object previous=poison();try{for(String name:HANDLERS){var target=receiver();var store=new Store(Fault.SET_AFTER_WRITE,null);install(target,store);var calls=new AtomicInteger();run(target,name,args->{calls.incrementAndGet();return null;});assertEquals(1,calls.get());assertNull(store.actual);assertFalse(target.greenhouseObserverHealthy());}}finally{restorePoison(previous);}
    }
    @Test void observerRemovalErrorCannotReplaceNormalOriginalReturn()throws Throwable{
        Object previous=poison();try{for(String name:HANDLERS){var target=receiver();install(target,new Store(Fault.REMOVE,null));var calls=new AtomicInteger();assertDoesNotThrow(()->{try{run(target,name,args->{calls.incrementAndGet();return null;});}catch(Throwable t){throw new AssertionError(t);}});assertEquals(1,calls.get());assertFalse(target.greenhouseObserverHealthy());}}finally{restorePoison(previous);}
    }
    @Test void nestedRestorationLinkageErrorCannotReplaceNormalOriginalReturn()throws Throwable{
        Object previous=poison();try{for(String name:HANDLERS){var target=receiver();var outer=new VisitorHistoryNativeCallFrame(new Object(),target,null,99);var store=new Store(Fault.RESTORE,outer);install(target,store);var calls=new AtomicInteger();run(target,name,args->{calls.incrementAndGet();return null;});assertEquals(1,calls.get());assertSame(outer,store.actual);assertFalse(target.greenhouseObserverHealthy());}}finally{restorePoison(previous);}
    }
    @Test void originalThrowableIdentityIsPreservedWhenSetupAndCleanupAlsoFail()throws Throwable{
        Object previous=poison();try{for(String name:HANDLERS)for(Fault fault:List.of(Fault.GET,Fault.SET_AFTER_WRITE,Fault.REMOVE,Fault.RESTORE)){var target=receiver();install(target,new Store(fault,fault==Fault.RESTORE?new VisitorHistoryNativeCallFrame(new Object(),target,null,99):null));var calls=new AtomicInteger();var original=new AssertionError("original "+name+" "+fault);try{run(target,name,args->{calls.incrementAndGet();throw original;});fail("Original error was swallowed");}catch(Throwable actual){assertSame(original,actual);}assertEquals(1,calls.get());}}finally{restorePoison(previous);}
    }
    @Test void failedListenerRunsSubsequentOriginalWithoutReenteringObservers()throws Throwable{
        Object previous=poison();try{var target=receiver();install(target,new Store(Fault.GET,null));var calls=new AtomicInteger();run(target,HANDLERS.getFirst(),args->{calls.incrementAndGet();return null;});install(target,new Store(Fault.GET,null));for(String name:HANDLERS)run(target,name,args->{calls.incrementAndGet();return null;});assertEquals(4,calls.get());assertFalse(target.greenhouseObserverHealthy());assertSame(target,poison());}finally{restorePoison(previous);}
    }
    @Test void successfulNestedHandlerRestoresOuterPrivateFrame()throws Throwable{
        Object previous=poison();try{var target=receiver();var store=new ThreadLocal<VisitorHistoryNativeCallFrame>();install(target,store);var calls=new AtomicInteger();run(target,HANDLERS.getFirst(),args->{var outer=store.get();assertNotNull(outer);try{run(target,HANDLERS.get(1),inner->{calls.incrementAndGet();assertNotSame(outer,store.get());return null;});}catch(Throwable failure){throw new AssertionError(failure);}assertSame(outer,store.get());calls.incrementAndGet();return null;});assertEquals(2,calls.get());assertNull(store.get());assertTrue(target.greenhouseObserverHealthy());}finally{restorePoison(previous);}
    }
    @Test void actualObserverHelperContainsEntryExitAndRestorationErrors(){for(Throwable fault:List.of(new ExceptionInInitializerError("entry"),new NoClassDefFoundError("exit"),new LinkageError("restoration"),new OutOfMemoryError("observer allocation"))){var calls=new AtomicInteger();assertFalse(GreenhouseObserverBoundary.run(()->{calls.incrementAndGet();if(fault instanceof Error error)throw error;throw new AssertionError(fault);}));assertEquals(1,calls.get());}}
    @Test void actualObserverHelperRunsSuccessfulObservationExactlyOnce(){var calls=new AtomicInteger();assertTrue(GreenhouseObserverBoundary.run(calls::incrementAndGet));assertEquals(1,calls.get());}
}
