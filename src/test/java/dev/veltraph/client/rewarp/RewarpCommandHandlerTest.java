package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end (pure) command handler: add/clear/replace/list against a fake capture + chat sink. */
class RewarpCommandHandlerTest {

    private final List<String> chat = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(10_000L);
    private final AtomicLong nextX = new AtomicLong(0);

    private Optional<RewarpPoint> capture() {
        long x = nextX.getAndIncrement();
        return Optional.of(new RewarpPoint("minecraft:overworld", GameArea.GARDEN,
                new BlockPos((int) x, 64, (int) x), 90f, 0f, now.get()));
    }

    private RewarpCommandHandler handler(RewarpService service) {
        return new RewarpCommandHandler(service, this::capture, chat::add, now::get);
    }

    private Optional<RewarpPoint> emptyCapture() {
        return Optional.empty();
    }

    private RewarpCommandHandler handlerWithWarp(RewarpService service, AtomicReference<String> sent) {
        RewarpWarpExecutor warp = new RewarpWarpExecutor(command -> {
            sent.set(command);
            return true;
        });
        return new RewarpCommandHandler(service, this::capture, chat::add, now::get,
                warp, () -> "/warp garden");
    }

    @Test
    void addAppendsAndReportsIndex() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("add");
        h.apply("add");
        assertEquals(2, service.savedCount());
        assertTrue(chat.get(0).contains("Saved rewarp #0"), chat.get(0));
        assertTrue(chat.get(1).contains("Saved rewarp #1"), chat.get(1));
    }

    @Test
    void clearEmptiesStore() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("add");
        h.apply("add");
        chat.clear();
        h.apply("clear");
        assertEquals(0, service.savedCount());
        assertTrue(chat.get(0).contains("Cleared 2"), chat.get(0));
    }

    @Test
    void replaceDefaultReplacesLast() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("add"); // #0 x=0
        h.apply("add"); // #1 x=1
        chat.clear();
        h.apply("replace"); // replaces #1 with x=2
        assertEquals(2, service.savedCount());
        assertEquals(0, service.store().get(0).orElseThrow().pos().x());
        assertEquals(2, service.store().get(1).orElseThrow().pos().x());
        assertTrue(chat.get(0).contains("Replaced rewarp #1"), chat.get(0));
    }

    @Test
    void replaceWithIndexReplacesThatPoint() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("add"); // #0 x=0
        h.apply("add"); // #1 x=1
        chat.clear();
        h.apply("replace 0"); // replaces #0 with x=2
        assertEquals(2, service.store().get(0).orElseThrow().pos().x());
        assertEquals(1, service.store().get(1).orElseThrow().pos().x());
        assertTrue(chat.get(0).contains("Replaced rewarp #0"), chat.get(0));
    }

    @Test
    void replaceOnEmptyReportsError() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("replace");
        assertEquals(0, service.savedCount());
        assertTrue(chat.get(0).contains("No rewarp points to replace"), chat.get(0));
    }

    @Test
    void replaceOutOfRangeReportsError() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("add");
        chat.clear();
        h.apply("replace 5");
        assertTrue(chat.get(0).contains("No rewarp point at index 5"), chat.get(0));
    }

    @Test
    void listEmptyAndPopulated() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = handler(service);
        h.apply("list");
        assertTrue(chat.get(0).contains("No saved rewarp points"), chat.get(0));
        chat.clear();
        h.apply("add");
        h.apply("add");
        chat.clear();
        h.apply("list");
        assertTrue(chat.get(0).contains("Saved rewarp points (2)"), chat.get(0));
        assertTrue(chat.get(1).contains("#0"), chat.get(1));
        assertTrue(chat.get(2).contains("#1"), chat.get(2));
    }

    @Test
    void invalidCommandReportsUsage() {
        RewarpCommandHandler h = handler(new RewarpService());
        h.apply("bogus");
        assertTrue(chat.get(0).contains("Usage"), chat.get(0));
    }

    @Test
    void warpSendsConfiguredCommandAndClearsPending() {
        RewarpService service = new RewarpService();
        service.store().add(new RewarpPoint("minecraft:overworld", GameArea.GARDEN,
                new BlockPos(10, 64, 10), 0f, 0f, now.get()));
        service.triggerReturn("minecraft:overworld", new dev.veltraph.client.world.Vec3(0, 64, 0));
        assertTrue(service.isReturnPending());
        AtomicReference<String> sent = new AtomicReference<>();
        RewarpCommandHandler h = handlerWithWarp(service, sent);
        h.apply("warp");
        assertEquals("warp garden", sent.get());
        assertTrue(chat.get(0).contains("sent /warp garden"), chat.get(0));
        assertTrue(service.pendingTarget().isEmpty());
    }

    @Test
    void addWithNoWorldReportsCaptureError() {
        RewarpService service = new RewarpService();
        RewarpCommandHandler h = new RewarpCommandHandler(service, this::emptyCapture, chat::add, now::get);
        h.apply("add");
        assertEquals(0, service.savedCount());
        assertTrue(chat.get(0).contains("Could not capture location"), chat.get(0));
    }
}
