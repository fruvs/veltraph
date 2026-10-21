package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundJumpEnvelopeEvidenceTest {
    @Test void envelopeRecorderReturnsOriginalQueryResultAndFreezesCachedPoint(){var e=new GroundLocomotionEvidence();var point=new Vec3(.7885532008338523,71.26,7.160557);assertFalse(e.envelope("BODY",point,-.10,-.18,false));var s=e.snapshot("done",GroundLocomotionPolicy.Phase.READY,null,null,null);e.envelope("BODY",new Vec3(2,2,2),0,0,true);assertEquals(point,s.envelopePoint());assertFalse(s.envelopeQueryPassed());assertEquals(-.1,s.envelopeProgress());assertEquals(-.18,s.envelopeLateral());}
    @Test void actualCore14OffsetCanMakeUnchangedEnvelopeReachWallBeforeJump(){var takeoff=new Vec3(.6011004412049329,70,7.245771955829208);var landing=new Vec3(.5,71,8.5);double length=takeoff.horizontalDistanceTo(landing),dx=(landing.x()-takeoff.x())/length,dz=(landing.z()-takeoff.z())/length;double centerX=takeoff.x()+dx*(-.10)-dz*(-.18);assertEquals(.7885532008338523,centerX,1e-12);assertTrue(centerX+.3>1);assertTrue(takeoff.x()+.3<1);}
}
