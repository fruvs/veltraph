package dev.veltraph.client.rotation;
/** Ordering only: cancellation performs its genuine diagnostic callbacks before final stored receipt verification. */
public final class QueuedRotationCancellationFence {
    private QueuedRotationCancellationFence(){}
    public interface Effects {boolean beforeCancel();void cancel();boolean afterCancel();}
    public static boolean run(Effects effects){java.util.Objects.requireNonNull(effects);if(!effects.beforeCancel())return false;effects.cancel();return effects.afterCancel();}
}
