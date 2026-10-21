package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterTravelControllerTest {
    final Vec3 goal=new Vec3(.5,67,12.5),surface=new Vec3(.5,70,.5);
    final WaterTravelController.Geometry geometry=new WaterTravelController.Geometry() {
        public boolean connection(Vec3 a,Vec3 b) { return true; }
        public boolean ground(Vec3 p) { return p.y()>=71; }
        public boolean water(Vec3 p) { return p.y()<71; }
    };
    WaterTravelController.Input input(Vec3 p,boolean submerged,int air,float health,boolean jump,long now) {
        return new WaterTravelController.Input(p,new Vec3(0,0,0),0,false,true,submerged,air,300,health,true,false,jump,now);
    }
    @Test void airDetourRetainsGoalAndWaitsForObservedRefill() {
        var c=new WaterTravelController();c.start(List.of(goal),20,0);
        assertTrue(c.needsAir(input(new Vec3(.5,67,.5),true,120,20,true,50)));
        c.surface(List.of(surface),50);
        assertTrue(c.tick(input(new Vec3(.5,67,.5),true,110,20,true,100),geometry,6000,1).jump());
        assertEquals(WaterTravelController.State.REFILLING,c.tick(input(surface,false,120,20,true,150),geometry,6000,1).state());
        assertEquals(WaterTravelController.State.REFILLING,c.tick(input(surface,false,260,20,true,200),geometry,6000,1).state());
        assertEquals(WaterTravelController.State.TRAVELLING,c.tick(input(surface,false,280,20,true,250),geometry,6000,1).state());
        assertEquals(goal,c.target());assertTrue(c.tick(input(surface,false,280,20,true,300),geometry,6000,1).sneak());
    }
    @Test void missingSurfaceAndActualDamageAreTerminalAndReleaseInputs() {
        var c=new WaterTravelController();c.start(List.of(goal),20,0);c.surface(List.of(),100);
        assertReleased(c.tick(input(new Vec3(.5,67,.5),true,100,20,true,100),geometry,6000,1));
        c.start(List.of(goal),20,0);assertReleased(c.tick(input(new Vec3(.5,67,.5),true,250,19,true,100),geometry,6000,1));
    }
    @Test void lavaAndObstructionCannotBeMadePassableByAirRecovery() {
        var c=new WaterTravelController();c.start(List.of(goal),20,0);
        var p=new Vec3(.5,67,.5);
        assertReleased(c.tick(new WaterTravelController.Input(p,new Vec3(0,0,0),0,false,true,true,250,300,20,true,true,true,100),geometry,6000,1));
        c.start(List.of(goal),20,0);
        var blocked=new WaterTravelController.Geometry() {
            public boolean connection(Vec3 a,Vec3 b) { return false; }
            public boolean ground(Vec3 p) { return false; }
            public boolean water(Vec3 p) { return true; }
        };
        assertReleased(c.tick(input(p,true,250,20,true,100),blocked,6000,1));
    }
    @Test void recoveredEquipmentGateControlsSneakAndDoesNotForbidExitJump() {
        var c=new WaterTravelController();c.start(List.of(new Vec3(.5,71,1.5)),20,0);
        var exit=c.tick(input(new Vec3(.5,70,.5),false,300,20,false,100),geometry,6000,1);
        assertEquals(WaterTravelController.State.TRAVELLING,exit.state());assertTrue(exit.jump());assertFalse(exit.sneak());
        c.start(List.of(goal),20,0);
        var descent=c.tick(input(surface,false,300,20,false,100),geometry,6000,1);assertFalse(descent.sneak());
    }
    @Test void supportedDryExitAcceptsOrdinaryStandingGravity() {
        var c=new WaterTravelController();var dry=new Vec3(.5,71,1.5);c.start(List.of(dry),20,0);
        var out=c.tick(new WaterTravelController.Input(dry,new Vec3(0,-.0784,0),0,true,false,false,300,300,20,true,false,true,100),geometry,6000,1);
        assertEquals(WaterTravelController.State.ARRIVED,out.state());assertFalse(out.jump()||out.sneak());
    }
    @Test void regenerationDoesNotConcealLaterDamage() {
        var c=new WaterTravelController();c.start(List.of(goal),18,0);
        assertEquals(WaterTravelController.State.TRAVELLING,c.tick(input(new Vec3(.5,67,.5),true,250,20,true,50),geometry,6000,1).state());
        assertReleased(c.tick(input(new Vec3(.5,67,.5),true,250,19,true,100),geometry,6000,1));
    }
    @Test void alignedIntermediateSwimKeepsForwardHeldAtNativeVelocity() {
        var c=new WaterTravelController();c.start(List.of(new Vec3(.5,67,4.5),goal),20,0);
        var out=c.tick(new WaterTravelController.Input(new Vec3(.5,67,.5),new Vec3(0,0,.16),0,true,true,true,250,300,20,true,false,true,50),geometry,6000,1);
        assertTrue(out.forward());assertFalse(out.back());
    }
    @Test void closeSupportedExitReleasesKeysWhileNativeFrictionSettles() {
        var c=new WaterTravelController();var goal=new Vec3(.5,71,1.5);c.start(List.of(goal),20,0);
        var out=c.tick(new WaterTravelController.Input(new Vec3(.5,71,1.24),new Vec3(0,-.0784,.05),0,true,false,false,300,300,20,true,false,true,50),geometry,6000,1);
        assertEquals(WaterTravelController.State.TRAVELLING,out.state());assertFalse(out.forward()||out.back()||out.left()||out.right()||out.jump()||out.sneak());
    }
    @Test void breathableRefillDoesNotChaseFractionalYOrTriggerSurfaceJump() {
        var c=new WaterTravelController();c.start(List.of(goal),20,0);c.surface(List.of(surface),50);
        var out=c.tick(input(new Vec3(.5,69.7,.5),false,150,20,true,100),geometry,6000,1);
        assertEquals(WaterTravelController.State.REFILLING,out.state());assertFalse(out.jump()||out.sneak());
    }
    @Test void shallowGroundedDropDoesNotEnableVanillaCrouchLedgeProtection() {
        var c=new WaterTravelController();c.start(List.of(new Vec3(7.5,74,84.5),new Vec3(7.4,73.875,83.5)),20,0);
        var out=c.tick(new WaterTravelController.Input(new Vec3(7.15,76.875,84.42),new Vec3(.008,-.005,0),-90,true,true,false,300,300,20,true,false,true,50),geometry,6000,1);
        assertEquals(WaterTravelController.State.TRAVELLING,out.state());assertFalse(out.sneak());
    }
    private static void assertReleased(WaterTravelController.Output out) {
        assertEquals(WaterTravelController.State.BLOCKED,out.state());
        assertFalse(out.forward()||out.back()||out.left()||out.right()||out.jump()||out.sneak());
    }
}
