package dev.veltraph.client.foraging.supply;
import java.util.*;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
/** Ordinary user-started selected supply/chest utility; no replant shortage trigger. */
public final class ForagingSupplyModule extends AbstractModule {
    public static final String ID="foraging_chest_fill";
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final List<dev.veltraph.client.metrics.FinancialActionReceipt> financialReceipts=new ArrayList<>();private java.util.UUID activation;
    public List<dev.veltraph.client.metrics.FinancialActionReceipt> acknowledgedFinancialActions(){return List.copyOf(financialReceipts);}
    private ForagingSupplyRuntime runtime;private McForagingSupplyPort port;private String frozenBindings;private ForagingSupplyConfig.Type frozenType;
    public ForagingSupplyModule(Minecraft client,ConfigService config,FarmServices services){super(ID,"Foraging Chest Supply",ModuleCategory.FORAGING);this.client=client;this.config=config;this.services=services;}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<String> preStartCheck(){try{if(!config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_ENABLED))return Optional.of("Enable standalone Foraging chest supply explicitly");var b=ForagingSupplyBindings.parse(config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_BINDINGS));var p=new McForagingSupplyPort(client,config,services,b,config.get(ForagingSupplyConfig.FORAGING_AUTO_FILL_TYPE));return new ForagingSupplyRuntime().readiness(p.read(),b.resources().get(config.get(ForagingSupplyConfig.FORAGING_AUTO_FILL_TYPE)));}catch(RuntimeException bad){return Optional.of(bad.getMessage());}}
    @Override public void onEnable(){financialReceipts.clear();activation=java.util.UUID.randomUUID();runtime=null;port=null;try{if(preStartCheck().isPresent()){setEnabled(false);return;}frozenBindings=config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_BINDINGS);frozenType=config.get(ForagingSupplyConfig.FORAGING_AUTO_FILL_TYPE);var b=ForagingSupplyBindings.parse(frozenBindings);port=new McForagingSupplyPort(client,config,services,b,frozenType,()->this);runtime=new ForagingSupplyRuntime();if(!runtime.start(port.read(),b,frozenType)){setEnabled(false);return;}port.bindOwner();}catch(RuntimeException bad){setEnabled(false);}}
    @Override public void onTick(){if(!isEnabled()||runtime==null||port==null)return;if(!config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_ENABLED)||!frozenBindings.equals(config.get(ForagingSupplyConfig.FORAGING_CHEST_FILL_BINDINGS))||frozenType!=config.get(ForagingSupplyConfig.FORAGING_AUTO_FILL_TYPE)||!port.ownerValid()){runtime.cancel(port);setEnabled(false);return;}try{runtime.tick(port.read(),port);if(runtime.acquiredCost()>0&&financialReceipts.isEmpty()){var proof=port.acceptedAcquisition().orElse(null);if(proof==null){runtime.unattributedAcquisition(port);setEnabled(false);return;}if(proof.amount()!=runtime.acquiredCost())throw new IllegalStateException("Accepted native cost mismatch");var receipt=ForagingSupplyFinancialProof.accept(this,activation,proof.menu(),proof.menuId(),proof.requestRevision(),new dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope(proof.scope().player(),proof.scope().world(),proof.scope().connection()),proof.amount(),proof.receipts()).orElse(null);if(receipt==null){runtime.unattributedAcquisition(port);setEnabled(false);return;}financialReceipts.add(receipt);}}catch(RuntimeException bad){runtime.cancel(port);}if(runtime.terminal())setEnabled(false);}
    @Override public void onDisable(){if(runtime!=null&&!runtime.terminal()&&port!=null)runtime.cancel(port);else if(port!=null)port.release();}
    @Override public boolean allowsScreen(String title){if(!isEnabled()||runtime==null)return false;try{var b=ForagingSupplyBindings.parse(frozenBindings);return title.equals(b.chestTitle())||title.equals(b.resources().get(frozenType).offerTitle());}catch(RuntimeException bad){return false;}}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return isEnabled()&&runtime!=null&&!runtime.terminal()&&port!=null&&port.consumeExpectedTeleport(position,now);
    }
    public ForagingSupplyRuntime.Stage stage(){return runtime==null?ForagingSupplyRuntime.Stage.IDLE:runtime.stage();}
    public long acquiredCost(){return runtime==null?0:runtime.acquiredCost();}
    @Override public String diagnostics(){return stage()+": "+(runtime==null?"Idle":runtime.reason());}
}
