package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostHealReturnTest {
    private static final Vec3 HOME=new Vec3(.5,70,.5);
    private static GhostHealReturn start(){var h=new GhostHealReturn();h.start(new GhostHealReturn.Pose(HOME,30,5,0,40,0),0);return h;}
    @Test void itemUseAloneCannotResumeCombat(){var h=start();assertEquals(GhostHealReturn.Action.CAST,h.tick(HOME,30,80,40,0,true,true,0,100,1200,15000));assertEquals(GhostHealReturn.Action.NONE,h.tick(HOME,30,80,40,0,true,true,0,2000,1200,15000));assertEquals(GhostHealReturn.State.WAIT_ACK,h.state());}
    @Test void acknowledgedHealRequiresPhysicalReturnAndView(){var h=start();h.tick(HOME,30,80,40,0,true,true,0,100,1200,15000);var displaced=new Vec3(4.5,70,.5);assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(displaced,30,80,90,0,true,true,0,1500,1200,15000));assertEquals(GhostHealReturn.Action.RESTORE_VIEW,h.tick(HOME,30,80,90,0,true,true,0,2000,1200,15000));assertEquals(GhostHealReturn.Action.COMPLETE,h.tick(HOME,30,5,90,0,true,true,0,2500,1200,15000));}
    @Test void shieldIncreaseAlsoAcknowledgesWitherImpact(){var h=start();h.tick(HOME,30,80,40,0,true,true,0,100,100,15000);assertEquals(GhostHealReturn.Action.RESTORE_VIEW,h.tick(HOME,30,80,40,4,true,true,0,200,100,15000));}
    @Test void blockedOrTimedOutHealDoesNotCastAgain(){var h=start();assertEquals(GhostHealReturn.Action.FAILED,h.tick(HOME,30,80,40,0,false,true,0,100,100,15000));h=start();h.tick(HOME,30,80,40,0,true,true,0,100,100,15000);assertEquals(GhostHealReturn.Action.FAILED,h.tick(HOME,30,80,40,0,true,true,0,16000,100,15000));}
    @Test void movingOnTheExactPointIsNotACompletedReturn(){var h=start();h.tick(HOME,30,80,40,0,true,true,0,100,100,15000);assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(HOME,30,5,90,0,true,true,.2,200,100,15000));}
    @Test void refusedUseReturnsWithoutHealthAcknowledgementAndDoesNotRecast(){var h=start();h.tick(HOME,30,80,40,0,true,true,0,100,100,15000);h.refuseCast();assertTrue(h.castRefused());assertEquals(GhostHealReturn.Action.RESTORE_VIEW,h.tick(HOME,30,80,40,0,true,true,0,101,100,15000));assertEquals(GhostHealReturn.Action.COMPLETE,h.tick(HOME,30,5,40,0,true,true,0,102,100,15000));assertEquals(GhostHealReturn.Action.COMPLETE,h.tick(HOME,30,5,40,0,true,true,0,103,100,15000));}

    private static final Vec3 F_GOAL=new Vec3(7.545936574497025,70,.3952714128513624);
    private static final Vec3 F_ACTUAL=new Vec3(7.725955924563885,70,.21125826701745712);
    private static final dev.veltraph.client.navigation.GroundPathfinder.Terrain F_FLOOR=new dev.veltraph.client.navigation.GroundPathfinder.Terrain(){
        public boolean clear(dev.veltraph.client.world.BlockPos p){return p.y()>=70;}
        public boolean floor(dev.veltraph.client.world.BlockPos p){return p.y()==69;}
    };
    private static GhostHealReturn acknowledgedF(){
        var h=new GhostHealReturn();h.start(new GhostHealReturn.Pose(F_GOAL,-85.79645f,-12.999993f,0,20,0),0);
        assertEquals(GhostHealReturn.Action.CAST,h.tick(F_GOAL,-85.79645f,80,20,0,true,true,0,100,300,15000));
        assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(F_ACTUAL,70.30139f,79.17236f,100,0,true,true,0,500,300,15000));return h;
    }
    @Test void actualFDefaultWalkerStopsWhileRecoveryStillRequiresMovement(){
        assertEquals(.2574253372109759,F_ACTUAL.distanceTo(F_GOAL),1e-12);
        var legacy=new dev.veltraph.client.navigation.WalkController(1L);legacy.start(java.util.List.of(),F_GOAL,0);
        var out=legacy.tick(new dev.veltraph.client.navigation.WalkController.Input(F_ACTUAL,70.30139f,true,500),F_FLOOR,4000);
        assertEquals(dev.veltraph.client.navigation.WalkController.State.ARRIVED,out.state());assertFalse(out.forward()||out.jump());
        var h=acknowledgedF();assertEquals(GhostHealReturn.State.RETURN,h.state());
        assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(F_ACTUAL,70.30139f,79.17236f,100,0,true,true,0,600,300,15000));
    }
    @Test void ghostStrictCallerRadiusKeepsActualWalkerMovingAtFDistance(){
        var strict=new dev.veltraph.client.navigation.WalkController(1L);strict.start(java.util.List.of(),F_GOAL,0,GhostHealReturn.RETURN_POSITION_RADIUS);
        var out=strict.tick(new dev.veltraph.client.navigation.WalkController.Input(F_ACTUAL,70.30139f,true,500),F_FLOOR,4000);
        assertEquals(dev.veltraph.client.navigation.WalkController.State.WALKING,out.state());
        assertFalse(out.forward(),"Measured F yaw is initially outside the existing15-degree alignment gate");
        assertTrue(Float.isFinite(out.yaw()));assertTrue(Math.abs(dev.veltraph.client.navigation.WalkController.wrap(out.yaw()-70.30139f))<=6.001);
        int invocation=0;
        while(!out.forward()&&invocation<50){
            float priorYaw=out.yaw();invocation++;
            out=strict.tick(new dev.veltraph.client.navigation.WalkController.Input(F_ACTUAL,priorYaw,true,500+invocation*50L),F_FLOOR,4000);
            assertEquals(dev.veltraph.client.navigation.WalkController.State.WALKING,out.state(),"Stationary F point is still outside recovery radius");
            assertTrue(Float.isFinite(out.yaw()));assertTrue(Math.abs(dev.veltraph.client.navigation.WalkController.wrap(out.yaw()-priorYaw))<=6.001,"Existing speed1 six-degree step limit");
            assertFalse(out.jump());
        }
        assertTrue(out.forward(),"Actual returned smooth-turn yaw must eventually enter the existing alignment gate");
        assertTrue(F_ACTUAL.distanceTo(F_GOAL)>GhostHealReturn.RETURN_POSITION_RADIUS);
        assertEquals(.2574253372109759,F_ACTUAL.distanceTo(F_GOAL),1e-12);
        assertEquals(GhostHealReturn.Action.WALK_RETURN,acknowledgedF().tick(F_ACTUAL,70.30139f,79.17236f,100,0,true,true,0,600,300,15000));
    }
    @Test void tighterArrivalEnablesExistingViewRestoreButKeepsGroundAndSpeedGates(){
        var inside=new Vec3(F_GOAL.x()+.1,F_GOAL.y(),F_GOAL.z());
        var strict=new dev.veltraph.client.navigation.WalkController(1L);strict.start(java.util.List.of(),F_GOAL,0,GhostHealReturn.RETURN_POSITION_RADIUS);
        assertEquals(dev.veltraph.client.navigation.WalkController.State.ARRIVED,strict.tick(new dev.veltraph.client.navigation.WalkController.Input(inside,90,true,500),F_FLOOR,4000).state());
        var h=acknowledgedF();assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(inside,90,79,100,0,true,false,0,600,300,15000));
        assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(inside,90,79,100,0,true,true,.021,650,300,15000));
        assertEquals(GhostHealReturn.Action.RESTORE_VIEW,h.tick(inside,90,79,100,0,true,true,0,700,300,15000));
        assertEquals(GhostHealReturn.Action.COMPLETE,h.tick(inside,-85.79645f,-12.999993f,100,0,true,true,0,800,300,15000));
    }
}
