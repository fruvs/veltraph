package dev.veltraph.client.combat.sven;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvenWarpPermitTest {
    final Object oldPlayer=new Object(),oldWorld=new Object(),connection=new Object();
    SvenWarpPermit permit(){return new SvenWarpPermit(new SvenIngressPort.Ticket(2,4),SvenIngressPort.Operation.PARENT_WARP,oldPlayer,oldWorld,connection,"verified_park_warp",1000,11000);}
    @Test void previousNativeIdentityAndStrictArrivalAreBound(){
        var p=permit();assertTrue(p.previousContext(oldPlayer,oldWorld,connection));assertFalse(p.previousContext(new Object(),oldWorld,connection));
        assertTrue(p.arrival(new Object(),new Object(),connection,true,GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,2000));
        var spawn=SvenCaveProfile.PARK_SPAWN;assertFalse(p.arrival(new Object(),new Object(),connection,true,GameArea.THE_PARK,new Vec3(spawn.x()+15,spawn.y(),spawn.z()),2000));
    }
    @Test void replacementConnectionWrongAreaDeadOrUnavailableActorsAndExpiryNeverPermitRebind(){
        var p=permit();assertFalse(p.arrival(new Object(),new Object(),new Object(),true,GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,2000));
        assertFalse(p.arrival(new Object(),new Object(),connection,true,GameArea.HUB,SvenCaveProfile.PARK_SPAWN,2000));
        assertFalse(p.arrival(new Object(),new Object(),connection,false,GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,2000));
        assertFalse(p.arrival(null,new Object(),connection,true,GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,2000));
        assertFalse(p.arrival(new Object(),new Object(),connection,true,GameArea.THE_PARK,SvenCaveProfile.PARK_SPAWN,11001));
        assertFalse(p.arrival(new Object(),new Object(),connection,true,GameArea.THE_PARK,new Vec3(Double.NaN,79,-18),2000));
    }
    @Test void anUnissuedOperationAndUnboundedLifetimeAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new SvenWarpPermit(new SvenIngressPort.Ticket(2,4),SvenIngressPort.Operation.CLICK_BUY_ONE,oldPlayer,oldWorld,connection,"verified",1000,11000));
        assertThrows(IllegalArgumentException.class,()->new SvenWarpPermit(new SvenIngressPort.Ticket(2,4),SvenIngressPort.Operation.PARENT_WARP,oldPlayer,oldWorld,connection,"verified",1000,61001));
    }
}
