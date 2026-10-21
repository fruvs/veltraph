package dev.veltraph.client.observation.nativeinput;
/** Read-only one real native outer mouse method advance. No history adoption or action permission. */
public interface NativeManualMouseHistoryCarrier {
 boolean nativeManualMouseAdvancedOnce(Object before,Object after);
}
