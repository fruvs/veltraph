package dev.veltraph.client.combat.sven;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class InstalledSvenWarpLifecycleTest {
    final Object owner=new Object(),oldPlayer=new Object(),oldWorld=new Object(),connection=new Object();
    final UUID id=UUID.randomUUID();
    final SvenWarpPermit permit=new SvenWarpPermit(new SvenIngressPort.Ticket(7,11),SvenIngressPort.Operation.PARENT_WARP,
            oldPlayer,oldWorld,connection,"verified_private_warp",1000,11000);
    InstalledSvenWarpLifecycle admitted(){
        var lifecycle=new InstalledSvenWarpLifecycle();
        assertTrue(lifecycle.capture(owner,owner,1,true,permit,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,1000));
        return lifecycle;
    }
    InstalledSvenWarpLifecycle.Observation observation(Object player,Object world,Object conn,UUID uuid,boolean alive,
            boolean attached,boolean loaded,boolean closed,boolean loading,GameArea area,Vec3 feet,boolean priority,long now){
        return new InstalledSvenWarpLifecycle.Observation(player,world,conn,uuid,alive,attached,loaded,closed,loading,area,feet,priority,now);
    }
    InstalledSvenWarpLifecycle.Observation arrived(){return observation(new Object(),new Object(),connection,id,true,true,true,true,false,GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,false,2000);}
    InstalledSvenWarpLifecycle.Decision evaluate(InstalledSvenWarpLifecycle l,InstalledSvenWarpLifecycle.Observation o){return l.evaluate(owner,owner,1,true,permit,o);}
    @Test void admissionRequiresIssuedExactPreviousRootContextAndSoleOwner(){
        var l=new InstalledSvenWarpLifecycle();
        assertFalse(l.capture(owner,new Object(),1,true,permit,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,2,true,permit,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,false,permit,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,true,null,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,true,permit,new Object(),oldWorld,connection,id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,true,permit,oldPlayer,new Object(),connection,id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,true,permit,oldPlayer,oldWorld,new Object(),id,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,true,permit,oldPlayer,oldWorld,connection,null,GameArea.THE_PARK,1000));
        assertFalse(l.capture(owner,owner,1,true,permit,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,999));
        assertFalse(l.capture(owner,owner,1,true,permit,oldPlayer,oldWorld,connection,id,GameArea.THE_PARK,11001));
        assertEquals(INACTIVE,evaluate(l,arrived()));
    }
    @Test void finiteNullUnknownAndUnloadedArrivalWaitWithoutCommit(){
        var l=admitted();
        assertEquals(WAIT,evaluate(l,observation(null,null,connection,null,false,false,false,true,false,GameArea.UNKNOWN,null,false,2000)));
        var a=arrived();
        assertEquals(WAIT,evaluate(l,observation(a.player(),a.world(),connection,id,true,true,true,true,false,GameArea.UNKNOWN,a.feet(),false,3000)));
        assertEquals(WAIT,evaluate(l,observation(a.player(),a.world(),connection,id,true,true,false,true,false,GameArea.THE_PARK,a.feet(),false,4000)));
        assertEquals(WAIT,evaluate(l,observation(a.player(),a.world(),connection,id,true,false,true,true,false,GameArea.THE_PARK,a.feet(),false,5000)));
        assertEquals(STOP,evaluate(l,observation(null,null,connection,null,false,false,false,true,false,GameArea.UNKNOWN,null,false,11001)));
    }
    @Test void exactAttachedAliveLoadedParkArrivalAndStrictBoundCommit(){
        assertEquals(COMMIT,evaluate(admitted(),arrived()));
        Vec3 spawn=SvenCaveProfile.PARK_SPAWN;
        assertEquals(COMMIT,evaluate(admitted(),observation(new Object(),new Object(),connection,id,true,true,true,true,false,
                GameArea.THE_PARK,new Vec3(spawn.x()+14.999,spawn.y(),spawn.z()),false,11000)));
        assertEquals(STOP,evaluate(admitted(),observation(new Object(),new Object(),connection,id,true,true,true,true,false,
                GameArea.THE_PARK,new Vec3(spawn.x()+15,spawn.y(),spawn.z()),false,2000)));
        assertEquals(STOP,evaluate(admitted(),observation(new Object(),new Object(),connection,id,true,true,true,true,false,
                GameArea.THE_PARK,new Vec3(Double.NaN,spawn.y(),spawn.z()),false,2000)));
    }
    @Test void loadedWrongReplacementAndForeignRealmStop(){
        assertEquals(STOP,evaluate(admitted(),observation(new Object(),new Object(),connection,id,true,true,true,true,false,
                GameArea.GARDEN,SvenCaveProfile.PARK_SPAWN,false,2000)));
        assertEquals(STOP,evaluate(admitted(),observation(new Object(),new Object(),connection,id,true,true,true,true,false,
                GameArea.THE_PARK,new Vec3(0,0,0),false,2000)));
    }
    @Test void oldVisibleContextMayWaitForCommandButCannotAdoptDifferentConnectionOrUuid(){
        assertEquals(WAIT,evaluate(admitted(),observation(oldPlayer,oldWorld,connection,id,true,true,true,true,false,
                GameArea.THE_PARK,new Vec3(0,0,0),false,2000)));
        assertEquals(STOP,evaluate(admitted(),observation(null,null,new Object(),null,false,false,false,true,false,
                GameArea.UNKNOWN,null,false,2000)));
        assertEquals(STOP,evaluate(admitted(),observation(new Object(),new Object(),connection,UUID.randomUUID(),true,true,true,true,false,
                GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,false,2000)));
    }
    @Test void revokedPermitSetupTicketOwnerManualStopDeathAndPriorityRemainStops(){
        var a=arrived();
        assertEquals(STOP,admitted().evaluate(owner,owner,1,true,null,a)); // pendingNativeWarp revoked by config/ticket mutation
        var equivalent=new SvenWarpPermit(permit.request(),permit.operation(),oldPlayer,oldWorld,connection,permit.command(),1000,11000);
        assertEquals(STOP,admitted().evaluate(owner,owner,1,true,equivalent,a));
        assertEquals(STOP,admitted().evaluate(new Object(),owner,1,true,permit,a));
        assertEquals(STOP,admitted().evaluate(owner,new Object(),1,true,permit,a));
        assertEquals(STOP,admitted().evaluate(owner,owner,2,true,permit,a));
        assertEquals(STOP,admitted().evaluate(owner,owner,1,false,permit,a));
        assertEquals(STOP,evaluate(admitted(),observation(a.player(),a.world(),connection,id,false,true,true,true,false,GameArea.THE_PARK,a.feet(),false,2000)));
        assertEquals(STOP,evaluate(admitted(),observation(a.player(),a.world(),connection,id,true,true,true,true,false,GameArea.THE_PARK,a.feet(),true,2000)));
    }
    @Test void onlyExactSameConnectionLoadingTrackerPermitsLoadingAndStillCannotCommit(){
        Object tracker=new Object();
        assertTrue(InstalledSvenWarpLifecycle.loadingScreen(connection,connection,true,tracker,tracker));
        assertFalse(InstalledSvenWarpLifecycle.loadingScreen(connection,new Object(),true,tracker,tracker));
        assertFalse(InstalledSvenWarpLifecycle.loadingScreen(connection,connection,false,tracker,tracker));
        assertFalse(InstalledSvenWarpLifecycle.loadingScreen(connection,connection,true,null,null));
        assertFalse(InstalledSvenWarpLifecycle.loadingScreen(connection,connection,true,tracker,new Object()));
        var a=arrived();
        assertEquals(WAIT,evaluate(admitted(),observation(a.player(),a.world(),connection,id,true,true,true,false,true,GameArea.THE_PARK,a.feet(),false,2000)));
        assertEquals(STOP,evaluate(admitted(),observation(a.player(),a.world(),connection,id,true,true,true,false,false,GameArea.THE_PARK,a.feet(),false,2000)));
    }
    @Test void exactBoundLiveDenialCancelsWithoutOrdinaryReceiptDispatch(){
        var l=admitted();
        assertTrue(l.denial(owner,permit," §cTravel denied ","§aTravel denied",2000));
        assertFalse(l.denial(owner,permit,"Travel denied","Travel denied later",2000));
        assertFalse(l.denial(owner,permit,"Travel denied","Purchase received",2000));
        assertFalse(l.denial(owner,permit,"","Travel denied",2000));
        assertFalse(l.denial(new Object(),permit,"Travel denied","Travel denied",2000));
        assertFalse(l.denial(owner,null,"Travel denied","Travel denied",2000));
        assertFalse(l.denial(owner,permit,"Travel denied","Travel denied",11001));
        l.clear();assertFalse(l.denial(owner,permit,"Travel denied","Travel denied",2000));
        assertEquals(INACTIVE,evaluate(l,arrived()));
    }
    @Test void clearingRevokesCallbackOwnershipAndDoesNotAdoptArrival(){
        var l=admitted();assertTrue(l.owns(owner));assertFalse(l.owns(new Object()));
        l.clear();assertFalse(l.active());assertFalse(l.owns(owner));assertNull(l.permit());assertEquals(INACTIVE,evaluate(l,arrived()));
    }
}
