package dev.veltraph.client.observation.nativeinput;

/** Exact observational toggle return; caller supplies genuine source/field identities, never Root permission. */
final class NativeToggleReturnTrace {
 private enum Phase { OUTER,MODE_PENDING,MODE_RETURNED,SUPER_PENDING,FIELD_PENDING,FIELD_RETURNED,SUPER_RETURNED,COMPLETE,INVALID }
 private static final ThreadLocal<NativeToggleReturnTrace> ACTIVE=new ThreadLocal<>();
 private final Object source,actor,supplier;
 private final boolean requested,beforeHeld;
 private final Thread thread=Thread.currentThread();
 private Phase phase=Phase.OUTER;
 private boolean mode;
 private Mode modeCall;
 private Super superCall;
 private Field fieldCall;
 private Return result;
 private NativeToggleReturnTrace(Object source,Object actor,boolean requested,boolean beforeHeld,Object supplier){this.source=source;this.actor=actor;this.requested=requested;this.beforeHeld=beforeHeld;this.supplier=supplier;}
 static NativeToggleReturnTrace begin(Object source,Object actor,boolean requested,boolean beforeHeld,Object supplier){var old=ACTIVE.get();if(old!=null){old.refuse();return null;}if(source==null||actor==null||supplier==null)return null;var trace=new NativeToggleReturnTrace(source,actor,requested,beforeHeld,supplier);ACTIVE.set(trace);return trace;}
 private synchronized void refuse(){phase=Phase.INVALID;}
 synchronized void nestedEntryObserved(){refuse();}
 private boolean frame(){if(thread!=Thread.currentThread()||ACTIVE.get()!=this||phase==Phase.INVALID){phase=Phase.INVALID;return false;}return true;}
 static final class Mode {private final NativeToggleReturnTrace trace;private Mode(NativeToggleReturnTrace t){trace=t;}}
 static final class Super {private final NativeToggleReturnTrace trace;private final boolean down;private Super(NativeToggleReturnTrace t,boolean down){trace=t;this.down=down;}}
 static final class Field {private final NativeToggleReturnTrace trace;private final Super inner;private final Object nativeToken;private final boolean down;private Field(NativeToggleReturnTrace t,Super inner,Object token,boolean down){trace=t;this.inner=inner;nativeToken=token;this.down=down;}}
 synchronized Mode modeEntered(Object actor,Object supplier){if(!frame()||phase!=Phase.OUTER||this.actor!=actor||this.supplier!=supplier){refuse();return null;}modeCall=new Mode(this);phase=Phase.MODE_PENDING;return modeCall;}
 synchronized void modeReturned(Mode call,boolean observed,boolean normal){if(!frame()||phase!=Phase.MODE_PENDING||call==null||call!=modeCall||call.trace!=this||!normal){refuse();return;}mode=observed;phase=Phase.MODE_RETURNED;}
 private boolean expectsField(){return !mode||requested;}
 private boolean expectedDown(){return mode?!beforeHeld:requested;}
 synchronized Super superEntered(Object actor,boolean down){if(!frame()||phase!=Phase.MODE_RETURNED||this.actor!=actor||!expectsField()||down!=expectedDown()){refuse();return null;}superCall=new Super(this,down);phase=Phase.SUPER_PENDING;return superCall;}
 synchronized Field fieldEntered(Super inner,Object nativeFieldToken,Object actor,boolean down){if(!frame()||phase!=Phase.SUPER_PENDING||inner==null||inner!=superCall||inner.trace!=this||this.actor!=actor||down!=inner.down||nativeFieldToken==null){refuse();return null;}fieldCall=new Field(this,inner,nativeFieldToken,down);phase=Phase.FIELD_PENDING;return fieldCall;}
 synchronized void fieldReturned(Field field,Object sameNativeFieldToken,Object actor,boolean down,boolean normal){if(!frame()||phase!=Phase.FIELD_PENDING||field==null||field!=fieldCall||field.trace!=this||field.inner!=superCall||field.nativeToken!=sameNativeFieldToken||this.actor!=actor||field.down!=down||!normal){refuse();return;}phase=Phase.FIELD_RETURNED;}
 synchronized void superReturned(Super inner,boolean normal){if(!frame()||phase!=Phase.FIELD_RETURNED||inner==null||inner!=superCall||inner.trace!=this||!normal){refuse();return;}phase=Phase.SUPER_RETURNED;}
 synchronized Return outerReturned(Object sameSource,Object actor,boolean requested,boolean afterHeld,boolean normal){try{boolean shape=expectsField()?phase==Phase.SUPER_RETURNED:phase==Phase.MODE_RETURNED;boolean expected=expectsField()?expectedDown():beforeHeld;if(!frame()||!normal||sameSource!=source||this.actor!=actor||this.requested!=requested||!shape||afterHeld!=expected){refuse();return null;}result=new Return(this,mode,afterHeld,fieldCall==null?null:fieldCall.nativeToken);phase=Phase.COMPLETE;return result;}finally{if(thread==Thread.currentThread()&&ACTIVE.get()==this)ACTIVE.remove();}}
 static final class Return {
  private final NativeToggleReturnTrace trace;private final boolean observedMode,held;private final Object fieldToken;
  private Return(NativeToggleReturnTrace t,boolean mode,boolean held,Object fieldToken){trace=t;observedMode=mode;this.held=held;this.fieldToken=fieldToken;}
  boolean mode(){return observedMode;}
  boolean held(){return held;}
  boolean hasField(){return fieldToken!=null;}
  Object fieldToken(){return fieldToken;}
  boolean completed(){return true;}
  boolean current(){synchronized(trace){return trace.thread==Thread.currentThread()&&trace.phase==Phase.COMPLETE&&trace.result==this;}}
 }
}
