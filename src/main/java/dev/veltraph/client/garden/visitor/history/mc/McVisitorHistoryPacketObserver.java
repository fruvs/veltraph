package dev.veltraph.client.garden.visitor.history.mc;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.movement.device.DeviceInvocationIdentity;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallCarrier;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Passive native container journal. Raw capsules confer no history, page, offer, spending or profile authority. */
public final class McVisitorHistoryPacketObserver {
    private static McVisitorHistoryPacketObserver active;
    private final Minecraft client;private final GameContextService location;
    private LocalPlayer player;private ClientLevel world;private ClientPacketListener listener;
    private long epoch,sequence,sends,contents,opens,closes,slots;private boolean exhausted;
    private RequestReceipt lastRequest;
    private final LinkedHashMap<Integer,ContentReceipt> lastContents=new LinkedHashMap<>();
    private OwnedMenuInput ownedInput;private GuiInputInvocation activeGuiInput;private Object stateObservationLease;private long nativeSendEntries;private long appliedSequence;private final ArrayDeque<AppliedStateReceipt> appliedStates=new ArrayDeque<>();
    private OpenReceipt lastOpen;private CloseReceipt lastClose;private SlotReceipt lastSlot;
    private McVisitorHistoryPacketObserver(Minecraft c,GameContextService location){this.client=Objects.requireNonNull(c);this.location=Objects.requireNonNull(location);}
    public static McVisitorHistoryPacketObserver install(Minecraft c,GameContextService location){
        if(c==null||!c.isSameThread())throw new IllegalStateException("Native history observation requires the client thread");
        if(active!=null){active.clear();active.player=null;active.world=null;active.listener=null;}
        var observer=new McVisitorHistoryPacketObserver(c,location);active=observer;observer.refresh();return observer;
    }
    public void retire(){if(client.isSameThread()&&active==this){active=null;clear();player=null;world=null;listener=null;}}
    /** Root calls this on ordinary client ticks, so logout clears retained world/item observations. */
    public void observeLifecycle(){if(client.isSameThread())refresh();}
    private void clear(){lastRequest=null;lastContents.clear();appliedStates.clear();stateObservationLease=null;ownedInput=null;activeGuiInput=null;lastOpen=null;lastClose=null;lastSlot=null;}
    private boolean refresh(){
        if(active!=this||!client.isSameThread()||exhausted)return false;
        if(player!=client.player||world!=client.level||listener!=client.getConnection()){
            player=client.player;world=client.level;listener=client.getConnection();clear();
            if(epoch==Long.MAX_VALUE){exhausted=true;return false;}epoch++;
        }
        return player!=null&&world!=null&&listener!=null&&player.level()==world&&client.gameMode!=null;
    }
    private boolean same(LocalPlayer p,ClientLevel w,Object l,long e){return active==this&&client.isSameThread()&&!exhausted
            &&player==p&&world==w&&listener==l&&epoch==e&&client.player==p&&client.level==w&&client.getConnection()==l;}
    private long next(){if(sequence==Long.MAX_VALUE){exhausted=true;clear();return -1;}return ++sequence;}
    /** Existing Root registration only. This accessor never installs/replaces a producer or asserts profile authority. */
    public static Optional<McVisitorHistoryPacketObserver> existing(Minecraft c){var o=active;return o!=null&&o.client==c&&o.refresh()?Optional.of(o):Optional.empty();}
    /** Finite observer lease only; cannot mint a native request or packet capsule. Foreign/replaced leases fail closed. */
    private Optional<Object> beginStateObservation(){if(!refresh()||stateObservationLease!=null)return Optional.empty();stateObservationLease=new Object();appliedStates.clear();return Optional.of(stateObservationLease);}
    private boolean stateObservationCurrent(Object lease){return refresh()&&lease!=null&&stateObservationLease==lease;}
    private void endStateObservation(Object lease){if(client.isSameThread()&&stateObservationLease==lease){stateObservationLease=null;appliedStates.clear();}}
    public long sentClickCount(){return refresh()?sends:-1;}
    public long appliedStateSequence(){return refresh()?appliedSequence:-1;}
    public Optional<RequestReceipt> latestNativeRequest(){return refresh()&&lastRequest!=null&&same(lastRequest.player,lastRequest.world,lastRequest.listener,lastRequest.epoch)?Optional.of(lastRequest):Optional.empty();}
    /** Unforgeable captured state handles; capture alone is not request/receipt authority. Persistent identities are never serialized by this API. */
    public static final class NativeState {
        private final McVisitorHistoryPacketObserver issuer;private final LocalPlayer player;private final ClientLevel world;private final ClientPacketListener listener;private final Connection transport;private final long epoch;
        private final McPestInventoryRestorationObserver.SelectionHistory slotHistory;private final AbstractContainerMenu menu;private final Object screen;private final String title;private final int revision,selected;
        private final List<NativeStackIdentity.Snapshot> cells,inventory,equipment;private final List<Boolean> emptyCells;private final NativeStackIdentity.Snapshot cursor;private final boolean cursorEmpty;
        private NativeState(McVisitorHistoryPacketObserver o){
            issuer=o;player=o.player;world=o.world;listener=o.listener;transport=listener.getConnection();epoch=o.epoch;menu=player.containerMenu;screen=o.client.gui.screen();
            title=screen instanceof AbstractContainerScreen<?> c&&c.getMenu()==menu?MenuSnapshot.clean(c.getTitle().getString()):"";revision=menu.getStateId();selected=player.getInventory().getSelectedSlot();slotHistory=McPestInventoryRestorationObserver.selectedSlotHistory(player.getInventory());
            var registry=world.registryAccess();var c=new ArrayList<NativeStackIdentity.Snapshot>();var empty=new ArrayList<Boolean>();for(var slot:menu.slots){var item=slot.getItem();c.add(NativeStackIdentity.capture(item,registry));empty.add(item.isEmpty());}cells=List.copyOf(c);emptyCells=List.copyOf(empty);
            var inv=new ArrayList<NativeStackIdentity.Snapshot>();for(int i=0;i<36;i++)inv.add(NativeStackIdentity.capture(player.getInventory().getItem(i),registry));inventory=List.copyOf(inv);
            var eq=new ArrayList<NativeStackIdentity.Snapshot>();for(var slot:net.minecraft.world.entity.EquipmentSlot.values())eq.add(NativeStackIdentity.capture(player.getItemBySlot(slot),registry));equipment=List.copyOf(eq);
            var carried=menu.getCarried();cursor=NativeStackIdentity.capture(carried,registry);cursorEmpty=carried.isEmpty();
        }
        public Object nativeMenu(){return menu;}public Object nativeScreen(){return screen;}public String title(){return title;}public int revision(){return revision;}public int selectedSlot(){return selected;}public boolean cursorEmpty(){return cursorEmpty;}
        public boolean sameInfrastructure(NativeState o){return o!=null&&slotHistory!=null&&o.slotHistory!=null&&slotHistory.current()&&o.slotHistory.current()&&issuer==o.issuer&&player==o.player&&world==o.world&&listener==o.listener&&transport==o.transport&&epoch==o.epoch&&selected==o.selected;}
        public boolean sameState(NativeState o){return sameInfrastructure(o)&&menu==o.menu&&screen==o.screen&&title.equals(o.title)&&revision==o.revision&&cells.equals(o.cells)&&inventory.equals(o.inventory)&&equipment.equals(o.equipment)&&cursor.equals(o.cursor);}
        public boolean isOwnedPickupPredictionFrom(NativeState before,int slot){
            if(!sameInfrastructure(before)||menu!=before.menu||screen!=before.screen||!title.equals(before.title)||revision!=before.revision||!before.cursorEmpty||!inventory.equals(before.inventory)||!equipment.equals(before.equipment)||cells.size()!=before.cells.size()||slot<0||slot>=cells.size())return false;
            if(sameState(before))return true; // Genuine normal native send may have a non-pickup slot; this never fabricates a prediction.
            if(before.emptyCells.get(slot)||!emptyCells.get(slot)||cursorEmpty||!cursor.equals(before.cells.get(slot)))return false;
            for(int i=0;i<cells.size();i++)if(i!=slot&&!cells.get(i).equals(before.cells.get(i)))return false;return true;
        }
    }
    private NativeState captureState(){try{return refresh()&&player.containerMenu!=null?new NativeState(this):null;}catch(RuntimeException unsupported){return null;}}
    private Optional<NativeState> currentNativeState(){return Optional.ofNullable(captureState());}
    /** Real normally-returning receive boundaries only; missing snapshots explicitly refuse pending authority. */
    public static final class AppliedStateReceipt {
        private final McVisitorHistoryPacketObserver issuer;private final long sequence;private final int kind;private final RequestReceipt request;
        private final NativeState before,after;private final ContentReceipt full;
        private AppliedStateReceipt(McVisitorHistoryPacketObserver o,ReceiveInvocation v,NativeState after,ContentReceipt full){issuer=o;sequence=++o.appliedSequence;kind=v.kind;request=v.request;before=v.beforeState;this.after=after;this.full=full;}
        public long sequence(){return sequence;}public int kind(){return kind;}public Optional<RequestReceipt> lastObservedRequest(){return Optional.ofNullable(request);}
        public Optional<NativeState> beforeState(){return Optional.ofNullable(before);}public Optional<NativeState> afterState(){return Optional.ofNullable(after);}public Optional<ContentReceipt> fullContent(){return Optional.ofNullable(full);}
    }
    private void recordApplied(ReceiveInvocation v,ContentReceipt full){
        if(stateObservationLease==null)return;if(appliedSequence==Long.MAX_VALUE){exhausted=true;clear();return;}var r=new AppliedStateReceipt(this,v,captureState(),full);appliedStates.addLast(r);while(appliedStates.size()>32)appliedStates.removeFirst();
    }
    private Optional<List<AppliedStateReceipt>> appliedStatesAfter(Object lease,long prior){
        if(!stateObservationCurrent(lease)||prior<0||prior>appliedSequence||prior<appliedSequence-appliedStates.size())return Optional.empty();
        return Optional.of(appliedStates.stream().filter(r->r.issuer==this&&r.sequence>prior).toList());
    }
    public enum OwnedInputStage { OWNED_PENDING, OWNED_REPLY_CLOSED_PENDING, NATIVE_FULL_CONTENT, INVALID }
    /** No public constructor, port Boolean or caller-provided receipt can mint an inspection. */
    public static final class OwnedInputInspection {
        private final OwnedMenuInput issuer;private final OwnedInputStage stage;private final ContentReceipt full;private final String reason;
        private OwnedInputInspection(OwnedMenuInput issuer,OwnedInputStage stage,ContentReceipt full,String reason){this.issuer=issuer;this.stage=stage;this.full=full;this.reason=reason;}
        public OwnedInputStage stage(){return stage;}public String reason(){return reason;}public Optional<ContentReceipt> fullContent(){return Optional.ofNullable(full);}
        public long sourceRequestSequence(){return issuer.request==null?-1:issuer.request.sequence;}public long sourceReplySequence(){return full==null?-1:full.sequence;}
    }
    /** Opaque finite observation of one real Root-owned input; original family deadline/ACK remain caller responsibilities. */
    public static final class OwnedMenuInput {
        private final McVisitorHistoryPacketObserver issuer;private final Object lease;private final NativeState before;private final String replyTitle;
        private final int slot,button;private final ContainerInput input;private final long entriesBefore,sendsBefore,applyBefore;
        private final Thread dispatchThread=Thread.currentThread();
        private final Object gameMode,options;private final ModuleManager root;private final Module owner;private final ModuleManager.LifecycleSnapshot lifecycle;private final Object store;
        private NativeState known;private RequestReceipt request;private ContentReceipt full;private long applied;private int upperStarted,upperReturned,sendEntered,sendReturned;private boolean sealed,failed,closed,replyClosedPending,replyOpened;
        private OwnedMenuInput(McVisitorHistoryPacketObserver o,Object lease,NativeState before,int slot,int button,ContainerInput input,String replyTitle){
            issuer=o;this.lease=lease;this.before=before;this.slot=slot;this.button=button;this.input=input;this.replyTitle=replyTitle;entriesBefore=o.nativeSendEntries;sendsBefore=o.sends;applyBefore=o.appliedSequence;applied=applyBefore;
            gameMode=o.client.gameMode;options=o.client.options;root=VeltraphBootstrap.moduleManager();owner=root==null?null:root.inputOwner().orElse(null);lifecycle=root==null?null:root.lifecycleSnapshot();store=VeltraphBootstrap.config();
        }
        /** Negative lifetime/foreign-entry veto for this already issued input; no provider, codec or source recapture. */
        public boolean dispatchLifetimeCurrent(){return Thread.currentThread()==dispatchThread&&!closed&&!failed&&!sealed&&active==issuer&&issuer.ownedInput==this&&issuer.stateObservationLease==lease&&!issuer.exhausted
            &&issuer.player==before.player&&issuer.world==before.world&&issuer.listener==before.listener&&issuer.epoch==before.epoch
            &&issuer.client.player==before.player&&issuer.client.level==before.world&&issuer.client.gameMode==gameMode&&issuer.client.options==options
            &&issuer.client.player!=null&&issuer.client.player.connection==before.listener&&issuer.client.player.containerMenu==before.menu
            &&before.slotHistory!=null&&before.slotHistory.storedStateCurrent()
            &&root!=null&&root==VeltraphBootstrap.moduleManager()&&store==VeltraphBootstrap.config()&&root.lifecycleCurrent(lifecycle)
            &&applied==applyBefore&&issuer.appliedSequence==applyBefore&&upperStarted==0&&upperReturned==0&&sendEntered==0&&sendReturned==0&&issuer.nativeSendEntries==entriesBefore&&issuer.sends==sendsBefore;}
        /** Callback-free leaf after Root providers: never invoke a source/provider to repair or rebase these captured facts. */
        private boolean nativeLeaf(){return !closed&&!failed&&active==issuer&&issuer.ownedInput==this&&issuer.stateObservationLease==lease&&!issuer.exhausted&&issuer.client.isSameThread()
            &&issuer.client.gameMode==gameMode&&issuer.client.options==options&&root==VeltraphBootstrap.moduleManager()&&store==VeltraphBootstrap.config()
            &&issuer.player==before.player&&issuer.world==before.world&&issuer.listener==before.listener&&issuer.epoch==before.epoch
            &&issuer.client.player==before.player&&issuer.client.level==before.world&&issuer.client.getConnection()==before.listener&&before.listener.getConnection()==before.transport
            &&before.slotHistory!=null&&before.slotHistory.current();}
        private boolean current(){
            if(!nativeLeaf()||root==null||owner==null||lifecycle==null||!lifecycle.known())return false;
            boolean role=owner.isEnabled()&&root.get(owner.id()).orElse(null)==owner&&root.inputOwner().orElse(null)==owner&&root.lifecycleCurrent(lifecycle);
            return role&&nativeLeaf();
        }
        public void sealAfterNativeCall(){
            if(sealed)return;sealed=true;var r=issuer.lastRequest;
            if(!current()||upperStarted!=1||upperReturned!=1||issuer.nativeSendEntries!=entriesBefore+1||sendEntered!=1||sendReturned!=1||issuer.sends!=sendsBefore+1||r==null||r.ownedLease!=lease||r.upper==null||r.upper.window!=this||!r.upper.completed||r.priorAppliedSequence!=applyBefore
                ||r.menu!=before.menu||r.revision!=before.revision||r.slot!=slot||r.button!=button||r.input!=input||r.predictedState==null||!r.predictedState.isOwnedPickupPredictionFrom(before,slot)){failed=true;return;}
            request=r;known=r.predictedState;
        }
        private OwnedInputInspection invalid(String reason){failed=true;return new OwnedInputInspection(this,OwnedInputStage.INVALID,null,reason);}
        private boolean replyProtectedState(NativeState state){return state.cursorEmpty&&state.inventory.equals(before.inventory)&&state.equipment.equals(before.equipment);}
        private boolean closedReplyState(NativeState state){return state.menu==before.player.inventoryMenu&&state.screen==null&&replyProtectedState(state);}
        public OwnedInputInspection inspect(){
            if(!sealed||request==null||known==null||!current()||issuer.nativeSendEntries!=entriesBefore+1||issuer.sends!=sendsBefore+1||issuer.lastRequest!=request)return invalid("Owned native invocation/request/session/lifecycle/selection provenance lost");
            var chain=issuer.appliedStatesAfter(lease,applied);if(chain.isEmpty())return invalid("Owned applied-state sequence unavailable or exceeded bounded history");
            for(var step:chain.get()){
                if(step.sequence!=applied+1||step.request!=request||step.before==null||step.after==null||!known.sameState(step.before)||!before.sameInfrastructure(step.after))return invalid("External mutation, foreign source, missing/throwing callback or applied-state gap");
                if((step.kind==1||step.kind==6)&&!step.after.cursorEmpty)return invalid("Authoritative native cursor is nonempty; no cursor exemption");
                if(step.after.menu!=known.menu||step.after.screen!=known.screen){
                    if(step.kind==5&&replyTitle!=null&&!replyOpened&&!replyClosedPending&&known.menu==before.menu&&known.screen==before.screen&&closedReplyState(step.after)){
                        // One normal close of this request's original menu is transport, never a receipt.
                        replyClosedPending=true;full=null;
                    }else if(step.kind==4&&replyTitle!=null&&!replyOpened&&(known.menu==before.menu||replyClosedPending)
                            &&step.after.menu!=before.menu&&replyTitle.equals(step.after.title)&&replyProtectedState(step.after)){
                        replyOpened=true;replyClosedPending=false;full=null;
                    }else return invalid("Unselected native menu/screen transition");
                }else if(!step.after.title.equals(known.title))return invalid("Native menu title changed outside selected transition");
                if(replyClosedPending&&!closedReplyState(step.after))return invalid("Selected reply close changed protected native inventory/cursor or intermediate state");
                known=step.after;applied=step.sequence;if(step.full!=null)full=step.full;
            }
            var live=issuer.captureState();if(live==null||!known.sameState(live))return invalid("Current native state differs from exact owned prediction/applied chain");
            if(replyClosedPending){
                if(!closedReplyState(live)||!nativeLeaf())return invalid("Selected reply close pending leaf changed");
                return new OwnedInputInspection(this,OwnedInputStage.OWNED_REPLY_CLOSED_PENDING,null,"One normally applied close after exact owned request; awaiting selected open/full content without credit");
            }
            if(full!=null&&full.lastObservedRequest==request&&full.sequence>request.sequence){
                var current=issuer.currentContent(known.menu);if(current.isPresent()&&current.get()==full&&nativeLeaf())return new OwnedInputInspection(this,OwnedInputStage.NATIVE_FULL_CONTENT,full,"Current normally-returned full native content after exact owned request");
            }
            if(!nativeLeaf())return invalid("Native source leaf changed after Root provider/snapshot callbacks");
            return new OwnedInputInspection(this,OwnedInputStage.OWNED_PENDING,null,"Exact native owned prediction/ordered partial applications; no full receipt or credit");
        }
        public void close(){if(closed)return;closed=true;issuer.endStateObservation(lease);if(issuer.ownedInput==this)issuer.ownedInput=null;}
    }
    /** Only actual native entry evidence can raise this; source preparation must not label an intervening input as clean DENIED. */
    public static final class NativeInputPreparationUncertainException extends IllegalStateException {private NativeInputPreparationUncertainException(){super("Actual native input occurred during source preparation; preserve UNKNOWN and issue no further input");}}
    public Optional<OwnedMenuInput> beginOwnedInput(Object expectedMenu,int expectedRevision,String expectedTitle,int slot,int button,ContainerInput input,String replyTitle){
        if(!refresh()||ownedInput!=null||!(client.gameMode instanceof VisitorHistoryNativeCallCarrier))return Optional.empty();long entriesAtStart=nativeSendEntries,sendsAtStart=sends;var before=captureState();
        if(nativeSendEntries!=entriesAtStart||sends!=sendsAtStart)throw new NativeInputPreparationUncertainException();
        if(before==null||before.menu!=expectedMenu||before.revision!=expectedRevision||!before.title.equals(expectedTitle)||!before.cursorEmpty||before.slotHistory==null||!before.slotHistory.current()||slot<0||slot>=before.cells.size()||button!=0||input!=ContainerInput.PICKUP)return Optional.empty();
        var lease=beginStateObservation();if(lease.isEmpty())return Optional.empty();OwnedMenuInput owned=null;
        try{owned=new OwnedMenuInput(this,lease.get(),before,slot,button,input,replyTitle);ownedInput=owned;
            boolean qualified=owned.current();boolean uncertain=nativeSendEntries!=entriesAtStart||sends!=sendsAtStart||owned.upperStarted!=0||owned.sendEntered!=0;
            if(uncertain){owned.close();throw new NativeInputPreparationUncertainException();}
            if(!qualified){owned.close();return Optional.empty();}return Optional.of(owned);
        }catch(RuntimeException|Error failure){if(owned!=null)owned.close();else endStateObservation(lease.get());throw failure;}
    }
    public static final class GuiInputInvocation {
        private final McVisitorHistoryPacketObserver issuer;private final OwnedMenuInput window;private final GuiInputInvocation previous;private final VisitorHistoryNativeCallCarrier carrier;
        private final Object gameMode,key,player,token;private final int menu,slot,button;private final ContainerInput input;private boolean completed;
        private GuiInputInvocation(McVisitorHistoryPacketObserver o,Object gm,Object key,Object player,Object token,VisitorHistoryNativeCallCarrier carrier,int menu,int slot,int button,ContainerInput input){issuer=o;window=o.ownedInput;previous=o.activeGuiInput;gameMode=gm;this.key=key;this.player=player;this.token=token;this.carrier=carrier;this.menu=menu;this.slot=slot;this.button=button;this.input=input;}
    }
    public static GuiInputInvocation beginGuiInput(Object gm,Object key,Object player,int menu,int slot,int button,ContainerInput input){
        var o=active;if(o==null||!o.refresh()||o.ownedInput==null||gm!=o.client.gameMode||player!=o.player||!(gm instanceof VisitorHistoryNativeCallCarrier carrier))return null;
        var token=carrier.veltraph$claimHistoryNativeCall(key,gm,player,7);if(token==null||!carrier.veltraph$historyNativeCallCurrent(token,key,gm,player,7,false))return null;
        var call=new GuiInputInvocation(o,gm,key,player,token,carrier,menu,slot,button,input);o.activeGuiInput=call;call.window.upperStarted++;
        var actualBefore=o.captureState();if(actualBefore==null||!call.window.before.sameState(actualBefore)||!call.window.nativeLeaf()||menu!=call.window.before.menu.containerId||slot!=call.window.slot||button!=call.window.button||input!=call.window.input||call.window.upperStarted!=1)call.window.failed=true;return call;
    }
    public static void finishGuiInput(GuiInputInvocation call,boolean normal){
        if(call==null)return;var o=call.issuer;try{
            if(call.window!=o.ownedInput||!o.stateObservationCurrent(call.window.lease))return;
            if(!normal||!call.carrier.veltraph$historyNativeCallCurrent(call.token,call.key,call.gameMode,call.player,7,true)||o.activeGuiInput!=call){call.window.failed=true;return;}
            call.completed=true;call.window.upperReturned++;if(call.window.upperReturned!=1)call.window.failed=true;
        }finally{if(o.activeGuiInput==call)o.activeGuiInput=call.previous;}
    }
    public record Status(boolean sessionObserved,boolean localAccountMatches,boolean gardenAreaObserved,
            boolean profileConfirmed,boolean gardenIdentityConfirmed,long epoch,long sentClicks,long fullContents,long opened,long closed,long partialSlots,String reason) {}
    public Status status(){
        boolean live=refresh(),account=false,garden=false;
        var p=player;var w=world;var l=listener;long e=epoch;
        if(live){
            try{account=p.getGameProfile().id().equals(client.getUser().getProfileId());var context=location.current();garden=context.onHypixel()&&context.inSkyBlock()&&context.area()==GameArea.GARDEN;}
            catch(RuntimeException unknown){/* Observation unavailable, no authority minted. */}
        }
        // Account UUID and area do not identify the active SkyBlock profile or a Garden instance.
        if(!same(p,w,l,e))live=false;
        return new Status(live,live&&account,live&&garden,false,false,epoch,sends,contents,opens,closes,slots,
                exhausted?"UNKNOWN: native observation counter exhausted":"UNKNOWN: active SkyBlock profile and Garden identity have no confirmed native binding");
    }
    public static final class RequestReceipt {
        private final McVisitorHistoryPacketObserver issuer;private final LocalPlayer player;private final ClientLevel world;
        private final ClientPacketListener listener;private final long epoch,sequence,at;private final AbstractContainerMenu menu;
        private final int menuId,revision,slot,button;private final ContainerInput input;private final List<ItemStack> inventoryBefore;private final NativeState predictedState;private final long priorAppliedSequence;private final Object ownedLease;private final GuiInputInvocation upper;
        private RequestReceipt(SendInvocation v){issuer=v.issuer;player=v.player;world=v.world;listener=v.listener;epoch=v.epoch;sequence=v.sequence;at=v.at;
            menu=v.menu;menuId=v.packet.containerId();revision=v.packet.stateId();slot=v.packet.slotNum();button=v.packet.buttonNum();input=v.packet.containerInput();inventoryBefore=v.inventoryBefore;predictedState=v.predictedState;priorAppliedSequence=v.priorAppliedSequence;ownedLease=v.ownedLease;upper=v.upper;}
        public long observedSequence(){return sequence;}public long observedAt(){return at;}public Object nativeMenu(){return menu;}
        public int menuId(){return menuId;}public int revision(){return revision;}public int slot(){return slot;}public int button(){return button;}public ContainerInput input(){return input;}
        public List<ItemStack> inventoryBefore(){return copy(inventoryBefore);}
        public Optional<NativeState> predictedNativeState(){return Optional.ofNullable(predictedState);}public long priorAppliedStateSequence(){return priorAppliedSequence;}
    }
    public static final class ContentReceipt {
        private final McVisitorHistoryPacketObserver issuer;private final LocalPlayer player;private final ClientLevel world;
        private final ClientPacketListener listener;private final long epoch,sequence,at;private final AbstractContainerMenu menu;
        private final int menuId,revision;private final List<ItemStack> packetItems,full36;private final ItemStack cursor;
        private final RequestReceipt lastObservedRequest;
        private ContentReceipt(ReceiveInvocation v,List<ItemStack> full36){issuer=v.issuer;player=v.player;world=v.world;listener=v.listener;epoch=v.epoch;sequence=v.sequence;at=v.at;
            menu=v.menu;menuId=v.menuId;revision=v.revision;packetItems=v.items;cursor=v.cursor;this.full36=full36;lastObservedRequest=v.request;}
        public long observedSequence(){return sequence;}public long observedAt(){return at;}public Object nativeMenu(){return menu;}
        public int menuId(){return menuId;}public int revision(){return revision;}
        public List<ItemStack> serverPacketItems(){return copy(packetItems);}public List<ItemStack> full36Inventory(){return copy(full36);}
        public boolean cursorEmpty(){return cursor.isEmpty();}
        /** Temporal provenance only, never exclusive causation or offer/page acknowledgment. */
        public Optional<RequestReceipt> lastObservedRequest(){return Optional.ofNullable(lastObservedRequest);}
    }
    public static final class OpenReceipt {
        private final long sequence,at,epoch;private final int menuId;private final String title;private final net.minecraft.network.chat.Component nativeTitle;private final Object menu;
        private OpenReceipt(ReceiveInvocation v,Object menu,String title){sequence=v.sequence;at=v.at;epoch=v.epoch;menuId=v.menuId;this.menu=menu;this.title=title;nativeTitle=v.openTitle.copy();}
        public long observedSequence(){return sequence;}public long observedAt(){return at;}public int menuId(){return menuId;}public String title(){return title;}public Object nativeMenu(){return menu;}
    }
    public static final class CloseReceipt {
        private final long sequence,at,epoch;private final int menuId;private final Object menu;
        private CloseReceipt(ReceiveInvocation v){sequence=v.sequence;at=v.at;epoch=v.epoch;menuId=v.menuId;menu=v.menu;}
        public long observedSequence(){return sequence;}public long observedAt(){return at;}public int menuId(){return menuId;}public Object nativeMenu(){return menu;}
    }
    public static final class SlotReceipt {
        private final McVisitorHistoryPacketObserver issuer;private final LocalPlayer player;private final ClientLevel world;private final ClientPacketListener listener;
        private final long epoch,sequence,at;private final AbstractContainerMenu menu;private final int menuId,revision,slot;private final boolean directInventory;private final ItemStack payload;
        private SlotReceipt(ReceiveInvocation v){issuer=v.issuer;player=v.player;world=v.world;listener=v.listener;epoch=v.epoch;sequence=v.sequence;at=v.at;menu=v.menu;
            menuId=v.menuId;revision=v.revision;slot=v.nativeSlot;directInventory=v.directInventory;payload=v.slotItem;}
        public long observedSequence(){return sequence;}public long observedAt(){return at;}public Object nativeMenu(){return menu;}
        public int menuId(){return menuId;}public int revision(){return revision;}public int nativeSlot(){return slot;}
        public boolean directInventoryIndex(){return directInventory;}public ItemStack payload(){return payload.copy();}
        /** A single changed slot is not a full inventory or completed visitor proof. */
        public boolean full36InventoryProof(){return false;}
    }
    public static final class SendInvocation {
        final McVisitorHistoryPacketObserver issuer;final LocalPlayer player;final ClientLevel world;final ClientPacketListener listener;
        final long epoch,sequence,at;final ServerboundContainerClickPacket packet;final Connection transport;final AbstractContainerMenu menu;
        final List<ItemStack> inventoryBefore;final NativeState predictedState;final long priorAppliedSequence;final Object ownedLease;final GuiInputInvocation upper;final OwnedMenuInput owned;final DeviceInvocationIdentity.Invocation identity;
        final VisitorHistoryNativeCallCarrier carrier;final Object nativeToken,argument1,argument2;
        private SendInvocation(McVisitorHistoryPacketObserver o,ServerboundContainerClickPacket p,Connection transport,AbstractContainerMenu menu,List<ItemStack> before,VisitorHistoryNativeCallCarrier carrier,Object token,Object a1,Object a2){
            issuer=o;player=o.player;world=o.world;listener=o.listener;epoch=o.epoch;sequence=o.next();at=System.currentTimeMillis();packet=p;this.transport=transport;this.menu=menu;inventoryBefore=before;predictedState=o.stateObservationLease==null?null:o.captureState();priorAppliedSequence=o.appliedSequence;ownedLease=o.stateObservationLease;upper=o.activeGuiInput;owned=o.ownedInput;
            identity=DeviceInvocationIdentity.head(o,p,transport,sequence>0);this.carrier=carrier;nativeToken=token;argument1=a1;argument2=a2;
        }
    }
    public static final class ReceiveInvocation {
        final McVisitorHistoryPacketObserver issuer;final LocalPlayer player;final ClientLevel world;final ClientPacketListener listener;
        final long epoch,sequence,at;final Packet<?> packet;final AbstractContainerMenu menu;final int menuId,revision;
        final int nativeSlot;final boolean directInventory;final ItemStack slotItem;
        final List<ItemStack> items;final ItemStack cursor;final net.minecraft.network.chat.Component openTitle;final RequestReceipt request;final DeviceInvocationIdentity.Invocation identity;
        final VisitorHistoryNativeCallCarrier carrier;final Object nativeToken;final int kind;final NativeState beforeState;final OwnedMenuInput owned;
        private ReceiveInvocation(McVisitorHistoryPacketObserver o,Packet<?> packet,AbstractContainerMenu menu,int id,int revision,List<ItemStack> items,ItemStack cursor,VisitorHistoryNativeCallCarrier carrier,Object token,int kind){
            issuer=o;player=o.player;world=o.world;listener=o.listener;epoch=o.epoch;sequence=o.next();at=System.currentTimeMillis();this.packet=packet;this.menu=menu;menuId=id;this.revision=revision;this.items=items;this.cursor=cursor;owned=o.ownedInput;beforeState=o.stateObservationLease==null?null:o.captureState();
            if(packet instanceof ClientboundSetPlayerInventoryPacket p){nativeSlot=p.slot();directInventory=true;slotItem=p.contents().copy();}
            else if(packet instanceof ClientboundContainerSetSlotPacket p){nativeSlot=p.getSlot();directInventory=p.getContainerId()==-2;slotItem=p.getItem().copy();}
            else{nativeSlot=-1;directInventory=false;slotItem=ItemStack.EMPTY;}
            openTitle=packet instanceof ClientboundOpenScreenPacket open?open.getTitle().copy():null;request=o.lastRequest;
            identity=DeviceInvocationIdentity.head(o,packet,listener,sequence>0);this.carrier=carrier;nativeToken=token;this.kind=kind;
        }
    }
    // Cross-package hooks require the exact private in-flight frame on the actual vanilla target. Public data never mints a capsule.
    public static SendInvocation beginSend(Connection transport,Packet<?> packet,io.netty.channel.ChannelFutureListener callback,boolean flush){
        var o=active;if(o==null||!o.refresh()||!(packet instanceof ServerboundContainerClickPacket p)
                ||transport!=o.listener.getConnection())return null;
        if(!(transport instanceof VisitorHistoryNativeCallCarrier carrier))return null;
        Object a2=Boolean.valueOf(flush),token=carrier.veltraph$claimHistoryNativeCall(packet,callback,a2,0);
        if(token==null||!carrier.veltraph$historyNativeCallCurrent(token,packet,callback,a2,0,false))return null;
        if(o.nativeSendEntries==Long.MAX_VALUE){o.exhausted=true;o.clear();return null;}o.nativeSendEntries++;
        var owned=o.ownedInput;if(owned!=null){owned.sendEntered++;var upper=o.activeGuiInput;
            if(owned.sealed||owned.sendEntered!=1||!owned.nativeLeaf()||upper==null||upper.window!=owned||!upper.carrier.veltraph$historyNativeCallCurrent(upper.token,upper.key,upper.gameMode,upper.player,7,false)
                ||p.containerId()!=owned.before.menu.containerId||p.stateId()!=owned.before.revision||p.slotNum()!=owned.slot||p.buttonNum()!=owned.button||p.containerInput()!=owned.input)owned.failed=true;
        }
        var menu=o.player.containerMenu;
        if(menu==null||menu.containerId!=p.containerId()||menu.getStateId()!=p.stateId()||p.slotNum()<0||p.slotNum()>=menu.slots.size())return null;
        var before=inventory(o.player);if(before.size()!=36)return null;
        if(!carrier.veltraph$historyNativeCallCurrent(token,packet,callback,a2,0,false))return null;
        return new SendInvocation(o,p,transport,menu,before,carrier,token,callback,a2);
    }
    public static void finishSend(SendInvocation v,boolean normal){
        if(v==null)return;var o=v.issuer;try{boolean actual=normal&&v.carrier.veltraph$historyNativeCallCurrent(v.nativeToken,v.packet,v.argument1,v.argument2,0,true)
            &&DeviceInvocationIdentity.finish(v.identity,active,v.packet,v.transport,normal)==DeviceInvocationIdentity.Result.RETURNED
            &&o.same(v.player,v.world,v.listener,v.epoch)&&o.player.containerMenu==v.menu;
        if(v.owned!=null&&o.ownedInput==v.owned){if(!actual)v.owned.failed=true;else v.owned.sendReturned++;}
        if(!actual)return;
        if(o.lastRequest==null||o.lastRequest.sequence<v.sequence){o.lastRequest=new RequestReceipt(v);o.sends++;}
        }catch(Throwable observationFailure){if(v.owned!=null&&o.ownedInput==v.owned&&o.stateObservationLease==v.owned.lease)v.owned.failed=true;}
    }
    public static ReceiveInvocation beginReceive(Object handler,Packet<?> packet){
        var o=active;if(o==null||!o.refresh()||handler!=o.listener||!(handler instanceof VisitorHistoryNativeCallCarrier carrier))return null;
        int kind=packet instanceof ClientboundContainerSetContentPacket?1:packet instanceof ClientboundContainerSetSlotPacket?2:packet instanceof ClientboundSetPlayerInventoryPacket?3:packet instanceof ClientboundOpenScreenPacket?4:packet instanceof ClientboundContainerClosePacket?5:packet instanceof ClientboundSetCursorItemPacket?6:-1;
        if(kind<0)return null;Object token=carrier.veltraph$claimHistoryNativeCall(packet,handler,null,kind);
        if(token==null||!carrier.veltraph$historyNativeCallCurrent(token,packet,handler,null,kind,false))return null;
        var observedOwner=o.ownedInput;
        try{
        if(packet instanceof ClientboundContainerSetContentPacket p){
            var menu=p.containerId()==0?o.player.inventoryMenu:o.player.containerMenu;
            if(menu==null||menu.containerId!=p.containerId()||p.items().size()!=menu.slots.size()||p.items().size()>512)return rejectedReceiveStart(o,observedOwner);
            return new ReceiveInvocation(o,p,menu,p.containerId(),p.stateId(),copy(p.items()),p.carriedItem().copy(),carrier,token,kind);
        }
        if(packet instanceof ClientboundSetPlayerInventoryPacket p){
            if(p.slot()<0||p.slot()>=36)return rejectedReceiveStart(o,observedOwner);
            return new ReceiveInvocation(o,p,o.player.inventoryMenu,-2,-1,List.of(),ItemStack.EMPTY,carrier,token,kind);
        }
        if(packet instanceof ClientboundContainerSetSlotPacket p){
            boolean direct=p.getContainerId()==-2;var menu=p.getContainerId()==0||direct?o.player.inventoryMenu:o.player.containerMenu;
            if(menu==null||p.getSlot()<0||(direct?p.getSlot()>=36:(menu.containerId!=p.getContainerId()||p.getSlot()>=menu.slots.size())))return rejectedReceiveStart(o,observedOwner);
            return new ReceiveInvocation(o,p,menu,p.getContainerId(),p.getStateId(),List.of(),ItemStack.EMPTY,carrier,token,kind);
        }
        if(packet instanceof ClientboundOpenScreenPacket p)return new ReceiveInvocation(o,p,null,p.getContainerId(),-1,List.of(),ItemStack.EMPTY,carrier,token,kind);
        if(packet instanceof ClientboundSetCursorItemPacket p){var menu=o.player.containerMenu;if(menu==null)return rejectedReceiveStart(o,observedOwner);return new ReceiveInvocation(o,p,menu,menu.containerId,menu.getStateId(),List.of(),p.contents().copy(),carrier,token,kind);}
        if(packet instanceof ClientboundContainerClosePacket p){var menu=o.player.containerMenu;
            if(menu==null||menu.containerId!=p.getContainerId())return rejectedReceiveStart(o,observedOwner);
            return new ReceiveInvocation(o,p,menu,p.getContainerId(),menu.getStateId(),List.of(),ItemStack.EMPTY,carrier,token,kind);}
        return rejectedReceiveStart(o,observedOwner);
        }catch(Throwable observationFailure){return rejectedReceiveStart(o,observedOwner);}
    }
    private static ReceiveInvocation rejectedReceiveStart(McVisitorHistoryPacketObserver o,OwnedMenuInput captured){if(captured!=null&&o.ownedInput==captured&&o.stateObservationLease==captured.lease)captured.failed=true;return null;}
    private static void rejectedReceiveFinish(ReceiveInvocation v){if(v.owned!=null&&v.issuer.ownedInput==v.owned&&v.issuer.stateObservationLease==v.owned.lease)v.owned.failed=true;}
    public static void finishReceive(ReceiveInvocation v,boolean normal){
        if(v==null)return;try{if(!normal){rejectedReceiveFinish(v);return;}
        if(!v.carrier.veltraph$historyNativeCallCurrent(v.nativeToken,v.packet,v.listener,null,v.kind,true)){rejectedReceiveFinish(v);return;}var o=v.issuer;
        if(DeviceInvocationIdentity.finish(v.identity,active,v.packet,v.listener,normal)!=DeviceInvocationIdentity.Result.RETURNED
                ||!o.same(v.player,v.world,v.listener,v.epoch)){rejectedReceiveFinish(v);return;}
        if(v.packet instanceof ClientboundContainerSetContentPacket){
            if(v.request!=o.lastRequest||v.menu.getStateId()!=v.revision||!matches(v.menu,v.items)||!ItemStack.matches(v.menu.getCarried(),v.cursor)){rejectedReceiveFinish(v);return;}
            var full=full36(v.menu,o.player,v.items);if(full.size()!=36){rejectedReceiveFinish(v);return;}
            var receipt=new ContentReceipt(v,full);o.recordApplied(v,receipt);var prior=o.lastContents.get(v.menuId);
            if(prior==null||prior.sequence<v.sequence){o.lastContents.put(v.menuId,receipt);while(o.lastContents.size()>4)o.lastContents.remove(o.lastContents.keySet().iterator().next());o.contents++;}
        }else if(v.packet instanceof ClientboundContainerSetSlotPacket||v.packet instanceof ClientboundSetPlayerInventoryPacket){
            ItemStack actual=v.directInventory?o.player.getInventory().getItem(v.nativeSlot):v.menu.slots.get(v.nativeSlot).getItem();
            if((!v.directInventory&&v.menu.getStateId()!=v.revision)||!ItemStack.matches(actual,v.slotItem)){rejectedReceiveFinish(v);return;}
            o.recordApplied(v,null);
            if(o.lastSlot==null||o.lastSlot.sequence<v.sequence){o.lastSlot=new SlotReceipt(v);o.slots++;}
        }else if(v.packet instanceof ClientboundOpenScreenPacket p){
            if(o.player.containerMenu.containerId!=p.getContainerId()||!(o.client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                    ||screen.getMenu()!=o.player.containerMenu||!screen.getTitle().equals(v.openTitle)){rejectedReceiveFinish(v);return;}
            o.recordApplied(v,null);
            if(dev.veltraph.client.garden.visitor.history.HistoryObservationOrder.newer(o.lastOpen==null?-1:o.lastOpen.sequence,v.sequence)){o.lastOpen=new OpenReceipt(v,o.player.containerMenu,v.openTitle.getString());o.opens++;}
        }else if(v.packet instanceof ClientboundSetCursorItemPacket){
            if(o.player.containerMenu!=v.menu||!ItemStack.matches(v.menu.getCarried(),v.cursor)){rejectedReceiveFinish(v);return;}o.recordApplied(v,null);
        }else if(v.packet instanceof ClientboundContainerClosePacket){
            if(o.player.containerMenu==v.menu){rejectedReceiveFinish(v);return;}o.recordApplied(v,null);if(dev.veltraph.client.garden.visitor.history.HistoryObservationOrder.newer(o.lastClose==null?-1:o.lastClose.sequence,v.sequence)){o.lastClose=new CloseReceipt(v);o.closes++;}
        }
        }catch(Throwable observationFailure){rejectedReceiveFinish(v);}
    }
    public Optional<ContentReceipt> currentContent(AbstractContainerMenu menu){
        if(!refresh()||menu==null||(menu!=player.containerMenu&&menu!=player.inventoryMenu))return Optional.empty();var r=lastContents.get(menu.containerId);
        if(r==null||r.issuer!=this||!same(r.player,r.world,r.listener,r.epoch)||r.menu!=menu||r.revision!=menu.getStateId()
                ||r.lastObservedRequest!=lastRequest||!matches(menu,r.packetItems)||!ItemStack.matches(menu.getCarried(),r.cursor))return Optional.empty();
        return Optional.of(r);
    }
    public Optional<RequestReceipt> currentRequest(AbstractContainerMenu menu){
        if(!refresh()||lastRequest==null||menu!=player.containerMenu||lastRequest.menu!=menu||!same(lastRequest.player,lastRequest.world,lastRequest.listener,lastRequest.epoch))return Optional.empty();return Optional.of(lastRequest);
    }
    public Optional<OpenReceipt> currentOpen(){
        if(!refresh()||lastOpen==null||lastOpen.epoch!=epoch||lastOpen.menu!=player.containerMenu
                ||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=lastOpen.menu
                ||!screen.getTitle().equals(lastOpen.nativeTitle))return Optional.empty();return Optional.of(lastOpen);
    }
    public Optional<CloseReceipt> currentClose(){
        if(!refresh()||lastClose==null||lastClose.epoch!=epoch||player.containerMenu==lastClose.menu
                ||(client.gui.screen() instanceof AbstractContainerScreen<?> screen&&screen.getMenu()==lastClose.menu))return Optional.empty();return Optional.of(lastClose);
    }
    public Optional<SlotReceipt> currentSlot(){
        if(!refresh()||lastSlot==null||!same(lastSlot.player,lastSlot.world,lastSlot.listener,lastSlot.epoch))return Optional.empty();var r=lastSlot;
        if(r.menu!=player.containerMenu&&r.menu!=player.inventoryMenu)return Optional.empty();
        ItemStack actual=r.directInventory?player.getInventory().getItem(r.slot):r.menu.slots.get(r.slot).getItem();
        if((!r.directInventory&&r.menu.getStateId()!=r.revision)||!ItemStack.matches(actual,r.payload))return Optional.empty();return Optional.of(r);
    }
    private static List<ItemStack> inventory(LocalPlayer p){var out=new ArrayList<ItemStack>();for(int i=0;i<36;i++)out.add(p.getInventory().getItem(i).copy());return List.copyOf(out);}
    private static List<ItemStack> copy(List<ItemStack> in){return in.stream().map(ItemStack::copy).toList();}
    private static boolean matches(AbstractContainerMenu menu,List<ItemStack> contents){if(menu.slots.size()!=contents.size())return false;for(int i=0;i<contents.size();i++)if(!ItemStack.matches(menu.slots.get(i).getItem(),contents.get(i)))return false;return true;}
    private static List<ItemStack> full36(AbstractContainerMenu menu,LocalPlayer p,List<ItemStack> packet){
        ItemStack[] cells=new ItemStack[36];for(int i=0;i<menu.slots.size();i++){var s=menu.slots.get(i);if(s.container!=p.getInventory())continue;int n=s.getContainerSlot();if(n<0||n>=36)continue;if(cells[n]!=null)return List.of();cells[n]=packet.get(i).copy();}
        if(Arrays.stream(cells).anyMatch(Objects::isNull))return List.of();return List.of(cells);
    }
}
