package dev.veltraph.client.observation.nativeinput;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.mixin.NativePlayerBirthFabricMergerMixin;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.Packet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual operation seam, deliberately without any native READ/decode/claim custody. Never asserts native acceptance. */
final class NativePlayerBirthFabricPassThroughTest {
    private static final Method ADD;
    static {
        try { ADD = NativePlayerBirthFabricMergerMixin.class.getDeclaredMethod("veltraph$birthFabricPassThroughAdd",
                List.class, Object.class, Operation.class, ChannelHandlerContext.class, Packet.class, List.class); ADD.setAccessible(true); }
        catch (ReflectiveOperationException unavailable) { throw new ExceptionInInitializerError(unavailable); }
    }
    private static Object wrapper() { return new NativePlayerBirthFabricMergerMixin() {}; }
    private static Packet<?> packet() {
        return (Packet<?>) Proxy.newProxyInstance(Packet.class.getClassLoader(), new Class<?>[]{Packet.class},
                (proxy, method, args) -> { throw new AssertionError("Operation seam must not query a synthetic packet"); });
    }
    private static boolean run(Object wrapper, List<?> list, Object output, Operation<Boolean> original, Packet<?> input, List<?> originalList) throws Throwable {
        try { return (Boolean) ADD.invoke(wrapper, list, output, original, null, input, originalList); }
        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
    }
    @Test void originalSamePacketAddAndResultStayUnchangedWithoutGrantingCustody() throws Throwable {
        var input = packet(); var list = new ArrayList<Object>(); var calls = new AtomicInteger();
        assertTrue(run(wrapper(), list, input, args -> { calls.incrementAndGet(); assertSame(list,args[0]); assertSame(input,args[1]); return list.add(args[1]); }, input, list));
        assertEquals(1,calls.get()); assertEquals(1,list.size()); assertSame(input,list.get(0));
        assertNull(NativePlayerBirthWireSource.finalDelivery(input,null));
    }
    @Test void foreignOutputAndForeignListKeepOriginalOperationButCannotMintDelivery() throws Throwable {
        var input = packet(); var output = packet(); var actual = new ArrayList<Object>(); var declared = new ArrayList<Object>();
        var calls = new AtomicInteger();
        assertTrue(run(wrapper(), actual, output, args -> { calls.incrementAndGet(); return actual.add(args[1]); }, input, declared));
        assertEquals(1,calls.get()); assertSame(output,actual.get(0)); assertTrue(declared.isEmpty());
        assertNull(NativePlayerBirthWireSource.finalDelivery(output,null));
        assertNull(NativePlayerBirthWireSource.finalDelivery(input,null));
    }
    @Test void publicBareProducerCannotCreateAnOutputEvenWithSamePacketArguments() {
        var input = packet(); var list = new ArrayList<Object>();
        assertNull(NativePlayerBirthWireSource.beginFabricPassThrough(new Object(),null,input,list,input,list));
        NativePlayerBirthWireSource.finishFabricPassThrough(null,new Object(),null,input,list,input,list,true,true);
        assertNull(NativePlayerBirthWireSource.finalDelivery(input,null)); assertTrue(list.isEmpty());
    }
    @Test void operationReentryPreservesOneOriginalAddPerInvocationWithoutMintingCustody() throws Throwable {
        var input = packet(); var list = new ArrayList<Object>(); var calls = new AtomicInteger(); var object = wrapper();
        assertTrue(run(object,list,input,args -> {
            calls.incrementAndGet();
            try { assertTrue(run(object,list,input,nested -> { calls.incrementAndGet(); return list.add(nested[1]); },input,list)); }
            catch (Throwable original) { throw new AssertionError(original); }
            return list.add(args[1]);
        },input,list));
        assertEquals(2,calls.get()); assertEquals(2,list.size()); assertSame(input,list.get(0)); assertSame(input,list.get(1));
        assertNull(NativePlayerBirthWireSource.finalDelivery(input,null));
    }
    @Test void originalFalseResultDoesNotBecomeSuccessOrDelivery() throws Throwable {
        var input = packet(); var list = new ArrayList<Object>(); var calls = new AtomicInteger();
        assertFalse(run(wrapper(),list,input,args -> { calls.incrementAndGet(); return false; },input,list));
        assertEquals(1,calls.get()); assertTrue(list.isEmpty()); assertNull(NativePlayerBirthWireSource.finalDelivery(input,null));
    }
    @Test void originalErrorEscapesByIdentityAndIsNeverRetried() {
        var input = packet(); var list = new ArrayList<Object>(); var calls = new AtomicInteger(); var error = new AssertionError("original add failed");
        assertSame(error,assertThrows(AssertionError.class,() -> run(wrapper(),list,input,args -> { calls.incrementAndGet(); throw error; },input,list)));
        assertEquals(1,calls.get()); assertTrue(list.isEmpty()); assertNull(NativePlayerBirthWireSource.finalDelivery(input,null));
    }
}
