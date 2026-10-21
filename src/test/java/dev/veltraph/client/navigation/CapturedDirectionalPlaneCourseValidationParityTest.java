package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen full-M reference arithmetic. Source-only authored tests: no native/query/budget oracle. */
class CapturedDirectionalPlaneCourseValidationParityTest {
    private record Rectangle(Vec3 minimum, Vec3 maximum) {}

    // Exact frozen M describe semantics, deliberately independent of the new boolean validator.
    private static Optional<Rectangle> originalDescribe(List<Vec3> course) {
        if (course == null || course.isEmpty() || course.size() > 6001) return Optional.empty();
        Vec3 first = course.getFirst();
        if (!GroundLocomotionMotion.finite(first)) return Optional.empty();
        double minX=first.x(), maxX=first.x(), minZ=first.z(), maxZ=first.z();
        for (var point : course) {
            if (!GroundLocomotionMotion.finite(point) || point.y()!=first.y()) return Optional.empty();
            minX=Math.min(minX,point.x()); maxX=Math.max(maxX,point.x());
            minZ=Math.min(minZ,point.z()); maxZ=Math.max(maxZ,point.z());
        }
        return Optional.of(new Rectangle(new Vec3(minX,first.y(),minZ),new Vec3(maxX,first.y(),maxZ)));
    }

    private static Optional<List<Rectangle>> originalLead(List<Vec3> course) {
        if (originalDescribe(course).isEmpty() || course.size()>66) return Optional.empty();
        var rectangles=new ArrayList<Rectangle>();
        for (int index=1; index<course.size(); index++) {
            int first=Math.max(0,index-3);
            Vec3 lo=course.get(first), hi=lo;
            double minX=lo.x(), maxX=lo.x(), minZ=lo.z(), maxZ=lo.z();
            int last=index, completeLast=index; double remaining=1.25D;
            while (last+1<course.size() && remaining>0D) {
                var from=course.get(last); var to=course.get(last+1);
                double length=from.horizontalDistanceTo(to); last++;
                if (length>remaining) {
                    var end=TravelPath.lerp(from,to,remaining/length);
                    minX=Math.min(minX,end.x()); maxX=Math.max(maxX,end.x());
                    minZ=Math.min(minZ,end.z()); maxZ=Math.max(maxZ,end.z()); break;
                }
                remaining-=length; completeLast=last;
            }
            for (int i=first; i<=completeLast; i++) {
                var point=course.get(i);
                minX=Math.min(minX,point.x()); maxX=Math.max(maxX,point.x());
                minZ=Math.min(minZ,point.z()); maxZ=Math.max(maxZ,point.z());
            }
            rectangles.add(new Rectangle(new Vec3(minX-.65D,lo.y(),minZ-.65D),
                    new Vec3(maxX+.65D,lo.y(),maxZ+.65D)));
        }
        if (rectangles.isEmpty()) {
            var point=course.getFirst(); rectangles.add(new Rectangle(point,point));
        }
        return Optional.of(List.copyOf(rectangles));
    }

    private static long[] bits(Vec3 v) {
        return new long[]{Double.doubleToRawLongBits(v.x()),Double.doubleToRawLongBits(v.y()),Double.doubleToRawLongBits(v.z())};
    }
    private static void same(Rectangle a, CapturedDirectionalPlaneCourse.Rectangle b) {
        assertArrayEquals(bits(a.minimum()),bits(b.minimum()));
        assertArrayEquals(bits(a.maximum()),bits(b.maximum()));
    }
    private static void parity(List<Vec3> course) {
        var expected=originalLead(course); var actual=CapturedDirectionalPlaneCourse.leadDomain(course);
        assertEquals(expected.isPresent(),actual.isPresent());
        if (expected.isPresent()) {
            assertEquals(expected.orElseThrow().size(),actual.orElseThrow().rectangles().size());
            for (int i=0; i<expected.orElseThrow().size(); i++)
                same(expected.orElseThrow().get(i),actual.orElseThrow().rectangles().get(i));
        }
    }
    private static List<Vec3> course(int count) {
        var values=new ArrayList<Vec3>();
        for (int i=0; i<count; i++) values.add(new Vec3(i*.17,70,(i%4)*.09));
        return values;
    }

    @Test void exactRawRectanglesForCoincidentZigzagAndPartialLeadEdges() {
        for (int count : new int[]{1,2,4,9,32,66}) parity(course(count));
        parity(List.of(new Vec3(0,70,0),new Vec3(0,70,0),new Vec3(.01,70,.01),
                new Vec3(2,70,-3),new Vec3(-2,70,4)));
        parity(List.of(new Vec3(Double.MAX_VALUE,0,Double.MAX_VALUE),
                new Vec3(-Double.MAX_VALUE,0,-Double.MAX_VALUE)));
    }

    @Test void originalSixtySixAndGlobalValidationBoundariesRemainDistinct() {
        for (int count : new int[]{66,67,6001,6002}) {
            var values=course(count); parity(values);
            assertEquals(count<=66,CapturedDirectionalPlaneCourse.leadDomain(values).isPresent());
            assertEquals(count<=6001,CapturedDirectionalPlaneCourse.describe(values).isPresent());
        }
        assertEquals(65,CapturedDirectionalPlaneCourse.leadDomain(course(66)).orElseThrow().rectangles().size());
    }

