package dev.veltraph.client.service.mc;
/** Native private mixin state; public observations expose no mutable version object or input authority. */
public interface NormalIdleKeyCarrier {
 default String cachedNormalIdleKeyDiagnostic(){return "UNKNOWN_CARRIER";}
 default void watchNormalIdleAttemptOrigin(Object exactSnapshot){}
 /** Observation only: one normal outer unscoped setter, never restart/publication permission. */
 default boolean normalIdleUnscopedAdvanced(Object before,Object after){return false;}
 default boolean normalIdleCanonicalToggleAdvanced(Object before,Object after,McHeldKeys.CanonicalTogglePublication actual){return false;}
 /** Negative-only unowned-attempt observation; missing native history refuses. */
 default Object normalIdleUnownedSnapshot(){return null;}
 default boolean normalIdleUnownedCurrent(Object exactSnapshot){return false;}
 Object normalIdleKeySnapshot();boolean normalIdleKeyCurrent(Object exactSnapshot);
 boolean normalIdleKeyAdvanced(Object before,Object after,java.util.List<?> actualIssuedInvocations);
 default boolean normalIdleKeyInFlightCurrent(Object original,java.util.List<?> returned,Object actualPending,int exactDepth){return false;}
 void normalIdleNativeBegin(boolean requested,boolean restoration);void normalIdleNativeReturned();
}
