package dev.veltraph.client.mixinbootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@ResourceLock("SYSTEM_ERR")
class RootNativeTickIngressEndHandlerDiagnosticTest {
    private static final String MC="net/minecraft/client/Minecraft", END="net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$EndTick";
    private static ClassNode target(){var t=new ClassNode();t.name=MC;return t;}
    private static MethodNode handler(ClassNode t){
        var m=new MethodNode(Opcodes.ACC_PRIVATE,"onEndTick","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,null);
        var merged=new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");merged.values=new ArrayList<>(List.of("mixin","net.fabricmc.fabric.mixin.event.lifecycle.client.MinecraftMixin"));m.visibleAnnotations=new ArrayList<>(List.of(merged));
        m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents","END_CLIENT_TICK","Lnet/fabricmc/fabric/api/event/Event;"));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/fabricmc/fabric/api/event/Event","invoker","()Ljava/lang/Object;",false));
        m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,END));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,MC));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,END,"onEndTick","(Lnet/minecraft/client/Minecraft;)V",true));m.instructions.add(new InsnNode(Opcodes.RETURN));t.methods.add(m);return m;
    }
    @Test void originalSevenOpcodeSourceStillRejectsMergedGateAndReportsOperandsWithoutMutation(){
        var t=target();var m=handler(t);var before=m.instructions.toArray();var bytes=new ByteArrayOutputStream();var previous=System.err;
        try{System.setErr(new PrintStream(bytes));var failure=assertThrows(IllegalStateException.class,()->new RootNativeTickIngressPlugin().postApply(MC,t,"dev.veltraph.client.mixin.RootNativeGameplayTickMixin",null));assertTrue(failure.getMessage().endsWith("unfamiliar native EndTick body"));}finally{System.setErr(previous);}
        assertArrayEquals(before,m.instructions.toArray());assertEquals(1,t.methods.size());String log=bytes.toString();assertTrue(log.contains("executableCount=7"));assertTrue(log.contains("opcode=25 variable=0"));assertTrue(log.contains("opcodeIndex=5 opcode=185 call="+END+".onEndTick"));assertTrue(log.contains("originalOperation=true"));
    }
    @Test void loggerFailureDoesNotChangeOriginalPostApplyStructuralFailure(){
        var t=target();var m=handler(t);var before=m.instructions.toArray();var previous=System.err;
        try{System.setErr(new PrintStream(OutputStream.nullOutputStream()){@Override public void println(String ignored){throw new AssertionError("logger unavailable");}});var failure=assertThrows(IllegalStateException.class,()->new RootNativeTickIngressPlugin().postApply(MC,t,"dev.veltraph.client.mixin.RootNativeGameplayTickMixin",null));assertTrue(failure.getMessage().endsWith("unfamiliar native EndTick body"));}finally{System.setErr(previous);}assertArrayEquals(before,m.instructions.toArray());
    }
    @Test void diagnosticBoundsOutputTo24ExistingNodesAndReportsTruncation(){
        var t=target();var m=handler(t);var nodes=new ArrayList<AbstractInsnNode>();for(int i=0;i<31;i++)nodes.add(new InsnNode(Opcodes.NOP));var bytes=new ByteArrayOutputStream();var previous=System.err;
        try{System.setErr(new PrintStream(bytes));RootNativeTickIngressPlugin.observeNativeEndHandler(t,m,nodes,null);}finally{System.setErr(previous);}String log=bytes.toString();assertEquals(24,log.lines().filter(s->s.contains("opcodeIndex=")).count());assertTrue(log.contains("executableCount=31"));assertTrue(log.contains("truncatedAfter=24"));assertEquals(31,nodes.size());
    }
}
