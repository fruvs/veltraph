package dev.veltraph.client.capture;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import java.nio.file.Path;
import java.util.Locale;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

/** Explicit opt-in local read-only recorder. No clicks, commands to servers, purchases or GUI replacement. */
public final class CaptureGuiTools {
    public static final Path DEFAULT_OUTPUT = Path.of("config/veltraph/captures/gui").toAbsolutePath().normalize();
    private static boolean registered;
    private static CaptureGuiTools observer;
    private CaptureTargetProvenance provenance;
    private final Path output;
    private GuiCaptureSession session;
    private long nextSampleNanos;
    private Object world, player, connection, screen, menu;
    private long worldEpoch, playerEpoch, connectionEpoch, screenEpoch, menuEpoch;
    private CaptureGuiTools(Path output) { this.output = output; }
    public static void register() { register(DEFAULT_OUTPUT); }
    public static synchronized void register(Path output) {
        if (registered) return;
        if (output == null || !output.isAbsolute() || !output.equals(output.normalize())) throw new IllegalArgumentException("Explicit normalized absolute GUI capture path required");
        var recorder = new CaptureGuiTools(output);
        observer = recorder;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("vcapture")
            .then(literal("gui")
                .executes(c -> recorder.status(c.getSource()))
                .then(literal("status").executes(c -> recorder.status(c.getSource())))
                .then(literal("start").then(argument("label", StringArgumentType.word()).executes(c -> recorder.start(c.getSource(), StringArgumentType.getString(c, "label")))))
                .then(literal("stop").executes(c -> recorder.stop(c.getSource()))))));
        ClientTickEvents.END_CLIENT_TICK.register(recorder::tick);
        registered = true;
    }
    private int status(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(session == null ? "GUI capture idle. Use /vcapture gui start chocolate" : "GUI capture " + (session.active() ? "recording" : "stopped (" + session.stopReason() + ")") + ": " + session.snapshots() + " snapshots; " + session.directory()));
        return 1;
    }
    private int start(FabricClientCommandSource source, String label) {
        if (session != null && session.active()) { source.sendError(Component.literal("GUI capture already recording; use /vcapture gui stop first")); return 0; }
        try {
            session = new GuiCaptureSession(output, label, System.currentTimeMillis());
            world = player = connection = screen = menu = null;
            worldEpoch = playerEpoch = connectionEpoch = screenEpoch = menuEpoch = 0;
            nextSampleNanos = 0;
            provenance = new CaptureTargetProvenance();
            source.sendFeedback(Component.literal("GUI capture started. Open and navigate your menus normally, then /vcapture gui stop. Saving to " + session.directory()));
            return 1;
        } catch (IllegalArgumentException invalid) { source.sendError(Component.literal(invalid.getMessage())); return 0; }
        catch (java.io.IOException failure) { source.sendError(Component.literal("GUI capture could not start: output disk/path unavailable or unsafe")); return 0; }
    }
    private int stop(FabricClientCommandSource source) {
        if (session == null || !session.active()) return status(source);
        try { session.stop("user_stop", System.currentTimeMillis()); return status(source); }
        catch (java.io.IOException failure) { source.sendError(Component.literal("GUI capture stopped; manifest update failed. Existing snapshot files remain at " + session.directory())); return 0; }
    }
    private void tick(Minecraft client) {
        if (session == null || !session.active()) return;
        long now = System.currentTimeMillis();
        try {
            if (session.expired(now)) { session.stop("duration_limit", now); return; }
            synchronizeContext(client);
            observeScreenBoundary(client);
            Object currentScreen = client.gui.screen();
            long nano = System.nanoTime();
            if (nano < nextSampleNanos) return;
            nextSampleNanos = nano + 250_000_000L;
            if (client.player == null || client.level == null || !(currentScreen instanceof AbstractContainerScreen<?> nativeScreen)) return;
            var nativeMenu = nativeScreen.getMenu();
            String title = visibleText(nativeScreen.getTitle().getString());
            if (nativeMenu != client.player.containerMenu || nativeMenu instanceof InventoryMenu || blockedTitle(title)) return;
            // Inspect containers before reading any stack; client-side ender storage is also excluded.
            for (var slot : nativeMenu.slots) if (slot.container instanceof PlayerEnderChestContainer) return;
            if (nativeMenu.slots.size() > 256) { session.stop("menu_slot_limit", now); return; }
            JsonObject content = new JsonObject();
            content.addProperty("schemaVersion", 2);
            content.addProperty("title", title);
            try { content.addProperty("menuType", BuiltInRegistries.MENU.getKey(nativeMenu.getType()).toString()); }
            catch (RuntimeException unsupported) { content.addProperty("menuType", "unavailable"); }
            content.addProperty("containerId", nativeMenu.containerId);
            content.addProperty("revision", nativeMenu.getStateId());
            content.addProperty("worldEpoch", worldEpoch);
            content.addProperty("playerContextEpoch", playerEpoch);
            content.addProperty("connectionContextEpoch", connectionEpoch);
            content.addProperty("screenEpoch", screenEpoch);
            content.addProperty("menuEpoch", menuEpoch);
            content.add("targetProvenance", provenance.menuEvidence());
            var slots = new JsonArray();
            int excluded = 0;
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (var slot : nativeMenu.slots) {
                if (slot.container instanceof Inventory || slot.container == client.player.getInventory()) { excluded++; continue; }
                var item = slot.getItem();
                var row = new JsonObject();
                row.addProperty("slot", slot.index);
                row.addProperty("containerSlot", slot.getContainerSlot());
                row.addProperty("itemId", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                row.addProperty("count", item.getCount());
                row.addProperty("visibleName", item.isEmpty() ? "" : visibleText(item.getHoverName().getString()));
                var lore = new JsonArray();
                var nativeLore = item.get(DataComponents.LORE);
                if (nativeLore != null) for (var line : nativeLore.lines()) lore.add(visibleText(line.getString()));
                row.add("lore", lore);
                var persistent = DataComponentMap.builder();
                var transientTypes = new JsonArray();
                for (var component : item.getComponents()) {
                    if (component.type().isTransient()) transientTypes.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString());
                    else addComponent(persistent, component);
                }
                var encoded = DataComponentMap.CODEC.encodeStart(ops, persistent.build()).result();
                row.addProperty("componentsCodecStatus", encoded.isPresent() ? "complete_persistent_effective_map" : "unsupported");
                if (encoded.isPresent()) row.add("persistentEffectiveComponents", encoded.get());
                row.add("excludedTransientComponentTypes", transientTypes);
                slots.add(row);
            }
            content.addProperty("excludedPlayerInventorySlots", excluded);
            content.add("slots", slots);
            var timing = new JsonObject();
            timing.addProperty("observedAtUtc", java.time.Instant.ofEpochMilli(now).toString());
            timing.addProperty("observedAtEpochMillis", now);
            timing.addProperty("worldGameTime", client.level.getGameTime());
            session.record(content, timing, now);
        } catch (RuntimeException | java.io.IOException failure) {
            try { session.stop("capture_error", now); } catch (java.io.IOException ignored) { }
            client.gui.hud.getChat().addClientSystemMessage(Component.literal("GUI capture stopped after an observation/write error. Existing files remain at " + session.directory()));
        }
    }
    /** Observer entry points do nothing while the explicit local session is inactive. */
    public static long nativeAction(String kind, net.minecraft.world.phys.BlockHitResult block, net.minecraft.world.entity.Entity entity, String hand, net.minecraft.world.phys.EntityHitResult entityHit) {
        return observeAction(kind, block == null ? null : block.getBlockPos(), block == null ? null : block.getDirection(), block, entity, hand, entityHit);
    }
    public static long nativeBlockBreak(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
        return observeAction("start_destroy_block", pos, face, null, null, "not_supplied_by_native_api", null);
    }
    private static long observeAction(String kind, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face, net.minecraft.world.phys.BlockHitResult block, net.minecraft.world.entity.Entity entity, String hand, net.minecraft.world.phys.EntityHitResult entityHit) {
        var recorder = observer;
        if (recorder == null || recorder.session == null || !recorder.session.active()) return 0;
        var client = Minecraft.getInstance();
        try {
            recorder.synchronizeContext(client);
            recorder.observeScreenBoundary(client);
            if (client.level == null || client.player == null) return 0;
            var target = new JsonObject(); target.addProperty("kind", "unknown");
            if (pos != null) {
                if (client.level.hasChunkAt(pos)) {
                    var state = client.level.getBlockState(pos);
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (!id.contains("ender_chest")) {
                        target.addProperty("kind", "block"); target.addProperty("blockId", id);
                        target.addProperty("x", pos.getX()); target.addProperty("y", pos.getY()); target.addProperty("z", pos.getZ());
                        target.addProperty("face", face.name());
                        if (block != null) { target.addProperty("inside", block.isInside()); target.add("actionHitPosition", vector(block.getLocation())); }
                        else target.addProperty("actionHitPositionStatus", "not_supplied_by_native_api");
                        target.addProperty("blockState", state.toString());
                    } else target.addProperty("reason", "excluded_storage_target");
                } else target.addProperty("reason", "target_chunk_not_loaded");
            } else if (entity != null && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                target.addProperty("kind", "entity"); target.addProperty("entityType", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                target.addProperty("entityRuntimeId", entity.getId()); target.add("entityPosition", vector(entity.position()));
                if (entityHit != null && entityHit.getEntity() == entity) target.add("actionHitPosition", vector(entityHit.getLocation()));
            }
            // Current ray is supporting gameplay evidence, never substituted for actual action arguments.
            var ray = new JsonObject();
            if (client.hitResult != null) { ray.addProperty("kind", client.hitResult.getType().name()); ray.add("hitPosition", vector(client.hitResult.getLocation())); }
            target.add("observedRay", ray);
            var pose = new JsonObject(); pose.add("position", vector(client.player.position())); pose.add("eyePosition", vector(client.player.getEyePosition()));
            pose.addProperty("yaw", client.player.getYRot()); pose.addProperty("pitch", client.player.getXRot());
            target.add("playerPose", pose); target.addProperty("hand", hand);
            long now = System.currentTimeMillis();
            var event = recorder.provenance.begin(kind, target, recorder.context(), now, client.gui.screen() == null);
            event.addProperty("screenEpoch", recorder.screenEpoch); event.addProperty("menuEpoch", recorder.menuEpoch);
            recorder.session.recordEvent(event, now);
            return event.get("actionSequence").getAsLong();
        } catch (RuntimeException | java.io.IOException failure) { recorder.observationFailed(client); return 0; }
    }
    public static void nativeActionReturned(long sequence, Boolean consumed) {
        var recorder = observer;
        if (sequence == 0 || recorder == null || recorder.session == null || !recorder.session.active()) return;
        try {
            var client = Minecraft.getInstance(); recorder.synchronizeContext(client);
            long now = System.currentTimeMillis(); recorder.session.recordEvent(recorder.provenance.finish(sequence, consumed, now), now);
        } catch (RuntimeException | java.io.IOException failure) { recorder.observationFailed(Minecraft.getInstance()); }
    }
    public static void nativeMenuInput() {
        var recorder = observer;
        if (recorder == null || recorder.session == null || !recorder.session.active()) return;
        try { long now = System.currentTimeMillis(); recorder.session.recordEvent(recorder.provenance.menuInput(now), now); }
        catch (RuntimeException | java.io.IOException failure) { recorder.observationFailed(Minecraft.getInstance()); }
    }
    public static void nativeScreenChanged() {
        var recorder = observer;
        if (recorder == null || recorder.session == null || !recorder.session.active()) return;
        try { var client = Minecraft.getInstance(); recorder.synchronizeContext(client); recorder.observeScreenBoundary(client); }
        catch (RuntimeException | java.io.IOException failure) { recorder.observationFailed(Minecraft.getInstance()); }
    }
    private void synchronizeContext(Minecraft client) {
        boolean changed = false;
        if (world != client.level) { world = client.level; worldEpoch++; changed = true; }
        if (player != client.player) { player = client.player; playerEpoch++; changed = true; }
        // Object identity only; connection contents and server address are never read.
        if (connection != client.getConnection()) { connection = client.getConnection(); connectionEpoch++; changed = true; }
        if (changed) provenance.contextChanged();
    }
    private void observeScreenBoundary(Minecraft client) throws java.io.IOException {
        Object currentScreen = client.gui.screen();
        Object currentMenu = currentScreen instanceof AbstractContainerScreen<?> nativeScreen ? nativeScreen.getMenu() : null;
        if (screen == currentScreen && menu == currentMenu) return;
        screen = currentScreen; screenEpoch++; menu = currentMenu; menuEpoch++;
        boolean allowed = currentScreen instanceof AbstractContainerScreen<?> nativeScreen && client.player != null
            && nativeScreen.getMenu() == client.player.containerMenu && !(nativeScreen.getMenu() instanceof InventoryMenu)
            && !blockedTitle(visibleText(nativeScreen.getTitle().getString()))
            && nativeScreen.getMenu().slots.stream().noneMatch(s -> s.container instanceof PlayerEnderChestContainer);
        long now = System.currentTimeMillis();
        var event = provenance.screenBoundary(allowed, context(), now);
        event.addProperty("screenEpoch", screenEpoch); event.addProperty("menuEpoch", menuEpoch);
        if (allowed) { var nativeMenu = ((AbstractContainerScreen<?>)currentScreen).getMenu(); event.addProperty("containerId", nativeMenu.containerId); event.addProperty("revisionAtOpen", nativeMenu.getStateId()); }
        session.recordEvent(event, now);
    }
    private JsonObject context() {
        var result = new JsonObject(); result.addProperty("worldEpoch", worldEpoch); result.addProperty("playerContextEpoch", playerEpoch);
        result.addProperty("connectionContextEpoch", connectionEpoch); return result;
    }
    private static JsonObject vector(net.minecraft.world.phys.Vec3 value) {
        var result = new JsonObject(); result.addProperty("x", value.x); result.addProperty("y", value.y); result.addProperty("z", value.z); return result;
    }
    private void observationFailed(Minecraft client) {
        try { session.stop("capture_error", System.currentTimeMillis()); } catch (java.io.IOException ignored) { }
        client.gui.hud.getChat().addClientSystemMessage(Component.literal("GUI action capture stopped after an observation/write error. Existing files remain at " + session.directory()));
    }
    private static <T> void addComponent(DataComponentMap.Builder builder, net.minecraft.core.component.TypedDataComponent<T> value) { builder.set(value.type(), value.value()); }
    static String visibleText(String value) { return value.replaceAll("§[0-9a-fk-orA-FK-OR]", ""); }
    static boolean blockedTitle(String title) { return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").contains("enderchest"); }
}
