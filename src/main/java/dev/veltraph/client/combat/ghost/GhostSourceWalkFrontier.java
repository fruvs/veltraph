package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Exact source OPEN ordering and GRAPH frontier rules. Native admissibility and full edge costs must be observed. */
public final class GhostSourceWalkFrontier {
    public record Cell(int x,int y,int z) {}
    public record Offset(int x,int y,int z) {}
    public static final class Node {
        private final Cell cell;private final Node parent;private final double edgeCost,heuristic,totalCost;
        private Node(Cell cell,Node parent,double edgeCost,double heuristic){this.cell=cell;this.parent=parent;this.edgeCost=edgeCost;this.heuristic=heuristic;totalCost=(parent==null?0:parent.totalCost())+edgeCost;}
        public Cell cell(){return cell;}public Node parent(){return parent;}
        public double edgeCost(){return edgeCost;}
        public double totalCost(){return totalCost;}
        public double priority(){return totalCost()+heuristic;}
    }
    private final Cell destination;private final int visitLimit;
    private final java.util.Set<Node> popped=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final ArrayList<Node> open=new ArrayList<>();private final HashSet<Cell> openCells=new HashSet<>(),visited=new HashSet<>();
    public GhostSourceWalkFrontier(Cell start,Cell destination,int visitLimit){
        this.destination=Objects.requireNonNull(destination);Objects.requireNonNull(start);
        if(visitLimit<1)throw new IllegalArgumentException("Positive observed source node limit required");this.visitLimit=visitLimit;
        // Source starts with a zero-cost, zero-heuristic node. Neighbor heuristics are set only on insertion.
        insert(new Node(start,null,0,0));
    }
    public boolean hasOpen(){return !open.isEmpty();}
    public boolean underVisitLimit(){return visited.size()<visitLimit;}
    public int visitedCount(){return visited.size();}
    public int openCount(){return open.size();}
    public boolean alreadyDiscovered(Cell c){return visited.contains(c)||openCells.contains(c);}
    public Node popVisit(){if(!hasOpen()||!underVisitLimit())throw new IllegalStateException("Source frontier empty or visited cap reached");
        var n=open.removeFirst();openCells.remove(n.cell);visited.add(n.cell);popped.add(n);return n;}
    public boolean reached(Node n){return n.cell.equals(destination);}
    /** No relaxation/reopen: source GRAPH rejects coordinates already in either set before native predicates. */
    public boolean addObservedEdge(Node from,Cell to,double fullObservedCost){
        Objects.requireNonNull(from);Objects.requireNonNull(to);
        if(!popped.contains(from))throw new IllegalArgumentException("Only the actual popped source node may expand");
        if(alreadyDiscovered(to))return false;
        if(!Double.isFinite(fullObservedCost))throw new IllegalArgumentException("Full observed native cost required");
        double h=sourceHeuristic(destination,to);
        if(!Double.isFinite(h))throw new IllegalArgumentException("Source integer heuristic overflow; invalid native graph domain");
        insert(new Node(to,from,fullObservedCost,h));return true;
    }
    private void insert(Node n){int lo=0,hi=open.size();
        // Source recursive binary insert uses > to take the right branch; equality goes left.
        while(lo!=hi){int mid=(lo+hi)/2;if(n.priority()>open.get(mid).priority())lo=mid+1;else hi=mid;}
        open.add(lo,n);openCells.add(n.cell);
    }
    public static double sourceHeuristic(Cell destination,Cell cell){
        int x=destination.x-cell.x,y=destination.y-cell.y,z=destination.z-cell.z;
        return Math.sqrt(x*x+y*y+z*z); // Original int arithmetic, not a substituted double metric.
    }
    /** Source raw loop order: X outer, Z middle, Y inner. Native filters follow; zero/triple-axis moves are retained here. */
    public static List<Offset> offsets(int step,int riseMax,boolean volumeMode,boolean dropRetry){
        if(step<1)throw new IllegalArgumentException("Positive observed source step required");
        int low=volumeMode?-step:Math.subtractExact(-step,dropRetry?15:1),high=volumeMode?step:riseMax;
        var result=new ArrayList<Offset>();
        for(long x=-step;x<=step;x+=step)for(long z=-step;z<=step;z+=step)for(long y=low;y<=high;y+=step)result.add(new Offset((int)x,(int)y,(int)z));
        return List.copyOf(result);
    }
    public static boolean structuralMove(Offset o){return (o.x!=0||o.y!=0||o.z!=0)&&!(o.x*o.z!=0&&o.y!=0);}
    public static boolean sourceDropRetry(boolean firstVolumeFlag,boolean dropAlreadyTried,int visitedCount){return !firstVolumeFlag&&!dropAlreadyTried&&visitedCount<500;}
    /** Original parent chain reconstruction, including start for a single-node result. Native edge conversion remains separate. */
    public static List<Cell> reconstruct(Node end){Objects.requireNonNull(end);var result=new ArrayList<Cell>();
        for(Node n=end;n!=null;n=n.parent)result.addFirst(n.cell);return List.copyOf(result);}
}
