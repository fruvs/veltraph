package dev.veltraph.client.rewarp.mc;

/** Identity-only lifetime for the actual coordinator's private pause source. Never grants native input. */
final class CycleCameraHandoff {
    static final class Attempt {
        private final Object source,farm;
        private boolean revoked,completed;
        private Attempt(Object source,Object farm){this.source=source;this.farm=farm;}
    }
    /** Logical retirement of one captured activation only; never an input/rotation grant. */
    static final class Retirement {
        private final dev.veltraph.client.core.ModuleManager manager;
        private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.core.Module module;
        private final Thread thread=Thread.currentThread();
        private boolean consumed;
        private Retirement(dev.veltraph.client.core.ModuleManager manager,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,dev.veltraph.client.core.Module module){this.manager=manager;this.lifecycle=lifecycle;this.module=module;}
        boolean retire(){
            if(consumed||Thread.currentThread()!=thread)return false;
            consumed=true;
            if(manager==null||module==null||!manager.originalActorLifecycleCurrent(lifecycle,module)||!module.isEnabled())return false;
            module.setEnabled(false);return true;
        }
    }
    static Retirement retirement(dev.veltraph.client.core.ModuleManager manager,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot original,dev.veltraph.client.core.Module module){return new Retirement(manager,original,module);}
    /** Actual coordinator catch sequence: preserve cancellation errors and never retry a new activation. */
    static void retireAfterFailure(Retirement original,Runnable cancel){cancel.run();if(original!=null)original.retire();}
    private Attempt pending;
    Attempt begin(Object source,Object farm){
        if(pending!=null){cancel();return null;}
        if(source==null||farm==null)return null;
        return pending=new Attempt(source,farm);
    }
    Object pendingSource(){return pending!=null&&!pending.revoked&&!pending.completed?pending.source:null;}
    Object originalFarm(Attempt exact){return pending==exact&&exact!=null&&!exact.revoked&&!exact.completed?exact.farm:null;}
    boolean complete(Attempt exact){
        if(pending!=exact||exact==null||exact.revoked||exact.completed)return false;
        exact.completed=true;return true;
    }
    void retire(Attempt exact){if(exact!=null)exact.revoked=true;if(pending==exact)pending=null;}
    void cancel(){var old=pending;pending=null;if(old!=null)old.revoked=true;}
}
