package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenFlightCourseTest {
    private static final Vec3 START=new Vec3(.5,81,.5),GOAL=new Vec3(24.5,81,.5);
    @Test void journeysVaryInThreeDimensionsWithUnchangedExactEndpointsAndBoundedCost() {
        var courses=new ArrayList<List<Vec3>>();
        for(long seed:new long[]{1,67,99999,809}) {
            var course=OpenFlightCourse.shape(START,List.of(GOAL),(a,b)->true,(a,b)->true,seed);courses.add(course);
            assertEquals(GOAL,course.getLast());
            assertTrue(course.stream().anyMatch(p->Math.abs(p.z()-.5)>.7));
            assertTrue(course.stream().anyMatch(p->Math.abs(p.y()-81)>.15));
            assertTrue(length(START,course)<=30.00000001);
            for(var p:course)if(p.x()<=2.5||p.x()>=22.5){assertEquals(.5,p.z(),1e-9);assertEquals(81,p.y(),1e-9);}
            assertEquals(course,OpenFlightCourse.shape(START,List.of(GOAL),(a,b)->true,(a,b)->true,seed));
        }
        assertEquals(courses.size(),new HashSet<>(courses).size());
    }
    @Test void oneBlockFlightPassageRetainsItsCenterlineForEverySeed() {
        BiPredicate<Vec3,Vec3> narrow=(a,b)->Math.abs(a.z()-.5)<.05&&Math.abs(b.z()-.5)<.05
                &&Math.abs(a.y()-81)<.05&&Math.abs(b.y()-81)<.05;
        for(long seed:new long[]{1,67,99999}) {
            var course=OpenFlightCourse.shape(START,List.of(GOAL),narrow,narrow,seed);
            assertFalse(course.isEmpty());assertEquals(GOAL,course.getLast());
            assertTrue(course.stream().allMatch(p->p.z()==.5&&p.y()==81));
        }
    }
    @Test void roofAndFloorDoNotAuthorizeAVerticalCurve() {
        BiPredicate<Vec3,Vec3> low=(a,b)->Math.abs(a.y()-81)<=.1&&Math.abs(b.y()-81)<=.1;
        var course=OpenFlightCourse.shape(START,List.of(GOAL),low,low,1);
        assertFalse(course.isEmpty());assertTrue(course.stream().allMatch(p->p.y()==81&&p.z()==.5));
    }
    @Test void offsetRoomAtOneEndDoesNotQualifyTheWholePassage() {
        BiPredicate<Vec3,Vec3> pinch=(a,b)->(Math.abs(a.z()-.5)<.05&&Math.abs(b.z()-.5)<.05)
                ||Math.max(a.x(),b.x())<5;
        var course=OpenFlightCourse.shape(START,List.of(GOAL),pinch,pinch,67);
        assertFalse(course.isEmpty());assertTrue(course.stream().allMatch(p->p.z()==.5&&p.y()==81));
    }
    @Test void diagonalObstructionInEnvelopePreventsBorrowingAxisOnlyClearance() {
        BiPredicate<Vec3,Vec3> cross=(a,b)->(a.z()==.5||a.y()==81)&&(b.z()==.5||b.y()==81);
        var course=OpenFlightCourse.shape(START,List.of(GOAL),cross,cross,99999);
        assertFalse(course.isEmpty());assertTrue(course.stream().allMatch(p->p.z()==.5&&p.y()==81));
    }
    @Test void bendsAroundANativeBlockedStraightShortcutOnlyAlongProvedOriginalEdges() {
        Vec3 a=new Vec3(4.5,81,.5),b=new Vec3(4.5,81,4.5),goal=new Vec3(8.5,81,4.5);
        BiPredicate<Vec3,Vec3> axis=(x,y)->x.x()==y.x()||x.z()==y.z();
        var course=OpenFlightCourse.shape(START,List.of(a,b,goal),axis,(x,y)->false,1);
        assertEquals(goal,course.getLast());Vec3 previous=START;
        for(var p:course){assertTrue(axis.test(previous,p));previous=p;}
    }
    @Test void cannotEmitUnloadedOrDisconnectedFallback() {
        assertTrue(OpenFlightCourse.shape(START,List.of(GOAL),(a,b)->false,(a,b)->true,1).isEmpty());
        assertTrue(OpenFlightCourse.shape(START,List.of(new Vec3(Double.NaN,81,.5)),(a,b)->true,(a,b)->true,1).isEmpty());
    }
    @Test void aPreferredRoomProbeCannotOverrideARequiredBodyVeto() {
        BiPredicate<Vec3,Vec3> actual=(a,b)->a.z()==.5&&b.z()==.5&&a.y()==81&&b.y()==81;
        var course=OpenFlightCourse.shape(START,List.of(GOAL),actual,(a,b)->true,1);
        assertFalse(course.isEmpty());assertEquals(GOAL,course.getLast());
        assertTrue(course.stream().allMatch(p->p.z()==.5&&p.y()==81));
    }
    private static double length(Vec3 from,List<Vec3> nodes) {
        double total=0;for(var node:nodes){total+=from.distanceTo(node);from=node;}return total;
    }
}
