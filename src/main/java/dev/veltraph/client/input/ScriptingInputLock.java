package dev.veltraph.client.input;
















public final class ScriptingInputLock {

    private static final java.util.concurrent.atomic.AtomicLong ownershipVersion=new java.util.concurrent.atomic.AtomicLong();private static volatile boolean versionExhausted;
    private static int pendingMutation,publicationInFlight;
    private static long attemptedMutations,releaseRegistrationVersion;private static boolean attemptHistoryExhausted,releaseRegistrationExhausted;
    private static void observeAttempt(){if(attemptedMutations==Long.MAX_VALUE)attemptHistoryExhausted=true;else attemptedMutations++;}
    private static synchronized void beginAttempt(){observeAttempt();publicationInFlight++;}
    private static synchronized void endAttempt(){publicationInFlight--;}

    private static volatile boolean active;
    private static volatile String ownerModuleId;
    private static volatile Runnable releaseInput=()->{};
    private static dev.veltraph.client.VeltraphBootstrap.RootOriginalControlStartup originalStartup;
    public static void beginOriginalRootInitialization(dev.veltraph.client.VeltraphBootstrap.RootOriginalControlStartup actual){
        if(actual!=null&&actual.actualInitializationEntry())originalStartup=actual;
    }
    public static void finishOriginalRootInitialization(dev.veltraph.client.VeltraphBootstrap.RootOriginalControlStartup actual){
        if(originalStartup==actual&&actual!=null&&actual.actualInitializationExit())originalStartup=null;
    }

    private ScriptingInputLock() {
    }
    /** Inject the platform release without making the ownership model depend on Minecraft. */
    public static void onRelease(Runnable release) {
        var startup=originalStartup;
        ControlSnapshot before=null;
        try{if(startup!=null)before=startup.beforeActualRegistration();}catch(RuntimeException|Error unavailable){}
        boolean normal=false;
        synchronized(ScriptingInputLock.class){if(releaseRegistrationVersion==Long.MAX_VALUE)releaseRegistrationExhausted=true;else releaseRegistrationVersion++;}
        beginAttempt();try{releaseInput=java.util.Objects.requireNonNull(release);normal=true;}finally{endAttempt();}
        if(normal&&startup!=null)try{startup.registrationReturned(before,originalPublicationSnapshot());}catch(RuntimeException|Error unavailable){}
    }

    /** Original callback installation, captured only by actual completed Root initialization. No control/native seed. */
    public static final class OriginalReleaseInstallation {
        private final Runnable callback;private final long version;
        private OriginalReleaseInstallation(){callback=releaseInput;version=releaseRegistrationVersion;}
        public boolean current(){synchronized(ScriptingInputLock.class){return !releaseRegistrationExhausted&&releaseRegistrationVersion==version&&releaseInput==callback;}}
    }
    public static OriginalReleaseInstallation originalReleaseInstallationFrom(
            dev.veltraph.client.VeltraphBootstrap.RootOriginalControlStartup actual){
        if(actual==null||!actual.actualReleaseInstallationCapture())return null;
        synchronized(ScriptingInputLock.class){return publicationInFlight==0&&!releaseRegistrationExhausted?new OriginalReleaseInstallation():null;}
    }
    /** Acquire the lock for {@code moduleId} (a macro started owning input). */
    public static void acquire(String moduleId) {
        boolean log;
        synchronized(ScriptingInputLock.class){observeAttempt();publicationInFlight++;pendingMutation++;if(!active||!java.util.Objects.equals(ownerModuleId,moduleId))advanceOwnershipVersion();log=!active;}
        try{
            if(log)dev.veltraph.client.log.VeltraphLog.details("ScriptingInputLock.acquire","input ownership acquired by "+moduleId);
            synchronized(ScriptingInputLock.class){ownerModuleId=moduleId;active=true;}
        }finally{synchronized(ScriptingInputLock.class){pendingMutation--;publicationInFlight--;}}
    }

    /** Release the lock and all input ownership (stop / disable / disconnect). */
    public static void releaseAll() {
        boolean owned;String previous;
        synchronized(ScriptingInputLock.class){observeAttempt();publicationInFlight++;pendingMutation++;if(active||ownerModuleId!=null)advanceOwnershipVersion();owned=active;previous=ownerModuleId;}
        try{
            try{
                if(owned)dev.veltraph.client.log.VeltraphLog.details("ScriptingInputLock.releaseAll","input ownership released (was "+previous+")");
                synchronized(ScriptingInputLock.class){active=false;ownerModuleId=null;}
            }finally{synchronized(ScriptingInputLock.class){pendingMutation--;}}
            if(owned)releaseInput.run();
        }finally{endAttempt();}
    }

