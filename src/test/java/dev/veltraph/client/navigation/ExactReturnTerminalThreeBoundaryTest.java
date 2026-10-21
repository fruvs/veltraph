package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Prescribed entry nodes survive broad approach variation; model-only geometry. */
class ExactReturnTerminalThreeBoundaryTest {
    private static final Vec3 START=new Vec3(.5,1,.5);
    private static final DynamicGroundRoutePlanner.Geometry OPEN=new DynamicGroundRoutePlanner.Geometry(){
        public boolean current(){return true;}
        public boolean standing(Vec3 point){return true;}
        public boolean sweep(Vec3 from,Vec3 to){return true;}
    };
    private static List<Vec3> straight(double endX,double spacing,double z){
        var nodes=new ArrayList<Vec3>();
        for(double x=START.x()+spacing;x<=endX;x+=spacing)nodes.add(new Vec3(x,1,z));
        return nodes;
    }
    private static DynamicGroundRoutePlanner.Plan plan(List<Vec3> nodes,long ordinal){
        return DynamicGroundRoutePlanner.planExactReturnWindows(START,nodes,77,ordinal,OPEN);
    }
    private static void prescribedTail(List<Vec3> nodes,DynamicGroundRoutePlanner.Plan plan,int count){
        assertEquals(nodes.subList(nodes.size()-count,nodes.size()),plan.course().subList(plan.course().size()-count,plan.course().size()));
        for(int i=0;i<count;i++){
            int oldIndex=nodes.size()-count+i,newIndex=plan.course().size()-count+i;
            assertSame(nodes.get(oldIndex),plan.course().get(newIndex));
            assertFalse(plan.broadEdges().get(newIndex),"Prescribed entry edge must not carry broad-route provenance");
        }
    }
    @Test void shortFinalDiagonalPreservesAllThreeOriginalEntryNodes(){
        var nodes=straight(29.5,1,1.5);nodes.add(new Vec3(30.5,1,.5));
        var result=plan(nodes,0);
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,result.outcome());
        prescribedTail(nodes,result,3);
    }
    @Test void shortFinalCornerPreservesAllThreeOriginalEntryNodes(){
        var nodes=straight(25.5,1,.5);nodes.add(new Vec3(25.5,1,1.5));
        var result=plan(nodes,0);
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,result.outcome());
        prescribedTail(nodes,result,3);
    }
    @Test void denselySampledStraightArrivalKeepsItsLongerPrescribedRun(){
        var nodes=straight(30.5,.25,.5);var result=plan(nodes,0);
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,result.outcome());
        // Ten final edges cover the existing 2.5-block terminal run, beyond the three-node minimum.
        prescribedTail(nodes,result,10);
    }
    @Test void oneToThreeNodesHaveNoBroadPrefix(){
        var all=List.of(new Vec3(12.5,1,.5),new Vec3(24.5,1,.5),new Vec3(30.5,1,.5));
        for(int count=1;count<=3;count++){
            var nodes=all.subList(all.size()-count,all.size());var result=plan(nodes,0);
            assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,result.outcome());
            prescribedTail(nodes,result,count);
        }
    }
    @Test void laterOpenApproachesStillVaryBeforeTheSamePrescribedEntry(){
        var nodes=straight(29.5,1,1.5);nodes.add(new Vec3(30.5,1,.5));
        var first=plan(nodes,0);var later=plan(nodes,4);
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,first.outcome());
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,later.outcome());
        prescribedTail(nodes,first,3);prescribedTail(nodes,later,3);
        assertNotEquals(first.course(),later.course());
        assertTrue(first.broadEdges().stream().anyMatch(Boolean::booleanValue));
        assertTrue(later.broadEdges().stream().anyMatch(Boolean::booleanValue));
    }
    @Test void narrowCorridorKeepsTheOriginalGraphWithoutBroadVariation(){
        var nodes=straight(30.5,1,.5);
        var narrow=new DynamicGroundRoutePlanner.Geometry(){
            public boolean current(){return true;}
            public boolean standing(Vec3 point){return Math.abs(point.z()-.5)<=.5;}
            public boolean sweep(Vec3 from,Vec3 to){return standing(from)&&standing(to);}
        };
        for(int ordinal=0;ordinal<4;ordinal++){
            var result=DynamicGroundRoutePlanner.planExactReturnWindows(START,nodes,77,ordinal,narrow);
            assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,result.outcome());
            assertEquals(nodes,result.course());prescribedTail(nodes,result,3);
            assertTrue(result.broadEdges().stream().noneMatch(Boolean::booleanValue));
        }
    }
}
