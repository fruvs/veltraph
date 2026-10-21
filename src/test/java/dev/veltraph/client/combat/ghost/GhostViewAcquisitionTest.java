package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostViewAcquisitionTest {
    private final Object parent=new Object(),player=new Object(),world=new Object(),connection=new Object(),job=new Object(),child=new Object(),body=new Object();
    private GhostSourcePathJobTransaction.Scope scope(long generation,long revision,Object world){return new GhostSourcePathJobTransaction.Scope(job,child,new GhostSourceOwnedParentArea.Stamp(parent,generation,revision,player,world,connection));}
    private final GhostSourcePathJobTransaction.Target target=new GhostSourcePathJobTransaction.Target(body,UUID.randomUUID());
    private GhostFarTargetView.Frame view(long tick,float yaw){return new GhostFarTargetView.Frame(tick,new Point(0,0,0),new Point(0,0,20),yaw,0);}
    @Test void heldMappedResultHasNoCommitUntilActualNaturalTurnAndConsumesOnce(){
        var gate=new GhostViewAcquisition<Object>();var s=scope(1,2,world);Object mapped=new Object();gate.hold(s,target,mapped);
        var camera=new GhostFarTargetView();float yaw=170;var commits=new AtomicInteger();
        for(int tick=0;tick<120;tick++){
            var f=view(tick,yaw);
            if(gate.observe(s,target,true,f)){assertSame(mapped,gate.resultForCommit());commits.incrementAndGet();gate.clear();break;}
            assertFalse(gate.ready());assertThrows(IllegalStateException.class,gate::resultForCommit);
            var plan=camera.advance(f);assertTrue(Math.abs(plan.yaw()-yaw)<=6.001);yaw=plan.yaw();
        }
        assertEquals(1,commits.get());assertFalse(gate.pending());assertThrows(IllegalStateException.class,gate::resultForCommit);
    }
    @Test void everyAuthorityOrGeometryChangeClearsPendingBeforeAnyCommit(){
        var s=scope(1,2,world);
        for(int kind=0;kind<7;kind++){
            var gate=new GhostViewAcquisition<Object>();gate.hold(s,target,new Object());
            var current=kind==0?scope(2,2,world):kind==1?scope(1,3,world):kind==2?scope(1,2,new Object()):kind==3?null:s;
            var currentBody=kind==4?new GhostSourcePathJobTransaction.Target(new Object(),target.nativeUuid()):kind==5?new GhostSourcePathJobTransaction.Target(body,UUID.randomUUID()):target;
            assertFalse(gate.observe(current,currentBody,kind!=6,view(3,0)));assertFalse(gate.pending());
            assertThrows(IllegalStateException.class,gate::resultForCommit);
        }
    }
    @Test void manualStopClearsReadyResultAndReplacementCannotReuseIt(){
        var gate=new GhostViewAcquisition<Object>();var s=scope(1,2,world);Object result=new Object();gate.hold(s,target,result);
        assertTrue(gate.observe(s,target,true,view(2,0)));gate.clear();
        assertThrows(IllegalStateException.class,gate::resultForCommit);Object next=new Object();gate.hold(s,target,next);
        assertFalse(gate.ready());assertTrue(gate.observe(s,target,true,view(3,0)));assertSame(next,gate.resultForCommit());
    }
    @Test void noStaleAlignmentPermissionSurvivesLaterUnalignedObservation(){
        var gate=new GhostViewAcquisition<Object>();var s=scope(1,2,world);gate.hold(s,target,new Object());
        assertTrue(gate.observe(s,target,true,view(3,0)));assertFalse(gate.observe(s,target,true,view(4,80)));
        assertTrue(gate.pending());assertThrows(IllegalStateException.class,gate::resultForCommit);
    }
}
