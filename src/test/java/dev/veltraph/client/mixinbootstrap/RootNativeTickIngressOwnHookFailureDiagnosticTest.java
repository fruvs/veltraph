package dev.veltraph.client.mixinbootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
@ResourceLock("SYSTEM_ERR")
class RootNativeTickIngressOwnHookFailureDiagnosticTest {
    private static ClassNode target(){var node=new ClassNode();node.name="net/minecraft/client/Minecraft";return node;}
    private static ClassNode failureTarget(RuntimeException original,AtomicInteger reads){var node=target();node.methods=new AbstractList<MethodNode>(){public int size(){return 1;}public MethodNode get(int index){reads.incrementAndGet();throw original;}};return node;}
    @Test void unrelatedOriginalPreAndPostHooksStaySilentAndLeaveTargetUntouched(){
        var stderr=System.err;var bytes=new ByteArrayOutputStream();var node=target();var plugin=new RootNativeTickIngressPlugin();
        try{System.setErr(new PrintStream(bytes));plugin.preApply("target",node,"private.unrelated.Mixin",null);plugin.postApply("target",node,"private.unrelated.Mixin",null);assertTrue(node.methods.isEmpty());assertEquals("",bytes.toString());}finally{System.setErr(stderr);}
    }
    @Test void originalOwnPreApplyFailureIsLoggedAndRethrownOnceWithExactIdentity(){
        var stderr=System.err;var bytes=new ByteArrayOutputStream();var original=new IllegalArgumentException("original preApply list failure");var reads=new AtomicInteger();var stack=original.getStackTrace();
        try{System.setErr(new PrintStream(bytes));var plugin=new RootNativeTickIngressPlugin();assertSame(original,assertThrows(IllegalArgumentException.class,()->plugin.preApply("target",failureTarget(original,reads),"dev.veltraph.client.mixin.RootNativeSdlClientMixin",null)));assertEquals(1,reads.get());assertArrayEquals(stack,original.getStackTrace());assertEquals(0,original.getSuppressed().length);assertTrue(bytes.toString().contains("VELTRAPH_ORIGINAL_BINDING_FAILURE target=net/minecraft/client/Minecraft"));assertTrue(bytes.toString().contains("original preApply list failure"));}finally{System.setErr(stderr);}
    }
    @Test void originalOwnPostApplyFailureIsLoggedAndRethrownOnceWithExactIdentity(){
        var stderr=System.err;var bytes=new ByteArrayOutputStream();var original=new IllegalStateException("original postApply list failure");var reads=new AtomicInteger();var stack=original.getStackTrace();
        try{System.setErr(new PrintStream(bytes));var plugin=new RootNativeTickIngressPlugin();assertSame(original,assertThrows(IllegalStateException.class,()->plugin.postApply("target",failureTarget(original,reads),"dev.veltraph.client.mixin.RootNativeGameplayTickMixin",null)));assertEquals(1,reads.get());assertArrayEquals(stack,original.getStackTrace());assertEquals(0,original.getSuppressed().length);assertTrue(bytes.toString().contains("original postApply list failure"));}finally{System.setErr(stderr);}
    }
    @Test void loggerErrorCannotReplaceOriginalOwnHookFailure(){
        var stderr=System.err;var original=new IllegalStateException("original own hook");var reads=new AtomicInteger();var logFailed=new java.util.concurrent.atomic.AtomicBoolean();
        try{System.setErr(new PrintStream(new ByteArrayOutputStream()){@Override public void println(String message){logFailed.set(true);throw new LinkageError("logger");}});var plugin=new RootNativeTickIngressPlugin();assertSame(original,assertThrows(IllegalStateException.class,()->plugin.preApply("target",failureTarget(original,reads),"dev.veltraph.client.mixin.RootNativeSdlClientMixin",null)));assertEquals(1,reads.get());assertTrue(logFailed.get());assertEquals(0,original.getSuppressed().length);}finally{System.setErr(stderr);}
    }
}
