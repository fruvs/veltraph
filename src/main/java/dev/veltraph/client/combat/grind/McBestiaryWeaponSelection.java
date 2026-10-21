package dev.veltraph.client.combat.grind;

import dev.veltraph.client.config.CombatConfig;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.service.InventoryService;
import dev.veltraph.client.service.mc.McInventoryService;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.core.CombatFinishingLifecycle;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A pinned native tool lease for the registered Bestiary engine. Never moves inventory items. */
final class McBestiaryWeaponSelection {
    private final Minecraft client;
    private final InventoryService inventory;
    private final ConfigService config;
    private Object player, world, connection;
    private int slot = -1, originalSlot = -1;
    private NativeStackIdentity.Snapshot original, normalizedTool;
    private ItemStack tool;
    private String preferred, settings;
    private long issuedAttacks;
    private final OwnedWeaponStart start;
    private final CombatWeaponPolicy.Family family;
    private final boolean common;
    private final MeleeGrindModule.ParentWeaponDeclaration declaration;
    private final java.util.function.Supplier<CombatWeaponPolicy> parentPolicy;
    private final java.util.function.IntSupplier parentFixedSlot;
    private final java.util.function.BooleanSupplier authority;
    private boolean committed,releasing;
    private final MeleeGrindModule owner;
    private SelectionAttempt selectionAttempt;
    private NativeLoan nativeLoan;
    private static final class SelectionAttempt {
        final BestiaryWeaponStart.Frame before;final ModuleManager root;final AbstractModule parent;
        final ModuleManager.LifecycleSnapshot lifecycle;final CombatFinishingLifecycle pair;
        final Object inventory,mode;final McPestInventoryRestorationObserver.SelectionHistory history;
        final java.util.Map<dev.veltraph.client.config.ConfigKey<?>,Long> weaponEpochs;
        McInventoryService.ScopedSlotReceipt actual;private NativeSetterReturnBoundary setterReturn;
        SelectionAttempt(BestiaryWeaponStart.Frame before,ModuleManager root,AbstractModule parent,
                ModuleManager.LifecycleSnapshot lifecycle,CombatFinishingLifecycle pair,Object inventory,Object mode,
                McPestInventoryRestorationObserver.SelectionHistory history,java.util.Map<dev.veltraph.client.config.ConfigKey<?>,Long> weaponEpochs){this.weaponEpochs=weaponEpochs;this.before=before;this.root=root;this.parent=parent;this.lifecycle=lifecycle;this.pair=pair;this.inventory=inventory;this.mode=mode;this.history=history;}
    }
    /** Exists only while the canonical native adapter invokes its original after-setter fence. */
    private static final class NativeSetterReturnBoundary {
        private final SelectionAttempt source;private boolean open=true;
        private NativeSetterReturnBoundary(SelectionAttempt source){this.source=source;}
    }
    private static final class NativeLoan {
        final SelectionAttempt source;final McInventoryService.ScopedSlotReceipt actual;
        final McPestInventoryRestorationObserver.SelectionHistory history;final WeaponSelectionReturn once=new WeaponSelectionReturn();
        NativeLoan(SelectionAttempt source,McInventoryService.ScopedSlotReceipt actual){this.source=source;this.actual=actual;history=actual.nativeSelectionHistory();}
    }
    McBestiaryWeaponSelection(Minecraft client, InventoryService inventory, ConfigService config,MeleeGrindModule owner) {
        this(client,inventory,config,null,owner::isEnabled,true,null,null,null,owner);
    }
    McBestiaryWeaponSelection(Minecraft client, InventoryService inventory, ConfigService config,
            CombatWeaponPolicy.Family family, java.util.function.BooleanSupplier authority, boolean common,MeleeGrindModule owner) {
        this(client,inventory,config,family,authority,common,null,null,null,owner);
    }
    McBestiaryWeaponSelection(Minecraft client,InventoryService inventory,ConfigService config,
            MeleeGrindModule.ParentWeaponDeclaration declaration,java.util.function.Supplier<CombatWeaponPolicy> policy,
            java.util.function.IntSupplier fixedSlot,java.util.function.BooleanSupplier authority,MeleeGrindModule owner){
        this(client,inventory,config,null,authority,false,declaration,policy,fixedSlot,owner);
    }
    private McBestiaryWeaponSelection(Minecraft client,InventoryService inventory,ConfigService config,
            CombatWeaponPolicy.Family family,java.util.function.BooleanSupplier authority,boolean common,
            MeleeGrindModule.ParentWeaponDeclaration declaration,java.util.function.Supplier<CombatWeaponPolicy> policy,
            java.util.function.IntSupplier fixedSlot,MeleeGrindModule owner){
        this.owner=java.util.Objects.requireNonNull(owner);this.client=client;this.inventory=inventory;this.config=config;this.family=family;this.common=common;this.authority=authority;
        this.declaration=declaration;parentPolicy=policy;parentFixedSlot=fixedSlot;
        start=new OwnedWeaponStart(new InventoryService(){
            public int selectedSlot(){return inventory.selectedSlot();}
            public java.util.List<String> hotbarItemNames(){return inventory.hotbarItemNames();}
            public void selectSlot(int next){selectPreparedNative(next);}
        },authority);
    }
    Optional<String> prepare(String signature) {
        if(selectionAttempt!=null||releasing)return Optional.of("Bestiary native selection is already in progress.");
        if (committed) return valid(signature, true) ? Optional.empty() : Optional.of("Bestiary protected tool lease is no longer valid.");
        start.cancel();
        var p = client.player;
        if (p == null || client.level == null || client.getConnection() == null || client.gui.screen() != null
                || p.containerMenu != p.inventoryMenu || !p.containerMenu.getCarried().isEmpty())
            return Optional.of("Bestiary weapon selection requires the native inventory to be idle.");
        if(declaration!=null&&!p.getInventory().getItem(inventory.selectedSlot()).is(Items.FISHING_ROD))
            return Optional.of("Fishing passive weapon readiness requires the original native rod to remain held.");
        String preference = config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON);
        var observed = new ArrayList<BestiaryWeaponSelector.Candidate>(9);
        for (int i = 0; i < 9; i++) {
            var item = p.getInventory().getItem(i); var custom = item.get(DataComponents.CUSTOM_NAME);
            observed.add(new BestiaryWeaponSelector.Candidate(i, item.isEmpty(), kind(item), custom == null ? null : custom.getString()));
        }
        var resolved=declaration!=null?new CombatWeaponPolicy.Resolved(parentPolicy.get(),""):common?new CombatWeaponPolicy.Resolved(CombatWeaponPolicy.common(),""):
                CombatWeaponPolicy.parse(config.get(CombatConfig.COMBAT_CONTEXTUAL_WEAPON_PROFILE),family);
        if(!resolved.valid())return Optional.of(resolved.error());
        var selected=declaration==null?resolved.policy().select(observed,config.get(CombatConfig.COMBAT_WEAPON_SLOT)-1,preference):
                OwnedWeaponStart.selectParentPolicy(declaration,resolved.policy(),parentFixedSlot.getAsInt(),observed,preference);
        if(resolved.policy().mode()==CombatWeaponPolicy.Mode.FIXED_SLOT && selected.isPresent()){
            var item=p.getInventory().getItem(selected.get().slot());
            if(!(item.is(ItemTags.SWORDS)||item.is(ItemTags.AXES)||item.is(Items.FISHING_ROD)&&item.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains("whip")))
                return Optional.of("Set Combat Weapon Slot (1-9) to a sword, axe or whip.");
        }
        if (selected.isEmpty()) return Optional.of("Bestiary needs a sword, axe, pickaxe or matching preferred custom-named item in the hotbar.");
        try {
            var frame = frame(signature);
            if (frame.heldSlot() < 0 || frame.heldSlot() > 8) return Optional.of("Bestiary native held slot is unavailable.");
            start.prepare(frame, selected.get(),family==null&&declaration==null?frame.hotbar():nativeLoadout());
            return Optional.empty();
        } catch (RuntimeException failure) {
            start.cancel(); return Optional.of("Bestiary item components cannot be protected: " + failure.getMessage());
        }
    }
    /** Only onEnable calls this, after the shared guard grants the controller its activation. */
    boolean commit(String signature) {
        var prepared = start.proposal();
        if (committed || prepared == null||selectionAttempt!=null||releasing) return false;
        try {
            var current = frame(signature);
            if (!BestiaryWeaponStart.mayCommit(prepared.frame(), current)) { start.cancel(); return false; }
            var p = client.player; var registry = client.level.registryAccess();
            tool = p.getInventory().getItem(prepared.choice().slot()).copy();
            original = NativeStackIdentity.capture(p.getInventory().getItem(prepared.frame().heldSlot()), registry);
            normalizedTool = NativeStackIdentity.capture(withoutWear(tool), registry);
            player = p; world = client.level; connection = client.getConnection();
            slot = prepared.choice().slot(); originalSlot = prepared.frame().heldSlot();
            preferred = prepared.frame().preferred(); settings = signature; issuedAttacks = 0;
            // The actual private producer, real native setter/packet return and exact history alone create custody.
            var root=VeltraphBootstrap.moduleManager();var actualParent=owner.declaredWeaponParentIdentity();
            if(actualParent==null||!(inventory instanceof McInventoryService)||!client.isSameThread()
                    ||root==null||root.get(actualParent.id()).orElse(null)!=actualParent||root.inputOwner().orElse(null)!=actualParent){start.cancel();return false;}
            var lifecycle=root.lifecycleSnapshot();var pair=actualParent==owner?null:CombatFinishingLifecycle.capture(actualParent,owner);
            var history=McPestInventoryRestorationObserver.selectedSlotHistory(p.getInventory());
            if(!lifecycle.known()||history==null||actualParent!=owner&&pair==null){start.cancel();return false;}
            var epochs=weaponEpochs(actualParent);if(epochs==null){start.cancel();return false;}
            var attempt=new SelectionAttempt(current,root,actualParent,lifecycle,pair,p.getInventory(),client.gameMode,history,epochs);selectionAttempt=attempt;
            boolean selected;
            try{selected=start.commit(current,family==null&&declaration==null?current.hotbar():nativeLoadout());}
            finally{if(selectionAttempt==attempt)selectionAttempt=null;}
            var receipt=attempt.actual;
            if(!selected||receipt==null||!receipt.confirmed()||!receipt.localSelected()||!receipt.packetSubmitted()
                    ||receipt.expectedSlot()!=originalSlot||receipt.requestedSlot()!=slot||receipt.nativeSelectionHistory()==null
                    ||!receipt.nativeSelectionHistory().followsExactlyOneSetter(attempt.history)||!startFields(attempt,slot)){
                release();return false;
            }
            var adopted=new NativeLoan(attempt,receipt);nativeLoan=adopted;committed = true;
            if(!WeaponSelectionParentBoundary.afterFinalCallback(()->valid(signature,true),
                    ()->nativeLoan==adopted&&adopted.history!=null&&adopted.history.current(),attempt.parent,
                    owner::declaredWeaponParentIdentity,()->attempt.root.inputOwner().orElse(null),()->startNativeFields(attempt,slot))){
                release();return false;
            }
            return true;
        } catch (RuntimeException failure) { release(); return false; }
    }
    /** Explicit Fishing reel contract permits only the held original rod's DAMAGE to vary. */
    private ItemStack proposalItem(ItemStack item,int nativeSlot){
        return declaration==MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER
                &&nativeSlot==inventory.selectedSlot()&&item.is(Items.FISHING_ROD)?withoutWear(item):item;
    }
    private java.util.List<NativeStackIdentity.Snapshot> nativeLoadout(){
        var p=client.player;var registry=client.level.registryAccess();
        var result=new java.util.ArrayList<NativeStackIdentity.Snapshot>();
        for(int i=0;i<p.getInventory().getContainerSize();i++)result.add(NativeStackIdentity.capture(proposalItem(p.getInventory().getItem(i),i),registry));
        for(var equipment:java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND))
            result.add(NativeStackIdentity.capture(p.getItemBySlot(equipment),registry));
        return java.util.List.copyOf(result);
    }
    private BestiaryWeaponStart.Frame frame(String signature) {
        var p = client.player; var w = client.level; var connection = client.getConnection();
        boolean idle = p != null && w != null && connection != null && client.gui.screen() == null
                && p.containerMenu == p.inventoryMenu && p.containerMenu.getCarried().isEmpty();
        var hotbar = new ArrayList<NativeStackIdentity.Snapshot>();
        if (idle) for (int i = 0; i < 9; i++) hotbar.add(NativeStackIdentity.capture(proposalItem(p.getInventory().getItem(i),i), w.registryAccess()));
        return new BestiaryWeaponStart.Frame(p, w, connection, inventory.selectedSlot(), hotbar,
                signature, config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON), idle);
    }
    int slot() { return committed?slot:-1; }
    long issuedAttacks(){return committed?issuedAttacks:0;}
    boolean valid(String signature, boolean checkHeld) {
        var p = client.player;
        if (!authority.getAsBoolean() || !committed || slot < 0 || p == null || p != player || client.level == null || client.level != world
                || client.getConnection() != connection || !java.util.Objects.equals(settings, signature)
                || !java.util.Objects.equals(preferred, config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON))
                || checkHeld && inventory.selectedSlot() != slot) return false;
        var current = p.getInventory().getItem(slot);
        return BestiaryToolWear.allowed(tool.getCount(), current.getCount(), tool.getDamageValue(), current.getDamageValue(),
                issuedAttacks, normalizedTool.matches(withoutWear(current), client.level.registryAccess()))
                && (originalSlot == slot || original.matches(p.getInventory().getItem(originalSlot), client.level.registryAccess()));
    }
    void issuedAttack() { if (slot >= 0 && issuedAttacks < Integer.MAX_VALUE) issuedAttacks++; }
    /** Called only after the existing owned scheduled-arrival port validates its exact saved loadout. */
    boolean rebind(String signature) {
        var p = client.player;
        if (!authority.getAsBoolean() || p == null || client.level == null || client.getConnection() == null || slot < 0
                || inventory.selectedSlot() != slot || !java.util.Objects.equals(settings, signature)
                || !java.util.Objects.equals(preferred, config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON))) return false;
        var current = p.getInventory().getItem(slot);
        if (!BestiaryToolWear.allowed(tool.getCount(), current.getCount(), tool.getDamageValue(), current.getDamageValue(),
                issuedAttacks, normalizedTool.matches(withoutWear(current), client.level.registryAccess()))
                || originalSlot != slot && !original.matches(p.getInventory().getItem(originalSlot), client.level.registryAccess())) return false;
        player = p; world = client.level; connection = client.getConnection(); return true;
    }
    private java.util.Map<dev.veltraph.client.config.ConfigKey<?>,Long> weaponEpochs(AbstractModule actualParent){
        return WeaponSelectionPolicyInputs.capture(config,declaration==MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER,
                actualParent instanceof dev.veltraph.client.combat.slayer.SlayerModule);
    }
    private java.util.List<NativeStackIdentity.Snapshot> startHotbar(SelectionAttempt source){
        var result=new java.util.ArrayList<NativeStackIdentity.Snapshot>();var p=client.player;
        for(int i=0;i<9;i++){var item=p.getInventory().getItem(i);
            if(declaration==MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER&&i==source.before.heldSlot()&&item.is(Items.FISHING_ROD))item=withoutWear(item);
            result.add(NativeStackIdentity.capture(item,client.level.registryAccess()));}
        return java.util.List.copyOf(result);
    }
    private boolean nativeContext(SelectionAttempt source,int held){
        var p=client.player;return client.isSameThread()&&p!=null&&p==source.before.player()&&client.level==source.before.world()
                &&client.getConnection()==source.before.connection()&&p.level()==client.level&&client.gameMode==source.mode&&p.getInventory()==source.inventory
                &&client.gui.screen()==null&&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty()
                &&p.getInventory().getSelectedSlot()==held&&VeltraphBootstrap.moduleManager()==source.root&&VeltraphBootstrap.config()==config
                &&source.root.get(source.parent.id()).orElse(null)==source.parent&&java.util.Objects.equals(preferred,config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON))&&source.weaponEpochs.equals(weaponEpochs(source.parent));
    }
    private boolean startNativeFields(SelectionAttempt source,int held){
        return nativeContext(source,held)&&source.root.lifecycleCurrent(source.lifecycle)&&(source.pair==null||source.pair.current())
                &&source.before.hotbar().equals(startHotbar(source))&&nativeContext(source,held)&&source.root.lifecycleCurrent(source.lifecycle)
                &&(source.pair==null||source.pair.current());
    }
    private boolean startFields(SelectionAttempt source,int held){
        return WeaponSelectionParentBoundary.active(source.parent,owner::declaredWeaponParentIdentity,
                ()->source.root.inputOwner().orElse(null),()->startNativeFields(source,held));
    }
    private void selectPreparedNative(int next){
        var attempt=selectionAttempt;if(attempt==null||next!=slot||!(inventory instanceof McInventoryService nativeInventory))return;
        attempt.actual=nativeInventory.selectSlotScoped(originalSlot,next,
                ()->selectionAttempt==attempt&&authority.getAsBoolean()&&java.util.Objects.equals(settings,owner.scheduledPauseSignature())
                        &&startFields(attempt,originalSlot)&&attempt.history.current(),
                ()->afterPreparedNativeSetter(attempt,next));
    }
    /** The final native adapter calls this only after its original setter returned, and again after packet submission. */
    private boolean afterPreparedNativeSetter(SelectionAttempt source,int next){
        if(selectionAttempt!=source||source.setterReturn!=null)return false;
        var boundary=new NativeSetterReturnBoundary(source);source.setterReturn=boundary;
        try{return selectionAttempt==source&&authority.getAsBoolean()&&java.util.Objects.equals(settings,owner.scheduledPauseSignature())&&startFields(source,next);}
        finally{boundary.open=false;if(source.setterReturn==boundary)source.setterReturn=null;}
    }
    /** Data from this private producer selects the parent reservation phase; it grants no setter or packet permission. */
    FishingWeaponSelectionState.Phase fishingReservationPhase(int rodSlot,AbstractModule actualParent){
        if(declaration!=MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER||releasing||!client.isSameThread()
                ||actualParent==null||client.player==null||client.level==null||client.getConnection()==null)return FishingWeaponSelectionState.Phase.REFUSED;
        var source=committed&&nativeLoan!=null?nativeLoan.source:selectionAttempt;
        boolean same=source==null?!committed:source.parent==actualParent&&source.before.heldSlot()==rodSlot&&originalSlot==rodSlot
                &&client.player==source.before.player()&&client.level==source.before.world()&&client.getConnection()==source.before.connection()
                &&client.player.getInventory()==source.inventory;
        var boundary=source==null?null:source.setterReturn;
        boolean after=source!=null&&source==selectionAttempt&&boundary!=null&&boundary.open&&boundary.source==source;
        var stage=committed?FishingWeaponSelectionState.Stage.COMMITTED:after?FishingWeaponSelectionState.Stage.AFTER_NATIVE_SETTER:FishingWeaponSelectionState.Stage.ORIGINAL;
        boolean history=source==null?!committed:committed?nativeLoan!=null&&nativeLoan.history!=null&&nativeLoan.history.current():
                after?afterSetterHistoryCurrent(source):source.history!=null&&source.history.current();
        boolean confirmed=committed&&nativeLoan!=null&&nativeLoan.actual!=null&&nativeLoan.actual.confirmed()
                &&nativeLoan.actual.localSelected()&&nativeLoan.actual.packetSubmitted()&&nativeLoan.actual.expectedSlot()==rodSlot&&nativeLoan.actual.requestedSlot()==slot;
        return FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(stage,same,history,confirmed,rodSlot,slot,inventory.selectedSlot()));
    }
    private boolean afterSetterHistoryCurrent(SelectionAttempt source){
        var observed=McPestInventoryRestorationObserver.selectedSlotHistory(source.inventory);
        return observed!=null&&observed.followsExactlyOneSetter(source.history);
    }
    /** Captured genuine selection history only. Parent cleanup rechecks this after child disable to preserve newer manual setters. */
    McPestInventoryRestorationObserver.SelectionHistory fishingReturnHistory(int rodSlot,AbstractModule actualParent){
        var loan=nativeLoan;var source=loan==null?null:loan.source;
        if(declaration!=MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER||!committed||releasing||source==null||source.parent!=actualParent
                ||actualParent==null||originalSlot!=rodSlot||source.before.heldSlot()!=rodSlot||loan.actual==null||!loan.actual.confirmed()
                ||!loan.actual.localSelected()||!loan.actual.packetSubmitted()||loan.actual.expectedSlot()!=rodSlot||loan.actual.requestedSlot()!=slot
                ||loan.history==null||!loan.history.current()||!client.isSameThread()||client.player!=source.before.player()||client.level!=source.before.world()
                ||client.getConnection()!=source.before.connection()||client.player==null||client.player.getInventory()!=source.inventory||inventory.selectedSlot()!=slot)return null;
        return loan.history;
    }
    private boolean cleanupFields(NativeLoan loan,int held){
        var source=loan.source;if(!nativeContext(source,held)||loan.actual==null||!loan.actual.confirmed()||!loan.actual.packetSubmitted()
                ||!loan.actual.localSelected()||loan.history==null)return false;
        boolean lifecycle=source.pair==null?source.root.pendingOwnDisableFromMatches(source.lifecycle,owner)
                :source.root.lifecycleCurrent(source.lifecycle)&&source.pair.childStoppingWithCurrentParent()
                    ||source.root.pendingOwnDisableFromMatches(source.lifecycle,source.parent)&&source.pair.parentAndChildStopping();
        var p=client.player;return lifecycle&&original!=null&&tool!=null&&normalizedTool!=null
                &&BestiaryToolWear.allowed(tool.getCount(),p.getInventory().getItem(slot).getCount(),tool.getDamageValue(),p.getInventory().getItem(slot).getDamageValue(),
                    issuedAttacks,normalizedTool.matches(withoutWear(p.getInventory().getItem(slot)),client.level.registryAccess()))
                &&original.matches(p.getInventory().getItem(originalSlot),client.level.registryAccess());
    }
    void release() {
        if(releasing)return;releasing=true;var captured=nativeLoan;nativeLoan=null;
        try{
            if(committed&&captured!=null&&slot>=0&&originalSlot!=slot&&inventory instanceof McInventoryService nativeInventory){
                captured.once.restore(new WeaponSelectionReturn.Effects(){
                    public boolean current(){return owner.declaredWeaponParentIdentity()==captured.source.parent&&releasing&&nativeLoan==null&&captured.history!=null&&captured.history.current()&&cleanupFields(captured,slot);}
                    public boolean submit(){
                        var receipt=nativeInventory.selectSlotScoped(slot,originalSlot,
                                ()->java.util.Objects.equals(settings,owner.scheduledPauseSignature())&&current(),
                                ()->java.util.Objects.equals(settings,owner.scheduledPauseSignature())&&owner.declaredWeaponParentIdentity()==captured.source.parent&&releasing&&nativeLoan==null&&cleanupFields(captured,originalSlot));
                        return receipt!=null&&receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted();
                    }
                });
            }
        }finally{
            committed = false; start.cancel();
            slot = originalSlot = -1; player = world = connection = null;
            original = normalizedTool = null; tool = null; preferred = settings = null; issuedAttacks = 0;releasing=false;
        }
    }
    private static ItemStack withoutWear(ItemStack stack) {
        var copy = stack.copy();
        if (copy.isDamageableItem()) copy.setDamageValue(0);
        return copy;
    }
    private static BestiaryWeaponSelector.Kind kind(ItemStack stack) {
        if (stack.is(Items.DIAMOND_SWORD) || stack.is(Items.GOLDEN_SWORD) || stack.is(Items.IRON_SWORD)
                || stack.is(Items.STONE_SWORD) || stack.is(Items.WOODEN_SWORD) || stack.is(Items.NETHERITE_SWORD))
            return BestiaryWeaponSelector.Kind.SWORD;
        // 26.3 models axes with data components/tags; the old AxeItem Java class no longer exists.
        if (stack.is(ItemTags.AXES)) return BestiaryWeaponSelector.Kind.AXE;
        if (stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.GOLDEN_PICKAXE) || stack.is(Items.IRON_PICKAXE)
                || stack.is(Items.STONE_PICKAXE) || stack.is(Items.WOODEN_PICKAXE) || stack.is(Items.NETHERITE_PICKAXE))
            return BestiaryWeaponSelector.Kind.PICKAXE;
        return BestiaryWeaponSelector.Kind.OTHER;
    }
}
