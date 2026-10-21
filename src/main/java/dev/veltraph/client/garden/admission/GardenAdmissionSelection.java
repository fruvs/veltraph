package dev.veltraph.client.garden.admission;
import java.util.function.Supplier;
import static dev.veltraph.client.garden.admission.GardenThresholdAdmission.Result;
/** Lazy source-priority selection. Unsupported chosen scheduling never substitutes a lower family. */
public final class GardenAdmissionSelection {
    private GardenAdmissionSelection() {}
    public enum Family { NONE, VISITOR, PEST }
    public enum State { ADMIT, DENIED, UNKNOWN_PRIORITY, UNSUPPORTED_INSTANT }
    public record Decision(Family family,State state,Result predicate){}
    public static Decision choose(boolean visitorAutomatic,Supplier<Result> visitor,boolean visitorInstant,
                                  Supplier<Result> pest,boolean pestInstant){
        if(visitorAutomatic){
            Result v=visitor.get();
            if(v==Result.ADMIT)return new Decision(Family.VISITOR,visitorInstant?State.UNSUPPORTED_INSTANT:State.ADMIT,v);
            if(unknown(v))return new Decision(Family.NONE,State.UNKNOWN_PRIORITY,v);
        }
        Result p=pest.get();
        if(p==Result.ADMIT)return new Decision(Family.PEST,pestInstant?State.UNSUPPORTED_INSTANT:State.ADMIT,p);
        return new Decision(Family.NONE,unknown(p)?State.UNKNOWN_PRIORITY:State.DENIED,p);
    }
    public static boolean sameAdmittedFamily(Family selected,Decision fresh){return selected!=null&&selected!=Family.NONE&&fresh!=null&&fresh.state()==State.ADMIT&&fresh.family()==selected;}
    private static boolean unknown(Result value){return value==null||value==Result.COUNT_UNKNOWN||value==Result.CAPABILITY_UNKNOWN||value==Result.THRESHOLD_UNKNOWN||value==Result.READINESS_UNKNOWN;}
}
