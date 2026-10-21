package dev.veltraph.client.mixinbootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.tree.ClassNode;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
@ResourceLock("SYSTEM_ERR")
class RootNativeTickIngressOriginalFailureDiagnosticTest {
    private static ClassNode target(){var node=new ClassNode();node.name="net/minecraft/client/Minecraft";return node;}
    @Test void successfulBindingRunsOnceAndLogsNothing(){
        var before=System.err;var bytes=new ByteArrayOutputStream();var calls=new AtomicInteger();
        try{System.setErr(new PrintStream(bytes));RootNativeTickIngressPlugin.observeBindingBody(target(),calls::incrementAndGet);assertEquals(1,calls.get());assertEquals("",bytes.toString());}finally{System.setErr(before);}
    }
    @Test void originalRuntimeExceptionIdentityAndStackRemainAndTargetIsLogged(){
        var before=System.err;var bytes=new ByteArrayOutputStream();var calls=new AtomicInteger();var original=new IllegalStateException("exact original binding guard");var stack=original.getStackTrace();
        try{System.setErr(new PrintStream(bytes));var thrown=assertThrows(IllegalStateException.class,()->RootNativeTickIngressPlugin.observeBindingBody(target(),()->{calls.incrementAndGet();throw original;}));assertSame(original,thrown);assertEquals(1,calls.get());assertArrayEquals(stack,thrown.getStackTrace());assertEquals(0,thrown.getSuppressed().length);assertTrue(bytes.toString().contains("target=net/minecraft/client/Minecraft"));assertTrue(bytes.toString().contains("exact original binding guard"));}finally{System.setErr(before);}
    }
    @Test void originalErrorSurvivesFailingLoggerWithoutWrappingOrSuppression(){
        var before=System.err;var original=new AssertionError("original Error");var logger=new LinkageError("logger Error");var stack=original.getStackTrace();var calls=new AtomicInteger();
        try{System.setErr(new PrintStream(new ByteArrayOutputStream()){@Override public void println(String line){throw logger;}});var thrown=assertThrows(AssertionError.class,()->RootNativeTickIngressPlugin.observeBindingBody(target(),()->{calls.incrementAndGet();throw original;}));assertSame(original,thrown);assertEquals(1,calls.get());assertArrayEquals(stack,thrown.getStackTrace());assertEquals(0,thrown.getSuppressed().length);}finally{System.setErr(before);}
    }
    @Test void originalRuntimeFailureSurvivesStackPrinterError(){
        var before=System.err;var failed=new java.util.concurrent.atomic.AtomicBoolean();
        var original=new IllegalStateException("original"){@Override public void printStackTrace(PrintStream output){failed.set(true);throw new AssertionError("stack logger failed");}};var stack=original.getStackTrace();
        try{System.setErr(new PrintStream(new ByteArrayOutputStream()));assertSame(original,assertThrows(IllegalStateException.class,()->RootNativeTickIngressPlugin.observeBindingBody(target(),()->{throw original;})));assertTrue(failed.get());assertArrayEquals(stack,original.getStackTrace());assertEquals(0,original.getSuppressed().length);}finally{System.setErr(before);}
    }
}
