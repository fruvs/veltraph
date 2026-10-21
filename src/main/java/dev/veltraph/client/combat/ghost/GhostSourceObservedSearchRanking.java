package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Source cohort sort order; all opaque actor/state predicates remain explicit observed requirements. */
public final class GhostSourceObservedSearchRanking {
    public enum Observed { YES, NO, UNKNOWN }
    public enum Randomization { ON, OFF, UNKNOWN }
    public record Candidate(UUID id,double nativeY,double squaredDistance,int lookSampleIndex,boolean nativePoweredCreeper,
                            Observed nativeBodyEligible,Observed destinationContains,Observed ignoredIdentity){
        public Candidate {Objects.requireNonNull(id);if(!Double.isFinite(nativeY))throw new IllegalArgumentException("Observed native Y required");
            GhostSourceLookRayIndex.score(squaredDistance,lookSampleIndex);}
        double score(){return GhostSourceLookRayIndex.score(squaredDistance,lookSampleIndex);}
    }
    public record Decision(boolean observed,List<Candidate> candidates){public Decision {candidates=List.copyOf(candidates);}}
    private GhostSourceObservedSearchRanking() {}
    public static Decision choose(List<Candidate> nativeAabbCohort,Randomization randomization,DoubleSupplier originalMultiplier){
        Objects.requireNonNull(nativeAabbCohort);Objects.requireNonNull(originalMultiplier);
        if(randomization==null||randomization==Randomization.UNKNOWN)return new Decision(false,List.of());
        var eligible=new java.util.ArrayList<Candidate>();
        for(var c:nativeAabbCohort){
            if(!c.nativePoweredCreeper()||Math.abs(c.nativeY()-76)>=2.5)continue;
            if(c.nativeBodyEligible()==null||c.nativeBodyEligible()==Observed.UNKNOWN)return new Decision(false,List.of());
            if(c.nativeBodyEligible()==Observed.NO)continue;
            if(c.destinationContains()==null||c.destinationContains()==Observed.UNKNOWN)return new Decision(false,List.of());
            if(c.destinationContains()==Observed.NO)continue;
            if(c.ignoredIdentity()==null||c.ignoredIdentity()==Observed.UNKNOWN)return new Decision(false,List.of());
            if(c.ignoredIdentity()==Observed.NO)eligible.add(c);
        }
        Comparator<Candidate> score=(a,b)->{
            double left=a.score(),right=b.score();
            if(randomization==Randomization.ON){left*=multiplier(originalMultiplier);right*=multiplier(originalMultiplier);}
            return Double.compare(left,right);
        };
        return new Decision(true,eligible.stream().sorted(Comparator.comparingDouble(Candidate::squaredDistance)).limit(15)
                .sorted(score).limit(5).toList());
    }
    private static double multiplier(DoubleSupplier source){double v=source.getAsDouble();if(!Double.isFinite(v)||v<1||v>=30)
        throw new IllegalArgumentException("Observed source random multiplier [1,30) required");return v;}
}
