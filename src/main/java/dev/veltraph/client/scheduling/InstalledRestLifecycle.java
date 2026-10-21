package dev.veltraph.client.scheduling;

import dev.veltraph.client.core.Module;
import java.util.Collection;
import java.util.function.Predicate;

/** Root uses this only after native scheduler validation. No activation or gameplay exemption. */
public final class InstalledRestLifecycle {
    private InstalledRestLifecycle(){}
    public static Module exactPausedOwner(Collection<Module> modules,Predicate<Module> ownsPaused){
        Module found=null;
        for(Module candidate:modules)if(candidate.isEnabled()&&ownsPaused.test(candidate)){
            if(found!=null)return null;found=candidate;
        }
        return found;
    }
    /** Inventory population before return is waiting; after return begins lost proof revokes input. */
    public static boolean permitsReturnUpdate(boolean returnStarted,boolean knownArea,boolean protectedGear,boolean closedMenu){
        return !returnStarted||knownArea&&protectedGear&&closedMenu;
    }
    /** WAITING_TO_DISCONNECT never authorizes replacement or disappearance of native objects. */
    public static boolean permitsNativeTransition(Module exactOwner,boolean expectedWorldTransition){
        return exactOwner!=null&&exactOwner.isEnabled()&&expectedWorldTransition;
    }
}
