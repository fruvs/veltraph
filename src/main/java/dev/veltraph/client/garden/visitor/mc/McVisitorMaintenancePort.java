package dev.veltraph.client.garden.visitor.mc;

import dev.veltraph.client.garden.visitor.VisitorCycleRuntime;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Binds a root-committed exact farm/helper token to the visitor coordinator's deferred lease port. */
public final class McVisitorMaintenancePort implements McVisitorCycleModule.LeasePort {
    private final FarmMaintenanceCoordinator maintenance;private McVisitorCycleModule helper;
    private FarmMaintenanceLease.Token token;
    private final dev.veltraph.client.inventory.selling.McVisitorPostQueueSelling selling;
    public McVisitorMaintenancePort(FarmMaintenanceCoordinator maintenance){this.maintenance=Objects.requireNonNull(maintenance);this.selling=null;}
    public McVisitorMaintenancePort(FarmMaintenanceCoordinator maintenance,net.minecraft.client.Minecraft client,dev.veltraph.client.service.FarmServices services,dev.veltraph.client.config.ConfigService config){this.maintenance=Objects.requireNonNull(maintenance);this.selling=new dev.veltraph.client.inventory.selling.McVisitorPostQueueSelling(client,services,config);}
    public void attach(McVisitorCycleModule helper){if(this.helper!=null&&this.helper!=helper)throw new IllegalStateException("Visitor maintenance port already bound");this.helper=Objects.requireNonNull(helper);}
    /** Call only with the result of shared startTransaction after guarded activation/commit. */
    public void committed(FarmMaintenanceLease.Token token){if(token==null||maintenance.tokenFor(helper)!=token)throw new IllegalArgumentException("Visitor maintenance activation was not committed for this exact helper");this.token=token;if(selling!=null)selling.committed();}
    @Override public Optional<String> readiness(){
        if(helper==null)return Optional.of("Visitor maintenance helper is not attached");
        // During the exact root reservation, the shared activation guard invokes normal readiness.
        if(maintenance.state()==FarmMaintenanceLease.State.PREPARED)return Optional.empty();
        return maintenance.startEligibility(helper);
    }
    private boolean attached(McVisitorCycleModule candidate){
        if(candidate!=helper)return false;if(token==null)token=maintenance.tokenFor(helper);
        return token!=null&&maintenance.tokenFor(helper)==token;
    }
    @Override public boolean acquire(McVisitorCycleModule helper){return valid(helper);}
    @Override public boolean valid(McVisitorCycleModule helper){return attached(helper)&&maintenance.valid(token);}
    @Override public boolean beginReturn(McVisitorCycleModule helper,BooleanSupplier proof){
        return valid(helper)&&proof.getAsBoolean()&&maintenance.beginRestore(token)&&maintenance.beginReturn(token,proof);
    }
    @Override public VisitorCycleRuntime.Progress tickReturn(McVisitorCycleModule helper){
        if(!attached(helper))return VisitorCycleRuntime.Progress.CANCELLED;
        return switch(maintenance.tickReturn(token)){case ACTIVE->VisitorCycleRuntime.Progress.ACTIVE;case READY->VisitorCycleRuntime.Progress.COMPLETE;case FAILED->VisitorCycleRuntime.Progress.FAILED;};
    }
    @Override public boolean resume(McVisitorCycleModule helper,BooleanSupplier proof){
        if(!attached(helper)||!proof.getAsBoolean()||!maintenance.resume(token,proof))return false;token=null;return true;
    }
    @Override public void cancel(McVisitorCycleModule helper){if(helper!=this.helper)return;var old=token==null?maintenance.tokenFor(helper):token;token=null;if(selling!=null)selling.cancel();if(old!=null)maintenance.cancel(old,"Visitor maintenance stopped; no farm resume");}
    @Override public boolean completing(McVisitorCycleModule helper){return helper==this.helper&&maintenance.isCompleting(helper);}
    @Override public void message(String line){if(selling!=null)selling.message(line);if(token!=null&&maintenance.tokenFor(helper)==token)maintenance.onServerMessage(line);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return token!=null&&maintenance.tokenFor(helper)==token&&maintenance.consumeExpectedTeleport(position,now);}
    @Override public VisitorCycleRuntime.Progress beginPostQueueAutoSell(McVisitorCycleModule candidate,BooleanSupplier restoredLoadout){return selling!=null&&attached(candidate)&&restoredLoadout.getAsBoolean()?selling.begin(()->valid(candidate)&&candidate.restoredLoadoutEquipmentWitness()):VisitorCycleRuntime.Progress.FAILED;}
    @Override public VisitorCycleRuntime.Progress tickPostQueueAutoSell(McVisitorCycleModule candidate){return selling!=null&&attached(candidate)?selling.tick():VisitorCycleRuntime.Progress.CANCELLED;}
    @Override public boolean allowsPostQueueAutoSellScreen(McVisitorCycleModule candidate,String title){return selling!=null&&attached(candidate)&&selling.allowsScreen(title);}
    @Override public String postQueueAutoSellSettings(){return selling==null?"Unbound post-queue seller":selling.settings();}
    @Override public boolean hasPostQueueCleanup(McVisitorCycleModule candidate){return candidate==helper&&selling!=null&&selling.hasCleanup();}
    @Override public void maintenanceTickPostQueueCleanup(McVisitorCycleModule candidate){if(candidate==helper&&selling!=null)selling.maintenanceTick();}
    @Override public List<dev.veltraph.client.inventory.selling.SellingAcknowledgment> acknowledgedPostQueueSales(McVisitorCycleModule candidate){return candidate==helper&&selling!=null?selling.acknowledgedSales():List.of();}
    @Override public Optional<dev.veltraph.client.inventory.selling.BazaarCustodyModule.Acknowledgment> acknowledgedPostQueueBazaarSale(McVisitorCycleModule candidate){return candidate==helper&&selling!=null?selling.acknowledgedBazaarSale():Optional.empty();}
    @Override public String status(){return maintenance.status();}
}
