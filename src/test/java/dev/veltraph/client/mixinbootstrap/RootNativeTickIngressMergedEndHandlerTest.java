package dev.veltraph.client.mixinbootstrap;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
class RootNativeTickIngressMergedEndHandlerTest {
    private static final String MC="net/minecraft/client/Minecraft", EVENTS="net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents", END=EVENTS+"$EndTick", CALLBACK="(Lnet/minecraft/client/Minecraft;)V";
    private static ClassNode target(){var t=new ClassNode();t.name=MC;t.methods.add(handler());return t;}
    private static MethodNode handler(){
        var m=new MethodNode(Opcodes.ACC_PRIVATE,"actualMergedOnEndTick","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,null);
        var merged=new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");merged.values=new ArrayList<>(List.of("mixin","net.fabricmc.fabric.mixin.event.lifecycle.client.MinecraftMixin"));m.visibleAnnotations=new ArrayList<>(List.of(merged));
        m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,EVENTS,"END_CLIENT_TICK","Lnet/fabricmc/fabric/api/event/Event;"));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/fabricmc/fabric/api/event/Event","invoker","()Ljava/lang/Object;",false));
        m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,END));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,END,"onEndTick",CALLBACK,true));m.instructions.add(new InsnNode(Opcodes.RETURN));return m;
    }
    private static void apply(RootNativeTickIngressPlugin p,ClassNode t){p.postApply(MC,t,"dev.veltraph.client.mixin.RootNativeGameplayTickMixin",null);}
    private static void refuses(Consumer<ClassNode> change){var t=target();change.accept(t);var bodies=t.methods.stream().map(m->m.instructions.toArray()).toList();int count=t.methods.size();assertThrows(IllegalStateException.class,()->apply(new RootNativeTickIngressPlugin(),t));assertEquals(count,t.methods.size());for(int i=0;i<count;i++)assertArrayEquals(bodies.get(i),t.methods.get(i).instructions.toArray());}
    @Test void actualObservedSixOpcodeHandlerBindsOriginalOperationExactlyOnce(){
        var t=target();var m=t.methods.getFirst();var original=m.instructions.toArray();var p=new RootNativeTickIngressPlugin();apply(p,t);assertEquals(2,t.methods.size());var current=m.instructions.toArray();assertEquals(6,current.length);for(int i:List.of(0,1,2,3,5))assertSame(original[i],current[i]);
        assertInstanceOf(MethodInsnNode.class,current[4]);var bound=(MethodInsnNode)current[4];assertEquals(Opcodes.INVOKESTATIC,bound.getOpcode());assertEquals(MC,bound.owner);assertEquals("veltraph$nativeEndTickOpcode",bound.name);assertEquals("(L"+END+";L"+MC+";)V",bound.desc);assertFalse(bound.itf);
        var wrapper=t.methods.get(1);var calls=new ArrayList<MethodInsnNode>();for(var n:wrapper.instructions)if(n instanceof MethodInsnNode call&&call.owner.equals(END)&&call.name.equals("onEndTick"))calls.add(call);assertEquals(1,calls.size());var replay=calls.getFirst();assertEquals(Opcodes.INVOKEINTERFACE,replay.getOpcode());assertEquals(CALLBACK,replay.desc);assertTrue(replay.itf);
        var after=m.instructions.toArray();assertThrows(IllegalStateException.class,()->apply(p,t));assertEquals(2,t.methods.size());assertArrayEquals(after,m.instructions.toArray());
    }
    @Test void alteredEventFieldOwnerNameDescriptorOrOpcodeRefusesBeforeMutation(){for(int variant=0;variant<4;variant++){final int v=variant;refuses(t->{var m=t.methods.getFirst();m.instructions.set(m.instructions.get(0),new FieldInsnNode(v==3?Opcodes.GETFIELD:Opcodes.GETSTATIC,v==0?"foreign/Events":EVENTS,v==1?"START_CLIENT_TICK":"END_CLIENT_TICK",v==2?"Ljava/lang/Object;":"Lnet/fabricmc/fabric/api/event/Event;"));});}}
    @Test void alteredInvokerOwnerNameDescriptorOpcodeOrInterfaceRefuses(){for(int variant=0;variant<5;variant++){final int v=variant;refuses(t->{var m=t.methods.getFirst();m.instructions.set(m.instructions.get(1),new MethodInsnNode(v==3?Opcodes.INVOKESTATIC:Opcodes.INVOKEVIRTUAL,v==0?"foreign/Event":"net/fabricmc/fabric/api/event/Event",v==1?"foreignInvoker":"invoker",v==2?"()V":"()Ljava/lang/Object;",v==4));});}}
    @Test void alteredEndTickTypeOrTypeOpcodeRefuses(){for(int variant=0;variant<2;variant++){final int v=variant;refuses(t->{var m=t.methods.getFirst();m.instructions.set(m.instructions.get(2),new TypeInsnNode(v==0?Opcodes.INSTANCEOF:Opcodes.CHECKCAST,v==1?"foreign/EndTick":END));});}}
    @Test void receiverMustBeInstanceALoadZero(){for(int variant=0;variant<2;variant++){final int v=variant;refuses(t->{var m=t.methods.getFirst();m.instructions.set(m.instructions.get(3),new VarInsnNode(v==0?Opcodes.ILOAD:Opcodes.ALOAD,v==1?1:0));});}}
    @Test void originalOperationOwnerNameDescriptorOpcodeAndInterfaceMustAllMatch(){for(int variant=0;variant<5;variant++){final int v=variant;refuses(t->{var m=t.methods.getFirst();m.instructions.set(m.instructions.get(4),new MethodInsnNode(v==3?Opcodes.INVOKEVIRTUAL:Opcodes.INVOKEINTERFACE,v==0?"foreign/EndTick":END,v==1?"onStartTick":"onEndTick",v==2?"()V":CALLBACK,v!=4));});}}
    @Test void originalOperationMustRemainAtItsExactObservedPosition(){refuses(t->{var m=t.methods.getFirst();var a=m.instructions.get(3);var b=m.instructions.get(4);m.instructions.remove(a);m.instructions.insert(b,a);});}
    @Test void continuationMustBeNormalVoidReturn(){refuses(t->{var m=t.methods.getFirst();m.instructions.set(m.instructions.getLast(),new InsnNode(Opcodes.ARETURN));});}
    @Test void extraExecutableInstructionAndUnmergedSelfCastAreUnsupported(){refuses(t->t.methods.getFirst().instructions.insert(new InsnNode(Opcodes.NOP)));refuses(t->{var m=t.methods.getFirst();m.instructions.insert(m.instructions.get(3),new TypeInsnNode(Opcodes.CHECKCAST,MC));});}
    @Test void missingForeignAnnotationStaticHandlerAndWrongDescriptorRefuse(){for(int variant=0;variant<4;variant++){final int v=variant;refuses(t->{var m=t.methods.getFirst();if(v==0)m.visibleAnnotations=null;else if(v==1)m.visibleAnnotations.getFirst().values.set(1,"foreign/MinecraftMixin");else if(v==2)m.access|=Opcodes.ACC_STATIC;else m.desc="()V";});}}
    @Test void twoOriginalAnnotatedEndOperationsRefuseUniqueSourceRequirement(){refuses(t->t.methods.add(handler()));}
    @Test void wrongTargetRefusesEvenWithExactHandler(){refuses(t->t.name="foreign/Minecraft");}
}
