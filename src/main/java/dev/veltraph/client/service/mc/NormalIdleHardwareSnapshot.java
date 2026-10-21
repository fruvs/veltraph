package dev.veltraph.client.service.mc;
import net.minecraft.client.*;
/** Passive private native observation only; it never certifies normal completion or admits effects. */
public final class NormalIdleHardwareSnapshot {
 private final Minecraft client;private final Object options,mouse,player;private final KeyMapping[] keys;private final Object[] keySnapshots;private final Object mouseSnapshot;private final float yaw,pitch;private final boolean released;private final boolean[] down;private String firstFailure="NONE",lastFailure="NONE";
 public String cachedCurrentDiagnostic(){return "first="+firstFailure+";last="+lastFailure;}
 private boolean failed(String reason){if(firstFailure.equals("NONE"))firstFailure=reason;lastFailure=reason;return false;}
 private NormalIdleHardwareSnapshot(Minecraft c,KeyMapping[] keys,Object[] snapshots,Object mouseSnapshot,boolean released){client=c;this.released=released;down=new boolean[9];for(int i=0;i<9;i++)down[i]=keys[i].isDown();options=c.options;mouse=c.mouseHandler;player=c.player;this.keys=keys;keySnapshots=snapshots;this.mouseSnapshot=mouseSnapshot;yaw=c.player.getYRot();pitch=c.player.getXRot();}
 private static KeyMapping[] keys(Minecraft c){return new KeyMapping[]{c.options.keyUp,c.options.keyDown,c.options.keyLeft,c.options.keyRight,c.options.keyJump,c.options.keyShift,c.options.keySprint,c.options.keyAttack,c.options.keyUse};}
 public static NormalIdleHardwareSnapshot capture(Minecraft c){return capture(c,true);}
 public static NormalIdleHardwareSnapshot captureBeforeRelease(Minecraft c){return capture(c,false);}
 private static NormalIdleHardwareSnapshot capture(Minecraft c,boolean released){
  if(c==null||!c.isSameThread()||c.player==null||c.options==null||c.mouseHandler==null||c.mouseHandler.getClass()!=MouseHandler.class||!((Object)c.mouseHandler instanceof NormalIdleMouseCarrier mouse))return null;
  var keys=keys(c);var snapshots=new Object[9];var seen=new java.util.IdentityHashMap<KeyMapping,Boolean>();
  for(int i=0;i<9;i++){var key=keys[i];if(key==null||seen.put(key,true)!=null||released&&key.isDown()||(key.getClass()!=KeyMapping.class&&key.getClass()!=ToggleKeyMapping.class)||!(key instanceof NormalIdleKeyCarrier carrier))return null;snapshots[i]=carrier.normalIdleKeySnapshot();if(snapshots[i]==null)return null;}
  var m=mouse.normalIdleMouseSnapshot();if(m==null)return null;var result=new NormalIdleHardwareSnapshot(c,keys,snapshots,m,released);return result.current()?result:null;
 }
 public boolean mousePoseCurrent(){return client.isSameThread()&&client.options==options&&client.mouseHandler==mouse&&client.player==player&&client.player!=null&&client.player.getYRot()==yaw&&client.player.getXRot()==pitch
  &&(Object)client.mouseHandler instanceof NormalIdleMouseCarrier carrier&&carrier.normalIdleMouseCurrent(mouseSnapshot);}
 /** Observe first future attempts only for this already accepted original normal-drop snapshot. */
 public void watchAcceptedNormalDropAttempts(){for(int i=0;i<9;i++)if(keys[i] instanceof NormalIdleKeyCarrier carrier)carrier.watchNormalIdleAttemptOrigin(keySnapshots[i]);}
 public Object keyObservation(KeyMapping actual){for(int i=0;i<9;i++)if(actual==keys[i])return keySnapshots[i];return null;}
 public boolean current(){
  if(!client.isSameThread())return failed("CLIENT_THREAD_CHANGED");if(client.options!=options)return failed("OPTIONS_CHANGED");
  if(client.mouseHandler!=mouse)return failed("MOUSE_OBJECT_CHANGED");if(client.player!=player)return failed("PLAYER_CHANGED");if(client.player==null)return failed("PLAYER_NULL");
  float actualYaw=client.player.getYRot();if(actualYaw!=yaw)return failed("ACTUAL_YAW_CHANGED expected="+yaw+" actual="+actualYaw);
  float actualPitch=client.player.getXRot();if(actualPitch!=pitch)return failed("ACTUAL_PITCH_CHANGED expected="+pitch+" actual="+actualPitch);
  if(!((Object)client.mouseHandler instanceof NormalIdleMouseCarrier carrier))return failed("MOUSE_CARRIER_MISSING");
  if(!carrier.normalIdleMouseCurrent(mouseSnapshot))return failed("MOUSE_NATIVE_HISTORY_CHANGED {"+carrier.cachedNormalIdleMouseDiagnostic()+"}");
  var actual=keys(client);for(int i=0;i<9;i++){
   if(actual[i]!=keys[i])return failed("KEY_MAPPING_CHANGED index="+i);
   boolean actualDown=actual[i].isDown();if(released?actualDown:actualDown!=down[i])return failed("KEY_DOWN_CHANGED index="+i+" expected="+(released?false:down[i])+" actual="+actualDown);
   if(!(actual[i] instanceof NormalIdleKeyCarrier key))return failed("KEY_CARRIER_MISSING index="+i);
   if(!key.normalIdleKeyCurrent(keySnapshots[i]))return failed("KEY_NATIVE_HISTORY_CHANGED index="+i+" {"+key.cachedNormalIdleKeyDiagnostic()+"}");
  }return true;
 }
}
