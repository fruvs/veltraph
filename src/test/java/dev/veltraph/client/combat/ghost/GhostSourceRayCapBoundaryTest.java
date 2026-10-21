package dev.veltraph.client.combat.ghost;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;
final class GhostSourceRayCapBoundaryTest {
    private Snapshot clear(){return new Snapshot(){public boolean passableRegistryObserved(){return true;}public List<Actor> actors(){return List.of();}public Block block(Cell c){return new Block(true,true,null,false);}};}
    @Test void sourceLimitThousandIsAllowedAndThousandOneThrows(){
        assertTrue(GhostSourceSampledRay.hits(new Point(0,0,0),new Point(997,0,0),new Box(997,-1,-1,998,1,1),new Point(1,0,0),Mode.visibility(),clear()));
        assertThrows(IllegalArgumentException.class,()->GhostSourceSampledRay.hits(new Point(0,0,0),new Point(997.01,0,0),new Box(997,-1,-1,998,1,1),new Point(1,0,0),Mode.visibility(),clear()));
    }
    @Test void repeatedInitialCellFarFromTargetSkipsItsCollider(){
        Snapshot s=new Snapshot(){public boolean passableRegistryObserved(){return true;}public List<Actor> actors(){return List.of();}public Block block(Cell c){if(c.x()==0)throw new AssertionError("Source same-cell skip must precede block read");return new Block(true,true,null,false);}};
        assertTrue(GhostSourceSampledRay.hits(new Point(.1,.1,.1),new Point(4,.1,.1),new Box(3.5,0,0,4.5,1,1),new Point(1,0,0),Mode.visibility(),s));
    }
}
