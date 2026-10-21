package dev.veltraph.client.service.mc;

import java.lang.invoke.MethodType;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/** Read-only exact final body map. Neither current stack discovery nor a name pattern supplies origin. */
final class RootNativeCameraOpcodeBindings {
    record Binding(Class<?> owner,String hookName,String hookDescriptor,String sourceName,String sourceDescriptor){
        boolean exact(StackWalker.StackFrame hook,StackWalker.StackFrame source){return hook.getDeclaringClass()==owner&&source.getDeclaringClass()==owner
            &&hook.getMethodName().equals(hookName)&&hook.getDescriptor().equals(hookDescriptor)
            &&source.getMethodName().equals(sourceName)&&source.getDescriptor().equals(sourceDescriptor);}
    }
    private static final Map<String,Binding> BINDINGS=load();
    private RootNativeCameraOpcodeBindings(){}
    static Binding get(String kind){return BINDINGS.get(kind);}
    private static Map<String,Binding> load(){
        var found=new java.util.HashMap<String,Binding>();
        for(var owner:new Class<?>[]{Minecraft.class,GameRenderer.class})for(var method:owner.getDeclaredMethods()){
            var annotation=method.getAnnotation(RootNativeCameraOpcodeBinding.class);if(annotation==null)continue;
            String original=switch(annotation.kind()){case "TICK"->"tick";case "FRAME"->"update";case "RENDER"->"runTick";case "UPDATE"->"renderFrame";default->null;};
            if(original==null||!original.equals(annotation.original())||annotation.sourceName().isEmpty()
                ||owner!=(annotation.kind().equals("FRAME")?GameRenderer.class:Minecraft.class))throw new IllegalStateException("Invalid camera opcode source binding");
            var binding=new Binding(owner,method.getName(),MethodType.methodType(method.getReturnType(),method.getParameterTypes()).descriptorString(),annotation.sourceName(),annotation.sourceDescriptor());
            if(found.put(annotation.kind(),binding)!=null)throw new IllegalStateException("Duplicate camera opcode source binding");
            boolean sourceExists=false;
            for(var source:owner.getDeclaredMethods())if(source.getName().equals(binding.sourceName())
                &&MethodType.methodType(source.getReturnType(),source.getParameterTypes()).descriptorString().equals(binding.sourceDescriptor()))sourceExists=true;
            if(!sourceExists)throw new IllegalStateException("Missing exact original camera source body");
        }
        if(found.size()!=4)throw new IllegalStateException("Incomplete original camera opcode source map");return Map.copyOf(found);
    }
}
