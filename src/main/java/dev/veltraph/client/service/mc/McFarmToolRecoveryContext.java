package dev.veltraph.client.service.mc;

import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.rewarp.mc.McFarmAutoRecoveryCoordinator;
import dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator;
import java.util.function.Supplier;

/** Negative-only selection routing. Only closed actual coordinator sources can claim one repair. */
public final class McFarmToolRecoveryContext {
    private static final ThreadLocal<Object> SOURCE=new ThreadLocal<>();
    private McFarmToolRecoveryContext() {}
    public static <T>T withSource(Object source,Supplier<T> action) {
        var old=SOURCE.get();SOURCE.set(source);
        try{return action.get();}finally{if(old==null)SOURCE.remove();else SOURCE.set(old);}
    }
    static Object claim(FarmModule farm,McInventoryService inventory) {
        var source=SOURCE.get();
        if(source instanceof McFarmCycleCoordinator.ToolSelectionSource cycle&&cycle.claim(farm,inventory))return cycle;
        if(source instanceof McFarmAutoRecoveryCoordinator.ToolSelectionSource recovery&&recovery.claim(farm,inventory))return recovery;
        return null;
    }
    static boolean current(Object exact,FarmModule farm,McInventoryService inventory) {
        if(exact==null||SOURCE.get()!=exact)return false;
        if(exact instanceof McFarmCycleCoordinator.ToolSelectionSource cycle)return cycle.current(farm,inventory);
        if(exact instanceof McFarmAutoRecoveryCoordinator.ToolSelectionSource recovery)return recovery.current(farm,inventory);
        return false;
    }
}
