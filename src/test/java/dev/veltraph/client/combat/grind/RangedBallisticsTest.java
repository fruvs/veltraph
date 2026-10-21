package dev.veltraph.client.combat.grind;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RangedBallisticsTest {
    @Test void leadsMovingMobAndCompensatesGravity(){var point=RangedBallistics.aim(new Vec3(0,2,0),new Vec3(12,1,0),new Vec3(0,0,.15),false);assertEquals(.6,point.z(),.001);assertEquals(1.4,point.y(),.01);}
    @Test void leadIsBoundedForPacketJumps(){var point=RangedBallistics.aim(new Vec3(0,2,0),new Vec3(200,1,0),new Vec3(10,0,-10),false);assertEquals(205.6,point.x(),.001);assertEquals(-5.6,point.z(),.001);}
    @Test void flightContinuesPastAimedMobAndIncludesShooterMotion(){var path=RangedBallistics.flight(new Vec3(0,2,0),-90,0,new Vec3(0,0,.2),false);assertTrue(path.get(8).x()>20);assertTrue(path.get(8).z()>1);assertTrue(path.get(8).y()<2);}
}
