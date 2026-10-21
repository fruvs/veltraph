package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.InventoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft-backed {@link InventoryService}. Reads the hotbar and changes the held slot exactly as a
 * player pressing a hotbar key would — {@code setSelectedSlot} plus the matching held-slot packet — so
 * the server registers the tool. It never opens or clicks inventory GUI slots. All reads/writes are
 * defensive; no player → safe no-op.
 */
public final class McInventoryService implements InventoryService {

    private final Minecraft client;
    private NativeCarryScope nativeCarryScope;
    /** Finishing opens this scope; generic callers retain their original packet path. */
    public final class NativeCarryScope implements AutoCloseable {
        private final java.util.function.BooleanSupplier finalLeaf;private final NativeCarryScope previous;private boolean closed,claimed;
        private NativeCarryScope(java.util.function.BooleanSupplier finalLeaf){this.finalLeaf=finalLeaf;previous=nativeCarryScope;}
        private boolean current(){return !closed&&nativeCarryScope==this;}
        @Override public void close(){closed=true;if(nativeCarryScope==this){nativeCarryScope=previous;while(nativeCarryScope!=null&&nativeCarryScope.closed)nativeCarryScope=nativeCarryScope.previous;}}
    }
    private NativeCarryScope claimNativeCarryScope(){
        var scope=nativeCarryScope;if(scope==null||scope.closed||scope.claimed)return null;scope.claimed=true;return scope;
    }
    public NativeCarryScope nativeCarrySynchronization(java.util.function.BooleanSupplier finalLeaf){
        if(!client.isSameThread()||finalLeaf==null)throw new IllegalStateException("Native carry scope requires actual client custody");
        var scope=new NativeCarryScope(finalLeaf);nativeCarryScope=scope;return scope;
    }


    public McInventoryService(Minecraft client) {
        this.client = client;
    }

