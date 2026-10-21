package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** DATA bookkeeping only. No native Episode/Scope/PoseAttempt/player/Scene/history/field proof fabricated. */
final class NativeBirthResetPoseOrderTest {
 private final Object episode=new Object(),player=new Object(),token=new Object();
 private NativeBirthResetPoseOrder order(){return new NativeBirthResetPoseOrder(episode,player);}
 @Test void singleDataPitchAndOuterResetReturnMatchByIdentity(){var o=order();assertTrue(o.pitchEntered(player,Float.floatToIntBits(0F)));assertTrue(o.pitchReturned(player,token,true));assertTrue(o.resetReturned(episode,player,token,true));}
 @Test void foreignPlayerCannotBorrowOrRetry(){var o=order();assertFalse(o.pitchEntered(new Object(),0));assertFalse(o.pitchEntered(player,0));}
 @Test void onlyExactPositiveZeroRequestedBitsMatch(){assertFalse(order().pitchEntered(player,Float.floatToIntBits(-0F)));assertFalse(order().pitchEntered(player,Float.floatToIntBits(1F)));}
 @Test void reentryConsumesAndRefusesExistingData(){var o=order();assertTrue(o.pitchEntered(player,0));assertFalse(o.pitchEntered(player,0));assertFalse(o.pitchReturned(player,token,true));}
 @Test void missingClaimOrFieldTokenCannotReturn(){assertFalse(order().pitchReturned(player,token,true));var o=order();assertTrue(o.pitchEntered(player,0));assertFalse(o.pitchReturned(player,null,true));}
 @Test void abnormalFieldReturnCannotBecomeNormalLater(){var o=order();assertTrue(o.pitchEntered(player,0));assertFalse(o.pitchReturned(player,token,false));assertFalse(o.pitchReturned(player,token,true));}
 @Test void repeatedFieldReturnRefusesOuterCompletion(){var o=order();assertTrue(o.pitchEntered(player,0));assertTrue(o.pitchReturned(player,token,true));assertFalse(o.pitchReturned(player,token,true));assertFalse(o.resetReturned(episode,player,token,true));}
 @Test void foreignEpisodeTokenOrPlayerCannotCompleteOuter(){var o=order();assertTrue(o.pitchEntered(player,0));assertTrue(o.pitchReturned(player,token,true));assertFalse(o.resetReturned(new Object(),player,token,true));}
 @Test void outerErrorAndReplayAreDenied(){var o=order();assertTrue(o.pitchEntered(player,0));assertTrue(o.pitchReturned(player,token,true));assertFalse(o.resetReturned(episode,player,token,false));assertFalse(o.resetReturned(episode,player,token,true));}
 @Test void expiredDataCannotBeRevived(){var o=order();o.refuse();assertFalse(o.pitchEntered(player,0));}
 @Test void preAddInstalledLeafReflectsOriginalDataStageOnly(){var listener=new Object();var connection=new Object();var packet=new Object();var o=new NativePlayerBirthOrder(packet,listener,connection);assertFalse(o.preAddInstalled(player));assertTrue(o.dispatch(packet,listener,connection,true));assertTrue(o.created(player,true));assertFalse(o.preAddInstalled(player));assertTrue(o.installed(player,true));assertTrue(o.preAddInstalled(player));assertFalse(o.preAddInstalled(new Object()));assertTrue(o.added(player,true));assertFalse(o.preAddInstalled(player));}
}