    /** Update using the unchanged generic acquire/release behavior. */
    public static void set(boolean shouldOwn,String moduleId){
        beginAttempt();try{if(shouldOwn)acquire(moduleId);else releaseAll();}finally{endAttempt();}
    }

    public static boolean isActive() {
        return active;
    }

    public static String ownerModuleId() {
        return ownerModuleId;
    }

    /**
     * Whether gameplay mouse-look should be suppressed this frame: only while the lock is held, no
     * screen is open (so UI still works), and a world is present.
     */
    public static boolean shouldSuppressLook(boolean screenOpen, boolean worldPresent) {
        return active && !screenOpen && worldPresent;
    }

    /**
     * Whether physical keyboard movement input should be suppressed this frame. Same rule as look
     * suppression: in-world, no screen open. (Veltraph drives the vanilla keys via the instance
     * {@code setPressed}, which is a different path than the suppressed physical {@code setKeyPressed},
     * so macro-owned movement is unaffected.)
     */
    public static boolean shouldSuppressKeyboard(boolean screenOpen, boolean worldPresent) {
        return active && !screenOpen && worldPresent;
    }
    private static void advanceOwnershipVersion(){while(true){long before=ownershipVersion.get();if(before==Long.MAX_VALUE){versionExhausted=true;return;}if(ownershipVersion.compareAndSet(before,before+1))return;}}
    public static final class ControlSnapshot {
        private final boolean capturedActive;private final String capturedOwner;private final long version,attempt;
        private ControlSnapshot(boolean active,String owner,long version){capturedActive=active;capturedOwner=owner;this.version=version;attempt=attemptedMutations;}
        public boolean current(){synchronized(ScriptingInputLock.class){return pendingMutation==0&&!versionExhausted&&ownershipVersion.get()==version&&active==capturedActive&&java.util.Objects.equals(ownerModuleId,capturedOwner);}}
        /** Strict original-publication history. Generic/native ControlSnapshot semantics remain effective ownership. */
        public boolean publicationCurrent(){synchronized(ScriptingInputLock.class){return publicationInFlight==0&&current()&&!attemptHistoryExhausted&&attemptedMutations==attempt;}}
        public boolean active(){return capturedActive;}public String ownerModuleId(){return capturedOwner;}
        /** Exact attempted-write history, including same values, failures and callback replacement. */
        public boolean sameHistory(ControlSnapshot other){return other!=null&&version==other.version&&attempt==other.attempt
            &&capturedActive==other.capturedActive&&java.util.Objects.equals(capturedOwner,other.capturedOwner);}
        public boolean immediateReturnedSetFrom(ControlSnapshot before){return returnedAttemptsFrom(before,2);}
        public boolean immediateReturnedSingleFrom(ControlSnapshot before){return returnedAttemptsFrom(before,1);}
        private boolean returnedAttemptsFrom(ControlSnapshot before,int exactAttempts){return before!=null&&before.attempt<=Long.MAX_VALUE-exactAttempts
            &&attempt==before.attempt+exactAttempts&&publicationCurrent();}
    }
    public static synchronized ControlSnapshot controlSnapshot(){return versionExhausted||pendingMutation!=0?null:new ControlSnapshot(active,ownerModuleId,ownershipVersion.get());}
    /** Strict producer snapshot unavailable until every publication/callback has returned. */
    public static synchronized ControlSnapshot originalPublicationSnapshot(){return publicationInFlight!=0||attemptHistoryExhausted?null:controlSnapshot();}
    /** Only the actual private Root write object can certify its normal-return publication. */
    public static final class RootOriginalPublicationReceipt {
        private final ControlSnapshot after;
        private RootOriginalPublicationReceipt(ControlSnapshot after){this.after=after;}
        public boolean current(){return after.publicationCurrent();}
        public ControlSnapshot control(){return after;}
    }
    public static RootOriginalPublicationReceipt publishOriginalRootOwnership(
            dev.veltraph.client.VeltraphBootstrap.RootOriginalOwnershipWrite source,boolean shouldOwn,String owner){
        return publishOriginalRootOwnership(source,shouldOwn,owner,false);
    }
    /** A new private issued handoff denies effects when its source is unavailable. */
    public static RootOriginalPublicationReceipt publishIssuedRootOwnership(
            dev.veltraph.client.VeltraphBootstrap.RootOriginalOwnershipWrite source,boolean shouldOwn,String owner){
        return publishOriginalRootOwnership(source,shouldOwn,owner,true);
    }
    private static RootOriginalPublicationReceipt publishOriginalRootOwnership(
            dev.veltraph.client.VeltraphBootstrap.RootOriginalOwnershipWrite source,boolean shouldOwn,String owner,boolean issued){
        var before=originalPublicationSnapshot();
        boolean authentic=false;
        try{authentic=source!=null&&before!=null&&source.beforeCurrent(before,shouldOwn,owner);}catch(RuntimeException|Error unavailable){}
        if(issued&&!authentic)return null;
        set(shouldOwn,owner); // Includes original logging and release callback, outside this class's monitor.
        var after=originalPublicationSnapshot();
        if(!authentic||after==null||!after.immediateReturnedSetFrom(before)||after.active()!=shouldOwn
            ||!java.util.Objects.equals(after.ownerModuleId(),shouldOwn?owner:null))return null;
        try{if(!source.afterCurrent(shouldOwn,owner))return null;}catch(RuntimeException|Error unavailable){return null;}
        synchronized(ScriptingInputLock.class){return after.publicationCurrent()?new RootOriginalPublicationReceipt(after):null;}
    }
    public static RootOriginalPublicationReceipt releaseOriginalRootOwnership(
            dev.veltraph.client.VeltraphBootstrap.RootOriginalOwnershipWrite source){
        var before=originalPublicationSnapshot();boolean authentic=false;
        try{authentic=source!=null&&before!=null&&source.beforeCurrent(before,false,null);}catch(RuntimeException|Error unavailable){}
        releaseAll();
        var after=originalPublicationSnapshot();if(!authentic||after==null||!after.immediateReturnedSingleFrom(before)||after.active()||after.ownerModuleId()!=null)return null;
        try{if(!source.afterCurrent(false,null))return null;}catch(RuntimeException|Error unavailable){return null;}
        synchronized(ScriptingInputLock.class){return after.publicationCurrent()?new RootOriginalPublicationReceipt(after):null;}
    }
    /** Existing G7 retained-helper ABI, sharing every attempted control write and callback boundary. */
    public static final class RootPublicationReceipt {
        private final ControlSnapshot after;
        private RootPublicationReceipt(ControlSnapshot after){this.after=after;}
        public boolean current(){return after.publicationCurrent();}
        /** Same authentic returned history, without capturing new state or accepting caller data. */
        public RootOriginalPublicationReceipt originalPublicationReceipt(){return new RootOriginalPublicationReceipt(after);}
    }
    public static RootPublicationReceipt publishActualRootOwnership(
            dev.veltraph.client.VeltraphBootstrap.RootInputOwnershipPublication source,boolean active,String owner){
        var before=originalPublicationSnapshot();boolean authentic=false;
        try{authentic=source!=null&&before!=null&&source.beforeCurrent(before,active,owner);}catch(RuntimeException|Error unavailable){}
        set(active,owner);
        var after=originalPublicationSnapshot();if(!authentic||after==null||!after.immediateReturnedSetFrom(before)||after.active()!=active
            ||!java.util.Objects.equals(after.ownerModuleId(),active?owner:null))return null;
        try{if(!source.afterCurrent(active,owner))return null;}catch(RuntimeException|Error unavailable){return null;}
        synchronized(ScriptingInputLock.class){return after.publicationCurrent()?new RootPublicationReceipt(after):null;}
    }
    /** Exact canonical manual cause only. Never executes native compatibility release or normal/G7 drop. */
    public static final class RootLogicalRetirementReceipt {
        private final ControlSnapshot before,after;private final Object cause;
        private RootLogicalRetirementReceipt(ControlSnapshot before,ControlSnapshot after,Object cause){this.before=before;this.after=after;this.cause=cause;}
        public boolean matches(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootLogicalInputRetirementCause exact){return original==before&&exact==cause&&after.publicationCurrent();}
        public boolean matches(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootMenuLogicalInputRetirementCause exact){return original==before&&exact==cause&&after.publicationCurrent();}
        public boolean matches(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootNoGameplayLogicalInputRetirementCause exact){return original==before&&exact==cause&&after.publicationCurrent();}
        public ControlSnapshot returnedControl(){return after;}
    }
    private static final class RootLogicalMutationObservation {RootLogicalRetirementReceipt receipt;}
    public static boolean retireActualRootLogical(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootLogicalInputRetirementCause actual){return retireRootLogical(original,actual,null);}
    public static RootLogicalRetirementReceipt retireActualRootLogicalReceipt(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootLogicalInputRetirementCause actual){
        RootLogicalMutationObservation observation=null;try{observation=new RootLogicalMutationObservation();}catch(RuntimeException|Error unavailable){}
        retireRootLogical(original,actual,observation);return observation==null?null:observation.receipt;
    }
    /** Distinct exact Root menu cause. No native release, cancel, provider or logger callback runs here. */
    public static RootLogicalRetirementReceipt retireActualRootMenuLogicalReceipt(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootMenuLogicalInputRetirementCause actual){
        boolean prepared=false;try{prepared=original!=null&&actual!=null&&actual.prepareCurrent(original);}catch(RuntimeException|Error unavailable){}
        synchronized(ScriptingInputLock.class){
            boolean strictBefore=original!=null&&original.publicationCurrent();observeAttempt();
            if(!prepared||!strictBefore||!original.current()||!original.capturedActive||original.capturedOwner==null)return null;
            try{if(!actual.finalCurrent(original))return null;}catch(RuntimeException|Error unavailable){return null;}
            if(ownershipVersion.get()==Long.MAX_VALUE){versionExhausted=true;return null;}
            advanceOwnershipVersion();active=false;ownerModuleId=null;
            try{return new RootLogicalRetirementReceipt(original,new ControlSnapshot(active,ownerModuleId,ownershipVersion.get()),actual);}
            catch(RuntimeException|Error unavailable){return null;}
        }
    }
    /** New closed no-gameplay stop cause; final native/source checks precede this exact owned attempt under the same monitor. */
    public static RootLogicalRetirementReceipt retireActualRootNoGameplayLogicalReceipt(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootNoGameplayLogicalInputRetirementCause actual){
        boolean prepared=false;try{prepared=original!=null&&actual!=null&&actual.prepareCurrent(original);}catch(RuntimeException|Error unavailable){}
        synchronized(ScriptingInputLock.class){
            if(!prepared||original==null||!original.publicationCurrent()||!original.capturedActive||original.capturedOwner==null){observeAttempt();return null;}
            try{if(!actual.finalCurrent(original)){observeAttempt();return null;}}catch(RuntimeException|Error unavailable){observeAttempt();return null;}
            // No callback or native work follows the final veto; count this exact attempt before the logical mutation.
            observeAttempt();
            if(ownershipVersion.get()==Long.MAX_VALUE){versionExhausted=true;return null;}
            advanceOwnershipVersion();active=false;ownerModuleId=null;
            try{return new RootLogicalRetirementReceipt(original,new ControlSnapshot(active,ownerModuleId,ownershipVersion.get()),actual);}
            catch(RuntimeException|Error unavailable){return null;}
        }
    }
    private static boolean retireRootLogical(ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootLogicalInputRetirementCause actual,RootLogicalMutationObservation observation){
        boolean prepared=false;
        try{prepared=original!=null&&actual!=null&&actual.prepareCurrent(original);}catch(RuntimeException|Error unavailable){}
        synchronized(ScriptingInputLock.class){
            boolean strictBefore=original!=null&&original.publicationCurrent();
            observeAttempt();
            if(!prepared||!original.current()||!original.capturedActive||original.capturedOwner==null)return false;
            try{if(!actual.finalCurrent(original))return false;}catch(RuntimeException|Error unavailable){return false;}
            if(ownershipVersion.get()==Long.MAX_VALUE){versionExhausted=true;return false;}
            advanceOwnershipVersion();active=false;ownerModuleId=null;
            if(observation!=null&&strictBefore)try{observation.receipt=new RootLogicalRetirementReceipt(original,new ControlSnapshot(active,ownerModuleId,ownershipVersion.get()),actual);}catch(RuntimeException|Error unavailable){}
            return true;
        }
    }

}
