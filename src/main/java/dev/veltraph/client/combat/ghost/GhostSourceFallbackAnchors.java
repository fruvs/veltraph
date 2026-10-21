package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.IntBinaryOperator;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import net.minecraft.util.Mth;

/** Readable fallback singleton: original coordinates, encounter order, strict distances and RNG draw order. */
public final class GhostSourceFallbackAnchors {
    public static final int SOURCE_VISIT_LIMIT=400;
    public static final List<Cell> ANCHORS=List.of(new Cell(174,76,59),new Cell(153,76,38),new Cell(144,76,93),new Cell(113,76,68),new Cell(157,76,87),new Cell(170,76,47),new Cell(161,76,78),new Cell(135,76,88),new Cell(96,76,69),new Cell(82,76,91),new Cell(51,76,112),new Cell(13,76,142),new Cell(17,76,107),new Cell(42,76,61),new Cell(13,76,47),new Cell(-49,76,65),new Cell(-16,76,64),new Cell(-14,76,99),new Cell(-44,76,109),new Cell(-58,76,132),new Cell(-26,76,132));
    /** Source captures Entity.position() doubles for eligibility/sorting; only Graph start is floored. */
    public record Position(double x,double y,double z) {
        public Position {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Actual finite player position required");}
        public Cell blockCell(){return new Cell(Mth.floor(x),Mth.floor(y),Mth.floor(z));}
    }
    public record Candidate(Cell anchor,Cell endpoint,double distance) {}
    private GhostSourceFallbackAnchors() {}
    public static List<Candidate> capture(Cell player,Cell lastAnchor,IntBinaryOperator random){
        java.util.Objects.requireNonNull(player);return capture(new Position(player.x(),player.y(),player.z()),lastAnchor,random);
    }
    public static List<Candidate> capture(Position player,Cell lastAnchor,IntBinaryOperator random){
        java.util.Objects.requireNonNull(player);java.util.Objects.requireNonNull(lastAnchor);java.util.Objects.requireNonNull(random);
        var candidates=new ArrayList<Candidate>();
        for(var anchor:ANCHORS){double distance=distance(anchor,player);if(anchor.equals(lastAnchor)||!(distance<60&&distance>10))continue;
            // Original evaluates objectArray[2] (Z) first, then objectArray[0] (X).
            int z=random.applyAsInt(-5,5),x=random.applyAsInt(-5,5);
            if(x< -5||x>=5||z< -5||z>=5)throw new IllegalArgumentException("Source random range -5 inclusive,5 exclusive required");
            candidates.add(new Candidate(anchor,new Cell(anchor.x()+x,anchor.y(),anchor.z()+z),distance));
        }return List.copyOf(candidates);
    }
    public static <T> T select(List<T> successes,java.util.function.Function<T,Candidate> candidate,java.util.function.IntUnaryOperator randomIndex){
        var ordered=new ArrayList<T>(successes);ordered.sort(Comparator.comparingDouble(value->candidate.apply(value).distance()));
        if(ordered.isEmpty())return null;int selected=randomIndex.applyAsInt(ordered.size());
        if(selected<0||selected>=ordered.size())throw new IllegalArgumentException("Actual randomOrNull index required");return ordered.get(selected);
    }
    static double distance(Cell a,Cell b){return distance(a,new Position(b.x(),b.y(),b.z()));}
    static double distance(Cell a,Position b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
}
