package dev.veltraph.client.combat.grind;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.combat.slayer.*;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.service.mc.McInventoryService;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Actual registered, opaque source-capable Slayer-parent finishing lease. Modern exact native IDs are an explicit local update.
 * Opaque native quest evidence + body/plate ownership do not themselves grant slots or attacks. */
public final class McSlayerFinishingSwap {
    public enum Selection { PRIMARY, FINISHING, REVOKED }
    private static final List<McSlayerFinishingSwap> CLEANUPS=new ArrayList<>();
    private static AttackWindow attackWindow;private static boolean tickRegistered;
    private final Minecraft client;private final SlayerModule parent;private final SlayerModule.FinishingParentBinding binding;private final MeleeGrindModule child;
    private final ConfigService config;private final ModuleManager registry;private final McInventoryService inventory;
    private final McDaedalusTargetHealth health;
    private Lease lease;private Object pendingSelection;private boolean retired,selectedOnce,slotTouched,cleanupPending,restoring;
    private ModuleManager.OwnDisableReceipt disableReceipt;private ModuleManager.LifecycleSnapshot stopped;
    private long cleanupBorn,cleanupLast,cleanupDeadline;private int selections,restores;private long attacks;
    private record Setting(Object value,long epoch){}
    private record Admission(ArmorStand ownerPlate,String ownerName,McDaedalusTargetHealth.Observed hp,String selectedName){}
    private record Frame(Object player,Object world,Object connection,Object gameMode,ModuleManager.LifecycleSnapshot lifecycle,
            CombatFinishingLifecycle pair,McSlayerQuestTierObserver.NativeQuestTier tier,Map<ConfigKey<?>,Setting> settings,
            LivingEntity target,Admission admission,List<NativeStackIdentity.Snapshot> inventory,List<NativeStackIdentity.Snapshot> equipment,
            ItemStack tool,NativeStackIdentity.Snapshot normalizedTool,int primary,int finishing,int finalReturn,int percentage){}
    private static final class Lease {
        final Frame frame;final McInventoryService.ScopedSlotReceipt selection;long submitted;
        Lease(Frame frame,McInventoryService.ScopedSlotReceipt selection){this.frame=frame;this.selection=selection;}
    }
    public static final class AttackWindow {
        private final McSlayerFinishingSwap issuer;private final Lease lease;private final Object boundary;
        private final LivingEntity target;private final Admission admission;private boolean submitted,failed;
        private AttackWindow(McSlayerFinishingSwap issuer,Lease lease,Object boundary,LivingEntity target,Admission admission){this.issuer=issuer;this.lease=lease;this.boundary=boundary;this.target=target;this.admission=admission;}
    }
    public static final class SendInvocation {
        private final AttackWindow window;private final Connection transport;private final ServerboundAttackPacket packet;
        private SendInvocation(AttackWindow window,Connection transport,ServerboundAttackPacket packet){this.window=window;this.transport=transport;this.packet=packet;}
    }
    public McSlayerFinishingSwap(Minecraft client,SlayerModule.FinishingParentBinding binding,MeleeGrindModule child,ConfigService config,ModuleManager registry){
        this.client=client;this.binding=Objects.requireNonNull(binding);this.parent=binding.parent();this.child=child;this.config=config;this.registry=registry;
        inventory=new McInventoryService(client);health=new McDaedalusTargetHealth(client);
        if(!tickRegistered){tickRegistered=true;ClientTickEvents.END_CLIENT_TICK.register(McSlayerFinishingSwap::drain);}
    }
    public boolean selectedOnce(){return selectedOnce;}public int selections(){return selections;}public int restores(){return restores;}public long submittedAttacks(){return attacks;}
    public boolean borrowed(){return lease!=null;}
    public boolean requiresOwnedCleanup(){return pendingSelection!=null||slotTouched||retired;}
    public boolean sameTarget(LivingEntity actual){return lease==null||lease.frame.target==actual;}
    public int heldSlot(int primary){if(lease==null)return retired?-1:primary;return active(lease.frame,lease.frame.finishing,null)?lease.frame.finishing:-1;}
    /** Prime the source wrapper's first HP observation before low-HP selection; no vanilla/max-HP substitution. */
    public void observe(LivingEntity target){if(!retired&&enabled()&&parent.finishingBindingCurrent(child,null)
            &&child.selectedNamedCombatTarget().filter(selected->selected.body()==target&&binding.targetLeaf(child,target,selected.observedName())).isPresent()
            &&SlayerEntityOwnership.own(client,target,SlayerEntityOwnership.visible(client)).isPresent())health.observe(target);}
    private boolean enabled(){return Boolean.TRUE.equals(config.get(DaedalusSwapConfig.USE_DAEDALUS_SWAP));}
    public Selection selectForActualBoundary(LivingEntity target,Object boundary){
        var entry=dev.veltraph.client.navigation.NativeCarryPublicationWitness.entry(pendingSelection!=null,restoring,retired,lease==null,!slotTouched,this::enabled);
        if(entry==dev.veltraph.client.navigation.NativeCarryPublicationWitness.Entry.REVOKED)return Selection.REVOKED;
        if(entry==dev.veltraph.client.navigation.NativeCarryPublicationWitness.Entry.PRIMARY)return Selection.PRIMARY;
        if(boundary==null||!child.finishingBoundaryCurrent(boundary,target))return Selection.REVOKED;
        if(lease!=null)return active(lease.frame,lease.frame.finishing,boundary)?Selection.FINISHING:Selection.REVOKED;
        if(!enabled())return Selection.PRIMARY;
        var frame=capture(target,boundary);if(frame==null)return retired?Selection.REVOKED:Selection.PRIMARY;
        if(frame.finishing==frame.primary)return Selection.PRIMARY;
        var pending=new Object();pendingSelection=pending;
        try {
            McInventoryService.ScopedSlotReceipt receipt;
            try(var synchronization=inventory.nativeCarrySynchronization(()->pendingSelection==pending&&lease==null&&activeLeaf(frame,frame.finishing,boundary,null))){
                receipt=inventory.selectSlotScoped(frame.primary,frame.finishing,
                        ()->pendingSelection==pending&&active(frame,frame.primary,boundary),
                        ()->pendingSelection==pending&&active(frame,frame.finishing,boundary));
            }
            if(pendingSelection!=pending)return Selection.REVOKED;
            if(receipt.localSelected()||receipt.packetSubmitted())slotTouched=true;
            if(!receipt.confirmed()||!receipt.localSelected()||!receipt.packetSubmitted()||!active(frame,frame.finishing,boundary)){
                retired=true;return Selection.REVOKED;
            }
            lease=new Lease(frame,receipt);selectedOnce=true;selections++;return Selection.FINISHING;
        }catch(Throwable failure){
            // A partial old publication stays owned even when an Error prevents any receipt.
            if(pendingSelection==pending&&lease==null)retired=true;
            throw failure;
        }finally{if(pendingSelection==pending)pendingSelection=null;}
    }

