package dev.veltraph.client.hunting;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.hunting.ShardWorkflowTest.*;

class ShardScheduledPauseTest {
    @Test void exactTokenFreezesEngineCallbacksAndWorkTimeoutWithoutRestart(){
        var actions=new Actions();var engine=new ShardWorkflow(actions);var cfg=settings(false);var closed=obs(null,0);var ticket=new Object();
        assertTrue(engine.start(cfg,closed,100));int cleanups=actions.cleanups;
        assertTrue(engine.pauseForScheduledRest(ticket,closed,101));assertEquals(cleanups+1,actions.cleanups);
        assertFalse(engine.pauseForScheduledRest(new Object(),closed,102));
        for(int i=0;i<100;i++){engine.tick(false,cfg,closed,60_000+i);engine.message("You do not have the requirement to use this item!");}
        assertSame(ShardWorkflow.State.OPEN_ATTRIBUTES,engine.state());assertTrue(engine.active());assertEquals(0,actions.opens);assertEquals(cleanups+1,actions.cleanups);
        assertFalse(engine.rebindForScheduledRest(new Object(),closed));assertFalse(engine.resumeAfterScheduledRest(ticket,60_200));
        assertTrue(engine.rebindForScheduledRest(ticket,closed));assertFalse(engine.resumeAfterScheduledRest(new Object(),60_200));
        assertTrue(engine.resumeAfterScheduledRest(ticket,60_200));assertFalse(engine.isScheduledPaused());
        engine.tick(true,cfg,closed,60_201);assertEquals(1,actions.opens);assertTrue(engine.active());
    }
    @Test void menuCursorAndPendingNavigationCannotBecomePauseCredits(){
        var actions=new Actions();var engine=new ShardWorkflow(actions);var cfg=settings(true);var ticket=new Object();
        var storage=menu(1,1,"Shard menu",button(10,"Mass"));engine.start(cfg,obs(null,4),0);
        assertFalse(engine.pauseForScheduledRest(ticket,obs(storage,4),1));
        assertFalse(engine.pauseForScheduledRest(ticket,new ShardWorkflow.Observation(null,obs(null,4).inventory(),false,false),1));
        engine.tick(true,cfg,obs(storage,4),1);engine.tick(true,cfg,obs(storage,4),2);
        assertEquals(1,actions.clicks);assertFalse(engine.pauseForScheduledRest(ticket,obs(null,4),3));
        var confirm=menu(2,1,"Confirm syphon",button(11,"Confirm"));engine.tick(true,cfg,obs(confirm,4),4);engine.tick(true,cfg,obs(confirm,4),5);
        assertSame(ShardWorkflow.State.WAIT_SYPHON_ACK,engine.state());assertFalse(engine.pauseForScheduledRest(ticket,obs(null,4),6));
        assertEquals(2,actions.clicks);
    }
    @Test void ownedResumeRetainsPlannedJobsAndRejectsChangedInventoryOrStoppedTicket(){
        var actions=new Actions();var engine=new ShardWorkflow(actions);var cfg=settings(false);var ticket=new Object();
        var attributes=menu(1,1,"Attributes",button(1,"Advanced active"),button(10,"Cod I","Progress: 2"));
        engine.start(cfg,obs(attributes,0),0);for(int i=1;i<=4;i++)engine.tick(true,cfg,obs(attributes,0),i);
        engine.tick(true,cfg,obs(null,0),5);assertSame(ShardWorkflow.State.OPEN_BAZAAR_MENU,engine.state());
        var jobs=engine.jobs();assertEquals(2,jobs.getFirst().quantity());
        assertTrue(engine.pauseForScheduledRest(ticket,obs(null,0),6));
        assertFalse(engine.rebindForScheduledRest(ticket,obs(null,2)));assertTrue(engine.isScheduledPaused());
        assertTrue(engine.rebindForScheduledRest(ticket,obs(null,0)));assertEquals(jobs,engine.jobs());assertEquals(0,engine.sessionCost());
        assertTrue(engine.resumeAfterScheduledRest(ticket,60_006));assertEquals(jobs,engine.jobs());
        assertTrue(engine.pauseForScheduledRest(ticket,obs(null,0),60_007));engine.cancel();
        assertFalse(engine.scheduledOwnedBy(ticket));assertFalse(engine.rebindForScheduledRest(ticket,obs(null,0)));assertFalse(engine.resumeAfterScheduledRest(ticket,70_000));
        assertSame(ShardWorkflow.State.CANCELLED,engine.state());
    }
    @Test void backwardsClockStopsExactPausedEngine(){
        var actions=new Actions();var engine=new ShardWorkflow(actions);var cfg=settings(false);var ticket=new Object();
        engine.start(cfg,obs(null,0),100);assertTrue(engine.pauseForScheduledRest(ticket,obs(null,0),101));
        assertTrue(engine.rebindForScheduledRest(ticket,obs(null,0)));assertFalse(engine.resumeAfterScheduledRest(ticket,100));
        assertSame(ShardWorkflow.State.FAILED,engine.state());assertFalse(engine.scheduledOwnedBy(ticket));
    }
}
