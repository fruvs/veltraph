package dev.veltraph.client.service.mc;

import java.lang.invoke.MethodType;
import java.util.Map;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.Main;

/** Immutable exact hook/source map. No suffix, wildcard, stack ancestor, or runtime current-source recapture. */
final class RootNativeSdlOpcodeBindings {
    record Binding(Class<?> owner,String hookName,String hookDescriptor,String sourceName,String sourceDescriptor) {
        boolean exact(StackWalker.StackFrame hook,StackWalker.StackFrame source) {
            return hook.getDeclaringClass()==owner&&source.getDeclaringClass()==owner
                &&hook.getMethodName().equals(hookName)&&hook.getDescriptor().equals(hookDescriptor)
                &&source.getMethodName().equals(sourceName)&&source.getDescriptor().equals(sourceDescriptor);
        }
    }
    private static final Map<String,Binding> BINDINGS=load();
    private RootNativeSdlOpcodeBindings() {}
    static Binding get(String kind){return BINDINGS.get(kind);}
    private static Map<String,Binding> load() {
        var found=new java.util.HashMap<String,Binding>();
        for(var owner:new Class<?>[]{Main.class,Minecraft.class,RenderSystem.class}) {
            var methods=owner.getDeclaredMethods();
            for(var hook:methods) {
                var metadata=hook.getAnnotation(RootNativeSdlOpcodeBinding.class);if(metadata==null)continue;
                Class<?> expectedOwner=switch(metadata.kind()) {
                    case "MAIN_RUN"->Main.class;case "WHOLE_RUN","SDL_OUTER","MOUSE_ACCUMULATE"->Minecraft.class;
                    case "SDL_INNER"->RenderSystem.class;default->null;
                };
                String expectedSource=switch(metadata.kind()) {
                    case "MAIN_RUN"->"([Ljava/lang/String;)V";
                    case "WHOLE_RUN","SDL_OUTER"->"()V";
                    case "SDL_INNER"->"(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V";
                    case "MOUSE_ACCUMULATE"->"(Z)V";default->null;
                };
                if(owner!=expectedOwner||expectedSource==null||!expectedSource.equals(metadata.sourceDescriptor())||metadata.sourceName().isEmpty())
                    throw new IllegalStateException("Invalid original SDL/mouse opcode metadata");
                boolean present=false;
                for(var source:methods)if(source.getName().equals(metadata.sourceName())
                    &&MethodType.methodType(source.getReturnType(),source.getParameterTypes()).descriptorString().equals(expectedSource))present=true;
                if(!present)throw new IllegalStateException("Missing exact original SDL/mouse source body");
                var binding=new Binding(owner,hook.getName(),MethodType.methodType(hook.getReturnType(),hook.getParameterTypes()).descriptorString(),metadata.sourceName(),expectedSource);
                if(found.put(metadata.kind(),binding)!=null)throw new IllegalStateException("Duplicate original SDL/mouse binding");
            }
        }
        if(found.size()!=5)throw new IllegalStateException("Incomplete original SDL/mouse binding map");
        return Map.copyOf(found);
    }
}
