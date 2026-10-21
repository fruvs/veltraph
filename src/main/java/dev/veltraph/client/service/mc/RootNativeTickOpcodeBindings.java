package dev.veltraph.client.service.mc;

import java.lang.invoke.MethodType;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Read-only exact map, built once from the final transform's retained opcode source metadata. */
final class RootNativeTickOpcodeBindings {
    record Binding(String original, String hookName, String hookDescriptor, String sourceName, String sourceDescriptor) {
        boolean exact(StackWalker.StackFrame hook, StackWalker.StackFrame source) {
            return hook.getDeclaringClass() == Minecraft.class && source.getDeclaringClass() == Minecraft.class
                    && hook.getMethodName().equals(hookName) && hook.getDescriptor().equals(hookDescriptor)
                    && source.getMethodName().equals(sourceName) && source.getDescriptor().equals(sourceDescriptor);
        }
    }
    private static final Map<String, Binding> BINDINGS = load();
    private RootNativeTickOpcodeBindings() {}
    static Binding get(String kind) { return BINDINGS.get(kind); }
    private static Map<String, Binding> load() {
        var found = new java.util.HashMap<String, Binding>();
        for (var method : Minecraft.class.getDeclaredMethods()) {
            var annotation = method.getAnnotation(RootNativeTickOpcodeBinding.class);
            if (annotation == null) continue;
            var expected = switch (annotation.kind()) { case "RUN" -> "run"; case "TICK" -> "runTick"; case "END" -> "tick"; default -> null; };
            if (expected == null || !expected.equals(annotation.original()) || annotation.sourceName().isEmpty())
                throw new IllegalStateException("Invalid original native opcode metadata");
            String desc = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).descriptorString();
            var binding = new Binding(annotation.original(), method.getName(), desc, annotation.sourceName(), annotation.sourceDescriptor());
            if (found.put(annotation.kind(), binding) != null) throw new IllegalStateException("Duplicate native opcode binding");
            boolean sourceExists = false;
            for (var source : Minecraft.class.getDeclaredMethods())
                if (source.getName().equals(binding.sourceName()) && MethodType.methodType(source.getReturnType(), source.getParameterTypes()).descriptorString().equals(binding.sourceDescriptor())) sourceExists = true;
            if (!sourceExists) throw new IllegalStateException("Missing exact native opcode body");
        }
        if (found.size() != 3) throw new IllegalStateException("Incomplete native opcode map");
        return Map.copyOf(found);
    }
}
