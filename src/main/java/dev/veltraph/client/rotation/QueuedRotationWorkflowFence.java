package dev.veltraph.client.rotation;
import java.util.function.BooleanSupplier;
/** Ordering of refusal leaves only; none of these booleans creates camera authority. */
public final class QueuedRotationWorkflowFence {
    private QueuedRotationWorkflowFence(){}
    public static boolean allowed(BooleanSupplier workflow,BooleanSupplier nativeLeaf,BooleanSupplier storedNativeLeaf){
        java.util.Objects.requireNonNull(workflow);java.util.Objects.requireNonNull(nativeLeaf);java.util.Objects.requireNonNull(storedNativeLeaf);
        return workflow.getAsBoolean()&&nativeLeaf.getAsBoolean()&&storedNativeLeaf.getAsBoolean();
    }
}
