package dev.veltraph.client.navigation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A late native query never becomes new route permission; the individual call cannot be preempted. */
public final class DynamicRouteQueryFence {
    private final LongSupplier clock;
    private final BooleanSupplier current;
    private final long began,budget;
    private boolean dynamicPlanningWatching;
    private int planningClockVisited,planningClockTrue,planningQueryVisited,planningQueryTrue,planningQueryOrdinal;
    void beginDynamicPlanningDiagnostic(){dynamicPlanningWatching=true;planningClockVisited=planningClockTrue=planningQueryVisited=planningQueryTrue=planningQueryOrdinal=0;}
    void endDynamicPlanningDiagnostic(){dynamicPlanningWatching=false;}
    private boolean planningClockOperand(int slot,boolean original){if(dynamicPlanningWatching){planningClockVisited|=1<<slot;if(original)planningClockTrue|=1<<slot;}return original;}
    private boolean planningQueryOperand(int slot,boolean original){if(dynamicPlanningWatching){planningQueryVisited|=1<<slot;if(original)planningQueryTrue|=1<<slot;}return original;}
    void copyOriginalDynamicPlanningDiagnostic(){if(dynamicPlanningWatching)GroundDynamicPlanningDiagnostics.fenceObservation(this,budget,originalAvailableElapsed,
            originalAvailableResult,originalAvailableEvaluated,planningClockVisited,planningClockTrue,planningQueryOrdinal,planningQueryVisited,planningQueryTrue,false);}
    private void originalDynamicPlanningQueryRefused(){if(dynamicPlanningWatching)GroundDynamicPlanningDiagnostics.fenceObservation(this,budget,originalAvailableElapsed,
            originalAvailableResult,originalAvailableEvaluated,planningClockVisited,planningClockTrue,planningQueryOrdinal,planningQueryVisited,planningQueryTrue,true);}
    private boolean initialDirectionalWatching;
    private int initialQueryVisited,initialQueryTrue,initialQueryOrdinal,initialClockVisited,initialClockTrue;
    public void beginInitialDirectionalDiagnostic(){initialDirectionalWatching=true;initialQueryVisited=initialQueryTrue=initialQueryOrdinal=initialClockVisited=initialClockTrue=0;}
    public void endInitialDirectionalDiagnostic(){initialDirectionalWatching=false;}
    private boolean initialClockOperand(int slot,boolean original){if(initialDirectionalWatching){initialClockVisited|=1<<slot;if(original)initialClockTrue|=1<<slot;}return original;}
    private boolean initialQueryOperand(int slot,boolean original){if(initialDirectionalWatching){initialQueryVisited|=1<<slot;if(original)initialQueryTrue|=1<<slot;}return original;}
    public void copyOriginalInitialDirectionalDiagnostic(){if(initialDirectionalWatching)GroundInitialDirectionalDiagnostics.fenceObservation(this,budget,
            originalAvailableElapsed,originalAvailableResult,originalAvailableEvaluated,initialClockVisited,initialClockTrue,
            initialQueryOrdinal,initialQueryVisited,initialQueryTrue,false);}
    private void originalInitialQueryRefused(){if(initialDirectionalWatching)GroundInitialDirectionalDiagnostics.fenceObservation(this,budget,
            originalAvailableElapsed,originalAvailableResult,originalAvailableEvaluated,initialClockVisited,initialClockTrue,
            initialQueryOrdinal,initialQueryVisited,initialQueryTrue,true);}
    private long originalAvailableElapsed;private boolean originalAvailableResult,originalAvailableEvaluated;
    private long firstQueryPre,lastQueryPre,lastQueryPost,initialJournalLastSample,proofCreationLastSample,firstFinalPublication;
    private boolean queryPreSeen,queryPostSeen,initialJournalSeen,proofCreationSeen,finalPublicationSeen;
    private boolean originalQueryPre(boolean original){if(!queryPreSeen)firstQueryPre=originalAvailableElapsed;queryPreSeen=true;lastQueryPre=originalAvailableElapsed;return original;}
    private boolean originalQueryPost(boolean original){queryPostSeen=true;lastQueryPost=originalAvailableElapsed;return original;}
    public boolean copyOriginalInitialJournalReturn(boolean original){initialJournalSeen=originalAvailableEvaluated;initialJournalLastSample=originalAvailableElapsed;return original;}
    public void copyOriginalProofCreation(){proofCreationSeen=originalAvailableEvaluated;proofCreationLastSample=originalAvailableElapsed;}
    public void beginOriginalFinalPublicationObservation(){finalPublicationSeen=false;}
    public boolean copyOriginalFirstFinalPublicationAvailable(boolean original){if(!finalPublicationSeen){finalPublicationSeen=true;firstFinalPublication=originalAvailableElapsed;}return original;}
    /** Original began and last existing clock samples only; no query, scope or clock is invoked. */
    public void copyOriginalClockPhasesToPublicationFailure(){GroundPublicationFailureCache.clockPhases(began,
            firstQueryPre,lastQueryPre,lastQueryPost,initialJournalLastSample,proofCreationLastSample,firstFinalPublication,
            queryPreSeen,queryPostSeen,initialJournalSeen,proofCreationSeen,finalPublicationSeen);}
    /** Copies scalar results of the last original clock read; never reads the clock or scope. */
    public void copyOriginalAvailableToPublicationFailure(){GroundPublicationFailureCache.fence(budget,originalAvailableElapsed,originalAvailableResult,originalAvailableEvaluated);copyOriginalClockPhasesToPublicationFailure();}
    public DynamicRouteQueryFence(LongSupplier clock,BooleanSupplier current,long budgetNanos){
        this.clock=Objects.requireNonNull(clock);this.current=Objects.requireNonNull(current);
        if(budgetNanos<1)throw new IllegalArgumentException("Invalid finite query budget");budget=budgetNanos;began=clock.getAsLong();
    }
    public boolean available(){long elapsed=clock.getAsLong()-began;if(initialDirectionalWatching)initialClockVisited=initialClockTrue=0;if(dynamicPlanningWatching)planningClockVisited=planningClockTrue=0;boolean result=planningClockOperand(0,initialClockOperand(0,elapsed>=0))&&planningClockOperand(1,initialClockOperand(1,elapsed<=budget));originalAvailableElapsed=elapsed;originalAvailableResult=result;originalAvailableEvaluated=true;copyOriginalInitialDirectionalDiagnostic();copyOriginalDynamicPlanningDiagnostic();return result;}
    public boolean query(BooleanSupplier query){
        if(initialDirectionalWatching){initialQueryOrdinal++;initialQueryVisited=initialQueryTrue=0;}
        if(dynamicPlanningWatching){planningQueryOrdinal++;planningQueryVisited=planningQueryTrue=0;}
        if(!planningQueryOperand(0,initialQueryOperand(0,originalQueryPre(available())))||!planningQueryOperand(1,initialQueryOperand(1,current.getAsBoolean()))){originalInitialQueryRefused();originalDynamicPlanningQueryRefused();return false;}
        boolean result=planningQueryOperand(2,initialQueryOperand(2,query.getAsBoolean()));
        boolean originalResult=result&&planningQueryOperand(3,initialQueryOperand(3,originalQueryPost(available())))&&planningQueryOperand(4,initialQueryOperand(4,current.getAsBoolean()));
        if(!originalResult){originalInitialQueryRefused();originalDynamicPlanningQueryRefused();}return originalResult;
    }
}
