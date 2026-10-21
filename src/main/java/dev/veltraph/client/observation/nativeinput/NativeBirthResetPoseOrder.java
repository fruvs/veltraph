package dev.veltraph.client.observation.nativeinput;
/** Pure operation lifetime DATA only; no native pose/history/source permission is produced. */
final class NativeBirthResetPoseOrder {
 private final Object episode,player;private boolean attempted,pitchReturned,resetReturned,refused;private Object token;
 NativeBirthResetPoseOrder(Object episode,Object player){this.episode=episode;this.player=player;}
 boolean pitchEntered(Object actualPlayer,int requestedBits){
  if(attempted){refused=true;return false;}attempted=true;
  if(refused||episode==null||player==null||actualPlayer!=player||requestedBits!=Float.floatToIntBits(0.0F)){refused=true;return false;}return true;
 }
 boolean pitchReturned(Object actualPlayer,Object actualToken,boolean normalField){
  if(refused||!attempted||pitchReturned||actualPlayer!=player||actualToken==null||!normalField){refused=true;return false;}token=actualToken;pitchReturned=true;return true;
 }
 boolean resetReturned(Object actualEpisode,Object actualPlayer,Object actualToken,boolean normalReset){
  if(refused||!pitchReturned||resetReturned||actualEpisode!=episode||actualPlayer!=player||actualToken!=token||!normalReset){refused=true;return false;}resetReturned=true;return true;
 }
 void refuse(){refused=true;}
}
