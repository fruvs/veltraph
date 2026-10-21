package dev.veltraph.client.farming.module;
import dev.veltraph.client.farming.movement.FarmRouteDriver;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.rewarp.FarmCycleProfileIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarmMaintenanceDropWitnessTest {
    /** Native port fixture: contract tests, not evidence of an executed native client. */
    static final class Route implements FarmRouteDriver {
        record Binding(Route owner,FarmMaintenanceState progress,Object nativeOwner,String profile) implements MaintenanceBinding {}
        Object nativeOwner=new Object(),current=nativeOwner;int cursor=3;String phase="TRAVELLING";boolean pending,blocked;
        int starts,stops,ticks,rebinds;
        public boolean startDrop(Vec3 p,int b){starts++;return true;}public State tick(){ticks++;cursor++;return State.ACTIVE;}
        public void stop(){stops++;blocked=true;}public String status(){return phase;}
        public Optional<FarmMaintenanceState> maintenanceState(){return Optional.of(FarmMaintenanceState.of(this,"cursor",cursor,"phase",phase,"blocked",blocked));}
        public Optional<MaintenanceBinding> captureMaintenanceBinding(String profile){return pending||blocked||nativeOwner!=current?Optional.empty():Optional.of(new Binding(this,maintenanceState().orElseThrow(),nativeOwner,profile));}
        public boolean maintenanceBindingUnchanged(MaintenanceBinding b,String profile){return b instanceof Binding saved&&saved.owner()==this&&saved.nativeOwner()==nativeOwner&&saved.profile().equals(profile)&&saved.progress().equals(maintenanceState().orElseThrow());}
        public boolean canRebindMaintenance(MaintenanceBinding b,String profile){return !pending&&!blocked&&maintenanceBindingUnchanged(b,profile);}
        public Optional<MaintenanceBinding> rebindMaintenance(MaintenanceBinding b,String profile){if(!canRebindMaintenance(b,profile))return Optional.empty();nativeOwner=current;rebinds++;return Optional.of(new Binding(this,maintenanceState().orElseThrow(),nativeOwner,profile));}
        public boolean maintenanceBoundToCurrentContext(MaintenanceBinding b,String profile){return canRebindMaintenance(b,profile)&&nativeOwner==current;}
    }
    record Scenario(FarmMaintenanceWitnessTest.Rig rig,WartCropsFarmModule farm,Route route,Object token,FarmMaintenanceEngineWitness witness){}
    Scenario capture()throws Exception{
        var r=new FarmMaintenanceWitnessTest.Rig();var farm=(WartCropsFarmModule)r.start("wart");var route=new Route();farm.setRouteDriver(route);
        FarmMaintenanceWitnessTest.set(FarmMaintenanceWitnessTest.field(farm,"sShapeEngine"),"dropTarget",new Vec3(.5,62,.5));
        assertTrue(farm.maintenanceEngineReadinessError().isEmpty());Object token=new Object();assertTrue(farm.suspendForMaintenance(token));
        return new Scenario(r,farm,route,token,farm.captureMaintenanceEngineWitness(token).orElseThrow());
    }
    @Test void supportedActiveDropCanCaptureExactMaintenanceWitness()throws Exception{
        var s=capture();var checkpoint=s.route.maintenanceState();s.route.current=new Object();s.rig.breaking.context=new Object();
        assertTrue(s.farm.maintenanceEngineUnchanged(s.token,s.witness));assertTrue(s.farm.rebindForMaintenance(s.token,s.witness));
        assertEquals(checkpoint,s.route.maintenanceState());assertEquals(0,s.route.starts);assertEquals(0,s.route.stops);assertEquals(0,s.route.ticks);assertEquals(1,s.route.rebinds);
        assertTrue(s.farm.resumeAfterMaintenance(s.token));
    }
    @Test void newCursorRejectsBeforeNativeMutation()throws Exception{var s=capture();s.route.cursor++;assertFalse(s.farm.rebindForMaintenance(s.token,s.witness));assertEquals(0,s.rig.breaking.rebinds);}
    @Test void foreignNavigatorAndCancelReject()throws Exception{
        var s=capture();s.farm.setRouteDriver(new Route());assertFalse(s.farm.rebindForMaintenance(s.token,s.witness));assertEquals(0,s.rig.breaking.rebinds);
        s=capture();s.route.stop();assertFalse(s.farm.rebindForMaintenance(s.token,s.witness));assertEquals(0,s.rig.breaking.rebinds);
    }
    @Test void wrongScopeAndProfileReceiptReject()throws Exception{
        var s=capture();assertFalse(s.farm.rebindForMaintenance(new Object(),s.witness));
        var binding=s.route.captureMaintenanceBinding(FarmCycleProfileIdentity.of(s.farm)).orElseThrow();
        assertFalse(new Route().canRebindMaintenance(binding,FarmCycleProfileIdentity.of(s.farm)));
        assertFalse(s.route.canRebindMaintenance(binding,"different-profile"));
    }
    @Test void pendingNewInputRejectsBeforeAndAfterRebind()throws Exception{
        var s=capture();s.route.pending=true;assertFalse(s.farm.rebindForMaintenance(s.token,s.witness));assertEquals(0,s.rig.breaking.rebinds);
        s=capture();assertTrue(s.farm.rebindForMaintenance(s.token,s.witness));s.route.pending=true;assertFalse(s.farm.resumeAfterMaintenance(s.token));
    }
    @Test void stolenNativeOwnerCannotBeOverwritten()throws Exception{var s=capture();s.route.nativeOwner=new Object();assertFalse(s.farm.rebindForMaintenance(s.token,s.witness));assertEquals(0,s.rig.breaking.rebinds);}
}
