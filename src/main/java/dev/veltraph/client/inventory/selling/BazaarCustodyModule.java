package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Configured local Bazaar command -> inventory confirmation -> sacks confirmation -> owned close. */
public final class BazaarCustodyModule extends AbstractModule {
    public static final String ID="auto_sell_bazaar_custody";
    private final Minecraft client;private final ConfigService config;private final McMenuAccess menus;private final SellingMenuAccess access;
    private final ModuleManager standaloneManager;private boolean preparedStandalone;private Map<Integer,String> preparedLedger=Map.of();private java.math.BigDecimal preparedPurse;private int standaloneSelected;
    private Object preparedScreen,preparedContainer;private MenuSnapshot preparedMenu;private boolean preparedGameplay;
    private BazaarStandaloneStoredConfig.State standaloneSettings;private boolean validatingStandalone;
    private final BazaarCustodyRuntime runtime=new BazaarCustodyRuntime();
    private BazaarCustodyPlan plan;private String frozen="",outcomeId="",status="Idle";private long tick,deadline;
    private Object player,world,connection,menuOwner,predictionMenu;private ItemStack predictedCursor=ItemStack.EMPTY;
    private Deque<BazaarCustodyRuntime.Stage> remaining=new ArrayDeque<>();private BazaarCustodyRuntime.Stage stage;
    private Map<Integer,String> openingLedger=Map.of();private java.math.BigDecimal openingPurse;private int openingSelected;
    private final List<dev.veltraph.client.metrics.FinancialActionReceipt.Evidence> financialEvidence=new ArrayList<>();
    private final List<BazaarCustodyRuntime.Proof> proofs=new ArrayList<>();private boolean opening,complete;
    public record Acknowledgment(String outcomeId,List<BazaarCustodyRuntime.Proof> stages,String currencyPrefix,
                                 SellingAcknowledgment.Denomination denomination,Object player,Object world,Object connection,List<dev.veltraph.client.metrics.FinancialActionReceipt.Evidence> evidence) {
        public Acknowledgment(String outcomeId,List<BazaarCustodyRuntime.Proof> stages,String currencyPrefix,SellingAcknowledgment.Denomination denomination,Object player,Object world,Object connection){this(outcomeId,stages,currencyPrefix,denomination,player,world,connection,List.of());}
        public Acknowledgment {stages=List.copyOf(stages);evidence=List.copyOf(evidence);}
        public boolean current(Object p,Object w,Object c){return player==p&&world==w&&connection==c;}
        public java.math.BigDecimal proceeds(){return stages.stream().map(BazaarCustodyRuntime.Proof::proceeds).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);}
    }
    public BazaarCustodyModule(Minecraft client,ConfigService config){this(client,config,null);}
    /** Registered standalone owner. Borrowed farm/visitor instances retain their two-argument lifecycle. */
    public BazaarCustodyModule(Minecraft client,ConfigService config,ModuleManager standaloneManager){
        super(ID,"Bazaar Inventory and Sacks",ModuleCategory.MISC);this.client=client;this.config=config;this.standaloneManager=standaloneManager;menus=new McMenuAccess(client);access=new SellingMenuAccess(client);
    }
    private boolean standaloneAuthority(){return standaloneManager==null||standaloneEffectScope(false)!=null;}
    /** Capture stored lifecycle/native leaves before ANY public role/provider callback. */
    private StandaloneEffectScope standaloneEffectScope(boolean cleanup){
        if(standaloneManager==null||validatingStandalone||!client.isSameThread()||!owner()
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=standaloneManager
                ||dev.veltraph.client.VeltraphBootstrap.config()!=config)return null;
        var lifecycle=standaloneManager.lifecycleSnapshot();if(!lifecycle.known())return null;
        var scope=new StandaloneEffectScope(lifecycle,cleanup);if(!scope.current())return null;
        validatingStandalone=true;
        try{
            boolean current=BazaarStandaloneEffectFence.current(standaloneManager,lifecycle,()->{
                var input=standaloneManager.inputOwner().orElse(null);
                return cleanup?(input==null||input==this):input==this&&isEnabled();
            },scope::current);
            return current?scope:null;
        }finally{validatingStandalone=false;}
    }
    private final class StandaloneEffectScope {
        final ModuleManager.LifecycleSnapshot lifecycle;final boolean cleanup;final String activation;
        final net.minecraft.client.player.LocalPlayer nativePlayer;final net.minecraft.client.multiplayer.ClientLevel nativeWorld;
        final net.minecraft.client.multiplayer.ClientPacketListener nativeConnection;final net.minecraft.client.multiplayer.MultiPlayerGameMode nativeGameMode;
        final Object nativeOptions;final net.minecraft.client.gui.screens.Screen nativeScreen;final net.minecraft.world.inventory.AbstractContainerMenu nativeMenu;
        final String title;final long cameraEpoch,cameraSerial;final List<net.minecraft.world.inventory.Slot> nativeSlots;
        final int selection,revision;final ItemStack cursor;final List<ItemStack> inventory,menuStacks;
        final BazaarStandaloneStoredConfig.State settings;final String command;
        StandaloneEffectScope(ModuleManager.LifecycleSnapshot lifecycle,boolean cleanup){
            this.lifecycle=lifecycle;this.cleanup=cleanup;activation=outcomeId;nativePlayer=client.player;nativeWorld=client.level;nativeConnection=client.getConnection();
            nativeGameMode=client.gameMode;nativeOptions=client.options;nativeScreen=client.gui.screen();nativeMenu=nativePlayer.containerMenu;
            selection=nativePlayer.getInventory().getSelectedSlot();revision=nativeMenu.getStateId();cursor=nativeMenu.getCarried().copy();
            title=nativeScreen==null?null:nativeScreen.getTitle().getString();nativeSlots=List.copyOf(nativeMenu.slots);
            cameraEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);cameraSerial=dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client);
            var items=new ArrayList<ItemStack>();for(int i=0;i<36;i++)items.add(nativePlayer.getInventory().getItem(i).copy());inventory=List.copyOf(items);
            var slots=new ArrayList<ItemStack>();for(var slot:nativeMenu.slots)slots.add(slot.getItem().copy());menuStacks=List.copyOf(slots);
            settings=BazaarStandaloneStoredConfig.capture(config);command=plan==null?"":plan.command();
        }
        /** Only concrete final local store reads, private stored lifecycle and direct native fields. */
        boolean directCurrent(){
            if(!client.isSameThread()||client.player!=nativePlayer||client.level!=nativeWorld||client.getConnection()!=nativeConnection
                    ||nativePlayer==null||nativeWorld==null||nativeConnection==null||!nativePlayer.isAlive()
                    ||client.gameMode!=nativeGameMode||client.options!=nativeOptions||client.gui.screen()!=nativeScreen
                    ||dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)!=cameraEpoch||dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)!=cameraSerial
                    ||nativePlayer.containerMenu!=nativeMenu||nativeMenu.getStateId()!=revision
                    ||nativePlayer.getInventory().getSelectedSlot()!=selection||!Objects.equals(outcomeId,activation)
                    ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=standaloneManager||dev.veltraph.client.VeltraphBootstrap.config()!=config
                    ||standaloneManager.get(ID).orElse(null)!=BazaarCustodyModule.this
                    ||!dev.veltraph.client.input.ScriptingInputLock.isActive()||!ID.equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId())
                    ||!cleanup&&(!isEnabled()||selection!=standaloneSelected)
                    ||settings==null||!settings.same(BazaarStandaloneStoredConfig.capture(config))
                    ||!cleanup&&(standaloneSettings==null||!standaloneSettings.same(settings)))return false;
            return true;
        }
        boolean current(){
            if(!directCurrent())return false;
            if(!ItemStack.matches(cursor,nativeMenu.getCarried())||nativeMenu.slots.size()!=menuStacks.size())return false;
            for(int i=0;i<36;i++)if(!ItemStack.matches(inventory.get(i),nativePlayer.getInventory().getItem(i)))return false;
            for(int i=0;i<menuStacks.size();i++)if(nativeMenu.slots.get(i)!=nativeSlots.get(i)||!ItemStack.matches(menuStacks.get(i),nativeMenu.slots.get(i).getItem()))return false;
            if(!Objects.equals(title,nativeScreen==null?null:nativeScreen.getTitle().getString()))return false;
            return directCurrent()&&standaloneManager.lifecycleCurrent(lifecycle);
        }
    }
    private boolean standaloneAction(BazaarCustodyRuntime.Action request,MenuSnapshot expected){
        var scope=standaloneEffectScope(false);if(scope==null||scope.nativeMenu!=menuOwner||scope.nativeGameMode==null
                ||scope.nativeMenu.containerId!=expected.id()||scope.revision!=expected.revision()||!scope.cursor.isEmpty())return false;
        if(request.close()){
            if(!scope.current())return false;scope.nativePlayer.closeContainer();return true;
        }
        var slot=expected.slots().stream().filter(value->value.id()==request.slot()&&!value.playerInventory()&&!value.locked()&&value.count()>0).findFirst();
        if(slot.isEmpty()||!scope.current())return false;
        scope.nativeGameMode.handleContainerInput(scope.nativeMenu.containerId,request.slot(),0,net.minecraft.world.inventory.ContainerInput.PICKUP,scope.nativePlayer);return true;
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean ownsGameplayInput(){return true;}
    @Override public boolean allowsScreen(String title){return plan!=null&&(MenuSnapshot.clean(title).equals(plan.menuTitle())||MenuSnapshot.clean(title).equals(plan.confirmTitle()));}
    private void release(){
        for(var key:List.of(client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse)){
            if(standaloneManager==null)dev.veltraph.client.service.mc.McHeldKeys.setHeld(key,false);
            else{
                var scope=standaloneEffectScope(!isEnabled());if(scope==null)return;
                if(!dev.veltraph.client.service.mc.McHeldKeys.setHeldScoped(key,false,scope::current).confirmed())return;
            }
        }
    }
    private Optional<String> readiness(BazaarCustodyPlan p){
        if(!config.get(SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED)||!config.get(MiscConfig.AUTO_SELL_ENABLED)||config.get(MiscConfig.AUTO_SELL_MODE)!=AutoSellMode.BAZAAR)return Optional.of("Enable explicit Bazaar custody workflow/destination");
        if(config.get(MiscConfig.AUTO_SELL_SACKS)!=!p.sacks().isEmpty())return Optional.of("Sacks permission must exactly match configured sack stage");
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Living native player/connection and empty cursor required");
        var observed=access.read(p.currencyPrefix());if(observed.coins()==null)return Optional.of("One exact current native purse observation required");
        for(var item:p.inventory()){
            var actual=observed.inventoryStack(item.index()).orElse(null);
            if(actual==null||actual.protectedItem()||actual.wearable()||!actual.identity().equals(item.identity())||actual.count()!=item.quantity())return Optional.of("Exact main-stack custody is missing/protected");
        }
        if(client.gui.screen()!=null&&(observed.menu()==null||!observed.menu().title().equals(p.menuTitle())))return Optional.of("Start from gameplay or the configured Bazaar menu");
        if(observed.menu()!=null){var trial=new BazaarCustodyRuntime();trial.start(observed,p,p.inventory().isEmpty()?BazaarCustodyRuntime.Stage.SACKS:BazaarCustodyRuntime.Stage.INVENTORY,0);if(trial.terminal())return Optional.of(trial.status());}
        return Optional.empty();
    }
    @Override public Optional<String> preStartCheck(){try{return readiness(BazaarCustodyPlan.parse(config.get(SellingConfig.SELLING_BAZAAR_PROFILE_JSON)));}catch(RuntimeException invalid){return Optional.of("Explicit Bazaar profile required: "+invalid.getMessage());}}
    @Override public void onEnable(){
        if(standaloneManager==null)release();preparedStandalone=false;tick=0;proofs.clear();financialEvidence.clear();complete=false;access.clear();predictedCursor=ItemStack.EMPTY;predictionMenu=null;menuOwner=null;opening=false;
        player=client.player;world=client.level;connection=client.getConnection();outcomeId=UUID.randomUUID().toString();frozen=config.get(SellingConfig.SELLING_BAZAAR_PROFILE_JSON);
        try{plan=BazaarCustodyPlan.parse(frozen);var error=readiness(plan);if(error.isPresent()){fail(error.get());return;}}
        catch(RuntimeException invalid){fail("Invalid exact Bazaar contract");return;}
        remaining=new ArrayDeque<>();if(!plan.inventory().isEmpty())remaining.add(BazaarCustodyRuntime.Stage.INVENTORY);if(!plan.sacks().isEmpty())remaining.add(BazaarCustodyRuntime.Stage.SACKS);
        stage=remaining.removeFirst();
        if(standaloneManager!=null){
            standaloneSettings=BazaarStandaloneStoredConfig.capture(config);if(standaloneSettings==null){fail("Known final local configuration store required for standalone custody");return;}
            var observed=read();if(!owner()){fail("Prepared standalone native owner changed");return;}
            preparedLedger=observed.ledger();preparedPurse=observed.coins();standaloneSelected=client.player.getInventory().getSelectedSlot();
            preparedScreen=client.gui.screen();preparedContainer=client.player.containerMenu;preparedMenu=observed.menu();
            preparedGameplay=preparedScreen==null&&preparedContainer==client.player.inventoryMenu;
            if(!PreparedBazaarStartBoundary.current(preparedGameplay,preparedScreen,preparedContainer,preparedMenu,
                    client.gui.screen(),client.player.containerMenu,client.player.inventoryMenu,observed.menu())){
                fail("Prepared standalone initial native menu mode is inconsistent");return;
            }
            preparedStandalone=true;status="Prepared; waiting for ordinary registered Root ownership";
        }else openOrBegin();
    }
    private boolean owner(){return client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive();}
    private SellingObservation read(){
        var value=access.read(plan.currencyPrefix());boolean prediction=predictionMenu==client.player.containerMenu&&!predictedCursor.isEmpty()&&ItemStack.matches(predictedCursor,client.player.containerMenu.getCarried());
        return new SellingObservation(value.menu(),value.inventory(),value.slots(),value.cursor(),value.coins(),value.droppedItems(),prediction);
    }
    private void openOrBegin(){
        if(!owner()||!standaloneAuthority()){fail("Native Bazaar owner/standalone authority changed");return;}var value=read();
        if(value.menu()!=null&&value.menu().title().equals(plan.menuTitle())){runtime.start(value,plan,stage,tick);menuOwner=client.player.containerMenu;opening=false;status=runtime.status();if(runtime.terminal())fail(status);return;}
        if(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()){fail("Native menus must acknowledge closed before opener");return;}
        openingLedger=value.ledger();openingPurse=value.coins();openingSelected=client.player.getInventory().getSelectedSlot();
        if(standaloneManager==null)client.getConnection().sendCommand(plan.command());
        else{
            var scope=standaloneEffectScope(false);if(scope==null||!scope.current()){fail("Standalone Bazaar command authority changed");return;}
            scope.nativeConnection.sendCommand(scope.command);
        }
        opening=true;deadline=tick+plan.timeoutTicks();status="Waiting for exact native Bazaar menu after configured command";
    }
    @Override public void onTick(){
        tick++;if(!owner()||!standaloneAuthority()||!config.get(SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED)||!config.get(MiscConfig.AUTO_SELL_ENABLED)
                ||config.get(MiscConfig.AUTO_SELL_MODE)!=AutoSellMode.BAZAAR||config.get(MiscConfig.AUTO_SELL_SACKS)!=!plan.sacks().isEmpty()
                ||!config.get(SellingConfig.SELLING_BAZAAR_PROFILE_JSON).equals(frozen)){fail("Exact native owner/settings/permission changed");return;}
        release();
        if(preparedStandalone){
            var observed=read();if(!observed.cursorEmpty()||!observed.ledger().equals(preparedLedger)||!Objects.equals(observed.coins(),preparedPurse)
                    ||!PreparedBazaarStartBoundary.current(preparedGameplay,preparedScreen,preparedContainer,preparedMenu,
                    client.gui.screen(),client.player.containerMenu,client.player.inventoryMenu,observed.menu())){
                fail("Prepared standalone Bazaar inventory/purse/cursor/menu mode changed");return;
            }
            preparedStandalone=false;openOrBegin();return;
        }
        if(opening){var observed=read();if(!observed.cursorEmpty()||!observed.ledger().equals(openingLedger)||observed.coins()==null||observed.coins().compareTo(openingPurse)!=0||client.player.getInventory().getSelectedSlot()!=openingSelected){fail("Protected inventory/purse/selection changed during Bazaar opener");return;}if(observed.menu()!=null&&observed.menu().title().equals(plan.menuTitle())){openOrBegin();return;}
            if(tick>deadline||client.gui.screen()!=null){fail("Configured Bazaar menu opener acknowledgment failed");return;}return;}
        var observed=read();boolean confirmation=runtime.state()==BazaarCustodyRuntime.State.WAIT_CONFIRM&&observed.menu()!=null&&observed.menu().title().equals(plan.confirmTitle());
        if(observed.menu()!=null&&client.player.containerMenu!=menuOwner&&!confirmation){fail("Native Bazaar container object replaced");return;}
        var action=runtime.tick(observed,tick);if(observed.menu()!=null&&runtime.ownedMenu()==observed.menu().id())menuOwner=client.player.containerMenu;
        if(action.isPresent()){
            var request=action.get();var current=read();var boundMenu=menus.read();boolean accepted=owner()&&standaloneAuthority()&&client.player.containerMenu==menuOwner&&runtime.valid(request,current)&&current.cursorEmpty()&&boundMenu!=null&&boundMenu.equals(current.menu())
                    &&(standaloneManager==null?(request.close()?menus.close(boundMenu.id()):menus.click(boundMenu,request.slot())):standaloneAction(request,boundMenu));
            runtime.applied(request,accepted,tick);
            if(accepted&&!request.close()){predictionMenu=client.player.containerMenu;predictedCursor=client.player.containerMenu.getCarried().copy();}
        }
        status=runtime.status();if(runtime.state()==BazaarCustodyRuntime.State.COMPLETE){
            var proof=runtime.acknowledged().orElseThrow();proofs.add(proof);
            financialEvidence.add(new dev.veltraph.client.metrics.FinancialActionReceipt.Evidence(menuOwner,proof.menuId(),proof.requestRevision(),proof.receiptRevision(),proof.matchedReceiptSerial(),"BAZAAR_"+proof.stage().name()));
            if(!remaining.isEmpty()){stage=remaining.removeFirst();predictedCursor=ItemStack.EMPTY;predictionMenu=null;menuOwner=null;openOrBegin();}
            else{complete=true;setEnabled(false);}
        }else if(runtime.terminal())setEnabled(false);
    }
    @Override public void onServerMessage(String line){if(isEnabled())runtime.onMessage(line);}
    @Override public void onDisable(){
        release();preparedStandalone=false;if(!complete)runtime.cancel("Stopped; no further Bazaar request");
        if(owner()&&client.player.containerMenu==menuOwner&&client.player.containerMenu.getCarried().isEmpty()){
            var current=menus.read();if(current!=null&&runtime.ownedMenu()==current.id()&&allowsScreen(current.title())){
                if(standaloneManager==null)menus.close(current.id());
                else{
                    var scope=standaloneEffectScope(true);if(scope!=null&&scope.nativeMenu==menuOwner&&scope.nativeMenu.containerId==current.id()
                            &&scope.revision==current.revision()&&scope.cursor.isEmpty()&&scope.current())scope.nativePlayer.closeContainer();
                }
            }
        }
    }
    private void fail(String reason){status=reason;runtime.cancel(reason);if(isEnabled())setEnabled(false);}
    public Optional<Acknowledgment> acknowledgedSale(){
        if(!client.isSameThread())throw new IllegalStateException("Bazaar evidence requires client thread");
        return complete&&owner()?Optional.of(new Acknowledgment(outcomeId,proofs,plan.currencyPrefix(),plan.denomination(),player,world,connection,financialEvidence)):Optional.empty();
    }
    public BazaarCustodyRuntime.State sellingState(){return runtime.state();}
    @Override public String diagnostics(){return status;}
}
