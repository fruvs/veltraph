package dev.veltraph.client.navigation;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MaintenanceCancellationAuthorityFrameTest {
    private static final class F {
        final Object provider=new Object(),capture=new Object(),policy=new Object(),farm=new Object(),helper=new Object(),pose=new Object(),player=new Object(),world=new Object(),connection=new Object(),store=new Object(),closure=new Object();
        final FarmMaintenanceLease lease=new FarmMaintenanceLease();final FarmMaintenanceLease.Token token;
        final MaintenanceMovementAuthorityFrame.Pair pair;final MaintenanceMovementAuthorityFrame.Context context;
        F(){token=lease.prepare(farm,helper,10,5000);assertTrue(lease.commit(token));pair=new MaintenanceMovementAuthorityFrame.Pair(provider,capture,policy,farm,helper,token,pose,null);context=new MaintenanceMovementAuthorityFrame.Context(player,world,connection,store,0,0);}
        MaintenanceMovementAuthorityFrame.Observation obs(boolean healthy,boolean policyCurrent,MaintenanceMovementAuthorityFrame.Context ctx,Object camera){return new MaintenanceMovementAuthorityFrame.Observation(pair,ctx,lease.state(),SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL,new Vec3(5,70,0),new Vec3(0,70,0),helper,camera,"helper","helper",true,true,true,true,true,true,healthy,policyCurrent,true);}
        MaintenanceMovementAuthorityFrame frame(){return MaintenanceMovementAuthorityFrame.capture(obs(true,true,context,token)).orElseThrow();}
        MaintenanceMovementAuthorityFrame.RetirementObservation close(Object issued,Object current,boolean window,Object camera,long serial){return new MaintenanceMovementAuthorityFrame.RetirementObservation(pair,context,issued,current,window,true,true,true,true,camera==null,true,4,4,9,serial,3,3);}
    }
    @Test void cancellationMayIgnoreSameStoreRevisionsAndGameplayHealthButNeverGrantsCurrentMovement(){var f=new F();var frame=f.frame();var revised=new MaintenanceMovementAuthorityFrame.Context(f.player,f.world,f.connection,f.store,8,9);var obs=f.obs(false,false,revised,f.token);assertFalse(frame.current(obs));assertTrue(frame.cancellationCurrent(obs));}
    @Test void foreignConfigStoreStillRejectsCancellation(){var f=new F();var frame=f.frame();assertFalse(frame.cancellationCurrent(f.obs(false,false,new MaintenanceMovementAuthorityFrame.Context(f.player,f.world,f.connection,new Object(),0,0),f.token)));}
    @Test void absentOrForeignCameraNeverBecomesTheCapturedToken(){var f=new F();var frame=f.frame();assertFalse(frame.cancellationCurrent(f.obs(false,false,f.context,null)));assertFalse(frame.cancellationCurrent(f.obs(false,false,f.context,f.helper)));}
    @Test void sourceWindowMustBeTheActualSameOpaqueClosure(){var f=new F();var frame=f.frame();assertTrue(frame.retirementKeysCurrent(f.close(f.closure,f.closure,true,null,9)));assertFalse(frame.retirementKeysCurrent(f.close(f.closure,new Object(),true,null,9)));}
    @Test void endedClosureOrBareNullCannotAuthorizeRetiredKeys(){var f=new F();var frame=f.frame();assertFalse(frame.retirementKeysCurrent(f.close(f.closure,null,false,null,9)));assertFalse(frame.retirementKeysCurrent(f.close(null,null,true,null,9)));}
    @Test void newSameEpochPublicationSerialPreservesNewerWork(){var f=new F();var frame=f.frame();assertFalse(frame.retirementKeysCurrent(f.close(f.closure,f.closure,true,null,10)));}
    @Test void retiredKeysNeverCancelANewCameraAuthority(){var f=new F();var frame=f.frame();assertFalse(frame.retirementKeysCurrent(f.close(f.closure,f.closure,true,f.helper,9)));}
    @Test void cancelledActiveLeaseCannotProduceALiveCancellationView(){var f=new F();var frame=f.frame();f.lease.cancel(f.token,"retire");assertFalse(frame.cancellationCurrent(f.obs(false,false,f.context,f.token)));}
}
