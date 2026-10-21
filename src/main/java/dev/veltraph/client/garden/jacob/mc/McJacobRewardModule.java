package dev.veltraph.client.garden.jacob.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.jacob.*;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Registered source-observed end -> selected exact local claim -> actual same farm return. */
public final class McJacobRewardModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_jacob_reward";
    private record ItemIdentity(String item,String components){}
    private record Inventory(Map<ItemIdentity,Long> counts) {
        Inventory {counts=Map.copyOf(counts);}
        long tickets(ItemIdentity ticket){return counts.getOrDefault(ticket,0L);}
        Map<ItemIdentity,Long> without(ItemIdentity ticket){var result=new HashMap<>(counts);result.remove(ticket);return Map.copyOf(result);}
    }
    /** Private native request lineage cannot be supplied by a config, GUI marker or fixture callback. */
    public final class NativeReceipt {
        private final JacobRewardClaim.Receipt receipt;private final Object menu;private final Inventory before,after;
        private final Object player,world,connection;private final FarmMaintenanceLease.Token lease;
        private NativeReceipt(JacobRewardClaim.Receipt receipt,Inventory after){this.receipt=receipt;this.menu=requestMenu;this.before=requestInventory;this.after=after;player=scope.player();world=scope.world();connection=scope.connection();lease=token;}
        public UUID id(){return receipt.id();}public long tickets(){return receipt.tickets();}
        public String medals(){return "UNKNOWN: no independent medal balance receipt";}
        public String coinCost(){return "UNKNOWN: no independent Coin cost receipt";}
    }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final FarmMaintenanceCoordinator maintenance;
    private final ModuleManager modules;private dev.veltraph.client.farming.module.FarmModule eventFarm;private ModuleManager.LifecycleSnapshot eventLifecycle;
    private final McMenuAccess menus;private JacobRewardClaim flow;private JacobContestTracker.Scope scope;
    private JacobContestTracker.Ended offered,prepared,started;private FarmMaintenanceLease.Token token,preparedToken;
    private String frozen;private long offerAt,serial;private boolean arming,pending,cancelling;
    private Object requestMenu;private Inventory requestInventory;private ItemIdentity ticket;private NativeReceipt receipt;
    private dev.veltraph.client.VeltraphBootstrap.RootJacobJournalStore journal;
    private dev.veltraph.client.VeltraphBootstrap.RootJacobJournalStore.SavedIntent intent;
    private JacobRewardJournalConfigFence preparedConfigFence,activeConfigFence;private JacobRewardClaimWindow claimWindow;
    private dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedMenuInput ownedInput;
    private String inputObservation="No owned request";private long requestSourceSequence=-1,replySourceSequence=-1;
    private static final class Activation {
        private FarmMaintenanceLease.Token token;private JacobRewardClaim model;
        private dev.veltraph.client.VeltraphBootstrap.RootJacobJournalStore.SavedIntent intent;
        private dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedMenuInput input;
    }
    private Activation activeActivation;
    private boolean sameActivation(Activation original){return original!=null&&activeActivation==original&&flow==original.model&&token==original.token;}
    private void cancelActivation(Activation original,String reason){
        if(original==null)return;if(original.model!=null)original.model.revokeUnconfirmed(reason);
        retireInput(original.input);original.input=null;if(journal!=null&&original.intent!=null)journal.unknown(this,original.intent);maintenance.cancel(original.token,reason);
    }
    private record ClaimRun(Activation activation,JacobRewardClaim flow,FarmMaintenanceLease.Token token,
        dev.veltraph.client.VeltraphBootstrap.RootJacobJournalStore.SavedIntent intent,Object menu,
        JacobContestTracker.Scope scope,JacobRewardJournalConfigFence configFence,JacobRewardClaimWindow window,
        dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedMenuInput input){}
    private ClaimRun captureRun(){return new ClaimRun(activeActivation,flow,token,intent,requestMenu,scope,activeConfigFence,claimWindow,ownedInput);}
    private boolean sameRun(ClaimRun run){return run!=null&&activeActivation==run.activation()&&run.activation()!=null&&run.activation().model==run.flow()&&run.activation().token==run.token()&&run.activation().intent==run.intent()&&run.activation().input==run.input()&&flow==run.flow()&&token==run.token()&&intent==run.intent()&&requestMenu==run.menu()&&scope==run.scope()
        &&activeConfigFence==run.configFence()&&claimWindow==run.window()&&ownedInput==run.input()&&isEnabled()&&run.configFence()!=null&&run.configFence().current(config);}
    private void retireInput(dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedMenuInput exact){if(exact!=null)exact.close();if(ownedInput==exact)ownedInput=null;}
    private void retireOwnedInput(){retireInput(ownedInput);}
    private void stopRun(ClaimRun run,String reason){
        if(run==null)return;run.flow().revokeUnconfirmed(reason);retireInput(run.input());if(run.activation()!=null&&run.activation().input==run.input())run.activation().input=null;
        if(journal!=null&&run.intent()!=null)journal.unknown(this,run.intent());maintenance.cancel(run.token(),reason);
    }
    private boolean ownedRun(ClaimRun run){return sameRun(run)&&owned()&&sameRun(run);}
    private dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedInputInspection inspectRun(ClaimRun run){
        if(run==null||run.input()==null||!ownedRun(run)){stopRun(run,"Original claim owner/configuration/durable scope replaced");return null;}
        try{var inspection=run.input().inspect();if(!sameRun(run)){stopRun(run,"Original claim fields/configuration replaced during native inspection");return null;}
            inputObservation=inspection.stage()+": "+inspection.reason();requestSourceSequence=inspection.sourceRequestSequence();replySourceSequence=inspection.sourceReplySequence();return inspection;
        }catch(Throwable uncertain){String reason="Applied-state inspection exception: "+uncertain.getClass().getSimpleName();if(sameRun(run))inputObservation=reason;stopRun(run,reason);return null;}
    }
    private boolean originalFull36(dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.ContentReceipt full){
        if(full==null||client.player==null||full.nativeMenu()!=requestMenu||full.menuId()!=client.player.containerMenu.containerId||full.revision()!=client.player.containerMenu.getStateId()||!full.cursorEmpty())return false;
        var exact=full.full36Inventory();if(exact.size()!=36)return false;
        for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(exact.get(i),client.player.getInventory().getItem(i)))return false;
        return true;
    }
    public void bindRootJournal(dev.veltraph.client.VeltraphBootstrap.RootJacobJournalStore issued){if(journal!=null||issued==null||!issued.affiliates(this,config))throw new IllegalStateException("Exact Root journal binding required once");journal=issued;}
    private String contractHash(){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(frozen.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    public String journalStatus(){return journal==null?"Journal not bound; claims blocked":journal.status(this);}
    public boolean reconcileIntent(java.util.UUID id,String exactContract,JacobRewardJournal.Decision decision){return journal!=null&&journal.reconcile(this,id,exactContract,decision);}
    public McJacobRewardModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance,ModuleManager modules){
        super(ID,"Selected Jacob reward claim",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;this.maintenance=maintenance;this.modules=modules;menus=new McMenuAccess(client);
    }
    private JacobRewardClaim.Contract contract(){return new JacobRewardClaim.Contract(config.get(JacobRewardConfig.JACOB_REWARD_TITLE),config.get(JacobRewardConfig.JACOB_REWARD_ROW),config.get(JacobRewardConfig.JACOB_REWARD_BEFORE_LORE).lines().toList(),config.get(JacobRewardConfig.JACOB_REWARD_AFTER_LORE).lines().toList(),config.get(JacobRewardConfig.JACOB_REWARD_ACK),config.get(JacobRewardConfig.JACOB_REWARD_TICKETS),config.get(JacobRewardConfig.JACOB_REWARD_TIMEOUT_MS));}
    private String signature(){var result=new TreeMap<String,String>();try{for(var field:JacobRewardConfig.class.getFields())if(field.get(null) instanceof ConfigKey<?> key)result.put(key.name(),String.valueOf(config.get(key)));}catch(IllegalAccessException e){throw new IllegalStateException(e);}return result.toString();}
    private boolean nativeSame(){return scope!=null&&client.player==scope.player()&&client.level==scope.world()&&client.getConnection()==scope.connection()&&client.player!=null&&client.player.isAlive();}
    private boolean closed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    /** Called only from Root's real tracker end extraction, retaining actual native/profile attribution. */
    public void observedEnd(JacobContestTracker.Scope candidate,JacobContestTracker.Ended event){
        if(!isEnabled()&&maintenance.tokenFor(this)==null&&config.get(JacobRewardConfig.JACOB_REWARD_ENABLED)&&candidate!=null&&event!=null&&event.lastObservation().clockKnown()){
            var farms=modules.enabledControllers().stream().filter(dev.veltraph.client.farming.module.FarmModule.class::isInstance).map(dev.veltraph.client.farming.module.FarmModule.class::cast).toList();
            if(farms.size()!=1||farms.getFirst().isSuspended()||!dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(farms.getFirst()).equals(candidate.profile()))return;
            eventLifecycle=modules.lifecycleSnapshot();if(!eventLifecycle.known())return;eventFarm=farms.getFirst();scope=candidate;offered=event;offerAt=services.clock().nowMillis();
        }
    }
    private boolean originalFarm(){return eventFarm!=null&&modules.all().contains(eventFarm)&&eventFarm.isEnabled()&&scope!=null
        &&dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(eventFarm).equals(scope.profile());}
    @Override public Optional<String> preReservationReadiness(){
        if(!arming||!config.get(JacobRewardConfig.JACOB_REWARD_ENABLED)||offered==null||!nativeSame()||!originalFarm()||!modules.lifecycleCurrent(eventLifecycle))return Optional.of("Fresh scoped source contest end required");
        if(services.clock().nowMillis()<offerAt||services.clock().nowMillis()-offerAt>config.get(JacobRewardConfig.JACOB_REWARD_TIMEOUT_MS))return Optional.of("Contest end expired");
        if(journal==null)return Optional.of("Root-owned durable Jacob journal required");var journalError=journal.admissionError(this);if(journalError!=null)return Optional.of(journalError);
        try{contract();}catch(RuntimeException invalid){return Optional.of("Explicit exact claim, claimed row, ACK, ticket count and deadline required");}
        String command=config.get(JacobRewardConfig.JACOB_REWARD_COMMAND);
        if(command.isBlank()||command.length()>256||command.contains("\n")||command.contains("\r")||config.get(JacobRewardConfig.JACOB_REWARD_TICKET_ITEM).isBlank()||config.get(JacobRewardConfig.JACOB_REWARD_TICKET_COMPONENTS).isBlank())return Optional.of("Selected open command and complete ticket item/components required");
        return Optional.empty();
    }
    public boolean dispatch(BooleanSupplier guardedStart){
        if(offered==null||isEnabled()||maintenance.tokenFor(this)!=null)return false;
        arming=true;
        try{if(preReservationReadiness().isPresent()||maintenance.startEligibility(this).isPresent())return false;return guardedStart.getAsBoolean();}
        finally{arming=false;}
    }
    @Override public boolean prepareActivation(FarmMaintenanceLease.Token lease){
        if(preReservationReadiness().isPresent()||!maintenance.preparedActivationValid(this,lease))return false;
        preparedConfigFence=JacobRewardJournalConfigFence.capture(config).orElse(null);if(preparedConfigFence==null)return false;
        prepared=offered;preparedToken=lease;frozen=signature();return true;
    }
    @Override public boolean activatePrepared(FarmMaintenanceLease.Token lease,BooleanSupplier activation){
        if(lease!=preparedToken||prepared==null||prepared!=offered||preparedConfigFence==null||!preparedConfigFence.current(config)||!nativeSame()||!modules.lifecycleCurrent(eventLifecycle)||!Objects.equals(frozen,signature())||!maintenance.preparedActivationValid(this,lease))return false;
        arming=true;try{return activation.getAsBoolean();}finally{arming=false;prepared=null;preparedToken=null;}
    }
    @Override public void cancelPrepared(FarmMaintenanceLease.Token lease){if(lease==preparedToken){prepared=null;preparedToken=null;offered=null;}}
    @Override public Optional<String> preStartCheck(){return preReservationReadiness();}
    @Override public void onEnable(){started=offered;offered=null;activeConfigFence=preparedConfigFence;intent=null;claimWindow=null;retireOwnedInput();inputObservation="No owned request";requestSourceSequence=-1;replySourceSequence=-1;pending=true;token=null;receipt=null;requestMenu=null;requestInventory=null;ticket=new ItemIdentity(config.get(JacobRewardConfig.JACOB_REWARD_TICKET_ITEM),config.get(JacobRewardConfig.JACOB_REWARD_TICKET_COMPONENTS));
        final Activation activation=new Activation();activeActivation=activation;
        flow=new JacobRewardClaim(new JacobRewardClaim.Port(){
            public boolean valid(){return sameActivation(activation)&&owned()&&sameActivation(activation);}
            public boolean closed(){return McJacobRewardModule.this.closed();}
            public boolean open(){
                if(!sameActivation(activation)||!owned()||!sameActivation(activation)||!closed()||intent!=null)return false;var original=captureRun();
                var prepared=journal.prepare(McJacobRewardModule.this,scope.profile(),contractHash());
                if(!sameRun(original)||!sameActivation(activation)){if(prepared!=null)journal.unknown(McJacobRewardModule.this,prepared);stopRun(original,"Original activation replaced during durable opening preparation");return false;}
                intent=prepared;activation.intent=prepared;var preparedRun=captureRun();
                if(prepared==null||!ownedRun(preparedRun)||!closed()||!sameRun(preparedRun)||preparedRun.window()==null||!preparedRun.window().current(services.clock().nowMillis()))return false;
                boolean sent=new McServerCommandService(client).sendCommand(config.get(JacobRewardConfig.JACOB_REWARD_COMMAND));
                if(!sameRun(preparedRun)){stopRun(preparedRun,"Original activation replaced during native opening request");return false;}return sent;
            }
            public JacobRewardClaim.Observation observe(){
                if(!sameActivation(activation))return null;var run=captureRun();if(!ownedRun(run))return null;
                if(run.input()!=null){
                    var inspection=inspectRun(run);if(inspection==null)return null;
                    if(inspection.stage()==dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedInputStage.INVALID){stopRun(run,"Original native input/applied-state chain invalid: "+inspection.reason());return null;}
                    // Only the privately minted exact native pending chain withholds a raw observation.
                    if(inspection.stage()==dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedInputStage.OWNED_PENDING)return null;
                    if(!originalFull36(inspection.fullContent().orElse(null))||!sameRun(run)){stopRun(run,"Original full native content/menu/cursor/inventory mismatch");return null;}
                }
                var menu=menus.read();var inv=inventory();if(menu==null||inv==null||!sameRun(run))return null;
                boolean exact=requestInventory==null||client.player.containerMenu==run.menu()&&inv.without(ticket).equals(requestInventory.without(ticket));
                return new JacobRewardClaim.Observation(menu,exact?inv.tickets(ticket):-1,client.player.containerMenu.getCarried().isEmpty());
            }
            public boolean click(MenuSnapshot expected,int slot){
                if(!sameActivation(activation)||!owned()||!sameActivation(activation)||requestMenu!=null)return false;var fresh=menus.read();var inv=inventory();
                if(fresh==null||!fresh.equals(expected)||inv==null||!client.player.containerMenu.getCarried().isEmpty())return false;
                requestMenu=client.player.containerMenu;requestInventory=inv;var original=captureRun();
                if(original.intent()==null||!ownedRun(original)||original.input()!=null)return false;
                dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedMenuInput issued=null;
                ClaimRun submitted=original;
                try{
                    var producer=dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.existing(client).orElse(null);if(producer==null)return false;
                    issued=producer.beginOwnedInput(original.menu(),fresh.revision(),fresh.title(),slot,0,net.minecraft.world.inventory.ContainerInput.PICKUP,null).orElse(null);
                    if(issued==null)return false;
                    // Preparation invokes Root providers: recheck the original E fences afterwards.
                    if(!sameRun(original)||!ownedRun(original)||client.player.containerMenu!=original.menu()){issued.close();stopRun(original,"Original claim replaced/configuration invalid after native preparation");return false;}
                    ownedInput=issued;activation.input=issued;submitted=captureRun();
                    if(!ownedRun(submitted)||client.player.containerMenu!=submitted.menu()||!sameRun(submitted)||submitted.window()==null||!submitted.window().current(services.clock().nowMillis())){stopRun(submitted,"Original claim dispatch deadline/owner invalid after preparation");return false;}
                    if(!menus.click(fresh,slot)){stopRun(submitted,"Original native request refused or uncertain");return false;}
                    // Retain the local issued handle across effect callbacks; never seal replacement state.
                    if(!sameRun(submitted)){stopRun(submitted,"Original claim replaced during native request");return false;}
                    issued.sealAfterNativeCall();var inspection=inspectRun(submitted);if(inspection==null)return false;
                    if(inspection.stage()==dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedInputStage.INVALID){stopRun(submitted,"Original native submission chain invalid: "+inspection.reason());return false;}return true;
                }catch(Throwable uncertain){String reason="Preparation/effect exception: "+uncertain.getClass().getSimpleName();if(sameRun(submitted))inputObservation=reason;if(issued!=null)issued.close();stopRun(submitted,reason);return false;}
            }
            public boolean close(MenuSnapshot expected){
                if(!sameActivation(activation))return false;var run=captureRun();var result=run.flow().receipt().orElse(null);var inv=inventory();
                if(result==null||inv==null||!ownedRun(run)||client.player.containerMenu!=run.menu()||!inv.without(ticket).equals(requestInventory.without(ticket))||inv.tickets(ticket)-requestInventory.tickets(ticket)!=result.tickets())return false;
                // Callback-capable E ownership is checked before the final exact applied-state proof.
                var applied=inspectRun(run);if(applied==null)return false;
                if(applied.stage()!=dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedInputStage.NATIVE_FULL_CONTENT||!originalFull36(applied.fullContent().orElse(null))||!sameRun(run)||run.window()==null||!run.window().current(services.clock().nowMillis()))return false;
                // Mint only from the same fresh operands read after every callback-capable validation.
                var finalMenu=menus.read();var finalInventory=inventory();
                if(finalMenu==null||!finalMenu.equals(expected)||finalInventory==null||!finalInventory.without(ticket).equals(requestInventory.without(ticket))
                    ||finalInventory.tickets(ticket)-requestInventory.tickets(ticket)!=result.tickets()||!originalFull36(applied.fullContent().orElse(null))||!sameRun(run)||!run.window().current(services.clock().nowMillis()))return false;
                receipt=new NativeReceipt(result,finalInventory);var originalReceipt=receipt;
                var acknowledged=journal.acknowledge(McJacobRewardModule.this,run.intent(),originalReceipt);
                if(acknowledged==null||!sameRun(run)||receipt!=originalReceipt)return false;intent=acknowledged;activation.intent=acknowledged;var acknowledgedRun=captureRun();
                var fresh=menus.read();if(fresh==null||!fresh.equals(expected)||!ownedRun(acknowledgedRun)||client.player.containerMenu!=run.menu())return false;
                // A fresh final inspection follows all provider callbacks and file-dependent guards.
                var finalApplied=inspectRun(acknowledgedRun);
                if(finalApplied==null||finalApplied.stage()!=dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.OwnedInputStage.NATIVE_FULL_CONTENT||!originalFull36(finalApplied.fullContent().orElse(null))||!sameRun(acknowledgedRun)||!acknowledgedRun.window().current(services.clock().nowMillis()))return false;
                retireInput(acknowledgedRun.input());return menus.close(expected.id());
            }
            public boolean beginReturn(){return sameActivation(activation)&&restored()&&maintenance.beginRestore(activation.token)&&maintenance.beginReturn(activation.token,(BooleanSupplier)McJacobRewardModule.this::restored);}
            public JacobRewardClaim.Return returning(){if(!sameActivation(activation))return JacobRewardClaim.Return.FAILED;return switch(maintenance.tickReturn(activation.token)){case ACTIVE->JacobRewardClaim.Return.ACTIVE;case READY->JacobRewardClaim.Return.READY;case FAILED->JacobRewardClaim.Return.FAILED;};}
            public boolean resume(){return sameActivation(activation)&&restored()&&maintenance.resume(activation.token,(BooleanSupplier)McJacobRewardModule.this::restored);}
            public void cancel(){cancelActivation(activation,"Original Jacob activation stopped; no replacement cancellation");}
        });activation.model=flow;
    }
    private Inventory inventory(){try{if(client.player==null||client.level==null)return null;var counts=new HashMap<ItemIdentity,Long>();var inv=client.player.getInventory();for(int i=0;i<inv.getContainerSize();i++){var stack=inv.getItem(i);if(!stack.isEmpty()){var id=new ItemIdentity(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),McMenuAccess.componentIdentity(stack,client.level.registryAccess()));counts.merge(id,(long)stack.getCount(),Math::addExact);}}return new Inventory(counts);}catch(RuntimeException unsupported){return null;}}
    private boolean owned(){return isEnabled()&&token!=null&&activeConfigFence!=null&&activeConfigFence.current(config)&&(intent==null||journal.current(this,intent))&&nativeSame()&&originalFarm()&&eventFarm.maintenanceOwnedBy(token)&&Objects.equals(frozen,signature())&&maintenance.valid(token);}
    private boolean restored(){var r=receipt;var inv=inventory();return r!=null&&r.lease==token&&activeConfigFence!=null&&activeConfigFence.current(config)&&intent!=null&&journal.current(this,intent)&&nativeSame()&&r.player==client.player&&r.world==client.level&&r.connection==client.getConnection()&&Objects.equals(frozen,signature())&&closed()&&inv!=null&&inv.equals(r.after);}
    public void committed(FarmMaintenanceLease.Token lease){if(!isEnabled()||!pending||maintenance.tokenFor(this)!=lease)throw new IllegalStateException("Jacob commit owner changed");token=lease;if(activeActivation==null)throw new IllegalStateException("Jacob activation missing at commit");activeActivation.token=lease;}
    @Override public void onTick(){var activation=activeActivation;var original=flow;if(!sameActivation(activation)||original==null)return;
        if(pending){pending=false;var settings=contract();long now=services.clock().nowMillis();claimWindow=JacobRewardClaimWindow.start(now,settings.timeoutMs());if(!original.start(settings,now,serial))cancelActivation(activation,"Original claim opening refused");}
        else original.tick(services.clock().nowMillis());if(sameActivation(activation)&&!original.active()&&isEnabled())setEnabled(false);
    }
    @Override public void onServerMessage(String text){serial++;if(isEnabled()&&flow!=null)flow.message(text,serial);maintenance.onServerMessage(text);}
    private void cancelOwned(){if(cancelling)return;cancelling=true;try{offered=null;retireOwnedInput();if(journal!=null&&intent!=null)journal.unknown(this,intent);maintenance.cancel(token==null?maintenance.tokenFor(this):token,"Jacob claim interrupted; no farm resume");}finally{cancelling=false;}}
    @Override public void onDisable(){pending=false;retireOwnedInput();menus.clear();if(maintenance.isCompleting(this))return;if(flow!=null)flow.cancel();cancelOwned();}
    @Override public boolean allowsScreen(String title){return title.equals(config.get(JacobRewardConfig.JACOB_REWARD_TITLE));}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&maintenance.consumeExpectedTeleport(position,now);}
    public Optional<NativeReceipt> nativeReceipt(){return Optional.ofNullable(receipt);}public JacobRewardClaim.State workflowState(){return flow==null?JacobRewardClaim.State.IDLE:flow.state();}
    /** Fresh observational receipt/return facts. No validity callback, click, close or state transition. */
    public String receiptDiagnostics(){
        var current=inventory();var menu=client.player==null?null:client.player.containerMenu;
        long beforeRows=0,afterRows=0;
        if(menu!=null&&client.player!=null){
            var beforeLore=config.get(JacobRewardConfig.JACOB_REWARD_BEFORE_LORE).lines().toList();var afterLore=config.get(JacobRewardConfig.JACOB_REWARD_AFTER_LORE).lines().toList();
            for(var slot:menu.slots)if(slot.container!=client.player.getInventory()){
                var stack=slot.getItem();if(!stack.isEmpty()&&MenuSnapshot.clean(stack.getHoverName().getString()).equals(config.get(JacobRewardConfig.JACOB_REWARD_ROW))){
                    var nativeLore=stack.get(net.minecraft.core.component.DataComponents.LORE);var lore=nativeLore==null?List.<String>of():nativeLore.lines().stream().map(line->MenuSnapshot.clean(line.getString())).toList();
                    if(lore.equals(beforeLore))beforeRows++;if(lore.equals(afterLore))afterRows++;
                }
            }
        }
        return (flow==null?"No claim flow":flow.status()+" | "+flow.receiptStatus())
            +" | requestNativeMenuCurrent="+(requestMenu!=null&&menu==requestMenu)
            +",nativeRevision="+(menu==null?-1:menu.getStateId())+",matchingBeforeRows="+beforeRows+",matchingClaimedRows="+afterRows
            +",nativeTickets="+(current==null||ticket==null?-1:current.tickets(ticket))
            +",nonTicketInventoryUnchanged="+(requestInventory!=null&&current!=null&&current.without(ticket).equals(requestInventory.without(ticket)))
            +",ownedInput="+inputObservation+",requestSourceSequence="+requestSourceSequence+",replySourceSequence="+replySourceSequence+",nativeReceipt="+(receipt!=null)+",cursorEmpty="+(menu!=null&&menu.getCarried().isEmpty())
            +",closed="+closed()+",helperEnabled="+isEnabled()+",originalFarmEnabled="+(eventFarm!=null&&eventFarm.isEnabled())
            +",originalFarmOwnsLease="+(eventFarm!=null&&token!=null&&eventFarm.maintenanceOwnedBy(token));
    }
    @Override public String diagnostics(){return flow==null?"Configure a selected claim contract; live reward grammar UNKNOWN":flow.status()+" | "+maintenance.status();}
    public List<dev.veltraph.client.metrics.FinancialActionReceipt> acknowledgedFinancialActions(){
        var r=receipt;if(r==null)return List.of();
        return List.of(new dev.veltraph.client.metrics.FinancialActionReceipt(r.id().toString(),this,
            dev.veltraph.client.metrics.FinancialActionReceipt.Kind.REWARD,
            dev.veltraph.client.metrics.FinancialActionReceipt.Denomination.JACOB_TICKETS,
            java.math.BigDecimal.valueOf(r.tickets()),new dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope(r.player,r.world,r.connection),
            List.of(new dev.veltraph.client.metrics.FinancialActionReceipt.Evidence(r.menu,r.receipt.before().id(),r.receipt.before().revision(),r.receipt.after().revision(),r.receipt.ackSerial(),"JACOB_SELECTED_ROW_ACK_EXACT_NATIVE_INVENTORY"))));
    }
    private record Binding(Object player,Object world,Object connection,Object gear){}
    /** Exact policy creation and stored configuration provenance; a generic Supplier cannot certify Saved gameplay. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return signature();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McJacobRewardModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        /** Exact retained stored state only; no settings Supplier/get or journal fence is called here. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McJacobRewardModule.GameplaySettings.Snapshot s
            &&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current();}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),s->s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(s.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
        public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
        public boolean restored(Object original,Object witness){return original instanceof Binding b&&witness instanceof BooleanSupplier proof&&client.player==b.player()&&client.level==b.world()&&client.getConnection()==b.connection()&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
    },JacobRewardClaim.MAX_TIMEOUT_MS);issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
}
