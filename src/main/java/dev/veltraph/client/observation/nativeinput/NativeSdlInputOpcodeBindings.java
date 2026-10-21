package dev.veltraph.client.observation.nativeinput;
import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.util.thread.BlockableEventLoop;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Set;
/** Read-only unique final-transformed method pairs. No literal suffix, ancestor or active-state origin grant. */
final class NativeSdlInputOpcodeBindings {
 private NativeSdlInputOpcodeBindings(){}
 private record Binding(Class<?> owner,String hook,String descriptor,String source,String sourceDescriptor){
  boolean exact(StackWalker.StackFrame a,StackWalker.StackFrame b){return a.getDeclaringClass()==owner&&b.getDeclaringClass()==owner
   &&a.getMethodName().equals(hook)&&a.getDescriptor().equals(descriptor)&&b.getMethodName().equals(source)&&b.getDescriptor().equals(sourceDescriptor);}}
 private static final Map<String,Binding> bindings=load();
 private static Map<String,Binding> load(){var found=new java.util.HashMap<String,Binding>();
  Set<String> sdl=Set.of("POLL_METHOD","POLL_NATIVE","KEY_DISPATCH","BUTTON_DISPATCH","MOTION_DISPATCH","KEY_QUEUE","BUTTON_QUEUE","MOTION_QUEUE","KEY_LAMBDA","BUTTON_LAMBDA","MOTION_LAMBDA","KEY_HANDLER","BUTTON_HANDLER","MOTION_HANDLER","BUFFER_CLOSE");
  for(Class<?> owner:new Class<?>[]{SDLEventHandler.class,BlockableEventLoop.class})for(var method:owner.getDeclaredMethods()){
   var label=method.getAnnotation(NativeSdlInputOpcodeBinding.class);if(label==null)continue;
   if(owner==SDLEventHandler.class?!sdl.contains(label.kind()):!label.kind().equals("TASK_RUN"))throw new IllegalStateException("Unexpected native SDL source role");
   String descriptor=MethodType.methodType(method.getReturnType(),method.getParameterTypes()).descriptorString();boolean exists=false;
   for(var source:owner.getDeclaredMethods())if(source.getName().equals(label.sourceName())&&MethodType.methodType(source.getReturnType(),source.getParameterTypes()).descriptorString().equals(label.sourceDescriptor()))exists=true;
   if(!exists||found.put(label.kind(),new Binding(owner,method.getName(),descriptor,label.sourceName(),label.sourceDescriptor()))!=null)throw new IllegalStateException("Native SDL source binding missing/duplicate");
  }
  if(found.size()!=16)throw new IllegalStateException("Native SDL source binding incomplete");return Map.copyOf(found);
 }
 static boolean actual(String role){var binding=bindings.get(role);if(binding==null)return false;
  return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
   var frames=stream.filter(f->f.getDeclaringClass()!=NativeSdlInputOpcodeBindings.class&&f.getDeclaringClass()!=NativeSdlInputLineage.class).limit(2).toList();
   return frames.size()==2&&binding.exact(frames.get(0),frames.get(1));});}
}
