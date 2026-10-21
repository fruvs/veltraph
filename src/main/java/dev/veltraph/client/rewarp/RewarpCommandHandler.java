package dev.veltraph.client.rewarp;

import dev.veltraph.client.command.RewarpCommand;
import dev.veltraph.client.log.VeltraphLog;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Applies a parsed {@link RewarpCommand} against a {@link RewarpService}, emitting local-chat feedback
 * and log lines. Minecraft-free: capture is injected as a {@code Supplier<Optional<RewarpPoint>>} (the
 * live helper) and chat as a {@code Consumer<String>}, so add/clear/replace/list are fully unit-testable.
 *
 * <p>Logs add/clear/replace/list events via {@link VeltraphLog} per the project conventions.
 */
public final class RewarpCommandHandler {

    private static final String TAG = "Rewarp";

    private final RewarpService service;
    private final Supplier<Optional<RewarpPoint>> capture;
    private final Consumer<String> chat;
    private final LongSupplier clockMillis;
    private final RewarpWarpExecutor warpExecutor;
    private final Supplier<String> rewarpCommand;

    public RewarpCommandHandler(RewarpService service,
                                Supplier<Optional<RewarpPoint>> capture,
                                Consumer<String> chat,
                                LongSupplier clockMillis) {
        this(service, capture, chat, clockMillis, null, () -> RewarpWarpExecutor.DEFAULT_COMMAND);
    }

    public RewarpCommandHandler(RewarpService service,
                                Supplier<Optional<RewarpPoint>> capture,
                                Consumer<String> chat,
                                LongSupplier clockMillis,
                                RewarpWarpExecutor warpExecutor,
                                Supplier<String> rewarpCommand) {
        this.service = service;
        this.capture = capture;
        this.chat = chat;
        this.clockMillis = clockMillis;
        this.warpExecutor = warpExecutor;
        this.rewarpCommand = rewarpCommand == null ? () -> RewarpWarpExecutor.DEFAULT_COMMAND : rewarpCommand;
    }

    /** Parse a raw {@code rewarp} argument tail and apply it. */
    public void apply(String argTail) {
        apply(RewarpCommand.parse(argTail));
    }

    /** Apply an already-parsed command. */
    public void apply(RewarpCommand command) {
        if (command == null || !command.valid()) {
            emit("[Veltraph] " + (command == null ? RewarpCommand.USAGE : command.error()));
            return;
        }
        switch (command.action()) {
            case ADD -> doAdd();
            case CLEAR -> doClear();
            case REPLACE -> doReplace(command.index());
            case LIST -> doList();
            case WARP -> doWarp();
        }
    }

    private void doAdd() {
        Optional<RewarpPoint> captured = capture.get();
        if (captured.isEmpty()) {
            emit("[Veltraph] Could not capture location — join a world first.");
            return;
        }
        RewarpPoint point = captured.get();
        service.store().add(point);
        VeltraphLog.basic(TAG, "added rewarp #" + (service.savedCount() - 1) + ": " + point.display(now()));
        emit("[Veltraph] Saved rewarp #" + (service.savedCount() - 1) + ": " + point.display(now()));
    }

    private void doClear() {
        int had = service.savedCount();
        service.store().clear();
        service.clearPending();
        VeltraphLog.basic(TAG, "cleared " + had + " rewarp point(s)");
        emit("[Veltraph] Cleared " + had + " rewarp point(s).");
    }

    private void doReplace(OptionalInt index) {
        if (service.savedCount() == 0) {
            emit("[Veltraph] No rewarp points to replace. Use 'rewarp add' first.");
            return;
        }
        Optional<RewarpPoint> captured = capture.get();
        if (captured.isEmpty()) {
            emit("[Veltraph] Could not capture location — join a world first.");
            return;
        }
        RewarpPoint point = captured.get();
        int target = index.orElse(service.savedCount() - 1);
        try {
            service.store().replace(target, point);
        } catch (IndexOutOfBoundsException e) {
            emit("[Veltraph] No rewarp point at index " + target + " (have "
                    + service.savedCount() + ").");
            return;
        }
        VeltraphLog.basic(TAG, "replaced rewarp #" + target + ": " + point.display(now()));
        emit("[Veltraph] Replaced rewarp #" + target + ": " + point.display(now()));
    }

    private void doList() {
        List<RewarpPoint> points = service.store().list();
        VeltraphLog.basic(TAG, "list (" + points.size() + " point(s))");
        if (points.isEmpty()) {
            emit("[Veltraph] No saved rewarp points.");
            return;
        }
        emit("[Veltraph] Saved rewarp points (" + points.size() + "):");
        long now = now();
        for (int i = 0; i < points.size(); i++) {
            emit("[Veltraph]   #" + i + " " + points.get(i).display(now));
        }
    }

    private void doWarp() {
        if (warpExecutor == null) {
            emit("[Veltraph] Rewarp command sender is not available.");
            return;
        }
        String command = RewarpWarpExecutor.normalizeCommand(rewarpCommand.get());
        boolean sent = warpExecutor.execute(command);
        if (sent) {
            service.clearPending();
            emit("[Veltraph] Rewarp: sent /" + command + ".");
        } else {
            emit("[Veltraph] Rewarp: could not send /" + command + " (not connected).");
        }
    }

    private long now() {
        return clockMillis.getAsLong();
    }

    private void emit(String line) {
        if (chat != null) {
            chat.accept(line);
        }
    }
}
