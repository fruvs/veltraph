package dev.veltraph.client.service.mc;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kernel/model scope only: no native tickets, Root source receipts, or positive native ingress are fabricated. */
class RootTickIngressStateTest {
    private final Object client = new Object(), event = new Object(), loop = new Object(), subscriber = new Object(), claim = new Object();
    private RootTickIngressState beforeClaim() {
        var state = new RootTickIngressState(client);
        assertTrue(state.enterEvent(client, event));
        assertTrue(state.enterLoop(client, event, loop));
        assertTrue(state.enterSubscriber(client, loop, subscriber));
        return state;
    }
    @Test void exactClosedModelSequenceEndsWithoutRetainingClaim() {
        var state = beforeClaim();
        assertTrue(state.claim(client, subscriber, claim));
        assertTrue(state.current(client, subscriber, claim));
        state.leaveClaim(claim, true); assertFalse(state.current(client, subscriber, claim));
        state.leaveSubscriber(subscriber, true); state.leaveLoop(loop, true); state.leaveEvent(event, true);
        assertTrue(state.live(client));
    }
    @Test void anotherSubscriberMayPrecedeTheRootClaim() {
        var state = new RootTickIngressState(client); var first = new Object();
        assertTrue(state.enterEvent(client, event)); assertTrue(state.enterLoop(client, event, loop));
        assertTrue(state.enterSubscriber(client, loop, first)); state.leaveSubscriber(first, true);
        assertTrue(state.enterSubscriber(client, loop, subscriber)); assertTrue(state.claim(client, subscriber, claim));
    }
    @Test void secondLoopRevokesExistingClaim() {
        var state = beforeClaim(); assertTrue(state.claim(client, subscriber, claim));
        assertFalse(state.enterLoop(client, event, new Object())); assertFalse(state.current(client, subscriber, claim));
    }
    @Test void nestedSubscriberRevokesExistingClaim() {
        var state = beforeClaim(); assertTrue(state.claim(client, subscriber, claim));
        assertFalse(state.enterSubscriber(client, loop, new Object())); assertFalse(state.current(client, subscriber, claim));
    }
    @Test void duplicateClaimRemainsRefusedAfterNormalClose() {
        var state = beforeClaim(); assertTrue(state.claim(client, subscriber, claim)); state.leaveClaim(claim, true);
        assertFalse(state.claim(client, subscriber, new Object())); assertFalse(state.live(client));
    }
    @Test void eventCannotBeReopenedAfterNormalClose() {
        var state = new RootTickIngressState(client); assertTrue(state.enterEvent(client, event)); state.leaveEvent(event, true);
        assertFalse(state.enterEvent(client, new Object())); assertFalse(state.live(client));
    }
    @Test void loopWithoutEventIsRefused() {
        var state = new RootTickIngressState(client); assertFalse(state.enterLoop(client, null, loop)); assertFalse(state.live(client));
    }
    @Test void subscriberWithoutLoopIsRefused() {
        var state = new RootTickIngressState(client); assertFalse(state.enterSubscriber(client, null, subscriber)); assertFalse(state.live(client));
    }
    @Test void claimWithoutSubscriberIsRefused() {
        var state = new RootTickIngressState(client); assertFalse(state.claim(client, null, claim)); assertFalse(state.live(client));
    }
    @Test void foreignClientRevokesInsteadOfBorrowingTheOriginal() {
        var state = beforeClaim(); assertFalse(state.claim(new Object(), subscriber, claim)); assertFalse(state.live(client));
    }
    @Test void exceptionalClaimCloseAndIncompleteSubscriberCloseRevoke() {
        var exceptional = beforeClaim(); assertTrue(exceptional.claim(client, subscriber, claim)); exceptional.leaveClaim(claim, false);
        assertFalse(exceptional.live(client));
        var incomplete = beforeClaim(); assertTrue(incomplete.claim(client, subscriber, claim)); incomplete.leaveSubscriber(subscriber, true);
        assertFalse(incomplete.live(client));
    }
    @Test void foreignThreadCannotReadAnActiveModelClaim() throws Exception {
        var state = beforeClaim(); assertTrue(state.claim(client, subscriber, claim));
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertFalse(executor.submit(() -> state.current(client, subscriber, claim)).get());
        }
        assertTrue(state.current(client, subscriber, claim));
    }
}
