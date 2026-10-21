package dev.veltraph.client.garden.pests.mc;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.garden.pests.PestInventoryRestorationJournal;
import dev.veltraph.client.service.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Passive native packet evidence. It changes no packet, pose, key, menu, inventory, lease or module. */
public final class McPestInventoryRestorationObserver {
    private static final long MAX_ACK_NANOS=2_000_000_000L;
    private static final IdentityHashMap<Minecraft,Host> HOSTS=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,SlotState> SLOT_VERSIONS=new IdentityHashMap<>();
    private static final class SlotState {private final PestInventoryRestorationJournal.SlotVersions versions=new PestInventoryRestorationJournal.SlotVersions();}
    private static SlotState slotState(Object inventory){return SLOT_VERSIONS.computeIfAbsent(inventory,key->new SlotState());}
    public interface SlotVersionCarrier {}
    public interface SendCarrier {
        int pest$pushEnvelope(SendEnvelope envelope);void pest$replaceEnvelope(int depth,SendEnvelope envelope);void pest$popEnvelope(int depth,SendEnvelope envelope);
        int pest$envelopeDepth();boolean pest$envelopeCurrent(int depth,SendEnvelope envelope);
    }
    public static final class SendEnvelope {
        private final InputInvocation input;private final OwnedSelectionInvocation selection;private final OriginalSelectionInvocation original;private final Object exactPacket;
        private SendEnvelope(OriginalSelectionInvocation original){this.input=null;this.selection=null;this.original=original;this.exactPacket=null;}
        private SendEnvelope(InputInvocation input,OwnedSelectionInvocation selection,Object exactPacket){this.input=input;this.selection=selection;this.original=null;this.exactPacket=exactPacket;}
    }
    public static SendEnvelope inputEnvelope(InputInvocation input,Object packet){return new SendEnvelope(input,null,packet);}
    private static SendEnvelope originalEnvelope(OriginalSelectionInvocation original){return new SendEnvelope(original);}
    private static boolean actualClientInventory(Object inventory){var c=Minecraft.getInstance();
        return c!=null&&c.isSameThread()&&c.player!=null&&PestInventoryRestorationJournal.clientInventory(true,c.player.getInventory(),inventory);
    }
    private static long slotVersion(Object inventory){if(!actualClientInventory(inventory)||!(inventory instanceof SlotVersionCarrier))return -1;
        var state=slotState(inventory);return state.versions.known()&&state.versions.inFlight()==0?state.versions.version():-1;}

