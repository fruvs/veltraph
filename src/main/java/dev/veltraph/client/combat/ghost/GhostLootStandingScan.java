package dev.veltraph.client.combat.ghost;
import java.util.Objects;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;

/** Incremental pure min0/max1 ->2 ->3 native foot scan. Readable generic coordinate helper guides this qualified modern Ghost extension. */
public final class GhostLootStandingScan {
    public enum State { SCANNING, READY, NO_STANDING, REVOKED }
    public enum Availability { OBSERVED, UNKNOWN }
    private final Cell item;private final Point player;
    private int radius=1,x=-1,y=-3,z=-1;private boolean original=true;private Cell pending,selected,best;private double bestScore=Double.POSITIVE_INFINITY;
    private State state=State.SCANNING;
    public GhostLootStandingScan(Cell actualItemCell,Point actualPlayer){item=Objects.requireNonNull(actualItemCell);player=Objects.requireNonNull(actualPlayer);}
    public State state(){return state;}public Cell selected(){return state==State.READY?selected:null;}
    public boolean originalProbe(){return original;}
    /** Idempotent until the actual observation for this exact requested cell is consumed. */
    public Cell nextCell(){
        if(state!=State.SCANNING)return null;if(pending!=null)return pending;
        if(original)return pending=item;
        while(state==State.SCANNING){
            if(x>radius){if(best!=null){selected=best;state=State.READY;return null;}if(radius==3){state=State.NO_STANDING;return null;}
                radius++;x=-radius;y=-3;z=-radius;}
            int cx=x,cy=y,cz=z;advanceIndex();
            if(Math.sqrt(cx*cx+cy*cy+cz*cz)>radius)continue;
            return pending=new Cell(Math.addExact(item.x(),cx),Math.addExact(item.y(),cy),Math.addExact(item.z(),cz));
        }return null;
    }
    /** No authority is inferred from these pure inputs: native binding supplies actual sourceFoot and neighbor observations. */
    public void observe(Cell actualCell,Availability availability,boolean sourceFoot,boolean actualOriginalNeighborUsable){
        if(state!=State.SCANNING||pending==null||!pending.equals(actualCell))throw new IllegalStateException("Exact pending cell observation required");
        Cell cell=pending;pending=null;if(availability!=Availability.OBSERVED){state=State.REVOKED;return;}
        if(original){original=false;if(sourceFoot&&actualOriginalNeighborUsable){selected=cell;state=State.READY;}return;}
        if(sourceFoot){double score=score(cell);if(score<bestScore){bestScore=score;best=cell;}}
    }
    public void revoke(){state=State.REVOKED;pending=null;}
    private void advanceIndex(){if(++z>radius){z=-radius;if(++y>1){y=-3;x++;}}}
    private double score(Cell c){double playerDistance=Math.sqrt(Math.pow(player.x()-(c.x()+.5),2)+Math.pow(player.y()-(c.y()+.5),2)+Math.pow(player.z()-(c.z()+.5),2));
        double itemDistance=Math.sqrt(Math.pow((double)c.x()-item.x(),2)+Math.pow((double)c.y()-item.y(),2)+Math.pow((double)c.z()-item.z(),2));return playerDistance+itemDistance;}
}
