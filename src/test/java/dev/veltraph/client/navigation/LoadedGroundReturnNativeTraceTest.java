package dev.veltraph.client.navigation;

import dev.veltraph.client.rewarp.SavedAnchorRecovery;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Measured M target97.29074 away, client target chunk unloaded; source120 shortcut is absent. */
class LoadedGroundReturnNativeTraceTest {
    static final Vec3 ORIGIN=new Vec3(-96.5,70,-96.5),TARGET=new Vec3(.79,70,-96.12);
    static final Object PLAYER=new Object(),WORLD=new Object(),CONNECTION=new Object();
    static class Driver implements LoadedGroundReturnController.Driver {
        Vec3 position=ORIGIN,walking;long now;int starts,ticks,stops;boolean unsafe;Set<Vec3> blocked=new HashSet<>();List<Vec3> requests=new ArrayList<>();
        LoadedGroundReturnController.Observation observation(){return new LoadedGroundReturnController.Observation(PLAYER,WORLD,CONNECTION,position,true,now);}
        public boolean ready(Vec3 p){return !unsafe&&Math.abs(ChunkGoalController.chunk(p.x())-ChunkGoalController.chunk(position.x()))<=5&&Math.abs(ChunkGoalController.chunk(p.z())-ChunkGoalController.chunk(position.z()))<=5&&p.y()==70;}
        public List<Vec3> loadedProgressCandidates(Vec3 from,Vec3 target){double dx=target.x()-from.x(),dz=target.z()-from.z(),length=Math.hypot(dx,dz);if(length==0)return List.of();return List.of(new Vec3(from.x()+dx/length*16,70,from.z()+dz/length*16),new Vec3(from.x()+dx/length*15,70,from.z()+dz/length*15));}
        public boolean start(Vec3 p,int nodes,int radius,double tolerance){starts++;requests.add(p);assertEquals(2500,nodes);assertEquals(32,radius);assertEquals(.35,tolerance);if(!ready(p)||p.distanceTo(position)>32||blocked.contains(p))return false;walking=p;return true;}
        public LoadedGroundReturnController.Progress tick(){ticks++;return position.distanceTo(walking)<.35?LoadedGroundReturnController.Progress.ARRIVED:LoadedGroundReturnController.Progress.ACTIVE;}
        public void stop(){stops++;walking=null;}
        void physicalArrival(){position=walking;now+=5000;}
    }
    @Test void measuredUnloaded97mSavedReturnRequiresExplicitLoadedGroundFallback(){
        var d=new Driver();var loaded=new LoadedGroundReturnController(d);assertFalse(d.ready(TARGET));assertTrue(TARGET.distanceTo(ORIGIN)<120);
        var recovery=new SavedAnchorRecovery(new SavedAnchorRecovery.Actions(){
            public boolean startWalk(){return d.start(TARGET,2500,32,.35);}
            // Extra method compiles against old M Actions; the old engine never invokes it.
            public boolean startLoadedWalk(){return loaded.start(TARGET,.35,d.observation());}
            public boolean startFlight(){fail("Survival return cannot authorize flight");return false;}
            public SavedAnchorRecovery.Travel travel(){return switch(loaded.tick(d.observation())){case MOVING->SavedAnchorRecovery.Travel.ACTIVE;case ARRIVED->SavedAnchorRecovery.Travel.ARRIVED;default->SavedAnchorRecovery.Travel.FAILED;};}
            public void align(){}public void release(){d.stop();}
        },SavedAnchorRecovery.Settings.defaults());
        assertTrue(recovery.start(true,false,0),"M validTRUE/directstart2500 radius32 supportNaN targetunloaded: old single-search return fails before moving");
        assertEquals(SavedAnchorRecovery.State.WALKING,recovery.state());assertNotEquals(TARGET,d.walking);
    }
    @Test void physicalHopsLoadFinalChunkNaturallyThenReachExactOriginalPose(){
        var d=new Driver();var c=new LoadedGroundReturnController(d);assertTrue(c.start(TARGET,.35,d.observation()));assertFalse(d.ready(TARGET));
        int arrivals=0;while(c.state()==LoadedGroundReturnController.State.MOVING&&arrivals++<12){d.physicalArrival();c.tick(d.observation());}
        assertEquals(LoadedGroundReturnController.State.ARRIVED,c.state());assertEquals(TARGET,d.position);assertTrue(d.ready(TARGET));assertTrue(c.hops()>1);assertEquals(TARGET,d.requests.getLast());assertTrue(d.now<45000);
    }
    @Test void blockedBestCandidateTriesOnlyOneBoundedAlternate(){
        var d=new Driver();var candidates=d.loadedProgressCandidates(ORIGIN,TARGET);d.blocked.add(candidates.getFirst());var c=new LoadedGroundReturnController(d);
        assertTrue(c.start(TARGET,.35,d.observation()));assertEquals(2,d.starts);assertEquals(candidates.get(1),d.walking);
        d=new Driver();d.blocked.addAll(d.loadedProgressCandidates(ORIGIN,TARGET));c=new LoadedGroundReturnController(d);assertFalse(c.start(TARGET,.35,d.observation()));assertEquals(2,d.starts);assertNull(d.walking);
    }
    @Test void dynamicGoalObstructionFailsAndReleasesBeforeAnyMoreMovement(){
        var d=new Driver();var c=new LoadedGroundReturnController(d);assertTrue(c.start(TARGET,.35,d.observation()));d.unsafe=true;
        assertEquals(LoadedGroundReturnController.State.FAILED,c.tick(d.observation()));assertEquals(0,d.ticks);assertNull(d.walking);
    }
    @Test void revokedOwnerAndOriginal45sDeadlineCannotStartAnotherHop(){
        var d=new Driver();var c=new LoadedGroundReturnController(d);assertTrue(c.start(TARGET,.35,d.observation()));int starts=d.starts;
        assertEquals(LoadedGroundReturnController.State.FAILED,c.tick(new LoadedGroundReturnController.Observation(new Object(),WORLD,CONNECTION,d.position,true,1)));assertEquals(starts,d.starts);assertEquals(0,d.ticks);
        d=new Driver();c=new LoadedGroundReturnController(d);assertTrue(c.start(TARGET,.35,d.observation()));d.now=45000;assertEquals(LoadedGroundReturnController.State.FAILED,c.tick(d.observation()));assertEquals(0,d.ticks);assertNull(d.walking);
    }
    @Test void falseArrivalOrUnloadedFinalEndpointNeverCompletes(){
        var d=new Driver(){public LoadedGroundReturnController.Progress tick(){return LoadedGroundReturnController.Progress.ARRIVED;}};var c=new LoadedGroundReturnController(d);assertTrue(c.start(TARGET,.35,d.observation()));
        assertEquals(LoadedGroundReturnController.State.FAILED,c.tick(d.observation()));assertNull(d.walking);
        var near=new Driver();near.position=new Vec3(-10,70,-96.5);c=new LoadedGroundReturnController(near);assertTrue(c.start(TARGET,.35,near.observation()));near.physicalArrival();near.unsafe=true;
        assertEquals(LoadedGroundReturnController.State.FAILED,c.tick(near.observation()));
    }
}
