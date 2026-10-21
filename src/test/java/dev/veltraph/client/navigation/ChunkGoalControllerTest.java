package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkGoalControllerTest {
    static final class Driver implements ChunkGoalController.Driver {
        boolean ready;int starts,stops;Vec3 candidate;ChunkGoalController.DriveState state=ChunkGoalController.DriveState.ACTIVE;
        public boolean ready(Vec3 p) { return ready; }
        public boolean start(Vec3 p) { starts++;candidate=p;return true; }
        public ChunkGoalController.DriveState tick() { return state; }
        public void stop() { stops++; }
    }
    @Test void loadedGoalAndCalculationMustBeObservedBeforeAnyMovement() {
        var c=new ChunkGoalController();var d=new Driver();var p=new Vec3(.5,70,.5);assertTrue(c.start(2,0,List.of(new Vec3(32.05,74,.5)),0));
        assertEquals(ChunkGoalController.State.WAITING_LOADED,c.tick(new ChunkGoalController.Input(p,true,true,false,100),d,5000));assertEquals(0,d.starts);
        d.ready=true;assertEquals(ChunkGoalController.State.WAITING_LOADED,c.tick(new ChunkGoalController.Input(p,true,true,true,200),d,5000));assertEquals(0,d.starts);
        assertEquals(ChunkGoalController.State.TRAVELLING,c.tick(new ChunkGoalController.Input(p,true,true,false,300),d,5000));assertEquals(1,d.starts);
    }
    @Test void distanceOnlyArrivalInWrongChunkDiscardsCandidateAndRetriesOnce() {
        var c=new ChunkGoalController();var d=new Driver();d.ready=true;
        assertTrue(c.start(2,0,List.of(new Vec3(32.05,74,.5),new Vec3(34.5,74,.5)),0));
        c.tick(new ChunkGoalController.Input(new Vec3(.5,70,.5),true,true,false,100),d,5000);
        d.state=ChunkGoalController.DriveState.ARRIVED;
        assertEquals(ChunkGoalController.State.WAITING_LOADED,c.tick(new ChunkGoalController.Input(new Vec3(31.9,74,.5),true,true,false,200),d,5000));
        d.state=ChunkGoalController.DriveState.ACTIVE;c.tick(new ChunkGoalController.Input(new Vec3(31.9,74,.5),true,true,false,300),d,5000);
        assertEquals(2,d.starts);assertEquals(new Vec3(34.5,74,.5),d.candidate);
        d.state=ChunkGoalController.DriveState.ARRIVED;
        assertEquals(ChunkGoalController.State.ARRIVED,c.tick(new ChunkGoalController.Input(new Vec3(34.25,74,.5),true,true,false,400),d,5000));
    }
    @Test void cancellationUnloadedTimeoutAndGoalReplacementCannotCompleteOldGoal() {
        var c=new ChunkGoalController();var d=new Driver();assertTrue(c.start(-1,0,List.of(new Vec3(-.1,74,.5)),0));
        assertEquals(-1,ChunkGoalController.chunk(-.1));
        assertEquals(ChunkGoalController.State.FAILED,c.tick(new ChunkGoalController.Input(new Vec3(.5,70,.5),true,true,false,6000),d,5000));
        assertFalse(c.start(0,0,List.of(new Vec3(16.1,74,.5)),7000));
        assertTrue(c.start(1,0,List.of(new Vec3(24.5,74,.5)),7000));
        assertEquals(ChunkGoalController.State.FAILED,c.tick(new ChunkGoalController.Input(new Vec3(24.5,74,.5),true,false,false,7100),d,5000));
    }
}
