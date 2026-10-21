package dev.veltraph.client.navigation;
import dev.veltraph.client.farming.module.FarmMaintenanceState;
import dev.veltraph.client.world.*;
import java.util.*;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarmRouteCheckpointTest {
    private static final WaterTravelController.Geometry CLEAR=new WaterTravelController.Geometry(){
        public boolean connection(Vec3 a,Vec3 b){return true;}public boolean water(Vec3 p){return true;}public boolean ground(Vec3 p){return false;}
    };
    private static WaterTravelController.Input in(Vec3 p,long t){return new WaterTravelController.Input(p,new Vec3(0,0,0),0,false,true,true,200,300,20,true,false,true,t);}
    @Test void concreteWaypointCopyNonzeroCursorAndTimersSurvivePassiveGeometryProof(){
        var first=new Vec3(.5,67,.5);var second=new Vec3(.5,67,3.5);var third=new Vec3(2.5,67,6.5);
        var nodes=new ArrayList<>(List.of(first,second,third));var c=new WaterTravelController();c.start(nodes,20,1234);
        c.tick(in(first,1240),CLEAR,6000,1);var state=c.maintenanceState();nodes.clear();
        assertEquals(1,state.fields().get("index"));assertEquals(List.of(first,second,third),state.fields().get("route"));
        assertTrue(c.maintenanceGeometryClear(first,CLEAR));assertEquals(state,c.maintenanceState());
        c.tick(in(second,1250),CLEAR,6000,1);assertNotEquals(state,c.maintenanceState());assertEquals(1,state.fields().get("index"));
        assertThrows(UnsupportedOperationException.class,()->((List<?>)state.fields().get("route")).clear());coverage(c,state);
    }
    @Test void airDetourPhaseAndBothCursorsAreRetainedAndBlockedGeometryFailsClosed(){
        var c=new WaterTravelController();var anchor=new Vec3(.5,67,.5);c.start(List.of(new Vec3(.5,67,6.5)),20,20);
        c.surface(List.of(anchor,new Vec3(.5,70,1.5)),30);c.tick(in(anchor,40),CLEAR,6000,1);var state=c.maintenanceState();
        assertEquals(WaterTravelController.State.SURFACING,state.fields().get("state"));assertEquals(1,state.fields().get("detourIndex"));
        assertTrue(c.maintenanceGeometryClear(anchor,CLEAR));assertEquals(state,c.maintenanceState());
        var blocked=new WaterTravelController.Geometry(){public boolean connection(Vec3 a,Vec3 b){return false;}public boolean water(Vec3 p){return true;}public boolean ground(Vec3 p){return false;}};
        assertFalse(c.maintenanceGeometryClear(anchor,blocked));assertEquals(state,c.maintenanceState());
        c.stop();assertFalse(c.maintenanceGeometryClear(anchor,CLEAR));assertNotEquals(state,c.maintenanceState());
    }
    @Test void idleGroundFollowerHasCompleteExplicitCheckpointWithoutDrawingRandomSeed(){
        var calls=new int[1];var c=new WalkController(()->{calls[0]++;return 1;});c.start(List.of(new BlockPos(1,64,1)),new Vec3(1.5,64,1.5),12);
        var state=c.maintenanceState();coverage(c,state);assertEquals(state,c.maintenanceState());assertEquals(0,calls[0]);
        c.reset();assertNotEquals(state,c.maintenanceState());
    }
    private static void coverage(Object engine,FarmMaintenanceState state){
        var declared=new HashSet<String>();for(var f:engine.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!f.isSynthetic())declared.add(f.getName());
        assertEquals(declared,state.fields().keySet(),"every declared source progress field must be named");
    }
}
