package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** CombatPathing result scoring. Random draws happen once per source map entry, before sorting. */
public final class GhostSourcePathResultScoring {
    public enum Observed { YES, NO, UNKNOWN }
    public record Edge(int nativeFromY,double observedSourceCost) {
        public Edge {finite(observedSourceCost);}
    }
    public record Candidate(Object nativeTarget,UUID nativeUuid,Object sourcePathIdentity,boolean walkingResult,
                            List<Edge> edges,double targetDistance,float nativePreferredDistance,double relativeYaw,
                            long remotePlayersInsideInflatedFive,long remotePlayersInsideInflatedTen,int lookSampleIndex,
                            Observed preferredActorAwareRay) {
        public Candidate {
            Objects.requireNonNull(nativeTarget);Objects.requireNonNull(nativeUuid);edges=List.copyOf(edges);
            finite(targetDistance);finite(nativePreferredDistance);finite(relativeYaw);
            if(targetDistance<0||nativePreferredDistance<0||remotePlayersInsideInflatedFive<0||remotePlayersInsideInflatedTen<0
                    ||lookSampleIndex<0||lookSampleIndex>20)throw new IllegalArgumentException("Observed source metrics required");
            if(walkingResult&&(sourcePathIdentity==null||edges.isEmpty()))
                throw new IllegalArgumentException("Actual WALK path and source edges required");
        }
    }
    /** The helper's static long is an observation, not a default or a fabricated native producer. */
    public record Context(Observed remotePlayerWithinOnePointFive,GhostSourceCrowdPressureCounter.Observation pressureObservation,boolean ghostVerticalPenalty) {}
    @FunctionalInterface public interface SourceRandom {double nextDouble(double exclusiveUpperBound);}
    public record Scored(Candidate candidate,double score) {}
    public record Selection(boolean observed,List<Scored> sorted,Scored selected) {
        public Selection {sorted=List.copyOf(sorted);}
    }
    private GhostSourcePathResultScoring() {}
    public static Selection choose(List<Candidate> sourceMapIterationOrder,Context context,SourceRandom random) {
        Objects.requireNonNull(sourceMapIterationOrder);Objects.requireNonNull(context);Objects.requireNonNull(random);
        if(context.remotePlayerWithinOnePointFive()==null||context.remotePlayerWithinOnePointFive()==Observed.UNKNOWN
                ||context.pressureObservation()==null||!context.pressureObservation().observed())return new Selection(false,List.of(),null);
        var scored=new ArrayList<Scored>();
        for(var c:sourceMapIterationOrder)scored.add(new Scored(c,score(c,context,random)));
        if(!context.pressureObservation().observed())return new Selection(false,List.of(),null);
        scored.sort(Comparator.comparingDouble(Scored::score));
        for(var s:scored){var c=s.candidate();
            if(!(c.nativePreferredDistance()<7.0f)||!(Math.abs(c.relativeYaw())<40.0))continue;
            if(c.preferredActorAwareRay()==null||c.preferredActorAwareRay()==Observed.UNKNOWN)
                return new Selection(false,scored,null);
            if(c.preferredActorAwareRay()==Observed.YES)return new Selection(true,scored,s);
        }
        return new Selection(true,scored,scored.isEmpty()?null:scored.getFirst());
    }
    public static double score(Candidate c,Context context,SourceRandom random) {
        Objects.requireNonNull(c);Objects.requireNonNull(context);Objects.requireNonNull(random);
        if(context.remotePlayerWithinOnePointFive()==null||context.remotePlayerWithinOnePointFive()==Observed.UNKNOWN
                ||context.pressureObservation()==null||!context.pressureObservation().observed())throw new IllegalStateException("Unobserved source scoring context");
        double value=c.targetDistance()/2.0;
        if(c.walkingResult()){double cost=0;for(var edge:c.edges())cost+=edge.observedSourceCost();value+=cost/2.0;}
        value+=draw(random,2.0);
        if(context.remotePlayerWithinOnePointFive()==Observed.YES)value+=draw(random,6.0);
        if(context.pressureObservation().value()>0)value+=draw(random,(double)context.pressureObservation().value()*3.0);
        value+=Math.abs(c.relativeYaw())/35.0;
        value+=(double)(c.remotePlayersInsideInflatedFive()*3L);
        value+=(double)c.remotePlayersInsideInflatedTen();
        value+=(double)c.lookSampleIndex();
        if(context.ghostVerticalPenalty()&&c.walkingResult()){
            int previous=c.edges().getFirst().nativeFromY();long changes=0;
            for(var edge:c.edges()){int delta=Math.abs(edge.nativeFromY()-previous);previous=edge.nativeFromY();if(delta>1)changes++;}
            value+=(double)(changes*3L);
        }
        if(!context.pressureObservation().observed())throw new IllegalStateException("Pressure observation changed while scoring");
        return value;
    }
    private static double draw(SourceRandom random,double upper){double v=random.nextDouble(upper);
        if(!Double.isFinite(v)||v<0||v>=upper)throw new IllegalArgumentException("Source random value outside [0, upper)");return v;}
    private static void finite(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("Observed finite metric required");}
}
