package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;

/** Original graph control flow over immutable complete native edge observations, never a vanilla A* fallback. */
public final class GhostSourceWalkWorker {
    public enum Observed { YES, NO, UNKNOWN }
    public enum Status { FOUND, START_NOT_AIR, END_NOT_AIR, START_NOT_LOADED, END_NOT_LOADED, NO_UNVISITED, VISIT_LIMIT, UNKNOWN_INPUT }
    public record EdgeKey(Cell from,Cell to,boolean dropRetry){public EdgeKey {Objects.requireNonNull(from);Objects.requireNonNull(to);}}
    public record Edge(Observed admissible,double fullSourceCost){public Edge {Objects.requireNonNull(admissible);if(admissible==Observed.YES&&!Double.isFinite(fullSourceCost))throw new IllegalArgumentException("Full source native edge cost required");}}
    /** Both flags select volume offsets; only the FIRST flag suppresses the source exhausted-frontier drop retry. */
    public record Inputs(Cell start,Cell end,int visitLimit,int step,int riseMax,boolean firstVolumeFlag,boolean secondVolumeFlag,
                         Observed startAir,Observed endAir,Observed startLoaded,Observed endLoaded,Map<EdgeKey,Edge> edges){
        public Inputs {Objects.requireNonNull(start);Objects.requireNonNull(end);Objects.requireNonNull(startAir);Objects.requireNonNull(endAir);
            Objects.requireNonNull(startLoaded);Objects.requireNonNull(endLoaded);edges=Map.copyOf(edges);
            if(visitLimit<1||step<1)throw new IllegalArgumentException("Observed positive source graph limits required");}
    }
    public record Result(Status status,List<Cell> orderedNodes,List<Double> graphEdgeCosts,int visitedCount,boolean dropRetry,int firstAttemptVisited){
        public Result {orderedNodes=List.copyOf(orderedNodes);graphEdgeCosts=List.copyOf(graphEdgeCosts);}
    }
    public interface ObservedEdges {boolean current();Edge observe(EdgeKey edge);}
    private GhostSourceWalkWorker() {}
    public static final class Progress {
        private volatile String stage="CREATED";private volatile int visited,open,firstPassVisits;private volatile boolean dropRetry;private volatile long startedNanos,lastStageNanos;
        public record Evidence(String stage,int visited,int open,boolean dropRetry,int firstPassVisits,long startedNanos,long lastStageNanos) {}
        void stage(String value){long now=System.nanoTime();if(startedNanos==0)startedNanos=now;stage=value;lastStageNanos=now;}
        void graph(int visits,int openCount,boolean drop,int first){visited=visits;open=openCount;dropRetry=drop;firstPassVisits=first;}
        public Evidence cached(){return new Evidence(stage,visited,open,dropRetry,firstPassVisits,startedNanos,lastStageNanos);}
    }
    public static Result solve(Inputs in){return solve(in,new ObservedEdges(){public boolean current(){return true;}public Edge observe(EdgeKey edge){return in.edges.get(edge);}});}
    /** Concrete native capture adapter invokes this on the client thread, then seals its immutable observations for replay. */
    public static Result solve(Inputs in,ObservedEdges observedEdges){return solve(in,observedEdges,null);}
    public static Result solve(Inputs in,ObservedEdges observedEdges,Progress progress){if(progress!=null)progress.stage("GRAPH");Objects.requireNonNull(in);Objects.requireNonNull(observedEdges);
        if(!observedEdges.current())return finished(terminal(Status.UNKNOWN_INPUT,0,false,0),progress);
        var observations=List.of(in.startAir,in.endAir,in.startLoaded,in.endLoaded);
        var failures=List.of(Status.START_NOT_AIR,Status.END_NOT_AIR,Status.START_NOT_LOADED,Status.END_NOT_LOADED);
        for(int i=0;i<observations.size();i++){
            if(observations.get(i)==Observed.UNKNOWN)return finished(terminal(Status.UNKNOWN_INPUT,0,false,0),progress);
            if(observations.get(i)==Observed.NO)return finished(terminal(failures.get(i),0,false,0),progress);
        }
        boolean retry=false;int firstVisits=0;
        while(true){
            var frontier=new GhostSourceWalkFrontier(in.start,in.end,in.visitLimit);
            var offsets=GhostSourceWalkFrontier.offsets(in.step,in.riseMax,in.firstVolumeFlag||in.secondVolumeFlag,retry);
            while(true){
                if(!observedEdges.current())return finished(terminal(Status.UNKNOWN_INPUT,frontier.visitedCount(),retry,firstVisits),progress);
                // Original checks exhausted open BEFORE cap, then pops/adds visited BEFORE testing destination.
                if(!frontier.hasOpen()){
                    if(GhostSourceWalkFrontier.sourceDropRetry(in.firstVolumeFlag,retry,frontier.visitedCount())){
                        firstVisits=frontier.visitedCount();retry=true;break; // New frontier resets visited/open, preserves cap and options.
                    }
                    return finished(terminal(Status.NO_UNVISITED,frontier.visitedCount(),retry,firstVisits),progress);
                }
                if(!frontier.underVisitLimit())return finished(terminal(Status.VISIT_LIMIT,frontier.visitedCount(),retry,firstVisits),progress);
                var from=frontier.popVisit();if(progress!=null)progress.graph(frontier.visitedCount(),frontier.openCount(),retry,firstVisits);
                if(frontier.reached(from)){
                    var nodes=GhostSourceWalkFrontier.reconstruct(from);var costs=new java.util.ArrayList<Double>();
                    for(var n=from;n.parent()!=null;n=n.parent())costs.addFirst(n.edgeCost());
                    return finished(new Result(Status.FOUND,nodes,costs,frontier.visitedCount(),retry,firstVisits),progress);
                }
                for(var offset:offsets){
                    if(!GhostSourceWalkFrontier.structuralMove(offset))continue;
                    var to=new Cell(Math.addExact(from.cell().x(),offset.x()),Math.addExact(from.cell().y(),offset.y()),Math.addExact(from.cell().z(),offset.z()));
                    if(frontier.alreadyDiscovered(to))continue;
                    var observed=observedEdges.observe(new EdgeKey(from.cell(),to,retry));
                    if(!observedEdges.current())return finished(terminal(Status.UNKNOWN_INPUT,frontier.visitedCount(),retry,firstVisits),progress);
                    if(observed==null||observed.admissible==Observed.UNKNOWN)return finished(terminal(Status.UNKNOWN_INPUT,frontier.visitedCount(),retry,firstVisits),progress);
                    if(observed.admissible==Observed.YES)frontier.addObservedEdge(from,to,observed.fullSourceCost);
                }
            }
        }
    }
    private static Result finished(Result actual,Progress progress){if(progress!=null){progress.graph(actual.visitedCount(),0,actual.dropRetry(),actual.firstAttemptVisited());progress.stage("GRAPH_"+actual.status());}return actual;}
    private static Result terminal(Status status,int visits,boolean retry,int firstVisits){return new Result(status,List.of(),List.of(),visits,retry,firstVisits);}
}
