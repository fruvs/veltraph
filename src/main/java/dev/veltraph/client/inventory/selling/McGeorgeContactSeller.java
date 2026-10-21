package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.pests.philip.SourceContactCommand;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Deferred committed-lease source contact command, then the accepted exact pet transfer seller. */
final class McGeorgeContactSeller {
    private final Minecraft client;private final ConfigService config;private final GuardedSellingModule seller;private final SellingMenuAccess access;
    private final SourceContactCommand contact;private SourceContactCommand.Settings settings;private BooleanSupplier committed=()->false;
    private GeorgeBatchRuntime batch;private McGeorgeBatchContactPort batchPort;private String batchOutcomeId="";private SellingAcknowledgment.Denomination batchDenomination;
    private BigDecimal purse;private SourceContactCommand.Observation initial;private Map<Integer,String> initialLedger=Map.of();private int tick;private boolean selling,failed;private String reason="Idle";
    McGeorgeContactSeller(Minecraft client,FarmServices services,ConfigService config){
        this.client=client;this.config=config;seller=new GuardedSellingModule(client,services,config);access=new SellingMenuAccess(client);
        contact=new SourceContactCommand(new SourceContactCommand.Port(){
            public SourceContactCommand.Observation read(){return observation();}
            public boolean send(SourceContactCommand.Observation expected,String command){
                if(!client.isSameThread()||!committed.getAsBoolean()||!expected.equals(observation())||!command.equals(settings.command()))return false;
                client.getConnection().sendCommand(command);return true;
            }
        });
    }
    private SourceContactCommand.Observation observation(){
        if(client.player==null||client.level==null||client.getConnection()==null)return new SourceContactCommand.Observation(false,null,null,null,null,"",0,List.of(),false,List.of(),-1);
        var value=access.read(config.get(SellingConfig.SELLING_CURRENCY_PREFIX));var inventory=new ArrayList<AbiphoneCall.Item>();
        for(int i=0;i<36;i++){var item=value.inventoryStack(i).orElseThrow();inventory.add(new AbiphoneCall.Item(item.registryId(),item.name(),item.count(),item.components()));}
        var rows=new ArrayList<AbiphoneCall.Row>();for(var slot:value.slots().values())rows.add(new AbiphoneCall.Row(slot.slot(),slot.inventoryIndex()>=0,
                slot.registryId().equals("minecraft:barrier")||slot.registryId().equals("minecraft:bedrock"),new AbiphoneCall.Item(slot.registryId(),slot.name(),slot.count(),slot.components())));
        rows.sort(Comparator.comparingInt(AbiphoneCall.Row::slot));var menu=value.menu();return new SourceContactCommand.Observation(client.player.isAlive(),client.player,client.level,client.getConnection(),menu==null?null:client.player.containerMenu,
                menu==null?"":menu.title(),menu==null?0:menu.revision(),rows,value.cursorEmpty(),inventory,client.player.getInventory().getSelectedSlot());
    }
    Optional<String> ready(){
        if(!config.get(SellingConfig.SELLING_GEORGE_CONTACT_ENABLED))return Optional.of("Explicit source George contact command is disabled");
        if(config.get(MiscConfig.AUTO_SELL_MODE)!=AutoSellMode.GEORGE_PETS||config.get(MiscConfig.AUTO_SELL_SACKS))return Optional.of("Exact George destination with no sacks required");
        try {
            var parsed=GeorgeContactProfile.parse(config.get(SellingConfig.SELLING_GEORGE_CONTACT_JSON));
            if(!parsed.title().equals(config.get(SellingConfig.SELLING_GEORGE_MENU_TITLE)))return Optional.of("Source contact title must match exact pet selling menu");
            var options=new SellingRuntime.Settings(AutoSellMode.GEORGE_PETS,config.get(SellingConfig.SELLING_GEORGE_MENU_TITLE),config.get(SellingConfig.SELLING_GEORGE_ACCEPT_LABEL),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_TITLE),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_LABEL),config.get(SellingConfig.SELLING_GEORGE_DEPOSIT_SLOT),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_PET_SLOT),config.get(SellingConfig.SELLING_GEORGE_RECEIPT_TEMPLATE),config.get(SellingConfig.SELLING_MAX_QUANTITY),BigDecimal.valueOf(config.get(SellingConfig.SELLING_MIN_PROCEEDS)),BigDecimal.valueOf(config.get(SellingConfig.SELLING_MAX_PROCEEDS)),BigDecimal.valueOf(config.get(SellingConfig.SELLING_TRASH_MAX_VALUE)),config.get(SellingConfig.SELLING_DELAY_TICKS),config.get(SellingConfig.SELLING_TIMEOUT_TICKS));
            var observed=access.read(config.get(SellingConfig.SELLING_CURRENCY_PREFIX));var snapshot=observation();
            if(!snapshot.live()||snapshot.menu()!=null||!snapshot.cursorEmpty()||observed.coins()==null||config.get(SellingConfig.SELLING_GEORGE_NAME).isBlank())return Optional.of("Closed healthy native owner/cursor/purse and exact George name required");
            var rules=SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON)).stream().filter(r->r.channel()==AutoSellMode.GEORGE_PETS&&r.matches(observed.inventoryStack(r.inventoryIndex()).orElse(null))).toList();
            if(!config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE).isBlank()){GeorgeBatchPlan.parse(config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE)).freeze(SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON)),observed,options);return Optional.empty();}
            if(rules.size()!=1||rules.getFirst().quantity()>options.maxQuantity()||rules.getFirst().total().compareTo(options.minProceeds())<0||rules.getFirst().total().compareTo(options.maxProceeds())>0)return Optional.of("One exact pet identity/value/quantity contract required");
            return Optional.empty();
        }catch(RuntimeException invalid){return Optional.of("Required source George contact/sale contract: "+invalid.getMessage());}
    }
    boolean start(BooleanSupplier committed){
        this.committed=committed;tick=0;selling=false;failed=false;var blocker=ready();if(blocker.isPresent()||!committed.getAsBoolean()){failed=true;reason=blocker.orElse("Committed exact lease required");return false;}
        settings=GeorgeContactProfile.parse(config.get(SellingConfig.SELLING_GEORGE_CONTACT_JSON));
        if(!config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE).isBlank()){
            var options=batchOptions();batchOutcomeId=UUID.randomUUID().toString();batchDenomination=config.get(SellingConfig.SELLING_CURRENCY_DENOMINATION);batchPort=new McGeorgeBatchContactPort(client,settings,options,config.get(SellingConfig.SELLING_CURRENCY_PREFIX),committed);batch=new GeorgeBatchRuntime(batchPort);batchPort.bind(batch);return batch.start(GeorgeBatchPlan.parse(config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE)),SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON)),options,tick);
        }batch=null;batchPort=null;
        var starting=access.read(config.get(SellingConfig.SELLING_CURRENCY_PREFIX));purse=starting.coins();initialLedger=starting.ledger();initial=observation();
        boolean started=contact.start(settings,tick);if(!started){failed=true;reason=contact.reason();}return started;
    }
    FarmAutoSellRuntime.Progress tick(){
        tick++;if(batch!=null){batch.tick(tick);reason=batch.reason();return batch.active()?FarmAutoSellRuntime.Progress.ACTIVE:batch.state()==GeorgeBatchRuntime.State.COMPLETE?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}if(failed||!committed.getAsBoolean()){cancel();failed=true;return FarmAutoSellRuntime.Progress.FAILED;}
        if(!selling){
            var current=access.read(config.get(SellingConfig.SELLING_CURRENCY_PREFIX));if(current.coins()==null||current.coins().compareTo(purse)!=0||!current.ledger().equals(initialLedger)){failed=true;reason="Protected inventory or purse changed during source contact command";contact.cancel();return FarmAutoSellRuntime.Progress.FAILED;}
            contact.tick(tick);reason=contact.reason();
            if(contact.state()==SourceContactCommand.State.FAILED||contact.state()==SourceContactCommand.State.CANCELLED){failed=true;return FarmAutoSellRuntime.Progress.FAILED;}
            if(contact.state()==SourceContactCommand.State.HANDOFF){
                if(!contact.handoffValid()){failed=true;reason="Fresh source George handoff ownership changed";return FarmAutoSellRuntime.Progress.FAILED;}
                var blocker=seller.preStartCheck();if(blocker.isPresent()){failed=true;reason=blocker.get();return FarmAutoSellRuntime.Progress.FAILED;}
                seller.setEnabled(true);if(!seller.isEnabled()){failed=true;reason="Exact George seller activation refused";return FarmAutoSellRuntime.Progress.FAILED;}selling=true;
            }return FarmAutoSellRuntime.Progress.ACTIVE;
        }
        if(seller.isEnabled())seller.onTick();reason=seller.diagnostics();
        return seller.isEnabled()?FarmAutoSellRuntime.Progress.ACTIVE:seller.sellingState()==SellingRuntime.State.COMPLETE?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;
    }
    FarmAutoSellRuntime.Progress cleanup(){if(batch!=null){batch.maintenance(++tick);return batch.cleaning()?FarmAutoSellRuntime.Progress.ACTIVE:batch.state()==GeorgeBatchRuntime.State.COMPLETE?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}seller.maintenanceTick();if(seller.isEnabled()||seller.hasPendingCleanup())return FarmAutoSellRuntime.Progress.ACTIVE;
        if(seller.sellingState()!=SellingRuntime.State.COMPLETE)return FarmAutoSellRuntime.Progress.FAILED;
        contact.release();return FarmAutoSellRuntime.Progress.COMPLETE;
    }
    void cancel(){
        if(batch!=null){batch.cancel(++tick);return;}seller.setEnabled(false);contact.cancel();
        if(!selling&&initial!=null&&settings!=null){var current=observation();var menu=access.read(config.get(SellingConfig.SELLING_CURRENCY_PREFIX));
            if(current.live()&&current.player()==initial.player()&&current.world()==initial.world()&&current.connection()==initial.connection()&&current.menu()!=null
                    &&current.menu()==contact.ownedMenu()&&current.title().equals(settings.title())&&current.cursorEmpty()&&current.inventory().equals(initial.inventory())&&current.selectedSlot()==initial.selectedSlot()
                    &&current.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(settings.marker())).count()==1&&menu.ledger().equals(initialLedger)&&menu.coins()!=null&&menu.coins().compareTo(purse)==0)
                access.closeOwned(menu.menu().id(),menu.menu().title());
        }
    }
    void maintenanceTick(){if(batch!=null){if(batch.cleaning())batch.maintenance(++tick);}else seller.maintenanceTick();}
    boolean hasPendingCleanup(){return batch!=null?batch.cleaning():seller.hasPendingCleanup();}
    boolean complete(){if(batch!=null)return batch.state()==GeorgeBatchRuntime.State.COMPLETE&&!batch.cleaning()&&!batch.escrowHeld();return selling&&!seller.isEnabled()&&!seller.hasPendingCleanup()&&seller.sellingState()==SellingRuntime.State.COMPLETE;}
    boolean allowsScreen(String title){if(batch!=null)return batch.allowsScreen(title);return settings!=null&&settings.title().equals(title)||seller.allowsScreen(title);}
    void message(String line){if(batch!=null){batchPort.message(line);return;}if(!selling)contact.message(line);else seller.onServerMessage(line);}
    SellingRuntime.State sellingState(){return batch!=null?batch.saleState():seller.sellingState();}
    Optional<SellingAcknowledgment> acknowledgment(){if(batch!=null){var outcomes=acknowledgments();return complete()&&outcomes.size()==1?Optional.of(outcomes.getFirst()):Optional.empty();}return complete()?seller.acknowledgedSale():Optional.empty();}
    List<GeorgeSaleIdentity> acknowledgedSourceIdentities(){return batch!=null?batchPort.acknowledgedSourceIdentities(batchOutcomeId):List.of();}
    List<SellingAcknowledgment> acknowledgments(){return batch!=null?batchPort.acknowledgments(batchOutcomeId,batchDenomination):acknowledgment().stream().toList();}
    private SellingRuntime.Settings batchOptions(){return new SellingRuntime.Settings(AutoSellMode.GEORGE_PETS,config.get(SellingConfig.SELLING_GEORGE_MENU_TITLE),config.get(SellingConfig.SELLING_GEORGE_ACCEPT_LABEL),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_TITLE),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_LABEL),config.get(SellingConfig.SELLING_GEORGE_DEPOSIT_SLOT),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_PET_SLOT),config.get(SellingConfig.SELLING_GEORGE_RECEIPT_TEMPLATE),config.get(SellingConfig.SELLING_MAX_QUANTITY),BigDecimal.valueOf(config.get(SellingConfig.SELLING_MIN_PROCEEDS)),BigDecimal.valueOf(config.get(SellingConfig.SELLING_MAX_PROCEEDS)),BigDecimal.valueOf(config.get(SellingConfig.SELLING_TRASH_MAX_VALUE)),config.get(SellingConfig.SELLING_DELAY_TICKS),config.get(SellingConfig.SELLING_TIMEOUT_TICKS));}
    String diagnostics(){if(batch!=null)return "George batch "+batch.state()+" acknowledged="+batch.completedCount()+": "+batch.reason();return "George source command "+contact.state()+": "+reason;}
}
