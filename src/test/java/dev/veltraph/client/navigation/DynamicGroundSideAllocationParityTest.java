package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Data-domain differential tests against the exact S71 original planner source, renamed only.
 * No live terrain, native context, permission, timing or cold-start proof is supplied here. */
class DynamicGroundSideAllocationParityTest {
    private static final class Reads {
        final List<String> trace=new ArrayList<>();
        final int refuseAt,throwAt; final Error failure;
        Reads(int refuseAt,int throwAt,Error failure){this.refuseAt=refuseAt;this.throwAt=throwAt;this.failure=failure;}
        boolean read(String value){trace.add(value);if(trace.size()==throwAt)throw failure;return trace.size()!=refuseAt;}
    }
    private static DynamicGroundRoutePlanner.Geometry actual(Reads r){return new DynamicGroundRoutePlanner.Geometry(){
        public boolean current(){return r.read("current");}
        public boolean standing(Vec3 p){return r.read("standing:"+p);}
        public boolean sweep(Vec3 a,Vec3 b){return r.read("sweep:"+a+":"+b);}
    };}
    private static OriginalSideAllocationDynamicGroundRoutePlanner.Geometry original(Reads r){return new OriginalSideAllocationDynamicGroundRoutePlanner.Geometry(){
        public boolean current(){return r.read("current");}
        public boolean standing(Vec3 p){return r.read("standing:"+p);}
        public boolean sweep(Vec3 a,Vec3 b){return r.read("sweep:"+a+":"+b);}
    };}
    private static void same(OriginalSideAllocationDynamicGroundRoutePlanner.Plan a,DynamicGroundRoutePlanner.Plan b){
        assertEquals(a.outcome().name(),b.outcome().name());assertEquals(a.course(),b.course());
        assertEquals(a.reason(),b.reason());assertEquals(a.broadEdges(),b.broadEdges());
    }
    private static List<Vec3> nodes(Vec3 origin,double length,int heading){
        double dx=heading==0?length:heading==1?-length:heading==2?0:length*.6;
        double dz=heading==2?length:heading==3?length*.8:0;
        return List.of(new Vec3(origin.x()+dx/2,origin.y(),origin.z()+dz/2),new Vec3(origin.x()+dx,origin.y(),origin.z()+dz));
    }
    @Test void allSeedsOrdinalsHeadingsAndLengthBranchesKeepExactCourseAndNativeQueryOrder(){
        Vec3 origin=new Vec3(-3.5,70,.5);
        for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,174319L})
            for(long ordinal:new long[]{0,1,2,3,4,Long.MAX_VALUE})for(int heading=0;heading<4;heading++)
                for(double length:new double[]{9.999,10,24,48,48.001}){
                    var nodes=nodes(origin,length,heading);var a=new Reads(-1,-1,null);var b=new Reads(-1,-1,null);
                    same(OriginalSideAllocationDynamicGroundRoutePlanner.plan(origin,nodes,seed,ordinal,original(a)),
                         DynamicGroundRoutePlanner.plan(origin,nodes,seed,ordinal,actual(b)));assertEquals(a.trace,b.trace);
                }
    }
    @Test void everyOriginalFalseCallbackPositionKeepsFallbackOrRevocationAndWholePrefix(){
        Vec3 origin=new Vec3(.5,70,.5);var nodes=nodes(origin,24,3);var all=new Reads(-1,-1,null);
        OriginalSideAllocationDynamicGroundRoutePlanner.plan(origin,nodes,71,2,original(all));
        for(int stop=1;stop<=all.trace.size();stop++){
            var a=new Reads(stop,-1,null);var b=new Reads(stop,-1,null);
            same(OriginalSideAllocationDynamicGroundRoutePlanner.plan(origin,nodes,71,2,original(a)),
                 DynamicGroundRoutePlanner.plan(origin,nodes,71,2,actual(b)));assertEquals(a.trace,b.trace);
        }
    }
    @Test void everyOriginalThrowingCallbackKeepsExactThrowableIdentityAndNoLaterQueries(){
        Vec3 origin=new Vec3(.5,70,.5);var nodes=nodes(origin,10,0);var all=new Reads(-1,-1,null);
        OriginalSideAllocationDynamicGroundRoutePlanner.plan(origin,nodes,19,1,original(all));
        for(int stop=1;stop<=all.trace.size();stop++){
            Error failure=new AssertionError("original callback "+stop);var a=new Reads(-1,stop,failure);var b=new Reads(-1,stop,failure);
            assertSame(failure,assertThrows(Error.class,()->OriginalSideAllocationDynamicGroundRoutePlanner.plan(origin,nodes,19,1,original(a))));
            assertSame(failure,assertThrows(Error.class,()->DynamicGroundRoutePlanner.plan(origin,nodes,19,1,actual(b))));
            assertEquals(a.trace,b.trace);assertEquals(stop,a.trace.size());
        }
    }
    @Test void mixedHeightWindowEndpointsAndInvalidFiniteInputsRemainExact(){
        Vec3 origin=new Vec3(.5,70,.5);
        for(var nodes:List.of(List.of(new Vec3(15.5,70,.5),new Vec3(16.5,71,.5),new Vec3(31.5,71,.5)),
                              List.of(new Vec3(Double.NaN,70,.5)),List.<Vec3>of())){
            var a=new Reads(-1,-1,null);var b=new Reads(-1,-1,null);
            same(OriginalSideAllocationDynamicGroundRoutePlanner.planWindows(origin,nodes,1,3,original(a)),
                 DynamicGroundRoutePlanner.planWindows(origin,nodes,1,3,actual(b)));assertEquals(a.trace,b.trace);
        }
    }
}
