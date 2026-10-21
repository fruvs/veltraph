package dev.veltraph.client.core;
import java.util.*;
import java.util.function.Predicate;
/** Source-pinned original private role-loop shapes, no Root/SDK/native authority. */
final class FrozenASControllerLoops {
    static Optional<Module> owner(ArrayList<Module> enabled){
        for(Module module:enabled) {
            if(!module.isPassiveObserver()&&module.ownsGameplayInput())return Optional.of(module);
        }
        return Optional.empty();
    }
    static int count(ArrayList<Module> enabled){
        int count=0;
        for (Module module : enabled) {
            if (!module.isPassiveObserver()) count++;
        }
        return count;
    }
    static Module first(ArrayList<Module> enabled){
        Module first=null;
        for (Module module : enabled) {
            boolean passive = module.isPassiveObserver();
            if (!passive && first == null) first = module;
        }
        if(first==null)throw new NoSuchElementException();return first;
    }
    static ModuleManager.UniqueOwnerScan unique(ArrayList<Module> snapshot,Predicate<Module> warpRefused){
        for(Module module:snapshot)java.util.Objects.requireNonNull(module);
        Module owner=null;
        for(Module module:snapshot){
            if(!module.isEnabled()||module.isPassiveObserver()||!module.ownsGameplayInput())continue;
            if(owner!=null)return new ModuleManager.UniqueOwnerScan(ModuleManager.UniqueOwnerStatus.AMBIGUOUS,null);
            if(warpRefused.test(module))return new ModuleManager.UniqueOwnerScan(ModuleManager.UniqueOwnerStatus.WARP_REFUSED,null);
            owner=module;
        }
        return new ModuleManager.UniqueOwnerScan(owner==null?ModuleManager.UniqueOwnerStatus.NONE:ModuleManager.UniqueOwnerStatus.OWNER,owner);
    }
}