    @Override
    public int selectedSlot() {
        try {
            LocalPlayer p = client.player;
            return p == null ? 0 : p.getInventory().getSelectedSlot();
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public void selectSlot(int slot) {
        if (slot < 0 || slot > 8) {
            return;
        }
        try {
            LocalPlayer p = client.player;
            if (p == null) {
                return;
            }
            dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,slot);
            p.getInventory().setSelectedSlot(slot);
            if (client.getConnection() != null) {
                // The same packet vanilla sends when the player scrolls/presses a hotbar key.
                client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            }
        } catch (Throwable ignored) {
            // Selection failed (no player / transient state) — caller's start check handles readiness.
        }
    }

    /** Metadata capture from this actual canonical native inventory; never a public fabricated item scope. */
    public McFarmToolReadiness captureFarmingTool(dev.veltraph.client.farming.module.FarmModule farm,int slot,String expectedName) {
        return McFarmToolReadiness.capture(client,this,farm,slot,expectedName);
    }

    public enum ScopedSlotStatus { CONFIRMED,REFUSED,FAILED }
    /** Actual local selection and packet submission are distinct; this private result is not server confirmation. */
    public static final class ScopedSlotReceipt {
        private final ScopedSlotStatus status;private final boolean selected,submitted;
        private final int before,after;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory nativeSelectionHistory;
        private ScopedSlotReceipt(ScopedSlotStatus status,boolean selected,boolean submitted,int before,int after){this(status,selected,submitted,before,after,null);}
        private ScopedSlotReceipt(ScopedSlotStatus status,boolean selected,boolean submitted,int before,int after,dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory nativeSelectionHistory){
            this.status=status;this.selected=selected;this.submitted=submitted;this.before=before;this.after=after;this.nativeSelectionHistory=nativeSelectionHistory;
        }
        /** Only this actual private selectSlotScoped invocation may attach an observed native setter history. */
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory nativeSelectionHistory(){return nativeSelectionHistory;}
        public ScopedSlotStatus status(){return status;}public boolean confirmed(){return status==ScopedSlotStatus.CONFIRMED;}
        public boolean localSelected(){return selected;}public boolean packetSubmitted(){return submitted;}
        public int expectedSlot(){return before;}public int requestedSlot(){return after;}
    }
    /** Invocation-private linkage, never a public caller-supplied permission or guessed current-version refresh. */
    private static final class ScopedSlotHistory {
        private final Inventory inventory;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory before;
        private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory after;
        private boolean nativeSetterReturned;
        private ScopedSlotHistory(Inventory inventory){this.inventory=inventory;before=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);}
        private boolean beforeCurrent(){return before==null||before.current();}
        private void nativeSetterReturned(){nativeSetterReturned=true;after=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);}
        private boolean afterCurrent(){return before==null||nativeSetterReturned&&after!=null&&after.followsExactlyOneSetter(before);}
        private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory exact(){return before!=null&&nativeSetterReturned&&after!=null&&after.followsExactlyOneSetter(before)?after:null;}
    }
    /** Observer precedes actual custody and callback-free native leaf; the post-setter fence expects the new slot. */
    public ScopedSlotReceipt selectSlotScoped(int expectedCurrent,int next,java.util.function.BooleanSupplier beforeSetterFence,java.util.function.BooleanSupplier afterSetterFence){
        if(!client.isSameThread()||expectedCurrent<0||expectedCurrent>8||next<0||next>8||beforeSetterFence==null||afterSetterFence==null)
            return new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
        var synchronization=claimNativeCarryScope();
        try {var nativeLeaf=McScopedInputReadFence.capture(client);var p=client.player;var world=client.level;var connection=client.getConnection();
            if(nativeLeaf==null||p==null||world==null||connection==null||p.level()!=world||p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty())return new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
            var inventory=p.getInventory();var history=new ScopedSlotHistory(inventory);var publication=new McScopedCarriedItemSynchronization.Publication[1];
            var outcome=dev.veltraph.client.navigation.ScopedSlotPublication.apply(expectedCurrent,next,new dev.veltraph.client.navigation.ScopedSlotPublication.Effects(){
                public int slot(){return inventory.getSelectedSlot();}
                public void observe(){dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,next);}
                private boolean context(){return client.player==p&&client.level==world&&client.getConnection()==connection&&p.getInventory()==inventory
                    &&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty();}
                public boolean beforeCurrent(){return context()&&dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(beforeSetterFence,()->context()&&inventory.getSelectedSlot()==expectedCurrent&&nativeLeaf.current()&&history.beforeCurrent());}
                public void select(int slot){inventory.setSelectedSlot(slot);history.nativeSetterReturned();}
                public boolean afterCurrent(){return context()&&dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(afterSetterFence,()->context()&&inventory.getSelectedSlot()==next&&nativeLeaf.current()&&history.afterCurrent());}
                public void submit(int slot){
                    if(synchronization==null){connection.send(new ServerboundSetCarriedItemPacket(slot));return;}
                    java.util.function.BooleanSupplier trustedLeaf=()->context()&&nativeLeaf.current()&&history.afterCurrent()
                            &&inventory.getSelectedSlot()==next&&synchronization.current()
                            &&synchronization.finalLeaf.getAsBoolean()
                            &&context()&&nativeLeaf.current()&&history.afterCurrent()&&inventory.getSelectedSlot()==next&&synchronization.current();
                    publication[0]=McScopedCarriedItemSynchronization.publish(client,slot,this::afterCurrent,trustedLeaf,history.exact());
                    if(!publication[0].submitted)
                        throw new IllegalStateException("Native carry equality/no-op is not a packet submission");
                }
            });
            var status=switch(outcome.status()){case CONFIRMED->ScopedSlotStatus.CONFIRMED;case REFUSED->ScopedSlotStatus.REFUSED;case FAILED->ScopedSlotStatus.FAILED;};
            if(publication[0]!=null&&!publication[0].confirmed)status=ScopedSlotStatus.FAILED;
            boolean submitted=outcome.packetSubmitted()||publication[0]!=null&&publication[0].submitted;
            return new ScopedSlotReceipt(status,outcome.localSelected(),submitted,expectedCurrent,next,outcome.localSelected()?history.exact():null);
        }catch(RuntimeException unavailable){return new ScopedSlotReceipt(ScopedSlotStatus.FAILED,false,false,expectedCurrent,next);}
    }

    /** Genuine issued-return device selection only. The original ordinary overload above remains byte-exact. */
    public ScopedSlotReceipt selectSlotScoped(int expectedCurrent,int next,java.util.function.BooleanSupplier beforeSetterFence,java.util.function.BooleanSupplier afterSetterFence,
            dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.Snapshot.DeviceSlotCustody.SelectionCall actualSelection){
        if(actualSelection==null)return new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
        ScopedSlotReceipt returned=null;ScopedSlotHistory actualHistory=null;
        try {
        if(!client.isSameThread()||expectedCurrent<0||expectedCurrent>8||next<0||next>8||beforeSetterFence==null||afterSetterFence==null)
            return returned=new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
        var synchronization=claimNativeCarryScope();
        try {var nativeLeaf=McScopedInputReadFence.capture(client);var p=client.player;var world=client.level;var connection=client.getConnection();
            if(nativeLeaf==null||p==null||world==null||connection==null||p.level()!=world||p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty())return returned=new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
            var inventory=p.getInventory();var history=new ScopedSlotHistory(inventory);actualHistory=history;var publication=new McScopedCarriedItemSynchronization.Publication[1];
            var outcome=dev.veltraph.client.navigation.ScopedSlotPublication.apply(expectedCurrent,next,new dev.veltraph.client.navigation.ScopedSlotPublication.Effects(){
                public int slot(){return inventory.getSelectedSlot();}
                public void observe(){dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,next);}
                private boolean context(){return client.player==p&&client.level==world&&client.getConnection()==connection&&p.getInventory()==inventory
                    &&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty();}
                public boolean beforeCurrent(){return context()&&dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(beforeSetterFence,()->context()&&inventory.getSelectedSlot()==expectedCurrent&&nativeLeaf.current()&&history.beforeCurrent());}
                public void select(int slot){actualSelection.executeNativeSetter(McInventoryService.this,inventory,slot);history.nativeSetterReturned();}
                public boolean afterCurrent(){return context()&&dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(afterSetterFence,()->context()&&inventory.getSelectedSlot()==next&&nativeLeaf.current()&&history.afterCurrent());}
                public void submit(int slot){
                    if(synchronization==null){connection.send(new ServerboundSetCarriedItemPacket(slot));return;}
                    java.util.function.BooleanSupplier trustedLeaf=()->context()&&nativeLeaf.current()&&history.afterCurrent()
                            &&inventory.getSelectedSlot()==next&&synchronization.current()
                            &&synchronization.finalLeaf.getAsBoolean()
                            &&context()&&nativeLeaf.current()&&history.afterCurrent()&&inventory.getSelectedSlot()==next&&synchronization.current();
                    publication[0]=McScopedCarriedItemSynchronization.publish(client,slot,this::afterCurrent,trustedLeaf,history.exact());
                    if(!publication[0].submitted)
                        throw new IllegalStateException("Native carry equality/no-op is not a packet submission");
                }
            });
            var status=switch(outcome.status()){case CONFIRMED->ScopedSlotStatus.CONFIRMED;case REFUSED->ScopedSlotStatus.REFUSED;case FAILED->ScopedSlotStatus.FAILED;};
            if(publication[0]!=null&&!publication[0].confirmed)status=ScopedSlotStatus.FAILED;
            boolean submitted=outcome.packetSubmitted()||publication[0]!=null&&publication[0].submitted;
            return returned=new ScopedSlotReceipt(status,outcome.localSelected(),submitted,expectedCurrent,next,outcome.localSelected()?history.exact():null);
        }catch(RuntimeException unavailable){return returned=new ScopedSlotReceipt(ScopedSlotStatus.FAILED,
                actualHistory!=null&&actualHistory.nativeSetterReturned,false,expectedCurrent,next,actualHistory==null?null:actualHistory.exact());}
        }finally{
            try{
                if(returned==null)returned=new ScopedSlotReceipt(ScopedSlotStatus.FAILED,
                        actualHistory!=null&&actualHistory.nativeSetterReturned,false,expectedCurrent,next,actualHistory==null?null:actualHistory.exact());
                actualSelection.complete(returned);
            }catch(RuntimeException|Error bookkeepingUnavailable){
                // No completion/history/allocation failure may replace an original native Error.
                try{actualSelection.complete(null);}catch(RuntimeException|Error ignored){ }
            }
        }
    }

    /** Genuine normal-return private Leg setter call only; existing ordinary and issued overloads stay unchanged. */
    public ScopedSlotReceipt selectSlotScoped(int expectedCurrent,int next,java.util.function.BooleanSupplier beforeSetterFence,java.util.function.BooleanSupplier afterSetterFence,
            dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.NormalReturnSelectionCall actualSelection){
        if(actualSelection==null)return new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
        ScopedSlotReceipt returned=null;ScopedSlotHistory actualHistory=null;
        try {
        if(!client.isSameThread()||expectedCurrent<0||expectedCurrent>8||next<0||next>8||beforeSetterFence==null||afterSetterFence==null)
            return returned=new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
        var synchronization=claimNativeCarryScope();
        try {var nativeLeaf=McScopedInputReadFence.capture(client);var p=client.player;var world=client.level;var connection=client.getConnection();
            if(nativeLeaf==null||p==null||world==null||connection==null||p.level()!=world||p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty())return returned=new ScopedSlotReceipt(ScopedSlotStatus.REFUSED,false,false,expectedCurrent,next);
            var inventory=p.getInventory();var history=new ScopedSlotHistory(inventory);actualHistory=history;var publication=new McScopedCarriedItemSynchronization.Publication[1];
            var outcome=dev.veltraph.client.navigation.ScopedSlotPublication.apply(expectedCurrent,next,new dev.veltraph.client.navigation.ScopedSlotPublication.Effects(){
                public int slot(){return inventory.getSelectedSlot();}
                public void observe(){dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,next);}
                private boolean context(){return client.player==p&&client.level==world&&client.getConnection()==connection&&p.getInventory()==inventory
                    &&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty();}
                public boolean beforeCurrent(){return context()&&dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(beforeSetterFence,()->context()&&inventory.getSelectedSlot()==expectedCurrent&&nativeLeaf.current()&&history.beforeCurrent());}
                public void select(int slot){actualSelection.executeNativeSetter(McInventoryService.this,inventory,slot);history.nativeSetterReturned();}
                public boolean afterCurrent(){return context()&&dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(afterSetterFence,()->context()&&inventory.getSelectedSlot()==next&&nativeLeaf.current()&&history.afterCurrent());}
                public void submit(int slot){
                    if(synchronization==null){connection.send(new ServerboundSetCarriedItemPacket(slot));return;}
                    java.util.function.BooleanSupplier trustedLeaf=()->context()&&nativeLeaf.current()&&history.afterCurrent()
                            &&inventory.getSelectedSlot()==next&&synchronization.current()
                            &&synchronization.finalLeaf.getAsBoolean()
                            &&context()&&nativeLeaf.current()&&history.afterCurrent()&&inventory.getSelectedSlot()==next&&synchronization.current();
                    publication[0]=McScopedCarriedItemSynchronization.publish(client,slot,this::afterCurrent,trustedLeaf,history.exact());
                    if(!publication[0].submitted)
                        throw new IllegalStateException("Native carry equality/no-op is not a packet submission");
                }
            });
            var status=switch(outcome.status()){case CONFIRMED->ScopedSlotStatus.CONFIRMED;case REFUSED->ScopedSlotStatus.REFUSED;case FAILED->ScopedSlotStatus.FAILED;};
            if(publication[0]!=null&&!publication[0].confirmed)status=ScopedSlotStatus.FAILED;
            boolean submitted=outcome.packetSubmitted()||publication[0]!=null&&publication[0].submitted;
            return returned=new ScopedSlotReceipt(status,outcome.localSelected(),submitted,expectedCurrent,next,outcome.localSelected()?history.exact():null);
        }catch(RuntimeException unavailable){return returned=new ScopedSlotReceipt(ScopedSlotStatus.FAILED,
                actualHistory!=null&&actualHistory.nativeSetterReturned,false,expectedCurrent,next,actualHistory==null?null:actualHistory.exact());}
        }finally{
            try{
                if(returned==null)returned=new ScopedSlotReceipt(ScopedSlotStatus.FAILED,
                        actualHistory!=null&&actualHistory.nativeSetterReturned,false,expectedCurrent,next,actualHistory==null?null:actualHistory.exact());
                actualSelection.complete(returned);
            }catch(RuntimeException|Error bookkeepingUnavailable){
                // Preserve an original native Error even if failed receipt allocation cannot finish.
                actualSelection.loseCustodyAfterCompletionFailure();
            }
        }
    }

    private GreenhouseSelectionStart greenhouseSelectionStart;
    public final class GreenhouseSelectionStart {
        private final dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent;
        private final dev.veltraph.client.core.ModuleManager root;private final dev.veltraph.client.core.ModuleManager.OwnEnableReceipt enable;
        private final Object player,world,connection,config,options,gameMode;private final Inventory inventory;private final int originalSlot;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory initialHistory;private boolean consumed;
        private GreenhouseSelectionStart(dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent,
                dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.OwnEnableReceipt enable){
            this.parent=parent;this.root=root;this.enable=enable;player=client.player;world=client.level;connection=client.getConnection();
            config=dev.veltraph.client.VeltraphBootstrap.config();options=client.options;gameMode=client.gameMode;inventory=client.player.getInventory();originalSlot=inventory.getSelectedSlot();
            initialHistory=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);
        }
        private boolean current(){return !consumed&&greenhouseSelectionStart==this&&client.isSameThread()&&enable.current()
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.get(parent.id()).orElse(null)==parent
            &&dev.veltraph.client.VeltraphBootstrap.config()==config&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.options==options&&client.gameMode==gameMode&&client.player!=null&&client.player.getInventory()==inventory
            &&initialHistory!=null&&initialHistory.current()&&inventory.getSelectedSlot()==originalSlot&&McInputService.canonicalFarmInventoryCurrent(McInventoryService.this,client);}
    }
    /** Only a real currently executing setEnabled(true) invocation can mint a one-use selection start. */
    public GreenhouseSelectionStart beginGreenhouseSelection(dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent){
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var enable=root==null?null:root.currentOwnEnableReceipt(parent).orElse(null);
        if(parent==null||enable==null||!enable.invocationCurrent()||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null
            ||!McInputService.canonicalFarmInventoryCurrent(this,client))return null;
        var previous=greenhouseSelectionStart;
        if(previous!=null&&previous.parent==parent&&previous.root==root&&previous.enable==enable)return null;
        var result=new GreenhouseSelectionStart(parent,root,enable);
        if(result.initialHistory==null||!result.initialHistory.current()||!enable.invocationCurrent())return null;
        greenhouseSelectionStart=result;return result;
    }
    private GreenhouseSlotCustody greenhouseSlotCustody;
    /** Actual canonical parent flow, never caller-constructed history or restoration permission. */
    public final class GreenhouseSlotCustody {
        private final dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent;
        private final Object player,world,connection,options,gameMode,config;
        private final Inventory inventory;private final int originalSlot;
        private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory ownHistory;
        private int ownSlot;private boolean retired;
        private GreenhouseSlotCustody(dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent,int originalSlot,
                dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,
                dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,GreenhouseSelectionStart start){
            this.parent=parent;this.originalSlot=originalSlot;ownSlot=originalSlot;this.root=root;this.lifecycle=lifecycle;this.input=input;
            player=start.player;world=start.world;connection=start.connection;options=start.options;gameMode=start.gameMode;config=start.config;inventory=start.inventory;
            ownHistory=start.initialHistory;
        }
        private boolean contextCurrent(){return !retired&&greenhouseSlotCustody==this&&client.isSameThread()
            &&client==Minecraft.getInstance()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.options==options&&client.gameMode==gameMode&&client.player!=null&&client.player.getInventory()==inventory
            &&dev.veltraph.client.VeltraphBootstrap.config()==config&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root
            &&McInputService.canonicalFarmInventoryCurrent(McInventoryService.this,client)&&root.get(parent.id()).orElse(null)==parent
            &&root.ownCleanupLifecycleCurrent(lifecycle,parent)&&input.current()&&input.active()&&parent.id().equals(input.ownerModuleId());}
        private boolean historyCurrent(){return contextCurrent()&&ownHistory!=null&&ownHistory.current()&&inventory.getSelectedSlot()==ownSlot;}
        private boolean actionCurrent(){return historyCurrent()&&root.lifecycleCurrent(lifecycle)&&parent.isEnabled();}
        private boolean menuCurrent(){return contextCurrent()&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty();}
        /** Local setter and submission are distinct; this result is never server acknowledgment. */
        public boolean select(int next,java.util.function.BooleanSupplier sourceFence){
            return publish(next,sourceFence,false);
        }
        private boolean publish(int next,java.util.function.BooleanSupplier sourceFence,boolean cleanup){
            if(next<0||next>8||sourceFence==null||!historyCurrent()||!cleanup&&!actionCurrent())return false;
            if(next==ownSlot){
                // A genuine own no-op retains only original custody; source callbacks cannot mint new history.
                try{return sourceFence.getAsBoolean()&&historyCurrent()&&menuCurrent()&&(cleanup||actionCurrent());}catch(RuntimeException unavailable){return false;}
            }
            var before=ownHistory;int expected=ownSlot;
            var nativeLeaf=McScopedInputReadFence.capture(client);
            if(nativeLeaf==null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty())return false;
            var outcome=dev.veltraph.client.navigation.ScopedSlotPublication.apply(expected,next,new dev.veltraph.client.navigation.ScopedSlotPublication.Effects(){
                public int slot(){return inventory.getSelectedSlot();}
                public void observe(){dev.veltraph.client.observation.unexpected.UnexpectedActions.selectedSlot(client,next);}
                public boolean beforeCurrent(){return sourceFence.getAsBoolean()&&historyCurrent()&&ownHistory==before&&nativeLeaf.current()&&menuCurrent()&&(cleanup||actionCurrent());}
                public void select(int slot){
                    inventory.setSelectedSlot(slot);
                    // Exact history is retained immediately after native normal return, before any source callback.
                    var actual=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);
                    if(contextCurrent()&&ownHistory==before&&actual!=null&&actual.followsExactlyOneSetter(before)&&inventory.getSelectedSlot()==next){ownHistory=actual;ownSlot=next;}
                }
                public boolean afterCurrent(){return sourceFence.getAsBoolean()&&historyCurrent()&&ownSlot==next&&nativeLeaf.current()&&menuCurrent()&&(cleanup||actionCurrent());}
                public void submit(int slot){client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));}
            });
            return outcome.confirmed()&&outcome.packetSubmitted()&&historyCurrent()&&(cleanup||actionCurrent());
        }
        /** Cleanup-only original slot restoration, retaining exact native lineage even before action result returns. */
        public boolean restoreOriginal(){
            if(!historyCurrent())return false;
            return publish(originalSlot,this::contextCurrent,true);
        }
    }
    /** Mint once at actual parent start; exact registered canonical service and native initial history required. */
    public GreenhouseSlotCustody captureGreenhouseSlotCustody(dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent,int originalSlot,GreenhouseSelectionStart start){
        if(start==null||start.parent!=parent||!start.current()||start.originalSlot!=originalSlot||parent==null||originalSlot<0||originalSlot>8||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null
            ||!McInputService.canonicalFarmInventoryCurrent(this,client)||client.player.getInventory().getSelectedSlot()!=originalSlot)return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        var lifecycle=root==null?null:root.lifecycleSnapshot();
        if(root==null||lifecycle==null||!lifecycle.known()||root.get(parent.id()).orElse(null)!=parent||root.inputOwner().orElse(null)!=parent
            ||!parent.isEnabled()||input==null||!input.active()||!input.current()||!parent.id().equals(input.ownerModuleId()))return null;
        // Source input-owner callbacks cannot replace original start identities or native history.
        if(!start.current())return null;
        // Consume before any snapshot/current provider; failures cannot retry by adopting new current history.
        start.consumed=true;
        var previous=greenhouseSlotCustody;
        var result=new GreenhouseSlotCustody(parent,originalSlot,root,lifecycle,input,start);greenhouseSlotCustody=result;
        if(!result.actionCurrent()){if(greenhouseSlotCustody==result)greenhouseSlotCustody=previous;return null;}return result;
    }

    @Override
    public List<String> hotbarItemNames() {
        List<String> out = new ArrayList<>(9);
        try {
            LocalPlayer p = client.player;
            if (p == null) {
                fillEmpty(out);
                return out;
            }
            Inventory inv = p.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getItem(i);
                out.add(stack == null || stack.isEmpty()
                        ? "" : stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable t) {
            out.clear();
            fillEmpty(out);
        }
        return out;
    }

    private static void fillEmpty(List<String> out) {
        for (int i = 0; i < 9; i++) {
            out.add("");
        }
    }
}
