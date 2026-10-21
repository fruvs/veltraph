package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact original S73 follower reference, renamed only. Pure data/terrain-port differential
 * tests preserve original diagnostic text and callback traces; they do not prove native speed. */
class WalkStartingDiagnosticParityTest {
    private static final Vec3 ORIGIN=new Vec3(.5,70,.5);
    private static final List<Vec3> COURSE=List.of(new Vec3(2.5,70,.5),new Vec3(4.5,70,1.5));
    private static final class Terrain implements GroundPathfinder.Terrain {
        final List<String> trace=new ArrayList<>();boolean blocked;final Error failure;final int failAt;
        Terrain(){this(null,-1);}Terrain(Error failure,int failAt){this.failure=failure;this.failAt=failAt;}
        boolean read(String value){trace.add(value);if(trace.size()==failAt)throw failure;return !blocked;}
        public boolean clear(BlockPos p){return read("clear:"+p);}
        public boolean floor(BlockPos p){return read("floor:"+p);}
        public boolean standable(BlockPos p){return read("standable:"+p);}
        public boolean standingClear(Vec3 p){return read("standing:"+p);}
        public boolean walkClear(Vec3 a,Vec3 z){return read("walk:"+a+":"+z);}
        public boolean stepClear(Vec3 a,Vec3 z){return read("step:"+a+":"+z);}
    }
    private static void same(OriginalStartingDiagnosticWalkController a,WalkController z){
        assertEquals(a.state().name(),z.state().name());assertEquals(a.diagnostics(),z.diagnostics());
        assertEquals(a.admittedPath(),z.admittedPath());assertEquals(a.locomotionTarget(),z.locomotionTarget());
        assertEquals(a.locomotionSteeringTarget(),z.locomotionSteeringTarget());assertEquals(a.locomotionContinuationCourse(),z.locomotionContinuationCourse());
        var before=a.maintenanceState().fields();var after=z.maintenanceState().fields();
        for(String key:List.of("path","course","continuationBroadEdges","certifiedDrops","destination","courseOrigin","steeringTarget","index","completedDropIndex","terminalRadius","lastYawStep","best","progressAt"))assertEquals(before.get(key),after.get(key),key);
        assertEquals(before.get("diagnostic"),foldDiagnostic(after.get("diagnostic")));
    }
    /** Explicit private checkpoint representation migration: structural message folds to the
     * exact original eagerly evaluated String. Every schema member is validated independently. */
    private static String foldDiagnostic(Object value){
        if(value instanceof String text)return text;
        assertInstanceOf(List.class,value);var fields=(List<?>)value;assertEquals(3,fields.size());
        assertEquals("starting proved dynamic course",fields.get(0));
        if(fields.get(1)!=null){assertInstanceOf(List.class,fields.get(1));for(Object point:(List<?>)fields.get(1))assertInstanceOf(Vec3.class,point);}
        if(fields.get(2)!=null)assertInstanceOf(Vec3.class,fields.get(2));
        return "starting proved dynamic course="+fields.get(1)+" destination="+fields.get(2);
    }
    private static void output(OriginalStartingDiagnosticWalkController.Output a,WalkController.Output z){
        assertEquals(a.forward(),z.forward());assertEquals(a.jump(),z.jump());assertEquals(Float.floatToIntBits(a.yaw()),Float.floatToIntBits(z.yaw()));assertEquals(a.state().name(),z.state().name());
    }
    @Test void successfulCourseAndCertifiedCourseKeepExactTextRepeatedReadsAndNoTerrainCalls(){
        var a=new OriginalStartingDiagnosticWalkController(7);var z=new WalkController(7);var ta=new Terrain();var tz=new Terrain();
        a.startCertifiedDynamicCourse(COURSE,ORIGIN,100,.35,ta,List.of(true,false));z.startCertifiedDynamicCourse(COURSE,ORIGIN,100,.35,tz,List.of(true,false));
        assertEquals(ta.trace,tz.trace);int count=tz.trace.size();Object checkpoint=z.maintenanceState().fields().get("diagnostic");
        assertEquals(List.of("starting proved dynamic course",COURSE,COURSE.getLast()),checkpoint);
        for(int i=0;i<3;i++){same(a,z);assertEquals(checkpoint,z.maintenanceState().fields().get("diagnostic"));}assertEquals(count,tz.trace.size());
        assertEquals("WALKING | starting proved dynamic course="+COURSE+" destination="+COURSE.getLast(),z.diagnostics());
    }
    @Test void retainedCourseIsCopiedBeforeMutableInputChangesAndResetKeepsOriginalText(){
        var points=new ArrayList<>(COURSE);var a=new OriginalStartingDiagnosticWalkController(1);var z=new WalkController(1);
        a.startCourse(points,ORIGIN,0,.35);z.startCourse(points,ORIGIN,0,.35);String old=a.diagnostics();Object savedCheckpoint=z.maintenanceState().fields().get("diagnostic");
        points.clear();points.add(new Vec3(999,999,999));same(a,z);assertEquals(old,z.diagnostics());
        a.reset();z.reset();same(a,z);assertEquals("IDLE"+old.substring(old.indexOf(" |")),z.diagnostics());
        assertEquals(savedCheckpoint,z.maintenanceState().fields().get("diagnostic"));
        var ta=new Terrain();var tz=new Terrain();output(a.tick(new OriginalStartingDiagnosticWalkController.Input(ORIGIN,0,true,1),ta,500),z.tick(new WalkController.Input(ORIGIN,0,true,1),tz,500));same(a,z);assertEquals(ta.trace,tz.trace);
    }
    @Test void originalStartOverloadDestinationNullSnapshotAndLaterStartReplacementRemainExact(){
        var a=new OriginalStartingDiagnosticWalkController(1);var z=new WalkController(1);
        a.startCourse(COURSE,ORIGIN,0,.35);z.startCourse(COURSE,ORIGIN,0,.35);
        var steps=new ArrayList<>(List.of(new BlockPos(2,70,0)));var destination=new Vec3(2.9,70,.2);
        a.start(steps,destination,2,.2);z.start(steps,destination,2,.2);steps.clear();same(a,z);
        assertTrue(z.diagnostics().endsWith("destination=null"));
        Object oldCheckpoint=z.maintenanceState().fields().get("diagnostic");
        var next=List.of(new Vec3(-2.5,70,-.5));a.startCourse(next,ORIGIN,3,.2);z.startCourse(next,ORIGIN,3,.2);same(a,z);
        assertTrue(z.diagnostics().endsWith("destination="+next.getLast()));assertNotEquals(oldCheckpoint,z.maintenanceState().fields().get("diagnostic"));
    }
    @Test void ordinaryTickArrivalObstructionAndTimeoutRetainCompleteQueryTracesAndOutputs(){
        for(int mode=0;mode<3;mode++){
            var a=new OriginalStartingDiagnosticWalkController(7);var z=new WalkController(7);var ta=new Terrain();var tz=new Terrain();
            a.startCourse(COURSE,ORIGIN,0,.35,ta);z.startCourse(COURSE,ORIGIN,0,.35,tz);same(a,z);
            if(mode==1){ta.blocked=true;tz.blocked=true;}
            for(int tick=0;tick<5;tick++){
                Vec3 pos=mode==0&&tick>1?COURSE.getLast():ORIGIN;long now=mode==2?1000+tick:tick*50;
                output(a.tick(new OriginalStartingDiagnosticWalkController.Input(pos,30,true,now),ta,500),z.tick(new WalkController.Input(pos,30,true,now),tz,500));
                same(a,z);assertEquals(ta.trace,tz.trace);
            }
        }
    }
    @Test void invalidCourseAndToleranceRetainPriorDiagnosticAndOriginalExceptionMessages(){
        var a=new OriginalStartingDiagnosticWalkController(1);var z=new WalkController(1);a.startCourse(COURSE,ORIGIN,0,.35);z.startCourse(COURSE,ORIGIN,0,.35);
        for(List<Vec3> invalid:java.util.Arrays.<List<Vec3>>asList(null,List.of(),java.util.Arrays.asList((Vec3)null),List.of(new Vec3(Double.NaN,70,0)))){
            var x=assertThrows(IllegalArgumentException.class,()->a.startCourse(invalid,ORIGIN,1,.35));var y=assertThrows(IllegalArgumentException.class,()->z.startCourse(invalid,ORIGIN,1,.35));assertEquals(x.getMessage(),y.getMessage());same(a,z);
        }
        for(double radius:new double[]{0,-1,.36,Double.NaN,Double.POSITIVE_INFINITY}){
            var x=assertThrows(IllegalArgumentException.class,()->a.startCourse(COURSE,ORIGIN,1,radius));var y=assertThrows(IllegalArgumentException.class,()->z.startCourse(COURSE,ORIGIN,1,radius));assertEquals(x.getMessage(),y.getMessage());same(a,z);
        }
    }
    @Test void certifiedDropCallbacksAndThrowingStartKeepOriginalPartialStateAndDiagnostic(){
        Vec3 origin=new Vec3(.5,70.5,.5);var points=List.of(new Vec3(1.5,69.5,.5));var all=new Terrain();new OriginalStartingDiagnosticWalkController(1).startCourse(points,origin,0,.35,all);
        for(int fail=1;fail<=all.trace.size();fail++){
            Error failure=new AssertionError("terrain "+fail);var ta=new Terrain(failure,fail);var tz=new Terrain(failure,fail);var a=new OriginalStartingDiagnosticWalkController(1);var z=new WalkController(1);
            assertSame(failure,assertThrows(Error.class,()->a.startCourse(points,origin,0,.35,ta)));assertSame(failure,assertThrows(Error.class,()->z.startCourse(points,origin,0,.35,tz)));same(a,z);assertEquals(ta.trace,tz.trace);
        }
        var a=new OriginalStartingDiagnosticWalkController(1);var z=new WalkController(1);var ta=new Terrain();var tz=new Terrain();a.startCourse(points,origin,0,.35,ta);z.startCourse(points,origin,0,.35,tz);same(a,z);assertEquals(ta.trace,tz.trace);
    }
    @Test void broadProvenanceFailureAfterCourseInstallKeepsStartingSnapshotAndResourceState(){
        var a=new OriginalStartingDiagnosticWalkController(1);var z=new WalkController(1);var broad=java.util.Arrays.asList(true,(Boolean)null);var ta=new Terrain();var tz=new Terrain();
        assertThrows(NullPointerException.class,()->a.startCertifiedDynamicCourse(COURSE,ORIGIN,0,.35,ta,broad));assertThrows(NullPointerException.class,()->z.startCertifiedDynamicCourse(COURSE,ORIGIN,0,.35,tz,broad));same(a,z);assertEquals(ta.trace,tz.trace);
        var wrong=List.of(true);assertThrows(IllegalArgumentException.class,()->a.startCertifiedDynamicCourse(COURSE,ORIGIN,0,.35,ta,wrong));assertThrows(IllegalArgumentException.class,()->z.startCertifiedDynamicCourse(COURSE,ORIGIN,0,.35,tz,wrong));same(a,z);
    }
    @Test void diagnosticsAndMaintenanceDoNotSampleSeedAndPreserveKnownStructuralCheckpointContract(){
        int[] before={0},after={0};var a=new OriginalStartingDiagnosticWalkController(()->{before[0]++;return 1;});var z=new WalkController(()->{after[0]++;return 1;});
        a.startCourse(COURSE,ORIGIN,0,.35);z.startCourse(COURSE,ORIGIN,0,.35);same(a,z);
        assertEquals(0,before[0]);assertEquals(0,after[0]);assertInstanceOf(List.class,z.maintenanceState().fields().get("diagnostic"));assertEquals(a.maintenanceState().fields().get("diagnostic"),foldDiagnostic(z.maintenanceState().fields().get("diagnostic")));
        a.reset();z.reset();same(a,z);assertEquals(0,before[0]);assertEquals(0,after[0]);
    }
}
