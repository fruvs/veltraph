package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLandingBrakePolicyTest {
    private static final Vec3 START=new Vec3(.5,70,3.262479782250013),TARGET=new Vec3(.5,71,4.5),AIR=new Vec3(.5,71.02442408821369,4.57142936314265);
    private static final WalkController.Output REQUEST=new WalkController.Output(true,true,27,WalkController.State.WALKING);
    private static GroundLocomotionPolicy.Frame frame(Vec3 pos,boolean ground,long now,int tick){return new GroundLocomotionPolicy.Frame(pos,ground,false,now,true,true,tick);}
    private static GroundLocomotionPolicy.Proof proof(boolean brake){return new GroundLocomotionPolicy.Proof(TARGET,true,true,true,false,null,null,brake);}
    private static GroundLocomotionPolicy launched(){var p=new GroundLocomotionPolicy();assertTrue(p.tick(frame(START,true,100,100),REQUEST,proof(false)).jump());p.tick(frame(new Vec3(.5,70.75,3.7),false,200,102),REQUEST,proof(false));return p;}
    @Test void provenApproachingLandingReleasesMovementAndRetainsOwnedHeadingOrigin(){var p=launched();var out=p.tick(frame(AIR,false,500,108),REQUEST,proof(true));assertEquals(GroundLocomotionPolicy.Phase.LANDING_BRAKE,p.phase());assertFalse(out.forward());assertFalse(out.jump());assertFalse(out.sprint());assertFalse(out.blocked());assertEquals(START,p.jumpOrigin(AIR));assertEquals(100,p.jumpIssuedTick());}
    @Test void predictedLandingCannotCompleteWithoutNativeGroundReceipt(){var p=launched();p.tick(frame(AIR,false,500,108),REQUEST,proof(true));var stillAir=p.tick(frame(AIR,false,550,109),REQUEST,proof(true));assertFalse(stillAir.forward());assertEquals(GroundLocomotionPolicy.Phase.LANDING_BRAKE,p.phase());var landed=p.tick(frame(new Vec3(.5,71,4.728),true,600,110),REQUEST,proof(false));assertFalse(landed.forward());assertEquals(GroundLocomotionPolicy.Phase.LANDED,p.phase());}
    @Test void landingBrakeStillRejectsLostSweepForeignScopeAndOriginalDeadline(){var p=launched();p.tick(frame(AIR,false,500,108),REQUEST,proof(true));assertTrue(p.tick(frame(AIR,false,550,109),REQUEST,new GroundLocomotionPolicy.Proof(TARGET,true,true,false,false,null,null,true)).blocked());var q=launched();q.tick(frame(AIR,false,500,108),REQUEST,proof(true));assertTrue(q.tick(frame(AIR,false,1901,140),REQUEST,proof(true)).blocked());var r=launched();r.tick(frame(AIR,false,500,108),REQUEST,proof(true));assertTrue(r.tick(new GroundLocomotionPolicy.Frame(AIR,false,false,550,false,true,109),REQUEST,proof(true)).blocked());}
}
