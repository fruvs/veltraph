package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkControllerSteeringObservationTest {
    private static final Vec3 START=new Vec3(.5,70,.5);
    private static final GroundPathfinder.Terrain OPEN=new GroundPathfinder.Terrain(){
        public boolean clear(BlockPos p){return p.y()>=70;}
        public boolean floor(BlockPos p){return p.y()==69;}
    };
    @Test void openCornerUsesAdmittedContinuousLeadWhileNodeRemainsOriginal() {
        var walker=new WalkController(7L);var first=new Vec3(2.5,70,.5);
        walker.startCertifiedDynamicCourse(List.of(first,new Vec3(2.5,70,5.5),new Vec3(6.5,70,5.5)),
                START,100,.35,OPEN,List.of(true,true,true));
        var output=walker.tick(new WalkController.Input(new Vec3(1.8,70,.5),-90,true,101),OPEN,4000);
        assertEquals(WalkController.State.WALKING,output.state());
        assertEquals(first,walker.locomotionTarget());
        assertTrue(walker.locomotionSteeringTarget().z()>.5D);
        assertTrue(walker.locomotionSteeringTarget().x()>=1.8D);
    }
    @Test void tightCornerKeepsItsExactNodeWhenWideLeadProofRefuses() {
        var corridor=new GroundPathfinder.Terrain(){
            public boolean clear(BlockPos p){return p.y()>=70&&(p.z()==0&&p.x()>=0&&p.x()<=3||p.x()==3&&p.z()>=0&&p.z()<=4);}
            public boolean floor(BlockPos p){return p.y()==69;}
            public boolean walkClear(Vec3 a,Vec3 b){return Math.abs(a.z()-.5D)<.001&&Math.abs(b.z()-.5D)<.001
                    ||Math.abs(a.x()-3.5D)<.001&&Math.abs(b.x()-3.5D)<.001;}
        };
        var walker=new WalkController(8L);var first=new Vec3(3.5,70,.5);
        walker.startCertifiedDynamicCourse(List.of(first,new Vec3(3.5,70,4.5)),START,100,.35,corridor,List.of(false,false));
        walker.tick(new WalkController.Input(new Vec3(2.8,70,.5),-90,true,101),corridor,4000);
        assertEquals(first,walker.locomotionSteeringTarget());
        assertEquals(first,walker.locomotionTarget());
    }
    @Test void observationNeverMutatesCameraCourseProgressOrClock() {
        var walker=new WalkController(9L);walker.startCourse(List.of(new Vec3(5.5,70,.5)),START,100,.35,OPEN);
        walker.tick(new WalkController.Input(START,0,true,101),OPEN,4000);
        String before=walker.diagnostics();Vec3 hint=walker.locomotionSteeringTarget();
        for(int i=0;i<100;i++)assertSame(hint,walker.locomotionSteeringTarget());
        assertEquals(before,walker.diagnostics());
        assertEquals(WalkController.State.WALKING,walker.state());
        assertTrue(walker.locomotionProgressCurrent(101,4000));
    }
    @Test void resetAndRefusalCannotRetainAnOldDirectionalTarget() {
        var walker=new WalkController(10L);var goal=new Vec3(5.5,70,.5);
        walker.startCourse(List.of(goal),START,100,.35,OPEN);
        walker.tick(new WalkController.Input(START,0,true,101),OPEN,4000);assertNotNull(walker.locomotionSteeringTarget());
        var blocked=new GroundPathfinder.Terrain(){public boolean clear(BlockPos p){return false;}public boolean floor(BlockPos p){return false;}};
        assertEquals(WalkController.State.BLOCKED,walker.tick(new WalkController.Input(START,0,true,102),blocked,4000).state());
        assertNull(walker.locomotionSteeringTarget());
        walker.reset();assertNull(walker.locomotionSteeringTarget());
    }
    @Test void terminalArrivalUsesOriginalRadiusAndNoDirectionalLead() {
        var walker=new WalkController(11L);walker.startCourse(List.of(START),START,100,.05,OPEN);
        assertEquals(WalkController.State.ARRIVED,walker.tick(new WalkController.Input(START,0,true,101),OPEN,4000).state());
        assertNull(walker.locomotionSteeringTarget());
    }
}
