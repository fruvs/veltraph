package dev.veltraph.client.combat.ghost;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;

final class GhostSourceSampledRayTest {
    private final Point eye=new Point(.5,.5,.5),target=new Point(3.5,.5,.5),direction=new Point(1,0,0);
    private final Box targetBox=new Box(3,0,0,4,1,1);
    private Snapshot snapshot(boolean observed,Block wall,List<Actor> actors){
        return new Snapshot(){
            public boolean passableRegistryObserved(){return observed;}
            public List<Actor> actors(){return actors;}
            public Block block(Cell cell){return cell.x()==1?wall:new Block(true,true,null,false);}
        };
    }
    private boolean hit(Mode mode,Snapshot snapshot){return GhostSourceSampledRay.hits(eye,target,targetBox,direction,mode,snapshot);}
    @Test void unknownRegistryNeverBecomesAnEmptyPassableFallback(){
        assertFalse(hit(Mode.visibility(),snapshot(false,new Block(true,true,null,false),List.of())));
    }
    @Test void unionCollisionBoundsOccludeWhileObservedOpenCellDoesNot(){
        assertFalse(hit(Mode.visibility(),snapshot(true,new Block(true,false,new Box(1,0,0,2,1,1),false),List.of())));
        assertTrue(hit(Mode.visibility(),snapshot(true,new Block(true,true,new Box(1,0,0,2,1,1),false),List.of())));
    }
    @Test void visibilityIgnoresActorsButStandingModeRejectsVisibleLivingActor(){
        var actor=new Actor(new Box(1,0,0,2,1,1),new Point(1.5,.5,.5),true,false,false,false);
        var s=snapshot(true,new Block(true,true,null,false),List.of(actor));
        assertTrue(hit(Mode.visibility(),s));assertFalse(hit(Mode.standing(),s));
    }
    @Test void sourceFluidFallbackOccludesWithoutCollisionShape(){
        assertFalse(hit(Mode.visibility(),snapshot(true,new Block(true,false,null,true),List.of())));
        assertTrue(hit(Mode.visibility(),snapshot(true,new Block(true,false,null,false),List.of())));
    }
    @Test void targetHitPrecedesSameSampledCellCollider(){
        var closeTarget=new Box(1,0,0,2,1,1);
        assertTrue(GhostSourceSampledRay.hits(eye,new Point(1.5,.5,.5),closeTarget,direction,
                Mode.standing(),snapshot(true,new Block(true,false,new Box(1,0,0,2,1,1),false),List.of())));
    }
    @Test void strictStandingModeRejectsNonPassableCellEvenWithNoCollision(){
        assertFalse(hit(Mode.standing(),snapshot(true,new Block(true,false,null,false),List.of())));
    }
    @Test void sourceFenceSnowAndRegistryExceptionsStayDistinct(){
        assertTrue(GhostSourceBlockPassability.passable(true,GhostSourceBlockPassability.Kind.FENCE_GATE,true,0,false));
        assertFalse(GhostSourceBlockPassability.passable(true,GhostSourceBlockPassability.Kind.FENCE_GATE,false,0,true));
        assertTrue(GhostSourceBlockPassability.passable(true,GhostSourceBlockPassability.Kind.SNOW_LAYER,false,1,false));
        assertFalse(GhostSourceBlockPassability.passable(true,GhostSourceBlockPassability.Kind.SNOW_LAYER,false,2,true));
        assertFalse(GhostSourceBlockPassability.passable(false,GhostSourceBlockPassability.Kind.OTHER,false,0,false));
    }
}
