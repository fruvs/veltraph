package dev.veltraph.client.observation.nativeinput;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.WeakHashMap;
import net.minecraft.client.KeyMapping;
/** Read-only retained binding attempts; no key setter, history exemption or adoption. */
public final class NativeKeyBindingHistory {
 private NativeKeyBindingHistory(){}
 private static final WeakHashMap<KeyMapping,NativeKeyBindingOrder> HISTORY=new WeakHashMap<>();
 private static boolean caller(String suffix){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.limit(8).anyMatch(f->f.getDeclaringClass()==KeyMapping.class&&f.getMethodName().endsWith(suffix)));}
 static void constructorReturned(KeyMapping actual){if(!caller("veltraph$manualKeyBorn"))return;synchronized(HISTORY){if(HISTORY.containsKey(actual))return;HISTORY.put(actual,new NativeKeyBindingOrder());}}
 public static Object snapshot(KeyMapping actual){try{NativeKeyBindingOrder order;synchronized(HISTORY){order=HISTORY.get(actual);}return order==null?null:order.snapshot();}catch(RuntimeException|Error unavailable){return null;}}
 public static boolean current(KeyMapping actual,Object exact){try{NativeKeyBindingOrder order;synchronized(HISTORY){order=HISTORY.get(actual);}return order!=null&&order.current(exact);}catch(RuntimeException|Error unavailable){return false;}}
 public static final class Write {private final KeyMapping actual;private final InputConstants.Key value;private final NativeKeyBindingOrder order;private final Object exact;private final NativeKeyBirthProof.ConfiguredBinding request;private Write(KeyMapping k,InputConstants.Key v,NativeKeyBindingOrder o,Object x,NativeKeyBirthProof.ConfiguredBinding r){actual=k;value=v;order=o;exact=x;request=r;}}
 /** Callable only by the woven private PUTFIELD observer. The attempt precedes vanilla mutation. */
 public static Write beforeField(KeyMapping actual,InputConstants.Key value){try{if(!caller("veltraph$manualBindingField"))return null;NativeKeyBindingOrder order;synchronized(HISTORY){order=HISTORY.get(actual);}if(order==null)return null;var observed=order.observe();var request=NativeKeyBirthProof.claimConfiguredField(actual,value,observed.before());return new Write(actual,value,order,observed.exact(),request);}catch(RuntimeException|Error unavailable){return null;}}
 public static void fieldReturned(Write actual,KeyMapping key,InputConstants.Key value,boolean normal){if(actual==null)return;try{if(!caller("veltraph$manualBindingField")||actual.actual!=key||actual.value!=value)return;actual.order.returned(actual.exact,normal);NativeKeyBirthProof.configuredFieldReturned(actual.request,key,value,actual.exact,normal);}catch(RuntimeException|Error unavailable){/* Vanilla return/throw is never changed. */}}
}
