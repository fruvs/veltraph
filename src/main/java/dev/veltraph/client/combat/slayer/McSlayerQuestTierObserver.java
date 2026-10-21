package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Private observation of actual returned choice, click submission and normally applied server evidence.
 * No clicks/messages/quest transitions, runtime downloads or synthesized acknowledgments are performed here. */
public final class McSlayerQuestTierObserver {
    private static McSlayerQuestTierObserver active;
    private final Minecraft client;private final ConfigService config;private final SlayerModule parent;
    private final SlayerQuestController controller;
    private long generation,serial;private boolean exhausted,observingAction;
    private Scope scope;private SlayerQuestTierSequence sequence;private ActionWindow window;private NativeQuestTier proof;
    public McSlayerQuestTierObserver(Minecraft client,ConfigService config,SlayerModule parent,SlayerQuestController controller){
        this.client=client;this.config=config;this.parent=parent;this.controller=controller;
    }
    private record Setting(Object value,long revision) {}
    private record Scope(Object player,Object world,ClientPacketListener listener,Object gameMode,
            SlayerSettings settings,ModuleManager registry,ModuleManager.LifecycleSnapshot lifecycle,
            Map<ConfigKey<?>,Setting> configSnapshot) {}
    /** This actual native receipt cannot be constructed or adopted from a journal fact or a public message. */
    public static final class NativeQuestTier {
        private final McSlayerQuestTierObserver issuer;private final Scope scope;
        private final SlayerQuestTierSequence sequence;private final SlayerQuestTierSequence.Fact fact;private final long generation,stateVersion;
        private NativeQuestTier(McSlayerQuestTierObserver issuer,Scope scope,SlayerQuestTierSequence sequence,long generation,long stateVersion){
            this.issuer=issuer;this.scope=scope;this.sequence=sequence;this.generation=generation;this.stateVersion=stateVersion;
            fact=sequence.fact().orElseThrow();}
        public String declaredBossLabel(){return fact.declaredBoss();}
        public String nativeChoiceMenuTitle(){return fact.nativeChoiceMenuTitle();}
        public String nativeTierLabel(){return fact.nativeTierLabel();}
        public int actualTier(){return fact.actualTier();}
        /** Client observation generation, never a server-provided quest nonce. */
        public long session(){return generation;}
        public boolean current(){return issuer.currentProof(this);}
        /** Observation-only trusted final leaf; role/provider callbacks have already run at the effect boundary. */
        public boolean leafCurrent(){return issuer.proofLeafCurrent(this);}
        public boolean belongsTo(SlayerModule owner){return issuer.parent==owner&&current();}
        /** Trusted observation-only identity/private-scope leaf; it invokes no role/provider callback. */
        public boolean belongsToLeaf(SlayerModule owner){return issuer.parent==owner&&leafCurrent();}
    }
    /** Persistent identity tags are copied independently of mutable native ItemStack/component aliases. */
    private record RowWitness(AbstractContainerScreen<?> screen,AbstractContainerMenu menu,net.minecraft.world.inventory.Slot slot,
            String title,int state,NativeStackIdentity.Snapshot row,NativeStackIdentity.Snapshot cursor) {}
    public static final class ActionWindow {
        private final McSlayerQuestTierObserver issuer;private final Scope scope;private final SlayerQuestTierSequence sequence;
        private final SlayerQuestController.ReturnedAction action;private final AbstractContainerMenu menu;
        private final long generation;private boolean submitted;private long submittedSerial;
        private ActionWindow(McSlayerQuestTierObserver issuer,SlayerQuestController.ReturnedAction action,AbstractContainerMenu menu){
            this.issuer=issuer;scope=issuer.scope;sequence=issuer.sequence;this.action=action;this.menu=menu;generation=issuer.generation;}
    }
    public static final class SendInvocation {
        private final ActionWindow window;private final Connection transport;private final ServerboundContainerClickPacket packet;
        private final boolean matches;
        private SendInvocation(ActionWindow window,Connection transport,ServerboundContainerClickPacket packet,boolean matches){
            this.window=window;this.transport=transport;this.packet=packet;this.matches=matches;}
    }
    public static final class ReceiveInvocation {
        private final McSlayerQuestTierObserver issuer;private final Scope scope;private final SlayerQuestTierSequence sequence;
        private final long generation;private final ClientPacketListener listener;private final Packet<?> packet;
        private final AbstractContainerMenu menu;private final List<ItemStack> payload,beforeStacks;private final ItemStack cursor;private final int beforeMenuState;
        private final List<NativeStackIdentity.Snapshot> frozenPayload;private final NativeStackIdentity.Snapshot frozenCursor;
        private final SlayerQuestController.State before;private final long beforeStateVersion;private final String chatRaw;
        private ReceiveInvocation(McSlayerQuestTierObserver issuer,Scope captured,SlayerQuestTierSequence journal,long generation,
                ClientPacketListener listener,Packet<?> packet,AbstractContainerMenu menu,List<ItemStack> payload,ItemStack cursor,
                SlayerQuestController.State before,long beforeStateVersion){
            this.issuer=issuer;scope=captured;sequence=journal;this.generation=generation;
            this.listener=listener;this.packet=packet;this.menu=menu;this.payload=payload;this.cursor=cursor;this.before=before;this.beforeStateVersion=beforeStateVersion;
            chatRaw=packet instanceof ClientboundSystemChatPacket chat?chat.content().getString():null;
            beforeMenuState=menu==null?-1:menu.getStateId();
            beforeStacks=menu==null?List.of():menu.slots.stream().map(slot->slot.getItem().copy()).toList();
            var registry=((net.minecraft.client.multiplayer.ClientLevel)captured.world).registryAccess();
            frozenPayload=payload.stream().map(item->NativeStackIdentity.capture(item,registry)).toList();
            frozenCursor=NativeStackIdentity.capture(cursor,registry);}

    }
    public static final class BoundaryInvocation {
        private final McSlayerQuestTierObserver issuer;private final SlayerQuestTierSequence oldSequence;private final long generation;
        private BoundaryInvocation(McSlayerQuestTierObserver issuer){this.issuer=issuer;oldSequence=issuer.sequence;generation=issuer.generation;}
    }
    /** Actual respawn handler entry closes old-world evidence even if references later return to equal values. */
    public static BoundaryInvocation beginWorldBoundary(ClientPacketListener listener){try{
        var observer=active;
        return observer!=null&&observer.client.isSameThread()&&observer.scope!=null&&observer.scope.listener==listener
                ?new BoundaryInvocation(observer):null;
        }catch(Throwable unavailable){return null;}}
    public static void finishWorldBoundary(BoundaryInvocation call){try{
        if(call==null)return;
        if(call.oldSequence!=null)call.oldSequence.invalidate();
        if(call.issuer.generation==call.generation&&call.issuer.sequence==call.oldSequence)call.issuer.retire();
        }catch(Throwable ignored){}}
    public String diagnostics(){return "Native tier observation "+(sequence==null?"UNKNOWN":sequence.phase())+" generation="+generation+" submittedSerial="+serial;}
    public void bind(){try{retire();if(client.isSameThread()&&parent.isEnabled())active=this;}catch(Throwable ignored){}}
    public void close(){try{retire();if(active==this)active=null;}catch(Throwable ignored){}}
    private void retire(){if(sequence!=null)sequence.invalidate();proof=null;window=null;scope=null;sequence=null;
        if(generation==Long.MAX_VALUE)exhausted=true;else generation++;}
    public void refresh(){var captured=scope;var old=sequence;long wanted=generation;
        try{if(captured!=null&&!currentScope(captured))refuseCaptured(wanted,captured,old);}
        catch(Throwable unavailable){refuseCaptured(wanted,captured,old);}}
    private void refuseCaptured(long wanted,Scope captured,SlayerQuestTierSequence old){
        if(generation==wanted&&scope==captured&&sequence==old)retire();else if(old!=null)old.invalidate();
    }
    public Optional<NativeQuestTier> evidence(){try{return proof!=null&&currentProof(proof)?Optional.of(proof):Optional.empty();}
        catch(Throwable unavailable){return Optional.empty();}}
    /** Public/deferred notifications can retire evidence, but can never establish it. */
    public void messageObserved(String raw){try{String text=MenuSnapshot.clean(raw);
        if(text.lines().anyMatch(line->line.startsWith("You already have an active")||line.equals("SLAYER QUEST COMPLETE!")
                ||line.equals("Your Slayer Quest has been cancelled!")||line.equals("You cannot afford to start this quest!")))retire();
        }catch(Throwable unavailable){retire();}}
    public ActionWindow beforeAction(SlayerQuestController.Action actual,MenuSnapshot menu,SlayerSettings settings){
        if(observingAction)return null;observingAction=true;
        long wanted=generation;SlayerQuestTierSequence capturedSequence=sequence;Scope capturedScope=scope;
        try {
            var returned=controller.returnedAction(actual).orElse(null);
            if(active!=this||exhausted||!client.isSameThread()||returned==null||returned.menu()!=menu||returned.settings()!=settings
                    ||!actionPhaseCurrent(returned))return null;
            if(returned.role()==SlayerQuestController.ReturnedAction.Role.OTHER){if(sequence!=null)retire();return null;}
            if(returned.role()==SlayerQuestController.ReturnedAction.Role.TIER){
                retire();wanted=generation;if(exhausted)return null;
                var observed=capture(settings);if(active!=this||generation!=wanted||observed==null)return null;
                scope=observed;sequence=new SlayerQuestTierSequence(returned,client.player.containerMenu,System.nanoTime());
                capturedScope=scope;capturedSequence=sequence;
            } else {
                if(capturedSequence==null||capturedScope==null||!currentScope(capturedScope)
                        ||!capturedSequence.confirmChoice(returned,client.player.containerMenu,System.nanoTime()))return null;
            }
            if(generation!=wanted||scope!=capturedScope||sequence!=capturedSequence)return null;
            if(window!=null||!currentScope(capturedScope)||!returned.current()||menu.id()!=client.player.containerMenu.containerId
                    ||menu.revision()!=client.player.containerMenu.getStateId()||!client.player.containerMenu.getCarried().isEmpty()
                    ||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu
                    ||!MenuSnapshot.clean(screen.getTitle().getString()).equals(menu.title())){refuseCaptured(wanted,capturedScope,capturedSequence);return null;}
            var expected=menu.slots().stream().filter(slot->slot.id()==actual.slotId()).findFirst().orElse(null);
            var nativeMenu=client.player.containerMenu;
            if(expected==null||actual.slotId()<0||actual.slotId()>=nativeMenu.slots.size())return null;
            var nativeSlot=nativeMenu.getSlot(actual.slotId());var stack=nativeSlot.getItem();
            if(nativeSlot.container==client.player.getInventory()||stack.isEmpty()||stack.getCount()!=expected.count()
                    ||!MenuSnapshot.clean(stack.getHoverName().getString()).equals(actual.expectedName())
                    ||!McMenuAccess.componentIdentity(stack,client.level.registryAccess()).equals(expected.componentIdentity())){
                refuseCaptured(wanted,capturedScope,capturedSequence);return null;}
            var rowWitness=new RowWitness(screen,nativeMenu,nativeSlot,menu.title(),menu.revision(),
                    NativeStackIdentity.capture(stack,client.level.registryAccess()),
                    NativeStackIdentity.capture(nativeMenu.getCarried(),client.level.registryAccess()));
            // Last public selector callback precedes the complete native row/cursor/screen/state leaf below.
            if(!currentScope(capturedScope)||!returned.current()){
                refuseCaptured(wanted,capturedScope,capturedSequence);return null;}
            if(!rowLeafCurrent(rowWitness,returned)||!scopeLeafCurrent(capturedScope)
                    ||active!=this||generation!=wanted||scope!=capturedScope||sequence!=capturedSequence||!actionPhaseCurrent(returned)){
                refuseCaptured(wanted,capturedScope,capturedSequence);return null;}
            window=new ActionWindow(this,returned,nativeMenu);return window;
        }catch(Throwable unavailable){if(generation==wanted)retire();else if(capturedSequence!=null)capturedSequence.invalidate();return null;}
        finally{observingAction=false;}
    }
    public void afterAction(ActionWindow handed,boolean actualClickReturned){try{
        if(handed==null)return;
        if(handed.issuer!=this||window!=handed||generation!=handed.generation||sequence!=handed.sequence||scope!=handed.scope)return;
        window=null;
        if(!actualClickReturned||!handed.submitted||!currentScope(handed.scope)||!handed.action.current()||!actionPhaseCurrent(handed.action)
                ||generation!=handed.generation||scope!=handed.scope||sequence!=handed.sequence){
            handed.sequence.invalidate();
            if(generation==handed.generation&&scope==handed.scope&&sequence==handed.sequence)proof=null;
            return;}
        if(handed.action.role()==SlayerQuestController.ReturnedAction.Role.TIER)
            handed.sequence.tierSubmitted(handed.action,handed.submittedSerial,true,System.nanoTime());
        else handed.sequence.confirmSubmitted(handed.action,handed.submittedSerial,true,System.nanoTime());
        }catch(Throwable unavailable){if(handed!=null)handed.sequence.invalidate();}}
    public static SendInvocation beginSend(Connection transport,Packet<?> packet){try{
        var observer=active;var handed=observer==null?null:observer.window;
        if(handed==null||!observer.client.isSameThread()||!(packet instanceof ServerboundContainerClickPacket click))return null;
        var action=handed.action.action();
        boolean matches=transport==handed.scope.listener.getConnection()&&click.containerId()==action.menuId()
                &&click.stateId()==handed.action.menu().revision()&&click.slotNum()==action.slotId()
                &&click.buttonNum()==0&&click.containerInput()==ContainerInput.PICKUP;
        return new SendInvocation(handed,transport,click,matches);
        }catch(Throwable unavailable){return null;}}
    public static void finishSend(SendInvocation call,boolean normallyReturned){try{
        if(call==null)return;var handed=call.window;var observer=handed.issuer;
        if(active!=observer||observer.window!=handed||observer.generation!=handed.generation
                ||observer.scope!=handed.scope||observer.sequence!=handed.sequence){handed.sequence.invalidate();return;}
        if(!normallyReturned||!call.matches||handed.submitted||!observer.currentScope(handed.scope)
                ||!handed.action.current()||!observer.actionPhaseCurrent(handed.action)||observer.client.player.containerMenu!=handed.menu
                ||call.transport!=handed.scope.listener.getConnection()) {handed.sequence.invalidate();return;}
        if(active!=observer||observer.window!=handed||observer.generation!=handed.generation
                ||observer.scope!=handed.scope||observer.sequence!=handed.sequence){handed.sequence.invalidate();return;}
        long event=observer.nextSerial();if(event<0){handed.sequence.invalidate();return;}
        handed.submitted=true;handed.submittedSerial=event;
        }catch(Throwable unavailable){if(call!=null)call.window.sequence.invalidate();}}
    public static ReceiveInvocation beginReceive(ClientPacketListener listener,Packet<?> packet){try{
        var observer=active;if(observer==null||!observer.client.isSameThread())return null;
        var captured=observer.scope;var journal=observer.sequence;long generation=observer.generation;
        var before=observer.controller.state();long version=observer.controller.observedStateVersion();
        if(captured==null||journal==null||listener!=captured.listener||version<0)return null;
        var menu=observer.client.player==null?null:observer.client.player.containerMenu;
        // Parent/action/session references are captured before the input-owner callback runs.
        if(!observer.currentScope(captured)||active!=observer||observer.scope!=captured||observer.sequence!=journal
                ||observer.generation!=generation||observer.controller.state()!=before||observer.controller.observedStateVersion()!=version)return null;
        ReceiveInvocation call;
        if(packet instanceof ClientboundContainerSetContentPacket contents
                &&journal.phase()==SlayerQuestTierSequence.Phase.TIER_SUBMITTED&&before==SlayerQuestController.State.CONFIRM_START){
            if(menu==null||observer.client.player.containerMenu!=menu||menu.containerId!=contents.containerId()
                    ||contents.items().isEmpty()||contents.items().size()>512||contents.items().size()!=menu.slots.size())return null;
            call=new ReceiveInvocation(observer,captured,journal,generation,listener,packet,menu,
                    contents.items().stream().map(ItemStack::copy).toList(),contents.carriedItem().copy(),before,version);
        }else if(packet instanceof ClientboundSystemChatPacket chat&&!chat.overlay()
                &&journal.phase()==SlayerQuestTierSequence.Phase.CONFIRM_SUBMITTED&&before==SlayerQuestController.State.WAIT_START
                &&SlayerQuestTierSequence.uniqueStartedHeader(chat.content().getString()))
            call=new ReceiveInvocation(observer,captured,journal,generation,listener,packet,null,List.of(),ItemStack.EMPTY,before,version);
        else return null;
        return observer.scopeLeafCurrent(captured)&&active==observer&&observer.scope==captured&&observer.sequence==journal&&observer.generation==generation
                &&observer.controller.state()==before&&observer.controller.observedStateVersion()==version?call:null;
        }catch(Throwable unavailable){return null;}}
    public static void finishReceive(ReceiveInvocation call,boolean normallyReturned){try{
        if(call==null)return;var observer=call.issuer;
        if(active!=observer||observer.scope!=call.scope||observer.sequence!=call.sequence||observer.generation!=call.generation){call.sequence.invalidate();return;}
        if(!normallyReturned||call.listener!=call.scope.listener||!observer.currentScope(call.scope)) {call.sequence.invalidate();return;}
        if(call.packet instanceof ClientboundContainerSetContentPacket contents){
            var menu=observer.client.player.containerMenu;
            if(menu!=call.menu||menu.containerId!=contents.containerId()||menu.getStateId()!=contents.stateId()
                    ||menu.slots.size()!=call.payload.size()||!ItemStack.matches(menu.getCarried(),call.cursor)
                    ||!menu.getCarried().isEmpty()||!(observer.client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                    ||screen.getMenu()!=menu){call.sequence.invalidate();return;}
            boolean changed=call.beforeMenuState!=menu.getStateId();
            for(int slot=0;slot<call.payload.size();slot++){
                if(!ItemStack.matches(call.payload.get(slot),menu.getSlot(slot).getItem())){call.sequence.invalidate();return;}
                changed|=!ItemStack.matches(call.beforeStacks.get(slot),menu.getSlot(slot).getItem());
            }
            if(!changed){call.sequence.invalidate();return;} // Equal/predicted/duplicate data alone cannot attest an application.
            var echoed=new ArrayList<String>();
            for(var item:call.payload){
                if(echoed.size()>=4096){call.sequence.invalidate();return;}echoed.add(item.getHoverName().getString());
                var lore=item.get(net.minecraft.core.component.DataComponents.LORE);
                if(lore!=null)for(var line:lore.lines()){
                    if(echoed.size()>=4096){call.sequence.invalidate();return;}echoed.add(line.getString());
                }
            }
            if(!SlayerQuestTierSequence.confirmationEcho(echoed,call.sequence.tierChoice())||!observer.currentScope(call.scope)
                    ||call.before!=SlayerQuestController.State.CONFIRM_START||observer.controller.state()!=call.before
                    ||call.beforeStateVersion<0||observer.controller.observedStateVersion()!=call.beforeStateVersion){call.sequence.invalidate();return;}
            String observedTitle=MenuSnapshot.clean(screen.getTitle().getString());
            if(!observer.contentLeafCurrent(call,screen,observedTitle)||!observer.scopeLeafCurrent(call.scope)
                    ||active!=observer||observer.scope!=call.scope||observer.sequence!=call.sequence||observer.generation!=call.generation
                    ||observer.controller.state()!=call.before||observer.controller.observedStateVersion()!=call.beforeStateVersion){call.sequence.invalidate();return;}
            long event=observer.nextSerial();if(event<0){call.sequence.invalidate();return;}
            call.sequence.menuApplied(menu,contents.containerId(),contents.stateId(),observedTitle,event,true,System.nanoTime());
        }else if(call.packet instanceof ClientboundSystemChatPacket chat){
            long event=observer.nextSerial();if(event<0){call.sequence.invalidate();return;}
            call.sequence.receivedStarted(call.chatRaw,event,call.before,observer.controller.state(),true,System.nanoTime());
            if(call.sequence.fact().isPresent()&&observer.currentScope(call.scope)
                    &&observer.sequence==call.sequence&&observer.scope==call.scope&&observer.generation==call.generation
                    &&call.beforeStateVersion>=0&&call.beforeStateVersion<Long.MAX_VALUE
                    &&observer.controller.observedStateVersion()==call.beforeStateVersion+1)
                observer.proof=new NativeQuestTier(observer,call.scope,call.sequence,call.generation,call.beforeStateVersion+1);
            else call.sequence.invalidate();
        }
        }catch(Throwable unavailable){if(call!=null)call.sequence.invalidate();}}
    /** Trusted fixed native equality/codecs only; no public input-owner/provider callback runs here. */
    private boolean rowLeafCurrent(RowWitness read,SlayerQuestController.ReturnedAction action){
        var p=client.player;
        if(p==null||client.level==null||client.gui.screen()!=read.screen||read.screen.getMenu()!=read.menu
                ||p.containerMenu!=read.menu||read.menu.containerId!=action.action().menuId()||read.menu.getStateId()!=read.state
                ||!MenuSnapshot.clean(read.screen.getTitle().getString()).equals(read.title)
                ||action.action().slotId()<0||action.action().slotId()>=read.menu.slots.size()
                ||read.menu.getSlot(action.action().slotId())!=read.slot||read.slot.container==p.getInventory()
                ||!read.menu.getCarried().isEmpty())return false;
        var registry=client.level.registryAccess();
        return read.cursor.matches(read.menu.getCarried(),registry)&&read.row.matches(read.slot.getItem(),registry)
                &&read.slot.getItem().getCount()>0
                &&MenuSnapshot.clean(read.slot.getItem().getHoverName().getString()).equals(action.action().expectedName());
    }
    /** Exact full-payload revalidation follows all selector callbacks, including same-reference mutations. */
    private boolean contentLeafCurrent(ReceiveInvocation call,AbstractContainerScreen<?> expectedScreen,String title){
        if(client.player==null||client.level==null||client.gui.screen()!=expectedScreen||expectedScreen.getMenu()!=call.menu
                ||client.player.containerMenu!=call.menu||!(call.packet instanceof ClientboundContainerSetContentPacket contents)
                ||call.menu.containerId!=contents.containerId()||call.menu.getStateId()!=contents.stateId()
                ||call.menu.slots.size()!=call.frozenPayload.size()||!call.menu.getCarried().isEmpty()
                ||!MenuSnapshot.clean(expectedScreen.getTitle().getString()).equals(title)
                ||!call.scope.settings.confirmTitle().equals(title))return false;
        var registry=client.level.registryAccess();
        if(!call.frozenCursor.matches(call.menu.getCarried(),registry))return false;
        for(int slot=0;slot<call.frozenPayload.size();slot++)
            if(!call.frozenPayload.get(slot).matches(call.menu.getSlot(slot).getItem(),registry))return false;
        return true;
    }
    private boolean actionPhaseCurrent(SlayerQuestController.ReturnedAction action){
        if(action==null||action.stateVersion()<0||controller.observedStateVersion()!=action.stateVersion())return false;
        return action.role()==SlayerQuestController.ReturnedAction.Role.TIER?controller.state()==SlayerQuestController.State.CONFIRM_START
                :action.role()==SlayerQuestController.ReturnedAction.Role.CONFIRM?controller.state()==SlayerQuestController.State.WAIT_START:true;
    }
    private long nextSerial(){if(serial==Long.MAX_VALUE){exhausted=true;return -1;}return ++serial;}
    private Map<ConfigKey<?>,Setting> settings(){
        if(!(config instanceof MapConfigService||config instanceof JsonConfigService))return null;
        var result=new LinkedHashMap<ConfigKey<?>,Setting>();
        for(var key:PersistableKeys.collect(List.of(SlayerConfig.class,CombatConfig.class,TaraConfig.class))){
            var epoch=config.revision(key);if(epoch.isEmpty()||epoch.getAsLong()<0)return null;
            result.put(key,new Setting(config.get(key),epoch.getAsLong()));}
        return Collections.unmodifiableMap(result);
    }
    private Scope capture(SlayerSettings settings){
        var registry=VeltraphBootstrap.moduleManager();if(registry==null)return null;
        var lifecycle=registry.lifecycleSnapshot();if(!lifecycle.known())return null;
        var p=client.player;var w=client.level;var listener=client.getConnection();var gameMode=client.gameMode;
        var saved=settings();if(saved==null||p==null||w==null||listener==null||gameMode==null)return null;
        var observed=new Scope(p,w,listener,gameMode,settings,registry,lifecycle,saved);
        return currentScope(observed)?observed:null;
    }
    private boolean currentScope(Scope observed){
        if(observed==null||exhausted||active!=this||!client.isSameThread()
                ||observed.registry.get(SlayerModule.ID).orElse(null)!=parent
                ||observed.registry.inputOwner().orElse(null)!=parent)return false;
        return scopeLeafCurrent(observed);
    }
    /** Known final-store reads and fixed parent/native fields only; no role/provider supplier runs here. */
    private boolean scopeLeafCurrent(Scope observed){
        if(observed==null||exhausted||active!=this||!client.isSameThread()
                ||!observed.configSnapshot.equals(settings()))return false;
        var p=client.player;
        return p!=null&&p==observed.player&&client.level==observed.world&&client.getConnection()==observed.listener
                &&client.gameMode==observed.gameMode&&p.isAlive()
                &&VeltraphBootstrap.moduleManager()==observed.registry&&VeltraphBootstrap.config()==config
                &&parent.matchesQuestTierController(controller)
                &&parent.matchesQuestTierNativeContext(observed.settings,observed.player,observed.world,observed.listener)
                &&observed.registry.lifecycleCurrent(observed.lifecycle);
    }
    private boolean currentProof(NativeQuestTier actual){
        if(actual==null||actual.issuer!=this||!currentScope(actual.scope))return false;
        return proofLeafCurrent(actual);
    }
    private boolean proofLeafCurrent(NativeQuestTier actual){
        if(actual==null||actual.issuer!=this||!scopeLeafCurrent(actual.scope))return false;
        return proof==actual&&actual.generation==generation&&scope==actual.scope&&sequence==actual.sequence
                &&controller.state()==SlayerQuestController.State.ACTIVE&&controller.observedStateVersion()==actual.stateVersion
                &&actual.sequence.confirmationChoice()!=null&&actual.sequence.confirmationChoice().current()
                &&actual.sequence.fact().orElse(null)==actual.fact;
    }
}
