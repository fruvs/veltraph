package dev.veltraph.client.hydra.response;
import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.hydra.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact recovered temporary-disable invocations; no staff identity or native execution inferred. */
final class HydraTemporaryDisableSourceTest {
    private static final HydraResponseTrigger FLOOR=HydraResponseTrigger.fromEvent(HydraEventTestFixtures.floorChange(false));
    private MapConfigService config(boolean parity,HydraOriginalPolicy policy,long after){
        var c=new MapConfigService();c.set(HydraConfig.HYDRA_PARITY_MODE,parity);c.set(HydraConfig.HYDRA_ORIGINAL_POLICY,policy);
        c.set(HydraConfig.STAFF_RESPONSE_POLICY,HydraResponsePolicy.OFF);c.set(HydraConfig.AFTER_RESPONDING_MS,after);
        c.set(HydraConfig.HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS,false);c.set(HydraConfig.MOVEMENT_REACTIONS_ENABLED,false);
        c.set(HydraConfig.AVOID_EVACUATION_ENABLED,false);c.set(HydraConfig.AFTER_DISCONNECT_ENABLED,false);
        return c;
    }
    private HydraResponseHandler ignore(long now,long after){var h=new HydraResponseHandler(()->0);assertFalse(h.onCheck(FLOOR,0,0,now,HydraResponseSettings.fromConfig(config(true,HydraOriginalPolicy.IGNORE,after))));assertTrue(h.tick(now).isEmpty());assertEquals(0,h.sourceDisableInvocations());return h;}
    private void quiet(HydraResponseHandler h,long now,int invocation){assertTrue(h.tick(now).isEmpty());assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED,h.state());assertEquals(invocation,h.sourceDisableInvocations());}
    @Test void longWallClockLeapCannotReplaceSixHundredSourceInvocations(){var h=ignore(1000,1);for(int i=1;i<600;i++)quiet(h,Long.MAX_VALUE,i);assertTrue(h.tick(Long.MAX_VALUE).isEmpty());assertEquals(HydraResponseState.LISTENING,h.state());assertEquals(0,h.sourceDisableInvocations());}
    @Test void repeatedClockValueStillAdvancesTheExactInvocationCounter(){var h=ignore(1000,30000);for(int i=1;i<600;i++)quiet(h,1000,i);h.tick(1000);assertEquals(HydraResponseState.LISTENING,h.state());assertFalse(h.sourceTemporaryDisable());}
    @Test void clockRollbackCannotShortenTheSourceQuietPhase(){var h=ignore(1000,1);for(int i=1;i<600;i++)quiet(h,1000-i,i);h.tick(-1000);assertEquals(HydraResponseState.LISTENING,h.state());}
    @Test void entryInvocationDoesNotConsumeTheRecoveredDisabledCounter(){var h=new HydraResponseHandler(()->0);h.onCheck(FLOOR,0,0,1000,HydraResponseSettings.fromConfig(config(true,HydraOriginalPolicy.IGNORE,1)));assertEquals(0,h.sourceDisableInvocations());h.tick(1000);assertEquals(0,h.sourceDisableInvocations());quiet(h,1000,1);}
    @Test void checksWhileQuietDoNotResetOrShortenTheCounter(){var h=ignore(1000,1);var s=HydraResponseSettings.fromConfig(config(true,HydraOriginalPolicy.REACT,1));for(int i=1;i<600;i++){assertFalse(h.onCheck(FLOOR,0,0,1000,s));quiet(h,1000,i);}h.tick(1000);assertEquals(HydraResponseState.LISTENING,h.state());}
    @Test void resetClearsTheOldPhaseAndDoesNotRearmItself(){var h=ignore(1000,1);quiet(h,1000,1);h.reset();assertEquals(HydraResponseState.INACTIVE,h.state());assertFalse(h.sourceTemporaryDisable());assertEquals(0,h.sourceDisableInvocations());for(int i=0;i<610;i++)assertTrue(h.tick(Long.MAX_VALUE).isEmpty());assertEquals(HydraResponseState.INACTIVE,h.state());h.arm();assertEquals(HydraResponseState.LISTENING,h.state());}
    @Test void parityAfterRespondingSettingDoesNotChangeTheFixedQuietLength(){for(long after:new long[]{0,1,30000,Long.MAX_VALUE}){var h=ignore(1000,after);for(int i=1;i<600;i++)quiet(h,1000,i);h.tick(1000);assertEquals(HydraResponseState.LISTENING,h.state());assertEquals(0,h.sourceDisableInvocations());}}
    @Test void nonParityIgnoreKeepsExistingWallClockBehavior(){var h=new HydraResponseHandler(()->0);var s=HydraResponseSettings.fromConfig(config(false,HydraOriginalPolicy.IGNORE,100));var staff=HydraResponseTrigger.fromEvent(HydraEventTestFixtures.staffNearby("Private actor",5,HydraSeverity.POSSIBLE,false));assertFalse(h.onCheck(staff,0,0,1000,s));h.tick(1000);assertFalse(h.sourceTemporaryDisable());h.tick(1099);assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED,h.state());h.tick(1100);assertEquals(HydraResponseState.LISTENING,h.state());}
    @Test void stopAllIsEmittedOnceAndDoesNotRepeatDuringTheQuietPhase(){var h=new HydraResponseHandler(()->0);assertTrue(h.onCheck(FLOOR,0,0,1000,HydraResponseSettings.fromConfig(config(true,HydraOriginalPolicy.STOP_ALL,1))));var actions=h.tick(1000);assertEquals(1,actions.size());assertEquals(HydraResponseAction.Kind.STOP_MACRO,actions.getFirst().kind());assertEquals(0,h.sourceDisableInvocations());for(int i=1;i<600;i++)quiet(h,1000,i);assertTrue(h.tick(1000).isEmpty());assertEquals(HydraResponseState.LISTENING,h.state());}
    private HydraTick tick(MapConfigService c,long now,boolean floor){return new HydraTick(HydraSettings.fromConfig(c),Whitelist.parse(""),now,List.of(),true,true,GameArea.GARDEN,0,false,0,false,GameModeKind.SURVIVAL,null,floor,0,0,false);}
    @Test void disablingParityRevokesAnAlreadyEnteredQuietPhaseThroughTheActualOrchestrator(){var c=config(true,HydraOriginalPolicy.IGNORE,1);c.set(HydraConfig.STAFF_ALERT_ENABLED,true);c.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO,false);var feature=new HydraAntiStaff(c,line->{},()->{},()->{},HydraAlertSink.NONE);feature.tick(tick(c,1000,true));assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED,feature.responseState());c.set(HydraConfig.HYDRA_PARITY_MODE,false);feature.tick(tick(c,1001,false));assertEquals(HydraResponseState.LISTENING,feature.responseState());}
    @Test void watcherDisableCancelsQuietStateAndAllPendingActions(){var c=config(true,HydraOriginalPolicy.IGNORE,1);c.set(HydraConfig.STAFF_ALERT_ENABLED,true);c.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO,false);var feature=new HydraAntiStaff(c,line->{},()->{},()->{},HydraAlertSink.NONE);feature.tick(tick(c,1000,true));assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED,feature.responseState());c.set(HydraConfig.STAFF_ALERT_ENABLED,false);feature.tick(tick(c,1001,false));assertEquals(HydraResponseState.INACTIVE,feature.responseState());}
}
