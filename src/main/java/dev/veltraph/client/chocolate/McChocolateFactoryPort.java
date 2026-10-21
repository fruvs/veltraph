package dev.veltraph.client.chocolate;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import java.math.BigDecimal;
import java.util.*;

/** Client-thread ingestion of real visible lore plus ALL native persistent effective components. */
public final class McChocolateFactoryPort implements ChocolateFactoryRuntime.Port,ChocolateConsequentialRuntime.Port,ChocolateSelectedTransactionRuntime.Port {
    private final Minecraft client;private final IdentityHashMap<ChocolateFactoryObservation,Binding> bindings=new IdentityHashMap<>();private final ArrayDeque<ChocolateFactoryObservation> bindingOrder=new ArrayDeque<>();
    private ChocolateFactoryProfile selectedSourceProfile;private ChocolateFactoryObservation selectedBefore;private ChocolateSelectedTransactionRuntime.Binding selectedBinding;private McVisitorHistoryPacketObserver.OwnedMenuInput selectedSource;private String selectedSourceTrace="No native selected input";
    private Object player,world,connection,session=new Object();private final Map<String,Object> observedScopes=new HashMap<>();
    private record Binding(Object player,Object world,Object connection,Object screen,Object menu,int id,int revision,Map<Integer,NativeStackIdentity.Snapshot> slots) {}
    public McChocolateFactoryPort(Minecraft client){this.client=Objects.requireNonNull(client);}
    public ChocolateFactoryObservation visibleMenu(){
        var p=new ChocolateFactoryProfile(1,"unconfigured","2026-09-30","",null,null,null,null,null,null,List.of(),5000,5000,null,null,null,null,false);return read(p);
    }
    public void bindObservedScope(String scope){
        if(scope==null||!scope.matches("[a-zA-Z0-9_-]{1,96}"))throw new IllegalArgumentException("Choose a short local Factory scope key");
        var raw=visibleMenu();if(raw==null||!raw.cursorEmpty())throw new IllegalArgumentException("Open the current Factory menu with an empty cursor");var state=CapturedChocolateFactoryParser.parse(raw.title(),raw.rows());if(!state.recognized()||!state.complete())throw new IllegalArgumentException("Captured Factory layout is incomplete/changed; wait for a stable menu");observedScopes.put(scope,session);
    }
    /** Explicit reconciliation declares a local label for this exact refreshed native inspection. */
    public void bindReconciliationInspection(String scope,ChocolateFactoryObservation inspected){thread();if(scope==null||!scope.matches("[a-zA-Z0-9_-]{1,96}"))throw new IllegalArgumentException("Exact original local scope required");var current=visibleMenu();if(current==null||inspected==null||current.session()!=inspected.session()||current.menu()!=inspected.menu()||current.screen()!=inspected.screen()||current.revision()!=inspected.revision()||!current.rows().equals(inspected.rows())||!current.cursorEmpty())throw new IllegalArgumentException("Native inspection changed before manual reconciliation");observedScopes.put(scope,session);}
    @Override public int currentHotbarSlot(){thread();return client.player==null?-1:client.player.getInventory().getSelectedSlot();}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Chocolate Factory observations require client thread");}
    @Override public ChocolateFactoryObservation read(ChocolateFactoryProfile p){return read(p,System.currentTimeMillis());}
    /** Capture and freshness check share a single clock boundary; elapsed codec work cannot make it future. */
    @Override public ChocolateFactoryObservation read(ChocolateFactoryProfile p,long observationTime){
        thread();
        if(player!=client.player||world!=client.level||connection!=client.getConnection()){bindings.clear();bindingOrder.clear();observedScopes.clear();player=client.player;world=client.level;connection=client.getConnection();session=new Object();}
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null||client.getConnection()==null||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu)return null;
        var menu=screen.getMenu();var rows=new LinkedHashMap<Integer,ChocolateFactoryObservation.Row>();var full=new HashMap<Integer,NativeStackIdentity.Snapshot>();
        try{for(var slot:menu.slots){var item=slot.getItem();var identity=NativeStackIdentity.capture(item,client.level.registryAccess());full.put(slot.index,identity);var lore=item.get(DataComponents.LORE);
            rows.put(slot.index,new ChocolateFactoryObservation.Row(slot.index,slot.container==client.player.getInventory(),BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.isEmpty()?"":MenuSnapshot.clean(item.getHoverName().getString()),item.getCount(),lore==null?List.of():lore.lines().stream().map(t->MenuSnapshot.clean(t.getString())).toList(),identity));if(slot.container==client.player.getInventory()){int stable=10000+slot.getContainerSlot();rows.put(stable,new ChocolateFactoryObservation.Row(stable,true,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.isEmpty()?"":MenuSnapshot.clean(item.getHoverName().getString()),item.getCount(),lore==null?List.of():lore.lines().stream().map(t->MenuSnapshot.clean(t.getString())).toList(),identity));}}}
        catch(RuntimeException unsupported){return null;}
        try{int extra=1000;for(var equipment:net.minecraft.world.entity.EquipmentSlot.values()){
            var item=client.player.getItemBySlot(equipment);var identity=NativeStackIdentity.capture(item,client.level.registryAccess());
            rows.put(extra,new ChocolateFactoryObservation.Row(extra,true,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.isEmpty()?"":MenuSnapshot.clean(item.getHoverName().getString()),item.getCount(),List.of(),identity));extra++;
        }}catch(RuntimeException unsupported){return null;}
        String title=MenuSnapshot.clean(screen.getTitle().getString());boolean verified=p.profileMarker()!=null&&p.profileMarker().matches(rows.get(p.profileMarker().slot()))&&title.equals(p.title());
        var quotes=new HashMap<String,ChocolateFactoryObservation.Quote>();
        if(verified)for(var u:p.upgrades()){
            var row=rows.get(u.button().slot());if(!u.button().matches(row)||row.itemId().equals("minecraft:barrier")||row.itemId().equals("minecraft:bedrock"))continue;
            var level=read(u.level(),rows);var cost=read(u.price(),rows);if(level.isEmpty()||cost.isEmpty()||cost.get().signum()<=0)continue;
            try{int l=level.get().intValueExact();if(l<0||l==Integer.MAX_VALUE)continue;quotes.put(u.id(),new ChocolateFactoryObservation.Quote(u.id(),u.kind(),row.slot(),l,cost.get(),row.fullIdentity(),rateDelta(u,row,l,cost.get(),read(p.production(),rows),observationTime),u.deltaAssumption()));}catch(ArithmeticException invalid){}
        }
        var result=new ChocolateFactoryObservation(p.scope(),session,menu,screen,menu.getStateId(),observationTime,title,verified,menu.getCarried().isEmpty(),rows,read(p.balance(),rows),read(p.production(),rows),read(p.capacity(),rows),read(p.prestigeProgress(),rows),read(p.prestigeGoal(),rows),quotes);
        if(p.usesCapturedLayout()){result=CapturedChocolateFactoryParser.observation(p,result,observedScopes.get(p.scope())==session);if((title.equals(ChocolateConsequentialParser.PREVIEW)||title.equals("Confirm Purchase")||java.util.Arrays.stream(ChocolateSelectedTransactions.Action.values()).anyMatch(a->ChocolateSelectedTransactions.title(a).equals(title)))&&observedScopes.get(p.scope())==session)result=new ChocolateFactoryObservation(result.scope(),result.session(),result.menu(),result.screen(),result.revision(),result.observedMillis(),title,false,result.cursorEmpty(),result.rows(),result.balance(),result.production(),result.capacity(),result.prestigeProgress(),result.prestigeGoal(),result.quotes(),result.targets(),true);}
        ChocolateObservationBindingRetention.put(bindings,bindingOrder,result,new Binding(player,world,connection,screen,menu,menu.containerId,menu.getStateId(),Map.copyOf(full)),64);return result;
    }
    public String selectedSourceTrace(){thread();return selectedSourceTrace;}
    @Override public void closeSelectedReceiptSource(){thread();if(selectedSource!=null){selectedSource.close();selectedSource=null;}selectedBefore=null;selectedBinding=null;selectedSourceProfile=null;}
    @Override public ChocolateFactoryRuntime.Delivery selectedClick(ChocolateFactoryObservation expected,int slot,int button,ChocolateSelectedTransactionRuntime.Binding binding,java.util.function.BooleanSupplier authority){return selectedClick(expected,slot,button,binding,authority,null);}
    @Override public ChocolateFactoryRuntime.Delivery selectedClick(ChocolateFactoryObservation expected,int slot,int button,ChocolateSelectedTransactionRuntime.Binding binding,java.util.function.BooleanSupplier authority,ChocolateSelectedTransactionRuntime.DispatchFence fence){
        thread();if(selectedSource!=null){var prior=selectedSource.inspect();var priorFull=prior.fullContent().orElse(null);long current=System.currentTimeMillis();
            if(!expected.title().equals("Confirm Purchase")||prior.stage()!=McVisitorHistoryPacketObserver.OwnedInputStage.NATIVE_FULL_CONTENT||priorFull==null||!priorFull.cursorEmpty()||priorFull.nativeMenu()!=expected.menu()||priorFull.revision()!=expected.revision()||selectedSourceProfile==null||current<priorFull.observedAt()||current-priorFull.observedAt()>selectedSourceProfile.freshnessMillis()){
                selectedSourceTrace="INVALID prior source retained; explicit Hitman confirmation refused: "+prior.reason();return ChocolateFactoryRuntime.Delivery.UNKNOWN;
            }
            closeSelectedReceiptSource();
        }
        var producer=McVisitorHistoryPacketObserver.existing(client);if(producer.isEmpty())return ChocolateFactoryRuntime.Delivery.DENIED;
        String reply=expected.title().equals("Rabbit Hitman")?"Confirm Purchase":expected.title().equals("Confirm Purchase")?"Rabbit Hitman":null;
        var source=producer.get().beginOwnedInput(expected.menu(),expected.revision(),expected.title(),slot,button,ContainerInput.PICKUP,reply);
        if(source.isEmpty())return ChocolateFactoryRuntime.Delivery.DENIED;var issuedSource=source.get();selectedSource=issuedSource;selectedBefore=expected;selectedBinding=binding;
        var delivered=fence==null?click(expected,slot,button,authority):click(expected,slot,button,authority,null,null,new SelectedEffectFence(fence,issuedSource,expected,binding));if(delivered!=ChocolateFactoryRuntime.Delivery.SENT){closeSelectedReceiptSource();return delivered;}
        issuedSource.sealAfterNativeCall();var proof=issuedSource.inspect();selectedSourceTrace=proof.stage()+" request="+proof.sourceRequestSequence()+" full="+proof.sourceReplySequence()+" "+proof.reason();
        if(selectedSource!=issuedSource||proof.stage()==McVisitorHistoryPacketObserver.OwnedInputStage.INVALID){issuedSource.close();if(selectedSource==issuedSource)closeSelectedReceiptSource();return ChocolateFactoryRuntime.Delivery.UNKNOWN;}return delivered;
    }
    /** Private native source identity leaf; this does not inspect or rebase the owned source. */
    private final class SelectedEffectFence {
        private final ChocolateSelectedTransactionRuntime.DispatchFence runtimeFence;private final McVisitorHistoryPacketObserver.OwnedMenuInput input;
        private final ChocolateFactoryObservation expected;private final ChocolateSelectedTransactionRuntime.Binding binding;
        private SelectedEffectFence(ChocolateSelectedTransactionRuntime.DispatchFence runtimeFence,McVisitorHistoryPacketObserver.OwnedMenuInput input,ChocolateFactoryObservation expected,ChocolateSelectedTransactionRuntime.Binding binding){this.runtimeFence=runtimeFence;this.input=input;this.expected=expected;this.binding=binding;}
        private boolean sourceLifetimeCurrent(){return selectedSource==input&&selectedBefore==expected&&selectedBinding==binding&&input.dispatchLifetimeCurrent();}
        private boolean current(ChocolateFactoryObservation observation,int slot,int button){return observation==expected&&runtimeFence.current(observation,slot,button,binding);}
    }
    /** No synthetic old observation is returned during prediction. Full state must be a current real native content capsule. */
    @Override public ChocolateFactoryObservation readSelectedReceipt(ChocolateFactoryProfile p,long now){
        thread();if(selectedSource==null)throw new IllegalStateException("Exact owned native request observation missing");var proof=selectedSource.inspect();selectedSourceProfile=p;selectedSourceTrace=proof.stage()+" request="+proof.sourceRequestSequence()+" full="+proof.sourceReplySequence()+" "+proof.reason();
        if(proof.stage()==McVisitorHistoryPacketObserver.OwnedInputStage.INVALID)throw new IllegalStateException(proof.reason());
        // This privately issued stage attests the normal close of the exact selected request only.
        // It grants no observation, receipt, resource debit or confirmation; original deadline remains.
        if(proof.stage()==McVisitorHistoryPacketObserver.OwnedInputStage.OWNED_REPLY_CLOSED_PENDING)return null;
        var observed=read(p,now);if(observed==null||!observed.contextBound()||!observed.scope().equals(p.scope())||observed.session()!=selectedBefore.session()||!selectedInventoryConserved(observed))throw new IllegalStateException("Selected native context/scope/hotbar changed");
        if(proof.stage()==McVisitorHistoryPacketObserver.OwnedInputStage.OWNED_PENDING)return null;
        var full=proof.fullContent().orElseThrow();if(full.nativeMenu()!=observed.menu()||full.revision()!=observed.revision()||!full.cursorEmpty()||!observed.cursorEmpty()||now<full.observedAt()||now-full.observedAt()>p.freshnessMillis())throw new IllegalStateException("Authoritative full native menu/revision/cursor/freshness mismatch");
        return observed;
    }
    private boolean selectedInventoryConserved(ChocolateFactoryObservation observed){
        boolean shop=selectedBefore.title().equals("Chocolate Shop");long beforeCount=0,afterCount=observed.rows().values().stream().filter(r->r.playerInventory()&&r.slot()>=1000).count();
        for(var row:selectedBefore.rows().values())if(row.playerInventory()&&row.slot()>=1000){beforeCount++;var current=observed.rows().get(row.slot());
            if(shop&&selectedBinding!=null&&row.slot()==selectedBinding.outputSlot()){if(!Objects.equals(row,current)&&(current==null||current.count()!=row.count()+1||!Objects.equals(current.fullIdentity(),selectedBinding.outputIdentity())))return false;}
            else if(!Objects.equals(row,current))return false;
        }return beforeCount==afterCount;
    }
    /** Deliberate existing-item exemplar. Only non-hotbar stack growth by one is bound, never a guessed output. */
    public Object transactionOutputIdentity(int index){thread();if(client.player==null||client.level==null||index<9||index>35)throw new IllegalArgumentException("Choose existing non-hotbar inventory slot 9..35");var original=client.player.getInventory().getItem(index);if(original.isEmpty()||original.getCount()>=original.getMaxStackSize())throw new IllegalArgumentException("Existing stack with room for exactly one item required");var expected=original.copy();expected.grow(1);return NativeStackIdentity.capture(expected,client.level.registryAccess());}
    private static Optional<BigDecimal> rateDelta(ChocolateFactoryProfile.Upgrade u,ChocolateFactoryObservation.Row row,int level,BigDecimal cost,Optional<BigDecimal> production,long now){
        var e=u.rateEvidence();if(e==null||e.price()==null||e.baseline()==null||e.fullIdentity()==null||now<e.capturedMillis()||now-e.capturedMillis()>300_000||e.level()!=level||e.price().compareTo(cost)!=0||production.isEmpty()||e.baseline().compareTo(production.get())!=0||!e.fullIdentity().equals(row.fullIdentity().toString()))return Optional.empty();
        return Optional.ofNullable(u.observedProductionDelta());
    }
    private static Optional<BigDecimal> read(ChocolateFactoryProfile.Field f,Map<Integer,ChocolateFactoryObservation.Row> rows){return f==null?Optional.empty():f.read(rows);}
    @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation expected,int slot){return click(expected,slot,0);}
    @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation expected,int slot,ChocolateFactoryRuntime.DispatchFence fence){return click(expected,slot,0,()->fence.current(expected,slot),fence,null,null);}
    @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation expected,int slot,int button){return click(expected,slot,button,()->true);}
    @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation expected,int slot,int button,ChocolateConsequentialRuntime.DispatchFence fence){return click(expected,slot,button,()->fence.current(expected,slot,button),null,fence,null);}
    /** Authority is evaluated before native identity validation; callbacks must pass a second final fence. */
    @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation expected,int slot,int button,java.util.function.BooleanSupplier authority){return click(expected,slot,button,authority,null,null,null);}
    private ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation expected,int slot,int button,java.util.function.BooleanSupplier authority,ChocolateFactoryRuntime.DispatchFence queueFence,ChocolateConsequentialRuntime.DispatchFence consequentialFence,SelectedEffectFence selectedFence){
        if(!authority.getAsBoolean())return ChocolateFactoryRuntime.Delivery.DENIED;
        if(button!=0&&button!=1)return ChocolateFactoryRuntime.Delivery.DENIED;
        thread();var b=bindings.get(expected);if(b==null||client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null||client.getConnection()==null||client.player!=b.player()||client.level!=b.world()||client.getConnection()!=b.connection()||client.gui.screen()!=b.screen()||client.player.containerMenu!=b.menu())return ChocolateFactoryRuntime.Delivery.DENIED;
        var menu=client.player.containerMenu;if(menu.getStateId()!=b.revision()||menu.containerId!=b.id()||!menu.getCarried().isEmpty()||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=menu||!MenuSnapshot.clean(screen.getTitle().getString()).equals(expected.title())||menu.slots.size()!=b.slots().size())return ChocolateFactoryRuntime.Delivery.DENIED;
        var row=expected.rows().get(slot);if(row==null||row.playerInventory()||row.count()<1||row.itemId().equals("minecraft:barrier")||row.itemId().equals("minecraft:bedrock"))return ChocolateFactoryRuntime.Delivery.DENIED;
        for(var s:menu.slots)if(!NativeStackIdentity.matches(b.slots().get(s.index),s.getItem(),client.level.registryAccess()))return ChocolateFactoryRuntime.Delivery.DENIED;
        int extra=1000;for(var equipment:net.minecraft.world.entity.EquipmentSlot.values()){
            var protectedRow=expected.rows().get(extra++);if(protectedRow==null||!(protectedRow.fullIdentity() instanceof NativeStackIdentity.Snapshot identity)||!NativeStackIdentity.matches(identity,client.player.getItemBySlot(equipment),client.level.registryAccess()))return ChocolateFactoryRuntime.Delivery.DENIED;
        }
        if(!authority.getAsBoolean())return ChocolateFactoryRuntime.Delivery.DENIED;
        var prerequisites=ChocolateNativeClickPrerequisites.capture(client,(net.minecraft.client.player.LocalPlayer)b.player(),menu,screen,b.id(),b.revision(),b.slots().size());
        if(prerequisites==null)return ChocolateFactoryRuntime.Delivery.DENIED;
        // Recheck after caller callbacks, including config/persistence stop, screen and inventory mutations.
        if(client.player!=b.player()||client.level!=b.world()||client.getConnection()!=b.connection()||client.gui.screen()!=b.screen()||client.player.containerMenu!=b.menu()||menu.getStateId()!=b.revision()||!menu.getCarried().isEmpty()||!MenuSnapshot.clean(screen.getTitle().getString()).equals(expected.title())||menu.slots.size()!=b.slots().size())return ChocolateFactoryRuntime.Delivery.DENIED;
        for(var s:menu.slots)if(!NativeStackIdentity.matches(b.slots().get(s.index),s.getItem(),client.level.registryAccess()))return ChocolateFactoryRuntime.Delivery.DENIED;
        extra=1000;for(var equipment:net.minecraft.world.entity.EquipmentSlot.values())if(!(expected.rows().get(extra++).fullIdentity() instanceof NativeStackIdentity.Snapshot identity)||!NativeStackIdentity.matches(identity,client.player.getItemBySlot(equipment),client.level.registryAccess()))return ChocolateFactoryRuntime.Delivery.DENIED;
        // Native identity codecs above can invoke callbacks. This final queue fence reads only the
        // original runtime's concrete state/reference fields and cannot reopen the identity interval.
        if(queueFence!=null&&!queueFence.current(expected,slot))return ChocolateFactoryRuntime.Delivery.DENIED;
        if(consequentialFence!=null&&!consequentialFence.current(expected,slot,button))return ChocolateFactoryRuntime.Delivery.DENIED;
        if(selectedFence!=null){
            if(!selectedFence.sourceLifetimeCurrent())return ChocolateFactoryRuntime.Delivery.UNKNOWN;
            if(!selectedFence.current(expected,slot,button))return ChocolateFactoryRuntime.Delivery.DENIED;
        }
        if(!prerequisites.current())return ChocolateFactoryRuntime.Delivery.DENIED;
        // Consume the observation before dispatch; exceptions after dispatch are never retried.
        bindings.remove(expected);ChocolateObservationBindingRetention.forget(bindingOrder,expected);try{client.gameMode.handleContainerInput(menu.containerId,slot,button,ContainerInput.PICKUP,client.player);return ChocolateFactoryRuntime.Delivery.SENT;}catch(RuntimeException unknown){return ChocolateFactoryRuntime.Delivery.UNKNOWN;}
    }
}
