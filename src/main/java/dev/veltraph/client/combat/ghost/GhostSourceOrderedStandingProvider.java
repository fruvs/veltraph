package dev.veltraph.client.combat.ghost;

import java.util.Objects;

/** Exact ordered minimum selection; unresolved native predicates are required, never guessed. */
public final class GhostSourceOrderedStandingProvider {
    public record Cell(int x,int y,int z) {}
    public record Point(double x,double y,double z){
        public Point { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Finite native standing point required"); }
        double distance(Point p){return Math.sqrt(Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2));}
    }
    public enum ParentAreaObservation { ABSENT, PRESENT, UNKNOWN }
    public interface NativeInputs {
        /** Must revalidate the observed parent-area identity/state as well as native frame custody. */
        boolean current();
        /** Observation of the active source macro area, never a default null assumption. */
        ParentAreaObservation parentAreaObservation();
        Cell targetBlock();
        Point targetStandingPoint();
        Point actualPlayerPosition();
        boolean targetOnGround();
        boolean sourceCellStandable(Cell cell);
        /** Uses source standing ray mode: actors=true, density10, reject non-passable=true. */
        boolean sourceProtectedTargetRay(Cell cell);
        double parentFieldValue(Cell cell);
        boolean lavaAtOrBelow(Cell cell);
        double customPenalty(Cell cell);
    }
    private GhostSourceOrderedStandingProvider() {}
    public static Cell choose(NativeInputs input){
        Objects.requireNonNull(input);if(!input.current())return null;
        ParentAreaObservation areaObservation=input.parentAreaObservation();
        if(areaObservation==null||areaObservation==ParentAreaObservation.UNKNOWN)return null;
        Cell origin;Point target,player;
        try{origin=input.targetBlock();target=input.targetStandingPoint();player=input.actualPlayerPosition();}
        catch(IllegalArgumentException unknownGeometry){return null;}
        if(origin==null||target==null||player==null)return null;
        Cell best=null;double minimum=Double.POSITIVE_INFINITY;
        for(int x=-3;x<4;x++)for(int y=-3;y<3;y++)for(int z=-3;z<4;z++){
            if(!input.current())return null;
            Cell cell=new Cell(origin.x+x,origin.y+y,origin.z+z);
            if(!input.sourceCellStandable(cell))continue;
            Point standing=new Point(cell.x+.5,cell.y,cell.z+.5);double distance=standing.distance(target);
            if(distance>2.5||!input.sourceProtectedTargetRay(cell))continue;
            double area=areaObservation==ParentAreaObservation.PRESENT?input.parentFieldValue(cell):0,custom=input.customPenalty(cell);
            if(!Double.isFinite(area)||!Double.isFinite(custom))return null;
            double score=Math.pow(distance-2,2)+standing.distance(player)+area*area+(input.lavaAtOrBelow(cell)?400:0)+custom;
            if(score<minimum){minimum=score;best=cell;} // Source first-minimum tie behavior.
        }
        if(!input.current())return null;
        return best!=null?best:input.targetOnGround()?origin:null;
    }
}
