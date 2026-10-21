package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Verifies the actual native adapter's pure takeoff/endpoint admission contract, not native collision proof. */
class GroundLocomotionChainedGeometryTest {
    private static final Vec3 A=new Vec3(.5,70,3.5),B=new Vec3(.5,71,4.5),C=new Vec3(.5,72,5.5);
    private static final WalkController.Output JUMP=new WalkController.Output(true,true,0,WalkController.State.WALKING),WALK=new WalkController.Output(true,false,0,WalkController.State.WALKING);
    private static GroundLocomotionPolicy.Frame frame(Vec3 p,boolean ground,long now){return new GroundLocomotionPolicy.Frame(p,ground,false,now,true,true);}
    private static GroundLocomotionPolicy.Proof proof(Vec3 target){return new GroundLocomotionPolicy.Proof(target,true,true,true,false);}
    private static void firstJump(GroundLocomotionPolicy p){p.tick(frame(A,true,100),JUMP,proof(B));p.tick(frame(new Vec3(.5,70.75,4),false,200),WALK,proof(B));p.tick(frame(B,true,400),WALK,proof(B));assertEquals(GroundLocomotionPolicy.Phase.LANDED,p.phase());}
    @Test void consecutiveRisesUseFreshActualTakeoffAndRetainAntiRetryTarget(){
        var p=new GroundLocomotionPolicy();firstJump(p);
        assertEquals(B,p.jumpOrigin(B));assertFalse(GroundLocomotionMotion.jumpEndpoints(A,C));assertTrue(GroundLocomotionMotion.jumpEndpoints(p.jumpOrigin(B),C));
        var second=p.tick(frame(B,true,450),JUMP,proof(C));assertTrue(second.jump());assertEquals(B,p.takeoff());assertEquals(C,p.landing());assertEquals(B,p.jumpOrigin(new Vec3(.5,71.5,5)));
    }
    @Test void dropThenRiseCannotUseTheOldHigherDropOrigin(){
        var p=new GroundLocomotionPolicy();var low=new Vec3(.5,70,5.5);var up=new Vec3(.5,71,6.5);
        var drop=new GroundLocomotionPolicy.Proof(low,true,true,false,false,B);
        p.tick(frame(B,true,100),WALK,drop);p.tick(frame(new Vec3(.5,70.75,5.1),false,200),WALK,drop);p.tick(frame(low,true,400),WALK,drop);
        assertEquals(low,p.jumpOrigin(low));assertFalse(GroundLocomotionMotion.jumpEndpoints(B,up));assertTrue(GroundLocomotionMotion.jumpEndpoints(p.jumpOrigin(low),up));
        assertTrue(p.tick(frame(low,true,450),JUMP,proof(up)).jump());assertEquals(low,p.takeoff());
    }
    @Test void clearingOldGeometryDoesNotClearSameJumpAntiRetryTarget(){
        var p=new GroundLocomotionPolicy();firstJump(p);
        assertTrue(p.tick(frame(A,true,450),JUMP,proof(B)).blocked());
    }
    @Test void neutralRearmClearsTraversalGeometryAndNeverChangesActiveTakeoffFromActualAirPose(){
        var p=new GroundLocomotionPolicy();p.tick(frame(A,true,100),JUMP,proof(B));assertEquals(A,p.jumpOrigin(new Vec3(.5,70.5,4)));
        p.tick(frame(new Vec3(.5,70.75,4),false,200),WALK,proof(B));assertEquals(A,p.jumpOrigin(new Vec3(.5,71.1,4.3)));
        p.tick(frame(B,true,400),WALK,proof(B));p.tick(frame(B,true,450),WALK,proof(new Vec3(.5,71,7)));
        assertNull(p.takeoff());assertNull(p.landing());assertEquals(C,p.jumpOrigin(C));
    }
}
