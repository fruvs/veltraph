package dev.veltraph.client.navigation;

import dev.veltraph.client.rotation.OwnedRotationTrajectory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RouteWorkflowEpochTest {
    @Test void publicResetWithSameVisibleStateRevokesOldWorkflow(){var w=new RouteWorkflowEpoch();var captured=w.snapshot();boolean enabled=true;int index=0;w.advance();assertTrue(enabled);assertEquals(0,index);assertFalse(w.current(captured));}
    @Test void foreignWorkflowCannotSupplyCurrentToken(){var a=new RouteWorkflowEpoch();var b=new RouteWorkflowEpoch();assertFalse(a.current(b.snapshot()));assertFalse(a.current(null));}
    @Test void capturedTokenIsStableUntilActualWorkflowMutation(){var w=new RouteWorkflowEpoch();var a=w.snapshot();var b=w.snapshot();assertTrue(w.current(a));assertTrue(w.current(b));w.advance();assertFalse(w.current(a));assertTrue(w.current(w.snapshot()));}
    @Test void noEffectResetAndReturnCannotReopenCapturedWorkflow(){var w=new RouteWorkflowEpoch();var old=w.snapshot();w.advance();w.advance();assertFalse(w.current(old));}
    @Test void exhaustionNeverReusesAnOldEpoch(){var w=new RouteWorkflowEpoch(Long.MAX_VALUE);var old=w.snapshot();assertTrue(w.current(old));w.advance();assertFalse(w.current(old));assertFalse(w.current(w.snapshot()));w.advance();assertFalse(w.current(w.snapshot()));}
    @Test void lateProviderResetMustVetoBeforeActualTrajectoryPlan(){
        var workflow=new RouteWorkflowEpoch();var old=workflow.snapshot();var nativeCamera=new OwnedRotationTrajectory();
        var scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
        assertTrue(workflow.current(old));long nativeEpoch=nativeCamera.epoch();
        Runnable lastProvider=workflow::advance;lastProvider.run();
        if(workflow.current(old))nativeCamera.plan(scope,10,new OwnedRotationTrajectory.Pose(0,0),new OwnedRotationTrajectory.Pose(90,0));
        assertEquals(nativeEpoch,nativeCamera.epoch());assertEquals(0,nativeCamera.publicationSerial());assertFalse(nativeCamera.active());
    }
    @Test void postPublicationCallbackCannotAdvanceOrDisableNewWorkflow(){
        var workflow=new RouteWorkflowEpoch();var old=workflow.snapshot();var nativeCamera=new OwnedRotationTrajectory();
        var scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
        long actual=nativeCamera.plan(scope,10,new OwnedRotationTrajectory.Pose(0,0),new OwnedRotationTrajectory.Pose(90,0));assertTrue(actual>0);
        workflow.advance();boolean newEnabled=true;int newIndex=0;Long accepted=null;
        if(workflow.current(old)){accepted=actual;newIndex++;newEnabled=false;}
        assertNull(accepted);assertEquals(0,newIndex);assertTrue(newEnabled);assertEquals(actual,nativeCamera.publicationSerial());
    }
}
