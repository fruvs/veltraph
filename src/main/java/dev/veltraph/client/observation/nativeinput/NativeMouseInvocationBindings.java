package dev.veltraph.client.observation.nativeinput;
import java.lang.invoke.MethodType;
import java.util.Map;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
/** Exact three consecutive transformed frames: own whole hook, wrapper, original real invocation method. */
final class NativeMouseInvocationBindings {
 private record Binding(Class<?> owner,String hook,String descriptor,String source,String sourceDescriptor,Class<?> caller,String callerName,String callerDescriptor){
  boolean exact(StackWalker.StackFrame h,StackWalker.StackFrame s,StackWalker.StackFrame c){return h.getDeclaringClass()==owner&&s.getDeclaringClass()==owner
   &&h.getMethodName().equals(hook)&&h.getDescriptor().equals(descriptor)&&s.getMethodName().equals(source)&&s.getDescriptor().equals(sourceDescriptor)
   &&c.getDeclaringClass()==caller&&c.getMethodName().equals(callerName)&&c.getDescriptor().equals(callerDescriptor);}}
 private static final Map<String,Binding> bindings=load();
 private NativeMouseInvocationBindings(){}
 private record Callee(Class<?> owner,String hook,String descriptor,String source,String sourceDescriptor){}
 private record Invoker(Class<?> owner,String name,String descriptor){}
 private static Map<String,Binding> load(){var callees=new java.util.HashMap<String,Callee>();var callers=new java.util.HashMap<String,Invoker>();
  var roles=java.util.Set.of("ACCUMULATION","TURN_PLAYER","ENTITY_TURN");
  for(Class<?> owner:new Class<?>[]{net.minecraft.client.Minecraft.class,MouseHandler.class,Entity.class})for(var method:owner.getDeclaredMethods()){
   var a=method.getAnnotation(NativeMouseInvocationBinding.class);
   if(a!=null){
    if(!roles.contains(a.kind())||((a.kind().equals("ENTITY_TURN"))!=(owner==Entity.class))||owner==net.minecraft.client.Minecraft.class)throw new IllegalStateException("Foreign native mouse callee role");
    boolean source=false;for(var candidate:owner.getDeclaredMethods())if(candidate.getName().equals(a.sourceName())&&descriptor(candidate).equals(a.sourceDescriptor()))source=true;
    if(!source||callees.put(a.kind(),new Callee(owner,method.getName(),descriptor(method),a.sourceName(),a.sourceDescriptor()))!=null)throw new IllegalStateException("Original mouse callee route missing/duplicate");
   }
   var invoker=method.getAnnotation(NativeMouseOriginalInvokerBinding.class);
   if(invoker!=null){Class<?> expected=invoker.kind().equals("ACCUMULATION")?net.minecraft.client.Minecraft.class:MouseHandler.class;
    if(!roles.contains(invoker.kind())||owner!=expected||callers.put(invoker.kind(),new Invoker(owner,method.getName(),descriptor(method)))!=null)throw new IllegalStateException("Original mouse invoker missing/duplicate");}
  }
  if(callees.size()!=3||callers.size()!=3)throw new IllegalStateException("Original mouse source route incomplete");
  var found=new java.util.HashMap<String,Binding>();for(String role:roles){var c=callees.get(role);var i=callers.get(role);
   found.put(role,new Binding(c.owner,c.hook,c.descriptor,c.source,c.sourceDescriptor,i.owner,i.name,i.descriptor));}
  return Map.copyOf(found);
 }
 private static String descriptor(java.lang.reflect.Method m){return MethodType.methodType(m.getReturnType(),m.getParameterTypes()).descriptorString();}
 static boolean actual(String kind){var b=bindings.get(kind);if(b==null)return false;
  return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
   var f=stream.filter(frame->frame.getDeclaringClass()!=NativeMouseInvocationBindings.class&&frame.getDeclaringClass()!=NativeMouseInvocationPhase.class).limit(3).toList();
   return f.size()==3&&b.exact(f.get(0),f.get(1),f.get(2));});}
}
