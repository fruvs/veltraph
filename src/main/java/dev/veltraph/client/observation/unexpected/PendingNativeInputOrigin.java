package dev.veltraph.client.observation.unexpected;
import java.util.Objects;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.Context;
/** One eligible native input callback and its exact changed outcome; no action authority or identity inference. */
final class PendingNativeInputOrigin {
    private record Origin(Context context,long before,long expected,long created){}
    private Origin pending;
    void record(Context context,long before,long matched,boolean down,long now){pending=new Origin(Objects.requireNonNull(context),before,UnexpectedInputMask.expected(before,matched,down),now);}
    boolean consume(Context current,long applied,long now){
        var old=pending;clear();
        return old!=null&&old.context().equals(current)&&now>=old.created()&&now-old.created()>=0&&now-old.created()<=200
                &&UnexpectedInputMask.valid(applied)&&old.before()!=old.expected()&&applied==old.expected();
    }
    void clear(){pending=null;}
}
