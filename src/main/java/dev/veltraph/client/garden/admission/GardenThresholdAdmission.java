package dev.veltraph.client.garden.admission;

/** Readable stable 4.0.30 predicates at source owner lines 471 and 572, without native actions. */
public final class GardenThresholdAdmission {
    private GardenThresholdAdmission() {}
    public enum Result { ADMIT, COUNT_UNKNOWN, NOT_READY, BELOW_THRESHOLD, DISABLED, CAPABILITY_UNKNOWN, CAPABILITY_DENIED, CONTEST_DENIED, THRESHOLD_UNKNOWN, READINESS_UNKNOWN }
    public static Result visitor(Integer count,Boolean sourceReady,double sampledThreshold,boolean automatic,Boolean gardenCapability,boolean contest,boolean allowContest) {
        return visitor(count,sourceReady,()->sampledThreshold,automatic,gardenCapability,contest,allowContest);
    }
    public static Result visitor(Integer count,Boolean sourceReady,java.util.function.DoubleSupplier threshold,boolean automatic,Boolean gardenCapability,boolean contest,boolean allowContest) {
        return visitorLazy(count,sourceReady,threshold,automatic,()->gardenCapability,contest,allowContest);
    }
    public static Result visitorLazy(Integer count,Boolean sourceReady,java.util.function.DoubleSupplier threshold,boolean automatic,java.util.function.Supplier<Boolean> capability,boolean contest,boolean allowContest) {
        if(count==null||count==-1)return Result.COUNT_UNKNOWN;
        if(sourceReady==null)return Result.READINESS_UNKNOWN;
        if(!sourceReady)return Result.NOT_READY;
        double sampledThreshold;try{sampledThreshold=threshold.getAsDouble();}catch(IllegalArgumentException invalid){return Result.THRESHOLD_UNKNOWN;}
        if(!Double.isFinite(sampledThreshold))return Result.THRESHOLD_UNKNOWN;
        if(count<roundToInt(sampledThreshold))return Result.BELOW_THRESHOLD;
        if(!automatic)return Result.DISABLED;
        Boolean gardenCapability=capability.get();
        if(gardenCapability==null)return Result.CAPABILITY_UNKNOWN;
        if(!gardenCapability)return Result.CAPABILITY_DENIED;
        if(contest&&!allowContest)return Result.CONTEST_DENIED;
        return Result.ADMIT;
    }
    public static Result pest(Integer count,double sampledThreshold,boolean thresholdCheck,boolean automatic,Boolean mayFly,boolean contest,boolean allowContest) {
        return pest(count,()->sampledThreshold,thresholdCheck,automatic,mayFly,contest,allowContest);
    }
    public static Result pest(Integer count,java.util.function.DoubleSupplier threshold,boolean thresholdCheck,boolean automatic,Boolean mayFly,boolean contest,boolean allowContest) {
        return pestLazy(count,threshold,thresholdCheck,automatic,()->mayFly,contest,allowContest);
    }
    public static Result pestLazy(Integer count,java.util.function.DoubleSupplier threshold,boolean thresholdCheck,boolean automatic,java.util.function.Supplier<Boolean> capability,boolean contest,boolean allowContest) {
        if(count==null||count==-1)return Result.COUNT_UNKNOWN;
        if(count<=0)return Result.NOT_READY;
        if(!automatic)return Result.DISABLED;
        // Source samples even when thresholdCheck=false, after automatic admission is enabled.
        double sampledThreshold;try{sampledThreshold=threshold.getAsDouble();}catch(IllegalArgumentException invalid){return Result.THRESHOLD_UNKNOWN;}
        if(thresholdCheck){
            if(!Double.isFinite(sampledThreshold))return Result.THRESHOLD_UNKNOWN;
            if(count<roundToInt(sampledThreshold))return Result.BELOW_THRESHOLD;
        }
        Boolean mayFly=capability.get();
        if(mayFly==null)return Result.CAPABILITY_UNKNOWN;
        if(!mayFly)return Result.CAPABILITY_DENIED;
        if(contest&&!allowContest)return Result.CONTEST_DENIED;
        return Result.ADMIT;
    }
    /** Kotlin roundToInt uses ties toward positive infinity and saturates at int boundaries. */
    static int roundToInt(double value){return (int)Math.floor(value+.5);}
}
