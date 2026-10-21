package dev.veltraph.client.service.mc;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual JDK kernel retirement models only; no native Root ticket, frame or receipt is fabricated. */
class RootTickNormalClaimRetirementTest {
    private final Object client=new Object(),event=new Object(),loop=new Object(),subscriber=new Object(),claim=new Object();
    private RootTickIngressState closedClaim(){
        var state=new RootTickIngressState(client);
        assertTrue(state.enterEvent(client,event));assertTrue(state.enterLoop(client,event,loop));
        assertTrue(state.enterSubscriber(client,loop,subscriber));assertTrue(state.claim(client,subscriber,claim));
        assertTrue(state.leaveClaim(claim,true));state.leaveSubscriber(subscriber,true);
        state.leaveLoop(loop,true);state.leaveEvent(event,true);return state;
    }
    @Test void normalSdkRetirementExpiresEntryAndPreservesExactNormalClaim(){
        var state=closedClaim();state.closeTick(true);
        assertFalse(state.live(client));assertFalse(state.current(client,subscriber,claim));
        assertTrue(state.normalClaimReturned(client,claim));
    }
    @Test void abnormalSdkRetirementInvalidatesPreviouslyNormalRootClaim(){
        var state=closedClaim();assertTrue(state.normalClaimReturned(client,claim));state.closeTick(false);
        assertFalse(state.live(client));assertFalse(state.normalClaimReturned(client,claim));
    }
    @Test void reentryBeforeRetirementRevokesHistoricalClaim(){
        var state=closedClaim();assertFalse(state.enterEvent(client,new Object()));state.closeTick(true);
        assertFalse(state.normalClaimReturned(client,claim));
    }
    @Test void duplicateSdkRetirementRevokesRatherThanReopening(){
        var state=closedClaim();state.closeTick(true);assertTrue(state.normalClaimReturned(client,claim));
        state.closeTick(true);assertFalse(state.normalClaimReturned(client,claim));assertFalse(state.live(client));
    }
    @Test void unfinishedNativeScopesRefuseNormalRetirement(){
        var state=new RootTickIngressState(client);assertTrue(state.enterEvent(client,event));assertTrue(state.enterLoop(client,event,loop));
        assertTrue(state.enterSubscriber(client,loop,subscriber));assertTrue(state.claim(client,subscriber,claim));assertTrue(state.leaveClaim(claim,true));
        state.closeTick(true);assertFalse(state.normalClaimReturned(client,claim));
    }
    @Test void historicalFactRequiresOriginalClientAndClaimIdentity(){
        var state=closedClaim();state.closeTick(true);
        assertFalse(state.normalClaimReturned(new Object(),claim));assertFalse(state.normalClaimReturned(client,new Object()));
        assertFalse(state.normalClaimReturned(client,null));assertTrue(state.normalClaimReturned(client,claim));
    }
    @Test void expiredHistoryCannotAdmitNewClaim(){
        var state=closedClaim();state.closeTick(true);assertFalse(state.claim(client,subscriber,new Object()));
        assertFalse(state.live(client));assertFalse(state.normalClaimReturned(client,claim));
    }
    @Test void foreignThreadCannotBorrowTheHistoricalReceipt()throws Exception{
        var state=closedClaim();state.closeTick(true);
        try(var worker=Executors.newSingleThreadExecutor()){
            assertFalse(worker.submit(()->state.normalClaimReturned(client,claim)).get());
        }
        assertTrue(state.normalClaimReturned(client,claim));
    }
}
