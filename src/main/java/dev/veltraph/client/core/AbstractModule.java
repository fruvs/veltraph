package dev.veltraph.client.core;

/**
 * Base {@link Module} implementation handling identity and the enable/disable lifecycle. Subclasses
 * override {@link #onEnable()}, {@link #onDisable()}, and {@link #onTick()}.
 */
public abstract class AbstractModule implements Module {

    private final String id;
    private final String displayName;
    private final ModuleCategory category;

    private boolean enabled;
    private ModuleManager ownedRegistry;
    private boolean ownedRegistryAmbiguous;
    private long lifecycleVersion,stateVersion;private boolean stateVersionExhausted;
    private boolean lifecycleVersionExhausted;
    private MacroState state = MacroState.STOPPED;

    protected AbstractModule(String id, String displayName, ModuleCategory category) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final ModuleCategory category() {
        return category;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final MacroState state() {
        return state;
    }

    @Override
    public final void setEnabled(boolean enabled) {
        if (enabled == this.enabled) {
            return;
        }
        var ownedInvocation=enabled&&!ownedRegistryAmbiguous&&ownedRegistry!=null?ownedRegistry.beforeActualEnable(this):null;
        var ownedDisable=!enabled&&!ownedRegistryAmbiguous&&ownedRegistry!=null?ownedRegistry.beforeActualDisable(this):null;
        this.enabled = enabled;
        observeLifecycleMutation();
        if (enabled) {
            state = MacroState.RUNNING;
            boolean completed=false;
            try {
                dev.veltraph.client.log.VeltraphLog.basic(displayName, "macro started");
                if(ownedInvocation!=null)ownedRegistry.enterActualEnable(ownedInvocation);
                onEnable();completed=true;
            }finally {if(ownedInvocation!=null)ownedRegistry.finishActualEnable(ownedInvocation,completed);}
        } else {
            boolean completed=false;
            try {
                onDisable();
                state = MacroState.STOPPED;
                dev.veltraph.client.log.VeltraphLog.basic(displayName, "macro stopped");
                if(ownedDisable!=null){ownedRegistry.enterDisableNormalReturned(ownedDisable);var receipt=ownedRegistry.currentOwnDisableReceipt(this).orElse(null);if(receipt!=null)postDisableNormalReturned(receipt);}
                completed=true;
            }finally{if(ownedDisable!=null)ownedRegistry.finishActualDisable(ownedDisable,completed);}
            if(ownedDisable!=null)ownedRegistry.observeManualDisableNormalReturned(ownedDisable);
        }
    }

    /** Subclasses (or the manager) may set a finer-grained run state for display. */
    protected final void setState(MacroState newState) {
        if(this.state!=newState){if(stateVersion==Long.MAX_VALUE)stateVersionExhausted=true;else stateVersion++;}
        this.state = newState;
    }

    /** Actual post-return observation only. Implementations must not publish keys/packets/gameplay or perform restoration here. */
    protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt receipt){}
    /** Registry association only; ambiguity refuses scoped receipts without changing ordinary lifecycle behavior. */
    final void attachOwnedRegistry(ModuleManager registry){if(ownedRegistry==null)ownedRegistry=registry;else if(ownedRegistry!=registry)ownedRegistryAmbiguous=true;}
    private void observeLifecycleMutation(){
        if(lifecycleVersion==Long.MAX_VALUE)lifecycleVersionExhausted=true;else lifecycleVersion++;
    }
    /** Authentic stored lifecycle fields, without virtual role/getter callbacks. */
    private LifecycleStamp reusableStoredData;
    final LifecycleStamp storedLifecycle(){
        // The original constructor's live reads, in their original order, occur on EVERY invocation.
        String observedId=id;boolean observedEnabled=enabled;MacroState observedState=state;
        long observedVersion=lifecycleVersion,observedStateVersion=stateVersion;boolean observedKnown=!lifecycleVersionExhausted&&!stateVersionExhausted;
        var prior=reusableStoredData;
        if(observedKnown&&prior!=null&&prior.module==this&&java.util.Objects.equals(prior.id,observedId)&&prior.enabled==observedEnabled
            &&prior.state==observedState&&prior.version==observedVersion&&prior.stateVersion==observedStateVersion&&prior.known==observedKnown)return prior;
        var observed=new LifecycleStamp(this,observedId,observedEnabled,observedState,observedVersion,observedStateVersion,observedKnown);
        reusableStoredData=observed;return observed;
    }
    /** Exact live private stamp comparison avoids allocating a replacement stamp in every native leaf. */
    final boolean storedLifecycleCurrent(LifecycleStamp captured){return captured!=null&&captured.module==this&&java.util.Objects.equals(captured.id,id)
        &&captured.enabled==enabled&&captured.state==state&&captured.version==lifecycleVersion&&captured.stateVersion==stateVersion&&captured.known&&!lifecycleVersionExhausted&&!stateVersionExhausted;}
    /** Cleanup-only old actor identity/version. State changes do not create a new enable lifetime. */
    final boolean storedEnableLifetimeCurrent(LifecycleStamp captured){return captured!=null&&captured.module==this&&captured.known&&captured.enabled&&enabled
        &&java.util.Objects.equals(captured.id,id)&&captured.version==lifecycleVersion&&!lifecycleVersionExhausted&&!ownedRegistryAmbiguous;}
    /** Exact enabled field, version and state lineage from a completed actual enable. */
    final boolean storedEnableHistoryCurrent(LifecycleStamp captured){return captured!=null&&captured.module==this&&captured.enabled&&enabled
        &&java.util.Objects.equals(captured.id,id)&&captured.version==lifecycleVersion&&captured.stateVersion==stateVersion&&captured.state==state&&captured.known&&!lifecycleVersionExhausted&&!stateVersionExhausted
        &&!ownedRegistryAmbiguous;}
    public static final class LifecycleStamp {
        private final AbstractModule module;private final String id;private final boolean enabled,known;
        private final MacroState state;private final long version,stateVersion;
        private LifecycleStamp(AbstractModule module,String id,boolean enabled,MacroState state,long version,long stateVersion,boolean known){this.module=module;this.id=id;this.enabled=enabled;this.state=state;this.version=version;this.stateVersion=stateVersion;this.known=known;}
        public boolean enabled(){return enabled;}public boolean known(){return known;}
        boolean ownEnableTo(LifecycleStamp other){return other!=null&&known&&other.known&&module==other.module&&java.util.Objects.equals(id,other.id)
            &&!enabled&&state==MacroState.STOPPED&&other.enabled&&other.state==MacroState.RUNNING&&version<Long.MAX_VALUE&&other.version==version+1;}
        boolean ownDisableInProgressTo(LifecycleStamp other){return other!=null&&known&&other.known&&module==other.module&&java.util.Objects.equals(id,other.id)
            &&enabled&&state==MacroState.RUNNING&&!other.enabled&&(other.state==MacroState.RUNNING||other.state==MacroState.STOPPED)
            &&version<Long.MAX_VALUE&&other.version==version+1;}
        boolean manualDisableTo(LifecycleStamp other){return known&&other!=null&&other.known&&module==other.module&&id.equals(other.id)
            &&enabled&&!other.enabled&&other.state==MacroState.STOPPED&&version<Long.MAX_VALUE&&other.version==version+1;}
        boolean ownDisableTo(LifecycleStamp other){return other!=null&&known&&other.known&&module==other.module&&java.util.Objects.equals(id,other.id)
            &&enabled&&state==MacroState.RUNNING&&!other.enabled&&other.state==MacroState.STOPPED&&version<Long.MAX_VALUE&&other.version==version+1;}
        /** Data-only exact normal pause transition; never gameplay or resume permission. */
        boolean scheduledPauseTo(LifecycleStamp other){return other!=null&&known&&other.known&&module==other.module&&java.util.Objects.equals(id,other.id)
            &&enabled&&other.enabled&&version==other.version&&state==MacroState.RUNNING&&other.state==MacroState.PAUSED
            &&stateVersion<Long.MAX_VALUE&&other.stateVersion==stateVersion+1;}
        boolean same(LifecycleStamp other){return other!=null&&module==other.module&&java.util.Objects.equals(id,other.id)&&enabled==other.enabled&&state==other.state&&version==other.version&&stateVersion==other.stateVersion&&known&&other.known;}
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onTick() {
    }
}
