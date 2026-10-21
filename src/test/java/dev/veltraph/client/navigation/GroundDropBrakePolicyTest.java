package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundDropBrakePolicyTest {
    private static final Vec3 ORIGIN=new Vec3(.5,71,3.5),TARGET=new Vec3(.5,70,4.5),ACTUAL=new Vec3(.5,70.76636799395752,4.67543971796843),V=new Vec3(0,-.230527368912964,.10725115763215755);
    private static final WalkController.Output REQUEST=new WalkController.Output(true,false,0,WalkController.State.WALKING);
    private static GroundLocomotionPolicy.Frame frame(Vec3 p,boolean ground,long now){return new GroundLocomotionPolicy.Frame(p,ground,false,now,true,true,100);}
    private static GroundLocomotionPolicy.Proof proof(GroundDropBrakeForecast.Episode episode){return new GroundLocomotionPolicy.Proof(TARGET,true,true,false,false,ORIGIN,null,false,episode);}
    @Test void brakeEpisodeCannotPublishForwardJumpOrSprintAndStillWaitsForActualGround(){var p=new GroundLocomotionPolicy();p.tick(frame(ORIGIN,true,100),REQUEST,proof(null));var e=GroundDropBrakeForecast.begin(100,false,ACTUAL,V,ORIGIN,TARGET,.1).orElseThrow();var out=p.tick(frame(ACTUAL,false,200),REQUEST,proof(e));assertFalse(out.forward());assertFalse(out.jump());assertFalse(out.sprint());assertFalse(out.blocked());assertEquals(GroundLocomotionPolicy.Phase.DROPPING,p.phase());}
    @Test void lostOriginalCertificateOrOriginalDeadlineStillBlocksBrake(){var p=new GroundLocomotionPolicy();p.tick(frame(ORIGIN,true,100),REQUEST,proof(null));var e=GroundDropBrakeForecast.begin(100,false,ACTUAL,V,ORIGIN,TARGET,.1).orElseThrow();p.tick(frame(ACTUAL,false,200),REQUEST,proof(e));assertTrue(p.tick(frame(ACTUAL,false,1901),REQUEST,proof(e)).blocked());}
}
