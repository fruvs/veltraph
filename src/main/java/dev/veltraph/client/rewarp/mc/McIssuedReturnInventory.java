package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import static dev.veltraph.client.rewarp.mc.IssuedReturnNativeFields.*;

/** Private capture from actual native inventory; final validation contains no codec or provider query. */
public final class McIssuedReturnInventory {
    private McIssuedReturnInventory() {}
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static boolean nativeList(List<?> values){return values.getClass()==ArrayList.class||values.getClass()==java.util.Arrays.asList(0).getClass();}
    private static ItemStack equipmentItem(java.util.EnumMap<?,?> map,EquipmentSlot slot){Object value=map.get(slot);return value==null&&!map.containsKey(slot)?ItemStack.EMPTY:value instanceof ItemStack stack?stack:null;}
    public static final class Snapshot implements AutoCloseable {
        private final Minecraft client;
        private final McSuspendedFarmReturnCoordinator.SourceHeader source;
        private final McSuspendedFarmReturnCoordinator.JoinCall joined;
        private final LocalPlayer player;
        private final Object world,listener,inventory,equipment,listCarrier,listBacking,equipmentMap,menu,cursor;
        private final int selected;
        private final List<IssuedReturnStackGraph.Stack> nativeStacks;
        private final List<NativeStackIdentity.Snapshot> semantic;
        private final IssuedReturnMutationJournal.Scope journal;
        private Snapshot(Minecraft client,McSuspendedFarmReturnCoordinator.SourceHeader source,
                         McSuspendedFarmReturnCoordinator.JoinCall joined,LocalPlayer player,Object inventory,Object equipment,
                         Object listCarrier,Object listBacking,Object equipmentMap,Object menu,Object cursor,int selected,
                         List<IssuedReturnStackGraph.Stack> nativeStacks,List<NativeStackIdentity.Snapshot> semantic,
                         IssuedReturnMutationJournal.Scope journal){
            this.client=client;this.source=source;this.joined=joined;this.player=player;world=client.level;listener=client.getConnection();
            this.inventory=inventory;this.equipment=equipment;this.listCarrier=listCarrier;this.listBacking=listBacking;this.equipmentMap=equipmentMap;
            this.menu=menu;this.cursor=cursor;this.selected=selected;this.nativeStacks=List.copyOf(nativeStacks);this.semantic=List.copyOf(semantic);this.journal=journal;
            selectedHistory=source==null?null:dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);
        }
        private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selectedHistory;
        private DeviceSlotCustody deviceCustody;
        /** Initial source-bound capture only. No Boolean fence can mint selected-slot custody. */
        DeviceSlotCustody captureDeviceSlotCustody(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual) {
            if(source==null||actual==null||!actual.belongsTo(source)||!actual.nativeCurrent()||!currentFinal()
                ||actual.transitIdentity()==null||actual.transitIdentity().sourceHeader()!=source
                ||actual.transitIdentity().originalSlot()!=selected||actual.expectedSlot()!=selected
                ||actual.requestedSlot()<0||actual.requestedSlot()>8||actual.requestedSlot()==selected
                ||actual.inventoryServiceIdentity()!=source.farmServicesIdentity().inventory()
                ||selectedHistory==null||!selectedHistory.current()
                ||deviceCustody!=null&&!deviceCustody.restoredCurrent())return null;
            var captured=new DeviceSlotCustody(actual);deviceCustody=captured;
            return actual.custodySourceCurrent()&&contentCurrent()?captured:null;
        }
        DeviceSlotCustody retainedDeviceSlotCustody(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit exact) {
            var retained=deviceCustody;return retained!=null&&retained.transit==exact?retained:null;
        }
        /** Required separately at arrival/resume; temporary device selection cannot satisfy it. */
        boolean originalSelectedSlotRestored() {
            return contentCurrent()&&(deviceCustody==null||deviceCustody.restoredCurrent());
        }
        private boolean selectedSlotCurrent(Items items) {
            if(source==null)return items.veltraph$returnSelected()==selected;
            return selectedHistory!=null&&selectedHistory.current()
                &&(deviceCustody==null?items.veltraph$returnSelected()==selected:deviceCustody.nativeSelectedCurrent(items));
        }
        public final class DeviceSlotCustody {
            private final McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit;
            private final McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall initialCall;
            private final dev.veltraph.client.service.mc.McInventoryService service;
            private final int temporary;
            private int expectedCurrent=selected;
            private SelectionCall selection,restoration;
            private boolean lost,restored;
            private DeviceSlotCustody(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual) {
                initialCall=actual;transit=actual.transitIdentity();service=actual.inventoryServiceIdentity();temporary=actual.requestedSlot();
            }
            public SelectionCall beginSelection(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual) {
                boolean restore=selection!=null;
                if(lost||restored||deviceCustody!=this||actual==null||actual.transitIdentity()!=transit
                    ||!actual.belongsTo(source)||actual.inventoryServiceIdentity()!=service||!actual.custodySourceCurrent()
                    ||!restore&&actual!=initialCall||actual.expectedSlot()!=expectedCurrent||actual.requestedSlot()!=(restore?selected:temporary)
                    ||restore&&(restoration!=null||selection.receipt==null||!selection.nativeReturned)
                    ||!(restore?actual.cleanupCurrent():actual.nativeCurrent())||!contentCurrent())return null;
                var call=new SelectionCall(actual,restore);if(restore)restoration=call;else selection=call;return call;
            }
            private boolean nativeSelectedCurrent(Items items) {
                if(lost||deviceCustody!=this||items.veltraph$returnSelected()!=expectedCurrent)return false;
                if(restored)return expectedCurrent==selected&&restoration!=null&&restoration.confirmedReceipt();
                return transit.custodySourceCurrent()&&(expectedCurrent==selected||selection!=null&&selection.nativeReturned)
                    &&(restoration==null||!restoration.completed||restoration.confirmedReceipt());
            }
            public boolean readyCurrent() {
                return !lost&&!restored&&deviceCustody==this&&selection!=null&&selection.confirmedReceipt()
                    &&expectedCurrent==temporary&&transit.custodySourceCurrent()&&contentCurrent();
            }
            public boolean restoredCurrent() {
                return !lost&&restored&&deviceCustody==this&&expectedCurrent==selected&&restoration!=null
                    &&restoration.confirmedReceipt()&&selectedHistory!=null&&selectedHistory.current()
                    &&inventory instanceof Items items&&items.veltraph$returnSelected()==selected&&journal.current();
            }
            /** Immutable actual issued selection/restoration lineage, not a current-slot permission. */
            public final class RestorationWitness {
                private final SelectionCall selectedCall=selection,restoredCall=restoration;
                private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory original=
                    selectedCall==null?null:selectedCall.beforeHistory;
                private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory returned=
                    restoredCall==null?null:restoredCall.returnedHistory;
                private RestorationWitness(){}
                public McSuspendedFarmReturnCoordinator.SourceHeader sourceHeaderIdentity(){return source;}
                public McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transitIdentity(){return transit;}
                public int originalSlot(){return selected;}
                public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory(){return original;}
                public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory restoredHistory(){return returned;}
                public boolean retainedLineageCurrent(){return !lost&&restored&&selection==selectedCall&&restoration==restoredCall
                    &&selectedCall!=null&&restoredCall!=null&&!selectedCall.restore&&restoredCall.restore
                    &&selectedCall.before==selected&&selectedCall.after==temporary&&restoredCall.before==temporary&&restoredCall.after==selected
                    &&selectedCall.confirmedReceipt()&&restoredCall.confirmedReceipt()
                    &&original!=null&&returned!=null&&original==selectedCall.beforeHistory&&returned==restoredCall.returnedHistory
                    &&selectedCall.returnedHistory!=null&&restoredCall.beforeHistory!=null
                    &&restoredCall.beforeHistory.sameStoredObservation(selectedCall.returnedHistory)
                    &&selectedCall.returnedHistory.followsExactlyOneStoredSetterObservation(original)
                    &&returned.followsExactlyOneStoredSetterObservation(restoredCall.beforeHistory);}
            }
            /** Read-only genuine receipt factory after both canonical native calls have completed. */
            public RestorationWitness captureRestorationWitness(){
                if(!restoredCurrent())return null;var actual=new RestorationWitness();return actual.retainedLineageCurrent()?actual:null;
            }
            public final class SelectionCall {
                private final McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual;
                private final boolean restore;
                private final int before,after;
                private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory beforeHistory=selectedHistory;
                private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory returnedHistory;
                private dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt receipt;
                private boolean attempted,nativeReturned,completed;
                private SelectionCall(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual,boolean restore) {
                    this.actual=actual;this.restore=restore;before=actual.expectedSlot();after=actual.requestedSlot();
                }
                /** Passive exact stored identity only; these observations cannot create a selection or permission. */
                public McSuspendedFarmReturnCoordinator.SourceHeader sourceHeaderIdentity(){return actual.sourceHeader();}
                public McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transitIdentity(){return actual.transitIdentity();}
                public boolean restoration(){return restore;}
                /** Exact already-issued call association only; no new selection or receipt can be minted. */
                public boolean matchesRequestCall(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall exact){return actual==exact&&custodySourceCurrent();}
                public boolean custodySourceCurrent() {
                    return !lost&&!completed&&deviceCustody==DeviceSlotCustody.this
                        &&(restore?restoration==this:selection==this)&&actual.transitIdentity()==transit
                        &&actual.belongsTo(source)&&actual.inventoryServiceIdentity()==service&&actual.custodySourceCurrent();
                }
                /** Called by the canonical scoped inventory Effects.select, after observers and fences. */
                public void executeNativeSetter(dev.veltraph.client.service.mc.McInventoryService exact,
                        net.minecraft.world.entity.player.Inventory exactInventory,int exactSlot) {
                    boolean canonicalCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                        var first=stream.filter(f->f.getDeclaringClass()!=SelectionCall.class).findFirst().orElse(null);
                        return first!=null&&first.getDeclaringClass().getEnclosingClass()==dev.veltraph.client.service.mc.McInventoryService.class
                            &&first.getMethodName().equals("select");
                    });
                    if(!canonicalCaller||attempted||exact!=service||exactInventory!=inventory||exactSlot!=after
                        ||!(restore?actual.cleanupCurrent():actual.nativeCurrent())
                        ||!custodySourceCurrent()||!contentCurrent()||expectedCurrent!=before||beforeHistory==null||!beforeHistory.current()) {
                        lost=true;throw new IllegalStateException("Original return device selected-slot setter refused");
                    }
                    attempted=true;
                    var window=journal.beginNativeSelectedSetter(inventory,after);
                    if(window==null){lost=true;throw new IllegalStateException("Original return device selected-slot window refused");}
                    boolean normal=false;
                    try {
                        exactInventory.setSelectedSlot(after);
                        returnedHistory=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);
                        normal=returnedHistory!=null&&returnedHistory.followsExactlyOneSetter(beforeHistory)
                            &&exactInventory.getSelectedSlot()==after;
                    } finally {
                        if(!journal.finishNativeSelectedSetter(window,normal))lost=true;
                    }
                    if(!normal||lost){lost=true;throw new IllegalStateException("Original return device native selection history refused");}
                    selectedHistory=returnedHistory;expectedCurrent=after;nativeReturned=true;
                }
                /** Only the same actual canonical service return may attach its immutable real receipt. */
                public void complete(dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt actualReceipt) {
                    try{completeActual(actualReceipt);}catch(RuntimeException|Error unavailable){lost=true;}
                }
                private void completeActual(dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt actualReceipt) {
                    boolean canonicalCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                        var first=stream.filter(f->f.getDeclaringClass()!=SelectionCall.class).findFirst().orElse(null);
                        return first!=null&&first.getDeclaringClass()==dev.veltraph.client.service.mc.McInventoryService.class;
                    });
                    if(!canonicalCaller||completed||!custodySourceCurrent()){lost=true;return;}
                    completed=true;receipt=actualReceipt;
                    if(receipt==null||!nativeReturned||receipt.expectedSlot()!=before||receipt.requestedSlot()!=after
                        ||!receipt.localSelected()||receipt.nativeSelectionHistory()==null
                        ||!receipt.nativeSelectionHistory().followsExactlyOneSetter(beforeHistory)
                        ||returnedHistory==null||!returnedHistory.current()) {lost=true;return;}
                    selectedHistory=receipt.nativeSelectionHistory();
                    if(restore){if(confirmedReceipt())restored=true;else lost=true;}
                }
                private boolean confirmedReceipt() {
                    return completed&&receipt!=null&&nativeReturned&&receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted()
                        &&receipt.nativeSelectionHistory()!=null&&receipt.nativeSelectionHistory().followsExactlyOneSetter(beforeHistory)
                        &&selectedHistory!=null&&selectedHistory.current();
                }
            }
        }
        /** Actual caller performs all other observers/providers BEFORE this final predicate. */
        public boolean currentFinal(){
            try{return (joined==null?source.currentRefusal():joined.currentRefusal())&&nativeSceneCurrent()&&contentCurrent()
                &&(joined==null?source.currentRefusal():joined.currentRefusal())&&journal.current();}
            catch(RuntimeException unsupported){return false;}
        }
        /** Historical native content fact only. It cannot authorize an action, rejoin or resume. */
        public boolean retiredContentCurrent(Minecraft exact){
            try{return exact==client&&client.isSameThread()&&nativeSceneCurrent()&&contentCurrent()&&journal.current();}
            catch(RuntimeException unsupported){return false;}
        }
        private boolean nativeSceneCurrent(){
            return client.player==player&&client.level==world&&client.getConnection()==listener&&player.containerMenu==menu&&player.inventoryMenu==menu
                &&((Object)player instanceof Player raw)&&raw.veltraph$returnInventory()==inventory
                &&((Object)player instanceof Living living)&&living.veltraph$returnEquipment()==equipment;
        }
        /** Original raw storage and graph only. No source/provider, native SDK scene getter or fresh snapshot. */
        private boolean finalStoredFactsCurrent(){
            return journal.current()&&client.player==player&&client.level==world&&player.connection==listener
                &&player.containerMenu==menu&&player.inventoryMenu==menu
                &&((Object)player instanceof Player raw)&&raw.veltraph$returnInventory()==inventory
                &&((Object)player instanceof Living living)&&living.veltraph$returnEquipment()==equipment
                &&contentCurrent()&&journal.current();
        }
        private boolean contentCurrent(){
            if(!journal.current()||!(inventory instanceof Items items)||!selectedSlotCurrent(items)
                ||items.veltraph$returnInventoryEquipment()!=equipment||items.veltraph$returnItems()!=listCarrier
                ||!(listCarrier instanceof ListFields raw)||raw.veltraph$returnList()!=listBacking
                ||!(listBacking instanceof List<?> backing)||!nativeList(backing)||backing.size()!=36
                ||!(equipment instanceof Equipment gear)||gear.veltraph$returnEquipmentItems()!=equipmentMap
                ||!(equipmentMap instanceof java.util.EnumMap<?,?> map)||!(menu instanceof Menu rawMenu)||rawMenu.veltraph$returnCursor()!=cursor)return false;
            for(int i=0;i<36;i++)if(backing.get(i)!=nativeStacks.get(i).nativeStack)return false;
            if(equipmentItem(map,EquipmentSlot.OFFHAND)!=nativeStacks.get(36).nativeStack)return false;
            for(int i=0;i<4;i++)if(equipmentItem(map,ARMOR[i])!=nativeStacks.get(37+i).nativeStack)return false;
            for(var stack:nativeStacks)if(!stack.current())return false;
            return journal.current();
        }
        /** Rebinds only through the coordinator's privately minted exact transport JoinCall. Never grants resume. */
        public Snapshot semanticMatchesJoined(McSuspendedFarmReturnCoordinator.JoinCall call){
            Snapshot next=null;
            try{
                if(call==null||joined!=null||call.sourceHeader()!=source||call.client()!=client||!call.currentRefusal()||!originalSelectedSlotRestored())return null;
                next=captureNative(client,source,call);
                if(next==null)return null;
                if(next.selected!=selected||!semantic.equals(next.semantic)||!contentCurrent()||!call.currentRefusal()||!next.currentFinal()){next.close();return null;}
                return next;
            }catch(RuntimeException unsupported){if(next!=null)next.close();return null;}
        }
        @Override public void close(){journal.close();}
    }
    /** Typed original retained HELPER_ACTIVE terminal resource observation only; this snapshot never grants resume or input. */
    public static final class RetainedMaintenanceSnapshot implements AutoCloseable {
        private final Minecraft client;
        private final FarmMaintenanceCoordinator.RetainedLoadoutOperation source;
        private final Snapshot nativeFacts;
        private final Thread thread=Thread.currentThread();
        private boolean closed;
        private RetainedMaintenanceSnapshot(Minecraft client,FarmMaintenanceCoordinator.RetainedLoadoutOperation source,Snapshot nativeFacts){
            this.client=client;this.source=source;this.nativeFacts=nativeFacts;
        }
        /** Pure final negative resource check after callers finish providers, mode queries and observers. */
        public boolean currentFinal(){
            try{return !closed&&Thread.currentThread()==thread&&client==Minecraft.getInstance()&&client.isSameThread()
                &&source.resourceSnapshotCurrent()&&nativeFacts.nativeSceneCurrent()&&nativeFacts.contentCurrent()
                &&source.resourceSnapshotCurrent()&&nativeFacts.journal.current();}
            catch(RuntimeException unsupported){return false;}
        }
        /** Last negative resource veto after all source/root/pose callbacks, immediately before selected FIELD. */
        public boolean finalStoredFactsCurrent(){
            try{return !closed&&Thread.currentThread()==thread&&nativeFacts.client==client
                &&nativeFacts.finalStoredFactsCurrent();}
            catch(RuntimeException unsupported){return false;}
        }
        @Override public void close(){closed=true;nativeFacts.close();}
    }
    /** Only the actual private Coordinator resource-capture window can mint this negative observation. */
    public static RetainedMaintenanceSnapshot captureRetainedMaintenance(Minecraft client,FarmMaintenanceCoordinator.RetainedLoadoutOperation source){
        IssuedReturnMutationJournal.Scope journal=null;
        try{
            if(client==null||source==null||client!=Minecraft.getInstance()||!client.isSameThread()||!source.resourceCaptureCurrent())return null;
            journal=IssuedReturnMutationJournal.open();if(journal==null)return null;
            if(!source.resourceCaptureCurrent()||client.player==null||client.level==null||client.getConnection()==null)throw new IllegalStateException();
            var player=client.player;if(player.getClass()!=LocalPlayer.class)throw new IllegalStateException();
            if(!((Object)player instanceof Player rawPlayer)||!((Object)player instanceof Living living))throw new IllegalStateException();
            var inventory=rawPlayer.veltraph$returnInventory();var equipment=living.veltraph$returnEquipment();
            if(inventory==null||inventory.getClass()!=net.minecraft.world.entity.player.Inventory.class||equipment==null||equipment.getClass()!=net.minecraft.world.entity.EntityEquipment.class
                ||player.inventoryMenu==null||player.inventoryMenu.getClass()!=net.minecraft.world.inventory.InventoryMenu.class)throw new IllegalStateException();
            if(!((Object)inventory instanceof Items items)||items.veltraph$returnInventoryEquipment()!=equipment||items.veltraph$returnSelected()<0||items.veltraph$returnSelected()>8
                ||!((Object)items.veltraph$returnItems() instanceof ListFields rawList)||!(rawList.veltraph$returnList() instanceof List<?> backing)||!nativeList(backing)||backing.size()!=36
                ||!((Object)equipment instanceof Equipment rawEquipment)||player.containerMenu!=player.inventoryMenu||!((Object)player.inventoryMenu instanceof Menu rawMenu))throw new IllegalStateException();
            var gear=rawEquipment.veltraph$returnEquipmentItems();if(gear==null||gear.getClass()!=java.util.EnumMap.class)throw new IllegalStateException();
            var menu=player.inventoryMenu;var cursor=rawMenu.veltraph$returnCursor();
            if(cursor!=ItemStack.EMPTY)throw new IllegalStateException("Native empty cursor required");
            var watched=new ArrayList<Object>();watched.add(inventory);watched.add(equipment);watched.add(items.veltraph$returnItems());watched.add(menu);
            var stacks=new ArrayList<IssuedReturnStackGraph.Stack>();
            for(int n=0;n<36;n++){if(!(backing.get(n) instanceof ItemStack stack))throw new IllegalStateException();stacks.add(IssuedReturnStackGraph.capture(stack,watched));}
            stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,EquipmentSlot.OFFHAND),watched));
            for(var slot:ARMOR)stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,slot),watched));
            var cursorGraph=IssuedReturnStackGraph.capture(cursor,watched);if(cursorGraph.count!=0)throw new IllegalStateException();stacks.add(cursorGraph);
            for(Object object:watched)if(!journal.watch(object))throw new IllegalStateException();
            if(!source.resourceCaptureCurrent()||items.veltraph$returnSelected()!=source.originalToolSlot())throw new IllegalStateException();
            // Raw content fact only: no codec, semantic permission, source/header transplant or joined scene is minted.
            var facts=new Snapshot(client,null,null,player,inventory,equipment,items.veltraph$returnItems(),backing,gear,menu,cursor,
                items.veltraph$returnSelected(),stacks,List.of(),journal);
            var result=new RetainedMaintenanceSnapshot(client,source,facts);
            if(!result.currentFinal()||!source.resourceCaptureCurrent())throw new IllegalStateException();
            journal=null;return result;
        }catch(RuntimeException unsupported){return null;}
        finally{if(journal!=null)journal.close();}
    }
    /** Typed original saved-return resource observation only; this snapshot never grants resume or input. */
    public static final class SavedMaintenanceSnapshot implements AutoCloseable {
        private final Minecraft client;
        private final FarmMaintenanceCoordinator.SavedMaintenanceResumeCall source;
        private final Snapshot nativeFacts;
        private final Thread thread=Thread.currentThread();
        private boolean closed;
        private SavedMaintenanceSnapshot(Minecraft client,FarmMaintenanceCoordinator.SavedMaintenanceResumeCall source,Snapshot nativeFacts){
            this.client=client;this.source=source;this.nativeFacts=nativeFacts;
        }
        /** Pure final negative resource check after callers finish providers, mode queries and observers. */
        public boolean currentFinal(){
            try{return !closed&&Thread.currentThread()==thread&&client==Minecraft.getInstance()&&client.isSameThread()
                &&source.resourceSnapshotCurrent()&&nativeFacts.nativeSceneCurrent()&&nativeFacts.contentCurrent()
                &&source.resourceSnapshotCurrent()&&nativeFacts.journal.current();}
            catch(RuntimeException unsupported){return false;}
        }
        @Override public void close(){closed=true;nativeFacts.close();}
    }
    /** Only the actual private Coordinator resource-capture window can mint this negative observation. */
    public static SavedMaintenanceSnapshot captureSavedMaintenance(Minecraft client,FarmMaintenanceCoordinator.SavedMaintenanceResumeCall source){
        IssuedReturnMutationJournal.Scope journal=null;
        try{
            if(client==null||source==null||client!=Minecraft.getInstance()||!client.isSameThread()||!source.resourceCaptureCurrent())return null;
            journal=IssuedReturnMutationJournal.open();if(journal==null)return null;
            if(!source.resourceCaptureCurrent()||client.player==null||client.level==null||client.getConnection()==null)throw new IllegalStateException();
            var player=client.player;if(player.getClass()!=LocalPlayer.class)throw new IllegalStateException();
            if(!((Object)player instanceof Player rawPlayer)||!((Object)player instanceof Living living))throw new IllegalStateException();
            var inventory=rawPlayer.veltraph$returnInventory();var equipment=living.veltraph$returnEquipment();
            if(inventory==null||inventory.getClass()!=net.minecraft.world.entity.player.Inventory.class||equipment==null||equipment.getClass()!=net.minecraft.world.entity.EntityEquipment.class
                ||player.inventoryMenu==null||player.inventoryMenu.getClass()!=net.minecraft.world.inventory.InventoryMenu.class)throw new IllegalStateException();
            if(!((Object)inventory instanceof Items items)||items.veltraph$returnInventoryEquipment()!=equipment||items.veltraph$returnSelected()<0||items.veltraph$returnSelected()>8
                ||!((Object)items.veltraph$returnItems() instanceof ListFields rawList)||!(rawList.veltraph$returnList() instanceof List<?> backing)||!nativeList(backing)||backing.size()!=36
                ||!((Object)equipment instanceof Equipment rawEquipment)||player.containerMenu!=player.inventoryMenu||!((Object)player.inventoryMenu instanceof Menu rawMenu))throw new IllegalStateException();
            var gear=rawEquipment.veltraph$returnEquipmentItems();if(gear==null||gear.getClass()!=java.util.EnumMap.class)throw new IllegalStateException();
            var menu=player.inventoryMenu;var cursor=rawMenu.veltraph$returnCursor();
            if(cursor!=ItemStack.EMPTY)throw new IllegalStateException("Native empty cursor required");
            var watched=new ArrayList<Object>();watched.add(inventory);watched.add(equipment);watched.add(items.veltraph$returnItems());watched.add(menu);
            var stacks=new ArrayList<IssuedReturnStackGraph.Stack>();
            for(int n=0;n<36;n++){if(!(backing.get(n) instanceof ItemStack stack))throw new IllegalStateException();stacks.add(IssuedReturnStackGraph.capture(stack,watched));}
            stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,EquipmentSlot.OFFHAND),watched));
            for(var slot:ARMOR)stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,slot),watched));
            var cursorGraph=IssuedReturnStackGraph.capture(cursor,watched);if(cursorGraph.count!=0)throw new IllegalStateException();stacks.add(cursorGraph);
            for(Object object:watched)if(!journal.watch(object))throw new IllegalStateException();
            if(!source.resourceCaptureCurrent())throw new IllegalStateException();
            // Raw content fact only: no codec, semantic permission, source/header transplant or joined scene is minted.
            var facts=new Snapshot(client,null,null,player,inventory,equipment,items.veltraph$returnItems(),backing,gear,menu,cursor,
                items.veltraph$returnSelected(),stacks,List.of(),journal);
            var result=new SavedMaintenanceSnapshot(client,source,facts);
            if(!result.currentFinal()||!source.resourceCaptureCurrent())throw new IllegalStateException();
            journal=null;return result;
        }catch(RuntimeException unsupported){return null;}
        finally{if(journal!=null)journal.close();}
    }
    /** Exact original before-suspend resource observation; no resume, posture or native action authority is supplied. */
    public static final class SavedMaintenancePauseSnapshot implements AutoCloseable {
        private final Minecraft client;
        private final FarmMaintenanceCoordinator.SavedMaintenancePauseCall source;
        private final Snapshot nativeFacts;
        private final Thread thread=Thread.currentThread();
        private boolean closed;
        private SavedMaintenancePauseSnapshot(Minecraft client,FarmMaintenanceCoordinator.SavedMaintenancePauseCall source,Snapshot nativeFacts){
            this.client=client;this.source=source;this.nativeFacts=nativeFacts;
        }
        /** Pure final negative resource check after callers finish providers, mode queries and observers. */
        public boolean currentFinal(){
            try{return !closed&&Thread.currentThread()==thread&&client==Minecraft.getInstance()&&client.isSameThread()
                &&source.resourceSnapshotCurrent()&&nativeFacts.nativeSceneCurrent()&&nativeFacts.contentCurrent()
                &&source.resourceSnapshotCurrent()&&nativeFacts.journal.current();}
            catch(RuntimeException unsupported){return false;}
        }
        @Override public void close(){closed=true;nativeFacts.close();}
    }
    /** Only the actual private Coordinator resource-capture window can mint this negative observation. */
    public static SavedMaintenancePauseSnapshot captureSavedMaintenancePause(Minecraft client,FarmMaintenanceCoordinator.SavedMaintenancePauseCall source){
        IssuedReturnMutationJournal.Scope journal=null;
        try{
            if(client==null||source==null||client!=Minecraft.getInstance()||!client.isSameThread()||!source.resourceCaptureCurrent())return null;
            journal=IssuedReturnMutationJournal.open();if(journal==null)return null;
            if(!source.resourceCaptureCurrent()||client.player==null||client.level==null||client.getConnection()==null)throw new IllegalStateException();
            var player=client.player;if(player.getClass()!=LocalPlayer.class)throw new IllegalStateException();
            if(!((Object)player instanceof Player rawPlayer)||!((Object)player instanceof Living living))throw new IllegalStateException();
            var inventory=rawPlayer.veltraph$returnInventory();var equipment=living.veltraph$returnEquipment();
            if(inventory==null||inventory.getClass()!=net.minecraft.world.entity.player.Inventory.class||equipment==null||equipment.getClass()!=net.minecraft.world.entity.EntityEquipment.class
                ||player.inventoryMenu==null||player.inventoryMenu.getClass()!=net.minecraft.world.inventory.InventoryMenu.class)throw new IllegalStateException();
            if(!((Object)inventory instanceof Items items)||items.veltraph$returnInventoryEquipment()!=equipment||items.veltraph$returnSelected()<0||items.veltraph$returnSelected()>8
                ||!((Object)items.veltraph$returnItems() instanceof ListFields rawList)||!(rawList.veltraph$returnList() instanceof List<?> backing)||!nativeList(backing)||backing.size()!=36
                ||!((Object)equipment instanceof Equipment rawEquipment)||player.containerMenu!=player.inventoryMenu||!((Object)player.inventoryMenu instanceof Menu rawMenu))throw new IllegalStateException();
            var gear=rawEquipment.veltraph$returnEquipmentItems();if(gear==null||gear.getClass()!=java.util.EnumMap.class)throw new IllegalStateException();
            var menu=player.inventoryMenu;var cursor=rawMenu.veltraph$returnCursor();
            if(cursor!=ItemStack.EMPTY)throw new IllegalStateException("Native empty cursor required");
            var watched=new ArrayList<Object>();watched.add(inventory);watched.add(equipment);watched.add(items.veltraph$returnItems());watched.add(menu);
            var stacks=new ArrayList<IssuedReturnStackGraph.Stack>();
            for(int n=0;n<36;n++){if(!(backing.get(n) instanceof ItemStack stack))throw new IllegalStateException();stacks.add(IssuedReturnStackGraph.capture(stack,watched));}
            stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,EquipmentSlot.OFFHAND),watched));
            for(var slot:ARMOR)stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,slot),watched));
            var cursorGraph=IssuedReturnStackGraph.capture(cursor,watched);if(cursorGraph.count!=0)throw new IllegalStateException();stacks.add(cursorGraph);
            for(Object object:watched)if(!journal.watch(object))throw new IllegalStateException();
            if(!source.resourceCaptureCurrent()||items.veltraph$returnSelected()!=source.originalToolSlot())throw new IllegalStateException();
            // Raw content fact only: no codec, semantic permission, source/header transplant or joined scene is minted.
            var facts=new Snapshot(client,null,null,player,inventory,equipment,items.veltraph$returnItems(),backing,gear,menu,cursor,
                items.veltraph$returnSelected(),stacks,List.of(),journal);
            var result=new SavedMaintenancePauseSnapshot(client,source,facts);
            if(!result.currentFinal()||!source.resourceCaptureCurrent())throw new IllegalStateException();
            journal=null;return result;
        }catch(RuntimeException unsupported){return null;}
        finally{if(journal!=null)journal.close();}
    }
    /** Typed original activation-rollback resource observation only; this snapshot never grants resume or input. */
    public static final class SavedMaintenanceRollbackSnapshot implements AutoCloseable {
        private final Minecraft client;
        private final FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source;
        private final Snapshot nativeFacts;
        private final Thread thread=Thread.currentThread();
        private boolean closed;
        private SavedMaintenanceRollbackSnapshot(Minecraft client,FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source,Snapshot nativeFacts){
            this.client=client;this.source=source;this.nativeFacts=nativeFacts;
        }
        /** Pure final negative resource check after callers finish providers, mode queries and observers. */
        public boolean currentFinal(){
            try{return !closed&&Thread.currentThread()==thread&&client==Minecraft.getInstance()&&client.isSameThread()
                &&source.resourceSnapshotCurrent()&&nativeFacts.nativeSceneCurrent()&&nativeFacts.contentCurrent()
                &&source.resourceSnapshotCurrent()&&nativeFacts.journal.current();}
            catch(RuntimeException unsupported){return false;}
        }
        @Override public void close(){closed=true;nativeFacts.close();}
    }
    /** Only the actual private Coordinator resource-capture window can mint this negative observation. */
    public static SavedMaintenanceRollbackSnapshot captureSavedMaintenanceRollback(Minecraft client,FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source){
        IssuedReturnMutationJournal.Scope journal=null;
        try{
            if(client==null||source==null||client!=Minecraft.getInstance()||!client.isSameThread()||!source.resourceCaptureCurrent())return null;
            journal=IssuedReturnMutationJournal.open();if(journal==null)return null;
            if(!source.resourceCaptureCurrent()||client.player==null||client.level==null||client.getConnection()==null)throw new IllegalStateException();
            var player=client.player;if(player.getClass()!=LocalPlayer.class)throw new IllegalStateException();
            if(!((Object)player instanceof Player rawPlayer)||!((Object)player instanceof Living living))throw new IllegalStateException();
            var inventory=rawPlayer.veltraph$returnInventory();var equipment=living.veltraph$returnEquipment();
            if(inventory==null||inventory.getClass()!=net.minecraft.world.entity.player.Inventory.class||equipment==null||equipment.getClass()!=net.minecraft.world.entity.EntityEquipment.class
                ||player.inventoryMenu==null||player.inventoryMenu.getClass()!=net.minecraft.world.inventory.InventoryMenu.class)throw new IllegalStateException();
            if(!((Object)inventory instanceof Items items)||items.veltraph$returnInventoryEquipment()!=equipment||items.veltraph$returnSelected()<0||items.veltraph$returnSelected()>8
                ||!((Object)items.veltraph$returnItems() instanceof ListFields rawList)||!(rawList.veltraph$returnList() instanceof List<?> backing)||!nativeList(backing)||backing.size()!=36
                ||!((Object)equipment instanceof Equipment rawEquipment)||player.containerMenu!=player.inventoryMenu||!((Object)player.inventoryMenu instanceof Menu rawMenu))throw new IllegalStateException();
            var gear=rawEquipment.veltraph$returnEquipmentItems();if(gear==null||gear.getClass()!=java.util.EnumMap.class)throw new IllegalStateException();
            var menu=player.inventoryMenu;var cursor=rawMenu.veltraph$returnCursor();
            if(cursor!=ItemStack.EMPTY)throw new IllegalStateException("Native empty cursor required");
            var watched=new ArrayList<Object>();watched.add(inventory);watched.add(equipment);watched.add(items.veltraph$returnItems());watched.add(menu);
            var stacks=new ArrayList<IssuedReturnStackGraph.Stack>();
            for(int n=0;n<36;n++){if(!(backing.get(n) instanceof ItemStack stack))throw new IllegalStateException();stacks.add(IssuedReturnStackGraph.capture(stack,watched));}
            stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,EquipmentSlot.OFFHAND),watched));
            for(var slot:ARMOR)stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,slot),watched));
            var cursorGraph=IssuedReturnStackGraph.capture(cursor,watched);if(cursorGraph.count!=0)throw new IllegalStateException();stacks.add(cursorGraph);
            for(Object object:watched)if(!journal.watch(object))throw new IllegalStateException();
            if(!source.resourceCaptureCurrent())throw new IllegalStateException();
            // Raw content fact only: no codec, semantic permission, source/header transplant or joined scene is minted.
            var facts=new Snapshot(client,null,null,player,inventory,equipment,items.veltraph$returnItems(),backing,gear,menu,cursor,
                items.veltraph$returnSelected(),stacks,List.of(),journal);
            var result=new SavedMaintenanceRollbackSnapshot(client,source,facts);
            if(!result.currentFinal()||!source.resourceCaptureCurrent())throw new IllegalStateException();
            journal=null;return result;
        }catch(RuntimeException unsupported){return null;}
        finally{if(journal!=null)journal.close();}
    }
    public static Snapshot capture(Minecraft client,McSuspendedFarmReturnCoordinator.SourceHeader source){
        try{
            if(client==null||source==null||!source.currentRefusal()||source.originalPlayer()!=client.player||source.originalWorld()!=client.level
                ||source.originalConnection()!=client.getConnection())return null;
            return captureNative(client,source,null);
        }catch(RuntimeException unsupported){return null;}
    }
    private static Snapshot captureNative(Minecraft client,McSuspendedFarmReturnCoordinator.SourceHeader source,McSuspendedFarmReturnCoordinator.JoinCall joined){
        var journal=IssuedReturnMutationJournal.open();if(journal==null)return null;
        try{
            if(!(joined==null?source.currentRefusal():joined.currentRefusal())||client.player==null||client.level==null)throw new IllegalStateException();
            var player=client.player;if(player.getClass()!=LocalPlayer.class)throw new IllegalStateException();
            if(!((Object)player instanceof Player rawPlayer)||!((Object)player instanceof Living living))throw new IllegalStateException();
            var inventory=rawPlayer.veltraph$returnInventory();var equipment=living.veltraph$returnEquipment();
            if(inventory==null||inventory.getClass()!=net.minecraft.world.entity.player.Inventory.class||equipment==null||equipment.getClass()!=net.minecraft.world.entity.EntityEquipment.class
                ||player.inventoryMenu==null||player.inventoryMenu.getClass()!=net.minecraft.world.inventory.InventoryMenu.class)throw new IllegalStateException();
            if(!((Object)inventory instanceof Items items)||items.veltraph$returnInventoryEquipment()!=equipment||items.veltraph$returnSelected()<0||items.veltraph$returnSelected()>8
                ||!((Object)items.veltraph$returnItems() instanceof ListFields rawList)||!(rawList.veltraph$returnList() instanceof List<?> backing)||!nativeList(backing)||backing.size()!=36
                ||!((Object)equipment instanceof Equipment rawEquipment)||player.containerMenu!=player.inventoryMenu||!((Object)player.inventoryMenu instanceof Menu rawMenu))throw new IllegalStateException();
            var gear=rawEquipment.veltraph$returnEquipmentItems();if(gear==null||gear.getClass()!=java.util.EnumMap.class)throw new IllegalStateException();
            var menu=player.inventoryMenu;var cursor=rawMenu.veltraph$returnCursor();
            if(cursor!=ItemStack.EMPTY)throw new IllegalStateException("Native empty cursor required");
            var watched=new ArrayList<Object>();watched.add(inventory);watched.add(equipment);watched.add(items.veltraph$returnItems());watched.add(menu);
            var stacks=new ArrayList<IssuedReturnStackGraph.Stack>();
            for(int i=0;i<36;i++){if(!(backing.get(i) instanceof ItemStack stack))throw new IllegalStateException();stacks.add(IssuedReturnStackGraph.capture(stack,watched));}
            stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,EquipmentSlot.OFFHAND),watched));
            for(var slot:ARMOR)stacks.add(IssuedReturnStackGraph.capture(equipmentItem(gear,slot),watched));
            var cursorGraph=IssuedReturnStackGraph.capture(cursor,watched);if(cursorGraph.count!=0)throw new IllegalStateException();stacks.add(cursorGraph);
            // Install native mutation observations before either codec can execute.
            for(Object object:watched)if(!journal.watch(object))throw new IllegalStateException();
            var semantic=new ArrayList<NativeStackIdentity.Snapshot>();var registry=client.level.registryAccess();
            for(var stack:stacks)semantic.add(NativeStackIdentity.capture(stack.nativeStack,registry));
            var result=new Snapshot(client,source,joined,player,inventory,equipment,items.veltraph$returnItems(),backing,gear,menu,cursor,
                items.veltraph$returnSelected(),stacks,semantic,journal);
            if(!result.currentFinal())throw new IllegalStateException();return result;
        }catch(RuntimeException unsupported){journal.close();return null;}
    }
}