    /** Read-only native setter history. It grants no slot, role, restoration or packet authority. */
    public static final class SelectionHistory {
        private final Object inventory;private final SlotState state;private final long version;
        private SelectionHistory(Object inventory,long version){this.inventory=inventory;this.state=slotState(inventory);this.version=version;}
        /** Immutable stored observation equality only; no current read, version adoption or authority. */
        public boolean sameStoredObservation(SelectionHistory other){return other!=null&&inventory==other.inventory&&state==other.state&&version==other.version;}
        /** Immutable metadata lineage only. No current inventory read, slot grant or version adoption. */
        public boolean followsExactlyOneStoredSetterObservation(SelectionHistory before){return before!=null&&inventory==before.inventory&&state==before.state
            &&before.version>=0&&before.version<=Long.MAX_VALUE-2&&version==before.version+2;}
        public boolean current(){return SLOT_VERSIONS.get(inventory)==state&&slotVersion(inventory)==version;}
        /** Negative read of this original state/version only; no native inventory getter, provider or recapture. */
        public boolean storedStateCurrent(){return SLOT_VERSIONS.get(inventory)==state&&state.versions.matches(version);}
        /** Entry and normal-return events from exactly one real native setter; receipt origin is checked by its issuer. */
        public boolean followsExactlyOneSetter(SelectionHistory before){return before!=null&&inventory==before.inventory&&state==before.state
            &&before.version>=0&&before.version<=Long.MAX_VALUE-2&&version==before.version+2&&current();}
    }
    public static SelectionHistory selectedSlotHistory(Object inventory){long version=slotVersion(inventory);return version<0?null:new SelectionHistory(inventory,version);}
    static long originalSlotVersion(Object inventory){return slotVersion(inventory);}
    private static final class OriginalSelectionInvocation {
        private final PestInventoryLease.OwnedRestoreHandle handle;private final Minecraft c;private final Host host;private final Window oldWindow;private final OwnedSelectionInvocation windowSelection;
        private final Object player,world,listener,inventory,options,gameMode,menu,store;private final ModuleManager root;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final McRotationService rotation;private final long generation,epoch,serial,version;private final int expected,next;
        private final List<Cell> contents;private final Cell carried;private final java.util.function.BooleanSupplier refusal;
        private final PestInventoryRestorationJournal.SelectionEffects effects=new PestInventoryRestorationJournal.SelectionEffects();private boolean closedClean;
        private OriginalSelectionInvocation(PestInventoryLease.OwnedRestoreHandle handle,int expected,int next,java.util.function.BooleanSupplier refusal){
            this.handle=handle;c=handle.nativeClient();host=HOSTS.computeIfAbsent(c,key->new Host());oldWindow=host.current;windowSelection=host.selection;
            player=c.player;world=c.level;listener=c.getConnection();inventory=c.player.getInventory();options=c.options;gameMode=c.gameMode;menu=c.player.containerMenu;
            store=VeltraphBootstrap.config();root=VeltraphBootstrap.moduleManager();lifecycle=root==null?null:root.lifecycleSnapshot();
            rotation=handle.originalParent() instanceof GardenActionModule parent&&parent.services.rotation() instanceof McRotationService service?service:null;
            generation=rotation==null?-1:rotation.requestGeneration();epoch=McOwnedRotation.epoch(c);serial=McOwnedRotation.publicationSerial(c);version=slotVersion(inventory);
            this.expected=expected;this.next=next;contents=inventory(c);carried=cursor(c);this.refusal=refusal;
        }
        private boolean leaf(boolean after){return c.isSameThread()&&host.originalSelection==this&&lifecycle!=null&&lifecycle.known()&&version>=0
            &&c.player==player&&c.level==world&&c.getConnection()==listener&&c.options==options&&c.gameMode==gameMode&&c.player.getInventory()==inventory
            &&c.player.containerMenu==menu&&menu==c.player.inventoryMenu&&c.gui.screen()==null&&carried.equals(cursor(c))&&contents.equals(inventory(c))
            &&selected(c)==(after?next:expected)&&slotVersion(inventory)==version+(after?2:0)
            &&(after?effects.afterSetter():effects.beforeSetter())&&handle.selectionLeaf()
            &&VeltraphBootstrap.moduleManager()==root&&VeltraphBootstrap.config()==store&&root.get(handle.originalParent().id()).orElse(null)==handle.originalParent()
            &&rotation!=null&&rotation.requestGeneration()==generation&&McOwnedRotation.epoch(c)==epoch&&McOwnedRotation.publicationSerial(c)==serial&&root.lifecycleCurrent(lifecycle);}
        private boolean current(boolean after){try{return refusal!=null&&refusal.getAsBoolean()&&leaf(after);}catch(RuntimeException unavailable){return false;}}
    }
    /** Private actual setter+carried-packet result; slot continuity is refusal evidence, never inventory ACK. */
    static final class OriginalSelectionReceipt {
        private final PestInventoryLease.OwnedRestoreHandle handle;private final Object inventory;private final OriginalSelectionInvocation invocation;
        private final long version;private final McInventoryService.ScopedSlotReceipt actual;
        private OriginalSelectionReceipt(OriginalSelectionInvocation call,McInventoryService.ScopedSlotReceipt actual){handle=call.handle;inventory=call.inventory;invocation=call;version=slotVersion(inventory);this.actual=actual;}
        boolean current(PestInventoryLease.OwnedRestoreHandle exact){return handle==exact&&invocation.closedClean&&invocation.effects.exact()&&actual!=null&&actual.confirmed()&&actual.localSelected()&&actual.packetSubmitted()
            &&exact.selectionLeaf()&&slotVersion(inventory)==version;}
    }
    static final class OriginalSelectionResult {
        private final McInventoryService.ScopedSlotReceipt actual;private final OriginalSelectionReceipt lineage;
        private OriginalSelectionResult(McInventoryService.ScopedSlotReceipt actual,OriginalSelectionReceipt lineage){this.actual=actual;this.lineage=lineage;}
        McInventoryService.ScopedSlotReceipt actual(){return actual;}OriginalSelectionReceipt lineage(){return lineage;}
    }
    static boolean originalSelectionFence(PestInventoryLease.OwnedRestoreHandle handle,boolean after){var host=HOSTS.get(handle.nativeClient());var call=host==null?null:host.originalSelection;
        return call==null||call.handle==handle&&call.leaf(after);}
    /** The native source invokes the actual effect itself; arbitrary retained slot receipts cannot seed lineage. */
    static OriginalSelectionResult selectOriginalOwned(PestInventoryLease.OwnedRestoreHandle handle,int expected,int next,java.util.function.BooleanSupplier refusal,java.util.function.Supplier<McInventoryService.ScopedSlotReceipt> effect){
        var c=handle.nativeClient();if(c==null||!c.isSameThread())return new OriginalSelectionResult(null,null);
        if(!(handle.nativeInventory() instanceof SlotVersionCarrier)||!(c.getConnection() instanceof SendCarrier))return new OriginalSelectionResult(effect.get(),null);
        OriginalSelectionInvocation call=null;SendEnvelope envelope=null;SendCarrier carrier=null;int depth=-1;
        try{call=new OriginalSelectionInvocation(handle,expected,next,refusal);carrier=(SendCarrier)call.listener;
            if(call.host.originalSelection!=null||!handle.selectionVersionCurrent())return new OriginalSelectionResult(null,null);
            depth=carrier.pest$pushEnvelope(null);call.host.originalSelection=call;envelope=originalEnvelope(call);carrier.pest$replaceEnvelope(depth,envelope);
            if(!call.current(false))return new OriginalSelectionResult(null,null);
            var actual=effect.get();boolean confirmed=actual!=null&&actual.confirmed()&&actual.localSelected()&&actual.packetSubmitted()
                &&actual.expectedSlot()==expected&&actual.requestedSlot()==next&&call.effects.exact()&&call.current(true);
            return new OriginalSelectionResult(actual,confirmed?new OriginalSelectionReceipt(call,actual):null);
        }finally{
            try{if(carrier!=null&&depth>=0){boolean exact=carrier.pest$envelopeCurrent(depth,envelope);carrier.pest$popEnvelope(depth,envelope);
                if(call!=null)call.closedClean=exact&&carrier.pest$envelopeDepth()==depth&&call.host.originalSelection==call;}}
            catch(Throwable unavailable){if(call!=null)call.effects.refuse();}
            finally{if(call!=null&&call.host.originalSelection==call)call.host.originalSelection=null;}
        }
    }

