package dev.veltraph.client.observation.nativeinput;
import java.util.IdentityHashMap;
/** Passive attempt identity. Equal final values never restore an older token. */
final class NativeKeyBindingOrder {
 private Object exact=new Object();private boolean normal=true,ambiguous;
 private final IdentityHashMap<Object,Boolean> pending=new IdentityHashMap<>();
 synchronized Object snapshot(){return pending.isEmpty()&&normal&&!ambiguous?exact:null;}
 synchronized boolean current(Object old){return old!=null&&old==exact&&snapshot()!=null;}
 record Write(Object before,Object exact){}
 synchronized Write observe(){Object before=snapshot();ambiguous=!pending.isEmpty();exact=new Object();normal=false;pending.put(exact,Boolean.TRUE);return new Write(before,exact);}
 synchronized Object attempt(){return observe().exact();}
 synchronized void returned(Object attempt,boolean returned){if(pending.remove(attempt)==null)return;if(!returned)ambiguous=true;if(attempt==exact)normal=returned;}
}
