package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.visitor.VisitorCycleRuntime;
import dev.veltraph.client.service.FarmServices;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

/** Borrows existing selling children; the caller retains its original maintenance lease and return. */
public final class McVisitorPostQueueSelling {
    private enum State { IDLE, SELLING, CLEANUP, COMPLETE, SKIPPED, FAILED, CANCELLED }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final GuardedSellingModule guarded;private final McGeorgeContactSeller george;private final BazaarCustodyModule bazaar;
    private State state=State.IDLE;private AutoSellMode mode;private Object player,world,connection;
    private String frozen="";private BooleanSupplier valid=()->false;private long deadline,last;
    private List<SellingAcknowledgment> sales=List.of();private Optional<BazaarCustodyModule.Acknowledgment> bazaarSale=Optional.empty();
    public McVisitorPostQueueSelling(Minecraft client,FarmServices services,ConfigService config){
        this.client=client;this.services=services;this.config=config;
        guarded=new GuardedSellingModule(client,services,config);george=new McGeorgeContactSeller(client,services,config);bazaar=new BazaarCustodyModule(client,config);
    }
    /** Commit resets only observations; it emits no native request and cannot restart any lease/timer. */
    public void committed(){state=State.IDLE;sales=List.of();bazaarSale=Optional.empty();valid=()->false;player=world=connection=null;}
    public String settings(){
        var values=new TreeMap<String,String>();
        try{for(var holder:List.of(SellingConfig.class,GeorgeBatchConfig.class,TravelConfig.class,RotationConfig.class))for(var field:holder.getFields())if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(IllegalAccessException error){throw new IllegalStateException(error);}
        values.put("mode",String.valueOf(config.get(MiscConfig.AUTO_SELL_MODE)));values.put("enabled",String.valueOf(config.get(MiscConfig.AUTO_SELL_ENABLED)));
        values.put("sacks",String.valueOf(config.get(MiscConfig.AUTO_SELL_SACKS)));values.put("george",config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE));
        values.put("timeout",String.valueOf(config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS)));return values.toString();
    }
    private boolean closed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean scope(){return client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&frozen.equals(settings());}
    private boolean eligible(){
        if(mode==AutoSellMode.BAZAAR){
            var plan=BazaarCustodyPlan.parse(config.get(SellingConfig.SELLING_BAZAAR_PROFILE_JSON));
            return plan.inventory().stream().anyMatch(item->{var stack=SellingMenuAccess.describeInventoryStack(client.player.getInventory().getItem(item.index()),item.index());return !stack.protectedItem()&&stack.identity().equals(item.identity())&&stack.count()==item.quantity();});
        }
        return SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON)).stream().anyMatch(rule->rule.channel()==mode&&rule.matches(SellingMenuAccess.describeInventoryStack(client.player.getInventory().getItem(rule.inventoryIndex()),rule.inventoryIndex())));
    }
    public VisitorCycleRuntime.Progress begin(BooleanSupplier exactOriginalLeaseAndRestoredGear){
        if(state!=State.IDLE)return VisitorCycleRuntime.Progress.FAILED;
        valid=Objects.requireNonNull(exactOriginalLeaseAndRestoredGear);
        if(!closed()||client.level==null||client.getConnection()==null||!client.player.isAlive()||!valid.getAsBoolean())return fail();
        mode=config.get(MiscConfig.AUTO_SELL_MODE);
        try{if(!eligible()){state=State.SKIPPED;return VisitorCycleRuntime.Progress.SKIPPED;}}catch(RuntimeException invalid){return fail();}
        if(!config.get(MiscConfig.AUTO_SELL_ENABLED)||config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS)<1000)return fail();
        player=client.player;world=client.level;connection=client.getConnection();frozen=settings();last=services.clock().nowMillis();deadline=Math.addExact(last,config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS));
        var readiness=switch(mode){case BAZAAR->bazaar.preStartCheck();case GEORGE_PETS->george.ready();default->guarded.preStartCheck();};
        if(readiness.isPresent()||!valid.getAsBoolean())return fail();
        boolean started=switch(mode){case BAZAAR->{bazaar.setEnabled(true);yield bazaar.isEnabled();}case GEORGE_PETS->george.start(()->scope()&&valid.getAsBoolean());default->{guarded.setEnabled(true);yield guarded.isEnabled();}};
        if(!started)return fail();state=State.SELLING;return VisitorCycleRuntime.Progress.ACTIVE;
    }
    public VisitorCycleRuntime.Progress tick(){
        if(state==State.COMPLETE)return VisitorCycleRuntime.Progress.COMPLETE;
        if(state!=State.SELLING&&state!=State.CLEANUP)return VisitorCycleRuntime.Progress.FAILED;
        long now=services.clock().nowMillis();
        if(!scope()||now<last||now>=deadline||!valid.getAsBoolean())return fail();last=now;
        if(state==State.SELLING){
            var result=switch(mode){case BAZAAR->{if(bazaar.isEnabled())bazaar.onTick();yield bazaar.isEnabled()?FarmAutoSellRuntime.Progress.ACTIVE:bazaar.acknowledgedSale().isPresent()?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}
                case GEORGE_PETS->george.tick();default->{if(guarded.isEnabled())guarded.onTick();yield guarded.isEnabled()?FarmAutoSellRuntime.Progress.ACTIVE:guarded.sellingState()==SellingRuntime.State.COMPLETE?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}};
            remember();if(result==FarmAutoSellRuntime.Progress.FAILED)return fail();if(result==FarmAutoSellRuntime.Progress.COMPLETE)state=State.CLEANUP;
            return VisitorCycleRuntime.Progress.ACTIVE;
        }
        var result=switch(mode){case BAZAAR->!bazaar.isEnabled()&&bazaar.acknowledgedSale().isPresent()&&closed()?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;
            case GEORGE_PETS->george.cleanup();default->{guarded.maintenanceTick();yield guarded.isEnabled()||guarded.hasPendingCleanup()?FarmAutoSellRuntime.Progress.ACTIVE:guarded.sellingState()==SellingRuntime.State.COMPLETE&&closed()?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}};
        remember();if(result==FarmAutoSellRuntime.Progress.FAILED||result==FarmAutoSellRuntime.Progress.COMPLETE&&!closed())return fail();
        if(result==FarmAutoSellRuntime.Progress.COMPLETE){state=State.COMPLETE;return VisitorCycleRuntime.Progress.COMPLETE;}
        return VisitorCycleRuntime.Progress.ACTIVE;
    }
    private void remember(){if(mode==AutoSellMode.GEORGE_PETS)sales=List.copyOf(george.acknowledgments());else if(mode==AutoSellMode.BAZAAR)bazaarSale=bazaar.acknowledgedSale();else sales=guarded.acknowledgedSale().stream().toList();}
    private VisitorCycleRuntime.Progress fail(){cancel();state=State.FAILED;return VisitorCycleRuntime.Progress.FAILED;}
    public void cancel(){if(state==State.SELLING||state==State.CLEANUP)remember();guarded.setEnabled(false);george.cancel();bazaar.setEnabled(false);state=State.CANCELLED;}
    /** Menu predicate reads cached child ownership only: never recursively validates the coordinator. */
    public boolean allowsScreen(String title){if((state!=State.SELLING&&state!=State.CLEANUP)||!scope())return false;return switch(mode){case BAZAAR->bazaar.allowsScreen(title);case GEORGE_PETS->george.allowsScreen(title);default->guarded.allowsScreen(title);};}
    public void message(String line){if(state!=State.SELLING||!scope())return;long now=services.clock().nowMillis();if(now<last||now>=deadline||!valid.getAsBoolean()){fail();return;}last=now;switch(mode){case BAZAAR->bazaar.onServerMessage(line);case GEORGE_PETS->george.message(line);default->guarded.onServerMessage(line);}}
    public boolean hasCleanup(){return guarded.hasPendingCleanup()||george.hasPendingCleanup();}
    public void maintenanceTick(){guarded.maintenanceTick();george.maintenanceTick();}
    public List<SellingAcknowledgment> acknowledgedSales(){return sales;}
    public Optional<BazaarCustodyModule.Acknowledgment> acknowledgedBazaarSale(){return bazaarSale;}
}
