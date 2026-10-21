package dev.veltraph.client.combat.heal.mc;

import dev.veltraph.client.combat.heal.HealServices;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.GhostConfig;
import dev.veltraph.client.service.InventoryService;
import dev.veltraph.client.combat.heal.HealItemSelector;
import dev.veltraph.client.combat.heal.HealItemType;
import dev.veltraph.client.combat.heal.HealStackingMode;
import dev.veltraph.client.combat.heal.SharedHealUseLedger;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

/** Native adapter for the recovered five-type selector and the existing owned camera lease.
 * Per-type timestamps witness a local emitted ordinary use, never a server heal/mana receipt.
 * Staged native readiness uses fresh received mana and raw HP; exact source timing remains open.
 */
public final class McHealServices implements HealServices {

    private final Minecraft client;
    private final ConfigService config;
    private final InventoryService inventory;
    private static dev.veltraph.client.service.mc.NativeActionBarSignal.Subscription statsSubscription;
    private static dev.veltraph.client.service.mc.NativeActionBarSignal.Receipt statsReceipt;
    private static final dev.veltraph.client.combat.heal.SkyBlockHealth sourceHealth = new dev.veltraph.client.combat.heal.SkyBlockHealth();
    private static java.util.OptionalInt sourceMana = java.util.OptionalInt.empty();


    // Original helper's map is static (source lines 58, 315, 371, 738).
    // Preserve it across handler replacement, snapshot disposal and world transitions.
    private final HealItemSelector healItems = SharedHealUseLedger.selector();
    private HealItemType weaponType, snapshotWeaponType;
    private HealStackingMode snapshotStackingMode;
    private boolean snapshotCapturedHudRequired;
    private int weaponSlot = -1;  // cached hotbar slot of the heal weapon
    private float savedPitch, savedYaw;
    private int savedSlot;
    private net.minecraft.client.player.LocalPlayer savedPlayer;
    private net.minecraft.client.multiplayer.ClientLevel savedLevel;
    private net.minecraft.client.multiplayer.ClientPacketListener savedConnection;
    private final dev.veltraph.client.service.RotationService rotation;
    private final net.minecraft.world.item.ItemStack[] savedHotbar=new net.minecraft.world.item.ItemStack[9];
    private Object authority;
    private dev.veltraph.client.combat.heal.HealCameraLease cameraLease;
    private long epoch, attempt, deadline;
    private long usePositionSerial;
    private int expectedSlot, snapshotWeaponSlot;
    private String snapshotWeaponName;
    private dev.veltraph.client.combat.heal.HealRawThresholds snapshotRawThresholds;
    private boolean snapshotPermission;
    private boolean cameraReady, restoring, restorationComplete=true, lookDownPermission=true, invalidCustody;
    private dev.veltraph.client.combat.heal.SkyBlockHealth health;
    /** Source instance permission, default true; dedicated parents may explicitly suppress look-down. */
    public void setLookDownPermission(boolean allowed) { lookDownPermission=allowed; }
    public boolean verifiedCustody() { return savedPlayer!=null && custodyValid(); }

    public McHealServices withHealth(dev.veltraph.client.combat.heal.SkyBlockHealth health) {
        this.health=health; return this;
    }

    public McHealServices(Minecraft client, ConfigService config, InventoryService inventory) {
        this(client,config,inventory,new dev.veltraph.client.service.mc.McRotationService(client,config));
    }
    public McHealServices(Minecraft client, ConfigService config, InventoryService inventory,
                          dev.veltraph.client.service.RotationService rotation) {
        this.client=client; this.config=config; this.inventory=inventory; this.rotation=rotation;
        McCapturedHudVitals.install(client);
        McReceivedHealth.install(client);
        if(statsSubscription==null)statsSubscription=dev.veltraph.client.service.mc.NativeActionBarSignal.subscribe(receipt->{
            if(!receipt.current(client))return;
            if(statsReceipt!=null&&(statsReceipt.player().get()!=receipt.player().get()
                    ||statsReceipt.world().get()!=receipt.world().get()||statsReceipt.connection().get()!=receipt.connection().get()))sourceHealth.reset();
            statsReceipt=receipt;
            sourceHealth.accept(receipt.component().getString(),receipt.receivedAt());
            sourceMana=dev.veltraph.client.fishing.FishingManaParser.parse(receipt.component().getString());
        });

    }

