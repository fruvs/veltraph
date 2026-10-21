package dev.veltraph.client.navigation;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
/** One bounded tail-first selection; definite unsafe tail may be replaced without a new episode. */
public final class GroundApproachSafeReplan {
    private GroundApproachSafeReplan(){}
    public static boolean sameEpisode(Object captured,java.util.function.Supplier<Object> live,BooleanSupplier active,BooleanSupplier root){
        if(captured==null||!active.getAsBoolean()||live.get()!=captured)return false;
        boolean rootCurrent=root.getAsBoolean();
        return rootCurrent&&active.getAsBoolean()&&live.get()==captured;
    }
    public interface Proof {boolean accepts(GroundJumpApproach.Plan plan);boolean definiteUnsafe();}
    public static Optional<GroundJumpApproach.Plan> choose(GroundJumpApproach.Plan tail,Supplier<List<GroundJumpApproach.Plan>> fresh,
            Proof proof,BooleanSupplier current,BooleanSupplier abandonTail){
        if(!current.getAsBoolean())return Optional.empty();
        if(tail!=null){
            if(proof.accepts(tail))return current.getAsBoolean()?Optional.of(tail):Optional.empty();
            if(!current.getAsBoolean()||!proof.definiteUnsafe())return Optional.empty();
            if(!abandonTail.getAsBoolean()||!current.getAsBoolean())return Optional.empty();
        }
        var candidates=fresh.get();if(candidates==null||candidates.size()>GroundJumpApproach.Key.values().length)return Optional.empty();
        for(var candidate:candidates){
            if(tail!=null&&candidate.key()==tail.key())continue;
            if(!current.getAsBoolean())return Optional.empty();
            if(proof.accepts(candidate))return current.getAsBoolean()?Optional.of(candidate):Optional.empty();
            if(!current.getAsBoolean())return Optional.empty();
        }
        return Optional.empty();
    }
}
