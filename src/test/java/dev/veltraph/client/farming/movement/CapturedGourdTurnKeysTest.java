package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CapturedGourdTurnKeysTest {
 @Test void actualYawKeysAdvanceWorldGoalAcrossGradualTurn(){var selector=new CapturedGourdTurnKeys();var from=new Vec3(.5,71,.5);var goal=new Vec3(5.5,71,.5);for(float yaw=0;yaw<=90;yaw+=3){var keys=selector.toward(from,goal,yaw);assertNotNull(keys);assertTrue(keys.dx()>.85);assertFalse(keys.forward()&&keys.back());assertFalse(keys.left()&&keys.right());}}
 @Test void withinArrivalZoneDoesNotDriveWhileCameraSettles(){assertNull(new CapturedGourdTurnKeys().toward(new Vec3(.5,71,.5),new Vec3(.55,71,.5),45));}
 @Test void hysteresisNeverChoosesOppositeGoalMotion(){var selector=new CapturedGourdTurnKeys();for(float yaw=0;yaw<360;yaw+=2){var keys=selector.toward(new Vec3(.5,71,.5),new Vec3(.5,71,5.5),yaw);assertTrue(keys.dz()>.85);}}
}
