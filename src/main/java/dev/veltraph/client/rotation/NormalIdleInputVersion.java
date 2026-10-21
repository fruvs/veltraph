package dev.veltraph.client.rotation;
/** Passive exact method-attempt history, no input/publication or authority API. */
public final class NormalIdleInputVersion {
 private long revision;private int depth,children;private volatile boolean known=true;private Object event,ordinaryEvent;private String firstFailure="NONE",lastFailure="NONE";
 private java.lang.ref.WeakReference<Snapshot> watchedOrigin;private String firstWatchedAttempt="NONE";
 /** Diagnostic watch only: authentic old snapshot identity, no history update or permission. */
 public void watchAttemptOrigin(Snapshot exact){
  if(exact==null||exact.owner!=this||!known||depth!=0||exact.revision!=revision||exact.event!=event||watchedOrigin!=null&&watchedOrigin.get()==exact)return;
  watchedOrigin=new java.lang.ref.WeakReference<>(exact);firstWatchedAttempt="NONE";
 }
 /** Called at the existing native HEAD, after its one original begin/claim. */
 public void observeAttemptOrigin(boolean requested,boolean restoration){
  var reference=watchedOrigin;var watched=reference==null?null:reference.get();if(watched==null||!firstWatchedAttempt.equals("NONE")||!known||depth!=1||revision==watched.revision)return;
  // No long-lived KeyMapping reference to the prior envelope/native scope survives observation.
  watchedOrigin=null;
  try {String frames=StackWalker.getInstance().walk(stream->stream.limit(18)
    .map(frame->frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber())
    .collect(java.util.stream.Collectors.joining(" <- ")));
   firstWatchedAttempt="watchedRevision="+watched.revision+" actualRevision="+revision+" requested="+requested
    +" restoration="+restoration+" eventIdentity="+System.identityHashCode(event)+" unscoped="+(event==null)+" origin=["+frames+"]";
  }catch(RuntimeException unavailable){firstWatchedAttempt="ORIGIN_UNAVAILABLE_"+unavailable.getClass().getSimpleName();}
 }
 public String cachedCurrentDiagnostic(){return "first="+firstFailure+";last="+lastFailure+";firstWatchedAttempt="+firstWatchedAttempt;}
 private boolean failed(String reason){if(firstFailure.equals("NONE"))firstFailure=reason;lastFailure=reason;return false;}
 public static final class Snapshot {private final NormalIdleInputVersion owner;private final long revision;private final Object event,ordinaryEvent;private Snapshot(NormalIdleInputVersion owner){this.owner=owner;revision=owner.revision;event=owner.event;ordinaryEvent=owner.ordinaryEvent;}}
 /** Negative-only history of outer attempts whose existing native claim was absent; never input authority. */
 private long unownedRevision;private boolean unownedKnown=true;
 public static final class UnownedSnapshot {private final NormalIdleInputVersion owner;private final long revision;private UnownedSnapshot(NormalIdleInputVersion owner){this.owner=owner;revision=owner.unownedRevision;}}
 private void observeUnownedAttempt(Object actualOwnedInvocation){if(actualOwnedInvocation!=null)return;if(unownedRevision==Long.MAX_VALUE){unownedKnown=false;return;}unownedRevision++;}
 /** Captures an immutable refusal operand only. Existing owned events do not refresh this operand. */
 public UnownedSnapshot unownedSnapshot(){return known&&unownedKnown&&depth==0?new UnownedSnapshot(this):null;}
 public boolean unownedCurrent(UnownedSnapshot exact){return exact!=null&&exact.owner==this&&known&&unownedKnown&&depth==0&&exact.revision==unownedRevision;}
 public void begin(Object actualOwnedInvocation,boolean clientThread){begin(actualOwnedInvocation,null,clientThread);}
 public void begin(Object actualOwnedInvocation,Object actualOrdinaryEvent,boolean clientThread){if(depth==0)observeUnownedAttempt(actualOwnedInvocation);if(!clientThread){known=false;return;}if(depth==0){if(revision==Long.MAX_VALUE){known=false;return;}revision++;event=actualOwnedInvocation;ordinaryEvent=actualOrdinaryEvent;children=0;}else children++;depth++;}
 public void end(boolean legalNestedShape){if(depth<=0){known=false;return;}if(--depth==0&&(children>1||children>0&&!legalNestedShape))known=false;}
 public void invalidate(){known=false;}
 public Snapshot snapshot(){return known&&depth==0?new Snapshot(this):null;}
 /** Negative-only check of an already retained private snapshot owner; never captures or initializes native history. */
 public static boolean retainedSnapshotCurrent(Object exact){
  if(!(exact instanceof Snapshot s))return false;var owner=s.owner;
  return owner!=null&&owner.known&&owner.depth==0&&s.owner==owner&&s.revision==owner.revision&&s.event==owner.event&&s.ordinaryEvent==owner.ordinaryEvent;
 }
 public boolean current(Snapshot s){
  if(s==null)return failed("SNAPSHOT_NULL");if(!known)return failed("NATIVE_HISTORY_UNKNOWN");if(depth!=0)return failed("NATIVE_INVOCATION_IN_FLIGHT_depth="+depth);
  if(s.owner!=this)return failed("HISTORY_OBJECT_REPLACED");
  if(s.revision!=revision)return failed("NATIVE_ATTEMPT_REVISION_CHANGED expected="+s.revision+" actual="+revision+" oldEvent="+System.identityHashCode(s.event)+" newEvent="+System.identityHashCode(event)+" newUnscoped="+(event==null));
  if(s.event!=event)return failed("NATIVE_EVENT_IDENTITY_CHANGED");if(s.ordinaryEvent!=ordinaryEvent)return failed("ORDINARY_EVENT_IDENTITY_CHANGED");return true;
 }
 /** A post-normal observation of one unscoped outer attempt; does not authorize later writes. */
 public boolean unscopedAdvancedOnce(Snapshot before,Snapshot after){return before!=null&&after!=null&&before.owner==this&&after.owner==this
  &&known&&depth==0&&children==0&&after.revision==revision&&after.event==event&&after.revision>before.revision&&after.revision-before.revision==1&&after.event==null;}
 /** Requires the private canonical native identity in the actual snapshot; never input authority. */
 public boolean canonicalUnscopedAdvancedOnce(Snapshot before,Snapshot after,Object actualOrdinaryEvent){return actualOrdinaryEvent!=null&&before!=null&&after!=null&&before.owner==this&&after.owner==this
  &&known&&depth==0&&children==1&&after.revision==revision&&after.event==event&&after.event==null&&after.ordinaryEvent==ordinaryEvent&&after.ordinaryEvent==actualOrdinaryEvent
  &&after.revision>before.revision&&after.revision-before.revision==1;}
 public boolean advancedOnly(Snapshot before,Snapshot after,java.util.List<?> actualInvocations){return before!=null&&after!=null&&before.owner==this&&after.owner==this&&known&&depth==0
  &&after.revision>=before.revision&&after.revision-before.revision==actualInvocations.size()&&(actualInvocations.isEmpty()?after.event==before.event:after.event==actualInvocations.getLast());}
 /** Read-only exact in-flight private event prefix. No snapshot, event or revision is refreshed. */
 public boolean inFlightOnly(Snapshot original,java.util.List<?> returned,Object actualPending,int exactDepth){return original!=null&&original.owner==this&&actualPending!=null&&known
  &&(exactDepth==1||exactDepth==2)&&depth==exactDepth&&children==exactDepth-1&&ordinaryEvent==null
  &&revision>original.revision&&revision-original.revision==returned.size()+1L&&event==actualPending;}

}
