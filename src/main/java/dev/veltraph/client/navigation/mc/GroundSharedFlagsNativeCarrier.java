package dev.veltraph.client.navigation.mc;
/** Passive write observation only. Public calls cannot create the private original-callsite context. */
public interface GroundSharedFlagsNativeCarrier {
    void observeSharedFlagsAttempt(Object exactData,Object exactItem,Object exactAccessor,Object attemptedValue,int nativeStage);
}
