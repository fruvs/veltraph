package dev.veltraph.client.navigation;

/** Failure-only scalar observer. Pass-through methods never decide admission. All native/query
 * results are supplied from original evaluations; no callbacks, clocks, world reads or lists. */
public final class GroundPublicationFailureCache {
    private GroundPublicationFailureCache() {}
    private static boolean active,directionalQualified;
    private static long visitedLow,visitedHigh,trueLow,trueHigh;
    private static String first,outer;
    private static CapturedProfileTrajectory.Input attemptedInput;
    private static CapturedProfileTrajectory.JumpIntent attemptedJump;
    private static CapturedProfileTrajectory.SprintIntent attemptedSprint;
    private static CapturedProfileTrajectory.Phase attemptedPhase;
    private static CapturedProfileTrajectory.Input input;
    private static CapturedProfileTrajectory.JumpIntent jump;
    private static CapturedProfileTrajectory.SprintIntent sprint;
    private static CapturedProfileTrajectory.Phase phase;
    private static float poseYaw,queuedYaw;private static boolean hasPoseYaw,hasQueuedYaw,hasPending,pending,hasFence,available;
    private static long budget,elapsed;
    private static long scopeBegan,firstQueryPre,lastQueryPre,lastQueryPost,initialJournalLastSample,proofCreationLastSample,firstFinalPublication;
    private static int clockMask,deadlineMask;private static boolean pendingDeadlineObserved;private static long pendingDeadlineNano;
    private static final long[] deadlineNanos=new long[5];
    /** Every value is copied from an existing original clock observation, never sampled here. */
    public static void clockPhases(long originalBegan,long queryFirst,long queryLast,long queryPost,long journalLast,long creationLast,long finalFirst,
            boolean preSeen,boolean postSeen,boolean journalSeen,boolean creationSeen,boolean finalSeen){
        if(active){scopeBegan=originalBegan;firstQueryPre=queryFirst;lastQueryPre=queryLast;lastQueryPost=queryPost;
            initialJournalLastSample=journalLast;proofCreationLastSample=creationLast;firstFinalPublication=finalFirst;
            clockMask=1|(preSeen?2:0)|(postSeen?4:0)|(journalSeen?8:0)|(creationSeen?16:0)|(finalSeen?32:0);}
    }
    public static void deadlineBegin(){if(active)pendingDeadlineObserved=false;}
    public static void deadlineNano(long originalNano){if(active){pendingDeadlineObserved=true;pendingDeadlineNano=originalNano;}}
    public static boolean deadlineSite(int site,boolean original){if(active){deadlineMask&=~(1<<site);if(pendingDeadlineObserved){deadlineNanos[site]=pendingDeadlineNano;deadlineMask|=1<<site;}}return original;}
    private static Long phaseValue(int bit,long value){return (clockMask&bit)!=0?value:null;}
    private static Long deadlineValue(int site){return (deadlineMask&(1<<site))!=0?deadlineNanos[site]:null;}
    private static GroundPublicationObservedOriginalClocks originalClocks(){return new GroundPublicationObservedOriginalClocks(
            phaseValue(1,scopeBegan),phaseValue(2,firstQueryPre),phaseValue(2,lastQueryPre),phaseValue(4,lastQueryPost),
            phaseValue(8,initialJournalLastSample),phaseValue(16,proofCreationLastSample),phaseValue(32,firstFinalPublication),
            deadlineValue(0),deadlineValue(1),deadlineValue(2),deadlineValue(3),deadlineValue(4));}
    public static void begin(){active=true;directionalQualified=false;visitedLow=visitedHigh=trueLow=trueHigh=0;first=outer=null;attemptedInput=null;attemptedJump=null;attemptedSprint=null;attemptedPhase=null;input=null;jump=null;sprint=null;phase=null;hasPoseYaw=hasQueuedYaw=hasPending=hasFence=false;clockMask=deadlineMask=0;pendingDeadlineObserved=false;}
    /** Metadata from the already-held private Decision, after original first guard. No native/role reads. */
    public static void attempt(CapturedProfileTrajectory.Input heldInput,CapturedProfileTrajectory.JumpIntent heldJump,
            CapturedProfileTrajectory.SprintIntent heldSprint,CapturedProfileTrajectory.Phase heldPhase){
        if(active){attemptedInput=heldInput;attemptedJump=heldJump;attemptedSprint=heldSprint;attemptedPhase=heldPhase;}
    }
    /** Cached private Decision context only; no source, native, world, provider or clock read. */
    public static void directionalContext(boolean fromPrivateEnvelope){if(active)directionalQualified=fromPrivateEnvelope;}
    private static String diagnosticContext(String value){return directionalQualified?"DIRECTIONAL_QUALIFIED::"+value:value;}
    public static boolean copy(int slot,boolean value){if(active){if(slot<64){visitedLow|=1L<<slot;if(value)trueLow|=1L<<slot;else trueLow&=~(1L<<slot);}else{visitedHigh|=1L<<(slot-64);if(value)trueHigh|=1L<<(slot-64);else trueHigh&=~(1L<<(slot-64));}}return value;}
    public static void returned(String site){if(active){if(first==null)first=diagnosticContext(site);outer=diagnosticContext(site);}}
    public static float poseYaw(float value){if(active){poseYaw=value;hasPoseYaw=true;}return value;}
    public static float queuedYaw(float value){if(active){queuedYaw=value;hasQueuedYaw=true;}return value;}
    public static boolean queuedPending(boolean value){if(active){pending=value;hasPending=true;}return value;}
    public static CapturedProfileTrajectory.Input input(CapturedProfileTrajectory.Input value){if(active)input=value;return value;}
    public static CapturedProfileTrajectory.JumpIntent jump(CapturedProfileTrajectory.JumpIntent value){if(active)jump=value;return value;}
    public static CapturedProfileTrajectory.SprintIntent sprint(CapturedProfileTrajectory.SprintIntent value){if(active)sprint=value;return value;}
    public static CapturedProfileTrajectory.Phase phase(CapturedProfileTrajectory.Phase value){if(active)phase=value;return value;}
    public static void fence(long originalBudget,long originalElapsed,boolean originalResult,boolean evaluated){if(active&&evaluated){hasFence=true;budget=originalBudget;elapsed=originalElapsed;available=originalResult;}}
    public static void success(){active=false;}
    public static GroundPublicationFailureSnapshot finish(String reason,GroundPublicationFailureSnapshot earlier){return active?finish(reason):earlier;}
    public static GroundPublicationFailureSnapshot finish(String reason){
        if(!active)return null;
        active=false;
        return new GroundPublicationFailureSnapshot(first,outer,diagnosticContext(reason),visitedLow,visitedHigh,trueLow,trueHigh,
                attemptedInput,attemptedJump,attemptedSprint,attemptedPhase,input,jump,sprint,phase,hasPoseYaw?poseYaw:null,hasQueuedYaw?queuedYaw:null,hasPending?pending:null,
                hasFence?budget:null,hasFence?elapsed:null,hasFence?available:null,originalClocks());
    }
}
