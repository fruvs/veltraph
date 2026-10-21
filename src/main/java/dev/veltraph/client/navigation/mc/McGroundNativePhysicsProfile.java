package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import dev.veltraph.client.navigation.GroundCapturedEffects;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundPhysicsRevisionTracked;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Read-only profile custody. No native setter, packet, input, or movement authority is exposed.
 * Kept separate from ordinary .1 motion proofs until real native profile fixtures prove integration. */
public final class McGroundNativePhysicsProfile {
    private McGroundNativePhysicsProfile() {}
    private static final List<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>> ATTRIBUTES =
            List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.GRAVITY,
                    Attributes.FRICTION_MODIFIER, Attributes.AIR_DRAG_MODIFIER, Attributes.MOVEMENT_EFFICIENCY);
    private static final java.util.Map<String,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> EFFECTS = java.util.Map.of(
            "minecraft:haste", MobEffects.HASTE, "minecraft:mining_fatigue", MobEffects.MINING_FATIGUE,
            "minecraft:strength", MobEffects.STRENGTH, "minecraft:night_vision", MobEffects.NIGHT_VISION,
            "minecraft:regeneration", MobEffects.REGENERATION, "minecraft:absorption", MobEffects.ABSORPTION,
            "minecraft:jump_boost", MobEffects.JUMP_BOOST);
    private static boolean knownNativeEffect(MobEffectInstance effect) {
        if (effect == null || effect.getClass() != MobEffectInstance.class
                || !((Object)effect instanceof GroundPhysicsRevisionTracked)) return false;
        String id = effect.getEffect().getRegisteredName();
        if (EFFECTS.get(id) != effect.getEffect()) return false;
        var value = effect.getEffect().value();
        String expected = id.equals("minecraft:regeneration") ? "net.minecraft.world.effect.RegenerationMobEffect"
                : id.equals("minecraft:absorption") ? "net.minecraft.world.effect.AbsorptionMobEffect"
                : "net.minecraft.world.effect.MobEffect";
        return value.getClass().getName().equals(expected)
                && value.getClass().getClassLoader() == net.minecraft.world.effect.MobEffect.class.getClassLoader();
    }
    public static Optional<Journey> capture(Minecraft client, LeaseGroundAdapter actor) {
        if (client == null || Minecraft.getInstance() != client || actor == null || !client.isSameThread()) return Optional.empty();
        var player = client.player; var level = client.level; var connection = client.getConnection();
        var root = VeltraphBootstrap.moduleManager();
        var lifecycle = root == null ? null : root.lifecycleSnapshot();
        if (player == null || player.getClass() != LocalPlayer.class || !((Object)player instanceof GroundPhysicsRevisionTracked)
                || level == null || connection == null || lifecycle == null || !lifecycle.known())
            return Optional.empty();
        try {
            // Register revisions and capture attributes/effects BEFORE the actual issued-leg callback.
            // current() then runs that callback only as refusal, followed by complete raw native leaves.
            var journey = new Journey(client, actor, player, level, connection, root, lifecycle);
            return journey.current().isPresent() ? Optional.of(journey) : Optional.empty();
        } catch (RuntimeException unavailable) { return Optional.empty(); }
    }
    public enum AdmissionKind { CAPTURED, UNSUPPORTED_INITIAL_PHYSICS, CUSTODY_REFUSED }
    /** Private factory result; failure never retires an actual Leg or grants walking/key authority. */
    public static final class Admission {
        private final AdmissionKind kind;private final Journey journey;
        private Admission(AdmissionKind kind,Journey journey){this.kind=kind;this.journey=journey;}
        public AdmissionKind kind(){return kind;}public Optional<Journey> journey(){return Optional.ofNullable(journey);}
    }
    private static boolean initialCapturedMarkers(LocalPlayer p){
        if(!((Object)p instanceof dev.veltraph.client.navigation.GroundNoGravityRevisionTracked)
                ||!((Object)p instanceof GroundPhysicsRevisionTracked)||!((Object)p instanceof GroundProfileMotionCarrier)
                ||!((Object)p instanceof GroundOwnedSprintCarrier)||!((Object)p instanceof GroundOwnedNativeTickCarrier)
                ||!((Object)p instanceof GroundOwnedSpeedRefreshCarrier)
                ||!((Object)p instanceof dev.veltraph.client.mixin.GroundSprintTimingAccessor)
                ||!((Object)p instanceof dev.veltraph.client.mixin.GroundSprintCapabilityAccessor)
                ||!((Object)p instanceof dev.veltraph.client.mixin.GroundBlockSpeedFactorAccessor)
                ||!((Object)p instanceof dev.veltraph.client.mixin.FigJumpPowerAccessor))return false;
        var data=p.getEntityData();
        if(data.getClass()!=net.minecraft.network.syncher.SynchedEntityData.class
                ||!((Object)data instanceof GroundPhysicsRevisionTracked)
                ||!((Object)data instanceof dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker))return false;
        var item=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)(Object)data).veltraph$nativeSharedItem(
                dev.veltraph.client.mixin.GroundSharedFlagsAccessor.veltraph$nativeSharedFlags());
        if(item==null||item.getClass()!=net.minecraft.network.syncher.SynchedEntityData.DataItem.class
                ||!((Object)item instanceof GroundPhysicsRevisionTracked))return false;
        var noGravityAccessor=dev.veltraph.client.mixin.GroundNoGravityAccessor.veltraph$nativeNoGravity();
        var noGravityItem=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)(Object)data).veltraph$nativeSharedItem(noGravityAccessor);
        if(noGravityItem==null||noGravityItem.getClass()!=net.minecraft.network.syncher.SynchedEntityData.DataItem.class
                ||!((Object)noGravityItem instanceof GroundPhysicsRevisionTracked)||noGravityItem.getAccessor()!=noGravityAccessor)return false;
        for(var attribute:ATTRIBUTES){var value=p.getAttribute(attribute);
            if(value==null||value.getClass()!=AttributeInstance.class||!((Object)value instanceof GroundPhysicsRevisionTracked))return false;}
        if(!dev.veltraph.client.navigation.GroundNativeGravityTraits.consistent(p.isNoGravity(),noGravityItem.getValue(),
                p.getAttribute(Attributes.GRAVITY).getValue(),p.getGravity()))return false;
        return p.getActiveEffects().stream().allMatch(McGroundNativePhysicsProfile::knownNativeEffect);
    }
    /** Distinguishes unsupported INITIAL traits from a changed supported capture. Only the former
     * may retain the original branch, and only after the original opaque actor/root/PWC check. */
    public static Admission captureCaptured(Minecraft client,LeaseGroundAdapter actor){
        if(client==null||Minecraft.getInstance()!=client||actor==null||!client.isSameThread())return new Admission(AdmissionKind.CUSTODY_REFUSED,null);
        var p=client.player;var level=client.level;var connection=client.getConnection();var root=VeltraphBootstrap.moduleManager();
        var lifecycle=root==null?null:root.lifecycleSnapshot();
        if(p==null||p.getClass()!=LocalPlayer.class||level==null||connection==null||lifecycle==null||!lifecycle.known())
            return new Admission(AdmissionKind.CUSTODY_REFUSED,null);
        Journey observed=null;
        try {
            if(!initialCapturedMarkers(p))return new Admission(AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS,null);
            observed=new Journey(client,actor,p,level,connection,root,lifecycle);
            if(observed.profile==null||!dev.veltraph.client.navigation.CapturedProfileRouteFamily.supported(observed.profile.state())){
                boolean changed=!GroundPhysicsNativeRevisions.capturedTokensCurrent(observed.revisions);
                observed.retire();return new Admission(changed?AdmissionKind.CUSTODY_REFUSED:AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS,null);
            }
            if(!observed.leaf()){
                boolean changed=!GroundPhysicsNativeRevisions.capturedTokensCurrent(observed.revisions)
                        ||client.player!=p||client.level!=level||client.getConnection()!=connection
                        ||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(lifecycle);
                observed.retire();return new Admission(changed?AdmissionKind.CUSTODY_REFUSED:AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS,null);
            }
            if(observed.current().isEmpty()){observed.retire();return new Admission(AdmissionKind.CUSTODY_REFUSED,null);}
            return new Admission(AdmissionKind.CAPTURED,observed);
        }catch(RuntimeException unavailable){if(observed!=null)observed.retire();return new Admission(AdmissionKind.CUSTODY_REFUSED,null);}
    }
    /** Explicit directional opt-in. Ordinary factories cannot acquire this private evolution mode. */
    public static Admission captureDirectionalQualified(Minecraft client,LeaseGroundAdapter actor){
        if(client==null||Minecraft.getInstance()!=client||actor==null||!client.isSameThread())return new Admission(AdmissionKind.CUSTODY_REFUSED,null);
        var p=client.player;var level=client.level;var connection=client.getConnection();var root=VeltraphBootstrap.moduleManager();
        var lifecycle=root==null?null:root.lifecycleSnapshot();
        if(p==null||p.getClass()!=LocalPlayer.class||level==null||connection==null||lifecycle==null||!lifecycle.known())
            return new Admission(AdmissionKind.CUSTODY_REFUSED,null);
        Journey observed=null;
        try {
            if(!initialCapturedMarkers(p))return new Admission(AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS,null);
            observed=new Journey(client,actor,p,level,connection,root,lifecycle,true);
            if(observed.profile==null||!dev.veltraph.client.navigation.GroundOwnedSprintTransition.directionalProfileSupported(observed.profile.state())){
                boolean changed=!GroundPhysicsNativeRevisions.capturedTokensCurrent(observed.revisions);
                observed.retire();return new Admission(changed?AdmissionKind.CUSTODY_REFUSED:AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS,null);
            }
            if(!observed.leaf()){
                boolean changed=!GroundPhysicsNativeRevisions.capturedTokensCurrent(observed.revisions)
                        ||client.player!=p||client.level!=level||client.getConnection()!=connection
                        ||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(lifecycle);
                observed.retire();return new Admission(changed?AdmissionKind.CUSTODY_REFUSED:AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS,null);
            }
            if(observed.current().isEmpty()){observed.retire();return new Admission(AdmissionKind.CUSTODY_REFUSED,null);}
            return new Admission(AdmissionKind.CAPTURED,observed);
        }catch(RuntimeException unavailable){if(observed!=null)observed.retire();return new Admission(AdmissionKind.CUSTODY_REFUSED,null);}
    }
    public static final class Journey {
        private final Minecraft client; private final LeaseGroundAdapter actor;
        private final LocalPlayer player; private final ClientLevel level;
        private final ClientPacketListener connection; private final ModuleManager root;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final boolean directionalQualified;
        private final boolean directionalFlatVanilla;
        private final List<AttributeInstance> attributes;
        private final Object sharedFlagsData,sharedFlagsItem;
        private final net.minecraft.network.syncher.EntityDataAccessor<Boolean> noGravityAccessor;
        private final Object noGravityItem;
        private List<GroundPhysicsNativeRevisions.Token> revisions;
        private final MobEffectInstance jump; private final int initialTick;
        private record EffectLife(MobEffectInstance instance, int duration) {}
        private final List<EffectLife> effects;
        private boolean sprinting;
        private GroundNativePhysicsProfile profile;
        private GroundOwnedSprintCarrier.Observation consumedOwnedSprint;
        private int lastTick; private boolean retired;
        private Journey(Minecraft client, LeaseGroundAdapter actor, LocalPlayer player,
                ClientLevel level, ClientPacketListener connection, ModuleManager root,
                ModuleManager.LifecycleSnapshot lifecycle) {
            this(client,actor,player,level,connection,root,lifecycle,false);
        }
        private Journey(Minecraft client, LeaseGroundAdapter actor, LocalPlayer player,
                ClientLevel level, ClientPacketListener connection, ModuleManager root,
                ModuleManager.LifecycleSnapshot lifecycle,boolean directionalQualified) {
            this.directionalQualified=directionalQualified;
            this.client = client; this.actor = actor; this.player = player; this.level = level;
            this.connection = connection; this.root = root; this.lifecycle = lifecycle;
            this.attributes = ATTRIBUTES.stream().map(player::getAttribute).toList();
            sharedFlagsData=player.getEntityData();
            sharedFlagsItem=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)sharedFlagsData).veltraph$nativeSharedItem(
                    dev.veltraph.client.mixin.GroundSharedFlagsAccessor.veltraph$nativeSharedFlags());
            noGravityAccessor=dev.veltraph.client.mixin.GroundNoGravityAccessor.veltraph$nativeNoGravity();
            noGravityItem=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)sharedFlagsData).veltraph$nativeSharedItem(noGravityAccessor);
            var tracked = new ArrayList<GroundPhysicsNativeRevisions.Token>();
            tracked.add(GroundPhysicsNativeRevisions.watch(player));
            for (var attribute : attributes) tracked.add(GroundPhysicsNativeRevisions.watch(attribute));
            jump = player.getEffect(MobEffects.JUMP_BOOST);
            effects = player.getActiveEffects().stream().map(e -> new EffectLife(e, e.getDuration())).toList();
            for (var life : effects) tracked.add(GroundPhysicsNativeRevisions.watch(life.instance()));
            revisions = java.util.Collections.unmodifiableList(tracked);
            initialTick = player.tickCount; lastTick = initialTick;
            sprinting = player.isSprinting();
            profile = GroundNativePhysicsProfile.classify(sample()).orElse(null);
            directionalFlatVanilla=directionalQualified&&profile!=null
                    &&dev.veltraph.client.navigation.GroundOwnedSprintTransition.directionalVanillaProfileSupported(profile.state());
        }
        /** Actual original native data/item/accessor plus explicit false and native gravity equality. */
        private boolean noGravityCurrent() {
            if(!((Object)player instanceof dev.veltraph.client.navigation.GroundNoGravityRevisionTracked)
                    ||player.getEntityData()!=sharedFlagsData
                    ||sharedFlagsData.getClass()!=net.minecraft.network.syncher.SynchedEntityData.class
                    ||!((Object)sharedFlagsData instanceof GroundPhysicsRevisionTracked)
                    ||!((Object)sharedFlagsData instanceof dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)
                    ||noGravityAccessor!=dev.veltraph.client.mixin.GroundNoGravityAccessor.veltraph$nativeNoGravity())return false;
            var item=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)sharedFlagsData).veltraph$nativeSharedItem(noGravityAccessor);
            var gravity=attributes.get(2);
            return item!=null&&item==noGravityItem&&item.getClass()==net.minecraft.network.syncher.SynchedEntityData.DataItem.class
                    &&(Object)item instanceof GroundPhysicsRevisionTracked&&item.getAccessor()==noGravityAccessor
                    &&gravity!=null&&gravity.getClass()==AttributeInstance.class&&((Object)gravity instanceof GroundPhysicsRevisionTracked)
                    &&dev.veltraph.client.navigation.GroundNativeGravityTraits.consistent(player.isNoGravity(),item.getValue(),gravity.getValue(),player.getGravity());
        }
        private boolean sampleMatchesCapturedProfile() {
            if(!noGravityCurrent())return false;
            var flagsData=player.getEntityData();
            var flagsAccessor=dev.veltraph.client.mixin.GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
            if (flagsData!=sharedFlagsData || flagsData.getClass()!=net.minecraft.network.syncher.SynchedEntityData.class
                    || !((Object)flagsData instanceof GroundPhysicsRevisionTracked)
                    || !((Object)flagsData instanceof dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)) return false;
            var flagsItem=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)(Object)flagsData).veltraph$nativeSharedItem(flagsAccessor);
            if (flagsItem==null || flagsItem!=sharedFlagsItem || flagsItem.getClass()!=net.minecraft.network.syncher.SynchedEntityData.DataItem.class
                    || !((Object)flagsItem instanceof GroundPhysicsRevisionTracked)
                    || flagsItem.getAccessor()!=flagsAccessor || !(flagsItem.getValue() instanceof Byte)) return false;
            for(int attributeIndex=0;attributeIndex<attributes.size();attributeIndex++) {
                var a=attributes.get(attributeIndex);
                if(a==null||a.getClass()!=AttributeInstance.class||!((Object)a instanceof GroundPhysicsRevisionTracked))return false;
            }
            for (int i = 1; i < attributes.size(); i++) if (!attributes.get(i).getModifiers().isEmpty()) return false;
            if (!((Object)player instanceof dev.veltraph.client.mixin.GroundBlockSpeedFactorAccessor speedFactor)) return false;
            // Only the immutable privately captured mathematical profile is compared here.
            // Every native getter still runs in the original sample encounter/argument order.
            var match = new GroundNativeProfileReadonlyMatch(profile.state());
            var movementModifiers=attributes.getFirst().getModifiers();
            if(dev.veltraph.client.navigation.GroundNativeEmptyProjection.ifEmpty(movementModifiers)==null)
                movementModifiers.stream().forEachOrdered(m ->
                    match.modifier(m.id().toString(), m.amount(), m.operation().ordinal()));
            double baseSpeed = attributes.getFirst().getBaseValue();
            double effectiveSpeed = attributes.getFirst().getValue();
            float travelSpeed = player.getSpeed();
            double jumpStrength = attributes.get(1).getValue();
            double gravity = attributes.get(2).getValue();
            double frictionModifier = attributes.get(3).getValue();
            double airDragModifier = attributes.get(4).getValue();
            int jumpAmplifier = jump == null ? -1 : jump.getAmplifier();
            if(dev.veltraph.client.navigation.GroundNativeEmptyProjection.ifEmpty(effects)==null)
                effects.stream().forEachOrdered(life -> {
                var e = life.instance();
                match.effect(e.getEffect().getRegisteredName(), e.getAmplifier(),
                        e.isAmbient(), e.isVisible(), e.showIcon());
            });
            double movementEfficiency = attributes.get(5).getValue();
            float blockSpeedFactor = speedFactor.veltraph$profileBlockSpeedFactor();
            return match.matches(baseSpeed,effectiveSpeed,travelSpeed,jumpStrength,gravity,
                    frictionModifier,airDragModifier,jumpAmplifier,movementEfficiency,blockSpeedFactor);
        }
        /** Pure metadata projection only; the original native getter remains at the sample site. */
        private static List<GroundNativePhysicsProfile.Modifier> projectMovementModifiers(
                java.util.Set<net.minecraft.world.entity.ai.attributes.AttributeModifier> rows) {
            var empty=dev.veltraph.client.navigation.GroundNativeEmptyProjection.<GroundNativePhysicsProfile.Modifier>ifEmpty(rows);
            if(empty!=null)return empty;
            return rows.stream().map(m ->
                    new GroundNativePhysicsProfile.Modifier(m.id().toString(), m.amount(), m.operation().ordinal())).toList();
        }
        /** Called at the original State argument position, retaining all nonempty getter order. */
        private static List<GroundCapturedEffects.Effect> projectCapturedEffects(List<EffectLife> rows) {
            var empty=dev.veltraph.client.navigation.GroundNativeEmptyProjection.<GroundCapturedEffects.Effect>ifEmpty(rows);
            if(empty!=null)return empty;
            return rows.stream().map(life -> {
                        var e = life.instance();
                        return new GroundCapturedEffects.Effect(e.getEffect().getRegisteredName(), e.getAmplifier(),
                                e.isAmbient(), e.isVisible(), e.showIcon());
                    }).toList();
        }
        private GroundNativePhysicsProfile.State sample() {
            if(!noGravityCurrent())return null;
            var flagsData=player.getEntityData();
            var flagsAccessor=dev.veltraph.client.mixin.GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
            if (flagsData!=sharedFlagsData || flagsData.getClass()!=net.minecraft.network.syncher.SynchedEntityData.class
                    || !((Object)flagsData instanceof GroundPhysicsRevisionTracked)
                    || !((Object)flagsData instanceof dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)) return null;
            var flagsItem=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)(Object)flagsData).veltraph$nativeSharedItem(flagsAccessor);
            if (flagsItem==null || flagsItem!=sharedFlagsItem || flagsItem.getClass()!=net.minecraft.network.syncher.SynchedEntityData.DataItem.class
                    || !((Object)flagsItem instanceof GroundPhysicsRevisionTracked)
                    || flagsItem.getAccessor()!=flagsAccessor || !(flagsItem.getValue() instanceof Byte)) return null;
            for(int attributeIndex=0;attributeIndex<attributes.size();attributeIndex++) {
                var a=attributes.get(attributeIndex);
                if(a==null||a.getClass()!=AttributeInstance.class||!((Object)a instanceof GroundPhysicsRevisionTracked))return null;
            }
            for (int i = 1; i < attributes.size(); i++) if (!attributes.get(i).getModifiers().isEmpty()) return null;
            if (!((Object)player instanceof dev.veltraph.client.mixin.GroundBlockSpeedFactorAccessor speedFactor)) return null;
            var modifiers = projectMovementModifiers(attributes.getFirst().getModifiers());
            return new GroundNativePhysicsProfile.State(attributes.getFirst().getBaseValue(),
                    attributes.getFirst().getValue(), player.getSpeed(), modifiers,
                    attributes.get(1).getValue(), attributes.get(2).getValue(), attributes.get(3).getValue(),
                    attributes.get(4).getValue(), jump == null ? -1 : jump.getAmplifier(),
                    projectCapturedEffects(effects), attributes.get(5).getValue(), speedFactor.veltraph$profileBlockSpeedFactor());
        }
        private static GroundOwnedSprintCarrier.SpeedState speedState(GroundNativePhysicsProfile.State state,boolean sprinting) {
            return new GroundOwnedSprintCarrier.SpeedState(state.baseSpeed(),state.effectiveSpeed(),state.travelSpeed(),state.movementModifiers(),sprinting);
        }
        /** Only exact stored native observations can advance captured movement custody.
         * Caller speed values, Boolean claims, and event lists cannot reach this leaf. */
        private boolean advanceOwnedNativeRevision() {
            if (GroundPhysicsNativeRevisions.capturedTokensCurrent(revisions)) return true;
            if (retired || profile == null || !((Object)player instanceof GroundOwnedSprintCarrier carrier)) return false;
            var exact = carrier.ownedSprintObservation();
            if (exact == null || exact == consumedOwnedSprint || !carrier.ownsSprintObservation(exact)
                    || exact.nativeTick() != player.tickCount || (long)exact.nativeTick() - lastTick != 1
                    || !actor.ownsProfileFrame(exact.frame()) || !exact.frame().nativeCurrent()
                    || !revisions.get(0).sameRevision(exact.beforePlayer())
                    || !revisions.get(1).sameRevision(exact.beforeMovement())
                    || !exact.before().equals(speedState(profile.state(),sprinting))) return false;
            for (int i=2;i<revisions.size();i++) if (revisions.get(i)==null || !revisions.get(i).current()) return false;
            var flagsAccessor=dev.veltraph.client.mixin.GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
            var flagsItem=((dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker)(Object)player.getEntityData()).veltraph$nativeSharedItem(flagsAccessor);
            if (exact.exactSharedFlagsAccessor()!=flagsAccessor || exact.exactSharedFlagsDataItem()!=flagsItem
                    || exact.afterFlags()!=player.getEntityData().get(flagsAccessor)
                    || ((exact.beforeFlags() & 8)!=0)!=sprinting || ((exact.afterFlags() & 8)!=0)!=player.isSprinting()) return false;
            var nativeModifier = dev.veltraph.client.mixin.GroundSprintModifierAccessor.veltraph$nativeSprintModifier();
            if (exact.exactNativeSprintModifier()!=nativeModifier || exact.exactNativeSprintIdentifier()!=nativeModifier.id()) return false;
            var playerChanges = new ArrayList<GroundPhysicsNativeRevisions.Change>();
            var movementChanges = new ArrayList<GroundPhysicsNativeRevisions.Change>();
            if (exact.sprintCalls()==1) {
                playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_METHOD,exact.nativeSprintArgument()));
                playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_FLAG,exact.nativeSprintArgument()));
                playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_SHARED,exact.nativeSprintArgument()));
                byte planned=exact.nativeSprintFlags();
                if (((planned & 8)!=0)!=exact.nativeSprintArgument()) return false;
                // Other original native shared-bit operations may change other bits during aiStep.
                // Sprint nested writes must preserve those bits at this original callsite.
                playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET2,planned));
                playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET3,planned));
                if (((exact.beforeFlags() & 8)!=0)!=exact.nativeSprintArgument())
                    playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_ITEM,planned));
                movementChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.REMOVE_ID,nativeModifier.id()));
                if (exact.nativeSprintArgument()) {
                    movementChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.ADD_TRANSIENT,nativeModifier));
                    movementChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.ADD_MODIFIER,nativeModifier));
                }
            } else if (exact.sprintCalls()!=0) return false;
            playerChanges.add(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,exact.after().field()));
            if (!exact.beforePlayer().followedExactlyBy(exact.afterPlayer(),playerChanges)
                    || !exact.beforeMovement().followedExactlyBy(exact.afterMovement(),movementChanges)) return false;
            var next = GroundNativePhysicsProfile.classify(sample()).orElse(null);
            if (next==null || !exact.after().equals(speedState(next.state(),player.isSprinting()))
                    || !(directionalQualified
                        ? dev.veltraph.client.navigation.GroundOwnedSprintTransition.profileEvolutionDirectionalQualified(profile.state(),next.state(),
                            exact.travelSpeed(),exact.sprintCalls(),exact.nativeSprintArgument())
                        : dev.veltraph.client.navigation.GroundOwnedSprintTransition.profileEvolution(profile.state(),next.state(),
                            exact.travelSpeed(),exact.sprintCalls(),exact.nativeSprintArgument()))) return false;
            var advanced = new ArrayList<>(revisions);
            advanced.set(0,exact.afterPlayer());advanced.set(1,exact.afterMovement());
            revisions=java.util.Collections.unmodifiableList(advanced);profile=next;sprinting=player.isSprinting();
            consumedOwnedSprint=exact;return true;
        }
        private boolean leaf() {
            if (retired || !client.isSameThread() || client.player != player || client.level != level
                    || client.getConnection() != connection || VeltraphBootstrap.moduleManager() != root
                    || !root.lifecycleCurrent(lifecycle) || client.gui.screen() != null || !player.isAlive()
                    || player.isSpectator() || player.isPassenger() || player.isUsingItem() || player.isScoping()
                    || player.isInWater() || player.isInLava() || player.getAbilities().flying
                    || player.onClimbable() || player.shouldDiscardFriction() || player.isNoGravity() || !noGravityCurrent()
                    || player.getPose() != Pose.STANDING
                    || player.tickCount < initialTick || player.tickCount < lastTick) return false;
            var size = player.getDimensions(Pose.STANDING);
            if (Math.abs(size.width() - .6) > .00001 || Math.abs(size.height() - 1.8) > .00001) return false;
            // This extra flat-only native restriction belongs solely to the vanilla directional opt-in.
            if(directionalFlatVanilla&&!player.onGround())return false;
            for (int i = 0; i < ATTRIBUTES.size(); i++)
                if (player.getAttribute(ATTRIBUTES.get(i)) != attributes.get(i)) return false;
            if (!advanceOwnedNativeRevision() || player.isSprinting() != sprinting) return false;
            if (player.getEffect(MobEffects.JUMP_BOOST) != jump
                    || player.getActiveEffects().size() != effects.size()) return false;
            for (var life : effects) {
                var e = life.instance();
                if (!knownNativeEffect(e) || player.getEffect(e.getEffect()) != e
                        || !GroundCapturedEffects.durationCurrent(initialTick, lastTick, player.tickCount,
                                life.duration(), e.getDuration())) return false;
            }
            if (profile == null || profile.sprinting() != sprinting || !sampleMatchesCapturedProfile()) return false;
            if (!((Object)player instanceof dev.veltraph.client.mixin.FigJumpPowerAccessor jumpPower)) return false;
            if (!((Object)player instanceof dev.veltraph.client.mixin.GroundBlockSpeedFactorAccessor speedFactor)
                    || Float.floatToIntBits(speedFactor.veltraph$profileBlockSpeedFactor()) != Float.floatToIntBits(1F)) return false;
            return Float.floatToIntBits(jumpPower.veltraph$sourceJumpPower(1F))
                    == Float.floatToIntBits(profile.jumpImpulse(1F));
        }
        /** Empty is terminal. Matching a later restored state can never revive this journey.
         * Only an exact stored owned native transition may advance sprint custody; all other mutations retire. */
        public Optional<GroundNativePhysicsProfile> current() {
            if (retired) return Optional.empty();
            try {
                if (!leaf() || !actor.current() || !leaf()) { retired = true; return Optional.empty(); }
                lastTick = player.tickCount;
                return Optional.of(profile);
            } catch (RuntimeException unavailable) { retired = true; return Optional.empty(); }
        }
        boolean capturedClient(Minecraft candidate) { return client == candidate; }
        boolean callbackFreeForecastCurrent(GroundNativePhysicsProfile.State state, int horizon) {
            try {
                if (horizon < 1 || horizon > 64) return false;
                if (!leaf()) { retired = true; return false; }
                if (!profile.state().equals(state)) return false;
                for (var life : effects) {
                    int duration = life.instance().getDuration();
                    if (duration != -1 && duration <= horizon) return false;
                }
                return true;
            } catch (RuntimeException unavailable) { retired = true; return false; }
        }
        boolean matchesForecast(GroundNativePhysicsProfile.State state, int horizon) {
            var observed = current();
            if (horizon < 1 || horizon > 64 || observed.isEmpty() || !observed.get().state().equals(state)) return false;
            for (var life : effects) {
                int duration = life.instance().getDuration();
                if (duration != -1 && duration <= horizon) return false;
            }
            return true;
        }
        /** Advisory duration from a freshly validated observation; never a caller-issued capability. */
        public java.util.OptionalInt remainingJumpEffectTicks() {
            return current().isEmpty() ? java.util.OptionalInt.empty()
                    : java.util.OptionalInt.of(jump == null ? 0 : jump.getDuration());
        }
        public void retire() { retired = true; }
    }
}
