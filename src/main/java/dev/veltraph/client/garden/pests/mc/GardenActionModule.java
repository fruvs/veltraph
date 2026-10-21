package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import java.math.BigDecimal;
import java.util.*;

/** Shared bounded action ownership, exact configuration freeze, native menu cleanup and local movement. */
public abstract class GardenActionModule extends AbstractModule {
    public enum Phase { IDLE, APPROACH, OPEN_MENU, BUY, RELOCATE, RELEASE, PICKUP_BAIT, PLACE_BAIT, WAIT_ACK, AIM, USE, WAIT_EFFECT, COMPLETE, FAILED, CANCELLED }
    protected final Minecraft client;protected final FarmServices services;protected final ConfigService config;protected final McMenuAccess menus;
    protected final PestInventoryLease lease;protected final McNavigator navigator;protected final McSharedMovementNavigator travel;protected final McGameContextService location;
    protected Phase phase=Phase.IDLE;protected String reason="Idle";protected LocalPlayer owner;protected ClientLevel level;
    protected Object ownedMenu;protected long deadline,last;protected int originalSlot;private Map<String,String> originalConfig=Map.of();
    protected GardenActionModule(String id,String name,Minecraft c,FarmServices s,ConfigService cfg){super(id,name,ModuleCategory.INTERMEDIARY);client=c;services=s;config=cfg;
        menus=new McMenuAccess(c);lease=new PestInventoryLease(c);navigator=new McNavigator(c,s.input(),cfg);travel=new McSharedMovementNavigator(c,s,cfg);location=new McGameContextService(c);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&walking&&now()<deadline&&travel.consumeExpectedTeleport(position,now);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    protected abstract boolean optedIn();protected abstract void begin();protected abstract void advance();protected abstract Optional<String> ready();
    protected abstract boolean screenAllowed(String title);
    /** Narrow child hook: incomplete retired transport may suppress Parent side effects in foreign scope. */
    protected boolean lifecycleStopAllowed(){return true;}
    /** Cleanup-only child proof may permit restitution after an explicit disable without permitting a stop. */
    protected boolean lifecycleCleanupAllowed(){return lifecycleStopAllowed();}
    /** A specialized child handles its own typed cleanup, including refusal, without raw fallthrough. */
    protected boolean childCleanupHandled(){return false;}
    /** Specialized initialization has already acquired its actual native Parent admission. */
    protected boolean childInitializationHandled(){return false;}
    /** Runs only after the existing ordinary tick guards; default actions need no deferred preparation. */
    protected boolean childTickPrepared(){return true;}
    /** Default actions issue no restart evidence. The concrete child supplies its actual closed interaction. */
    protected dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.RetiredParentInteraction childCompletionInteraction(){return null;}
    /** Runs inside actual finish, before disable; null means cancellation, failure or unavailable normal proof. */
    protected void childBeforeOwnedDisable(NormalCompletionReceipt completion){}
    /** Read-only actual COMPLETE disable returned normally, including the entire real Core post-return hook. */
    protected void childOwnedDisableNormalReturned(NormalCompletionReceipt completion){}
    /** Actual native tentative enable observation only. The child must defer authority to the first ordinary tick. */
    protected void childRestartPreparation(ModuleManager.OwnEnableReceipt ownEnable){}
    /** Logical source stamp only: never admission, control, gear or cleanup permission. */
    protected final long sourceVersion(){return normalSourceVersion;}
    /** Only actual managed disable normal return can provide this private Core receipt. Read-only child hook. */
    protected void childActualDisableReturned(ModuleManager.OwnDisableReceipt ownDisable){}
    @Override protected final void postDisableNormalReturned(ModuleManager.OwnDisableReceipt ownDisable){childActualDisableReturned(ownDisable);}
    private NormalCompletionReceipt issuedNormalCompletion;
    private long normalSourceVersion;private boolean normalSourceExhausted;
    private Map<ConfigKey<?>,Long> originalConfigEpochs;
    private boolean issuingNormalCompletion;
    /** Privately produced normal source completion, never a caller-selected terminal kind or restart permission. */
    public static final class NormalCompletionReceipt {
        private final GardenActionModule parent;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.RetiredParentInteraction interaction;
        private final ModuleManager root;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final Object player,world,connection,store,rotation,inventoryMenu,ownedMenu,options,gameMode;
        private final int originalSlot;private OwnedMenuCleanupReceipt menuCleanup;private boolean menuIssuing;
        private final Map<String,String> frozenConfig;private final Map<ConfigKey<?>,Long> frozenEpochs;private final long deadline,sourceVersion,clockFloor;
        private NormalCompletionReceipt(GardenActionModule parent,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.RetiredParentInteraction interaction,
                ModuleManager root,ModuleManager.LifecycleSnapshot lifecycle){
            this.parent=parent;this.interaction=interaction;this.root=root;this.lifecycle=lifecycle;
            player=parent.owner;world=parent.level;connection=parent.client.getConnection();store=parent.config;rotation=parent.services.rotation();
            inventoryMenu=parent.owner==null?null:parent.owner.inventoryMenu;ownedMenu=parent.ownedMenu;
            options=parent.client.options;gameMode=parent.client.gameMode;originalSlot=parent.originalSlot;
            frozenConfig=parent.originalConfig;frozenEpochs=parent.originalConfigEpochs;deadline=parent.deadline;sourceVersion=parent.normalSourceVersion;clockFloor=parent.last;
        }
        public GardenActionModule parent(){return parent;}
        public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.RetiredParentInteraction interaction(){return interaction;}
        public long deadlineMillis(){return deadline;}
        public long originalClockFloorMillis(){return clockFloor;}
        /** Final known native configuration implementations have callback-free typed reads. Unknown providers refuse. */
        private boolean nativeLeaf(boolean cleanup,ModuleManager.LifecycleSnapshot observed){var c=parent.client;
            return c.isSameThread()&&(cleanup||parent.isEnabled())&&parent.phase==Phase.COMPLETE&&parent.deadline==deadline
                &&!parent.normalSourceExhausted&&parent.normalSourceVersion==sourceVersion
                &&(cleanup||parent.owner==player&&parent.level==world)&&c.player==player&&c.level==world&&c.getConnection()==connection
                &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.get(parent.id()).orElse(null)==parent
                &&dev.veltraph.client.VeltraphBootstrap.config()==store&&parent.config==store
                &&(store instanceof MapConfigService||store instanceof JsonConfigService)
                &&c.options==options&&c.gameMode==gameMode&&parent.services.rotation()==rotation&&parent.originalConfig==frozenConfig&&parent.originalConfigEpochs==frozenEpochs
                &&player instanceof LocalPlayer actual&&actual.inventoryMenu==inventoryMenu
                &&(cleanup||parent.ownedMenu==ownedMenu)&&frozenEpochs!=null&&frozenEpochs.equals(parent.configurationEpochs())
                &&frozenConfig.equals(parent.signature())&&root.lifecycleCurrent(observed);
        }
        private boolean sourceCurrent(){try{
            if(!nativeLeaf(false,lifecycle))return false;long time=parent.now();
            boolean source=time>=parent.last&&time<deadline&&parent.owner.isAlive()&&parent.optedIn()
                &&frozenConfig.equals(parent.signature())&&GardenPlotBounds.contains(parent.plot(),parent.owner.getX(),parent.owner.getZ())
                &&(!parent.config.get(PestConfig.PEST_ACTION_CONTEST_ONLY)||GardenContestSignal.active(parent.sidebar()));
            // The actual H proof ends with its private admission and native controller lifecycle leaf.
            return source&&interaction!=null&&interaction.current()&&nativeLeaf(false,lifecycle);
        }catch(RuntimeException unavailable){return false;}}
        public boolean current(){return parent.issuedNormalCompletion==this&&sourceCurrent();}
        /** Original normal source only. It grants no H admission, camera, key, gear or gameplay permission. */
        public boolean cleanupSourceCurrent(){try{
            if(parent.issuedNormalCompletion!=this)return false;
            var observed=root.lifecycleSnapshot();if(observed==null||!observed.known()||!nativeLeaf(true,observed))return false;
            long time=parent.now();boolean source=time>=parent.last&&time<deadline&&((LocalPlayer)player).isAlive()&&parent.optedIn()
                &&GardenPlotBounds.contains(parent.plot(),((LocalPlayer)player).getX(),((LocalPlayer)player).getZ())
                &&(!parent.config.get(PestConfig.PEST_ACTION_CONTEST_ONLY)||GardenContestSignal.active(parent.sidebar()));
            // No gameplay proof or control baseline adoption after actual owned camera cancellation.
            return source&&nativeLeaf(true,observed);
        }catch(RuntimeException unavailable){return false;}}
        public boolean strictPolicyCurrent(){return cleanupSourceCurrent();}
        private static final class FinalPolicySnapshot {
            private final NormalCompletionReceipt owner;private final ModuleManager.LifecycleSnapshot lifecycle;
            private final long sourceVersion,deadline,last;private final double x,y,z;
            private final Map<String,String> values;private final Map<ConfigKey<?>,Long> epochs;private final List<String> sidebar;
            private FinalPolicySnapshot(NormalCompletionReceipt owner){this.owner=owner;lifecycle=owner.root.lifecycleSnapshot();
                sourceVersion=owner.parent.normalSourceVersion;deadline=owner.parent.deadline;last=owner.parent.last;
                var player=(LocalPlayer)owner.player;x=player.getX();y=player.getY();z=player.getZ();
                values=owner.parent.signature();epochs=owner.parent.configurationEpochs();sidebar=owner.nativeSidebar();
            }
        }
        /** Native state only. This privately minted observation is a refusal fence, never permission. */
        public Object finalPolicySnapshot(){try{
            if(!(store instanceof MapConfigService||store instanceof JsonConfigService)||player==null||root==null)return null;
            var snapshot=new FinalPolicySnapshot(this);return finalPolicyCurrent(snapshot)?snapshot:null;
        }catch(RuntimeException unavailable){return null;}}
        /** No H/permit/roster/provider/clock or game-context callback may run in this final leaf. */
        public boolean finalPolicyCurrent(Object exact){try{
            if(!(exact instanceof FinalPolicySnapshot snapshot)||snapshot.owner!=this||parent.issuedNormalCompletion!=this
                    ||snapshot.lifecycle==null||!snapshot.lifecycle.known()||snapshot.epochs==null)return false;
            var c=parent.client;var actual=(LocalPlayer)player;
            return c.isSameThread()&&parent.phase==Phase.COMPLETE&&!parent.normalSourceExhausted&&parent.normalSourceVersion==sourceVersion
                &&parent.normalSourceVersion==snapshot.sourceVersion&&parent.deadline==deadline&&parent.deadline==snapshot.deadline&&parent.last==snapshot.last
                &&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode&&actual.isAlive()
                &&actual.inventoryMenu==inventoryMenu&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.get(parent.id()).orElse(null)==parent
                &&dev.veltraph.client.VeltraphBootstrap.config()==store&&parent.config==store&&(store instanceof MapConfigService||store instanceof JsonConfigService)
                &&parent.originalConfig==frozenConfig&&parent.originalConfigEpochs==frozenEpochs
                &&snapshot.values.equals(frozenConfig)&&snapshot.epochs.equals(frozenEpochs)
                &&snapshot.values.equals(parent.signature())&&snapshot.epochs.equals(parent.configurationEpochs())
                &&Double.compare(actual.getX(),snapshot.x)==0&&Double.compare(actual.getY(),snapshot.y)==0&&Double.compare(actual.getZ(),snapshot.z)==0
                &&snapshot.sidebar.equals(nativeSidebar())&&root.lifecycleCurrent(snapshot.lifecycle);
        }catch(RuntimeException unavailable){return false;}}
        /** Sampled final clock is checked without invoking another clock or policy/provider callback. */
        public boolean finalPolicyTimeCurrent(Object exact,long sampledTime){return exact instanceof FinalPolicySnapshot snapshot&&snapshot.owner==this
            &&sampledTime>=clockFloor&&sampledTime>=snapshot.last&&sampledTime<snapshot.deadline&&snapshot.deadline==deadline;}
        /** New source only. It cannot renew the old H, camera, key, inventory or menu permission. */
        public static final class ReentrySourceSnapshot {
            private final NormalCompletionReceipt completion;private final ModuleManager.OwnEnableReceipt enable;
            private final ModuleManager.LifecycleSnapshot stopped,lifecycle;private final long version,deadline,last;
            private final double x,y,z;private final List<String> sidebar;
            private ReentrySourceSnapshot(NormalCompletionReceipt completion,ModuleManager.OwnEnableReceipt enable,ModuleManager.LifecycleSnapshot stopped){
                this.completion=completion;this.enable=enable;this.stopped=stopped;lifecycle=completion.root.lifecycleSnapshot();
                version=completion.parent.normalSourceVersion;deadline=completion.parent.deadline;last=completion.parent.last;
                var player=(LocalPlayer)completion.player;x=player.getX();y=player.getY();z=player.getZ();sidebar=completion.nativeSidebar();
            }
            public NormalCompletionReceipt normalCompletion(){return completion;}public GardenActionModule parent(){return completion.parent;}
            public long newDeadlineMillis(){return deadline;}public long newClockFloorMillis(){return last;}
            public boolean current(){return completion.reentryNativeCurrent(this);}
            public boolean sampledTimeCurrent(long sampled){return sampled>=completion.clockFloor&&sampled>=last&&sampled<completion.deadline&&sampled<deadline;}
        }
        /** Actual private core own-enable before/after match, exact one source reset, original frozen policy. */
        public ReentrySourceSnapshot observeOwnReentry(ModuleManager.OwnEnableReceipt enable,ModuleManager.LifecycleSnapshot actualStopped){try{
            if(enable==null||enable.parent()!=parent||actualStopped==null||sourceVersion==Long.MAX_VALUE
                    ||!root.ownEnableMatches(enable,actualStopped,parent))return null;
            var snapshot=new ReentrySourceSnapshot(this,enable,actualStopped);return snapshot.current()?snapshot:null;
        }catch(RuntimeException unavailable){return null;}}
        private boolean reentryNativeCurrent(ReentrySourceSnapshot snapshot){try{var c=parent.client;var actual=(LocalPlayer)player;
            return snapshot!=null&&snapshot.completion==this&&snapshot.enable.parent()==parent&&root.ownEnableMatches(snapshot.enable,snapshot.stopped,parent)
                &&c.isSameThread()&&parent.isEnabled()&&parent.state()==MacroState.RUNNING&&parent.phase==Phase.IDLE&&parent.issuedNormalCompletion==null
                &&!parent.normalSourceExhausted&&sourceVersion<Long.MAX_VALUE&&parent.normalSourceVersion==sourceVersion+1&&parent.normalSourceVersion==snapshot.version
                &&parent.deadline==snapshot.deadline&&parent.last==snapshot.last&&c.player==player&&c.level==world&&c.getConnection()==connection
                &&c.options==options&&c.gameMode==gameMode&&actual.isAlive()&&actual.inventoryMenu==inventoryMenu&&actual.containerMenu==inventoryMenu
                &&c.gui.screen()==null&&actual.containerMenu.getCarried().isEmpty()&&parent.ownedMenu==null
                &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.get(parent.id()).orElse(null)==parent
                &&dev.veltraph.client.VeltraphBootstrap.config()==store&&parent.config==store&&(store instanceof MapConfigService||store instanceof JsonConfigService)
                &&frozenEpochs!=null&&parent.originalConfig!=frozenConfig&&parent.originalConfigEpochs!=frozenEpochs
                &&frozenConfig.equals(parent.originalConfig)&&frozenEpochs.equals(parent.originalConfigEpochs)
                &&frozenConfig.equals(parent.signature())&&frozenEpochs.equals(parent.configurationEpochs())
                &&Double.compare(actual.getX(),snapshot.x)==0&&Double.compare(actual.getY(),snapshot.y)==0&&Double.compare(actual.getZ(),snapshot.z)==0
                &&snapshot.sidebar.equals(nativeSidebar())&&root.lifecycleCurrent(snapshot.lifecycle);
        }catch(RuntimeException unavailable){return false;}}
        /** Direct native scoreboard cells; no location/provider resolver or configuration callback. */
        private List<String> nativeSidebar(){var c=parent.client;if(c.level==null)return List.of();var board=c.level.getScoreboard();
            if(board==null)return List.of();var objective=board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
            if(objective==null)return List.of();var rows=new ArrayList<String>();rows.add(objective.getDisplayName().getString());
            for(var entry:board.listPlayerScores(objective)){var name=entry.owner();var team=board.getPlayersTeam(name);
                rows.add(team==null?name:team.getPlayerPrefix().getString()+name+team.getPlayerSuffix().getString());}
            return List.copyOf(rows);
        }

        /** The transaction invokes this concrete producer; it cannot supply a caller-made menu result. */
        public OwnedMenuCleanupReceipt cleanupMenuIssued(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit){
            if(menuIssuing)return null;if(menuCleanup!=null)return menuCleanup.permit==permit?menuCleanup:null;
            menuIssuing=true;try{
                var observed=root.lifecycleSnapshot();MenuCleanupStatus status=MenuCleanupStatus.REFUSED;
                if(permit!=null&&permit.parent()==parent&&permit.nativeClient()==parent.client&&permit.currentOriginal()
                        &&permit.cleanupCurrent()&&cleanupSourceCurrent()&&root.lifecycleCurrent(observed)){
                    if(ownedMenu==null&&menuLeaf(observed))status=MenuCleanupStatus.NO_OWNED_WINDOW;
                    else if(ownedMenu!=null)status=MenuCleanupStatus.UNKNOWN_OWNED_WINDOW_TRANSITION;
                    // No unqualified closeContainer can mutate a foreign or reentrant window.
                }
                menuCleanup=new OwnedMenuCleanupReceipt(this,permit,status);
                return menuCleanup;
            }catch(RuntimeException unavailable){menuCleanup=new OwnedMenuCleanupReceipt(this,permit,MenuCleanupStatus.FAILED);return menuCleanup;}
            finally{menuIssuing=false;}
        }
        private boolean menuLeaf(ModuleManager.LifecycleSnapshot observed){var c=parent.client;
            return nativeLeaf(true,observed)&&ownedMenu==null&&c.player==player&&c.level==world&&c.getConnection()==connection
                &&c.player.containerMenu==inventoryMenu&&c.gui.screen()==null&&c.player.containerMenu.getCarried().isEmpty()
                &&originalSlot>=0&&c.player.getInventory().getSelectedSlot()==originalSlot&&c.options==options&&c.gameMode==gameMode;
        }
    }
    public enum MenuCleanupStatus { NO_OWNED_WINDOW,UNKNOWN_OWNED_WINDOW_TRANSITION,REFUSED,FAILED }
    /** Private actual native no-window evidence; an unsupported owned transition cannot authorize restart. */
    public static final class OwnedMenuCleanupReceipt {
        private final NormalCompletionReceipt completion;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit;
        private final MenuCleanupStatus status;
        private OwnedMenuCleanupReceipt(NormalCompletionReceipt completion,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit,MenuCleanupStatus status){
            this.completion=completion;this.permit=permit;this.status=status;
        }
        public NormalCompletionReceipt completion(){return completion;}
        public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit(){return permit;}
        public MenuCleanupStatus status(){return status;}
        public boolean currentOwned(){try{
            if(status!=MenuCleanupStatus.NO_OWNED_WINDOW||permit==null||completion.menuCleanup!=this)return false;
            var observed=completion.root.lifecycleSnapshot();
            return observed!=null&&observed.known()&&completion.cleanupSourceCurrent()&&permit.currentOriginal()&&permit.cleanupCurrent()
                &&completion.menuLeaf(observed);
        }catch(RuntimeException unavailable){return false;}}
        public boolean restartQualified(){return currentOwned();}
        private static final class FinalMenuSnapshot {
            private final OwnedMenuCleanupReceipt owner;private final Object policy,menu,inventory;private final ItemStackValue cursor;
            private final int selected;
            private FinalMenuSnapshot(OwnedMenuCleanupReceipt owner){this.owner=owner;var c=owner.completion.parent.client;
                policy=owner.completion.finalPolicySnapshot();menu=c.player.containerMenu;inventory=c.player.getInventory();
                cursor=new ItemStackValue(c.player.containerMenu.getCarried());selected=c.player.getInventory().getSelectedSlot();
            }
        }
        private static final class ItemStackValue {
            private final net.minecraft.world.item.ItemStack value;private ItemStackValue(net.minecraft.world.item.ItemStack value){this.value=value.copy();}
            private boolean matches(net.minecraft.world.item.ItemStack actual){return net.minecraft.world.item.ItemStack.matches(value,actual);}
        }
        private static final class FinalReentryMenuSnapshot {
            private final OwnedMenuCleanupReceipt owner;private final NormalCompletionReceipt.ReentrySourceSnapshot source;
            private final Object inventory,menu;private final ItemStackValue cursor;private final int selected;
            private FinalReentryMenuSnapshot(OwnedMenuCleanupReceipt owner,NormalCompletionReceipt.ReentrySourceSnapshot source){this.owner=owner;this.source=source;
                var c=owner.completion.parent.client;inventory=c.player.getInventory();menu=c.player.containerMenu;cursor=new ItemStackValue(c.player.containerMenu.getCarried());selected=c.player.getInventory().getSelectedSlot();}
        }
        public Object finalReentryNativeSnapshot(NormalCompletionReceipt.ReentrySourceSnapshot source){try{
            if(source==null||source.normalCompletion()!=completion||status!=MenuCleanupStatus.NO_OWNED_WINDOW)return null;
            var snapshot=new FinalReentryMenuSnapshot(this,source);return finalReentryNativeCurrent(snapshot,source)?snapshot:null;
        }catch(RuntimeException unavailable){return null;}}
        /** New native observation only; old currentOwned/finalPolicy predicates remain revoked by the real source reset. */
        public boolean finalReentryNativeCurrent(Object exact,NormalCompletionReceipt.ReentrySourceSnapshot source){try{
            if(!(exact instanceof FinalReentryMenuSnapshot snapshot)||snapshot.owner!=this||snapshot.source!=source||source==null
                    ||source.normalCompletion()!=completion||completion.menuCleanup!=this||status!=MenuCleanupStatus.NO_OWNED_WINDOW||completion.ownedMenu!=null)return false;
            var c=completion.parent.client;return source.current()&&c.player==completion.player&&c.level==completion.world&&c.getConnection()==completion.connection
                &&c.player.getInventory()==snapshot.inventory&&c.player.containerMenu==snapshot.menu&&snapshot.menu==completion.inventoryMenu
                &&c.gui.screen()==null&&snapshot.cursor.matches(c.player.containerMenu.getCarried())&&c.player.containerMenu.getCarried().isEmpty()
                &&snapshot.selected==completion.originalSlot&&c.player.getInventory().getSelectedSlot()==snapshot.selected;
        }catch(RuntimeException unavailable){return false;}}
        public Object finalNativeSnapshot(){try{if(status!=MenuCleanupStatus.NO_OWNED_WINDOW)return null;
            var snapshot=new FinalMenuSnapshot(this);return finalNativeCurrent(snapshot)?snapshot:null;
        }catch(RuntimeException unavailable){return null;}}
        /** No scope/permit/provider/clock callbacks; private actual absent-window result remains bound to its original receipt. */
        public boolean finalNativeCurrent(Object exact){try{
            if(!(exact instanceof FinalMenuSnapshot snapshot)||snapshot.owner!=this||completion.menuCleanup!=this
                    ||status!=MenuCleanupStatus.NO_OWNED_WINDOW||completion.ownedMenu!=null||snapshot.policy==null)return false;
            var c=completion.parent.client;
            return completion.finalPolicyCurrent(snapshot.policy)&&c.player==completion.player&&c.level==completion.world&&c.getConnection()==completion.connection
                &&c.player.containerMenu==snapshot.menu&&snapshot.menu==completion.inventoryMenu&&c.player.getInventory()==snapshot.inventory
                &&c.gui.screen()==null&&snapshot.cursor.matches(c.player.containerMenu.getCarried())&&c.player.containerMenu.getCarried().isEmpty()
                &&c.player.getInventory().getSelectedSlot()==snapshot.selected&&snapshot.selected==completion.originalSlot;
        }catch(RuntimeException unavailable){return false;}}
    }
    private NormalCompletionReceipt issueNormalCompletion(){
        if(issuingNormalCompletion||phase!=Phase.COMPLETE||!isEnabled())return null;
        issuingNormalCompletion=true;try{
            var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(lifecycle==null||!lifecycle.known()||root.get(id()).orElse(null)!=this)return null;
            var interaction=childCompletionInteraction();if(interaction==null)return null;
            var receipt=new NormalCompletionReceipt(this,interaction,root,lifecycle);
            if(!receipt.sourceCurrent())return null;
            issuedNormalCompletion=receipt;return receipt.current()?receipt:null;
        }catch(RuntimeException unavailable){return null;}finally{issuingNormalCompletion=false;}
    }
    protected boolean allowsBarn(){return false;}
    @Override public boolean allowsScreen(String title){return isEnabled()&&screenAllowed(LocationText.stripColorCodes(title).trim());}
    protected long now(){return services.clock().nowMillis();}
    protected int plot(){return config.get(PestConfig.PEST_ACTION_PLOT);}
    protected List<String> sidebar(){location.resolution();return location.signals().sidebarRowsRaw().stream().map(s->LocationText.stripColorCodes(s).trim()).toList();}
    protected Optional<BigDecimal> balance(String prefix){return PestBindings.amount(sidebar(),prefix,"");}
    protected Optional<String> commonReady(){
        if(!optedIn())return Optional.of("Enable this Garden action explicitly");
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gameMode==null)return Optional.of("Garden player/world unavailable");
        if(plot()<(allowsBarn()?0:1)||plot()>24||!GardenPlotBounds.contains(plot(),client.player.getX(),client.player.getZ()))return Optional.of("Stand inside the selected permitted Garden plot");
        if(config.get(PestConfig.PEST_ACTION_TIMEOUT_MS)<100||config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT)<0||config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT)>8)return Optional.of("Invalid Garden timing/hotbar bounds");
        if(!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Start with an empty cursor");
        if(config.get(PestConfig.PEST_ACTION_CONTEST_ONLY)&&!GardenContestSignal.active(sidebar()))return Optional.of("Active Jacob's Contest is required");return Optional.empty();
    }
    @Override public final Optional<String> preStartCheck(){try{var common=commonReady();return common.isPresent()?common:ready();}catch(RuntimeException bad){return Optional.of(bad.getMessage());}}
    @Override public final void onEnable(){
        ModuleManager.OwnEnableReceipt ownEnable=null;
        try{var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();if(root!=null)ownEnable=root.currentOwnEnableReceipt(this).orElse(null);}
        catch(RuntimeException unavailable){} // An unknown observation grants nothing; ordinary defaults remain unchanged.
        childRestartPreparation(ownEnable);
        if(normalSourceVersion==Long.MAX_VALUE)normalSourceExhausted=true;else normalSourceVersion++;
        issuedNormalCompletion=null;
        if(!lifecycleStopAllowed()){phase=Phase.FAILED;reason="MISMATCH: prior Garden transport retirement incomplete";return;}
        if(!childInitializationHandled()){travel.stop();walking=false;release();}else if(!lifecycleStopAllowed())return;
        owner=client.player;level=client.level;originalSlot=owner==null?-1:owner.getInventory().getSelectedSlot();ownedMenu=null;
        originalConfig=signature();last=now();deadline=last+config.get(PestConfig.PEST_ACTION_TIMEOUT_MS);phase=Phase.IDLE;
        originalConfigEpochs=configurationEpochs();
        var invalid=preStartCheck();if(invalid.isPresent()){finish(Phase.FAILED,invalid.get());return;}try{begin();}catch(RuntimeException failed){finish(Phase.FAILED,failed.getMessage());}
    }
    @Override public final void onTick(){
        if(!isEnabled()||!lifecycleStopAllowed())return;
        long time=now();if(time<last||time>=deadline){finish(Phase.FAILED,"Garden request timed out; no automatic retry");return;}last=time;
        if(client.player!=owner||client.level!=level||owner==null||!owner.isAlive()||!optedIn()||!originalConfig.equals(signature())
                ||!GardenPlotBounds.contains(plot(),owner.getX(),owner.getZ())||(config.get(PestConfig.PEST_ACTION_CONTEST_ONLY)&&!GardenContestSignal.active(sidebar()))){finish(Phase.CANCELLED,"Player/world, plot, contest or settings changed");return;}
        try{if(!childTickPrepared())return;advance();}catch(RuntimeException failed){finish(Phase.FAILED,"Garden action failed: "+failed.getMessage());}
    }
    protected void finish(Phase terminal,String text){phase=terminal;reason=text==null?"Garden action failed":text;
        if(isEnabled()&&lifecycleStopAllowed()){
            // Only this actual COMPLETE invocation can produce an enabled normal-completion receipt.
            var completion=terminal==Phase.COMPLETE?issueNormalCompletion():null;
            childBeforeOwnedDisable(completion);
            if(isEnabled()&&lifecycleStopAllowed()){
                setEnabled(false);
                // Reached only after real disable/Core posthook/log completed normally; no effects in this read-only callback.
                if(completion!=null)childOwnedDisableNormalReturned(completion);
            }
        }}
    @Override public void onDisable(){
        if(childCleanupHandled())return;
        if(!lifecycleCleanupAllowed()){owner=null;level=null;ownedMenu=null;menus.clear();walking=false;return;}
        if(phase!=Phase.COMPLETE&&phase!=Phase.FAILED&&phase!=Phase.CANCELLED){phase=Phase.CANCELLED;reason="Manual stop";}
        try{if(client.player==owner&&client.level==level&&owner!=null&&owner.containerMenu==ownedMenu)owner.closeContainer();}
        finally{try{travel.stop();}finally{try{lease.restore();}finally{release();if(client.player==owner&&client.level==level&&originalSlot>=0)services.inventory().selectSlot(originalSlot);owner=null;level=null;ownedMenu=null;menus.clear();}}}
    }
    protected void release(){try{services.rotation().cancelRotation();}finally{services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(client.options!=null){dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyAttack,false);client.options.keyUp.setDown(false);client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);client.options.keyRight.setDown(false);client.options.keyJump.setDown(false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyShift,false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keySprint,false);}}}
    protected MenuSnapshot menu(){return menus.read();}
    protected void own(MenuSnapshot m){if(m!=null&&owner!=null&&owner.containerMenu.containerId==m.id())ownedMenu=owner.containerMenu;}
    protected void closeOwned(){if(owner!=null&&client.player==owner&&owner.containerMenu==ownedMenu){owner.closeContainer();ownedMenu=null;menus.clear();}}
    protected static MenuSnapshot.Slot button(MenuSnapshot m,String name){if(m==null)return null;var list=m.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(name)).toList();return list.size()==1?list.getFirst():null;}
    protected boolean approach(Entity target){
        if(target==null||client.level.getEntity(target.getId())!=target||!target.isAlive()||client.gui.screen()!=null)return false;
        if(owner.distanceTo(target)>2.8||!owner.hasLineOfSight(target)){
            if(!walking){var p=target.blockPosition();for(int[] off:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                if(travel.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+off[0],p.getY(),p.getZ()+off[1])),.35,lease.owned()?SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL:SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){walking=true;break;}
                if(!walking){finish(Phase.FAILED,"No loaded safe path to Garden entity");return false;}}
            var status=travel.tick();if(status==McTravelNavigator.State.FAILED){finish(Phase.FAILED,"Garden approach path blocked");return false;}if(status==McTravelNavigator.State.ARRIVED)walking=false;return false;
        }
        travel.stop();walking=false;return aim(target);
    }
    protected boolean aim(Entity target){var delta=target.getBoundingBox().getCenter().subtract(owner.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));services.rotation().track(yaw,pitch);
        return Math.abs(dev.veltraph.client.navigation.WalkController.wrap(owner.getYRot()-yaw))<2&&Math.abs(owner.getXRot()-pitch)<2;}
    protected boolean interact(Entity e){if(!approach(e)||!(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)||hit.getEntity()!=e)return false;client.gameMode.interact(owner,e,hit,InteractionHand.MAIN_HAND);return true;}
    private boolean walking;
    /** Read-only freeze of every original Pest key; actual final native stores refuse unsupported epochs. */
    private Map<ConfigKey<?>,Long> configurationEpochs(){
        if(!(config instanceof MapConfigService||config instanceof JsonConfigService))return null;
        var result=new HashMap<ConfigKey<?>,Long>();try{for(var field:PestConfig.class.getFields())if(field.get(null) instanceof ConfigKey<?> key){
            var revision=config.revision(key);if(revision.isEmpty()||revision.getAsLong()<0)return null;result.put(key,revision.getAsLong());
        }}catch(IllegalAccessException unavailable){return null;}return Map.copyOf(result);
    }
    private Map<String,String> signature(){var result=new TreeMap<String,String>();try{for(var field:PestConfig.class.getFields())if(field.get(null) instanceof ConfigKey<?> key)result.put(key.name(),String.valueOf(config.get(key)));}catch(IllegalAccessException bad){throw new IllegalStateException(bad);}return Map.copyOf(result);}
    public Phase workflowPhase(){return phase;}
    @Override public String diagnostics(){return phase+": "+reason;}
    @Override public void onServerMessage(String line){if(isEnabled()&&line!=null){String clean=LocationText.stripColorCodes(line);
        if(clean.equals("The server is shutting down!")||clean.contains("please slow down")||clean.equals("You don't have enough Copper!")||clean.equals("You already have this effect active!"))finish(Phase.FAILED,"Server rejected Garden action: "+clean);}}
}
