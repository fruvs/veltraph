package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class InitialLaneCoverageTest {
 @Test void midrowFirstNearThenFarThenSameNearPreservesOriginalExitSide(){var c=new InitialLaneCoverage();c.start(true,Direction.EAST,TurnDirection.LEFT);assertEquals(InitialLaneCoverage.Decision.REVERSE,c.end(TurnDirection.LEFT,new Vec3(.5,70,-20.5),true,true));assertEquals(InitialLaneCoverage.Decision.REVERSE,c.end(TurnDirection.RIGHT,new Vec3(.5,70,30.5),true,true));assertEquals(InitialLaneCoverage.Decision.EXIT,c.end(TurnDirection.LEFT,new Vec3(.5,70,-20.5),true,true));assertFalse(c.pending());}
 @Test void unknownLoadedEndOrBlockedReturnNeverClaimsCoverage(){var c=new InitialLaneCoverage();c.start(true,Direction.EAST,TurnDirection.LEFT);assertEquals(InitialLaneCoverage.Decision.DENY,c.end(TurnDirection.LEFT,new Vec3(.5,70,0),false,true));assertEquals(InitialLaneCoverage.Decision.DENY,c.end(TurnDirection.LEFT,new Vec3(.5,70,0),true,false));assertTrue(c.pending());}
 @Test void ChangedLaneOrWrongReturnEndCannotComplete(){var c=new InitialLaneCoverage();c.start(true,Direction.EAST,TurnDirection.LEFT);c.end(TurnDirection.LEFT,new Vec3(.5,70,-20.5),true,true);assertEquals(InitialLaneCoverage.Decision.DENY,c.end(TurnDirection.RIGHT,new Vec3(3.5,70,30.5),true,true));c.end(TurnDirection.RIGHT,new Vec3(.5,70,30.5),true,true);assertEquals(InitialLaneCoverage.Decision.DENY,c.end(TurnDirection.LEFT,new Vec3(.5,70,-10.5),true,true));}
 @Test void optOutAndCancelPreserveSourceRowTransitionContract(){var c=new InitialLaneCoverage();c.start(false,Direction.EAST,TurnDirection.LEFT);assertEquals(InitialLaneCoverage.Decision.EXIT,c.end(TurnDirection.LEFT,null,false,false));c.start(true,Direction.EAST,TurnDirection.LEFT);c.cancel();assertFalse(c.pending());}
}
