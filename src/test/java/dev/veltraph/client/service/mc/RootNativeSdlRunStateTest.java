package dev.veltraph.client.service.mc;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** Model kernel only. These opaque values do not construct authentic Main/run frames or SDL/manual receipts. */
class RootNativeSdlRunStateTest {
    private final Object client=new Object(),whole=new Object();
    @Test void originalWholeModelClaimIsCurrentOnlyUntilClose() {
        var state=new RootNativeSdlRunState(client);assertTrue(state.claim(client,whole));assertTrue(state.current(client,whole));
        state.close(whole,true);assertFalse(state.current(client,whole));assertTrue(state.live(client));
    }
    @Test void wholeRunCannotBeClaimedTwiceAfterItsNormalReturn() {
        var state=new RootNativeSdlRunState(client);assertTrue(state.claim(client,whole));state.close(whole,true);
        assertFalse(state.claim(client,new Object()));assertFalse(state.live(client));
    }
    @Test void nestedWholeRunRevokesOriginalRatherThanBorrowingIt() {
        var state=new RootNativeSdlRunState(client);assertTrue(state.claim(client,whole));
        assertFalse(state.claim(client,new Object()));assertFalse(state.current(client,whole));
    }
    @Test void foreignClientCannotTakeTheWholeSource() {
        var state=new RootNativeSdlRunState(client);assertFalse(state.claim(new Object(),whole));assertFalse(state.live(client));
    }
    @Test void missingWholeRecordCannotCreateAClaim() {
        var state=new RootNativeSdlRunState(client);assertFalse(state.claim(client,null));assertFalse(state.live(client));
    }
    @Test void exceptionalOriginalReturnRevokesTheRetainedClaim() {
        var state=new RootNativeSdlRunState(client);assertTrue(state.claim(client,whole));state.close(whole,false);
        assertFalse(state.current(client,whole));assertFalse(state.live(client));
    }
    @Test void foreignCloseRevokesInsteadOfReplacingTheOriginal() {
        var state=new RootNativeSdlRunState(client);assertTrue(state.claim(client,whole));state.close(new Object(),true);
        assertFalse(state.current(client,whole));assertFalse(state.live(client));
    }
    @Test void foreignThreadCannotClaimOrReadTheOriginalSource() throws Exception {
        var state=new RootNativeSdlRunState(client);assertTrue(state.claim(client,whole));
        try(var executor=Executors.newSingleThreadExecutor()) {
            assertFalse(executor.submit(()->state.current(client,whole)).get());
            assertFalse(executor.submit(()->state.claim(client,new Object())).get());
        }
        assertFalse(state.current(client,whole));assertFalse(state.live(client));
    }
}
