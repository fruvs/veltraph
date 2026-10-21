package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.inventory.equipment.McEquipmentWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Vanilla ordered SWAP then held-slot/use. Prediction is not an ACK; completion requires the later server effect/resource proof. */
public final class PestInventoryLease {
    private final Minecraft c;private final McEquipmentWorkflow port;private final dev.veltraph.client.service.mc.McInventoryService scopedInventory;private Object owner,level;
    private PestBindings.Item spec;private int source=-1,hotbar=-1,previous=-1;private ItemStack displaced=ItemStack.EMPTY;private boolean swapped,selected;private long leaseGeneration,ownedSelectionAttempt;private OwnedRestoreHandle activeOwnedHandle;
    PestInventoryLease(Minecraft c){this.c=c;port=new McEquipmentWorkflow(c);scopedInventory=new dev.veltraph.client.service.mc.McInventoryService(c);}
    boolean begin(PestBindings.Item item,int destination){
        leaseGeneration++;
        spec=item;source=PestItems.uniqueSlot(c,item);if(source<0||destination<0||destination>8||c.gui.screen()!=null||c.player.containerMenu!=c.player.inventoryMenu)return false;
        owner=c.player;level=c.level;previous=c.player.getInventory().getSelectedSlot();hotbar=source<9?source:destination;
        displaced=c.player.getInventory().getItem(hotbar).copy();selected=false;swapped=source>=9;
        return !swapped||port.swap(port.read(),source,hotbar);
    }
    boolean ready(){
        if(!owned()||c.gui.screen()!=null||c.player.containerMenu!=c.player.inventoryMenu||!c.player.containerMenu.getCarried().isEmpty())return false;
        if(!PestItems.matches(c.player.getInventory().getItem(hotbar),spec))return false;
        if(swapped&&(!ItemStack.matches(displaced,c.player.getInventory().getItem(source))||!port.correctionTickPassed()))return false;
        if(!selected){selected=port.select(port.read(),hotbar);return false;}return c.player.getInventory().getSelectedSlot()==hotbar;
    }
    /** Same relocation and correction tick; only the held-slot issue uses the scoped native leaf. */
    boolean readyOwned(OwnedRestoreHandle exact,java.util.function.BooleanSupplier scope){
        long selectionAttempt=++ownedSelectionAttempt;long generation=exact!=null&&exact.producer==this?exact.generation:-1;Object p=owner,w=level,connection=c.getConnection(),menu=c.player==null?null:c.player.containerMenu,gameMode=c.gameMode;
        var before=snapshot();int expectedSlot=selected?hotbar:previous;int desired=hotbar;
        if(!owned()||!restoreScene(p,w,connection,menu,gameMode,generation,scope)||!matches(before)||c.player.getInventory().getSelectedSlot()!=expectedSlot)return false;
        if(desired<0||desired>8||!PestItems.matches(c.player.getInventory().getItem(desired),spec))return false;
        if(swapped&&(source<9||source>=36||!ItemStack.matches(displaced,c.player.getInventory().getItem(source))||!port.correctionTickPassed()))return false;
        if(!selected){
            var receipt=selectOwned(desired,expectedSlot,before,p,w,connection,menu,gameMode,generation,scope);
            boolean applied=receipt!=null&&receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted()
                &&restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(before)&&c.player.getInventory().getSelectedSlot()==desired;
            if(leaseGeneration==generation&&ownedSelectionAttempt==selectionAttempt)selected=applied;
            return false; // Original first-selection tick never advances the action.
        }
        return restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(before)&&c.player.getInventory().getSelectedSlot()==desired;
    }
    boolean selectedSlotOwned(){return owned()&&selected&&c.player.getInventory().getSelectedSlot()==hotbar;}
    boolean activeTool(){return selectedSlotOwned()&&PestItems.matches(c.player.getInventory().getItem(hotbar),spec);}
    boolean use(){return activeTool()&&port.use(port.read());}
    void restore(){
        if(owned()){
            if(swapped&&c.gui.screen()==null&&c.player.containerMenu==c.player.inventoryMenu&&c.player.containerMenu.getCarried().isEmpty()
                    &&ItemStack.matches(displaced,c.player.getInventory().getItem(source))
                    &&(c.player.getInventory().getItem(hotbar).isEmpty()||PestItems.matches(c.player.getInventory().getItem(hotbar),spec)))port.swap(port.read(),source,hotbar);
            if(previous>=0)port.select(port.read(),previous);
        }
        owner=level=null;source=hotbar=previous=-1;swapped=selected=false;
    }
    enum RestoreResult { NOT_REQUIRED,REFUSED,CONFIRMED_LOCAL,UNCONFIRMED,FAILED }
    enum ServerConfirmation { NOT_REQUIRED,UNCONFIRMED,AUTHORITATIVE_AFTER_REQUEST }
    /** Private native-result receipt. Predicted local SWAP is explicitly not a server acknowledgment. */
    public static final class OwnedRestoreReceipt {
        private final PestInventoryLease producer;private final long generation;private final Object player,world,connection,menu,gameMode;
        private final OwnedRestoreHandle handle;private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit;
        private final java.util.List<ItemStack> before,after;private final ItemStack cursorBefore,cursorAfter;
        private final int selectedBefore,selectedAfter;private final RestoreResult swap,selection;private final ServerConfirmation server;
        private final McPestInventoryRestorationObserver.Window restorationWindow;private final boolean finalFence,logicalRetired;private final dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt nativeSelection;
        private OwnedRestoreReceipt(PestInventoryLease producer,long generation,Object player,Object world,Object connection,Object menu,Object gameMode,
                java.util.List<ItemStack> before,java.util.List<ItemStack> after,ItemStack cursorBefore,ItemStack cursorAfter,int selectedBefore,int selectedAfter,
                RestoreResult swap,RestoreResult selection,ServerConfirmation server,boolean finalFence,boolean logicalRetired,dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt nativeSelection,McPestInventoryRestorationObserver.Window restorationWindow,OwnedRestoreHandle handle,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit){
            this.handle=handle;this.permit=permit;this.producer=producer;this.generation=generation;this.player=player;this.world=world;this.connection=connection;this.menu=menu;this.gameMode=gameMode;
            this.before=before;this.after=after;this.cursorBefore=cursorBefore;this.cursorAfter=cursorAfter;this.selectedBefore=selectedBefore;this.selectedAfter=selectedAfter;
            this.swap=swap;this.selection=selection;this.server=server;this.finalFence=finalFence;this.logicalRetired=logicalRetired;this.nativeSelection=nativeSelection;this.restorationWindow=restorationWindow;
        }
        boolean localComplete(){return finalFence&&logicalRetired&&(swap==RestoreResult.NOT_REQUIRED||swap==RestoreResult.CONFIRMED_LOCAL)
            &&(selection==RestoreResult.NOT_REQUIRED||selection==RestoreResult.CONFIRMED_LOCAL&&nativeSelection!=null&&nativeSelection.confirmed()&&nativeSelection.localSelected()&&nativeSelection.packetSubmitted());}
        boolean restartConfirmed(){return localComplete()&&(server==ServerConfirmation.NOT_REQUIRED||authoritativeReceipt()!=null);}
        RestoreResult swap(){return swap;}RestoreResult selection(){return selection;}ServerConfirmation serverConfirmation(){return server==ServerConfirmation.UNCONFIRMED&&authoritativeReceipt()!=null?ServerConfirmation.AUTHORITATIVE_AFTER_REQUEST:server;}
        public McPestInventoryRestorationObserver.AuthoritativeRestorationReceipt authoritativeReceipt(){return restorationWindow==null?null:restorationWindow.authoritativeReceipt();}
        public OwnedRestoreHandle handle(){return handle;}public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit(){return permit;}
        public boolean currentOwned(){return permit!=null&&handle!=null&&permit.originalHandle()==handle&&permit.nativeClient()==producer.c
            &&permit.parent()==handle.originalParent()&&permit.currentOriginal()&&permit.cleanupCurrent()
            &&currentLocal(permit::cleanupCurrent)&&handle.selectionVersionCurrent();}
        public boolean restartQualified(){var authoritative=authoritativeReceipt();return currentOwned()&&restartConfirmed()
            &&(server==ServerConfirmation.NOT_REQUIRED||authoritative!=null&&authoritative.permit()==permit);}
        private static final class FinalInventorySnapshot {
            private final OwnedRestoreReceipt owner;private final Object inventory,menu,options,gameMode,connection,ack,ackSnapshot;
            private final java.util.List<ItemStack> contents;private final ItemStack cursor;private final int selected,state;private final long slotVersion;
            private FinalInventorySnapshot(OwnedRestoreReceipt owner){this.owner=owner;var c=owner.producer.c;
                inventory=c.player.getInventory();menu=c.player.containerMenu;options=c.options;gameMode=c.gameMode;connection=c.getConnection();
                contents=owner.producer.snapshot();cursor=c.player.containerMenu.getCarried().copy();selected=c.player.getInventory().getSelectedSlot();state=c.player.inventoryMenu.getStateId();
                slotVersion=McPestInventoryRestorationObserver.originalSlotVersion(inventory);
                var actual=owner.restorationWindow==null?null:owner.restorationWindow.nativeAuthoritativeReceipt();ack=actual;ackSnapshot=actual==null?null:actual.finalNativeSnapshot();
            }
        }
        /** Private observation from this actual effect result; no permit/codec/provider callback. */
        public Object finalNativeSnapshot(){try{if(!localComplete()||handle==null)return null;
            var snapshot=new FinalInventorySnapshot(this);return finalNativeCurrent(snapshot)?snapshot:null;
        }catch(RuntimeException unavailable){return null;}}
        /** Last native leaf after the issuer's callbacks. It cannot grant or refresh any scope. */
        public boolean finalNativeCurrent(Object exact){try{
            if(!(exact instanceof FinalInventorySnapshot snapshot)||snapshot.owner!=this||!localComplete()||handle==null)return false;var c=producer.c;
            boolean server=serverConfirmationWithoutCallbacks()==ServerConfirmation.NOT_REQUIRED||snapshot.ack instanceof McPestInventoryRestorationObserver.AuthoritativeRestorationReceipt actual
                &&actual.permit()==permit&&snapshot.ackSnapshot!=null&&actual.finalNativeCurrent(snapshot.ackSnapshot);
            return server&&c.isSameThread()&&producer.restoreLeaf(player,world,connection,menu,gameMode,generation)
                &&c.player.getInventory()==snapshot.inventory&&c.player.containerMenu==snapshot.menu&&snapshot.menu==menu&&c.getConnection()==snapshot.connection
                &&c.options==snapshot.options&&c.gameMode==snapshot.gameMode&&snapshot.slotVersion>=0
                &&McPestInventoryRestorationObserver.originalSlotVersion(snapshot.inventory)==snapshot.slotVersion&&handle.selectionVersionCurrent()
                &&snapshot.selected==selectedAfter&&c.player.getInventory().getSelectedSlot()==snapshot.selected&&c.player.inventoryMenu.getStateId()==snapshot.state
                &&producer.matches(snapshot.contents)&&producer.matches(after)&&ItemStack.matches(snapshot.cursor,c.player.containerMenu.getCarried())
                &&ItemStack.matches(cursorAfter,c.player.containerMenu.getCarried())&&c.player.containerMenu.getCarried().isEmpty();
        }catch(RuntimeException unavailable){return false;}}
        private ServerConfirmation serverConfirmationWithoutCallbacks(){return server;}
        boolean currentLocal(java.util.function.BooleanSupplier scope){return finalFence&&logicalRetired
            &&producer.restoreLeaf(player,world,connection,menu,gameMode,generation)&&producer.matches(after)
            &&ItemStack.matches(cursorAfter,producer.c.player.containerMenu.getCarried())&&producer.c.player.getInventory().getSelectedSlot()==selectedAfter
            &&producer.fence(scope)&&producer.restoreLeaf(player,world,connection,menu,gameMode,generation)&&producer.matches(after)
            &&ItemStack.matches(cursorAfter,producer.c.player.containerMenu.getCarried())&&producer.c.player.getInventory().getSelectedSlot()==selectedAfter;}
        @Override public String toString(){return "inventory restore swap="+swap+", select="+selection+", server="+serverConfirmation()+", fence="+finalFence+", retired="+logicalRetired;}
    }
    private boolean fence(java.util.function.BooleanSupplier scope){try{return c.isSameThread()&&scope!=null&&scope.getAsBoolean();}catch(RuntimeException unavailable){return false;}}
    private java.util.List<ItemStack> snapshot(){var out=new java.util.ArrayList<ItemStack>();if(c.player!=null)for(int i=0;i<36;i++)out.add(c.player.getInventory().getItem(i).copy());return java.util.List.copyOf(out);}
    private boolean matches(java.util.List<ItemStack> expected){if(c.player==null||expected.size()!=36)return false;for(int i=0;i<36;i++)if(!ItemStack.matches(expected.get(i),c.player.getInventory().getItem(i)))return false;return true;}
    /** No supplied callback runs inside the final leaf; every effect checks it after the refusal fence. */
    private boolean restoreLeaf(Object p,Object w,Object connection,Object menu,Object gameMode,long generation){
        return leaseGeneration==generation&&p!=null&&connection!=null&&gameMode!=null&&c.player==p&&c.level==w&&c.getConnection()==connection
            &&c.gameMode==gameMode&&c.player.isAlive()&&c.gui.screen()==null&&c.player.containerMenu==menu&&menu==c.player.inventoryMenu
            &&c.player.containerMenu.getCarried().isEmpty();
    }
    private boolean restoreScene(Object p,Object w,Object connection,Object menu,Object gameMode,long generation,java.util.function.BooleanSupplier scope){
        return restoreLeaf(p,w,connection,menu,gameMode,generation)&&fence(scope)&&restoreLeaf(p,w,connection,menu,gameMode,generation);
    }
    /** Separate expected slots model the actual owned setter followed by its ordinary packet. */
    private dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt selectOwned(int slot,int expectedSlot,java.util.List<ItemStack> expected,
            Object p,Object w,Object connection,Object menu,Object gameMode,long generation,java.util.function.BooleanSupplier scope){
        var exact=activeOwnedHandle;
        java.util.function.Supplier<dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt> effect=()->scopedInventory.selectSlotScoped(expectedSlot,slot,
            ()->restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(expected)&&c.player.getInventory().getSelectedSlot()==expectedSlot
                &&(exact==null||McPestInventoryRestorationObserver.originalSelectionFence(exact,false)),
            ()->restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(expected)&&c.player.getInventory().getSelectedSlot()==slot
                &&(exact==null||McPestInventoryRestorationObserver.originalSelectionFence(exact,true)));
        if(exact==null||exact.generation!=generation)return effect.get();
        var result=McPestInventoryRestorationObserver.selectOriginalOwned(exact,expectedSlot,slot,scope,effect);
        if(activeOwnedHandle==exact&&leaseGeneration==generation)exact.selection=result.lineage();
        return result.actual();
    }
    /** Private actual lease version; read-only provenance, never native permission. */
    public static final class OwnedRestoreHandle {
        private final PestInventoryLease producer;private final long generation;private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.core.Module parent;private final Object player,world,connection,store,inventory,menu,options,gameMode;
        private long initialSlotVersion;private McPestInventoryRestorationObserver.OriginalSelectionReceipt selection;
        private dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit issuedPermit;private OwnedRestoreReceipt issuedResult;private boolean issuing;
        private OwnedRestoreHandle(PestInventoryLease producer,long generation){
            this.producer=producer;this.generation=generation;var c=producer.c;player=producer.owner;world=producer.level;connection=c.getConnection();
            inventory=c.player==null?null:c.player.getInventory();menu=c.player==null?null:c.player.containerMenu;options=c.options;gameMode=c.gameMode;store=dev.veltraph.client.VeltraphBootstrap.config();
            root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            var candidate=root==null?null:root.inputOwner().orElse(null);
            parent=lifecycle!=null&&lifecycle.known()&&candidate!=null&&root.get(candidate.id()).orElse(null)==candidate&&root.lifecycleCurrent(lifecycle)?candidate:null;
            initialSlotVersion=McPestInventoryRestorationObserver.originalSlotVersion(inventory);
        }
        public Minecraft nativeClient(){return producer.c;}public dev.veltraph.client.core.Module originalParent(){return parent;}
        Object nativeInventory(){return inventory;}
        boolean selectionLeaf(){var c=producer.c;return parent!=null&&generation==producer.leaseGeneration&&c.player==player&&c.level==world&&c.getConnection()==connection
            &&c.player!=null&&c.player.getInventory()==inventory&&c.player.containerMenu==menu&&menu==c.player.inventoryMenu&&c.gui.screen()==null&&c.player.containerMenu.getCarried().isEmpty()
            &&c.options==options&&c.gameMode==gameMode&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.get(parent.id()).orElse(null)==parent&&dev.veltraph.client.VeltraphBootstrap.config()==store;}
        boolean selectionVersionCurrent(){return selection!=null?selection.current(this):selectionLeaf()&&initialSlotVersion>=0&&McPestInventoryRestorationObserver.originalSlotVersion(inventory)==initialSlotVersion;}
        public boolean currentOriginal(){try{var c=producer.c;if(!c.isSameThread()||!selectionLeaf()||!selectionVersionCurrent()||!producer.owned())return false;
            var frame=producer.snapshot();int expected=producer.selected?producer.hotbar:producer.previous;
            boolean source=c.player.getInventory().getSelectedSlot()==expected&&producer.hotbar>=0&&producer.hotbar<=8&&PestItems.matches(c.player.getInventory().getItem(producer.hotbar),producer.spec)
                &&(!producer.swapped||producer.source>=9&&producer.source<36&&ItemStack.matches(producer.displaced,c.player.getInventory().getItem(producer.source)));
            return source&&selectionLeaf()&&selectionVersionCurrent()&&producer.matches(frame)&&c.player.getInventory().getSelectedSlot()==expected;
        }catch(RuntimeException unavailable){return false;}}
        /** The actual transaction invokes this once; unrelated opaque permits cannot stamp this old lease. */
        public OwnedRestoreReceipt restoreIssued(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit){
            if(issuing)return null;if(issuedPermit!=null)return issuedPermit==permit?issuedResult:null;
            if(permit==null||permit.originalHandle()!=this||permit.nativeClient()!=producer.c||permit.parent()!=parent||!permit.inventoryIssueCurrent()||!currentOriginal())return null;
            issuedPermit=permit;issuing=true;try{
                var rotation=parent instanceof GardenActionModule action&&action.services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation?nativeRotation:null;
                issuedResult=producer.restoreOwned(this,()->permit.currentOriginal()&&permit.cleanupCurrent(),parent,rotation,permit);return issuedResult;
            }finally{issuing=false;}
        }
    }
    OwnedRestoreHandle cleanupHandle(){if(owner==null&&source<0&&previous<0)return null;
        if(activeOwnedHandle==null||activeOwnedHandle.generation!=leaseGeneration)activeOwnedHandle=new OwnedRestoreHandle(this,leaseGeneration);return activeOwnedHandle;}
    /** This private constructor captures the actual old lease, native menu mapping and full inverse. */
    static final class RestoreSpecification {
        private final PestInventoryLease producer;private final long generation;private final Object player,world,connection,menu,gameMode;
        private final int source,hotbar,temporary,restored,menuId,revision;private final int[] slots=new int[36];
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit;
        private final java.util.List<ItemStack> before,after;private final ItemStack cursor;
        private RestoreSpecification(PestInventoryLease producer,OwnedRestoreHandle exact,int source,int hotbar,int temporary,int restored,java.util.List<ItemStack> before,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit){
            this.permit=permit;this.producer=producer;generation=exact.generation;this.source=source;this.hotbar=hotbar;this.temporary=temporary;this.restored=restored;
            var c=producer.c;player=c.player;world=c.level;connection=c.getConnection();menu=c.player.containerMenu;gameMode=c.gameMode;
            menuId=c.player.inventoryMenu.containerId;revision=c.player.inventoryMenu.getStateId();this.before=before.stream().map(ItemStack::copy).toList();
            var inverse=new java.util.ArrayList<ItemStack>(this.before);inverse.set(source,this.before.get(hotbar).copy());inverse.set(hotbar,this.before.get(source).copy());after=java.util.List.copyOf(inverse);
            cursor=c.player.inventoryMenu.getCarried().copy();java.util.Arrays.fill(slots,-1);
            for(var slot:c.player.inventoryMenu.slots)if(slot.container==c.player.getInventory()){
                int i=slot.getContainerSlot();if(i>=0&&i<36){if(slots[i]>=0)throw new IllegalStateException("Duplicate player inventory mapping");slots[i]=slot.index;}
            }
            for(int slot:slots)if(slot<0)throw new IllegalStateException("Incomplete native inventory mapping");
        }
        boolean currentLeaf(){return producer.restoreLeaf(player,world,connection,menu,gameMode,generation);}
        boolean currentBefore(){return currentLeaf()&&producer.matches(before)&&producer.c.player.getInventory().getSelectedSlot()==temporary;}
        boolean matchesAfter(){return currentLeaf()&&producer.matches(after);}
        dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit(){return permit;}
        Object inventory(){return ((net.minecraft.client.player.LocalPlayer)player).getInventory();}Object listener(){return connection;}int menuId(){return menuId;}int revision(){return revision;}int source(){return source;}int hotbar(){return hotbar;}int temporary(){return temporary;}int restored(){return restored;}
        int menuSlot(int inventory){return inventory>=0&&inventory<36?slots[inventory]:-1;}int sourceMenuSlot(){return slots[source];}
        int inventorySlot(int nativeSlot){for(int i=0;i<36;i++)if(slots[i]==nativeSlot)return i;return -1;}
        java.util.List<ItemStack> before(){return before;}java.util.List<ItemStack> after(){return after;}ItemStack cursor(){return cursor;}
    }
    private static final class RestoreRefused extends RuntimeException {}
    OwnedRestoreReceipt restoreOwned(java.util.function.BooleanSupplier scope){return restoreOwned(cleanupHandle(),scope);}
    OwnedRestoreReceipt restoreOwned(OwnedRestoreHandle exact,java.util.function.BooleanSupplier scope){return restoreOwned(exact,scope,null,null);}
    OwnedRestoreReceipt restoreOwned(OwnedRestoreHandle exact,java.util.function.BooleanSupplier scope,dev.veltraph.client.core.Module expectedParent,dev.veltraph.client.service.mc.McRotationService rotation){
        return restoreOwned(exact,scope,expectedParent,rotation,null);
    }
    private OwnedRestoreReceipt restoreOwned(OwnedRestoreHandle exact,java.util.function.BooleanSupplier scope,dev.veltraph.client.core.Module expectedParent,dev.veltraph.client.service.mc.McRotationService rotation,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.InventoryCleanupPermit permit){
        McPestInventoryRestorationObserver.Window restorationWindow=null;
        long generation=exact!=null&&exact.producer==this?exact.generation:-1;Object p=owner,w=level,connection=c.getConnection(),menu=c.player==null?null:c.player.containerMenu,gameMode=c.gameMode;
        var before=snapshot();var after=before;var cursorBefore=c.player==null?ItemStack.EMPTY:c.player.containerMenu.getCarried().copy();var cursorAfter=cursorBefore;
        int selectedBefore=c.player==null?-1:c.player.getInventory().getSelectedSlot(),selectedAfter=selectedBefore;
        RestoreResult swap=swapped?RestoreResult.REFUSED:RestoreResult.NOT_REQUIRED,selection=previous>=0?RestoreResult.REFUSED:RestoreResult.NOT_REQUIRED;
        ServerConfirmation server=swapped?ServerConfirmation.UNCONFIRMED:ServerConfirmation.NOT_REQUIRED;boolean finalFence=false,retired=false;dev.veltraph.client.service.mc.McInventoryService.ScopedSlotReceipt nativeSelection=null;
        int oldSource=source,oldHotbar=hotbar,oldPrevious=previous;var oldDisplaced=displaced.copy();var oldSpec=spec;boolean oldSwapped=swapped,oldSelected=selected;int expectedTemporarySlot=oldSelected?oldHotbar:oldPrevious;
        try{
            if(!owned()||!restoreScene(p,w,connection,menu,gameMode,generation,scope)||!matches(before)||c.player.getInventory().getSelectedSlot()!=expectedTemporarySlot)throw new RestoreRefused();
            if(oldSwapped){
                if(oldSource<9||oldSource>=36||oldHotbar<0||oldHotbar>8||!ItemStack.matches(oldDisplaced,c.player.getInventory().getItem(oldSource))
                    ||!(c.player.getInventory().getItem(oldHotbar).isEmpty()||PestItems.matches(c.player.getInventory().getItem(oldHotbar),oldSpec)))throw new RestoreRefused();
                var expected=new java.util.ArrayList<ItemStack>(before);expected.set(oldSource,before.get(oldHotbar).copy());expected.set(oldHotbar,before.get(oldSource).copy());
                var observation=port.read();if(!restoreScene(p,w,connection,menu,gameMode,generation,scope)||!matches(before)||c.player.getInventory().getSelectedSlot()!=expectedTemporarySlot)throw new RestoreRefused();
                if(expectedParent!=null&&rotation!=null){
                    var specification=new RestoreSpecification(this,exact,oldSource,oldHotbar,expectedTemporarySlot,oldPrevious,before,permit);
                    restorationWindow=McPestInventoryRestorationObserver.open(c,specification,expectedParent,rotation,scope);
                }
                if(!restoreScene(p,w,connection,menu,gameMode,generation,scope)||!matches(before)||c.player.getInventory().getSelectedSlot()!=expectedTemporarySlot)throw new RestoreRefused();
                boolean applied=port.swap(observation,oldSource,oldHotbar);after=snapshot();
                swap=applied&&restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(expected)&&c.player.getInventory().getSelectedSlot()==expectedTemporarySlot?RestoreResult.CONFIRMED_LOCAL:RestoreResult.UNCONFIRMED;
                if(swap!=RestoreResult.CONFIRMED_LOCAL)throw new RestoreRefused();
            }
            if(oldPrevious>=0){
                var observation=port.read();if(!restoreScene(p,w,connection,menu,gameMode,generation,scope)||!matches(after)||c.player.getInventory().getSelectedSlot()!=expectedTemporarySlot)throw new RestoreRefused();
                final var selectionFrame=after;
                nativeSelection=McPestInventoryRestorationObserver.selectOwned(restorationWindow,
                    ()->selectOwned(oldPrevious,expectedTemporarySlot,selectionFrame,p,w,connection,menu,gameMode,generation,scope));boolean applied=nativeSelection!=null&&nativeSelection.confirmed()&&nativeSelection.localSelected()&&nativeSelection.packetSubmitted();selectedAfter=c.player==null?-1:c.player.getInventory().getSelectedSlot();
                selection=applied&&restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(after)&&selectedAfter==oldPrevious?RestoreResult.CONFIRMED_LOCAL:RestoreResult.UNCONFIRMED;
            }
            after=snapshot();cursorAfter=c.player==null?ItemStack.EMPTY:c.player.containerMenu.getCarried().copy();selectedAfter=c.player==null?-1:c.player.getInventory().getSelectedSlot();
            finalFence=restoreScene(p,w,connection,menu,gameMode,generation,scope)&&matches(after)
                &&c.player.getInventory().getSelectedSlot()==(oldPrevious>=0?oldPrevious:expectedTemporarySlot);
        }catch(RestoreRefused refused){}catch(RuntimeException failed){if(swap==RestoreResult.REFUSED)swap=RestoreResult.FAILED;else selection=RestoreResult.FAILED;}
        finally{if(leaseGeneration==generation){owner=level=null;source=hotbar=previous=-1;swapped=selected=false;retired=true;}}
        return new OwnedRestoreReceipt(this,generation,p,w,connection,menu,gameMode,before,after,cursorBefore,cursorAfter,selectedBefore,selectedAfter,swap,selection,server,finalFence,retired,nativeSelection,restorationWindow,exact,permit);
    }
    boolean owned(){return c.player==owner&&c.level==level&&c.player!=null;}
}
