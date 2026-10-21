package dev.veltraph.client.navigation;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class StrictWalkReturnTest {
    private static final Vec3 TARGET=new Vec3(1.5,1,.5);
    private GroundPathfinder.Terrain floor(boolean clear){return new GroundPathfinder.Terrain(){
        public boolean clear(BlockPos p){return clear&&p.y()>=1;}
        public boolean floor(BlockPos p){return p.y()==0;}
    };}
    @Test void strictEndpointContinuesWalkingWhereExistingDefaultStops(){
        var near=new WalkController.Input(new Vec3(1.22,1,.5),-90,true,100);
        var defaults=new WalkController(()->1L);defaults.start(List.of(),TARGET,0);
        assertEquals(WalkController.State.ARRIVED,defaults.tick(near,floor(true),2000).state());
        var strict=new WalkController(()->1L);strict.start(List.of(),TARGET,0,.14);
        var stillWalking=strict.tick(near,floor(true),2000);
        assertEquals(WalkController.State.WALKING,stillWalking.state());assertTrue(stillWalking.forward());
        var arrived=strict.tick(new WalkController.Input(new Vec3(1.37,1,.5),-90,true,200),floor(true),2000);
        assertEquals(WalkController.State.ARRIVED,arrived.state());assertFalse(arrived.forward()||arrived.jump());
    }
    @Test void regroundAndCollisionAreRequiredEvenInsideStrictRadius(){
        var strict=new WalkController(()->1L);strict.start(List.of(),TARGET,0,.14);
        assertEquals(WalkController.State.WALKING,strict.tick(new WalkController.Input(TARGET,-90,false,100),floor(true),2000).state());
        assertEquals(WalkController.State.ARRIVED,strict.tick(new WalkController.Input(TARGET,-90,true,200),floor(true),2000).state());
        strict.start(List.of(),TARGET,0,.14);
        var obstructed=strict.tick(new WalkController.Input(TARGET,-90,true,100),floor(false),2000);
        assertEquals(WalkController.State.BLOCKED,obstructed.state());assertFalse(obstructed.forward()||obstructed.jump());
    }
    @Test void strictPoseCannotStayWalkingForeverAndDefaultsResetOnNewRoute(){
        var strict=new WalkController(()->1L);strict.start(List.of(),TARGET,0,.14);
        var position=new Vec3(1.22,1,.5);strict.tick(new WalkController.Input(position,-90,true,0),floor(true),500);
        var timeout=strict.tick(new WalkController.Input(position,-90,true,501),floor(true),500);
        assertEquals(WalkController.State.BLOCKED,timeout.state());assertFalse(timeout.forward()||timeout.jump());
        strict.start(List.of(),TARGET,600);
        assertEquals(WalkController.State.ARRIVED,strict.tick(new WalkController.Input(position,-90,true,601),floor(true),500).state());
    }
    @Test void stricterRadiusCannotLoosenExistingArrivalProof(){
        var strict=new WalkController();
        for(double value:new double[]{0,-1,.36,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->strict.start(List.of(),TARGET,0,value));
    }
}
