package dev.veltraph.client.observation.unexpected;
import java.util.Objects;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.Context;
/** One pending vanilla mouse origin. This is evidence, never input or action authority. */
final class PendingNativeMouseOrigin {
    private Context context;
    private long created;
    void record(Context exactContext,long now){context=Objects.requireNonNull(exactContext);created=now;}
    boolean consume(Context exactContext,long now){
        var observed=context;long at=created;clear();
        return observed!=null&&observed.equals(exactContext)&&now>=at&&now-at>=0&&now-at<=150;
    }
    void clear(){context=null;created=0;}
}
