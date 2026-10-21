package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.core.ModuleManager;
import java.util.function.BooleanSupplier;

/** Rejection-only transaction used by the native standalone adapter; it never mints action permits. */
public final class BazaarStandaloneEffectFence {
    private BazaarStandaloneEffectFence() {}
    public static boolean current(ModuleManager manager,ModuleManager.LifecycleSnapshot before,BooleanSupplier roles,BooleanSupplier nativeLeaves){
        return manager!=null&&before!=null&&before.known()&&manager.lifecycleCurrent(before)
                &&roles.getAsBoolean()&&nativeLeaves.getAsBoolean()&&manager.lifecycleCurrent(before);
    }
}
