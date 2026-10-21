package dev.veltraph.client.combat.ghost;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.*;
class GhostSourceParentGuardDispatcherTest {
    static final class Port implements OwnedPort {
        Object parent=new Object(),child=new Object();long generation=1;boolean live=true;
        Role claimed,unknown;boolean revoke,loseCurrent;List<Role> calls=new ArrayList<>();
        public boolean current(){return live;}public Object parentIdentity(){return parent;}public Object actualSearchChildIdentity(){return child;}public long parentGeneration(){return generation;}
        public Result dispatch(Role r){calls.add(r);if(revoke)generation++;if(loseCurrent)live=false;return r==unknown?Result.UNKNOWN:r==claimed?Result.CLAIMED:Result.UNHANDLED;}
    }
    @Test void claimedOrdinaryMaintenanceStopsBeforeLaterHandlers(){
        var p=new Port();p.claimed=Role.AUTO_SELL;assertEquals(Result.CLAIMED,GhostSourceParentGuardDispatcher.beforeSearch(p.parent,p.child,1,p));
        assertEquals(List.of(Role.AUTO_SELL),p.calls);
    }
    @Test void explicitGhostConstructorSkipsOnlyTheTwoVerifiedFarmingGardenRoles(){
        var p=new Port();assertEquals(Result.UNHANDLED,GhostSourceParentGuardDispatcher.beforeSearch(p.parent,p.child,1,p));
        assertFalse(p.calls.contains(Role.FARM_GARDEN_PAUSE));assertFalse(p.calls.contains(Role.FARM_PEST_SPRAY));
        assertTrue(p.calls.contains(Role.PESTS));assertTrue(p.calls.contains(Role.SCHEDULED_REST));
    }
    @Test void liveOwnershipLossDuringDispatchCannotGrantAnUnhandledResult(){
        var p=new Port();p.loseCurrent=true;
        assertEquals(Result.UNKNOWN,GhostSourceParentGuardDispatcher.beforeSearch(p.parent,p.child,1,p));
        assertEquals(List.of(Role.AUTO_SELL),p.calls);
    }
    @Test void unknownRoleOrChangedChildGenerationCannotPublishSelection(){
        var p=new Port();p.unknown=Role.PESTS;assertEquals(Result.UNKNOWN,GhostSourceParentGuardDispatcher.beforeSearch(p.parent,p.child,1,p));
        p=new Port();p.revoke=true;assertEquals(Result.UNKNOWN,GhostSourceParentGuardDispatcher.beforeSearch(p.parent,p.child,1,p));
        assertEquals(Result.UNKNOWN,GhostSourceParentGuardDispatcher.beforeSearch(p.parent,new Object(),p.generation,p));
    }
}
