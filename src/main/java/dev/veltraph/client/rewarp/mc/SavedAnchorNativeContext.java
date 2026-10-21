package dev.veltraph.client.rewarp.mc;
import dev.veltraph.client.core.ModuleManager;
/** Callback-free refusal from actual captured Root lifecycle and exact native objects. Never a lease. */
final class SavedAnchorNativeContext {
    record Scene(ModuleManager registry,ModuleManager.LifecycleSnapshot lifecycle,long generation,Object target,
                 Object player,Object world,Object listener,Object nativeConnection) {}
    /** Data only. Native custody is held privately by the original arrived Journey capsule. */
    record DisablePose(double x,double y,double z,float yaw,float pitch,Object posture,int slot){}
    static boolean unchangedDisablePose(DisablePose before,DisablePose after){
        return before!=null&&after!=null&&Double.isFinite(before.x())&&Double.isFinite(before.y())&&Double.isFinite(before.z())
                &&Float.isFinite(before.yaw())&&Float.isFinite(before.pitch())&&before.posture()!=null&&before.slot()>=0&&before.slot()<9
                &&Double.doubleToLongBits(before.x())==Double.doubleToLongBits(after.x())
                &&Double.doubleToLongBits(before.y())==Double.doubleToLongBits(after.y())
                &&Double.doubleToLongBits(before.z())==Double.doubleToLongBits(after.z())
                &&Float.floatToIntBits(before.yaw())==Float.floatToIntBits(after.yaw())
                &&Float.floatToIntBits(before.pitch())==Float.floatToIntBits(after.pitch())
                &&before.posture()==after.posture()&&before.slot()==after.slot();
    }
    /** Original farm gameplay keys only; durable helper journals do not renew or revoke this snapshot. */
    static final class FarmSettings {
        private final dev.veltraph.client.config.ConfigService store;
        private final dev.veltraph.client.config.MapConfigService.StoredKeysSnapshot snapshot;
        private FarmSettings(dev.veltraph.client.config.ConfigService store,dev.veltraph.client.config.MapConfigService.StoredKeysSnapshot snapshot){this.store=store;this.snapshot=snapshot;}
        boolean current(dev.veltraph.client.config.ConfigService original){return store==original&&snapshot.current();}
    }
    static FarmSettings captureFarmSettings(dev.veltraph.client.config.ConfigService store){
        try{
            var keys=new java.util.ArrayList<dev.veltraph.client.config.ConfigKey<?>>();
            for(var field:dev.veltraph.client.config.FarmingConfig.class.getFields())
                if(field.get(null) instanceof dev.veltraph.client.config.ConfigKey<?> key)keys.add(key);
            var snapshot=store instanceof dev.veltraph.client.config.MapConfigService map?map.storedKeysSnapshot(keys)
                    :store instanceof dev.veltraph.client.config.JsonConfigService json?json.storedKeysSnapshot(keys):null;
            return snapshot==null?null:new FarmSettings(store,snapshot);
        }catch(IllegalAccessException|RuntimeException unavailable){return null;}
    }
    private SavedAnchorNativeContext(){}
    private static boolean sameNative(Scene expected,Scene actual){
        return expected!=null&&actual!=null&&expected.registry()!=null&&expected.registry()==actual.registry()
                &&expected.lifecycle()!=null&&expected.lifecycle()==actual.lifecycle()&&expected.lifecycle().known()
                &&expected.generation()>=0&&expected.generation()==actual.generation()
                &&expected.target()!=null&&expected.target()==actual.target()
                &&expected.player()!=null&&expected.player()==actual.player()&&expected.world()!=null&&expected.world()==actual.world()
                &&expected.listener()!=null&&expected.listener()==actual.listener()
                &&expected.nativeConnection()!=null&&expected.nativeConnection()==actual.nativeConnection();
    }
    static boolean current(Scene expected,Scene actual){
        return sameNative(expected,actual)&&expected.registry().lifecycleCurrent(expected.lifecycle());
    }
    /** Read-only arrived-pose observation after one authentic completed helper disable.
     * Does not grant movement, pending cleanup, or continuation from a replacement lifecycle. */
    static boolean arrivedAfterOwnDisable(Scene expected,Scene actual,dev.veltraph.client.core.Module exactHelper){
        return sameNative(expected,actual)&&exactHelper!=null
                &&expected.registry().completedOwnDisableFrom(expected.lifecycle(),exactHelper).isPresent();
    }

}
