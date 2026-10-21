package dev.veltraph.client.observation.nativeinput;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.service.mc.NormalIdleKeyCarrier;
import dev.veltraph.client.service.mc.OwnedPoseHistoryCarrier;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
/** Exact returned SDK screen transitions. Retained observations are data, never input permission. */
public final class NativeGuiKeyResetLineage {
 private NativeGuiKeyResetLineage(){}
 private static final ThreadLocal<Episode> ACTIVE=new ThreadLocal<>();private static final ThreadLocal<Release> RELEASE=new ThreadLocal<>();
 private static final ThreadLocal<Restore> RESTORE=new ThreadLocal<>();
 private static final ThreadLocal<Grab> GRAB=new ThreadLocal<>();private static final ThreadLocal<InnerCall> INNER=new ThreadLocal<>();
 private static RetainedOpeningReset retainedOpeningReset;
 private static NormalTransition lastTransition;private static ActualGuiOutcome lastGuiOutcome;
 private static boolean caller(Class<?> type,String suffix){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.limit(8).anyMatch(f->f.getDeclaringClass()==type&&f.getMethodName().endsWith(suffix)));}
 private static KeyMapping[] keys(Minecraft c){var o=c.options;return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};}
 public static final class Episode {
  final Minecraft client;final Object options,player,world,listener,connection,mode,camera;final Screen old,next;final KeyMapping[] keys;final NormalIdleKeyCarrier[] carrier=new NormalIdleKeyCarrier[9];final Object[] before=new Object[9],after=new Object[9];final String[] bindings=new String[9];final Object[] bindingHistories=new Object[9];final boolean[] normal=new boolean[9],beforeHeld=new boolean[9],afterHeld=new boolean[9],queried=new boolean[9];final int[] attempts=new int[9];final Episode prior;final boolean closing,focused,paused;final ScriptingInputLock.ControlSnapshot control;final OwnedPoseHistoryCarrier poseCarrier;final Object poseHistory;final float yaw,pitch;
  final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;boolean invalid;private Grab nativeInner;private Grab completedGrab;private ActualGuiEntry sourceEntry;int releaseAllCalls,restoreAllCalls,pendingRestore=-1;
  private Episode(Minecraft c,Screen next,Episode prior){client=c;options=c.options;player=c.player;world=c.level;listener=c.getConnection();connection=c.getConnection()==null?null:c.getConnection().getConnection();mode=c.gameMode;camera=c.getCameraEntity();focused=c.isWindowActive();paused=c.isPaused();control=ScriptingInputLock.originalPublicationSnapshot();poseCarrier=(Object)c.player instanceof OwnedPoseHistoryCarrier v?v:null;poseHistory=poseCarrier==null?null:poseCarrier.ownedPoseHistorySnapshot();yaw=c.player==null?Float.NaN:c.player.getYRot();pitch=c.player==null?Float.NaN:c.player.getXRot();old=c.gui.screen();this.next=next;closing=next==null;this.prior=prior;keys=keys(c);root=VeltraphBootstrap.moduleManager();lifecycle=root==null?null:root.lifecycleSnapshot();invalid=prior!=null;
   for(int i=0;i<9;i++){if(!(keys[i] instanceof NormalIdleKeyCarrier v))throw new IllegalStateException("Missing native history");carrier[i]=v;before[i]=v.normalIdleKeySnapshot();bindings[i]=keys[i].saveString();bindingHistories[i]=NativeKeyBindingHistory.snapshot(keys[i]);if(bindingHistories[i]==null||!NativeKeyBindingHistory.current(keys[i],bindingHistories[i]))throw new IllegalStateException("Unknown binding history");beforeHeld[i]=keys[i].isDown();afterHeld[i]=beforeHeld[i];if(before[i]==null||!v.normalIdleKeyCurrent(before[i]))throw new IllegalStateException("Unknown before history");}}
  private Episode(Grab phase){var o=phase.outer;client=o.client;options=o.options;player=o.player;world=o.world;listener=o.listener;connection=o.connection;mode=o.mode;camera=o.camera;old=null;next=null;keys=o.keys;prior=o;closing=true;focused=o.focused;paused=o.paused;control=o.control;poseCarrier=o.poseCarrier;poseHistory=o.poseHistory;yaw=o.yaw;pitch=o.pitch;root=o.root;lifecycle=o.lifecycle;nativeInner=phase;}
  // Retain the original constructor reset header. It does not grant a rooted transition.
  boolean header(){if(client!=Minecraft.getInstance()||!client.isSameThread()||client.options!=options||client.player!=player||client.level!=world||client.getConnection()!=listener||VeltraphBootstrap.moduleManager()!=root||root!=null&&(lifecycle==null||!root.lifecycleCurrent(lifecycle)))return false;
   var actual=keys(client);for(int i=0;i<9;i++)if(actual[i]!=keys[i]||!bindings[i].equals(keys[i].saveString())||!NativeKeyBindingHistory.current(keys[i],bindingHistories[i]))return false;return true;}
  boolean nativePolicy(){return header()&&player!=null&&world!=null&&listener!=null&&connection!=null&&client.getConnection().getConnection()==connection&&((net.minecraft.network.Connection)connection).isConnected()
   &&mode!=null&&client.gameMode==mode&&camera==player&&client.getCameraEntity()==camera&&client.player.isAlive()&&client.player.level()==world
   &&focused&&client.isWindowActive()==focused&&!paused&&client.isPaused()==paused&&root!=null&&lifecycle!=null&&lifecycle.known()&&control!=null&&control.publicationCurrent()&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&poseCarrier!=null&&poseHistory!=null&&poseCarrier.ownedPoseHistoryCurrent(poseHistory)
   &&Float.floatToRawIntBits(client.player.getYRot())==Float.floatToRawIntBits(yaw)&&Float.floatToRawIntBits(client.player.getXRot())==Float.floatToRawIntBits(pitch);}

 }
 public static final class Release {final Episode episode;final int index;final Release prior;final Object token=new Object();boolean claimed,fieldReturned;private Release(Episode e,int index,Release prior){episode=e;this.index=index;this.prior=prior;}}
 /** The whole real Gui.setScreen entry precedes init/removed callbacks. No Root seed is minted. */
 public static Episode begin(Screen next){try{var c=Minecraft.getInstance();boolean originalCaller=caller(Gui.class,"veltraph$manualGuiTransition");if(originalCaller)retainedOpeningReset=null;if(!originalCaller||c==null||!c.isSameThread()||c.options==null)return null;var selected=claimNativeInner(c,next);if(selected!=null)return selected;lastTransition=null;lastGuiOutcome=null;var prior=ACTIVE.get();if(prior!=null)prior.invalid=true;if(next==null&&c.gui.screen()==null)return null;var result=new Episode(c,next,prior);ACTIVE.set(result);return result;}catch(RuntimeException|Error unavailable){return null;}}
 public static void beginReleaseAll(){var e=ACTIVE.get();if(e==null)return;try{if(e.closing||!caller(Gui.class,"veltraph$manualGuiReleaseAll")||!e.header()||e.client.gui.screen()!=e.next||++e.releaseAllCalls!=1)e.invalid=true;}catch(RuntimeException|Error unavailable){e.invalid=true;}}
 public static Release beforeRelease(KeyMapping key){var e=ACTIVE.get();if(e==null)return null;try{if(!caller(KeyMapping.class,"veltraph$manualGuiReleaseKey")||e.invalid||e.closing||e.releaseAllCalls!=1||!e.header())return null;int index=-1;for(int i=0;i<9;i++)if(e.keys[i]==key)index=i;if(index<0)return null;if(e.normal[index]||!e.carrier[index].normalIdleKeyCurrent(e.before[index])){e.invalid=true;return null;}var old=RELEASE.get();if(old!=null){old.episode.invalid=true;e.invalid=true;}var result=new Release(e,index,old);RELEASE.set(result);return result;}catch(RuntimeException|Error unavailable){e.invalid=true;return null;}}
 public static Object claimField(KeyMapping key,boolean down){var actual=RELEASE.get();if(actual==null)return claimRestoreField(key,down);try{if(!caller(KeyMapping.class,"veltraph$manualActualKeyWrite")||actual.episode.invalid||actual.claimed||down||actual.episode.keys[actual.index]!=key){actual.episode.invalid=true;return null;}actual.claimed=true;return actual.token;}catch(RuntimeException|Error unavailable){actual.episode.invalid=true;return null;}}
 public static void fieldReturned(Object token,KeyMapping key,boolean down,boolean normal){var actual=RELEASE.get();if(actual==null){restoreFieldReturned(token,key,down,normal);return;}try{if(!caller(KeyMapping.class,"veltraph$manualActualKeyWrite")||token!=actual.token||!actual.claimed||actual.fieldReturned||!normal||down||key!=actual.episode.keys[actual.index]||key.isDown()){actual.episode.invalid=true;return;}actual.fieldReturned=true;}catch(RuntimeException|Error unavailable){actual.episode.invalid=true;}}
 public static void afterRelease(Release actual,boolean normal){if(actual==null)return;try{var e=actual.episode;if(RELEASE.get()!=actual||e.invalid||!normal||!actual.fieldReturned||!e.header()){e.invalid=true;return;}var after=e.carrier[actual.index].normalIdleKeySnapshot();
  // Plain and native legal toggle outer histories both count one outer null-event attempt.
  if(after==null||!e.carrier[actual.index].normalIdleKeyAdvanced(e.before[actual.index],after,Collections.singletonList(null))||!e.carrier[actual.index].normalIdleKeyCurrent(after)||e.keys[actual.index].isDown()){e.invalid=true;return;}e.after[actual.index]=after;e.normal[actual.index]=true;e.attempts[actual.index]=1;e.afterHeld[actual.index]=false;
 }catch(RuntimeException|Error unavailable){actual.episode.invalid=true;}finally{if(RELEASE.get()==actual)RELEASE.set(actual.prior);}}
 public static final class NormalReset {private final Episode episode;private final Object openingOrigin=new Object();private NormalReset(Episode e){episode=e;}Object openingOriginIdentity(){return openingOrigin;}boolean advances(KeyMapping key,Object old){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.normal[i]&&episode.carrier[i].normalIdleKeyAdvanced(old,episode.after[i],Collections.singletonList(null))&&episode.carrier[i].normalIdleKeyCurrent(episode.after[i]);return false;}Screen oldScreen(){return episode.old;}Screen nextScreen(){return episode.next;}boolean matchesNative(Minecraft c,Object options,Object player,Object world,Object listener,ModuleManager root){return episode.client==c&&episode.options==options&&episode.player==player&&episode.world==world&&episode.listener==listener&&episode.root==root&&episode.header()&&c.gui.screen()==episode.next;}
Object after(KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.after[i];return null;}String binding(KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.bindings[i];return null;}}
 /** Immutable historical key projection, not old-scene current permission. No old player/world/GuiEpisode is retained. */
 static final class RetainedOpeningReset {
  private final java.lang.ref.WeakReference<Minecraft> client;private final java.lang.ref.WeakReference<Object> options;
  private final java.lang.ref.WeakReference<Screen> screen;private final java.lang.ref.WeakReference<Thread> thread;
  private final java.lang.ref.WeakReference<KeyMapping>[] keys;private final NativeKeyBirthProof[] successors;
  private final Object[] after,bindingHistory;private final String[] bindings;private final Object origin;private NativePlayerBirthProof.LoadingUpdateScope owner;
  @SuppressWarnings("unchecked") private RetainedOpeningReset(NormalReset reset,NativeKeyBirthProof[] successors){var e=reset.episode;client=new java.lang.ref.WeakReference<>(e.client);options=new java.lang.ref.WeakReference<>(e.options);screen=new java.lang.ref.WeakReference<>(e.next);thread=new java.lang.ref.WeakReference<>(Thread.currentThread());origin=reset.openingOriginIdentity();this.successors=successors.clone();after=e.after.clone();bindingHistory=e.bindingHistories.clone();bindings=e.bindings.clone();keys=(java.lang.ref.WeakReference<KeyMapping>[])new java.lang.ref.WeakReference<?>[9];for(int i=0;i<9;i++)keys[i]=new java.lang.ref.WeakReference<>(e.keys[i]);}
  private boolean matches(Minecraft c,Object options,Screen screen,KeyMapping[] keys,NativeKeyBirthProof[] proofs,Object[] histories,Object[] bindings,String[] names){
   if(client.get()!=c||this.options.get()!=options||this.screen.get()!=screen||thread.get()!=Thread.currentThread()||keys.length!=9||proofs.length!=9||histories.length!=9||bindings.length!=9||names.length!=9)return false;
   for(int i=0;i<9;i++)if(this.keys[i].get()!=keys[i]||successors[i]!=proofs[i]||after[i]!=histories[i]||bindingHistory[i]!=bindings[i]||!this.bindings[i].equals(names[i])||!proofs[i].retainedOpeningFields(origin,keys[i],histories[i],bindings[i],names[i],c,options))return false;return true;
  }
  boolean finalRawCurrent(NativePlayerBirthProof.LoadingUpdateScope scope,KeyMapping[] keys,NativeKeyBirthProof[] proofs,Object[] histories,Object[] bindings){
   if(owner!=scope||retainedOpeningReset!=this)return false;for(int i=0;i<9;i++)if(this.keys[i].get()!=keys[i]||successors[i]!=proofs[i]||after[i]!=histories[i]||bindingHistory[i]!=bindings[i]||!proofs[i].retainedOpeningRawCurrent(origin,keys[i],histories[i],bindings[i]))return false;return owner==scope&&retainedOpeningReset==this;
  }
  boolean ownedProjectionCurrent(NativePlayerBirthProof.LoadingUpdateScope scope,Minecraft c,Object options,Screen screen,KeyMapping[] keys,NativeKeyBirthProof[] proofs,Object[] histories,Object[] bindings,String[] names){return owner==scope&&retainedOpeningReset==this&&matches(c,options,screen,keys,proofs,histories,bindings,names)&&retainedOpeningReset==this;}
 }
 private static void recordOpening(NormalReset reset){
  var e=reset.episode;if(ACTIVE.get()!=e||e.invalid||e.closing||e.nativeInner!=null||!(e.next instanceof net.minecraft.client.gui.screens.LevelLoadingScreen)||!caller(Gui.class,"veltraph$manualGuiTransition"))return;
  var successors=new NativeKeyBirthProof[9];for(int i=0;i<9;i++){if(e.keys[i].getClass()!=KeyMapping.class&&e.keys[i].getClass()!=ToggleKeyMapping.class)return;successors[i]=NativeKeyBirthProof.recordedOpeningSuccessor(e.keys[i],reset);if(successors[i]==null)return;}
  if(ACTIVE.get()!=e||e.invalid||e.closing||e.nativeInner!=null)return;
  retainedOpeningReset=new RetainedOpeningReset(reset,successors);
 }
 static RetainedOpeningReset claimRetainedOpening(NativePlayerBirthProof.LoadingUpdateScope scope,Minecraft c,Object options,Screen screen,KeyMapping[] keys,NativeKeyBirthProof[] proofs,Object[] histories,Object[] bindings,String[] names){
  boolean producer=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.filter(f->f.getDeclaringClass()!=NativeGuiKeyResetLineage.class).findFirst().map(f->f.getDeclaringClass()==NativePlayerBirthProof.class&&f.getMethodName().equals("loadingUpdateEntered")).orElse(false));
  var receipt=retainedOpeningReset;if(!producer||scope==null||receipt==null||receipt.owner!=null||!receipt.matches(c,options,screen,keys,proofs,histories,bindings,names))return null;receipt.owner=scope;return receipt;
 }
 /** Native SDK BEFORE observation only. A private Episode binds it to this one original invocation. */
 public static final class ActualGuiEntry {
  private final Episode episode;private ActualGuiEntry(Episode e){episode=e;}
  public boolean beforeCurrent(Minecraft c){try{if(c!=episode.client||ACTIVE.get()!=episode||episode.invalid||episode.releaseAllCalls!=0||episode.restoreAllCalls!=0||c.gui.screen()!=episode.old||!episode.nativePolicy())return false;
   for(int i=0;i<9;i++)if(!episode.carrier[i].normalIdleKeyCurrent(episode.before[i])||episode.keys[i].isDown()!=episode.beforeHeld[i])return false;return true;
  }catch(RuntimeException|Error unavailable){return false;}}
  public boolean matchesNative(Minecraft c,Object options,Object player,Object world,Object listener,ModuleManager root){try{return episode.client==c&&episode.options==options&&episode.player==player&&episode.world==world&&episode.listener==listener&&episode.root==root&&episode.nativePolicy();}catch(RuntimeException|Error unavailable){return false;}}
  public Object before(KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.before[i];return null;}
  public boolean beforeHeld(KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.beforeHeld[i];throw new IllegalArgumentException("Foreign mapping");}
  public String binding(KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.bindings[i];return null;}
  public Object beforePose(OwnedPoseHistoryCarrier carrier){return carrier==episode.poseCarrier?episode.poseHistory:null;}
  public Screen oldScreen(){return episode.old;}public Screen nextScreen(){return episode.next;}
 }
 /** Only the actual woven Gui entry can expose an episode; no new snapshots or public enrollment. */
 public static ActualGuiEntry actualGuiEntry(Episode actual,Minecraft c){try{if(actual==null||ACTIVE.get()!=actual||!caller(Gui.class,"veltraph$manualGuiTransition"))return null;
  if(actual.sourceEntry==null)actual.sourceEntry=new ActualGuiEntry(actual);return actual.sourceEntry.beforeCurrent(c)?actual.sourceEntry:null;
 }catch(RuntimeException|Error unavailable){return null;}}
 /** Read-only BEFORE lookup for an already recorded actual SDK entry; never captures a frame. */
 public static ActualGuiEntry peekActualGuiEntry(Minecraft c){var e=ACTIVE.get();var actual=e==null?null:e.sourceEntry;return actual!=null&&actual.beforeCurrent(c)?actual:null;}
 /** Read-only completed data observation, including failure. It is never an authority issuer. */
 public static ActualGuiOutcome peekActualGuiOutcome(Minecraft c){try{var actual=lastGuiOutcome;if(actual==null)return null;var e=actual.entry.episode;
  return e.client==c&&e.nativePolicy()&&(c.gui.screen()==e.old||c.gui.screen()==e.next)?actual:null;
 }catch(RuntimeException|Error unavailable){return null;}}
 /** Whole native outcome; UNKNOWN or failed data cannot become a normal source transition. */
 public static final class ActualGuiOutcome {
  private final ActualGuiEntry entry;private final boolean normal;private final NormalTransition transition;
  private ActualGuiOutcome(ActualGuiEntry entry,boolean normal,NormalTransition transition){this.entry=entry;this.normal=normal;this.transition=transition;}
  public ActualGuiEntry entryIdentity(){return entry;}public boolean wholeNormal(){return normal;}
  public NormalTransition normalTransition(){return normal&&transition!=null&&transition.current(entry.episode.client)?transition:null;}
  public boolean nativeContextCurrent(Minecraft c){try{var e=entry.episode;return c==e.client&&e.nativePolicy()&&c.gui.screen()==e.next;}catch(RuntimeException|Error unavailable){return false;}}
 }
 /** Paired final wrapper observation. Source failure remains failed even if the screen field changed. */
 public static ActualGuiOutcome actualGuiOutcome(Episode actual,ActualGuiEntry entry,boolean normal,Minecraft c){try{
  if(actual==null||entry==null||actual.sourceEntry!=entry||entry.episode!=actual||c!=actual.client||!caller(Gui.class,"veltraph$manualGuiTransition"))return null;
  var returned=lastTransition;var qualified=normal&&returned!=null&&returned.episode==actual&&returned.current(c)?returned:null;
  var result=new ActualGuiOutcome(entry,normal,qualified);lastGuiOutcome=result;return result;
 }catch(RuntimeException|Error unavailable){return null;}}
 /** Read-only canonical lookup: never captures or renews an episode. */
 public static NormalTransition peekNormalTransition(Minecraft c){var actual=lastTransition;return actual!=null&&actual.current(c)?actual:null;}
 public static final class NormalTransition {
  private final Episode episode;private NormalTransition(Episode e){episode=e;}
  public Screen oldScreen(){return episode.old;}public Screen nextScreen(){return episode.next;}
  private int index(KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return i;return -1;}
  public Object before(KeyMapping key){int i=index(key);return i<0?null:episode.before[i];}
  public Object after(KeyMapping key){int i=index(key);return i<0?null:episode.after[i];}
  public String binding(KeyMapping key){int i=index(key);return i<0?null:episode.bindings[i];}
  public boolean held(KeyMapping key){int i=index(key);if(i<0)throw new IllegalArgumentException("Foreign mapping");return episode.afterHeld[i];}
  public boolean beforeHeld(KeyMapping key){int i=index(key);if(i<0)throw new IllegalArgumentException("Foreign mapping");return episode.beforeHeld[i];}
  public int actualOuterAttempts(KeyMapping key){int i=index(key);return i<0?-1:episode.attempts[i];}
  public boolean advances(KeyMapping key,Object old){try{int i=index(key);return i>=0&&current(episode.client)&&episode.carrier[i].normalIdleKeyAdvanced(old,episode.before[i],Collections.emptyList())&&episode.carrier[i].normalIdleKeyAdvanced(episode.before[i],episode.after[i],Collections.nCopies(episode.attempts[i],null));}catch(RuntimeException|Error unavailable){return false;}}
  public boolean matchesNative(Minecraft c,Object options,Object player,Object world,Object listener,ModuleManager root){return episode.client==c&&episode.options==options&&episode.player==player&&episode.world==world&&episode.listener==listener&&episode.root==root&&current(c);}
  public boolean current(Minecraft c){try{if(c!=episode.client||episode.invalid||episode.old==episode.next||!episode.nativePolicy()||c.gui.screen()!=episode.next)return false;for(int i=0;i<9;i++)if(!episode.normal[i]||episode.after[i]==null||!episode.carrier[i].normalIdleKeyCurrent(episode.after[i])||episode.keys[i].isDown()!=episode.afterHeld[i])return false;return true;}catch(RuntimeException|Error unavailable){return false;}}
 }
 public static final class Restore {
  private final Episode episode;private final int index;private final Restore prior;private final boolean setter;private final Object token=new Object();private boolean claimed,fieldReturned;
  private Restore(Episode e,int i,Restore prior,boolean setter){episode=e;index=i;this.prior=prior;this.setter=setter;}
 }
 /** Actual Gui close call site, before the restoration loop. */
 public static void beginRestoreAll(){var e=ACTIVE.get();if(e==null)return;try{
  var phase=GRAB.get();boolean inner=phase!=null&&phase.outer==e&&phase.inner!=null&&phase.innerExecuting&&INNER.get()==phase.call;
  if(!caller(Gui.class,"veltraph$manualGuiRestoreAll")||!e.closing||!e.nativePolicy()||e.client.gui.screen()!=null||e.releaseAllCalls!=0||!expectedHistories(e,-1)){e.invalid=true;return;}
  if(inner){if(e.restoreAllCalls!=1||phase.innerRestoreSeen){e.invalid=true;return;}phase.innerRestoreSeen=true;e.restoreAllCalls=2;for(int i=0;i<9;i++)if(e.keys[i] instanceof ToggleKeyMapping){e.queried[i]=false;e.normal[i]=false;}}
  else if(++e.restoreAllCalls!=1)e.invalid=true;
 }catch(RuntimeException|Error unavailable){e.invalid=true;}}
 private static boolean restoreLoopCurrent(Episode e){if(e.restoreAllCalls==1)return true;var phase=GRAB.get();return e.restoreAllCalls==2&&phase!=null&&phase.outer==e&&phase.innerExecuting&&phase.innerRestoreSeen&&INNER.get()==phase.call;}
 private static boolean expectedHistories(Episode e,int except){for(int i=0;i<9;i++)if(i!=except){var expected=e.after[i]==null?e.before[i]:e.after[i];if(!e.carrier[i].normalIdleKeyCurrent(expected)||e.keys[i].isDown()!=e.afterHeld[i])return false;}return true;}
 public static Restore beforeRestoreQuery(ToggleKeyMapping key){var e=ACTIVE.get();if(e==null)return null;try{
  if(!caller(KeyMapping.class,"veltraph$manualGuiRestoreQuery")||e.invalid||!e.closing||!restoreLoopCurrent(e)||e.pendingRestore!=-1||!e.nativePolicy()||!expectedHistories(e,-1))return reject(e);
  int i=-1;for(int n=0;n<9;n++)if(e.keys[n]==key)i=n;if(i<0)return null;if(e.queried[i]||RESTORE.get()!=null)return reject(e);
  var r=new Restore(e,i,null,false);RESTORE.set(r);return r;
 }catch(RuntimeException|Error unavailable){return reject(e);}}
 private static Restore reject(Episode e){e.invalid=true;return null;}
 public static void afterRestoreQuery(Restore r,boolean value,boolean normal){if(r==null)return;try{var e=r.episode;
  if(RESTORE.get()!=r||e.invalid||r.setter||!normal||!e.nativePolicy()||!expectedHistories(e,r.index)) {e.invalid=true;return;}
  var after=e.carrier[r.index].normalIdleKeySnapshot();if(after==null||!e.carrier[r.index].normalIdleKeyAdvanced(e.before[r.index],after,Collections.nCopies(e.attempts[r.index]+1,null))||!e.carrier[r.index].normalIdleKeyCurrent(after)||e.keys[r.index].isDown()!=e.afterHeld[r.index]){e.invalid=true;return;}
  e.queried[r.index]=true;e.attempts[r.index]++;e.after[r.index]=after;e.normal[r.index]=!value;if(value){if(e.afterHeld[r.index]){e.invalid=true;return;}e.pendingRestore=r.index;}
 }catch(RuntimeException|Error unavailable){r.episode.invalid=true;}finally{if(RESTORE.get()==r)RESTORE.set(r.prior);}}
 public static Restore beforeRestoreSetter(ToggleKeyMapping key,boolean down){var e=ACTIVE.get();if(e==null)return null;try{
  boolean relevant=false;for(var actual:e.keys)if(actual==key)relevant=true;if(!relevant)return null;
  if(!caller(KeyMapping.class,"veltraph$manualGuiRestoreSetter")||e.invalid||!e.closing||!down||e.pendingRestore<0||e.keys[e.pendingRestore]!=key||!e.nativePolicy()||!expectedHistories(e,-1)||RESTORE.get()!=null)return reject(e);
  var r=new Restore(e,e.pendingRestore,null,true);RESTORE.set(r);return r;
 }catch(RuntimeException|Error unavailable){return reject(e);}}
 private static Object claimRestoreField(KeyMapping key,boolean down){var r=RESTORE.get();if(r==null)return null;try{
  if(!caller(KeyMapping.class,"veltraph$manualActualKeyWrite")||r.episode.invalid||!r.setter||r.claimed||!down||r.episode.keys[r.index]!=key){r.episode.invalid=true;return null;}r.claimed=true;return r.token;
 }catch(RuntimeException|Error unavailable){r.episode.invalid=true;return null;}}
 private static void restoreFieldReturned(Object token,KeyMapping key,boolean down,boolean normal){var r=RESTORE.get();if(r==null)return;try{
  if(!caller(KeyMapping.class,"veltraph$manualActualKeyWrite")||r.episode.invalid||token!=r.token||!r.claimed||r.fieldReturned||!r.setter||!normal||!down||r.episode.keys[r.index]!=key||!key.isDown()){r.episode.invalid=true;return;}r.fieldReturned=true;
 }catch(RuntimeException|Error unavailable){r.episode.invalid=true;}}
 public static void afterRestoreSetter(Restore r,boolean normal){if(r==null)return;try{var e=r.episode;
  if(RESTORE.get()!=r||e.invalid||!r.setter||!normal||!r.fieldReturned||!e.nativePolicy()||!expectedHistories(e,r.index)){e.invalid=true;return;}
  var after=e.carrier[r.index].normalIdleKeySnapshot();if(after==null||!e.carrier[r.index].normalIdleKeyAdvanced(e.before[r.index],after,Collections.nCopies(e.attempts[r.index]+1,null))||!e.carrier[r.index].normalIdleKeyCurrent(after)||!e.keys[r.index].isDown()){e.invalid=true;return;}
  e.attempts[r.index]++;e.after[r.index]=after;e.afterHeld[r.index]=true;e.normal[r.index]=true;e.pendingRestore=-1;
 }catch(RuntimeException|Error unavailable){r.episode.invalid=true;}finally{if(RESTORE.get()==r)RESTORE.set(r.prior);}}
 /** Typed records for only the selected Gui INVOKE -> actual mouse INVOKE -> inner-null SDK chain. */
 public static final class Grab {
  private final Episode outer;private final Gui gui;private final MouseHandler mouse;private final boolean beforeGrabbed;private final Grab prior;
  private Episode inner;private InnerCall call;private boolean innerExecuting,innerRestoreSeen,innerNormal,nestedGrabEntered,nestedGrabReturned,returned;private int innerCalls;
  private Grab(Episode outer,Gui gui,MouseHandler mouse,boolean beforeGrabbed,Grab prior){this.outer=outer;this.gui=gui;this.mouse=mouse;this.beforeGrabbed=beforeGrabbed;this.prior=prior;}
 }
 public static final class GrabCall {private final Grab phase;private final boolean nested;private boolean returned;private GrabCall(Grab p,boolean nested){phase=p;this.nested=nested;}}
 public static final class InnerCall {private final Grab phase;private final InnerCall prior;private boolean returned;private InnerCall(Grab p,InnerCall prior){phase=p;this.prior=prior;}}
 private static boolean firstLoopComplete(Episode e){if(e.restoreAllCalls!=1||e.pendingRestore!=-1||!expectedHistories(e,-1))return false;for(int i=0;i<9;i++)if(e.keys[i] instanceof ToggleKeyMapping&&(!e.queried[i]||!e.normal[i]))return false;return true;}
 /** BEFORE the exact Gui.setScreen grabMouse INVOKE; normal native no-change calls remain exactly once. */
 public static GrabCall beforeGuiGrab(Gui gui,MouseHandler mouse){var e=ACTIVE.get();if(e==null)return null;try{
  if(!caller(Gui.class,"veltraph$manualGuiGrabMouse")||e.invalid||!e.closing||gui!=e.client.gui||mouse!=e.client.mouseHandler||gui.screen()!=null||!e.nativePolicy()){e.invalid=true;return null;}
  var prior=GRAB.get();if(prior!=null){if(prior.outer!=e||!prior.innerExecuting||INNER.get()!=prior.call||e.restoreAllCalls!=2||!prior.innerRestoreSeen||prior.nestedGrabEntered||!mouse.isMouseGrabbed()||!expectedHistories(e,-1)){e.invalid=true;return null;}prior.nestedGrabEntered=true;return new GrabCall(prior,true);}
  if(e.completedGrab!=null||!firstLoopComplete(e)){e.invalid=true;return null;}
  boolean before=mouse.isMouseGrabbed();if(!before&&net.minecraft.client.input.InputQuirks.RESTORE_KEY_STATE_AFTER_MOUSE_GRAB){e.invalid=true;return null;}
  var phase=new Grab(e,gui,mouse,before,null);GRAB.set(phase);return new GrabCall(phase,false);
 }catch(RuntimeException|Error unavailable){e.invalid=true;return null;}}
 public static void afterGuiGrab(GrabCall call,boolean normal){if(call==null)return;var p=call.phase;var e=p.outer;try{
  if(call.returned||GRAB.get()!=p||ACTIVE.get()!=e||e.invalid||!normal||!e.nativePolicy()||p.gui!=e.client.gui||p.mouse!=e.client.mouseHandler||p.gui.screen()!=null||!p.mouse.isMouseGrabbed()||!expectedHistories(e,-1)){e.invalid=true;return;}call.returned=true;
  if(call.nested){if(!p.innerExecuting||!p.nestedGrabEntered||p.nestedGrabReturned||e.restoreAllCalls!=2){e.invalid=true;return;}p.nestedGrabReturned=true;return;}
  if(p.returned||(p.beforeGrabbed?(p.innerCalls!=0||e.restoreAllCalls!=1):(p.innerCalls!=1||p.inner==null||!p.innerNormal||!p.nestedGrabReturned||e.restoreAllCalls!=2))){e.invalid=true;return;}
  p.returned=true;e.completedGrab=p;
 }catch(RuntimeException|Error unavailable){e.invalid=true;}finally{if(!call.nested&&GRAB.get()==p)GRAB.set(p.prior);}}
 /** BEFORE only MouseHandler.grabMouse's selected Gui.setScreen(null) INVOKE. */
 public static InnerCall beforeGrabbedInner(MouseHandler mouse,Gui gui,Screen next){var p=GRAB.get();if(p==null)return null;var e=p.outer;try{
  if(!caller(MouseHandler.class,"veltraph$manualGuiGrabbedInner")||mouse!=p.mouse||gui!=p.gui||next!=null||p.beforeGrabbed||p.innerCalls!=0||p.inner!=null||INNER.get()!=null||p.innerExecuting||ACTIVE.get()!=e||e.invalid||!e.nativePolicy()||gui.screen()!=null||!mouse.isMouseGrabbed()||!firstLoopComplete(e)){e.invalid=true;return null;}
  p.innerCalls++;var call=new InnerCall(p,null);p.call=call;INNER.set(call);return call;
 }catch(RuntimeException|Error unavailable){e.invalid=true;return null;}}
 private static Episode claimNativeInner(Minecraft c,Screen next){var call=INNER.get();if(call==null)return null;var p=call.phase;var e=p.outer;try{
  if(!caller(MouseHandler.class,"veltraph$manualGuiGrabbedInner")||next!=null||c!=e.client||ACTIVE.get()!=e||GRAB.get()!=p||p.call!=call||p.inner!=null||p.innerExecuting||e.invalid||c.gui!=p.gui||c.mouseHandler!=p.mouse||c.gui.screen()!=null||!p.mouse.isMouseGrabbed()||!e.nativePolicy()||!firstLoopComplete(e)){e.invalid=true;return null;}
  var inner=new Episode(p);p.inner=inner;p.innerExecuting=true;return inner;
 }catch(RuntimeException|Error unavailable){e.invalid=true;return null;}}
 private static void finishNativeInner(Episode actual,boolean normal){var p=actual.nativeInner;var e=p.outer;try{
  if(GRAB.get()!=p||INNER.get()!=p.call||ACTIVE.get()!=e||p.inner!=actual||!p.innerExecuting||e.invalid||!normal||!p.innerRestoreSeen||!p.nestedGrabReturned||e.restoreAllCalls!=2||e.pendingRestore!=-1||!e.nativePolicy()||!expectedHistories(e,-1)){e.invalid=true;return;}
  for(int i=0;i<9;i++)if(e.keys[i] instanceof ToggleKeyMapping&&(!e.queried[i]||!e.normal[i])){e.invalid=true;return;}p.innerNormal=true;
 }catch(RuntimeException|Error unavailable){e.invalid=true;}finally{p.innerExecuting=false;}}
 public static void afterGrabbedInner(InnerCall call,boolean normal){if(call==null)return;var p=call.phase;try{if(INNER.get()!=call||p.call!=call||call.returned||!normal||!p.innerNormal||p.innerExecuting||p.outer.invalid){p.outer.invalid=true;return;}call.returned=true;}catch(RuntimeException|Error unavailable){p.outer.invalid=true;}finally{if(INNER.get()==call)INNER.set(call.prior);}}
 private static boolean finishedGrabCurrent(Episode e){var p=e.completedGrab;return p!=null&&p.outer==e&&p.returned&&e.restoreAllCalls==(p.beforeGrabbed?1:2)&&(p.beforeGrabbed?p.innerCalls==0:p.innerCalls==1&&p.innerNormal&&p.nestedGrabReturned);}
 public static void finish(Episode actual,boolean normal){if(actual==null)return;if(actual.nativeInner!=null){finishNativeInner(actual,normal);return;}try{
  if(ACTIVE.get()!=actual||actual.invalid||!normal||actual.client.gui.screen()!=actual.next||!actual.header())return;
  if(actual.closing){if(!finishedGrabCurrent(actual)||actual.releaseAllCalls!=0||actual.pendingRestore!=-1)return;for(int i=0;i<9;i++){
   if(actual.keys[i] instanceof ToggleKeyMapping){if(!actual.queried[i]||!actual.normal[i])return;}else{if(!actual.carrier[i].normalIdleKeyCurrent(actual.before[i])||actual.keys[i].isDown()!=actual.beforeHeld[i])return;actual.after[i]=actual.before[i];actual.normal[i]=true;}
  }}else if(actual.releaseAllCalls!=1||actual.restoreAllCalls!=0)return;
  for(int i=0;i<9;i++)if(!actual.normal[i]||actual.after[i]==null||!actual.carrier[i].normalIdleKeyCurrent(actual.after[i])||actual.keys[i].isDown()!=actual.afterHeld[i])return;
  var transition=new NormalTransition(actual);
  if(!actual.closing){var proof=new NormalReset(actual);for(var key:actual.keys)NativeKeyBirthProof.nativeGuiResetReturned(key,proof);recordOpening(proof);McNativeManualInputLineage.actualGuiResetReturned(proof);}
  if(transition.current(actual.client)){lastTransition=transition;try{McNativeManualInputLineage.actualGuiTransitionReturned(transition);}catch(RuntimeException|Error unavailable){}}
 }catch(RuntimeException|Error unavailable){/* Never changes native result or adopts unknown before histories. */}finally{if(ACTIVE.get()==actual)ACTIVE.set(actual.prior);}}
}
