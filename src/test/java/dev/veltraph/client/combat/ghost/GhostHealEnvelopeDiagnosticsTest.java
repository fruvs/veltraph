package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostHealEnvelopeDiagnosticsTest {
    private static GhostHealDirectReturn.Scope scope(){return new GhostHealDirectReturn.Scope(new Object(),new Object(),new Object(),new Object(),new GhostHealReturn.Pose(new Vec3(.5,70,.5),-90,-13,0,1401,0));}
    private static GhostHealDirectReturn.Frame frame(GhostHealDirectReturn.Scope s){return new GhostHealDirectReturn.Frame(s,92,new Vec3(2.156913715562566,70,.5),-90,79.17236f,.19970707490831807,0,true);}
    private static final class Geometry implements GhostHealDirectReturn.Geometry {
        int currentCalls,envelopeCalls,diagnosticCalls;int failQuery;
        Geometry(int failQuery){this.failQuery=failQuery;}
        public boolean current(){currentCalls++;return true;}
        public boolean clearEnvelope(Vec3 a,Vec3 b){return ++envelopeCalls!=failQuery;}
        public GhostHealDirectReturn.Failure failure(){return GhostHealDirectReturn.Failure.BLOCKED;}
        public String diagnostic(){diagnosticCalls++;throw new AssertionError("Reporting only");}
    }
    @Test void rejectedFreshUnionAfterDirectSelectionSurvivesReporterFailureWithoutRepeatingPermissions(){
        var s=scope();var d=new GhostHealDirectReturn();d.start(s);var first=frame(s);d.advance(new GhostHealDirectReturn.Frame(s,91,first.position(),first.yaw(),first.pitch(),first.velocityX(),first.velocityZ(),true),new Geometry(-1));var g=new Geometry(1);
        var result=d.advance(frame(s),g);
        assertEquals(GhostHealDirectReturn.Outcome.REJECTED,result.outcome());assertNull(result.intent());assertEquals(GhostHealDirectReturn.Mode.DIRECT,d.mode());
        assertEquals(1,g.envelopeCalls);assertEquals(3,g.currentCalls);assertEquals(1,g.diagnosticCalls);assertTrue(result.reason().contains("DIAGNOSTIC_ERROR"));
    }
    @Test void unsupportedInitialPathSurvivesReporterFailureAndDirectSuccessNeverReports(){
        var s=scope();var path=new GhostHealDirectReturn();path.start(s);var blocked=new Geometry(1);
        var result=path.advance(frame(s),blocked);
        assertEquals(GhostHealDirectReturn.Outcome.PATH,result.outcome());assertNull(result.intent());assertEquals(1,blocked.envelopeCalls);assertEquals(3,blocked.currentCalls);assertEquals(1,blocked.diagnosticCalls);
        var direct=new GhostHealDirectReturn();direct.start(s);var clear=new Geometry(-1);var success=direct.advance(frame(s),clear);
        assertEquals(GhostHealDirectReturn.Outcome.DIRECT,success.outcome());assertNotNull(success.intent());assertEquals(1,clear.envelopeCalls);assertEquals(2,clear.currentCalls);assertEquals(0,clear.diagnosticCalls);
    }
    @Test void originalGeometryExceptionRemainsSameException(){
        var s=scope();var d=new GhostHealDirectReturn();d.start(s);var original=new IllegalStateException("Native query failed");
        var g=new GhostHealDirectReturn.Geometry(){public boolean current(){return true;}public boolean clearEnvelope(Vec3 a,Vec3 b){throw original;}public String diagnostic(){throw new AssertionError("Must not run");}};
        assertSame(original,assertThrows(IllegalStateException.class,()->d.advance(frame(s),g)));
    }
}
