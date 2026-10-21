package dev.veltraph.client.service.mc;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** Pure state tests only: no Minecraft, authentic Root source, native ticket, or positive native receipt is fabricated. */
class RootTickIngressClaimCloseTest {
    private final Object client=new Object(),event=new Object(),loop=new Object(),subscriber=new Object(),claim=new Object();
    private RootTickIngressState claimed() {
        var state=new RootTickIngressState(client);
        assertTrue(state.enterEvent(client,event));assertTrue(state.enterLoop(client,event,loop));
        assertTrue(state.enterSubscriber(client,loop,subscriber));assertTrue(state.claim(client,subscriber,claim));
        return state;
    }
    @Test void exactNormalClaimCloseReturnsAcceptanceAndRetiresActiveClaim() {
        var state=claimed();assertTrue(state.leaveClaim(claim,true));
        assertFalse(state.current(client,subscriber,claim));assertTrue(state.live(client));
        state.leaveSubscriber(subscriber,true);state.leaveLoop(loop,true);state.leaveEvent(event,true);
        assertTrue(state.live(client));
    }
    @Test void exceptionalOriginalReturnCannotSealTheClaim() {
        var state=claimed();assertFalse(state.leaveClaim(claim,false));assertFalse(state.live(client));
        assertFalse(state.current(client,subscriber,claim));
    }
    @Test void foreignClaimCloseCannotSealOrReplaceTheOriginal() {
        var state=claimed();assertFalse(state.leaveClaim(new Object(),true));
        assertFalse(state.leaveClaim(claim,true));assertFalse(state.live(client));
    }
    @Test void nestedEventRevocationCannotBecomeNormalClaimAcceptance() {
        var state=claimed();assertFalse(state.enterEvent(client,new Object()));
        assertFalse(state.leaveClaim(claim,true));assertFalse(state.live(client));
    }
    @Test void duplicateNormalCloseIsNotASecondAcceptedReturn() {
        var state=claimed();assertTrue(state.leaveClaim(claim,true));
        assertFalse(state.leaveClaim(claim,true));assertFalse(state.live(client));
    }
    @Test void absentClaimCannotCreateAcceptedClosure() {
        var state=new RootTickIngressState(client);assertFalse(state.leaveClaim(null,true));assertFalse(state.live(client));
    }
    @Test void foreignThreadCannotSealTheOriginalClaim() throws Exception {
        var state=claimed();
        try(var executor=Executors.newSingleThreadExecutor()) {
            assertFalse(executor.submit(()->state.leaveClaim(claim,true)).get());
        }
        assertFalse(state.live(client));assertFalse(state.leaveClaim(claim,true));
    }
    @Test void prematureSubscriberClosePreventsLaterClaimSeal() {
        var state=claimed();state.leaveSubscriber(subscriber,true);
        assertFalse(state.leaveClaim(claim,true));assertFalse(state.live(client));
    }
}
