package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmphibiousPathfinderTest {
    AmphibiousPathfinder.Terrain terrain(boolean allowDrop) {
        return new AmphibiousPathfinder.Terrain() {
            public Vec3 position(BlockPos p) { return new Vec3(p.x()+.5,p.y(),p.z()+.5); }
            public boolean occupiable(Vec3 p) { return p.x()<1&&p.y()==5||p.x()>=1&&p.y()>=1&&p.y()<=3&&p.z()>=0&&p.z()<1; }
            public boolean startClear(Vec3 p) { return occupiable(p); }
            public boolean water(Vec3 p) { return p.x()>=1; }
            public boolean connection(Vec3 a,Vec3 b,int maxDrop) {
                return Math.abs(a.y()-b.y())<=1||allowDrop&&maxDrop>=a.y()-b.y();
            }
        };
    }
    @Test void explicitDropIsRequiredAndExactWaterGoalIsPreserved() {
        Vec3 from=new Vec3(.5,5,.5),goal=new Vec3(4.76,1.2,.27);
        assertFalse(AmphibiousPathfinder.find(terrain(true),from,goal,12,1000,0).found());
        var result=AmphibiousPathfinder.find(terrain(true),from,goal,12,1000,4);
        assertTrue(result.found());assertEquals(goal,result.nodes().getLast());
    }
    @Test void missingLoadedGoalAndSearchBoundsFailClosed() {
        var from=new Vec3(.5,5,.5);
        assertFalse(AmphibiousPathfinder.find(terrain(true),from,new Vec3(4.5,0,.5),12,1000,4).found());
        assertFalse(AmphibiousPathfinder.find(terrain(true),from,new Vec3(20.5,1,.5),1,2,4).found());
    }
    @Test void observedAirRecoveryCanDescendSameColumnWithoutGrantingGeneralAirNodes() {
        var t=new AmphibiousPathfinder.Terrain() {
            public Vec3 position(BlockPos p) { return new Vec3(p.x()+.5,p.y(),p.z()+.5); }
            public boolean occupiable(Vec3 p) { return p.x()==.5&&p.z()==.5&&p.y()>=1&&p.y()<=2; }
            public boolean water(Vec3 p) { return occupiable(p); }
            public boolean airRecovery(Vec3 p) { return p.x()==.5&&p.z()==.5&&p.y()>2&&p.y()<4; }
            public boolean startClear(Vec3 p) { return water(p)||airRecovery(p); }
            public boolean connection(Vec3 a,Vec3 b,int drop) { return a.x()==b.x()&&a.z()==b.z()&&a.y()-b.y()<=3; }
        };
        var result=AmphibiousPathfinder.find(t,new Vec3(.5,3.1,.5),new Vec3(.5,1,.5),4,50,0);
        assertTrue(result.found());assertEquals(new Vec3(.5,1,.5),result.nodes().getLast());
        assertFalse(AmphibiousPathfinder.find(t,new Vec3(1.5,3.1,.5),new Vec3(.5,1,.5),4,50,0).found());
    }
}
