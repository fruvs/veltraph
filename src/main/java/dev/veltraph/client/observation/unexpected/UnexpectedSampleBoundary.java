package dev.veltraph.client.observation.unexpected;
import java.util.Objects;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
/** Comparisons may span native frames only while the exact activation and native epochs agree. */
public final class UnexpectedSampleBoundary {
    private UnexpectedSampleBoundary() {}
    public static boolean comparable(Context previous,Context current) {
        return previous!=null&&Objects.equals(previous,current);
    }
    public static boolean changed(Value previous,Value current,Kind kind,double threshold) {
        if(threshold==0)return !previous.equals(current);
        if(kind==Kind.ROTATION)return Math.max(Math.abs(Math.IEEEremainder(previous.x()-current.x(),360)),Math.abs(previous.y()-current.y()))>threshold;
        return Math.sqrt(Math.pow(previous.x()-current.x(),2)+Math.pow(previous.y()-current.y(),2)+Math.pow(previous.z()-current.z(),2))>threshold;
    }
}
