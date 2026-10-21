package dev.veltraph.client.inventory.bottle;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Registered R57 acquisition parent with exact local bindings and finite64-stack refill cycles. */
public final class BottleAcquisitionModule extends AbstractModule implements dev.veltraph.client.metrics.BottleFinancialOwner {
    public static final String ID="bottle_acquire_refill";
    private final dev.veltraph.client.service.ClockService clock;private final ConfigService config;private final McBottleAcquisitionPort port;private final BottleAcquisitionRuntime runtime;private BottleAcquisitionConfig.Contract authorized;private long tick;private long consumedTravel=-1;private final List<dev.veltraph.client.metrics.FinancialActionReceipt> financialReceipts=new ArrayList<>();private int recordedPurchases;
    public BottleAcquisitionModule(Minecraft client,ConfigService config,FarmServices services,GameContextService context){super(ID,"Bottle Acquisition and Refill",ModuleCategory.MISC);this.config=config;clock=services.clock();port=new McBottleAcquisitionPort(client,config,services,context,()->this);runtime=new BottleAcquisitionRuntime(port);}
    public boolean acknowledgeManualRecovery(){if(isEnabled()||port.fillWorkflow().active())return false;if(port.fillWorkflow().state()==BottleFillWorkflow.State.RECOVERY_REQUIRED&&!port.fillWorkflow().acknowledgeManualRecovery())return false;return runtime.acknowledgeManualRecovery();}
    public List<dev.veltraph.client.metrics.FinancialActionReceipt> financialReceipts(){return List.copyOf(financialReceipts);}
    public BottleAcquisitionRuntime runtime(){return runtime;}public BottleFillWorkflow fillWorkflow(){return port.fillWorkflow();}
    @Override public boolean requiresSkyBlock(){return true;}@Override public boolean hasTravelSetupPhase(){return true;}
    @Override public Optional<String> preStartCheck(){try{if(runtime.active()||runtime.state()==BottleAcquisitionRuntime.State.UNKNOWN||port.fillWorkflow().active()||port.fillWorkflow().state()==BottleFillWorkflow.State.RECOVERY_REQUIRED)return Optional.of("Bottle parent or unknown fill custody remains owned");if(!config.get(BottleAcquisitionConfig.BOTTLE_ACQUISITION_ENABLED))return Optional.of("Enable Bottle Acquisition and Refill explicitly");var c=BottleAcquisitionConfig.read(config);var o=port.read();if(!o.valid()||!o.closed()||!o.supported()||o.area()!=dev.veltraph.client.game.GameArea.ISLAND)return Optional.of("Closed supported native Island owner required");if(!McBottleFillPort.vanillaWater(c.fill().water()))return Optional.of("Explicit observed vanilla water identity required");return port.readiness(c);}catch(RuntimeException e){return Optional.of(Objects.toString(e.getMessage(),"Invalid explicit bottle binding"));}}
    @Override public void onEnable(){tick=0;var error=preStartCheck();if(error.isPresent()){setEnabled(false);return;}authorized=BottleAcquisitionConfig.read(config);financialReceipts.clear();recordedPurchases=0;port.bind(authorized);runtime.start(authorized.acquisition(),tick);if(!runtime.active())setEnabled(false);}
    private boolean unchanged(){try{return config.get(BottleAcquisitionConfig.BOTTLE_ACQUISITION_ENABLED)&&authorized.equals(BottleAcquisitionConfig.read(config));}catch(RuntimeException e){return false;}}
    @Override public void onTick(){if(!unchanged())runtime.cancel();runtime.tick(++tick);recordAcknowledged();if(!runtime.active())setEnabled(false);}
    private void recordAcknowledged(){var purchases=runtime.receipts();while(recordedPurchases<purchases.size()){int index=recordedPurchases++;port.purchaseReceipt(purchases.get(index),this,index+1L).ifPresent(financialReceipts::add);}}
    @Override public void onDisable(){runtime.cancel();port.release();}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&(port.fillWorkflow().active()||port.fillWorkflow().recoveryReservesCurrentContext());}
    @Override public void maintenanceTick(){if(!isEnabled()&&port.fillWorkflow().active())port.fillWorkflow().tick(++tick);}
    @Override public boolean allowsScreen(String title){return authorized!=null&&(title.equals(authorized.acquisition().npcTitle())||title.equals(authorized.acquisition().buyTitle())||authorized.storage().stream().anyMatch(s->title.equals(s.title())));}
    @Override public Optional<BottleAcquisitionRuntime.Travel> pendingBottleFinancialTravel(){return pendingNativeWarp();}
    public Optional<BottleAcquisitionRuntime.Travel> pendingNativeWarp(){return isEnabled()?runtime.pendingTravel():Optional.empty();}
    private boolean issuedAuthorized(Object soleOwner,BottleAcquisitionRuntime.Travel exactTravel){return soleOwner==this&&isEnabled()&&unchanged()&&exactTravel!=null&&runtime.pendingTravel().orElse(null)==exactTravel;}
    /** Genuine native observation, read-only; Root remains responsible for its sole-owner safety/loading proof. */
    public BottleAcquisitionRuntime.Admission issuedTravelAdmission(Object soleOwner,BottleAcquisitionRuntime.Travel exactTravel){
        if(!issuedAuthorized(soleOwner,exactTravel))return BottleAcquisitionRuntime.Admission.STOP;
        return runtime.inspectTravel(exactTravel,port.read(),tick+1);
    }
    /** Clock-only loading/unknown wait after Root matches the original issued P/W/C/UUID/config ticket. */
    public BottleAcquisitionRuntime.Progress tickIssuedLoading(Object soleOwner,BottleAcquisitionRuntime.Travel exactTravel){
        if(!issuedAuthorized(soleOwner,exactTravel)){runtime.cancel();setEnabled(false);return BottleAcquisitionRuntime.Progress.FAILED;}
        var result=runtime.waitIssuedTravel(exactTravel,++tick,clock.nowMillis());
        if(result==BottleAcquisitionRuntime.Progress.FAILED)setEnabled(false);return result;
    }
    /** Core calls only for its sole input owner after normal stop/timer/safety observers retain first refusal. */
    public BottleAcquisitionRuntime.Progress tickIssuedTravel(Object soleOwner){if(soleOwner!=this||!isEnabled()||!unchanged()){runtime.cancel();return BottleAcquisitionRuntime.Progress.FAILED;}var result=runtime.pollTravel(port.read(),++tick);if(result==BottleAcquisitionRuntime.Progress.FAILED)setEnabled(false);return result;}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){if(isEnabled()&&unchanged()&&port.consumeExpectedTeleport(position,now))return true;var t=pendingNativeWarp().orElse(null);if(t==null||t.sequence()==consumedTravel||now<t.issuedMillis()||now>t.expiresMillis()||position==null||position.distanceTo(t.landing().position())>.9)return false;var o=port.read();if(o.connection()!=t.connection()||o.uuid()==null||!o.uuid().equals(t.uuid())||o.teleportSerial()<=t.serial()||o.teleportPosition()==null||o.teleportPosition().distanceTo(position)>.01)return false;consumedTravel=t.sequence();return true;}
    @Override public String diagnostics(){return runtime.state()+": "+runtime.reason()+" |64-stack receipts="+runtime.receipts().size()+" |water stored="+runtime.filled()+" |quoted spend="+runtime.spent();}
}