    @Override
    public boolean available() {
        return client.player != null && client.level != null && client.gui.screen() == null;
    }

    /** Legacy seam name; now selects all five classified source heal types. */
    @Override
    public boolean hasHyperion() {
        if (savedPlayer != null) return custodyValid() && snapshotWeaponType != null;
        weaponSlot = -1;
        weaponType = null;
        if (client.player == null) return false;
        var hotbar = new ArrayList<HealItemSelector.Item>(9);
        for (int slot = 0; slot < 9; slot++) {
            var stack = client.player.getInventory().getItem(slot);
            hotbar.add(new HealItemSelector.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.getHoverName().getString(), stack.isEmpty()));
        }
        var selected = healItems.select(hotbar, config.get(GhostConfig.AUTO_HEAL_STACKING),
                System.currentTimeMillis());
        if (selected.isEmpty()) return false;
        weaponSlot = selected.get().slot();
        weaponType = selected.get().type();
        return true;
    }

    @Override public boolean usesSourceReadiness(){return true;}
    @Override public long sourceClientTick(){return client.player==null?Long.MIN_VALUE:client.player.tickCount;}
    @Override public SourceTickScope sourceTickScope(){return new SourceTickScope(client.player,client.level,client.getConnection());}
    @Override public boolean restoreSourceSavedSlot(){
        if(savedPlayer==null||!custodyValid())return false;
        expectedSlot=savedSlot;inventory.selectSlot(savedSlot);
        return custodyValid();
    }
    @Override public boolean sourceHyperionLookDown(){return savedPlayer!=null&&snapshotWeaponType==HealItemType.HYPERION&&snapshotPermission;}
    @Override public boolean sourcePostUsePositionReceived(){
        var receipt=NativeHealPoseSignal.read(client);
        return savedPlayer!=null&&custodyValid()&&receipt.current()&&receipt.serial()>usePositionSerial;
    }
    @Override public void cancelOwnedHealRotation(){
        if(savedPlayer==null||!custodyValid())return;
        var before=new dev.veltraph.client.combat.heal.HealCameraLease(savedPlayer,savedLevel,savedConnection,authority,epoch);
        boolean installedBefore=dev.veltraph.client.service.mc.McOwnedRotation.installed(client);
        rotation.cancelRotation();
        boolean installedAfter=dev.veltraph.client.service.mc.McOwnedRotation.installed(client);
        var after=new dev.veltraph.client.combat.heal.HealCameraLease(client.player,client.level,client.getConnection(),
                dev.veltraph.client.service.mc.McOwnedRotation.authority(client),
                dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
        // Production cancellation has no callbacks/install calls and makes exactly one epoch increment.
        if(!dev.veltraph.client.combat.heal.HealOwnedCancellation.mayRenew(before,after,true,installedBefore,installedAfter)){
            invalidCustody=true;return;
        }
        epoch=after.epoch();cameraLease=after;
    }
    private record SourceVitals(int health,int mana) {}
    private java.util.Optional<SourceVitals> currentVitals(long now){
        if(client.player==null||!client.player.isAlive()||client.player.isDeadOrDying())return java.util.Optional.empty();
        var captured=McCapturedHudVitals.current(client,now);
        if(captured.isPresent()){var values=captured.get().fields();return java.util.Optional.of(new SourceVitals(values.currentHealth(),values.currentMana()));}
        boolean required=savedPlayer==null?config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED):snapshotCapturedHudRequired;
        if(required||statsReceipt==null||!statsReceipt.current(client)||now<statsReceipt.receivedAt()
                ||now-statsReceipt.receivedAt()>2000||sourceMana.isEmpty())return java.util.Optional.empty();
        var raw=sourceHealth.rawHealth(now);return raw.isPresent()?java.util.Optional.of(new SourceVitals((int)raw.getAsDouble(),sourceMana.getAsInt())):java.util.Optional.empty();
    }
    private boolean playerBlocker(){
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                &&hit.getEntity() instanceof net.minecraft.world.entity.player.Player)return true;
        return !client.level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                client.player.getBoundingBox().inflate(4,4,4),p->p!=client.player&&!p.isInvisible()&&p.isAlive()).isEmpty();
    }
    @Override public boolean sourceReady(boolean ignoreHealth,boolean emergency){
        long now=System.currentTimeMillis();
        var vitals=currentVitals(now);
        if(!available()||vitals.isEmpty()||(savedPlayer!=null&&!custodyValid()))return false;
        if(savedPlayer==null&&!hasHyperion())return false;
        var type=savedPlayer==null?weaponType:snapshotWeaponType;
        if(type==null)return false;
        var thresholds=savedPlayer==null?dev.veltraph.client.combat.heal.HealRawThresholds.fromConfig(config):snapshotRawThresholds;
        return dev.veltraph.client.combat.heal.HealSourceReadiness.ready(new dev.veltraph.client.combat.heal.HealSourceReadiness.Input(
                true,config.get(GhostConfig.GHOST_AUTO_HEAL_ENABLED),playerBlocker(),type,vitals.get().mana(),
                vitals.get().health(),healItems.cooling(type,now),ignoreHealth,emergency,
                thresholds.normal(),thresholds.emergency()));
    }
    @Override public boolean sourceHealthRecovered(){return currentVitals(System.currentTimeMillis())
            .map(v->v.health()>config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE)).orElse(false);}
    private ArrayList<HealItemSelector.Item> observedHotbar(){
        var hotbar=new ArrayList<HealItemSelector.Item>(9);
        for(int slot=0;slot<9;slot++){var stack=client.player.getInventory().getItem(slot);
            hotbar.add(new HealItemSelector.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getHoverName().getString(),stack.isEmpty()));}
        return hotbar;
    }
    @Override public boolean prospectiveSourceReady(){
        long now=System.currentTimeMillis();
        var vitals=currentVitals(now);
        if(!available()||savedPlayer==null||!custodyValid()||vitals.isEmpty())return false;
        return dev.veltraph.client.combat.heal.HealProspectiveReadiness.ready(healItems,observedHotbar(),
                snapshotStackingMode,now,true,config.get(GhostConfig.GHOST_AUTO_HEAL_ENABLED),playerBlocker(),
                vitals.get().mana(),vitals.get().health(),false,false,snapshotRawThresholds);
    }
    @Override public boolean prepareSourceRepeat(){
        if(savedPlayer==null||!custodyValid())return false;
        var hotbar=new ArrayList<HealItemSelector.Item>(9);
        for(int slot=0;slot<9;slot++){var stack=savedPlayer.getInventory().getItem(slot);
            hotbar.add(new HealItemSelector.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getHoverName().getString(),stack.isEmpty()));}
        var selection=healItems.select(hotbar,snapshotStackingMode,System.currentTimeMillis());
        if(selection.isEmpty())return false;
        snapshotWeaponType=selection.get().type();snapshotWeaponSlot=selection.get().slot();
        if(!sourceReady(true,false))return false;
        cameraReady=false;long token=++attempt;
        if(!lookDownPermission||!sourceHyperion(savedHotbar[snapshotWeaponSlot])){cameraReady=true;return true;}
        var random=java.util.concurrent.ThreadLocalRandom.current();
        rotation.rotateTo(savedPlayer.getYRot()+(float)random.nextDouble(-10,10),75f+(float)random.nextDouble(10),()->{
            if(token==attempt&&!restoring&&savedPlayer!=null&&custodyValid())cameraReady=true;
        });return true;
    }

    @Override
    public double healthPct() {
        var captured=McCapturedHudVitals.current(client,System.currentTimeMillis());
        if(captured.isPresent())return captured.get().fields().percent();
        if(config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED))return Double.NaN;
        if(health!=null) {
            var current=health.percent(System.currentTimeMillis());
            if(current.isPresent()) return current.getAsDouble();
        }
        try {
            if (client.player == null) {
                return 100.0;
            }
            float max = client.player.getMaxHealth();
            if (max <= 0) {
                return 100.0;
            }
            return Math.max(0.0, Math.min(100.0, client.player.getHealth() / max * 100.0));
        } catch (Throwable t) {
            return 100.0;
        }
    }

    @Override
    public boolean clearAbove() {
        try {
            if (client.player == null || client.level == null) {
                return false;
            }
            BlockPos head = client.player.blockPosition().above(2);
            return client.level.getBlockState(head).isAir();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public boolean ownsSnapshot(){return savedPlayer!=null;}
    @Override public boolean custodyValid() {
        if(invalidCustody)return false;
        if(savedPlayer==null)return true;
        if(client.player!=savedPlayer || client.level!=savedLevel || client.getConnection()!=savedConnection
                || !available() || !savedPlayer.isAlive() || savedPlayer.getInventory().getSelectedSlot()!=expectedSlot
                || System.currentTimeMillis()>deadline
                || !config.get(GhostConfig.GHOST_HEAL_WEAPON_NAME).equals(snapshotWeaponName)
                || snapshotRawThresholds==null || !snapshotRawThresholds.matches(config)
                || config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED)!=snapshotCapturedHudRequired
                || config.get(GhostConfig.AUTO_HEAL_STACKING)!=snapshotStackingMode
                || lookDownPermission!=snapshotPermission)return false;
        if(dev.veltraph.client.service.mc.McOwnedRotation.installed(client)
                && (authority!=dev.veltraph.client.service.mc.McOwnedRotation.authority(client)
                || epoch!=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)))return false;
        for(int i=0;i<9;i++)if(!net.minecraft.world.item.ItemStack.matches(savedHotbar[i],savedPlayer.getInventory().getItem(i)))return false;
        return true;
    }
    @Override public void tickCamera() {
        if(savedPlayer==null)return;
        if(!custodyValid()){discardSnapshot();invalidCustody=true;return;}
        rotation.tickRotation();
    }
    @Override public boolean cameraReady(){return savedPlayer!=null && cameraReady && custodyValid();}
    @Override public boolean restorationComplete(){return restorationComplete;}
    @Override public void lookDown() {
        if(!available() || !hasHyperion() || client.getConnection()==null)return;
        savedPlayer=client.player; savedLevel=client.level; savedConnection=client.getConnection();
        savedPitch=savedPlayer.getXRot(); savedYaw=savedPlayer.getYRot();
        savedSlot=expectedSlot=inventory.selectedSlot();snapshotWeaponSlot=weaponSlot;
        snapshotWeaponName=config.get(GhostConfig.GHOST_HEAL_WEAPON_NAME);
        snapshotRawThresholds=dev.veltraph.client.combat.heal.HealRawThresholds.fromConfig(config);
        snapshotCapturedHudRequired=config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED);
        snapshotWeaponType=weaponType;snapshotStackingMode=config.get(GhostConfig.AUTO_HEAL_STACKING);snapshotPermission=lookDownPermission;
        for(int i=0;i<9;i++)savedHotbar[i]=savedPlayer.getInventory().getItem(i).copy();
        dev.veltraph.client.service.mc.McOwnedRotation.validate(client);
        authority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
        epoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        cameraLease=new dev.veltraph.client.combat.heal.HealCameraLease(savedPlayer,savedLevel,savedConnection,authority,epoch);
        deadline=System.currentTimeMillis()+15000L; long token=++attempt;
        invalidCustody=false;cameraReady=false; restoring=false; restorationComplete=false;
        if(!lookDownPermission || !sourceHyperion(savedHotbar[snapshotWeaponSlot])){cameraReady=true;return;}
        // Latest source: Hyperion AND instance permission, pitch [75,85), yaw offset [-10,10).
        var random=java.util.concurrent.ThreadLocalRandom.current();
        rotation.rotateTo(savedYaw+(float)random.nextDouble(-10,10),75f+(float)random.nextDouble(10),()->{
            if(token==attempt && !restoring && savedPlayer!=null && custodyValid())cameraReady=true;
        });
    }
    private boolean sourceHyperion(net.minecraft.world.item.ItemStack stack) {
        return dev.veltraph.client.combat.heal.HealLookDownPolicy.required(
                stack.is(net.minecraft.world.item.Items.IRON_SWORD),stack.getHoverName().getString(),lookDownPermission);
    }
    @Override public void castHyperion() { castWithOutcome(); }
    @Override public dev.veltraph.client.combat.heal.HealCastOutcome castWithOutcome() {
        var refused = dev.veltraph.client.combat.heal.HealCastOutcome.REFUSED_CUSTODY;
        if(!cameraReady() || client.gameMode==null || !clearAbove() || snapshotWeaponSlot<0 || snapshotWeaponSlot>8)return refused;
        if(!net.minecraft.world.item.ItemStack.matches(savedHotbar[snapshotWeaponSlot],savedPlayer.getInventory().getItem(snapshotWeaponSlot)))return refused;
        long now=System.currentTimeMillis();
        if(healItems.cooling(snapshotWeaponType,now))return dev.veltraph.client.combat.heal.HealCastOutcome.REFUSED_COOLDOWN;
        if(!sourceReady(true,false))return refused;
        expectedSlot=snapshotWeaponSlot; inventory.selectSlot(snapshotWeaponSlot);
        if(!custodyValid()){discardSnapshot();return refused;}
        // Source records before ordinary use; this is local emission, not a server mana/heal receipt.
        healItems.recordEmittedUse(snapshotWeaponType,now);
        usePositionSerial=NativeHealPoseSignal.read(client).serial();
        client.gameMode.useItem(savedPlayer,InteractionHand.MAIN_HAND);
        savedPlayer.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
        return dev.veltraph.client.combat.heal.HealCastOutcome.EMITTED;
    }
    @Override public void restoreLook() {
        if(savedPlayer==null){restorationComplete=true;return;}
        if(!custodyValid()){discardSnapshot();return;}
        long token=++attempt; restoring=true; cameraReady=false;
        rotation.rotateTo(savedYaw,savedPitch,()->{
            if(token!=attempt || !restoring || savedPlayer==null || !custodyValid())return;
            expectedSlot=savedSlot;inventory.selectSlot(savedSlot);
            if(!custodyValid()){discardSnapshot();invalidCustody=true;return;}
            restorationComplete=true;clearSnapshot();
        });
    }
    private void clearSnapshot(){savedPlayer=null;savedLevel=null;savedConnection=null;authority=null;cameraLease=null;snapshotWeaponType=null;snapshotStackingMode=null;snapshotRawThresholds=null;java.util.Arrays.fill(savedHotbar,null);}
    @Override public void discardSnapshot() {
        // Never cancel a replacement owner's camera request.
        if(savedPlayer!=null && (!dev.veltraph.client.service.mc.McOwnedRotation.installed(client)
                || cameraLease.owns(new dev.veltraph.client.combat.heal.HealCameraLease(client.player,client.level,
                client.getConnection(),dev.veltraph.client.service.mc.McOwnedRotation.authority(client),
                dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)))))rotation.cancelRotation();
        ++attempt;clearSnapshot();cameraReady=false;restoring=false;restorationComplete=true;invalidCustody=false;
    }
}
