package dev.veltraph.client.service.mc;

import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Lower-level value-comparison models only: no Minecraft frame, native scope, phase or receipt is constructed. */
class RootNativeCameraOpcodeBindingIdentityTest {
    private record Frame(Class<?> owner,String method,String descriptor) implements StackWalker.StackFrame {
        @Override public String getClassName(){return owner.getName();}
        @Override public String getMethodName(){return method;}
        @Override public Class<?> getDeclaringClass(){return owner;}
        @Override public MethodType getMethodType(){return MethodType.fromMethodDescriptorString(descriptor,owner.getClassLoader());}
        @Override public String getDescriptor(){return descriptor;}
        @Override public int getByteCodeIndex(){return 0;}
        @Override public String getFileName(){return null;}
        @Override public int getLineNumber(){return -1;}
        @Override public boolean isNativeMethod(){return false;}
        @Override public StackTraceElement toStackTraceElement(){return new StackTraceElement(getClassName(),method,null,-1);}
    }
    private final RootNativeCameraOpcodeBindings.Binding binding=new RootNativeCameraOpcodeBindings.Binding(String.class,
        "exactInjectedHook","(I)V","exactConservedMovedBody309","()V");
    private Frame hook(){return new Frame(String.class,"exactInjectedHook","(I)V");}
    private Frame body(){return new Frame(String.class,"exactConservedMovedBody309","()V");}
    @Test void exactRetainedValuePairMatches(){assertTrue(binding.exact(hook(),body()));}
    @Test void foreignHookOwnerRefuses(){assertFalse(binding.exact(new Frame(Integer.class,"exactInjectedHook","(I)V"),body()));}
    @Test void foreignBodyOwnerRefuses(){assertFalse(binding.exact(hook(),new Frame(Integer.class,"exactConservedMovedBody309","()V")));}
    @Test void hookSuffixDoesNotMatch(){assertFalse(binding.exact(new Frame(String.class,"foreign$exactInjectedHook","(I)V"),body()));}
    @Test void bodySuffixDoesNotMatch(){assertFalse(binding.exact(hook(),new Frame(String.class,"foreign$exactConservedMovedBody309","()V")));}
    @Test void anotherDeferredBodyNumberDoesNotMatch(){assertFalse(binding.exact(hook(),new Frame(String.class,"exactConservedMovedBody310","()V")));}
    @Test void hookDescriptorMustMatch(){assertFalse(binding.exact(new Frame(String.class,"exactInjectedHook","(J)V"),body()));}
    @Test void bodyDescriptorMustMatch(){assertFalse(binding.exact(hook(),new Frame(String.class,"exactConservedMovedBody309","(Z)V")));}
}
