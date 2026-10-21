package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes existing pure admission and policy kernels, never a healthy native capsule or ACK. Native selected/source/slot acceptance is separate. */
final class IssuedLoadedHandoffPolicyTest {
    private final Object root=new Object(),driver=new Object(),player=new Object(),world=new Object(),listener=new Object(),config=new Object(),service=new Object();
    private LegacyTravelPublicationIdentity.State state(Object active,long journey,long generation,long epoch,long serial){
        return new LegacyTravelPublicationIdentity.State(root,driver,driver,driver,player,world,listener,config,7,journey,service,generation,epoch,serial,active,true,true,true);
    }
    @Test void pureCleanHandoffCanChangeChildJourneyButNeverParentOrScene(){
        var before=state(null,2,3,4,5);var old=state(null,1,3,4,5);var candidate=new Object();
        assertNotNull(LegacyTravelPublicationIdentity.Lease.admitAfter(before,before,candidate,old,old,9,9,9));
        var foreign=new LegacyTravelPublicationIdentity.State(root,new Object(),driver,driver,player,world,listener,config,7,2,service,3,4,5,null,true,true,true);
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(foreign,foreign,candidate,old,old,9,9,9));
        var foreignWorld=new LegacyTravelPublicationIdentity.State(root,driver,driver,driver,player,new Object(),listener,config,7,2,service,3,4,5,null,true,true,true);
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(foreignWorld,foreignWorld,candidate,old,old,9,9,9));
    }
    @Test void pureAdmissionABAAndUnfinishedPredecessorRefuse(){
        var before=state(null,2,3,4,5);var old=state(null,1,3,4,5);var candidate=new Object();
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(before,before,candidate,old,old,9,9,10));
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(before,before,candidate,old,old,8,9,9));
        var liveOld=state(new Object(),1,3,4,5);
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(before,before,candidate,liveOld,liveOld,9,9,9));
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(before,before,candidate,old,state(null,1,4,4,5),9,9,9));
    }
    @Test void exactReturnProfilesKeepBothDevicesAndExcludeFlight(){
        for(var profile:ApproachMode.values()){
            var allowed=SharedMovementPolicy.allowedFor(SharedMovementPolicy.Purpose.EXACT_RETURN,profile);
            assertTrue(allowed.contains(SharedMovementPolicy.Mode.WALK));assertFalse(allowed.contains(SharedMovementPolicy.Mode.FLIGHT));
            if(profile==ApproachMode.WALK_ONLY)assertEquals(Set.of(SharedMovementPolicy.Mode.WALK),allowed);
            else assertEquals(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.AOTE,SharedMovementPolicy.Mode.AOTV),allowed);
        }
    }
    @Test void deviceLandingEnvelopeDoesNotReplaceExactSegmentEntryRadius(){
        var options=new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.AOTE,SharedMovementPolicy.Mode.AOTV),SharedMovementPolicy.Purpose.EXACT_RETURN,2,45000,.35,1.25);
        var selected=new Vec3(16,70,0);var proof=new Object();
        for(var mode:new SharedMovementPolicy.Mode[]{SharedMovementPolicy.Mode.AOTE,SharedMovementPolicy.Mode.AOTV}){
            var device=new SharedMovementPolicy.Candidate(mode.name(),mode,selected,1,1,proof,proof,proof);
            assertEquals(.30,SharedMovementPolicy.legTerminalRadius(device,options));
            assertEquals(.35,options.terminalRadius());assertEquals(2,options.toolUses());assertEquals(45000,options.deadlineMillis());
        }
        assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(options.allowed(),options.purpose(),2,45000,.350001,1.25));
    }
}
