package dev.veltraph.client.combat.ghost;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Bounded client-thread demand capture for a pure off-thread source worker. No guessed missing-cell defaults. */
public final class GhostSourceWalkDemandCapture<K,V> {
    public enum State { CAPTURING, SEALED, REVOKED, FAILED }
    private final ConcurrentHashMap<K,CompletableFuture<V>> values=new ConcurrentHashMap<>();
    private record Request<K,V>(K key,CompletableFuture<V> future) {}
    private final ConcurrentLinkedQueue<Request<K,V>> required=new ConcurrentLinkedQueue<>(), speculative=new ConcurrentLinkedQueue<>();
    private final java.util.Set<K> priorityQueued=ConcurrentHashMap.newKeySet();
    static final int MAX_SPECULATIVE_READS_PER_PUMP=128;
    static final int MAX_COORDINATE_REFILL_HANDOFFS=64;
    static final int MAX_COORDINATE_PUMP_TURNS=512;
    private final int speculativeReadLimit;
    private final java.util.concurrent.atomic.AtomicInteger queuedRequired=new java.util.concurrent.atomic.AtomicInteger(),queuedSpeculative=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong nativeReads=new java.util.concurrent.atomic.AtomicLong(),nativeFailures=new java.util.concurrent.atomic.AtomicLong();
    private final AtomicBoolean valid=new AtomicBoolean(true);private volatile State state=State.CAPTURING;
    private final ConcurrentHashMap<K,V> observed=new ConcurrentHashMap<>();
    private final java.util.Set<K> used=ConcurrentHashMap.newKeySet();
    private final Runnable afterFuturePublished;
    public GhostSourceWalkDemandCapture(){this(()->{});}
    GhostSourceWalkDemandCapture(Runnable afterFuturePublished){this(afterFuturePublished,MAX_SPECULATIVE_READS_PER_PUMP);}
    // Package-local deterministic scheduling regression seam. Runtime construction always uses the bounded default.
    GhostSourceWalkDemandCapture(Runnable afterFuturePublished,int speculativeReadLimit){
        this.afterFuturePublished=java.util.Objects.requireNonNull(afterFuturePublished);
        if(speculativeReadLimit<1||speculativeReadLimit>MAX_SPECULATIVE_READS_PER_PUMP)throw new IllegalArgumentException("Bounded positive speculative limit required");
        this.speculativeReadLimit=speculativeReadLimit;
    }
    boolean hasPendingRequiredReads(){return !required.isEmpty();}
    int pendingReads(){return required.size()+speculative.size();}
    public V require(K key){used.add(key);if(state==State.SEALED){var value=observed.get(key);if(value==null)throw new IllegalStateException("Cell outside sealed capture");return value;}if(!valid.get())throw new IllegalStateException("Actual source capture revoked");
        var future=publish(java.util.Objects.requireNonNull(key),true);
        try{var value=future.join();if(!valid.get())throw new IllegalStateException("Actual source capture revoked");return value;}
        catch(CompletionException error){if(valid.get()){state=State.FAILED;invalidate(error);}throw error;}}
    /** Nonblocking equivalent of require for resumable pure coordinate workers only.
     * Used-cell registration, exceptional outcome, final-valid check and failure invalidation
     * retain require's semantics. join executes only after this exact future reports done. */
    V requireAvailable(K key){
        used.add(key);
        if(state==State.SEALED){var value=observed.get(key);if(value==null)throw new IllegalStateException("Cell outside sealed capture");return value;}
        if(!valid.get())throw new IllegalStateException("Actual source capture revoked");
        var actualKey=java.util.Objects.requireNonNull(key);
        // A completed exact future needs no new publication allocation or priority promotion.
        // Pending/speculative futures still use the existing priority publication path.
        var future=values.get(actualKey);
        if(future==null||!future.isDone())future=publish(actualKey,true);
        if(!future.isDone())throw new GhostSourceWalkPendingCell(future);
        try{var value=future.join();if(!valid.get())throw new IllegalStateException("Actual source capture revoked");return value;}
        catch(CompletionException error){if(valid.get()){state=State.FAILED;invalidate(error);}throw error;}
    }
    /** Scheduling-only priority publication for a caller-proved finite dependency set.
     * No native reads or joins occur here; original require calls alone mark cells used.
     * Failed unused members remain unavailable futures, never guessed values or route failures. */
    void primeRequired(Iterable<K> keys){
        java.util.Objects.requireNonNull(keys);
        if(state!=State.CAPTURING||!valid.get())throw new IllegalStateException("Actual source capture not accepting demands");
        for(var key:keys)publish(java.util.Objects.requireNonNull(key),true);
    }
    public void prefetch(Iterable<K> keys){if(state!=State.CAPTURING)return;for(var key:keys)publish(java.util.Objects.requireNonNull(key),false);}
    private CompletableFuture<V> publish(K key,boolean demanded){
        // The map never removes a published future. Repeated prefetch/require publication
        // can reuse that exact future without allocating a losing placeholder.
        // Pending demand retains the same one-time priority promotion as a racing loser.
        var existing=values.get(key);
        if(existing!=null){if(demanded&&!existing.isDone()&&priorityQueued.add(key)){queuedRequired.incrementAndGet();required.add(new Request<>(key,existing));}return existing;}
        var created=new CompletableFuture<V>();var previous=values.putIfAbsent(key,created);
        if(previous!=null){if(demanded&&!previous.isDone()&&priorityQueued.add(key)){queuedRequired.incrementAndGet();required.add(new Request<>(key,previous));}return previous;}
        // Exact future is published before either queue; promotion cannot observe a missing future.
        afterFuturePublished.run();if(!valid.get()){created.completeExceptionally(new IllegalStateException("Capture revoked during publication"));return created;}
        if(demanded){if(priorityQueued.add(key)){queuedRequired.incrementAndGet();required.add(new Request<>(key,created));}}
        else {queuedSpeculative.incrementAndGet();speculative.add(new Request<>(key,created));}
        return created;
    }
    /** Fresh scope is checked before and after each bounded read. Available time bounds useful speculation; a finite read ceiling also prevents a zero-clock runaway. Budget controls scheduling, never source visit limits. */
    public int pump(long budgetNanos,java.util.function.LongSupplier nanoClock,BooleanSupplier actualCurrentScope,Function<K,V> actualNativeRead){
        return pumpInternal(budgetNanos,nanoClock,actualCurrentScope,actualNativeRead,null,null);
    }
    /** Coordinate-only scheduling adapter; all authority/native reads remain freshly fenced. */
    int pumpCoordinate(long budgetNanos,java.util.function.LongSupplier nanoClock,BooleanSupplier actualCurrentScope,Function<K,V> actualNativeRead,BooleanSupplier enteredWorker){
        return pumpCoordinate(budgetNanos,nanoClock,actualCurrentScope,actualNativeRead,enteredWorker,Thread::yield);
    }
    // Package-local deterministic handoff seam; production uses Thread.yield, never sleep/join.
    int pumpCoordinate(long budgetNanos,java.util.function.LongSupplier nanoClock,BooleanSupplier actualCurrentScope,Function<K,V> actualNativeRead,BooleanSupplier enteredWorker,Runnable schedulingHandoff){
        return pumpInternal(budgetNanos,nanoClock,actualCurrentScope,actualNativeRead,java.util.Objects.requireNonNull(enteredWorker),java.util.Objects.requireNonNull(schedulingHandoff));
    }
    private int pumpInternal(long budgetNanos,java.util.function.LongSupplier nanoClock,BooleanSupplier actualCurrentScope,Function<K,V> actualNativeRead,BooleanSupplier enteredWorker,Runnable schedulingHandoff){
        if(budgetNanos<1)throw new IllegalArgumentException("Positive capture time budget required");
        if(!valid.get()||state!=State.CAPTURING)return 0;
        if(!actualCurrentScope.getAsBoolean()){revoke();return 0;}
        long start=nanoClock.getAsLong();int count=0,speculativeReads=0,refillHandoffs=0,pumpTurns=0;boolean successfullyPublished=false;
        while(nanoClock.getAsLong()-start<budgetNanos){
            if(enteredWorker!=null&&pumpTurns++>=MAX_COORDINATE_PUMP_TURNS)break;
            var request=required.poll();if(request!=null)queuedRequired.decrementAndGet();
            if(request==null){
                if(speculativeReads<speculativeReadLimit){request=speculative.poll();if(request!=null)queuedSpeculative.decrementAndGet();}
                if(request==null){
                    if(enteredWorker==null||!successfullyPublished||refillHandoffs>=MAX_COORDINATE_REFILL_HANDOFFS||!valid.get()||state!=State.CAPTURING||!enteredWorker.getAsBoolean())break;
                    if(!actualCurrentScope.getAsBoolean()){revoke();break;}
                    if(!valid.get()||state!=State.CAPTURING||!enteredWorker.getAsBoolean()||nanoClock.getAsLong()-start>=budgetNanos)break;
                    refillHandoffs++;schedulingHandoff.run();
                    if(!actualCurrentScope.getAsBoolean()){revoke();break;}
                    if(!valid.get()||state!=State.CAPTURING||!enteredWorker.getAsBoolean())break;
                    continue;
                }
            }
            if(request.future().isDone())continue; // A promoted speculative request was already resolved by the priority queue.
            var key=request.key();if(!used.contains(key))speculativeReads++;
            try{if(!actualCurrentScope.getAsBoolean()){revoke();break;}var value=java.util.Objects.requireNonNull(measuredRead(actualNativeRead,key));
                if(!actualCurrentScope.getAsBoolean()){revoke();break;}observed.put(key,value);boolean published=request.future().complete(value);count++;successfullyPublished|=published;
            }catch(RuntimeException error){
                if(!actualCurrentScope.getAsBoolean()){revoke();break;}
                // Speculative unavailable cells are not route failures. Their exact future records unavailable input.
                request.future().completeExceptionally(error);
                if(used.contains(key)){state=State.FAILED;invalidate(error);break;}
            }
        }return count;
    }
    public boolean alreadyObserved(K key){return observed.containsKey(key);}
    public java.util.Map<K,V> usedSnapshot(){var result=new java.util.HashMap<K,V>();for(var key:used){var v=observed.get(key);if(v==null)throw new IllegalStateException("Required cell is not captured");result.put(key,v);}return java.util.Map.copyOf(result);}
    public boolean valid(){return valid.get();}
    public State state(){return state;}
    public java.util.Map<K,V> snapshot(){if(!valid.get())throw new IllegalStateException("Capture unavailable");return java.util.Map.copyOf(observed);}
    public void seal(BooleanSupplier actualFinalScope){if(!actualFinalScope.getAsBoolean()){revoke();return;}
        if(state!=State.CAPTURING||!valid.get())throw new IllegalStateException("Capture cannot seal");state=State.SEALED;}
    public void revoke(){state=State.REVOKED;invalidate(new IllegalStateException("Native job/context/epoch invalidated"));}
    private void invalidate(RuntimeException error){valid.set(false);values.values().forEach(v->v.completeExceptionally(error));required.clear();speculative.clear();queuedRequired.set(0);queuedSpeculative.set(0);}
    private V measuredRead(Function<K,V> read,K key){nativeReads.incrementAndGet();try{return read.apply(key);}catch(RuntimeException error){nativeFailures.incrementAndGet();throw error;}}
    public record Diagnostic(State state,boolean valid,long publishedKeys,int usedKeys,long observedKeys,int pendingRequired,int pendingSpeculative,long nativeReads,long failedReads) {}
    /** Cached/concurrent counters only; weakly consistent during worker publication, never an authority condition. */
    public Diagnostic cachedDiagnostic(){return new Diagnostic(state,valid.get(),values.mappingCount(),used.size(),observed.mappingCount(),Math.max(0,queuedRequired.get()),Math.max(0,queuedSpeculative.get()),nativeReads.get(),nativeFailures.get());}

}
