package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;

/** Actual shared source-aware navigator; no player/world/permission writes. */
public final class McFarmRouteDriver implements FarmRouteDriver {
    private McNavigator navigator;
    private final McNavigator originalNavigator;
    private final Minecraft client;
    private final ConfigService config;
    private final dev.veltraph.client.farming.module.WartCropsFarmModule wart;
    private boolean dispatch;
    private DropAdmissionCall dropAdmissionCall;
    /** Exact private issue statement, never a token synthesized from a visible target or ambient tick. */
    public final class DropAdmissionCall {
        private final Vec3 target;private final int bound;
        private dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication issued;
        private boolean claimed;
        private DropAdmissionCall(Vec3 target,int bound){this.target=target;this.bound=bound;}
        public McFarmRouteDriver driverIdentity(){return McFarmRouteDriver.this;}
        public dev.veltraph.client.farming.module.WartCropsFarmModule wartIdentity(){return wart;}
        public Vec3 targetPosition(){return target;}public int bound(){return bound;}
        public boolean claimOriginalIssue(){if(claimed||!dispatch||dropAdmissionCall!=this)return false;claimed=true;return true;}
        public boolean bindIssuedSource(dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication exact){
            if(!claimed||issued!=null||exact==null||!dispatch||dropAdmissionCall!=this)return false;issued=exact;return true;
        }
        public boolean issuedSourceCurrent(dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication exact){return claimed&&issued==exact&&exact!=null;}
    }
    private dev.veltraph.client.service.mc.McWartDropWaterActor waterActor;
    public McFarmRouteDriver(Minecraft client,InputService input,ConfigService config){this.client=client;this.config=config;wart=null;navigator=new McNavigator(client,input,config);originalNavigator=navigator;}
    /** Exact canonical Root services plus original Wart instance; no positive permission supplier. */
    public McFarmRouteDriver(Minecraft client,dev.veltraph.client.service.FarmServices services,ConfigService config,
            dev.veltraph.client.farming.module.WartCropsFarmModule wart){
        this.client=client;this.config=config;this.wart=java.util.Objects.requireNonNull(wart);navigator=new McNavigator(client,services.input(),config);originalNavigator=navigator;
    }
    @Override public boolean startDrop(Vec3 target,int bound){
        if(wart==null)return navigator.startDrop(target,bound);
        // S-shape retains its existing path. Only the private vertical phase can issue this WATER actor.
        if(wart.mode()!=dev.veltraph.client.farming.domain.FarmMovementMode.VERTICAL){stop();navigator=originalNavigator;return navigator.startDrop(target,bound);}
        if(dispatch)return false;dispatch=true;
        try{stop();dropAdmissionCall=new DropAdmissionCall(target,bound);
            var source=dev.veltraph.client.VeltraphBootstrap.captureOriginalWartDrop(dropAdmissionCall);
            if(source==null||source.configIdentity()!=config)return false;
            waterActor=new dev.veltraph.client.service.mc.McWartDropWaterActor(client,source);
            navigator=new McNavigator(client,waterActor,config,waterActor);return navigator.startDrop(target,bound);
        }finally{dropAdmissionCall=null;dispatch=false;}
    }
    @Override public State tick(){
        if(dispatch)return State.BLOCKED;dispatch=true;
        try{return switch(navigator.tick()){case WALKING->State.ACTIVE;case ARRIVED->State.ARRIVED;default->State.BLOCKED;};}
        finally{dispatch=false;}
    }
    @Override public void stop(){navigator.stop();if(waterActor!=null){waterActor.cleanupOwned();waterActor=null;}}
    @Override public String status(){return navigator.status();}
    private record Binding(McFarmRouteDriver driver,McNavigator.MaintenanceBinding nativeBinding) implements MaintenanceBinding {}
    @Override public java.util.Optional<dev.veltraph.client.farming.module.FarmMaintenanceState> maintenanceState(){return java.util.Optional.of(
            dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"navigator",navigator.maintenanceState()));}
    @Override public java.util.Optional<MaintenanceBinding> captureMaintenanceBinding(String profile){return navigator.captureMaintenanceBinding(profile).map(b->new Binding(this,b));}
    @Override public boolean maintenanceBindingUnchanged(MaintenanceBinding b,String profile){return b instanceof Binding saved&&saved.driver()==this&&navigator.maintenanceBindingUnchanged(saved.nativeBinding(),profile);}
    @Override public boolean canRebindMaintenance(MaintenanceBinding b,String profile){return b instanceof Binding saved&&saved.driver()==this&&navigator.canRebindMaintenance(saved.nativeBinding(),profile);}
    @Override public java.util.Optional<MaintenanceBinding> rebindMaintenance(MaintenanceBinding b,String profile){return b instanceof Binding saved&&saved.driver()==this
            ?navigator.rebindMaintenance(saved.nativeBinding(),profile).map(n->new Binding(this,n)):java.util.Optional.empty();}
    @Override public boolean maintenanceBoundToCurrentContext(MaintenanceBinding b,String profile){return b instanceof Binding saved&&saved.driver()==this&&navigator.maintenanceBoundToCurrentContext(saved.nativeBinding(),profile);}
}