    @Test void nullNonfiniteNonlevelAndSignedZeroMatchFrozenReference() {
        parity(null); parity(List.of()); parity(Arrays.asList((Vec3)null));
        for (Vec3 bad : new Vec3[]{null,new Vec3(Double.NaN,70,0),new Vec3(0,Double.NaN,0),
                new Vec3(0,70,Double.POSITIVE_INFINITY),new Vec3(Double.NEGATIVE_INFINITY,70,0),new Vec3(1,71,1)})
            parity(Arrays.asList(new Vec3(0,70,0),bad));
        parity(List.of(new Vec3(-0D,-0D,+0D),new Vec3(+0D,+0D,-0D),new Vec3(.5,-0D,.5)));
    }

    @Test void mutableInputDoesNotChangeReturnedImmutableDomain() {
        var input=new ArrayList<>(course(4)); var expected=originalLead(input).orElseThrow();
        var result=CapturedDirectionalPlaneCourse.leadDomain(input).orElseThrow();
        input.set(0,new Vec3(900,901,902)); input.clear();
        assertEquals(expected.size(),result.rectangles().size());
        for (int i=0;i<expected.size();i++) same(expected.get(i),result.rectangles().get(i));
        assertThrows(UnsupportedOperationException.class,()->result.rectangles().clear());
        assertThrows(UnsupportedOperationException.class,()->result.rectangles().add(result.rectangles().getFirst()));
    }

    @Test void foreignListEncounterSequenceIsUnchangedForValidCourses() {
        for (int count : new int[]{1,4,66,67}) {
            var old=new ForeignList(course(count),null,null); var actual=new ForeignList(course(count),null,null);
            var expected=originalLead(old); var result=CapturedDirectionalPlaneCourse.leadDomain(actual);
            assertEquals(expected.isPresent(),result.isPresent()); assertEquals(old.encounters,actual.encounters);
        }
    }

    @Test void invalidOversizeCourseStillValidatesBeforeSixtySixCheck() {
        var values=new ArrayList<>(course(67)); values.set(50,new Vec3(Double.NaN,70,0));
        var old=new ForeignList(values,null,null); var actual=new ForeignList(values,null,null);
        assertTrue(originalLead(old).isEmpty()); assertTrue(CapturedDirectionalPlaneCourse.leadDomain(actual).isEmpty());
        assertEquals(old.encounters,actual.encounters);
        assertTrue(actual.encounters.contains("next:50")); assertFalse(actual.encounters.contains("next:51"));
        assertEquals(1,actual.encounters.stream().filter("size"::equals).count());
        var globallyTooLarge=new ForeignList(course(6002),null,null);
        assertTrue(CapturedDirectionalPlaneCourse.leadDomain(globallyTooLarge).isEmpty());
        assertEquals(List.of("isEmpty","size"),globallyTooLarge.encounters);
    }

    @Test void foreignListExceptionsKeepIdentityAndOriginalEncounterOrder() {
        for (String site : List.of("isEmpty","size","getFirst","iterator","hasNext:0","next:3")) {
            for (Throwable marker : List.of(new IllegalStateException(site),new AssertionError(site))) {
                var old=new ForeignList(course(67),site,marker); var actual=new ForeignList(course(67),site,marker);
                assertSame(marker,assertThrows(marker.getClass(),()->originalLead(old)));
                assertSame(marker,assertThrows(marker.getClass(),()->CapturedDirectionalPlaneCourse.leadDomain(actual)));
                assertEquals(old.encounters,actual.encounters);
            }
        }
    }

    @Test void publicDescribeKeepsIndependentGlobalEnvelopeContract() {
        var values=List.of(new Vec3(8,-0D,4),new Vec3(-7,+0D,11),new Vec3(3,-0D,-9));
        same(originalDescribe(values).orElseThrow(),CapturedDirectionalPlaneCourse.describe(values).orElseThrow());
        var result=CapturedDirectionalPlaneCourse.describe(values).orElseThrow();
        assertArrayEquals(bits(new Vec3(-7,-0D,-9)),bits(result.minimum()));
        assertArrayEquals(bits(new Vec3(8,-0D,11)),bits(result.maximum()));
    }

    private static final class ForeignList extends AbstractList<Vec3> {
        private final List<Vec3> values; private final String throwSite; private final Throwable marker;
        private final List<String> encounters=new ArrayList<>();
        ForeignList(List<Vec3> values,String throwSite,Throwable marker) {
            this.values=values; this.throwSite=throwSite; this.marker=marker;
        }
        private void encounter(String site) { encounters.add(site); if(site.equals(throwSite)){if(marker instanceof RuntimeException failure)throw failure;throw (Error)marker;} }
        @Override public boolean isEmpty(){encounter("isEmpty");return values.isEmpty();}
        @Override public int size(){encounter("size");return values.size();}
        @Override public Vec3 getFirst(){encounter("getFirst");return values.getFirst();}
        @Override public Vec3 get(int index){encounter("get:"+index);return values.get(index);}
        @Override public Iterator<Vec3> iterator(){
            encounter("iterator");return new Iterator<>() {
                int index;
                @Override public boolean hasNext(){encounter("hasNext:"+index);return index<values.size();}
                @Override public Vec3 next(){encounter("next:"+index);return values.get(index++);}
            };
        }
    }
}