    public boolean restoreActive(){
        var borrowed=lease;if(borrowed==null)return !retired;if(restoring)return false;
        restoring=true;
        try {
            var frame=borrowed.frame;
            McInventoryService.ScopedSlotReceipt result;
            try(var synchronization=inventory.nativeCarrySynchronization(()->lease==borrowed&&activeLeaf(frame,frame.primary,null,null)&&lease==borrowed)){
                result=inventory.selectSlotScoped(frame.finishing,frame.primary,
                        ()->lease==borrowed&&active(frame,frame.finishing,null)&&lease==borrowed,
                        ()->lease==borrowed&&active(frame,frame.primary,null)&&lease==borrowed);
            }
            if(lease!=borrowed)return false; // A captured old return cannot erase or retire a newer loan.
            if(!result.confirmed()||!result.localSelected()||!result.packetSubmitted()||!active(frame,frame.primary,null)||lease!=borrowed){retired=true;return false;}
            lease=null;slotTouched=false;restores++;return true;
        } finally { restoring=false; }
    }
    /** During onDisable this only retains the real old slot receipt; it publishes no key/slot/packet. */
    public void childStopping(){if(lease==null||cleanupPending)return;cleanupPending=true;cleanupBorn=cleanupLast=System.nanoTime();
        try{cleanupDeadline=Math.addExact(cleanupBorn,Math.multiplyExact(((Number)lease.frame.settings.get(SlayerConfig.SLAYER_MENU_TIMEOUT_MS).value).longValue(),1_000_000L));}
        catch(RuntimeException invalid){cleanupDeadline=cleanupBorn;}
        if(!CLEANUPS.contains(this))CLEANUPS.add(this);}
    /** Read-only CoreB hook. Tentative post-hook receipts cannot perform restoration here. */
    public void observeParentStopped(ModuleManager.OwnDisableReceipt actual){if(!cleanupPending||lease==null||actual==null||actual.parent()!=parent||!actual.returnedCurrent())return;
        disableReceipt=actual;stopped=actual.stoppedSnapshot();}
    private static void drain(Minecraft client){for(var pending:List.copyOf(CLEANUPS)){
        if(pending.client!=client)continue;try{if(pending.tryCleanup())CLEANUPS.remove(pending);}catch(Throwable unavailable){pending.retired=true;CLEANUPS.remove(pending);}
    }}
    private boolean tryCleanup(){
        if(!cleanupPending||lease==null)return true;
        long now=System.nanoTime();if(now<cleanupLast||now>cleanupDeadline){retired=true;cleanupPending=false;lease=null;return true;}
        cleanupLast=now;
        if(disableReceipt==null||!disableReceipt.current())return false;
        var frame=lease.frame;
        if(!cleanup(frame,frame.finishing)){retired=true;cleanupPending=false;lease=null;return true;}
        McInventoryService.ScopedSlotReceipt result;
        try(var synchronization=inventory.nativeCarrySynchronization(()->cleanupLeaf(frame,frame.finalReturn))){
            result=inventory.selectSlotScoped(frame.finishing,frame.finalReturn,
                    ()->cleanup(frame,frame.finishing),()->cleanup(frame,frame.finalReturn));
        }
        boolean completed=result.confirmed()&&result.localSelected()&&result.packetSubmitted()&&cleanup(frame,frame.finalReturn);
        if(completed){restores++;slotTouched=false;}else retired=true;
        cleanupPending=false;lease=null;return true;
    }
    private Map<ConfigKey<?>,Setting> settings(){
        if(!(config instanceof MapConfigService||config instanceof JsonConfigService))return null;
        var out=new LinkedHashMap<ConfigKey<?>,Setting>();
        for(var key:PersistableKeys.all()){
            var epoch=config.revision(key);var value=config.get(key);
            if(epoch.isEmpty()||epoch.getAsLong()<0||!immutableSetting(value))return null;
            out.put(key,new Setting(value,epoch.getAsLong()));}
        return Map.copyOf(out);
    }
    private static boolean immutableSetting(Object value){return value instanceof String||value instanceof Boolean||value instanceof Integer
            ||value instanceof Long||value instanceof Float||value instanceof Double||value instanceof Enum<?>;}
    private Frame capture(LivingEntity target,Object boundary){
        var lifecycle=registry.lifecycleSnapshot();var pair=CombatFinishingLifecycle.capture(parent,child);
        var tier=parent.observedQuestTier().orElse(null);var p=client.player;var world=client.level;var connection=client.getConnection();
        var saved=settings();if(!lifecycle.known()||pair==null||tier==null||p==null||world==null||connection==null||saved==null
                ||!parent.finishingBindingCurrent(child,tier)||!child.finishingBoundaryCurrent(boundary,target)
                ||child.selectedNamedCombatTarget().filter(selected->selected.body()==target&&binding.targetLeaf(child,target,selected.observedName())).isEmpty()
                ||SlayerEntityOwnership.own(client,target,SlayerEntityOwnership.visible(client)).isEmpty())return null;
        var named=child.selectedNamedCombatTarget().filter(selected->selected.body()==target).orElse(null);
        var visible=SlayerEntityOwnership.visible(client);var ownership=SlayerEntityOwnership.own(client,target,visible).orElse(null);
        if(named==null||ownership==null||!binding.targetLeaf(child,target,named.observedName()))return null;
        var ownerPlate=visible.stream().filter(entity->entity instanceof ArmorStand&&entity.getUUID().equals(ownership.plate())).map(entity->(ArmorStand)entity).findFirst().orElse(null);
        if(ownerPlate==null||!ownerPlate.hasCustomName())return null;
        var hp=health.observe(target).orElse(null);int pct=config.get(DaedalusSwapConfig.DAEDALUS_SWAP_HEALTH_THRESHOLD_PERCENTAGE);
        if(hp==null||hp.current()<=0||hp.reference()<=0||pct<0||pct>100||pct!=100&&hp.current()>(int)(hp.reference()*(pct/100.0f)))return null;
        int primary=child.committedWeaponSlot(),selected=p.getInventory().getSelectedSlot(),chosen=-1;
        if(primary<0||primary>8||selected!=primary||client.gui.screen()!=null||p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty())return null;
        for(int slot=0;slot<9;slot++)if(modernTool(p.getInventory().getItem(slot))){chosen=slot;break;}
        if(chosen<0)chosen=primary; // source item missing retains exact primary fallback
        int finalReturn=parent.finishingOriginalReturnSlot(child);if(finalReturn<0||finalReturn>8)return null;
        var all=new ArrayList<NativeStackIdentity.Snapshot>();for(int slot=0;slot<36;slot++)all.add(NativeStackIdentity.capture(p.getInventory().getItem(slot),world.registryAccess()));
        var gear=new ArrayList<NativeStackIdentity.Snapshot>();for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))gear.add(NativeStackIdentity.capture(p.getItemBySlot(slot),world.registryAccess()));
        var tool=p.getInventory().getItem(chosen).copy();
        var frame=new Frame(p,world,connection,client.gameMode,lifecycle,pair,tier,saved,target,new Admission(ownerPlate,ownerPlate.getCustomName().getString(),hp,named.observedName()),List.copyOf(all),List.copyOf(gear),tool,
                NativeStackIdentity.capture(withoutDamage(tool),world.registryAccess()),primary,chosen,finalReturn,pct);
        if(!active(frame,primary,boundary)){retired=true;return null;}
        return frame;
    }
    private boolean fields(Frame frame,int held){
        var p=client.player;if(p==null||client.level==null||p!=frame.player||client.level!=frame.world||client.getConnection()!=frame.connection||client.gameMode!=frame.gameMode
                ||!p.isAlive()||client.gui.screen()!=null||p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty()||p.getInventory().getSelectedSlot()!=held
                ||VeltraphBootstrap.config()!=config||VeltraphBootstrap.moduleManager()!=registry||!frame.settings.equals(settings()))return false;
        long issued=lease!=null&&lease.frame==frame?lease.submitted:0;
        // Only an already normally returned actual packet in this private attack window may
        // budget the in-flight hit's durability. An API call or public counter cannot do so.
        var pending=attackWindow;
        if(pending!=null&&pending.issuer==this&&pending.lease==lease&&pending.submitted&&!pending.failed&&issued<Long.MAX_VALUE)issued++;
        for(int slot=0;slot<36;slot++){
            var actual=p.getInventory().getItem(slot);
            if(slot==frame.finishing){if(!FinishingToolWear.allowed(frame.tool.getCount(),actual.getCount(),frame.tool.getDamageValue(),actual.getDamageValue(),issued,
                    frame.normalizedTool.matches(withoutDamage(actual),client.level.registryAccess())))return false;}
            else if(!frame.inventory.get(slot).matches(actual,client.level.registryAccess()))return false;
        }
        int i=0;for(var equipment:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))
            if(!frame.equipment.get(i++).matches(p.getItemBySlot(equipment),client.level.registryAccess()))return false;
        return true;
    }
    /** All external role/filter callbacks precede the final trusted native/private leaf. */
    private boolean active(Frame frame,int held,Object boundary){
        if(!client.isSameThread()||retired||frame==null||registry.inputOwner().orElse(null)!=parent
                ||!parent.finishingBindingCurrent(child,frame.tier)||!frame.tier.current())return false;
        if(boundary!=null&&!child.finishingBoundaryCurrent(boundary,frame.target))return false;
        return activeLeaf(frame,held,boundary,null);
    }
    private boolean admissionLeaf(Frame frame,Admission pinned,boolean exactHp){
        if(pinned==null||frame.target==null||!binding.targetLeaf(child,frame.target,pinned.selectedName)||client.level==null||client.player==null)return false;
        var owner=pinned.ownerPlate;
        if(owner==null||!owner.isAlive()||!owner.hasCustomName()||client.level.getEntity(owner.getId())!=owner
                ||!client.level.isLoaded(owner.blockPosition())||!pinned.ownerName.equals(owner.getCustomName().getString()))return false;
        var visible=SlayerEntityOwnership.visible(client);var actual=SlayerEntityOwnership.own(client,frame.target,visible).orElse(null);
        if(actual==null||!actual.plate().equals(owner.getUUID())||!actual.body().equals(frame.target.getUUID())
                ||SlayerEntityOwnership.name(client,frame.target,visible,pinned.selectedName::equals).isEmpty())return false;
        return health.leaf(pinned.hp,exactHp).isPresent();
    }
    /** No public owner/role/filter/mechanic callback runs in or after this final effect leaf. */
    private boolean activeLeaf(Frame frame,int held,Object boundary,Admission exactAdmission){
        if(!client.isSameThread()||retired||frame==null||!parent.finishingBindingLeafCurrent(child,frame.tier)
                ||!frame.tier.leafCurrent()||!fields(frame,held)||!frame.pair.current()||!registry.lifecycleCurrent(frame.lifecycle))return false;
        boolean initial=lease==null;
        if((boundary!=null||initial||exactAdmission!=null)&&!admissionLeaf(frame,exactAdmission==null?frame.admission:exactAdmission,exactAdmission!=null||initial))return false;
        return boundary==null||child.finishingBoundaryNativeLeafCurrent(boundary,frame.target);
    }
    private boolean cleanup(Frame frame,int held){
        if(!client.isSameThread()||frame==null||registry.inputOwner().isPresent()||!neutral()||disableReceipt==null||stopped==null)return false;
        return cleanupLeaf(frame,held);
    }
    /** Callback-free final cleanup source; public input-owner observation precedes this leaf. */
    private boolean cleanupTimeCurrent(){
        long now=System.nanoTime();
        if(!dev.veltraph.client.navigation.NativeCarryPublicationWitness.withinOriginalWindow(now,cleanupBorn,cleanupLast,cleanupDeadline))return false;
        cleanupLast=now;return true;
    }
    private boolean cleanupLeaf(Frame frame,int held){
        if(!client.isSameThread()||frame==null||!neutral()||disableReceipt==null||stopped==null||!cleanupTimeCurrent())return false;
        return fields(frame,held)&&frame.pair.childStoppedNormally()
                &&registry.ownDisableFromMatches(disableReceipt,frame.lifecycle,stopped,parent)&&registry.lifecycleCurrent(stopped)&&cleanupTimeCurrent();
    }
    private boolean neutral(){var o=client.options;return o!=null&&!o.keyUp.isDown()&&!o.keyDown.isDown()&&!o.keyLeft.isDown()&&!o.keyRight.isDown()
            &&!o.keyJump.isDown()&&!o.keyShift.isDown()&&!o.keySprint.isDown()&&!o.keyAttack.isDown()&&!o.keyUse.isDown();}
    public AttackWindow beginActualAttack(LivingEntity target,Object boundary){
        if(lease==null||attackWindow!=null||!active(lease.frame,lease.frame.finishing,boundary))return null;
        var current=health.leaf(lease.frame.admission.hp,false).orElse(null);if(current==null)return null;
        var admission=new Admission(lease.frame.admission.ownerPlate,lease.frame.admission.ownerName,current,lease.frame.admission.selectedName);
        if(!activeLeaf(lease.frame,lease.frame.finishing,boundary,admission))return null;
        attackWindow=new AttackWindow(this,lease,boundary,target,admission);return attackWindow;
    }
    /** Called after the original Rot send returns normally, immediately before gameMode.attack. */
    public boolean beforeActualAttack(AttackWindow actual){
        if(actual==null||actual.issuer!=this||attackWindow!=actual||lease!=actual.lease||actual.submitted||actual.failed)return false;
        if(!active(actual.lease.frame,actual.lease.frame.finishing,actual.boundary))return false;
        return attackWindow==actual&&lease==actual.lease&&!actual.submitted&&!actual.failed
                &&activeLeaf(actual.lease.frame,actual.lease.frame.finishing,actual.boundary,actual.admission);
    }
    public boolean finishActualAttack(AttackWindow actual,boolean originalReturned){
        if(actual==null||actual.issuer!=this||attackWindow!=actual)return false;
        try {
            if(lease!=actual.lease)return false; // Close only the captured old window; do not retire a newer lease.
            if(!originalReturned||!actual.submitted||actual.failed||!active(actual.lease.frame,actual.lease.frame.finishing,actual.boundary)){
                retired=true;return false;}
            if(actual.lease.submitted==Long.MAX_VALUE){retired=true;return false;}
            actual.lease.submitted++;attacks++;return true;
        } finally { if(attackWindow==actual)attackWindow=null; }
    }
    public static SendInvocation beginSend(Connection transport,Packet<?> packet){
        var window=attackWindow;if(window==null||!(packet instanceof ServerboundAttackPacket attack))return null;
        var owner=window.issuer;
        if(!owner.client.isSameThread()||transport!=owner.client.getConnection().getConnection()||attack.entityId()!=window.target.getId())return null;
        return new SendInvocation(window,transport,attack);
    }
    public static void finishSend(SendInvocation captured,boolean originalReturned){
        if(captured==null)return;var window=captured.window;var owner=window.issuer;
        if(attackWindow!=window||owner.lease!=window.lease)return;
        if(!originalReturned||window.submitted||captured.transport!=owner.client.getConnection().getConnection()
                ||!owner.active(window.lease.frame,window.lease.frame.finishing,window.boundary)){window.failed=true;return;}
        window.submitted=true;
    }
    private static ItemStack withoutDamage(ItemStack item){var copy=item.copy();if(copy.isDamageableItem())copy.setDamageValue(0);return copy;}
    private static boolean modernTool(ItemStack item){
        if(item.isEmpty()||!item.is(Items.GOLDEN_SWORD))return false;var data=item.get(DataComponents.CUSTOM_DATA);if(data==null)return false;
        var tag=data.copyTag();String top=tag.getString("id").orElse(""),nested=tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id","");
        if(!top.isEmpty()&&!nested.isEmpty()&&!top.equals(nested))return false;
        return DaedalusNativeItemIdentity.modernFinishingTool("GOLD_SWORD","SWORD",top.isEmpty()?nested:top);
    }
}
