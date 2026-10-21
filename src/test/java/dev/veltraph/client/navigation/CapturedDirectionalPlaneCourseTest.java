package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic native-fact arithmetic only, not an actual scene or initial eligibility receipt. */
class CapturedDirectionalPlaneCourseTest {
    private static GroundGeometryCellJournal.Observation fact(double top,boolean sturdy,float friction,float speed){
        var shape=List.of(new GroundGeometryCellJournal.Box(0,0,0,1,top,1));
        return new GroundGeometryCellJournal.Observation(new Object(),shape,friction,speed,shape,sturdy);
    }
    @Test void actualFullUpPlaneAtExactHeightPassesModel(){
        assertTrue(CapturedDirectionalPlaneCourse.supported(fact(1D,true,.6F,1F),70,71D,.6F));
    }
    @Test void flatSlabHeightDoesNotSubstituteForFullNativeSturdyUp(){
        assertFalse(CapturedDirectionalPlaneCourse.supported(fact(.5D,false,.6F,1F),71,71.5D,.6F));
    }
    @Test void flatCarpetTopRetainsPartialGeometryClassification(){
        assertFalse(CapturedDirectionalPlaneCourse.supported(fact(.0625D,false,.6F,1F),71,71.0625D,.6F));
    }
    @Test void wholeSegmentRectangleIncludesPartialSupportBetweenGraphNodes(){
        var rectangle=CapturedDirectionalPlaneCourse.describe(List.of(new Vec3(.5,71,.5),new Vec3(8.5,71,.5))).orElseThrow();
        double middle=4.5;
        assertTrue(middle>=rectangle.minimum().x()&&middle<=rectangle.maximum().x());
        assertFalse(CapturedDirectionalPlaneCourse.supported(fact(.0625D,false,.6F,1F),71,71D,.6F));
    }
    @Test void entireEagerCurveAndItsSteeringChordsRemainInsideRectangle(){
        var course=List.of(new Vec3(0,71,0),new Vec3(1,71,2),new Vec3(5,71,1),new Vec3(8,71,0));
        var rectangle=CapturedDirectionalPlaneCourse.describe(course).orElseThrow();
        for(int i=0;i<course.size();i++)for(int j=i;j<course.size();j++)for(int n=0;n<=10;n++){
            var point=TravelPath.lerp(course.get(i),course.get(j),n/10D);
            assertTrue(point.x()>=rectangle.minimum().x()&&point.x()<=rectangle.maximum().x());
            assertTrue(point.z()>=rectangle.minimum().z()&&point.z()<=rectangle.maximum().z());
        }
    }
    @Test void elevationVariationAndUnknownCourseAreNotClassifiedAsFlat(){
        assertTrue(CapturedDirectionalPlaneCourse.describe(List.of(new Vec3(0,71,0),new Vec3(2,71.01,0))).isEmpty());
        assertTrue(CapturedDirectionalPlaneCourse.describe(List.of()).isEmpty());
        assertTrue(CapturedDirectionalPlaneCourse.describe(List.of(new Vec3(Double.NaN,71,0))).isEmpty());
    }
    @Test void wrongFrictionSpeedOrShapeTopRefusesPlane(){
        assertFalse(CapturedDirectionalPlaneCourse.supported(fact(1,true,.98F,1F),70,71,.6F));
        assertFalse(CapturedDirectionalPlaneCourse.supported(fact(1,true,.6F,.4F),70,71,.6F));
        assertFalse(CapturedDirectionalPlaneCourse.supported(fact(.5,true,.6F,1F),70,71,.6F));
    }
    @Test void localLeadDomainDoesNotBecomeAnArbitraryDistantChordRectangle(){
        var points=new java.util.ArrayList<Vec3>();
        for(int i=0;i<=10;i++)points.add(new Vec3(i,70,0));
        for(int i=1;i<=10;i++)points.add(new Vec3(10,70,i));
        var domain=CapturedDirectionalPlaneCourse.leadDomain(points).orElseThrow();
        assertFalse(domain.rectangles().stream().anyMatch(r->5D>=r.minimum().x()&&5D<=r.maximum().x()
                &&5D>=r.minimum().z()&&5D<=r.maximum().z()));
    }
    @Test void realPureOpenSteeringPriorWindowLeadAndWholeChordFitLocalDomain(){
        var points=new java.util.ArrayList<Vec3>();
        for(int i=0;i<=48;i++){
            double t=i/48D;points.add(new Vec3(.5+20*t-1.5*Math.sin(Math.PI*t)*Math.sin(Math.PI*t),70,
                    .5+20*t+1.5*Math.sin(Math.PI*t)*Math.sin(Math.PI*t)));
        }
        var course=points.subList(1,points.size());var domain=CapturedDirectionalPlaneCourse.leadDomain(points).orElseThrow();
        for(int index=0;index<course.size();index++)for(double side:new double[]{-.65D,0D,.65D}){
            int edge=Math.max(0,index-2);var from=edge==0?points.getFirst():course.get(edge-1);var to=course.get(edge);
            var middle=TravelPath.lerp(from,to,.5D);double length=from.horizontalDistanceTo(to);
            var actual=new Vec3(middle.x()-(to.z()-from.z())/length*side,70,middle.z()+(to.x()-from.x())/length*side);
            var target=OpenTravelSteering.target(points.getFirst(),course,index,actual,1.25D,(a,b)->true);
            var rectangle=domain.rectangles().get(index);
            for(int n=0;n<=20;n++){
                var chord=TravelPath.lerp(actual,target,n/20D);
                assertTrue(chord.x()>=rectangle.minimum().x()&&chord.x()<=rectangle.maximum().x());
                assertTrue(chord.z()>=rectangle.minimum().z()&&chord.z()<=rectangle.maximum().z());
            }
        }
    }
    @Test void projectionRegionIsEnforcedRatherThanBorrowedForRemotePose(){
        var course=List.of(new Vec3(0,70,0),new Vec3(10,70,0));
        assertTrue(CapturedDirectionalPlaneCourse.inProjectionRegion(new Vec3(5,70,.65),course));
        assertFalse(CapturedDirectionalPlaneCourse.inProjectionRegion(new Vec3(5,70,.650001),course));
        assertFalse(CapturedDirectionalPlaneCourse.inProjectionRegion(new Vec3(5,70.01,0),course));
    }
    @Test void initialAndFinalEndpointsSurviveEveryLeadDomain(){
        var points=List.of(new Vec3(0,70,0),new Vec3(1,70,0),new Vec3(2,70,1));
        var domain=CapturedDirectionalPlaneCourse.leadDomain(points).orElseThrow();
        assertTrue(domain.rectangles().getFirst().minimum().x()<=0D);
        assertTrue(domain.rectangles().getLast().maximum().x()>=2D);
        assertTrue(domain.rectangles().getLast().maximum().z()>=1D);
    }
    @Test void finiteInitialBodyDomainLimitDoesNotCreateNewBudgetAllowance(){
        assertTrue(CapturedDirectionalPlaneCourse.leadDomain(java.util.Collections.nCopies(67,new Vec3(0,70,0))).isEmpty());
        assertTrue(CapturedDirectionalPlaneCourse.leadDomain(java.util.Collections.nCopies(66,new Vec3(0,70,0))).isPresent());
    }
    @Test void openFortyFiveOriginalGoalDomainFitsInitialPlusFinalNativeCellBudget(){
        var points=new java.util.ArrayList<Vec3>();
        for(int i=0;i<=48;i++)points.add(new Vec3(.5+20*i/48D,70,.5+20*i/48D));
        var domain=CapturedDirectionalPlaneCourse.leadDomain(points).orElseThrow();
        var cells=new java.util.HashSet<GroundGeometryCellJournal.Cell>();
        for(var r:domain.rectangles()){
            double half=(double).6F/2D;
            int loX=(int)Math.floor(r.minimum().x()-half-.025D-1E-7D)-1;
            int hiX=(int)Math.floor(r.maximum().x()+half+.025D+1E-7D)+1;
            int loZ=(int)Math.floor(r.minimum().z()-half-.025D-1E-7D)-1;
            int hiZ=(int)Math.floor(r.maximum().z()+half+.025D+1E-7D)+1;
            int loY=(int)Math.floor(70-.081D-1E-7D)-1;
            int hiY=(int)Math.floor(70+(double)1.8F+1E-7D)+1;
            for(int x=loX;x<=hiX;x++)for(int y=loY;y<=hiY;y++)for(int z=loZ;z<=hiZ;z++)cells.add(new GroundGeometryCellJournal.Cell(x,y,z));
        }
        assertEquals(new Vec3(20.5,70,20.5),points.getLast());
        assertTrue(cells.size()*2<=4096,"original unshortened OPEN45 domain: "+cells.size());
        assertTrue(domain.rectangles().size()<=65);
    }
    @Test void malformedUnknownShapeCannotProduceSupportedPlaneFact(){
        var bad=List.of(new GroundGeometryCellJournal.Box(Double.NaN,0,0,1,1,1));
        assertFalse(CapturedDirectionalPlaneCourse.supported(new GroundGeometryCellJournal.Observation(new Object(),bad,.6F,1F,bad,true),70,71,.6F));
        assertFalse(CapturedDirectionalPlaneCourse.supported(null,70,71,.6F));
    }
}
