package dev.veltraph.client.hydra;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.hydra.mc.McHydraResponder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleHydraReadonlyTest {
 @Test void privateIdentityReadNeverInvokesUntrustedResponder(){var responder=new HydraResponder(){public void sendChat(String x){throw new AssertionError();}public void sendCommand(String x){throw new AssertionError();}public void fixAngle(double y,double p){throw new AssertionError();}public boolean hasActiveLocalReaction(){throw new AssertionError("interface callback forbidden");}};var feature=new HydraAntiStaff(new MapConfigService(),s->{},()->{},()->{},HydraAlertSink.NONE,responder,()->0);assertSame(responder,feature.nativeResponderIdentity());assertTrue(feature.nativeIdleCurrent(responder));assertFalse(feature.nativeIdleCurrent(new Object()));assertFalse(feature.nativeIdleCurrent(null));assertFalse((Object)responder instanceof McHydraResponder);}
 @Test void actualNativeIdleReadNeverInvokesAnyExecutionCallback(){var responder=new McHydraResponder(s->{throw new AssertionError();},s->{throw new AssertionError();},null,null,()->{throw new AssertionError();},()->{throw new AssertionError();});assertTrue(responder.nativeIdleCurrent());}
}
