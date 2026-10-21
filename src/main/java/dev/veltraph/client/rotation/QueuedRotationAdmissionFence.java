package dev.veltraph.client.rotation;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
/** Read-only ordering only: provider reentry cannot authorize adoption of changed original native state. */
public final class QueuedRotationAdmissionFence {
    private QueuedRotationAdmissionFence(){}
    public static <T> T observe(Supplier<T> provider,Predicate<T> expectedScope,BooleanSupplier originalNativeCurrent){
        java.util.Objects.requireNonNull(provider);java.util.Objects.requireNonNull(expectedScope);java.util.Objects.requireNonNull(originalNativeCurrent);
        T observed=provider.get();return observed!=null&&expectedScope.test(observed)&&originalNativeCurrent.getAsBoolean()?observed:null;
    }
}
