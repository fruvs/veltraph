package dev.veltraph.client.combat.ghost;
import java.util.concurrent.*;
import java.util.function.Supplier;
/** Dedicated bounded solver threads; cancellation first resolves capture reads, then removes queued work. */
public final class GhostSourceWalkSolverExecutor {
    private static final java.util.concurrent.atomic.AtomicInteger IDS=new java.util.concurrent.atomic.AtomicInteger();
    private static final ThreadPoolExecutor EXECUTOR=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"Veltraph-Ghost-Solver-"+IDS.incrementAndGet());t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public static final class Task<T> {
        private volatile boolean executing;
        private final CompletableFuture<T> result=new CompletableFuture<>();
        private final FutureTask<T> work;
        private final Continuation continuation;
        private final Runnable resolveCapture;
        private Task(Supplier<T> supplier,Runnable resolveCapture){this.resolveCapture=resolveCapture;continuation=null;work=new FutureTask<>(()->{executing=true;try{return supplier.get();}finally{executing=false;}}){protected void done(){try{result.complete(get());}catch(CancellationException e){result.completeExceptionally(e);}catch(InterruptedException e){Thread.currentThread().interrupt();result.completeExceptionally(e);}catch(ExecutionException e){result.completeExceptionally(e.getCause());}}};}
        private Task(Supplier<T> supplier,Runnable resolveCapture,boolean resumable){this.resolveCapture=resolveCapture;work=null;continuation=new Continuation(supplier);}
        public CompletableFuture<T> future(){return result;}
        /** Passive entered-worker state only; never a scope/native-read permission. */
        public boolean executing(){return executing&&(work==null||!work.isDone())&&!result.isDone();}
        public void cancel(){
            if(continuation==null){resolveCapture.run();work.cancel(false);EXECUTOR.remove(work);}
            else continuation.cancel();
        }
        /** One queued or executing supplier invocation at a time; waiting owns no solver thread. */
        private final class Continuation implements Runnable {
            private final Object gate=new Object();
            private final Supplier<T> supplier;
            private boolean terminal,queued;
            private long waitGeneration;
            private Continuation(Supplier<T> supplier){this.supplier=supplier;}
            private void enqueue(){
                RejectedExecutionException rejection=null;
                synchronized(gate){
                    if(terminal||queued||result.isDone())return;
                    queued=true;
                    try{EXECUTOR.execute(this);}catch(RejectedExecutionException full){queued=false;terminal=true;rejection=full;}
                }
                if(rejection!=null){
                    // Revocation may complete availability synchronously; terminal suppresses its callback.
                    try{resolveCapture.run();}finally{result.completeExceptionally(rejection);}
                }
            }
            public void run(){
                synchronized(gate){
                    queued=false;
                    if(terminal||result.isDone())return;
                    executing=true;
                }
                T value=null;Throwable failure=null;GhostSourceWalkPendingCell pending=null;
                try{value=supplier.get();}catch(GhostSourceWalkPendingCell cell){pending=cell;}catch(Throwable error){failure=error;}
                long generation;
                synchronized(gate){
                    executing=false;
                    if(terminal||result.isDone())return;
                    if(pending==null){
                        terminal=true;
                        if(failure==null)result.complete(value);else result.completeExceptionally(failure);
                        return;
                    }
                    generation=++waitGeneration;
                }
                // Completion is only a wakeup, including exceptional completion. The resumed
                // supplier's capture read remains responsible for the original value/failure.
                pending.availability().whenComplete((ignored,error)->resume(generation));
            }
            private void resume(long generation){
                synchronized(gate){
                    if(terminal||result.isDone()||generation!=waitGeneration)return;
                    ++waitGeneration;
                }
                enqueue();
            }
            private void cancel(){
                synchronized(gate){terminal=true;}
                // Mark terminal first to suppress rescheduling during capture resolution, but
                // resolve capture before publishing cancellation or removing queued work.
                try{resolveCapture.run();}finally{
                    result.completeExceptionally(new CancellationException());
                    EXECUTOR.remove(this);
                }
            }
        }
    }
    private GhostSourceWalkSolverExecutor() {}
    public static <T> Task<T> submit(Supplier<T> actualPureWorker,Runnable resolvePendingCaptureOnCancellation){
        var task=new Task<>(java.util.Objects.requireNonNull(actualPureWorker),java.util.Objects.requireNonNull(resolvePendingCaptureOnCancellation));
        try{EXECUTOR.execute(task.work);}catch(RejectedExecutionException full){task.resolveCapture.run();task.result.completeExceptionally(full);task.work.cancel(false);}return task;
    }
    /** Retries a resumable pure worker after cell availability without parking a solver thread. */
    public static <T> Task<T> submitResumable(Supplier<T> actualPureWorker,Runnable resolvePendingCaptureOnCancellation){
        var task=new Task<>(java.util.Objects.requireNonNull(actualPureWorker),java.util.Objects.requireNonNull(resolvePendingCaptureOnCancellation),true);
        task.continuation.enqueue();return task;
    }
}
