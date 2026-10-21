package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.Objects;

/** CURSOR_ENGINE711–872 queue advancement only; native geometry observations and Root movement remain separate. */
public final class GhostSourceWalkCursor {
    public enum Observed { YES, NO, UNKNOWN }
    public enum Status { DONT_MOVE, STOP_SUCCESS, CONTINUE, UNKNOWN }
    public enum EndMode { NONE, WHOLE_PATH, OTHER }
    public record EdgeObservation(GhostSourceSampledRay.Point destination,double threshold,Observed nextEdgeCornerHit){
        public EdgeObservation {Objects.requireNonNull(destination);if(!Double.isFinite(threshold)||threshold<0)throw new IllegalArgumentException("Actual finite source radius required");Objects.requireNonNull(nextEdgeCornerHit);}
    }
    public record Advance(Status status,int pollCount,GhostSourceSampledRay.Point lastPolledDestination,boolean refreshRemainingGeometry) {}
    private GhostSourceWalkCursor() {}
    /** Source endpoint radius branch. NaN means an applicable original native destination predicate remains unobserved. */
    public static double observedThreshold(int remainingEdges,Double actualSingleEdgeOverride,EndMode mode,Observed sourceSpecialDestination){
        Objects.requireNonNull(mode);if(remainingEdges<1)throw new IllegalArgumentException("Nonempty actual source cursor required");
        if(remainingEdges<=1&&actualSingleEdgeOverride!=null){if(!Double.isFinite(actualSingleEdgeOverride)||actualSingleEdgeOverride<0)throw new IllegalArgumentException("Observed source override required");return actualSingleEdgeOverride;}
        if(remainingEdges==1&&mode!=EndMode.NONE)return .25;
        if(mode==EndMode.WHOLE_PATH)return .35;
        if(sourceSpecialDestination==null||sourceSpecialDestination==Observed.UNKNOWN)return Double.NaN;
        return sourceSpecialDestination==Observed.YES?.75:.5;
    }
    public static Advance observeAdvance(List<EdgeObservation> edges,GhostSourceSampledRay.Point actualOrPredictedPlayer,Observed nativeControlAvailable){
        Objects.requireNonNull(edges);
        if(nativeControlAvailable==null||nativeControlAvailable==Observed.UNKNOWN)return new Advance(Status.UNKNOWN,0,null,false);
        if(nativeControlAvailable==Observed.NO)return new Advance(Status.DONT_MOVE,0,null,false);
        if(edges.isEmpty())return new Advance(Status.STOP_SUCCESS,0,null,false);
        Objects.requireNonNull(actualOrPredictedPlayer);int last=-1;
        for(int i=0;i<edges.size();i++){
            var e=Objects.requireNonNull(edges.get(i));var point=e.destination();
            double horizontal=Math.sqrt(Math.pow(actualOrPredictedPlayer.x()-point.x(),2)+Math.pow(actualOrPredictedPlayer.z()-point.z(),2));
            boolean reached=horizontal<e.threshold()&&Math.abs(actualOrPredictedPlayer.y()-point.y())<=1;
            // Source only consults the next edge's footprint geometry for the first five unsatisfied destinations.
            if(!reached&&i<5&&i+1<edges.size()){
                if(e.nextEdgeCornerHit()==Observed.UNKNOWN)return new Advance(Status.UNKNOWN,0,null,false);
                reached=e.nextEdgeCornerHit()==Observed.YES;
            }
            if(reached)last=i;
        }
        int count=last+1;
        if(count==edges.size())return new Advance(Status.STOP_SUCCESS,count,edges.get(last).destination(),false);
        // Bytecode local24 is decremented to -1 by poll loop before the refresh test (offset671).
        return new Advance(Status.CONTINUE,count,last<0?null:edges.get(last).destination(),true);
    }
}
