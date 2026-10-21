package dev.veltraph.client.core;

/** Private stored parent AND unregistered-child lifecycle witness; callbacks cannot fabricate its version. */
public final class CombatFinishingLifecycle {
    private final AbstractModule parent,child;
    private final AbstractModule.LifecycleStamp parentStamp,childStamp;
    private CombatFinishingLifecycle(AbstractModule parent,AbstractModule child) {
        this.parent=parent;this.child=child;parentStamp=parent.storedLifecycle();childStamp=child.storedLifecycle();
    }
    public static CombatFinishingLifecycle capture(AbstractModule parent,AbstractModule child) {
        if(parent==null||child==null||parent==child)return null;
        var witness=new CombatFinishingLifecycle(parent,child);
        return witness.parentStamp.known()&&witness.childStamp.known()
                &&witness.parentStamp.enabled()&&witness.childStamp.enabled()?witness:null;
    }
    /** This observation alone is not cleanup authority; native cleanup additionally requires CoreB's real normal-return receipt. */
    public boolean childStoppedNormally(){return childStamp.ownDisableTo(child.storedLifecycle());}
    /** Private stored transitions from the captured run, including the actual onDisable body before its final STOPPED state. */
    public boolean childStoppingWithCurrentParent(){return parentStamp.same(parent.storedLifecycle())&&childStamp.ownDisableInProgressTo(child.storedLifecycle());}
    public boolean parentAndChildStopping(){return parentStamp.ownDisableInProgressTo(parent.storedLifecycle())&&childStamp.ownDisableInProgressTo(child.storedLifecycle());}
    public boolean current() { return parentStamp.same(parent.storedLifecycle())&&childStamp.same(child.storedLifecycle()); }
}
