package dev.veltraph.client.builder;
/** STARTUP ONLY: one initial unclaimed-lock Root-frame deferral. No active-menu clock/context authority is granted here. */
final class Source95AcquisitionAdmission {
 enum Decision { WAIT, ADMITTED, BLOCKED }
 private enum State { NEW, WAITING, ADMITTED, REJECTED }
 record Snapshot(Object rootClock,long rootTick,Object owner,String requiredId,Object registered,Object inputOwner,Object player,Object world,Object connection,boolean enabled,boolean clientThread,boolean alive,boolean gameMode,boolean closedInventory,boolean cursorEmpty,boolean onGround,boolean lockActive,String lockOwner){
  boolean baseValid(){return rootClock!=null&&rootTick>=0&&rootTick<Long.MAX_VALUE&&owner!=null&&requiredId!=null&&!requiredId.isBlank()&&registered==owner&&inputOwner==owner&&player!=null&&world!=null&&connection!=null&&enabled&&clientThread&&alive&&gameMode&&closedInventory&&cursorEmpty&&onGround;}
  boolean fullyOwned(){return lockActive&&requiredId.equals(lockOwner);}
  boolean lockAbsent(){return !lockActive&&lockOwner==null;}
  boolean sameContext(Snapshot s){return s!=null&&rootClock==s.rootClock&&owner==s.owner&&requiredId.equals(s.requiredId)&&registered==s.registered&&inputOwner==s.inputOwner&&player==s.player&&world==s.world&&connection==s.connection;}
 }
 private State state=State.NEW;private Snapshot initial;private long startMillis=-1,lastObservedMillis;private boolean observedMillis;private String reason="Not admitted";
 Decision poll(Snapshot current,long now,long timeout,boolean policyValid){
  if(state==State.REJECTED)return Decision.BLOCKED;
  if(state==State.ADMITTED)throw new IllegalStateException("Startup admission is complete; use active purchase guards");
  if(!observeTime(now))return Decision.BLOCKED;
  if(!policyValid)return reject("Source policy revoked before startup admission");
  if(current==null||!current.baseValid())return reject("Exact registered enabled native startup owner/PWC/inventory unavailable");
  if(state==State.NEW){initial=current;startMillis=now;if(timeout<1)return reject("Source startup timeout invalid");if(current.fullyOwned()){state=State.ADMITTED;reason="Initial Root frame fully owns source input";return Decision.ADMITTED;}if(!current.lockAbsent())return reject("Initial source input lock is foreign or inconsistent");state=State.WAITING;reason="Await exactly next Root frame's source input lock; no actions admitted";return Decision.WAIT;}
  if(!initial.sameContext(current))return reject("Source startup context/clock identity changed; no adoption");
  if(now<startMillis||now-startMillis>=timeout)return reject("Original source startup deadline expired or clock regressed");
  if(current.rootTick()!=initial.rootTick()+1)return reject("Source startup must continue in exactly next Root frame");
  if(!current.fullyOwned())return reject("Source input lock absent/foreign in second startup frame");
  state=State.ADMITTED;reason="Next Root frame fully owns source input";return Decision.ADMITTED;
 }
 /** Raw message observation can revoke pending startup but cannot spend its one deferral or admit an action. */
 boolean observePending(Snapshot current,long now,long timeout,boolean policyValid){
  if(state!=State.WAITING)return false;
  if(!observeTime(now))return false;
  if(!policyValid||current==null||!current.baseValid()||!initial.sameContext(current)||now<startMillis||now-startMillis>=timeout||current.rootTick()<initial.rootTick()||current.rootTick()>initial.rootTick()+1||!(current.fullyOwned()||current.lockAbsent())||current.rootTick()==initial.rootTick()+1&&!current.fullyOwned()){reject("Observed pending source admission revoked");return false;}return true;
 }
 /** Shared production raw-message path: policy observation precedes native snapshot; no admission/action is issued. */
 void observePendingMessage(dev.veltraph.client.config.ConfigService config,Source95AcquisitionPolicy policy,Source95AcquisitionAuthority authority,java.util.function.Supplier<Snapshot> current,long now,long timeout){boolean valid=policy.validate(config,authority);if(!observePending(current.get(),now,timeout,valid))authority.revoke(reason);}
 private boolean observeTime(long now){if(observedMillis&&now<lastObservedMillis){reject("Observed startup clock regressed; no rearm");return false;}lastObservedMillis=now;observedMillis=true;return true;}
 private Decision reject(String why){state=State.REJECTED;reason=why;return Decision.BLOCKED;}
 void cancel(){reject("Source startup admission cancelled; no rearm");}
 boolean admitted(){return state==State.ADMITTED;}boolean waiting(){return state==State.WAITING;}long startMillis(){return startMillis;}long lastObservedMillis(){return lastObservedMillis;}String reason(){return reason;}
}
