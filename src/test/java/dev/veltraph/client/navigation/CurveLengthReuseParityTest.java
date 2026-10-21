package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen T arithmetic and complete public geometry traces, without clocks or native queries. */
final class CurveLengthReuseParityTest {
    private static final Vec3 START=new Vec3(.5,1,.5);
    @SuppressWarnings("unchecked")
    private static List<Vec3> curve(Class<?> type,Vec3 from,Vec3 to,double offset,double skew)throws Exception {
        Method method=type.getDeclaredMethod("curve",Vec3.class,Vec3.class,double.class,double.class);
        method.setAccessible(true);return (List<Vec3>)method.invoke(null,from,to,offset,skew);
    }
    private static void bits(Vec3 expected,Vec3 actual){
        assertEquals(Double.doubleToRawLongBits(expected.x()),Double.doubleToRawLongBits(actual.x()));
        assertEquals(Double.doubleToRawLongBits(expected.y()),Double.doubleToRawLongBits(actual.y()));
        assertEquals(Double.doubleToRawLongBits(expected.z()),Double.doubleToRawLongBits(actual.z()));
    }
    private static void bits(List<Vec3> expected,List<Vec3> actual){
        assertEquals(expected.size(),actual.size());for(int i=0;i<expected.size();i++)bits(expected.get(i),actual.get(i));
    }
    private static void parity(FrozenTCurveLengthPlanner.Plan expected,DynamicGroundRoutePlanner.Plan actual){
        assertEquals(expected.outcome().name(),actual.outcome().name());assertEquals(expected.reason(),actual.reason());
        assertEquals(expected.broadEdges(),actual.broadEdges());bits(expected.course(),actual.course());
    }
    @Test void everyCurveCoordinateMatchesFrozenRawBitsAtBoundariesAndBothSigns()throws Exception {
        for(double length:new double[]{10,Math.nextUp(10D),10.2,Math.nextDown(48D),48})
            for(double offset:new double[]{-4.75,-3.3,-1.25,-0.0,0.0,1.25,3.3,4.75})
                for(double skew:new double[]{-.3,-0.0,0.0,.3}){
                    Vec3 goal=new Vec3(START.x()+length,START.y(),START.z());
                    var expected=curve(FrozenTCurveLengthPlanner.class,START,goal,offset,skew);
                    var actual=curve(DynamicGroundRoutePlanner.class,START,goal,offset,skew);
                    bits(expected,actual);assertSame(goal,actual.getLast());
                }
    }
    @Test void diagonalAndTranslatedCurvesMatchAllCoordinateBits()throws Exception {
        for(Vec3 start:List.of(START,new Vec3(-100.25,-0.0,103.75),new Vec3(-0.0,0.0,-0.0)))
            for(int direction:new int[]{-1,1})for(double axis:new double[]{8,16,24}){
                Vec3 goal=new Vec3(start.x()+direction*axis,start.y(),start.z()-axis);
                bits(curve(FrozenTCurveLengthPlanner.class,start,goal,-3.45,.27),
                     curve(DynamicGroundRoutePlanner.class,start,goal,-3.45,.27));
            }
    }
    @Test void knownLengthHelperPreservesZeroUnderflowOverflowAndNonfiniteArithmetic()throws Exception {
        Method old=FrozenTCurveLengthPlanner.class.getDeclaredMethod("side",Vec3.class,Vec3.class,double.class,double.class);
        Method next=DynamicGroundRoutePlanner.class.getDeclaredMethod("sideKnownLength",Vec3.class,Vec3.class,double.class,double.class,double.class);
        old.setAccessible(true);next.setAccessible(true);
        for(Vec3 goal:List.of(new Vec3(-0.0,0.0,0.0),new Vec3(Double.MIN_VALUE,0,0),
                new Vec3(Double.MAX_VALUE,0,Double.MAX_VALUE),new Vec3(Double.NaN,0,1),new Vec3(Double.POSITIVE_INFINITY,0,1)))
            for(double offset:new double[]{-0.0,0.0,-3.3,3.3}){
                Vec3 start=new Vec3(0,0,0);
                bits((Vec3)old.invoke(null,start,goal,.3,offset),
                     (Vec3)next.invoke(null,start,goal,.3,offset,start.horizontalDistanceTo(goal)));
            }
    }
    private static final class Trace implements DynamicGroundRoutePlanner.Geometry,FrozenTCurveLengthPlanner.Geometry {
        final List<String> calls=new ArrayList<>();final String refuseKind;final int refuseAt;final Throwable failure;
        int current,standing,sweep;
        Trace(){this("",-1,null);}Trace(String kind,int at,Throwable failure){refuseKind=kind;refuseAt=at;this.failure=failure;}
        private boolean record(String kind,int ordinal,String operands){
            calls.add(kind+":"+ordinal+":"+operands);
            if(kind.equals(refuseKind)&&ordinal==refuseAt){
                if(failure instanceof RuntimeException runtime)throw runtime;
                if(failure instanceof Error error)throw error;
                return false;
            }return true;
        }
        private static String point(Vec3 p){return Long.toHexString(Double.doubleToRawLongBits(p.x()))+","+
            Long.toHexString(Double.doubleToRawLongBits(p.y()))+","+Long.toHexString(Double.doubleToRawLongBits(p.z()));}
        public boolean current(){return record("current",++current,"");}
        public boolean standing(Vec3 p){return record("standing",++standing,point(p));}
        public boolean sweep(Vec3 from,Vec3 to){return record("sweep",++sweep,point(from)+">"+point(to));}
    }
    @Test void publicSeededPlansPreserveOutcomeProvenanceAndFullGeometryTrace(){
        for(long seed:new long[]{0,77,Long.MIN_VALUE,Long.MAX_VALUE})for(long ordinal:new long[]{0,1,2,3,4,Long.MAX_VALUE}){
            Trace old=new Trace(),next=new Trace();Vec3 goal=new Vec3(28.5,1,12.5);
            parity(FrozenTCurveLengthPlanner.plan(START,List.of(goal),seed,ordinal,old),
                   DynamicGroundRoutePlanner.plan(START,List.of(goal),seed,ordinal,next));assertEquals(old.calls,next.calls);
        }
    }
    @Test void originalRefusalsPreserveCallbackPrefixesAndFallbackCandidates(){
        for(String kind:List.of("current","standing","sweep"))for(int at:new int[]{1,2,3,10,80,200}){
            Trace old=new Trace(kind,at,null),next=new Trace(kind,at,null);Vec3 goal=new Vec3(36.5,1,.5);
            parity(FrozenTCurveLengthPlanner.plan(START,List.of(goal),77,2,old),
                   DynamicGroundRoutePlanner.plan(START,List.of(goal),77,2,next));assertEquals(old.calls,next.calls);
        }
    }
    @Test void callbackRuntimeAndErrorIdentityAndPrefixRemainOriginal(){
        for(Throwable failure:List.of(new IllegalStateException("original"),new AssertionError("original")))
            for(String kind:List.of("current","standing","sweep"))for(int at:new int[]{1,2,10}){
                Trace old=new Trace(kind,at,failure),next=new Trace(kind,at,failure);Vec3 goal=new Vec3(36.5,1,.5);
                assertSame(failure,assertThrows(failure.getClass(),()->FrozenTCurveLengthPlanner.plan(START,List.of(goal),77,2,old)));
                assertSame(failure,assertThrows(failure.getClass(),()->DynamicGroundRoutePlanner.plan(START,List.of(goal),77,2,next)));
                assertEquals(old.calls,next.calls);
            }
    }
    @Test void windowsAndInvalidOrOutOfRangeInputsPreserveOriginalGatesAndTrace(){
        for(List<Vec3> nodes:List.of(List.of(new Vec3(20.5,1,.5),new Vec3(21.5,2,.5),new Vec3(42.5,2,.5)),
                List.of(new Vec3(.5,1,.5)),List.of(new Vec3(10.499999,1,.5)),List.of(new Vec3(48.500001,1,.5)),
                List.of(new Vec3(Double.MAX_VALUE,1,Double.MAX_VALUE)),List.of(new Vec3(Double.NaN,1,1)))){
            Trace old=new Trace(),next=new Trace();
            parity(FrozenTCurveLengthPlanner.planWindows(START,nodes,77,2,old),
                   DynamicGroundRoutePlanner.planWindows(START,nodes,77,2,next));assertEquals(old.calls,next.calls);
        }
    }
}
