package dev.veltraph.client.combat.ghost;
import java.util.Optional;
/** Source counter1 main-thread callback queue. Scheduling alone grants no native action authority. */
public final class GhostSourceCallbackQueue<T> {
    private Object job;private T value;private long dueTick;
    public void enqueue(Object actualJob,T actualCallback,long actualTick){
        if(job!=null)throw new IllegalStateException("Actual source callback already pending");
        job=java.util.Objects.requireNonNull(actualJob);value=java.util.Objects.requireNonNull(actualCallback);
        dueTick=Math.addExact(actualTick,1);
    }
    public Optional<T> take(Object actualCurrentJob,long actualTick){
        if(job==null)return Optional.empty();if(job!=actualCurrentJob){cancel();return Optional.empty();}
        if(actualTick<dueTick)return Optional.empty();T next=value;cancel();return Optional.of(next);
    }
    public boolean pending(){return job!=null;}
    public void cancel(){job=null;value=null;dueTick=0;}
}