    private static final class OwnedSelectionInvocation {
        private final Window window;private final long version;private int setters,completed,packets;private boolean failed;
        private OwnedSelectionInvocation(Window window){this.window=window;version=slotState(window.spec.inventory()).versions.version();}
    }
    private static boolean versionCurrent(Window w){var state=slotState(w.spec.inventory());var call=w.host.selection;
        return state.versions.known()&&state.versions.inFlight()==0&&(call!=null&&call.window==w?state.versions.version()==call.version+call.setters+call.completed:state.versions.version()==w.slotVersion);
    }
    public static final class LocalSelectionInvocation {
        private final Object inventory;private final SlotState state;private final Window window;private final OwnedSelectionInvocation owner;
        private final OriginalSelectionInvocation original;private final int before,next;private LocalSelectionInvocation(Object inventory,SlotState state,Window window,OwnedSelectionInvocation owner,OriginalSelectionInvocation original,int before,int next){this.inventory=inventory;this.state=state;this.window=window;this.owner=owner;this.original=original;this.before=before;this.next=next;}
    }
    public static LocalSelectionInvocation beginLocalSelection(net.minecraft.world.entity.player.Inventory inventory,int next){
        // Common Inventory also runs on integrated-server and foreign-player threads: no map/version touch there.
        if(!actualClientInventory(inventory))return null;
        var c=Minecraft.getInstance();var state=slotState(inventory);state.versions.begin();
        var host=HOSTS.get(c);var w=host==null?null:host.current;var owner=host==null?null:host.selection;
        if(owner==null||owner.window!=w||w==null||w.spec.inventory()!=inventory)owner=null;
        if(owner!=null)owner.setters++;else if(w!=null&&w.spec.inventory()==inventory)host.journal.invalidate(w.journal);
        var original=host==null?null:host.originalSelection;if(original!=null&&original.inventory!=inventory)original=null;
        if(original!=null)original.effects.setter();
        return new LocalSelectionInvocation(inventory,state,w,owner,original,inventory.getSelectedSlot(),next);
    }
    public static void finishLocalSelection(LocalSelectionInvocation call,boolean normal){
        if(call==null)return;call.state.versions.finish();
        if(call.original!=null){call.original.effects.completed(normal);if(call.before!=call.original.expected||call.next!=call.original.next||((net.minecraft.world.entity.player.Inventory)call.inventory).getSelectedSlot()!=call.next)call.original.effects.refuse();}
        if(call.owner!=null){call.owner.completed++;if(!normal||call.before!=call.owner.window.spec.temporary()||call.next!=call.owner.window.spec.restored()
                ||((net.minecraft.world.entity.player.Inventory)call.inventory).getSelectedSlot()!=call.next)call.owner.failed=true;}
        else if(call.window!=null&&call.window.host.current==call.window)call.window.host.journal.invalidate(call.window.journal);
    }
    private static final class Cell {
        private final ItemStack stack;private Cell(ItemStack stack){this.stack=stack.copy();}
        @Override public boolean equals(Object other){return other instanceof Cell c&&ItemStack.matches(stack,c.stack);}
        @Override public int hashCode(){return 1;}
    }
    private static List<Cell> cells(List<ItemStack> stacks){return stacks.stream().map(Cell::new).toList();}
    private static List<Cell> inventory(Minecraft c){var out=new ArrayList<Cell>();if(c.player!=null)for(int i=0;i<36;i++)out.add(new Cell(c.player.getInventory().getItem(i)));return List.copyOf(out);}
    private static Cell cursor(Minecraft c){return new Cell(c.player==null?ItemStack.EMPTY:c.player.containerMenu.getCarried());}
    private static int selected(Minecraft c){return c.player==null?-1:c.player.getInventory().getSelectedSlot();}
    private static final class Host {private final PestInventoryRestorationJournal<Cell> journal=new PestInventoryRestorationJournal<>();private Window current;private OwnedSelectionInvocation selection;private OriginalSelectionInvocation originalSelection;}
    /** Only the package native inventory producer can create this privately minted original-window handle. */
    public static final class Window {
        private final Minecraft c;private final Host host;private final PestInventoryLease.RestoreSpecification spec;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit;
        private final ModuleManager root;private final Module parent;private final Object store;private final McRotationService rotation;
        private final long generation,epoch,serial;private final java.util.function.BooleanSupplier refusal;
        private final PestInventoryRestorationJournal.Window<Cell> journal;private final Connection transport;
        private long slotVersion;private boolean selectionAccepted;private AuthoritativeRestorationReceipt authoritative;
        private Window(Minecraft c,Host host,PestInventoryLease.RestoreSpecification spec,Module parent,McRotationService rotation,java.util.function.BooleanSupplier refusal,
                PestInventoryRestorationJournal.Window<Cell> journal){
            this.c=c;this.host=host;this.spec=spec;this.permit=spec.permit();this.parent=parent;this.rotation=rotation;this.refusal=refusal;this.journal=journal;
            root=VeltraphBootstrap.moduleManager();store=VeltraphBootstrap.config();generation=rotation.requestGeneration();epoch=McOwnedRotation.epoch(c);serial=McOwnedRotation.publicationSerial(c);
            transport=c.getConnection().getConnection();slotVersion=slotState(spec.inventory()).versions.version();
        }
        private boolean base(){try{
            if(!c.isSameThread()||host.current!=this||VeltraphBootstrap.moduleManager()!=root)return false;
            var lifecycle=root.lifecycleSnapshot();if(lifecycle==null||!lifecycle.known())return false;
            Object input=root.inputOwner().orElse(null),camera=McOwnedRotation.authority(c);int controllers=root.enabledControllers().size();
            boolean role=parent.isEnabled()?controllers==1&&input==parent&&camera==parent:controllers==0&&input==null&&camera==null;
            boolean permission=refusal!=null&&refusal.getAsBoolean();
            return permission&&role&&root.get(parent.id()).orElse(null)==parent&&root.lifecycleCurrent(lifecycle)&&spec.currentLeaf()
                &&VeltraphBootstrap.moduleManager()==root&&VeltraphBootstrap.config()==store&&c.getConnection().getConnection()==transport
                &&versionCurrent(this)&&rotation.requestGeneration()==generation&&McOwnedRotation.epoch(c)==epoch&&McOwnedRotation.publicationSerial(c)==serial;
        }catch(RuntimeException unavailable){return false;}}
        private boolean frame(){return base()&&spec.matchesAfter()&&selected(c)==journal.selected();}
        public AuthoritativeRestorationReceipt authoritativeReceipt(){return authoritative!=null&&authoritative.current()?authoritative:null;}
        AuthoritativeRestorationReceipt nativeAuthoritativeReceipt(){return authoritative;}

    }
    /** Actual server-applied after-request state; it does not claim server operation causation or grant restart. */
    public static final class AuthoritativeRestorationReceipt {
        private final Window window;private final Object packet,listener;private final int revision;private final List<Cell> after;private final Cell cursor;
        private AuthoritativeRestorationReceipt(Window window,Object packet,Object listener,int revision,List<Cell> after,Cell cursor){this.window=window;this.packet=packet;this.listener=listener;this.revision=revision;this.after=List.copyOf(after);this.cursor=cursor;}
        public boolean current(){var c=window.c;return window.selectionAccepted&&window.host.selection==null&&window.host.originalSelection==null&&window.base()&&after.equals(inventory(c))&&cursor.equals(cursor(c))
            &&c.player.inventoryMenu.getStateId()==revision&&window.host.journal.current(window.journal,System.nanoTime(),true,inventory(c),cursor(c),selected(c));}
        public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit(){return window.permit;}
        public long windowNonce(){return window.journal.nonce();}public int serverRevision(){return revision;}
        public String qualification(){return "AUTHORITATIVE_AFTER_REQUEST; server causal nonce unavailable";}
        private static final class FinalAckSnapshot {
            private final AuthoritativeRestorationReceipt owner;private final Object inventory,menu,transport,options,gameMode;
            private final long slotVersion;private final int selected,state;private final List<Cell> contents;private final Cell carried;
            private FinalAckSnapshot(AuthoritativeRestorationReceipt owner){this.owner=owner;var c=owner.window.c;
                inventory=c.player.getInventory();menu=c.player.containerMenu;transport=c.getConnection().getConnection();options=c.options;gameMode=c.gameMode;
                slotVersion=slotVersion(inventory);selected=selected(c);state=c.player.inventoryMenu.getStateId();contents=inventory(c);carried=cursor(c);
            }
        }
        public Object finalNativeSnapshot(){try{var snapshot=new FinalAckSnapshot(this);return finalNativeCurrent(snapshot)?snapshot:null;}catch(RuntimeException unavailable){return null;}}
        /** Actual private packet/window/native cells only; caller and scope/provider callbacks are excluded. */
        public boolean finalNativeCurrent(Object exact){try{
            if(!(exact instanceof FinalAckSnapshot snapshot)||snapshot.owner!=this)return false;var w=window;var c=w.c;
            return c.isSameThread()&&w.host.current==w&&w.authoritative==this&&w.selectionAccepted&&w.host.selection==null&&w.host.originalSelection==null
                &&w.spec.currentLeaf()&&c.player.getInventory()==snapshot.inventory&&c.player.containerMenu==snapshot.menu&&snapshot.menu==c.player.inventoryMenu
                &&c.getConnection()==listener&&c.getConnection().getConnection()==snapshot.transport&&snapshot.transport==w.transport
                &&c.options==snapshot.options&&c.gameMode==snapshot.gameMode&&c.gui.screen()==null
                &&snapshot.slotVersion>=0&&slotVersion(snapshot.inventory)==snapshot.slotVersion&&versionCurrent(w)
                &&snapshot.selected==selected(c)&&snapshot.state==c.player.inventoryMenu.getStateId()&&snapshot.state==revision
                &&snapshot.contents.equals(after)&&snapshot.contents.equals(inventory(c))&&snapshot.carried.equals(cursor)&&snapshot.carried.equals(cursor(c))
                &&w.host.journal.current(w.journal,System.nanoTime(),true,inventory(c),cursor(c),selected(c));
        }catch(RuntimeException unavailable){return false;}}

    }
    static Window open(Minecraft c,PestInventoryLease.RestoreSpecification spec,Module parent,McRotationService rotation,java.util.function.BooleanSupplier refusal){
        if(c==null||!c.isSameThread()||spec==null||parent==null||rotation==null||refusal==null||!spec.currentBefore()
                ||!(spec.listener() instanceof SendCarrier carrier)||carrier.pest$envelopeDepth()!=0||!(spec.inventory() instanceof SlotVersionCarrier)
                ||!slotState(spec.inventory()).versions.known()||slotState(spec.inventory()).versions.inFlight()!=0)return null;
        try {long now=System.nanoTime();if(now<0)return null;var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(lifecycle==null||!lifecycle.known()||root.get(parent.id()).orElse(null)!=parent||!refusal.getAsBoolean()||!root.lifecycleCurrent(lifecycle)||!spec.currentBefore())return null;
            var host=HOSTS.computeIfAbsent(c,key->new Host());var journal=host.journal.open(now,Math.addExact(now,MAX_ACK_NANOS),spec.menuId(),spec.revision(),spec.source(),spec.hotbar(),spec.temporary(),spec.restored(),cells(spec.before()),cells(spec.after()),new Cell(spec.cursor()));
            if(journal==null)return null;var window=new Window(c,host,spec,parent,rotation,refusal,journal);host.current=window;
            if(!window.base()||!spec.currentBefore()){host.journal.invalidate(journal);return null;}return window;
        }catch(RuntimeException unavailable){return null;}
    }
    /** Exact native selection scope; no caller list/Boolean can advance an expected slot version. */
    static McInventoryService.ScopedSlotReceipt selectOwned(Window window,java.util.function.Supplier<McInventoryService.ScopedSlotReceipt> effect){
        if(window==null)return effect.get();
        var listener=window.spec.listener();var carrier=listener instanceof SendCarrier c?c:null;OwnedSelectionInvocation call=null;SendEnvelope envelope=null;int depth=-1;
        try{
            if(carrier==null||window.host.selection!=null||!window.base())return null;
            depth=carrier.pest$pushEnvelope(null);call=new OwnedSelectionInvocation(window);window.host.selection=call;
            envelope=new SendEnvelope(null,call,null);carrier.pest$replaceEnvelope(depth,envelope);
            var receipt=effect.get();
            boolean actual=receipt!=null&&receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted()&&!call.failed
                &&call.setters==1&&call.completed==1&&call.packets==1&&window.base();
            boolean accepted=actual&&window.host.journal.selected(window.journal,System.nanoTime(),receipt.expectedSlot(),receipt.requestedSlot(),true,true,inventory(window.c),cursor(window.c));
            window.selectionAccepted=accepted;if(accepted)window.slotVersion=slotState(window.spec.inventory()).versions.version();else window.host.journal.invalidate(window.journal);
            return receipt;
        }finally{if(window.host.selection==call)window.host.selection=null;
            try{if(carrier!=null&&depth>=0)carrier.pest$popEnvelope(depth,envelope);}catch(Throwable observerFailure){window.host.journal.invalidate(window.journal);}
            if(!window.selectionAccepted)window.host.journal.invalidate(window.journal);
        }
    }
    public static final class OutboundInvocation {
        private final Window window;private final SendInvocation owned;private final OwnedSelectionInvocation selection;private final OriginalSelectionInvocation original;private final boolean raw;
        private OutboundInvocation(OriginalSelectionInvocation original){this.window=original.oldWindow;this.owned=null;this.selection=null;this.original=original;this.raw=false;}
        private OutboundInvocation(Window window,SendInvocation owned,OwnedSelectionInvocation selection,boolean raw){this.window=window;this.owned=owned;this.selection=selection;this.original=null;this.raw=raw;}
    }
    private static OutboundInvocation originalOutbound(OriginalSelectionInvocation original){return new OutboundInvocation(original);}
    public static OutboundInvocation beginOutbound(Object listener,Packet<?> packet,boolean hasEnvelope,SendEnvelope envelope){
        var c=Minecraft.getInstance();if(c==null||!c.isSameThread())return null;
        if(hasEnvelope){
            if(envelope==null)return null; // Unknown actual upper boundary never borrows another or newer issue.
            if(envelope.input!=null){var entry=envelope.input;var old=entry.window;
                // Unknown/failed qualification may only revoke this entry's captured old window.
                var disturbance=old!=null&&listener==old.spec.listener()&&inventoryTraffic(packet)?new OutboundInvocation(old,null,null,true):null;
                if(envelope.exactPacket!=packet||!entry.owned)return refuseDisturbance(disturbance);
                try{var send=beginSend(entry,listener,packet);return send==null?refuseDisturbance(disturbance):new OutboundInvocation(send.window,send,null,false);}
                catch(Throwable unavailable){return refuseDisturbance(disturbance);}
            }
            if(envelope.original!=null){var call=envelope.original;boolean own=listener==call.listener&&packet instanceof ServerboundSetCarriedItemPacket p&&p.getSlot()==call.next&&call.current(true);
                if(!own){call.effects.refuse();if(call.oldWindow!=null&&call.oldWindow.host.current==call.oldWindow)call.oldWindow.host.journal.invalidate(call.oldWindow.journal);}
                return originalOutbound(call);
            }
            if(envelope.selection!=null){var call=envelope.selection;var w=call.window;
                boolean own=packet instanceof ServerboundSetCarriedItemPacket p&&listener==w.spec.listener()&&p.getSlot()==w.spec.restored()
                    &&w.host.current==w&&w.host.selection==call&&call.setters==1&&call.completed==1&&!call.failed&&w.base()&&w.spec.matchesAfter()&&selected(c)==w.spec.restored();
                var outgoing=new OutboundInvocation(w,null,own?call:null,!own);return own?outgoing:refuseDisturbance(outgoing);
            }
            return null; // Explicit null-window upper invocation.
        }
        var host=HOSTS.get(c);var w=host==null?null:host.current;if(w==null||listener!=w.spec.listener())return null;
        return inventoryTraffic(packet)?refuseDisturbance(new OutboundInvocation(w,null,null,true)):null;
    }
    /** Native entry refusal occurs before original send, so nested consumers cannot use disturbed evidence. */
    private static OutboundInvocation refuseDisturbance(OutboundInvocation captured){
        if(captured!=null&&captured.window!=null&&captured.window.host.current==captured.window)captured.window.host.journal.invalidate(captured.window.journal);
        return captured;
    }
    private static boolean inventoryTraffic(Packet<?> packet){return packet instanceof ServerboundContainerClickPacket||packet instanceof ServerboundSetCarriedItemPacket
        ||packet instanceof ServerboundContainerClosePacket||packet instanceof ServerboundContainerButtonClickPacket||packet instanceof ServerboundContainerSlotStateChangedPacket;}
    public static void finishOutbound(OutboundInvocation call,boolean normal){
        if(call==null)return;
        if(call.original!=null){var original=call.original;
            // Exact old native scope only; reentrant replacement cannot receive this packet or failure.
            if(original.host.originalSelection==original)original.effects.packet(normal&&original.current(true));
            if(original.windowSelection!=null&&original.windowSelection.window==call.window&&call.window!=null&&call.window.host.current==call.window&&call.window.host.selection==original.windowSelection){
                original.windowSelection.packets++;if(!normal||!call.window.base())original.windowSelection.failed=true;
            }else if(call.window!=null&&call.window.host.current==call.window)call.window.host.journal.invalidate(call.window.journal);
            return;
        }
        if(call.window==null||call.window.host.current!=call.window)return;var w=call.window;
        if(call.owned!=null)finishSend(call.owned,normal);
        else if(call.selection!=null){call.selection.packets++;if(!normal||!w.base())call.selection.failed=true;}
        else if(call.raw)w.host.journal.invalidate(w.journal);
    }
    /** Exact upper vanilla invocation, including a captured null window; never a later global lookup. */
    public static final class InputInvocation {
        private final Window window;private final Object gameMode,player;private final boolean owned;
        private InputInvocation(Window window,Object gameMode,Object player,boolean owned){this.window=window;this.gameMode=gameMode;this.player=player;this.owned=owned;}
    }
    /** Callback-free old-window capture. It has invalidation custody only until qualification succeeds. */
    public static InputInvocation captureInputEntry(Object gameMode,Object player){
        var c=Minecraft.getInstance();var host=c==null||!c.isSameThread()?null:HOSTS.get(c);
        return new InputInvocation(host==null?null:host.current,gameMode,player,false);
    }
    /** Every fallible source read follows the distinct native boundary and its old invalidation-only capture. */
    public static InputInvocation qualifyInputEntry(InputInvocation entry,int menu,int slot,int button,ContainerInput input){
        if(entry==null)return null;var c=Minecraft.getInstance();var w=entry.window;
        boolean owned=w!=null&&entry.gameMode==c.gameMode&&entry.player==c.player&&menu==w.spec.menuId()&&slot==w.spec.sourceMenuSlot()
            &&button==w.spec.hotbar()&&input==ContainerInput.SWAP&&w.spec.currentBefore()&&w.base()&&w.host.current==w;
        return new InputInvocation(w,entry.gameMode,entry.player,owned);
    }
    public static void finishInput(InputInvocation call,boolean normal){
        if(call==null||call.window==null)return;var w=call.window;if(w.host.current!=w)return;
        if(!normal||!call.owned||w.journal.status()==PestInventoryRestorationJournal.Status.ARMED)w.host.journal.invalidate(w.journal);
    }
    public static final class SendInvocation {
        private final InputInvocation input;private final Window window;private final Object packet,listener;
        private final PestInventoryRestorationJournal.Send<Cell> journal;
        private SendInvocation(InputInvocation input,Window window,Object packet,Object listener,PestInventoryRestorationJournal.Send<Cell> journal){this.input=input;this.window=window;this.packet=packet;this.listener=listener;this.journal=journal;}
    }
    public static SendInvocation beginSend(InputInvocation input,Object listener,Packet<?> packet){
        if(input==null||input.window==null)return null;var w=input.window;
        if(w.host.current!=w||listener!=w.spec.listener()||!(packet instanceof ServerboundContainerClickPacket p))return null;
        int source=p.slotNum()==w.spec.sourceMenuSlot()?w.spec.source():-1;
        boolean scope=input.owned&&input.gameMode==w.c.gameMode&&input.player==w.c.player&&w.base();
        var journal=w.host.journal.beginSend(w.journal,System.nanoTime(),p.containerId(),p.stateId(),source,p.buttonNum(),p.containerInput()==ContainerInput.SWAP,
            inventory(w.c),cursor(w.c),selected(w.c),scope);
        return new SendInvocation(input,w,packet,listener,journal);
    }
    public static void finishSend(SendInvocation call,boolean normal){
        if(call==null)return;var w=call.window;if(w.host.current!=w||call.listener!=w.spec.listener())return;
        w.host.journal.finishSend(call.journal,System.nanoTime(),normal,w.base(),inventory(w.c),cursor(w.c),selected(w.c));
    }
    public static final class ReceiveInvocation {
        private final Window window;private final Object packet,listener;private final PestInventoryRestorationJournal.Receive<Cell> journal;
        private final int menu,revision;private final Map<Integer,Cell> payload;private final boolean completePayload;
        private ReceiveInvocation(Window window,Object packet,Object listener,PestInventoryRestorationJournal.Receive<Cell> journal,int menu,int revision,Map<Integer,Cell> payload,boolean completePayload){
            this.window=window;this.packet=packet;this.listener=listener;this.journal=journal;this.menu=menu;this.revision=revision;this.payload=Map.copyOf(payload);this.completePayload=completePayload;
        }
    }
    public static ReceiveInvocation beginReceive(Object listener,Packet<?> packet){
        var c=Minecraft.getInstance();if(c==null||!c.isSameThread())return null;var host=HOSTS.get(c);var w=host==null?null:host.current;
        if(w==null||listener!=c.getConnection()||!(packet instanceof ClientboundContainerSetContentPacket||packet instanceof ClientboundContainerSetSlotPacket))return null;
        // Copy the actual wire payload at native entry before vanilla or caller callbacks can mutate stacks.
        var payload=new HashMap<Integer,Cell>();int menu=-1,revision=-1;boolean complete=true;
        if(packet instanceof ClientboundContainerSetContentPacket p){
            menu=p.containerId();revision=p.stateId();complete=p.carriedItem().isEmpty();
            for(int i=0;i<36;i++){int slot=w.spec.menuSlot(i);if(slot<0||slot>=p.items().size()){complete=false;break;}payload.put(i,new Cell(p.items().get(slot)));}
        }else if(packet instanceof ClientboundContainerSetSlotPacket p){
            menu=p.getContainerId();revision=p.getStateId();int index=w.spec.inventorySlot(p.getSlot());if(index>=0)payload.put(index,new Cell(p.getItem()));
        }
        var journal=host.journal.beginReceive(w.journal,System.nanoTime(),w.base()&&w.spec.matchesAfter()&&selected(c)==w.journal.selected());
        return new ReceiveInvocation(w,packet,listener,journal,menu,revision,payload,complete);
    }
    public static void finishReceive(ReceiveInvocation call,boolean normal){
        if(call==null)return;var w=call.window;if(w.host.current!=w||call.listener!=w.c.getConnection())return;
        boolean scope=call.completePayload&&w.base()&&w.spec.matchesAfter()&&selected(w.c)==w.journal.selected()
            &&w.c.player.inventoryMenu.getStateId()==call.revision;
        var after=inventory(w.c);var carried=cursor(w.c);
        boolean accepted=w.host.journal.finishReceive(call.journal,System.nanoTime(),normal,scope,call.menu,call.revision,call.payload,after,carried,selected(w.c));
        if(accepted&&w.host.current==w&&w.base()&&w.spec.matchesAfter()&&w.c.player.inventoryMenu.getStateId()==call.revision){
            var actual=new AuthoritativeRestorationReceipt(w,call.packet,call.listener,call.revision,after,carried);w.authoritative=actual;
            // Passive exact old-window callback: real after-native packet output, never a synthetic ACK or new scope lookup.
            var permit=w.permit;if(permit!=null)try{permit.observeAuthoritativeRestoration(actual);}catch(Throwable notificationFailure){/* Preserve original native packet result. */}
        }
    }
}
