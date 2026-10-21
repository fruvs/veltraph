package dev.veltraph.client.garden.loadout.mc;

import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.gui.McMenuAccess;
import dev.veltraph.client.service.mc.McServerCommandService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Read-only native ownership bindings and exact gear; vanilla named menu commands/click/close only. */
public final class McNamedLoadoutPort implements NamedLoadoutFlow.Port {
    private final Minecraft client;private final McMenuAccess menus;
    private final IdentityHashMap<NamedLoadoutFlow.Observation,Binding> bindings=new IdentityHashMap<>();
    private record Binding(Object player,Object world,Object connection,Object menu,Map<String,NamedLoadoutFlow.Gear> gear){}
    private RetainedWindow retained;
    private final Map<dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation,Boolean> consumedRetained=new WeakHashMap<>();
    private Object issuedMenu;private int issuedRevision=-1;private ItemStack predictedCursor=ItemStack.EMPTY;
    public McNamedLoadoutPort(Minecraft client){this.client=client;menus=new McMenuAccess(client);}
    public static NamedLoadoutFlow.Gear fingerprint(ItemStack stack){return new NamedLoadoutFlow.Gear(stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),
            stack.isEmpty()?"":stack.getComponents().stream().map(Object::toString).sorted().toList().toString());}
    public static Map<String,NamedLoadoutFlow.Gear> equipped(net.minecraft.world.entity.player.Player player){
        var result=new LinkedHashMap<String,NamedLoadoutFlow.Gear>();for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND})result.put(slot.name(),fingerprint(player.getItemBySlot(slot)));return Map.copyOf(result);
    }
    @Override public NamedLoadoutFlow.Observation read(){
        if(!client.isSameThread())throw new IllegalStateException("Named loadout requires client thread");bindings.clear();
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||client.gameMode==null)return new NamedLoadoutFlow.Observation(null,null,null,null,null,Map.of(),false,false,false);
        var p=client.player;var current=p.containerMenu;var m=menus.read();var gear=equipped(p);boolean empty=current.getCarried().isEmpty();
        boolean prediction=current==issuedMenu&&!predictedCursor.isEmpty()&&ItemStack.matches(predictedCursor,current.getCarried());
        var o=new NamedLoadoutFlow.Observation(p,client.level,client.getConnection(),m==null?null:current,m,gear,empty,prediction,issuedMenu==null||current!=issuedMenu||current.getStateId()!=issuedRevision);
        bindings.put(o,new Binding(p,client.level,client.getConnection(),current,gear));return o;
    }
    private Binding valid(NamedLoadoutFlow.Observation expected){var b=bindings.get(expected);
        if(b==null||client.player==null||client.level!=b.world()||client.player!=b.player()||client.getConnection()!=b.connection()||client.player.containerMenu!=b.menu()
                ||!client.player.isAlive()||client.gameMode==null||!client.player.containerMenu.getCarried().isEmpty()||!equipped(client.player).equals(b.gear()))return null;return b;}
    @Override public boolean command(NamedLoadoutFlow.Observation expected,String command){
        if(retained!=null)return retained.effect(dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.COMMAND,expected,-1,()->ordinaryCommand(expected,command));
        return ordinaryCommand(expected,command);
    }
    private boolean ordinaryCommand(NamedLoadoutFlow.Observation expected,String command){
        if(valid(expected)==null||client.gui.screen()!=null||expected.snapshot()!=null||!command.equals("loadout"))return false;
        return new McServerCommandService(client).sendCommand(command);
    }
    @Override public boolean click(NamedLoadoutFlow.Observation expected,int slot){
        if(retained!=null)return retained.effect(dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.CLICK,expected,slot,()->ordinaryClick(expected,slot));
        return ordinaryClick(expected,slot);
    }
    private boolean ordinaryClick(NamedLoadoutFlow.Observation expected,int slot){if(valid(expected)==null||expected.snapshot()==null)return false;
        issuedMenu=client.player.containerMenu;issuedRevision=client.player.containerMenu.getStateId();if(!menus.click(expected.snapshot(),slot))return false;
        predictedCursor=client.player.containerMenu.getCarried().copy();return true;}
    @Override public boolean close(NamedLoadoutFlow.Observation expected){
        if(retained!=null)return retained.effect(dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.CLOSE,expected,-1,()->ordinaryClose(expected));
        return ordinaryClose(expected);
    }
    private boolean ordinaryClose(NamedLoadoutFlow.Observation expected){if(valid(expected)==null||expected.snapshot()==null)return false;return menus.close(expected.snapshot().id());}

    /** Explicit observed ancillary values. No defaults and no inference from armor or timer data. */
    public record AncillaryContract(String exactRow,Map<String,String> values,Map<String,String> exactNativeGear) {
        public AncillaryContract {
            values=Map.copyOf(values);exactNativeGear=Map.copyOf(exactNativeGear);
            if(!exactNativeGear.keySet().equals(Set.of("HEAD","CHEST","LEGS","FEET","OFFHAND"))||exactNativeGear.values().stream().anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("Exact native five-gear identities required");
            if(exactRow==null||exactRow.isBlank()||exactRow.length()>2048||!values.keySet().equals(Set.of("PET","HOTM","HOTF","POWER","TUNING","EQUIPMENT"))
                ||values.values().stream().anyMatch(v->v==null||v.isBlank()||v.length()>256||v.indexOf(';')>=0||v.indexOf('=')>=0||v.chars().anyMatch(ch->ch<32)||v.equalsIgnoreCase("unknown")))throw new IllegalArgumentException("Six explicitly observed ancillary fields required");
            String canonical="Veltraph private armor-only state "+values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(";"));
            if(!exactRow.equals(canonical))throw new IllegalArgumentException("Only declared private armor-only counterparty state is implemented; live formats unknown");
        }
    }
    private static long nativeSerial;
    private static AncillaryObservation ancillary;
    private static RetainedWindow activeRetained;private static AppliedReceipt passiveGear;private static InitialGearHistory initialGear;
    private record AncillaryObservation(Object listener,Object player,Object world,long serial,long at,String row){}
    /** Observation only. Root must retain this exact native capsule once at its original arm. */
    public static final class InitialGearHistory {
        private final McNamedLoadoutPort port;private final Object player,world,listener,transport,inventory,equipment,options,gameMode;private final UUID uuid;
        private Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> gear;private boolean capturing=true;
        private final IdentityHashMap<Object,Boolean> aliases=new IdentityHashMap<>();private final long serial;
        private boolean revoked,consumed;private RetainedWindow handoff;
        private InitialGearHistory(McNamedLoadoutPort port){this.port=port;var c=port.client;
            player=c.player;world=c.level;listener=c.getConnection();transport=c.getConnection().getConnection();inventory=c.player.getInventory();equipment=((EquipmentCarrier)inventory).namedLoadoutEquipment();options=c.options;gameMode=c.gameMode;uuid=c.player.getUUID();
            serial=nativeSerial;
            for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND}){var stack=c.player.getItemBySlot(slot);if(!stack.isEmpty()){aliases.put(stack,Boolean.TRUE);aliases.put(stack.getComponents(),Boolean.TRUE);}}
        }
        private boolean leaf(){var c=port.client;return !capturing&&gear!=null&&!revoked&&!consumed&&initialGear==this&&c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==listener
            &&c.player!=null&&c.player.isAlive()&&c.player.level()==world&&uuid.equals(c.player.getUUID())&&c.options==options&&c.gameMode==gameMode
            &&c.player.getInventory()==inventory&&inventory instanceof EquipmentCarrier actual&&actual.namedLoadoutEquipment()==equipment
            &&c.getConnection().getConnection()==transport&&((net.minecraft.network.Connection)transport).isConnected();}
        public boolean current(){try{if(!leaf())return false;var observed=captureGear(port.client);return gear.equals(observed)&&leaf();}catch(RuntimeException unknown){revoke();return false;}}
        public Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> gear(){return gear;}
        /** Refusal only. A cancelled Root arm can release this observer; it cannot renew custody. */
        public void revoke(){revoked=true;if(initialGear==this)initialGear=null;}
        /** Private once-only transition into the exact already validated operation, never a renewed snapshot. */
        private boolean consumeInto(RetainedWindow exact){
            if(exact==null||exact.port!=port||exact.initial!=this||!current()||!exact.originalGear.equals(gear)||!exact.operation.bindingsCurrentFor(port,exact.flow,exact.target.name(),exact.settings,exact.ancillaryContract)
                ||!exact.operation.nativeCurrentFor(port,exact.flow,exact.target.name()))return false;
            if(!gear.equals(exact.originalGear)||!leaf())return false;consumed=true;handoff=exact;if(initialGear==this)initialGear=null;return true;
        }
        private boolean handedTo(RetainedWindow exact){return !revoked&&consumed&&handoff==exact&&exact.initial==this;}
    }
    public InitialGearHistory captureInitialGearHistory(){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null
            ||!(client.player.getInventory() instanceof EquipmentCarrier)||!client.player.isAlive()||client.player.level()!=client.level
            ||!client.getConnection().getConnection().isConnected())return null;
        InitialGearHistory next;try{next=new InitialGearHistory(this);}catch(RuntimeException unavailable){return null;}
        if(initialGear!=null)initialGear.revoked=true;initialGear=next;
        try{next.gear=captureGear(client);next.capturing=false;if(next.current())return next;next.revoke();return null;}catch(RuntimeException unavailable){next.revoked=true;next.capturing=false;if(initialGear==next)initialGear=null;return null;}
    }
    /** Opaque actual one-port/one-flow/one-loan operation. A public flow Receipt cannot substitute. */
    public static final class RetainedWindow {
        private final McNamedLoadoutPort port;private final NamedLoadoutFlow flow;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation operation;
        private final NamedLoadoutFlow.Settings settings;private final NamedLoadoutFlow.Profile target;private final AncillaryContract ancillaryContract;private final InitialGearHistory initial;
        private final Object player,world,listener,transport,inventory,options,gameMode;private final UUID uuid;
        private final int selected;private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selectedHistory;
        private final List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> full36;
        private final dev.veltraph.client.garden.loadout.RetainedLoadoutJournal journal;
        private final IdentityHashMap<Object,Boolean> protectedAliases=new IdentityHashMap<>();private final IdentityHashMap<Object,Boolean> cursorAliases=new IdentityHashMap<>();
        private final Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> originalGear;
        private final long issuedAt=System.nanoTime();private Object ownedMenu;private int ownedMenuId=-1;private int clickedSlot=-1;private int clickedRevision=-1;private int predictedCursorWrites;private dev.veltraph.client.inventory.NativeStackIdentity.Snapshot clickRow;
        private dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect effect;
        private int sendsInFlight,receivesInFlight;private SendInvocation sending;private ReceiveInvocation receiving;private InventoryWrite inventoryWrite;private boolean retired;private AppliedReceipt receipt;
        private RetainedWindow(McNamedLoadoutPort port,NamedLoadoutFlow flow,
                dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation operation,
                NamedLoadoutFlow.Settings settings,NamedLoadoutFlow.Profile target,AncillaryContract contract){
            this.port=port;this.flow=flow;this.operation=operation;this.settings=settings;this.target=target;ancillaryContract=contract;initial=operation.initialGearHistory();
            var c=port.client;player=c.player;world=c.level;listener=c.getConnection();transport=c.getConnection().getConnection();inventory=c.player.getInventory();options=c.options;gameMode=c.gameMode;uuid=c.player.getUUID();
            selected=c.player.getInventory().getSelectedSlot();selectedHistory=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(inventory);
            full36=capture36(c);originalGear=captureGear(c);journal=new dev.veltraph.client.garden.loadout.RetainedLoadoutJournal(encodings(full36),contract.exactRow());refreshAliases();
        }
        private void refreshAliases(){protectedAliases.clear();protectedAliases.putAll(cursorAliases);var c=port.client;for(int i=0;i<36;i++){var stack=c.player.getInventory().getItem(i);if(!stack.isEmpty()){protectedAliases.put(stack,Boolean.TRUE);protectedAliases.put(stack.getComponents(),Boolean.TRUE);}}
            for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND}){var stack=c.player.getItemBySlot(slot);if(!stack.isEmpty()){protectedAliases.put(stack,Boolean.TRUE);protectedAliases.put(stack.getComponents(),Boolean.TRUE);}}}
        /** Callback-free native leaf follows every source/loan callback before effects or acceptance. */
        private boolean nativeLeaf(){var c=port.client;
            boolean current=!retired&&initial!=null&&initial.handedTo(this)&&activeRetained==this&&port.retained==this&&c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==listener
                &&c.player!=null&&c.player.isAlive()&&c.player.level()==world&&uuid.equals(c.player.getUUID())&&c.options==options&&c.gameMode==gameMode
                &&c.player.getInventory()==inventory&&c.getConnection().getConnection()==transport&&((net.minecraft.network.Connection)transport).isConnected()&&c.player.getInventory().getSelectedSlot()==selected
                &&selectedHistory!=null&&selectedHistory.current()&&full36.equals(capture36(c))
                &&operation.bindingsCurrentFor(port,flow,target.name(),settings,ancillaryContract)&&operation.nativeCurrentFor(port,flow,target.name())&&initial.handedTo(this)&&!journal.refused();
            // Preserve the original window: sample AFTER all snapshot/source callbacks, never from a prior tick.
            if(!current)return false;long finalNow=System.nanoTime();
            return finalNow>=issuedAt&&finalNow-issuedAt<=10_000_000_000L;
        }
        private boolean current(){try{return operation.currentFor(port,flow,target.name())&&nativeLeaf();}catch(RuntimeException unknown){journal.refuse();return false;}}
        private boolean effect(dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect requested,NamedLoadoutFlow.Observation expected,int slot,java.util.function.BooleanSupplier original){
            if(effect!=null||sendsInFlight!=0||receivesInFlight!=0||!operation.actionsAllowed()||!current()||port.valid(expected)==null){journal.refuse();return false;}
            int menu=expected.snapshot()==null?-1:expected.snapshot().id(),revision=expected.snapshot()==null?-1:expected.snapshot().revision();
            if(requested==dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.CLICK){
                var rows=expected.snapshot().slots().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.id()==slot&&r.count()==1&&r.name().equalsIgnoreCase(target.name())
                    &&r.itemId().equals(target.actionItemId())&&r.lore().contains(settings.inactiveMarker())&&!r.lore().contains(settings.activeMarker())).toList();
                if(rows.size()!=1||expected.menu()!=ownedMenu){journal.refuse();return false;}clickedSlot=slot;clickedRevision=revision;clickRow=dev.veltraph.client.inventory.NativeStackIdentity.capture(port.client.player.containerMenu.slots.get(slot).getItem(),port.client.level.registryAccess());
            }
            if(requested==dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.CLOSE&&(expected.menu()!=ownedMenu||!journal.completeApplied())){journal.refuse();return false;}
            if(!journal.begin(requested,current(),menu,revision,nativeSerial))return false;
            effect=requested;boolean normal=false,result=false;
            try{result=original.getAsBoolean();normal=true;return result;}
            finally{journal.returned(requested,normal&&result,current());effect=null;refreshAliases();}
        }
        /** Actual native window age, diagnostic only. */
        public long elapsedMillis(){return (System.nanoTime()-issuedAt)/1_000_000L;}
        public boolean refused(){return journal.refused();}
        public void retire(){if(activeRetained==this)activeRetained=null;if(port.retained==this)port.retained=null;retired=true;}
    }
    /** Privately minted from actual native submissions, applied packets and this exact APPLIED flow. */
    public static final class AppliedReceipt {
        private final RetainedWindow window;private final NamedLoadoutFlow.Receipt actualFlowReceipt;
        private final List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> inventory;
        private final Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> gear;
        private final IdentityHashMap<Object,Boolean> gearAliases=new IdentityHashMap<>();private boolean gearRevoked;private final long ancillarySerial;
        private AppliedReceipt(RetainedWindow window){this.window=window;actualFlowReceipt=window.flow.receipt();inventory=window.full36;gear=captureGear(window.port.client);ancillarySerial=nativeSerial;
            for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND}){var stack=window.port.client.player.getItemBySlot(slot);if(!stack.isEmpty()){gearAliases.put(stack,Boolean.TRUE);gearAliases.put(stack.getComponents(),Boolean.TRUE);}}
            // Data capture only. Static passive lineage is published only by appliedReceipt after its final fence.
        }
        public boolean currentFor(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation operation,NamedLoadoutFlow flow,String target){
            return window.operation==operation&&window.flow==flow&&window.target.name().equals(target)&&window.receipt==this
                &&flow.receipt()==actualFlowReceipt&&flow.state()==NamedLoadoutFlow.State.APPLIED&&window.current()&&retainedGearCurrent();
        }
        /**
         * Negative observation of the genuine completed lease while its original tuple is still held.
         * This never admits a loadout effect and never uses the active HELPER_ACTIVE window gate.
         */
        public boolean terminalCurrentFor(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation operation,NamedLoadoutFlow flow,String target){
            try{
                if(!originalWindowDeadlineCurrentFor(operation,flow,target)||!retainedLeaf()
                        ||!operation.terminalAppliedObservationCurrent(window.port,flow,target,window.settings,window.ancillaryContract))return false;
                var c=window.port.client;
                boolean observed=window.full36.equals(capture36(c))&&inventory==window.full36
                    &&gear.equals(captureGear(c))&&equipped(c.player).equals(window.target.gear())
                    &&gearEncodings(gear).equals(window.ancillaryContract.exactNativeGear())
                    &&window.selectedHistory!=null&&window.selectedHistory.current()
                    &&c.player.getInventory().getSelectedSlot()==window.selected
                    &&c.gui.screen()==null&&c.player.containerMenu==c.player.inventoryMenu
                    &&c.player.containerMenu.getCarried().isEmpty()
                    &&ancillary!=null&&ancillary.listener()==window.listener&&ancillary.player()==window.player
                    &&ancillary.world()==window.world&&ancillary.serial()==ancillarySerial
                    &&ancillary.row().equals(window.ancillaryContract.exactRow());
                if(!observed||!retainedLeaf()
                        ||!operation.terminalAppliedObservationCurrent(window.port,flow,target,window.settings,window.ancillaryContract))return false;
                // All capture/source/native callbacks precede these private original-window facts and clock.
                return !window.retired&&activeRetained==window&&window.port.retained==window
                    &&window.initial!=null&&window.initial.handedTo(window)&&window.effect==null
                    &&window.sendsInFlight==0&&window.receivesInFlight==0&&window.sending==null
                    &&window.receiving==null&&window.inventoryWrite==null&&window.journal.complete(true,true)
                    &&!gearRevoked&&passiveGear==this&&originalWindowDeadlineCurrentFor(operation,flow,target);
            }catch(RuntimeException unknown){return false;}
        }
        /**
         * Pure negative identity/deadline veto after final native callbacks. The original private
         * issuance time is never refreshed; a matching clock observation grants no action authority.
         */
        public boolean originalWindowDeadlineCurrentFor(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation operation,NamedLoadoutFlow flow,String target){
            if(operation==null||flow==null||target==null||window.operation!=operation||window.flow!=flow
                    ||!window.target.name().equals(target)||window.receipt!=this||flow.receipt()!=actualFlowReceipt
                    ||flow.state()!=NamedLoadoutFlow.State.APPLIED||gearRevoked||passiveGear!=this
                    ||window.retired||activeRetained!=window||window.port.retained!=window)return false;
            // Final private original-submission facts follow every late source/native callback.
            // RetainedLoadoutJournal is final; complete/completeApplied read only its bare fields.
            if(window.initial==null||!window.initial.handedTo(window)||window.effect!=null
                    ||window.sendsInFlight!=0||window.receivesInFlight!=0||window.sending!=null
                    ||window.receiving!=null||window.inventoryWrite!=null||!window.journal.complete(true,true))return false;
            long finalNow=System.nanoTime();
            return finalNow>=window.issuedAt&&finalNow-window.issuedAt<=10_000_000_000L;
        }
        /** Passive gear-only lineage survives retirement. Root separately proves original farm/profile/config. */
        private boolean retainedLeaf(){var c=window.port.client;
            return !gearRevoked&&passiveGear==this&&c.isSameThread()&&c.player==window.player&&c.level==window.world&&c.getConnection()==window.listener
                &&c.player!=null&&c.player.isAlive()&&c.player.level()==window.world&&window.uuid.equals(c.player.getUUID())&&c.options==window.options&&c.gameMode==window.gameMode
                &&c.player.getInventory()==window.inventory&&c.getConnection().getConnection()==window.transport&&((net.minecraft.network.Connection)window.transport).isConnected();
        }
        public boolean retainedGearCurrent(){try{if(!retainedLeaf())return false;var observed=captureGear(window.port.client);return gear.equals(observed)&&retainedLeaf();}catch(RuntimeException unknown){revokeRetainedGear();return false;}}
        /** Genuine operation's initial native gear, never recaptured from a caller-provided public Receipt. */
        public Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> initialGear(){return window.originalGear;}
        public List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> protectedInventory(){return inventory;}
        public Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> appliedGear(){return gear;}
        public String target(){return window.target.name();}
        /** Refusal only, releasing the static passive graph on original-run teardown; no renewal. */
        public void revokeRetainedGear(){gearRevoked=true;if(passiveGear==this)passiveGear=null;}
    }
    /** Passive diagnostic only; neither empty metadata nor matching values admits an operation. */
    public static boolean retainedNativeMetadataClear(){return activeRetained==null&&initialGear==null;}
    public RetainedWindow beginRetained(NamedLoadoutFlow flow,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation operation,
            NamedLoadoutFlow.Settings settings,String name,AncillaryContract contract){
        if(!client.isSameThread()||activeRetained!=null||retained!=null||flow==null||!flow.usesExactPort(this)||flow.active()||operation==null||settings==null||contract==null
            ||consumedRetained.containsKey(operation)||client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu
            ||!client.player.containerMenu.getCarried().isEmpty())return null;
        var targets=settings.profiles().stream().filter(p->p.name().equals(name)).toList();if(targets.size()!=1)return null;
        var observed=ancillary;if(observed==null||observed.listener()!=client.getConnection()||observed.player()!=client.player||observed.world()!=client.level||!observed.row().equals(contract.exactRow())||System.nanoTime()<observed.at()||System.nanoTime()-observed.at()>30_000_000_000L)return null;
        var original=operation.initialGearHistory();if(original==null||original.port!=this||!original.current())return null;
        if(!operation.currentFor(this,flow,name)||!operation.bindingsCurrentFor(this,flow,name,settings,contract)||!operation.nativeCurrentFor(this,flow,name))return null;
        RetainedWindow next;try{next=new RetainedWindow(this,flow,operation,settings,targets.getFirst(),contract);}catch(RuntimeException unknown){return null;}
        if(!original.current()||!next.originalGear.equals(original.gear)||!original.consumeInto(next))return null;
        consumedRetained.put(operation,Boolean.TRUE);retained=next;activeRetained=next;if(!next.current()){next.retire();return null;}return next;
    }
    public AppliedReceipt appliedReceipt(RetainedWindow exact){
        if(exact==null||retained!=exact||activeRetained!=exact||exact.port!=this||exact.flow.state()!=NamedLoadoutFlow.State.APPLIED||exact.flow.receipt()==null||exact.flow.receipt().restoring()
            ||!exact.flow.usesExactPort(this)||!exact.flow.requestName().equals(exact.target.name())||client.player==null||!equipped(client.player).equals(exact.target.gear())||!gearEncodings(captureGear(client)).equals(exact.ancillaryContract.exactNativeGear())
            ||!exact.current()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()
            ||!exact.journal.complete(true,exact.nativeLeaf()))return null;
        if(exact.receipt==null){
            var next=new AppliedReceipt(exact);
            // Constructor snapshots can run native getters; a late capture never publishes a usable receipt.
            if(!next.gear.equals(captureGear(client))||!exact.current()||!exact.nativeLeaf()){
                next.gearRevoked=true;exact.journal.refuse();return null;
            }
            if(passiveGear!=null)passiveGear.gearRevoked=true;
            passiveGear=next;exact.receipt=next;
            return next; // No snapshot/source callback is permitted after final validation and publication.
        }
        return exact.current()&&exact.nativeLeaf()?exact.receipt:null;
    }
    private static List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> capture36(Minecraft c){var values=new ArrayList<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot>();
        for(int i=0;i<36;i++)values.add(dev.veltraph.client.inventory.NativeStackIdentity.capture(c.player.getInventory().getItem(i),c.level.registryAccess()));return List.copyOf(values);}
    private static List<String> encodings(List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> values){return values.stream().map(dev.veltraph.client.inventory.NativeStackIdentity.Snapshot::persistentCanonicalEncoding).toList();}
    private static Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> captureGear(Minecraft c){var values=new LinkedHashMap<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot>();
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND})values.put(slot.name(),dev.veltraph.client.inventory.NativeStackIdentity.capture(c.player.getItemBySlot(slot),c.level.registryAccess()));return Map.copyOf(values);}
    private static Map<String,String> gearEncodings(Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> gear){var values=new LinkedHashMap<String,String>();gear.forEach((key,value)->values.put(key,value.persistentCanonicalEncoding()));return Map.copyOf(values);}
    /** Implemented only by target mixins; tokens belong to their private in-flight frame. */
    public interface NativeSendCarrier {
        Object namedLoadoutSendClaim(Object packet);
        boolean namedLoadoutSendCurrent(Object token,Object packet,boolean normal);
    }
    public interface NativeCallCarrier {
        Object namedLoadoutClaim(Object packet,int kind);
        boolean namedLoadoutCurrent(Object token,Object packet,int kind,boolean normal);
    }
    /** This observer accepts only calls on an actual transformed native method stack. */
    public static final class SendInvocation {
        private final RetainedWindow window;private final Object listener,packet,token;private final NativeSendCarrier carrier;private final boolean matching;private int transportEntries,transportReturns;private boolean transportFailed;
        private SendInvocation(RetainedWindow window,Object listener,Object packet,boolean matching,NativeSendCarrier carrier,Object token){this.window=window;this.listener=listener;this.packet=packet;this.matching=matching;this.carrier=carrier;this.token=token;}
    }
    public static SendInvocation sendEntry(Object listener,net.minecraft.network.protocol.Packet<?> packet){
        var w=activeRetained;if(w==null||!w.port.client.isSameThread()||listener!=w.listener||!(listener instanceof NativeSendCarrier carrier))return null;
        boolean inventoryTraffic=packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket||packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket
            ||packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket||packet instanceof net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
        if(!inventoryTraffic)return null;
        var token=carrier.namedLoadoutSendClaim(packet);if(token==null||!carrier.namedLoadoutSendCurrent(token,packet,false)){w.journal.refuse();return null;}
        if(w.effect==null||w.sendsInFlight!=0||!w.nativeLeaf()){w.journal.refuse();return null;}
        boolean matching=switch(w.effect){
            case COMMAND->packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket p&&p.command().equals(w.settings.command());
            case CLICK->packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket p&&p.containerId()==w.ownedMenuId&&p.stateId()==w.clickedRevision&&p.slotNum()==w.clickedSlot&&p.buttonNum()==0&&p.containerInput()==net.minecraft.world.inventory.ContainerInput.PICKUP;
            case CLOSE->packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket p&&p.getContainerId()==w.ownedMenuId;};
        w.sendsInFlight++;var call=new SendInvocation(w,listener,packet,matching,carrier,token);w.sending=call;return call;
    }
    public static void sendExit(SendInvocation call,Object listener,Object packet,boolean normal){
        if(call==null)return;var w=call.window;if(activeRetained!=w)return;
        boolean exact=call.listener==listener&&call.packet==packet&&w.sendsInFlight==1&&call.transportEntries==1&&call.transportReturns==1&&!call.transportFailed&&call.carrier.namedLoadoutSendCurrent(call.token,packet,true)&&w.nativeLeaf();w.sendsInFlight--;w.sending=null;
        w.journal.submitted(w.effect,call.matching&&exact,normal);
    }
    public interface NativeTransportCarrier {
        Object namedLoadoutTransportClaim(Object packet,Object callback,Object flush);
        boolean namedLoadoutTransportCurrent(Object token,Object packet,Object callback,Object flush,boolean normal);
    }
    public static final class TransportInvocation {
        private final SendInvocation send;private final Object transport,packet,callback,flush,token;private final NativeTransportCarrier carrier;
        private TransportInvocation(SendInvocation send,Object transport,Object packet,Object callback,Object flush,NativeTransportCarrier carrier,Object token){this.send=send;this.transport=transport;this.packet=packet;this.callback=callback;this.flush=flush;this.carrier=carrier;this.token=token;}
    }
    public static TransportInvocation transportEntry(Object transport,net.minecraft.network.protocol.Packet<?> packet,Object callback,boolean flush){
        var w=activeRetained;if(w==null||!w.port.client.isSameThread()||transport!=w.transport||!(transport instanceof NativeTransportCarrier carrier))return null;
        var send=w.sending;if(send==null||send.packet!=packet||send.transportEntries!=0||!send.carrier.namedLoadoutSendCurrent(send.token,packet,false)||!w.nativeLeaf()){
            if(packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket||packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket||packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket||packet instanceof net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket)w.journal.refuse();return null;}
        Object flag=Boolean.valueOf(flush),token=carrier.namedLoadoutTransportClaim(packet,callback,flag);if(token==null||!carrier.namedLoadoutTransportCurrent(token,packet,callback,flag,false)){w.journal.refuse();return null;}
        send.transportEntries++;return new TransportInvocation(send,transport,packet,callback,flag,carrier,token);
    }
    public static void transportExit(TransportInvocation call,boolean normal){
        if(call==null)return;var w=call.send.window;if(activeRetained!=w)return;
        boolean exact=normal&&w.sending==call.send&&call.send.transportEntries==1&&call.send.transportReturns==0&&w.transport==call.transport
            &&call.carrier.namedLoadoutTransportCurrent(call.token,call.packet,call.callback,call.flush,true)
            &&call.send.carrier.namedLoadoutSendCurrent(call.send.token,call.packet,false)&&w.nativeLeaf();
        if(exact)call.send.transportReturns++;else {call.send.transportFailed=true;w.journal.refuse();}
    }
    public static final class ReceiveInvocation {
        private final RetainedWindow window;private final Object listener,packet,player,world,token;private final NativeCallCarrier carrier;private final int kind;private final long serial;
        private final List<ItemStack> items;private final ItemStack carried;private final String text;private final int menu,revision;
        private final Map<Integer,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> writes;
        private final Map<Integer,Integer> written=new HashMap<>();private boolean activeRow;
        private dev.veltraph.client.inventory.NativeStackIdentity.Snapshot rowIdentity;
        private ReceiveInvocation(RetainedWindow window,Object listener,Object packet,Object player,Object world,long serial,List<ItemStack> items,ItemStack carried,String text,int menu,int revision,NativeCallCarrier carrier,Object token,int kind){
            this.window=window;this.listener=listener;this.packet=packet;this.player=player;this.world=world;this.serial=serial;this.items=items;this.carried=carried;this.text=text;this.menu=menu;this.revision=revision;this.carrier=carrier;this.token=token;this.kind=kind;
            var c=Minecraft.getInstance();var map=new LinkedHashMap<Integer,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot>();
            if(packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket content){
                var actual=content.containerId()==c.player.inventoryMenu.containerId?c.player.inventoryMenu:c.player.containerMenu;
                if(actual.containerId==content.containerId())for(var slot:actual.slots)if(slot.container==c.player.getInventory()&&slot.getContainerSlot()>=0&&slot.getContainerSlot()<=40&&slot.index>=0&&slot.index<content.items().size())
                    map.put(slot.getContainerSlot(),dev.veltraph.client.inventory.NativeStackIdentity.capture(content.items().get(slot.index),c.level.registryAccess()));
                if(window!=null&&window.clickedSlot>=0&&actual==window.ownedMenu&&window.clickedSlot<content.items().size()){
                    var row=content.items().get(window.clickedSlot);activeRow=activeRow(window,row);rowIdentity=dev.veltraph.client.inventory.NativeStackIdentity.capture(row,c.level.registryAccess());
                }
            }else if(packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket slot){
                int index=-1;if(slot.getContainerId()==-2)index=slot.getSlot();else {var actual=slot.getContainerId()==c.player.inventoryMenu.containerId?c.player.inventoryMenu:c.player.containerMenu;
                    if(actual.containerId==slot.getContainerId()&&slot.getSlot()>=0&&slot.getSlot()<actual.slots.size()){var cell=actual.slots.get(slot.getSlot());if(cell.container==c.player.getInventory())index=cell.getContainerSlot();}}
                if(index>=0&&index<=40)map.put(index,dev.veltraph.client.inventory.NativeStackIdentity.capture(slot.getItem(),c.level.registryAccess()));
            }else if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket slot){
                if(slot.slot()>=0&&slot.slot()<=40)map.put(slot.slot(),dev.veltraph.client.inventory.NativeStackIdentity.capture(slot.contents(),c.level.registryAccess()));
            }
            writes=Map.copyOf(map);
        }
    }
    private static boolean activeRow(RetainedWindow w,ItemStack row){var lore=row.get(net.minecraft.core.component.DataComponents.LORE);
        return row.getCount()==1&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(row.getItem()).toString().equals(w.target.activeItemId())
            &&dev.veltraph.client.gui.MenuSnapshot.clean(row.getHoverName().getString()).equals(w.target.name())&&lore!=null
            &&lore.lines().stream().map(line->dev.veltraph.client.gui.MenuSnapshot.clean(line.getString())).anyMatch(w.settings.activeMarker()::equals)
            &&lore.lines().stream().map(line->dev.veltraph.client.gui.MenuSnapshot.clean(line.getString())).noneMatch(w.settings.inactiveMarker()::equals);
    }
    public static ReceiveInvocation receiveEntry(Object listener,net.minecraft.network.protocol.Packet<?> packet){
        var c=Minecraft.getInstance();if(c==null||!c.isSameThread()||c.player==null||c.level==null||listener!=c.getConnection()
            ||!(listener instanceof NativeCallCarrier carrier))return null;
        int kind=packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket?1:packet instanceof net.minecraft.network.protocol.game.ClientboundOpenScreenPacket?2:packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket?3:packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket?4:packet instanceof net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket?5:packet instanceof net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket?6:-1;
        if(kind<0)return null;var token=carrier.namedLoadoutClaim(packet,kind);if(token==null||!carrier.namedLoadoutCurrent(token,packet,kind,false))return null;
        if(nativeSerial==Long.MAX_VALUE){if(activeRetained!=null)activeRetained.journal.refuse();return null;}long serial=++nativeSerial;
        var w=activeRetained;if(w!=null){if(!w.nativeLeaf()||w.receivesInFlight!=0){w.journal.refuse();w=null;}else w.receivesInFlight++;}
        List<ItemStack> items=List.of();ItemStack carried=ItemStack.EMPTY;String text=null;int menu=-1,revision=-1;
        if(packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket p){items=p.items().stream().map(ItemStack::copy).toList();carried=p.carriedItem().copy();menu=p.containerId();revision=p.stateId();}
        else if(packet instanceof net.minecraft.network.protocol.game.ClientboundOpenScreenPacket p){menu=p.getContainerId();text=dev.veltraph.client.gui.MenuSnapshot.clean(p.getTitle().getString());}
        else if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket p)carried=p.contents().copy();
        else if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p)text=dev.veltraph.client.gui.MenuSnapshot.clean(p.content().getString());
        var call=new ReceiveInvocation(w,listener,packet,c.player,c.level,serial,items,carried,text,menu,revision,carrier,token,kind);if(w!=null){w.receiving=call;if(!carried.isEmpty())w.journal.refuse();}return call;
    }
    public static void receiveExit(ReceiveInvocation call,boolean normal){
        if(call==null)return;var c=Minecraft.getInstance();boolean same=c!=null&&c.isSameThread()&&c.player==call.player&&c.level==call.world&&c.getConnection()==call.listener&&call.carrier.namedLoadoutCurrent(call.token,call.packet,call.kind,true);
        if(call.kind==3&&call.packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket&&call.text!=null&&normal&&same&&call.text.startsWith("Veltraph private armor-only state ")){if(initialGear!=null&&initialGear.player==call.player&&initialGear.listener==call.listener&&call.serial>initialGear.serial)initialGear.revoked=true;if(passiveGear!=null&&passiveGear.window.player==call.player&&passiveGear.window.listener==call.listener&&call.serial>passiveGear.ancillarySerial)passiveGear.gearRevoked=true;ancillary=new AncillaryObservation(call.listener,call.player,call.world,call.serial,System.nanoTime(),call.text);}
        var w=call.window;if(w==null||activeRetained!=w)return;boolean current=same&&w.receivesInFlight==1&&w.nativeLeaf();w.receivesInFlight--;w.receiving=null;
        if(!normal||!current||!call.written.keySet().equals(call.writes.keySet())||call.written.values().stream().anyMatch(value->value!=1)){w.journal.refuse();return;}
        if(call.packet instanceof net.minecraft.network.protocol.game.ClientboundOpenScreenPacket){
            var menu=c.player.containerMenu;var snapshot=w.port.menus.read();boolean matching=w.effect==null&&snapshot!=null&&snapshot.title().equals(w.settings.menuTitle())&&snapshot.id()==call.menu&&snapshot.title().equals(call.text);
            if(!matching){w.journal.refuse();return;}w.ownedMenu=menu;w.ownedMenuId=menu.containerId;w.journal.opened(menu.containerId,true,w.nativeLeaf());
        }else if(call.packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket){
            if(w.clickedSlot<0)return;
            var actualMenu=c.player.containerMenu;
            if(call.menu==w.ownedMenuId&&actualMenu==w.ownedMenu){
                boolean row=call.activeRow&&call.rowIdentity!=null&&w.clickedSlot<actualMenu.slots.size()
                    &&call.rowIdentity.matches(actualMenu.slots.get(w.clickedSlot).getItem(),c.level.registryAccess())&&activeRow(w,actualMenu.slots.get(w.clickedSlot).getItem());
                w.journal.appliedMenu(call.menu,call.revision,call.serial,row,call.carried.isEmpty()&&actualMenu.getCarried().isEmpty(),true,current&&actualMenu.getStateId()==call.revision&&w.nativeLeaf());
            }
            if(call.menu==c.player.inventoryMenu.containerId){
                if(c.player.inventoryMenu.getStateId()!=call.revision){w.journal.refuse();return;}
                var rows=new ArrayList<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot>();
                for(int i=0;i<36;i++){var cell=call.writes.get(i);if(cell==null){w.journal.refuse();return;}rows.add(cell);}
                w.journal.appliedInventory(call.serial,encodings(rows),encodings(capture36(c)),call.carried.isEmpty()&&c.player.inventoryMenu.getCarried().isEmpty(),true,current&&w.nativeLeaf());
                var wireGear=new LinkedHashMap<String,String>();String[] gearNames={"FEET","LEGS","CHEST","HEAD","OFFHAND"};
                for(int i=0;i<5;i++){var cell=call.writes.get(36+i);if(cell==null){w.journal.refuse();return;}wireGear.put(gearNames[i],cell.persistentCanonicalEncoding());}
                boolean gear=equipped(c.player).equals(w.target.gear())&&wireGear.equals(w.ancillaryContract.exactNativeGear())&&gearEncodings(captureGear(c)).equals(wireGear);
                w.journal.appliedGear(call.serial,gear,true,current&&w.nativeLeaf());
            }
        }else if(call.kind==3&&call.packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket&&call.text!=null&&call.text.startsWith("Veltraph private armor-only state ")&&w.clickedSlot>=0)w.journal.appliedAncillary(call.serial,call.text,true,current&&w.nativeLeaf());
        w.refreshAliases();if(!w.nativeLeaf())w.journal.refuse();
    }
    /** Same-value or ABA native writes to protected live stacks invalidate the exact old operation. */
    /** Private exact normally returning setter belonging to one immutable wire cell. */
    public static final class InventoryWrite {
        private final RetainedWindow window;private final ReceiveInvocation receive;private final Object inventory;private final int index;
        private final ItemStack next;private final dev.veltraph.client.inventory.NativeStackIdentity.Snapshot value;
        private int equipmentWrites;private boolean equipmentFailed;
        private InventoryWrite(RetainedWindow window,ReceiveInvocation receive,Object inventory,int index,ItemStack next,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot value){this.window=window;this.receive=receive;this.inventory=inventory;this.index=index;this.next=next;this.value=value;}
    }
    public static InventoryWrite inventoryWriteEntry(net.minecraft.world.entity.player.Inventory inventory,int index,ItemStack next){
        var start=initialGear;if(start!=null&&start.port.client.isSameThread()&&start.inventory==inventory&&index>=36)start.revoked=true;
        var observed=passiveGear;if(observed!=null&&observed.window.port.client.isSameThread()&&observed.window.inventory==inventory&&index>=36)observed.gearRevoked=true;
        var w=activeRetained;if(w==null||!w.port.client.isSameThread()||w.inventory!=inventory)return null;
        var call=w.receiving;var expected=call==null?null:call.writes.get(index);
        if(index<0||index>40||call==null||w.receivesInFlight!=1||w.inventoryWrite!=null||expected==null||call.written.containsKey(index)
            ||!call.carrier.namedLoadoutCurrent(call.token,call.packet,call.kind,false)||!expected.matches(next,w.port.client.level.registryAccess())){w.journal.refuse();return null;}
        if(index<36&&!w.full36.get(index).equals(expected)){w.journal.refuse();return null;}
        if(index>=36){String name=new String[]{"FEET","LEGS","CHEST","HEAD","OFFHAND"}[index-36];
            var old=dev.veltraph.client.inventory.NativeStackIdentity.capture(inventory.getItem(index),w.port.client.level.registryAccess());
            String allowed=w.clickedSlot<0?old.persistentCanonicalEncoding():w.ancillaryContract.exactNativeGear().get(name);
            if(!allowed.equals(expected.persistentCanonicalEncoding())){w.journal.refuse();return null;}
        }
        var write=new InventoryWrite(w,call,inventory,index,next,expected);call.written.put(index,0);w.inventoryWrite=write;if(!next.isEmpty()){w.protectedAliases.put(next,Boolean.TRUE);w.protectedAliases.put(next.getComponents(),Boolean.TRUE);}return write;
    }
    public static void inventoryWriteExit(InventoryWrite write,boolean normal){
        if(write==null)return;var w=write.window;if(activeRetained!=w)return;
        boolean exact=normal&&w.inventoryWrite==write&&w.receiving==write.receive&&write.receive.carrier.namedLoadoutCurrent(write.receive.token,write.receive.packet,write.receive.kind,false)
            &&write.value.matches(((net.minecraft.world.entity.player.Inventory)write.inventory).getItem(write.index),w.port.client.level.registryAccess())
            &&(write.index<36?write.equipmentWrites==0:write.equipmentWrites==1)&&!write.equipmentFailed;
        write.receive.written.put(write.index,exact?1:-1);w.inventoryWrite=null;if(!exact)w.journal.refuse();
    }
    public interface EquipmentCarrier {net.minecraft.world.entity.EntityEquipment namedLoadoutEquipment();}
    public static final class EquipmentWrite {
        private final InventoryWrite original;private final Object equipment;private final EquipmentSlot slot;private final ItemStack next;
        private EquipmentWrite(InventoryWrite original,Object equipment,EquipmentSlot slot,ItemStack next){this.original=original;this.equipment=equipment;this.slot=slot;this.next=next;}
    }
    public static EquipmentWrite equipmentWriteEntry(Object equipment,EquipmentSlot slot,ItemStack next){
        var start=initialGear;if(start!=null&&start.port.client.isSameThread()&&start.equipment==equipment)start.revoked=true;
        var observed=passiveGear;if(observed!=null&&observed.window.port.client.isSameThread()&&observed.window.inventory instanceof EquipmentCarrier held&&held.namedLoadoutEquipment()==equipment)observed.gearRevoked=true;
        var w=activeRetained;if(w==null||!w.port.client.isSameThread()||!(w.inventory instanceof EquipmentCarrier actual)||actual.namedLoadoutEquipment()!=equipment)return null;
        var call=w.inventoryWrite;String[] names={"FEET","LEGS","CHEST","HEAD","OFFHAND"};
        if(call==null||call.index<36||call.index>40||!names[call.index-36].equals(slot.name())||call.next!=next||call.equipmentWrites!=0||call.equipmentFailed
            ||w.receiving!=call.receive||!call.value.matches(next,w.port.client.level.registryAccess())){w.journal.refuse();return null;}
        call.equipmentWrites++;return new EquipmentWrite(call,equipment,slot,next);
    }
    public static void equipmentWriteExit(EquipmentWrite call,boolean normal){
        if(call==null)return;var write=call.original;var w=write.window;if(activeRetained!=w)return;
        if(!normal||w.inventoryWrite!=write||w.receiving!=write.receive||!write.value.matches(w.port.client.player.getItemBySlot(call.slot),w.port.client.level.registryAccess())){write.equipmentFailed=true;w.journal.refuse();}
    }
    public static void equipmentMutation(Object equipment){var start=initialGear;if(start!=null&&start.port.client.isSameThread()&&start.equipment==equipment)start.revoked=true;
        var observed=passiveGear;if(observed!=null&&observed.window.port.client.isSameThread()&&observed.window.inventory instanceof EquipmentCarrier held&&held.namedLoadoutEquipment()==equipment)observed.gearRevoked=true;var w=activeRetained;if(w!=null&&w.port.client.isSameThread()&&w.inventory instanceof EquipmentCarrier actual&&actual.namedLoadoutEquipment()==equipment)w.journal.refuse();}
    public static void wholeInventoryMutation(Object inventory){var start=initialGear;if(start!=null&&start.port.client.isSameThread()&&start.inventory==inventory)start.revoked=true;var observed=passiveGear;if(observed!=null&&observed.window.port.client.isSameThread()&&observed.window.inventory==inventory)observed.gearRevoked=true;protectedInventoryMutation(inventory);}
    public static void protectedInventoryMutation(Object inventory){var w=activeRetained;if(w!=null&&w.port.client.isSameThread()&&w.inventory==inventory)w.journal.refuse();}
    public static void cursorMutation(Object menu,ItemStack next){
        var w=activeRetained;if(w==null||!w.port.client.isSameThread()||w.port.client.player.containerMenu!=menu&&w.port.client.player.inventoryMenu!=menu)return;
        boolean prediction=w.effect==dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.CLICK&&menu==w.ownedMenu&&w.receivesInFlight==0
            &&w.predictedCursorWrites==0&&w.clickRow!=null&&w.clickRow.matches(next,w.port.client.level.registryAccess());
        if(prediction){w.predictedCursorWrites++;if(!next.isEmpty()){w.cursorAliases.put(next,Boolean.TRUE);w.cursorAliases.put(next.getComponents(),Boolean.TRUE);w.protectedAliases.put(next,Boolean.TRUE);w.protectedAliases.put(next.getComponents(),Boolean.TRUE);}}
        boolean ownClose=w.effect==dev.veltraph.client.garden.loadout.RetainedLoadoutJournal.Effect.CLOSE&&next.isEmpty()&&w.receivesInFlight==0;
        var receive=w.receiving;boolean serverEmpty=next.isEmpty()&&receive!=null&&w.receivesInFlight==1&&receive.carried.isEmpty()
            &&(receive.kind==1||receive.kind==6)&&receive.carrier.namedLoadoutCurrent(receive.token,receive.packet,receive.kind,false);
        if(!prediction&&!ownClose&&!serverEmpty)w.journal.refuse();
    }
    public static void nativeObservationFailure(){var start=initialGear;if(start!=null&&start.port.client.isSameThread())start.revoke();var passive=passiveGear;if(passive!=null&&passive.window.port.client.isSameThread())passive.revokeRetainedGear();var w=activeRetained;if(w!=null&&w.port.client.isSameThread())w.journal.refuse();}
    public static void protectedComponentMutation(Object components){var start=initialGear;if(start!=null&&start.port.client.isSameThread()&&start.aliases.containsKey(components))start.revoked=true;var observed=passiveGear;if(observed!=null&&observed.window.port.client.isSameThread()&&observed.gearAliases.containsKey(components))observed.gearRevoked=true;var w=activeRetained;if(w!=null&&w.port.client.isSameThread()&&w.protectedAliases.containsKey(components))w.journal.refuse();}
    public static void protectedStackMutation(ItemStack stack){var start=initialGear;if(start!=null&&start.port.client.isSameThread()&&start.aliases.containsKey(stack))start.revoked=true;var observed=passiveGear;if(observed!=null&&observed.window.port.client.isSameThread()&&observed.gearAliases.containsKey(stack))observed.gearRevoked=true;var w=activeRetained;if(w!=null&&w.port.client.isSameThread()&&w.protectedAliases.containsKey(stack))w.journal.refuse();}
}
