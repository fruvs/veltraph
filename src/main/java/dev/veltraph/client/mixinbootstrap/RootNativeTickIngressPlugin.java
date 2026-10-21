package dev.veltraph.client.mixinbootstrap;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import com.llamalad7.mixinextras.utils.MixinInternals;

/** Pinned Fabric 4.1.9 opcode composition; unfamiliar layouts fail rather than opening a fallback phase. */
public final class RootNativeTickIngressPlugin implements IMixinConfigPlugin {
    private static final String PHASE = "dev/veltraph/client/service/mc/RootNativeGameplayTickPhase";
    private static final String MC = "net/minecraft/client/Minecraft";
    private static final String RENDERER = "net/minecraft/client/renderer/GameRenderer";
    private static final String CAMERA_PHASE = "dev/veltraph/client/service/mc/RootNativeCameraPhase";
    private static final String MAIN = "net/minecraft/client/main/Main";
    private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
    private static final String SDL_PHASE = "dev/veltraph/client/service/mc/RootNativeSdlBoundaryPhase";
    private static final String SDL_HANDLER = "com/mojang/blaze3d/platform/SDLEventHandler";
    private static final String TASK_LOOP = "net/minecraft/util/thread/BlockableEventLoop";
    private static final String SDL_INPUT = "dev/veltraph/client/observation/nativeinput/NativeSdlInputLineage";
    private static final String MOUSE = "net/minecraft/client/MouseHandler";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String MOUSE_PHASE = "dev/veltraph/client/observation/nativeinput/NativeMouseInvocationPhase";
    private static final String EVENTS = "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents";
    private static final String END = EVENTS + "$EndTick";
    private static final String CALLBACK = "(L" + MC + ";)V";
    private static final String LOOP_DESC = "([L" + END + ";L" + MC + ";)V";
    private static final String LOOP_FRAME = PHASE + "$LoopFrame";
    private static final String BODY = "veltraph$originalEndTickLoopBody";

    private InsnList originalRun, originalRunTick, originalTick;
    private InsnList originalCameraTick, originalCameraFrame, originalRenderFrame;
    private InsnList originalMainRun, originalSdlPoll;
    private boolean gameplaySourceCaptured,sdlClientSourceCaptured;
    private MethodNode originalAccumulationMethod,originalTurnPlayerMethod,originalEntityTurnMethod;
    private InsnList originalAccumulation,originalTurnPlayer;
    private record InputRole(String kind,String target,String mixin,String enter,String method,String descriptor,int count,boolean whole){}
    private record InputSource(InputRole role,MethodNode original,InsnList instructions){}
    private final java.util.Map<String,InputSource> originalInputSources=new java.util.LinkedHashMap<>();
    private static final InputRole[] INPUT_ROLES={
        new InputRole("POLL_METHOD","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","beginPoll","pollEvents","()V",1,true),
        new InputRole("POLL_NATIVE","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","beginNativePoll","pollEvents","()V",1,false),
        new InputRole("BUFFER_CLOSE","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","beforeNativeBufferClose","pollEvents","()V",2,false),
        new InputRole("KEY_DISPATCH","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","beginDispatch","pollEvents","()V",1,false),
        new InputRole("KEY_QUEUE","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","captureQueue","handleKeyEvent","(Lorg/lwjgl/sdl/SDL_Event;)V",1,false),
        new InputRole("KEY_LAMBDA","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputDispatchMixin","beginLambda","lambda$handleKeyEvent$0","(Lorg/lwjgl/sdl/SDL_Event;ILnet/minecraft/client/input/KeyEvent;)V",1,true),
        new InputRole("KEY_HANDLER","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputDispatchMixin","beginHandler","lambda$handleKeyEvent$0","(Lorg/lwjgl/sdl/SDL_Event;ILnet/minecraft/client/input/KeyEvent;)V",1,false),
        new InputRole("BUTTON_DISPATCH","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","beginDispatch","pollEvents","()V",1,false),
        new InputRole("BUTTON_QUEUE","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","captureQueue","handleMouseButtonEvent","(Lorg/lwjgl/sdl/SDL_Event;)V",1,false),
        new InputRole("BUTTON_LAMBDA","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputDispatchMixin","beginLambda","lambda$handleMouseButtonEvent$0","(JLnet/minecraft/client/input/MouseButtonInfo;I)V",1,true),
        new InputRole("BUTTON_HANDLER","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputDispatchMixin","beginHandler","lambda$handleMouseButtonEvent$0","(JLnet/minecraft/client/input/MouseButtonInfo;I)V",1,false),
        new InputRole("MOTION_DISPATCH","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","beginDispatch","pollEvents","()V",1,false),
        new InputRole("MOTION_QUEUE","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputPollMixin","captureQueue","handleMouseMotionEvent","(Lorg/lwjgl/sdl/SDL_Event;)V",1,false),
        new InputRole("MOTION_LAMBDA","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputDispatchMixin","beginLambda","lambda$handleMouseMotionEvent$0","(JLorg/lwjgl/sdl/SDL_MouseMotionEvent;)V",1,true),
        new InputRole("MOTION_HANDLER","com/mojang/blaze3d/platform/SDLEventHandler","dev.veltraph.client.mixin.NativeSdlInputDispatchMixin","beginHandler","lambda$handleMouseMotionEvent$0","(JLorg/lwjgl/sdl/SDL_MouseMotionEvent;)V",1,false),
        new InputRole("TASK_RUN","net/minecraft/util/thread/BlockableEventLoop","dev.veltraph.client.mixin.NativeSdlTaskDeliveryMixin","beginTaskRun","doRunTask","(Ljava/lang/Runnable;)V",1,false)
    };

    private MethodNode nativeEndHandler;
    private IExtension wrapMethodExtension;
    private final FinalSourceBindings finalBindings = new FinalSourceBindings();
    @Override public void onLoad(String mixinPackage) {
        // Config selection precedes deferred service initialization and extension activation.
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        wrapMethodExtension = retainRegisteredWrapMethodExtension(MixinInternals.getExtensions().getExtensions(), wrapMethodExtension);
        MixinInternals.registerExtension(finalBindings, false);
    }
    /** Registered source instance now; the original postApply guard still requires active ordering later. */
    static IExtension retainRegisteredWrapMethodExtension(List<IExtension> registered, IExtension retained) {
        for (var extension : registered)
            if (extension.getClass().getName().equals("com.llamalad7.mixinextras.injector.wrapmethod.WrapMethodApplicatorExtension")) {
                require(retained == null, "duplicate deferred WrapMethod extension"); retained = extension;
            }
        require(retained != null, "pinned deferred WrapMethod extension unavailable");
        return retained;
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
        observeBindingBody(targetClass, () -> preApplyOriginal(targetClassName, targetClass, mixinClassName, info));
    }
    private void preApplyOriginal(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
        if(mixinClassName.equals("dev.veltraph.client.mixin.NativeMouseAccumulationEntryMixin")){
            require(targetClass.name.equals(MOUSE)&&originalAccumulationMethod==null,"duplicate/foreign original accumulation entry");
            originalAccumulationMethod=uniqueMethod(targetClass,"handleAccumulatedMovement","()V");originalAccumulation=originalAccumulationMethod.instructions;
        }else if(mixinClassName.equals("dev.veltraph.client.mixin.NativeMouseTurnPlayerEntryMixin")){
            require(targetClass.name.equals(MOUSE)&&originalTurnPlayerMethod==null,"duplicate/foreign original turnPlayer entry");
            originalTurnPlayerMethod=uniqueMethod(targetClass,"turnPlayer","(D)V");originalTurnPlayer=originalTurnPlayerMethod.instructions;
        }else if(mixinClassName.equals("dev.veltraph.client.mixin.NativeManualMouseTurnOpcodeMixin")){
            require(targetClass.name.equals(ENTITY)&&originalEntityTurnMethod==null,"duplicate/foreign original Entity turn entry");
            originalEntityTurnMethod=uniqueMethod(targetClass,"turn","(DD)V");
        }
        for(var role:INPUT_ROLES)if(role.mixin.equals(mixinClassName)){
            require(targetClass.name.equals(role.target)&&!originalInputSources.containsKey(role.kind),"duplicate/foreign original SDL input source");
            var original=uniqueMethod(targetClass,role.method,role.descriptor);
            originalInputSources.put(role.kind,new InputSource(role,original,original.instructions));
        }
        if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeSdlMainMixin")) {
            require(originalMainRun == null && targetClass.name.equals(MAIN), "duplicate original Main run source");
            originalMainRun = originalSource(targetClass,"main","([Ljava/lang/String;)V","run","()V").instructions;
        } else if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeSdlPollMixin")) {
            require(originalSdlPoll == null && targetClass.name.equals(RENDER_SYSTEM), "duplicate original SDL poll source");
            var poll=uniqueMethod(targetClass,"pollEvents","(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V");int count=0;
            for(var node:poll.instructions)if(node instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
                &&call.owner.equals("com/mojang/blaze3d/platform/SDLEventHandler")&&call.name.equals("pollEvents")&&call.desc.equals("()V"))count++;
            require(count==1,"unfamiliar original inner SDL poll opcode");originalSdlPoll=poll.instructions;
        } else if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeSdlClientMixin")) {
            require(!sdlClientSourceCaptured&&targetClass.name.equals(MC),"duplicate/foreign SDL client source");sdlClientSourceCaptured=true;
            var runBody=uniqueMethod(targetClass,"run","()V").instructions;
            var tickBody=uniqueMethod(targetClass,"runTick","(Z)V").instructions;
            require((originalRun==null||runBody==originalRun)&&(originalRunTick==null||tickBody==originalRunTick),"SDL did not retain original client bodies");
            originalRun=runBody;originalRunTick=tickBody;
            int poll=0,mouse=0;
            for(var node:originalRun)if(node instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC
                &&call.owner.equals(RENDER_SYSTEM)&&call.name.equals("pollEvents")&&call.desc.equals("(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V"))poll++;
            for(var node:originalRunTick)if(node instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
                &&call.owner.equals("net/minecraft/client/MouseHandler")&&call.name.equals("handleAccumulatedMovement")&&call.desc.equals("()V"))mouse++;
            require(poll==1&&mouse==1,"unfamiliar original client SDL/mouse opcodes");
        } else if (mixinClassName.equals("dev.veltraph.client.mixin.OwnedRotationTickMixin")) {
            require(originalCameraTick == null && targetClass.name.equals(MC), "duplicate original tick camera source");
            var cameraTickBody = uniqueMethod(targetClass, "tick", "()V").instructions;
            require(originalTick == null || cameraTickBody == originalTick, "camera tick did not retain original tick body");
            originalCameraTick = cameraTickBody;
        } else if (mixinClassName.equals("dev.veltraph.client.mixin.OwnedRotationFrameMixin")) {
            require(originalCameraFrame == null && targetClass.name.equals(RENDERER), "duplicate original frame camera source");
            originalCameraFrame = uniqueMethod(targetClass, "update", "(Lnet/minecraft/client/DeltaTracker;)V").instructions;
        } else if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeCameraIngressMixin")) {
            require(originalRenderFrame == null && targetClass.name.equals(MC), "duplicate original render camera source");
            var renderRunTick = originalSource(targetClass, "runTick", "(Z)V", "renderFrame", "(Z)V").instructions;
            require(originalRunTick == null || renderRunTick == originalRunTick,
                    "render camera source did not retain the original runTick body");
            var render = uniqueMethod(targetClass, "renderFrame", "(Z)V"); int calls = 0;
            for (var node : render.instructions) if (node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals(RENDERER) && call.name.equals("update") && call.desc.equals("(Lnet/minecraft/client/DeltaTracker;)V")) calls++;
            require(calls == 1, "unfamiliar original renderer update opcode"); originalRunTick = renderRunTick; originalRenderFrame = render.instructions;
        } else if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeGameplayTickMixin")) {
            require(!gameplaySourceCaptured&&targetClass.name.equals(MC),"duplicate original native source capture");gameplaySourceCaptured=true;
            var runBody=originalSource(targetClass,"run","()V","runTick","(Z)V").instructions;
            var tickBody=originalSource(targetClass,"runTick","(Z)V","tick","()V").instructions;
            require((originalRun==null||runBody==originalRun)&&(originalRunTick==null||tickBody==originalRunTick),"gameplay did not retain original client bodies");
            originalRun=runBody;originalRunTick=tickBody;
            var gameplayTickBody = uniqueMethod(targetClass, "tick", "()V").instructions;
            require(originalCameraTick == null || originalCameraTick == gameplayTickBody, "camera tick did not retain original tick body");
            originalTick = gameplayTickBody;
        }
    }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
        observeBindingBody(targetClass, () -> postApplyOriginal(targetClassName, targetClass, mixinClassName, info));
    }
    private void postApplyOriginal(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
        if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeGameplayTickMixin")) bindNativeEndOpcode(targetClass);
        else if (mixinClassName.equals("dev.veltraph.client.mixin.RootNativeFabricEndTickMixin")) bindSubscriberLoop(targetClass);
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Veltraph native tick ingress: " + reason);
    }
    private static boolean fabricEndHandler(MethodNode method) {
        if (!method.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
                || (method.access & Opcodes.ACC_STATIC) != 0) return false;
        return mergedFromFabric(method.visibleAnnotations) || mergedFromFabric(method.invisibleAnnotations);
    }
    private static boolean mergedFromFabric(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (var annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                if (annotation.values.get(i).equals("mixin")
                        && annotation.values.get(i + 1).equals("net.fabricmc.fabric.mixin.event.lifecycle.client.MinecraftMixin")) return true;
        }
        return false;
    }
    private static boolean endCall(AbstractInsnNode node) {
        return node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEINTERFACE
                && call.owner.equals(END) && call.name.equals("onEndTick") && call.desc.equals(CALLBACK) && call.itf;
    }
    private void bindNativeEndOpcode(ClassNode target) {
        require(target.name.equals(MC), "wrong native target");
        MethodNode handler = null; MethodInsnNode operation = null; int count = 0;
        for (var method : target.methods) if (fabricEndHandler(method)) {
            for (var node : method.instructions) if (endCall(node)) { handler = method; operation = (MethodInsnNode) node; count++; }
        }
        require(count == 1, "expected one original Fabric Minecraft EndTick opcode, got " + count);
        var executable = new java.util.ArrayList<AbstractInsnNode>();
        for (var node : handler.instructions) if (node.getOpcode() >= 0) executable.add(node);
        if (executable.size() != 6) observeNativeEndHandler(target, handler, executable, operation);
        require(executable.size() == 6, "unfamiliar native EndTick body");
        require(executable.get(0) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals(EVENTS) && field.name.equals("END_CLIENT_TICK")
                && field.desc.equals("Lnet/fabricmc/fabric/api/event/Event;"), "unexpected native event field source");
        require(executable.get(1) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals("net/fabricmc/fabric/api/event/Event") && call.name.equals("invoker")
                && call.desc.equals("()Ljava/lang/Object;") && !call.itf, "unexpected native event invoker");
        require(executable.get(2) instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                && cast.desc.equals(END), "unexpected native event cast");
        require(executable.get(3) instanceof VarInsnNode self && self.getOpcode() == Opcodes.ALOAD && self.var == 0,
                "unexpected native client receiver");
        // Mixin removes the redundant target CHECKCAST after instance ALOAD 0 before this hook.
        require(executable.get(4) == operation && executable.get(5).getOpcode() == Opcodes.RETURN,
                "unexpected native EndTick operation or continuation");
        nativeEndHandler = handler;
        handler.instructions.set(operation, new MethodInsnNode(Opcodes.INVOKESTATIC, MC, "veltraph$nativeEndTickOpcode",
                "(L" + END + ";L" + MC + ";)V", false));
        target.methods.add(observedOperation("veltraph$nativeEndTickOpcode", "(L" + END + ";L" + MC + ";)V",
                new String[]{END, MC}, "EventFrame", "enterOriginalEndTickOpcode", "(L" + END + ";L" + MC + ";)",
                "leaveOriginalEndTickOpcode", new MethodInsnNode(Opcodes.INVOKEINTERFACE, END, "onEndTick", CALLBACK, true)));
    }
    /** Diagnostic only: observe the already selected handler without changing its instructions or gates. */
    static void observeNativeEndHandler(ClassNode target, MethodNode handler,
            List<AbstractInsnNode> executable, MethodInsnNode operation) {
        try {
            System.err.println("VELTRAPH_NATIVE_END_HANDLER target=" + target.name + " handler=" + handler.name
                    + handler.desc + " executableCount=" + executable.size());
            int limit = Math.min(executable.size(), 24);
            for (int i = 0; i < limit; i++) {
                var node = executable.get(i);
                String operand = "";
                if (node instanceof FieldInsnNode field) operand = " field=" + field.owner + "." + field.name + ":" + field.desc;
                else if (node instanceof MethodInsnNode call) operand = " call=" + call.owner + "." + call.name + call.desc + " interface=" + call.itf;
                else if (node instanceof TypeInsnNode type) operand = " type=" + type.desc;
                else if (node instanceof VarInsnNode variable) operand = " variable=" + variable.var;
                System.err.println("VELTRAPH_NATIVE_END_HANDLER opcodeIndex=" + i + " opcode=" + node.getOpcode()
                        + operand + " originalOperation=" + (node == operation));
            }
            if (executable.size() > limit) System.err.println("VELTRAPH_NATIVE_END_HANDLER truncatedAfter=" + limit);
        } catch (Throwable diagnosticUnavailable) {
            // The original structural assertion must retain its own failure even if reporting is unavailable.
        }
    }
    private static void bindSubscriberLoop(ClassNode target) {
        require(target.name.equals(EVENTS), "wrong Fabric loop target");
        MethodNode body = null; int matches = 0;
        for (var method : target.methods) {
            require(!method.name.equals(BODY), "duplicate subscriber loop transformation");
            if (method.name.equals("lambda$static$3") && method.desc.equals(LOOP_DESC)) { body = method; matches++; }
        }
        require(matches == 1 && (body.access & Opcodes.ACC_STATIC) != 0, "expected pinned static EndTick loop");
        MethodInsnNode operation = null; int count = 0;
        for (var node : body.instructions) if (endCall(node)) { operation = (MethodInsnNode) node; count++; }
        require(count == 1, "expected one original Fabric subscriber opcode");
        body.instructions.set(operation, new MethodInsnNode(Opcodes.INVOKESTATIC, EVENTS, "veltraph$nativeEndTickSubscriberOpcode",
                "(L" + END + ";L" + MC + ";)V", false));
        String oldName = body.name; body.name = BODY;
        target.methods.add(observedOperation("veltraph$nativeEndTickSubscriberOpcode", "(L" + END + ";L" + MC + ";)V",
                new String[]{END, MC}, "SubscriberFrame", "enterOriginalSubscriber", "(L" + END + ";L" + MC + ";)",
                "leaveOriginalSubscriber", new MethodInsnNode(Opcodes.INVOKEINTERFACE, END, "onEndTick", CALLBACK, true)));
        var wrapper = observedOperation(oldName, LOOP_DESC, new String[]{"[L" + END + ";", MC}, "LoopFrame",
                "enterOriginalEndTickLoop", "(L" + MC + ";)", "leaveOriginalEndTickLoop",
                new MethodInsnNode(Opcodes.INVOKESTATIC, EVENTS, BODY, LOOP_DESC, false));
        wrapper.access = body.access; target.methods.add(wrapper);
    }
    /** Original invocation lies outside both observer catch regions; it is never retried or replaced. */
    private static MethodNode observedOperation(String name, String desc, String[] arguments, String frameName,
                                               String enter, String enterArgs, String leave, MethodInsnNode original) {
        String frame = PHASE + "$" + frameName;
        var method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
        var setup = new LabelNode(); var setupEnd = new LabelNode(); var setupFail = new LabelNode();
        var call = new LabelNode(); var callEnd = new LabelNode(); var callFail = new LabelNode();
        var close = new LabelNode(); var closeEnd = new LabelNode(); var closeFail = new LabelNode();
        var exceptionalClose = new LabelNode(); var exceptionalCloseEnd = new LabelNode(); var exceptionalCloseFail = new LabelNode(); var rethrow = new LabelNode();
        var code = method.instructions;
        code.add(new InsnNode(Opcodes.ACONST_NULL)); code.add(new VarInsnNode(Opcodes.ASTORE, 2));
        code.add(setup);
        if (!frameName.equals("LoopFrame")) code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PHASE, enter, enterArgs + "L" + frame + ";", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 2)); code.add(setupEnd); code.add(new JumpInsnNode(Opcodes.GOTO, call));
        code.add(setupFail); code.add(new FrameNode(Opcodes.F_FULL, 3, new Object[]{arguments[0], arguments[1], Opcodes.NULL}, 1, new Object[]{"java/lang/Throwable"}));
        code.add(new InsnNode(Opcodes.POP)); code.add(new JumpInsnNode(Opcodes.GOTO, call));
        code.add(call); code.add(new FrameNode(Opcodes.F_FULL, 3, new Object[]{arguments[0], arguments[1], frame}, 0, null));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new VarInsnNode(Opcodes.ALOAD, 1)); code.add(original); code.add(callEnd);
        code.add(close); code.add(new VarInsnNode(Opcodes.ALOAD, 2)); code.add(new InsnNode(Opcodes.ICONST_1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PHASE, leave, "(L" + frame + ";Z)V", false)); code.add(closeEnd); code.add(new InsnNode(Opcodes.RETURN));
        code.add(closeFail); code.add(new FrameNode(Opcodes.F_FULL, 3, new Object[]{arguments[0], arguments[1], frame}, 1, new Object[]{"java/lang/Throwable"}));
        code.add(new InsnNode(Opcodes.POP)); code.add(new InsnNode(Opcodes.RETURN));
        code.add(callFail); code.add(new FrameNode(Opcodes.F_FULL, 3, new Object[]{arguments[0], arguments[1], frame}, 1, new Object[]{"java/lang/Throwable"}));
        code.add(new VarInsnNode(Opcodes.ASTORE, 3)); code.add(exceptionalClose);
        code.add(new VarInsnNode(Opcodes.ALOAD, 2)); code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PHASE, leave, "(L" + frame + ";Z)V", false));
        code.add(exceptionalCloseEnd); code.add(new JumpInsnNode(Opcodes.GOTO, rethrow));
        code.add(exceptionalCloseFail); code.add(new FrameNode(Opcodes.F_FULL, 4, new Object[]{arguments[0], arguments[1], frame, "java/lang/Throwable"}, 1, new Object[]{"java/lang/Throwable"}));
        code.add(new InsnNode(Opcodes.POP)); code.add(rethrow);
        code.add(new FrameNode(Opcodes.F_FULL, 4, new Object[]{arguments[0], arguments[1], frame, "java/lang/Throwable"}, 0, null));
        code.add(new VarInsnNode(Opcodes.ALOAD, 3)); code.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(setup, setupEnd, setupFail, "java/lang/Throwable"));
        method.tryCatchBlocks.add(new TryCatchBlockNode(call, callEnd, callFail, "java/lang/Throwable"));
        method.tryCatchBlocks.add(new TryCatchBlockNode(close, closeEnd, closeFail, "java/lang/Throwable"));
        method.tryCatchBlocks.add(new TryCatchBlockNode(exceptionalClose, exceptionalCloseEnd, exceptionalCloseFail, "java/lang/Throwable"));
        method.maxStack = 2; method.maxLocals = 4; return method;
    }
    private static MethodNode uniqueMethod(ClassNode target, String name, String desc) {
        MethodNode found = null;
        for (var method : target.methods) if (method.name.equals(name) && method.desc.equals(desc)) {
            require(found == null, "duplicate original method"); found = method;
        }
        require(found != null, "missing original method " + name); return found;
    }
    private static MethodNode originalSource(ClassNode target, String name, String desc, String callee, String calleeDesc) {
        var method = uniqueMethod(target, name, desc); int count = 0;
        for (var node : method.instructions) if (node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(MC) && call.name.equals(callee) && call.desc.equals(calleeDesc)) count++;
        require(count == 1, "unfamiliar original native opcode " + name); return method;
    }
    private static MethodNode retainedBody(ClassNode target, InsnList original, String desc) {
        MethodNode found = null;
        for (var method : target.methods) if (method.instructions == original) {
            require(found == null && method.desc.equals(desc), "ambiguous or changed original opcode body"); found = method;
        }
        require(found != null, "original native body was not conserved"); return found;
    }
    private static boolean mergedSource(MethodNode method, String mixin) {
        for (var list : java.util.Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations)) if (list != null)
            for (var annotation : list) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
                for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                    if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
        return false;
    }
    private static MethodNode phaseHook(ClassNode target, String enter) {
        MethodNode found = null;
        for (var method : target.methods) if (mergedSource(method, "dev.veltraph.client.mixin.RootNativeGameplayTickMixin"))
            for (var node : method.instructions) if (node instanceof MethodInsnNode call && call.owner.equals(PHASE) && call.name.equals(enter)) {
                require(found == null, "duplicate actual phase hook"); found = method;
            }
        require(found != null, "missing actual phase hook"); return found;
    }
    private static void attachBinding(MethodNode hook, MethodNode body, String kind, String original) {
        int count = 0;
        for (var node : body.instructions) if (node instanceof MethodInsnNode call && call.owner.equals(MC)
                && call.name.equals(hook.name) && call.desc.equals(hook.desc)) count++;
        require(count == 1, "original opcode did not call its exact conserved hook");
        var annotation = new AnnotationNode("Ldev/veltraph/client/service/mc/RootNativeTickOpcodeBinding;");
        annotation.values = new java.util.ArrayList<>(List.of("kind", kind, "original", original,
                "sourceName", body.name, "sourceDescriptor", body.desc));
        if (hook.visibleAnnotations == null) hook.visibleAnnotations = new java.util.ArrayList<>();
        hook.visibleAnnotations.add(annotation);
    }
    private static MethodNode cameraHook(ClassNode target, String mixin, String enter) {
        MethodNode found = null;
        for (var method : target.methods) if (mergedSource(method, mixin))
            for (var node : method.instructions) if (node instanceof MethodInsnNode call && call.owner.equals(CAMERA_PHASE) && call.name.equals(enter)) {
                require(found == null, "duplicate actual camera phase hook"); found = method;
            }
        require(found != null, "missing actual camera phase hook"); return found;
    }
    private static void attachCameraBinding(ClassNode target, MethodNode hook, MethodNode body, String kind, String original) {
        int count = 0;
        for (var node : body.instructions) if (node instanceof MethodInsnNode call && call.owner.equals(target.name)
                && call.name.equals(hook.name) && call.desc.equals(hook.desc)) count++;
        require(count == 1, "original camera opcode did not call exact conserved hook");
        var annotation = new AnnotationNode("Ldev/veltraph/client/service/mc/RootNativeCameraOpcodeBinding;");
        annotation.values = new java.util.ArrayList<>(List.of("kind", kind, "original", original,
                "sourceName", body.name, "sourceDescriptor", body.desc));
        if (hook.visibleAnnotations == null) hook.visibleAnnotations = new java.util.ArrayList<>();hook.visibleAnnotations.add(annotation);
    }
    private static void refuseExistingCameraBindings(ClassNode target) {
        for (var method : target.methods) for (var list : java.util.Arrays.asList(method.visibleAnnotations,method.invisibleAnnotations)) if (list != null)
            for (var annotation : list) require(!annotation.desc.equals("Ldev/veltraph/client/service/mc/RootNativeCameraOpcodeBinding;"), "foreign/duplicate camera source binding");
    }
    private static MethodNode sdlHook(ClassNode target,String mixin,String enter){
        MethodNode found=null;
        for(var method:target.methods)if(mergedSource(method,mixin))for(var node:method.instructions)
            if(node instanceof MethodInsnNode call&&call.owner.equals(SDL_PHASE)&&call.name.equals(enter)){
                require(found==null,"duplicate actual SDL source hook");found=method;}
        require(found!=null,"missing actual SDL source hook");return found;
    }
    private static MethodNode uniqueHookCaller(ClassNode target,MethodNode hook,String desc){
        MethodNode found=null;int count=0;
        for(var method:target.methods)for(var node:method.instructions)if(node instanceof MethodInsnNode call
            &&call.owner.equals(target.name)&&call.name.equals(hook.name)&&call.desc.equals(hook.desc)){found=method;count++;}
        require(count==1&&found!=null&&found.desc.equals(desc),"unfamiliar exact SDL whole-run wrapper opcode");return found;
    }
    private static void attachSdlBinding(ClassNode target,MethodNode hook,MethodNode body,String kind){
        int count=0;
        for(var node:body.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals(target.name)
            &&call.name.equals(hook.name)&&call.desc.equals(hook.desc))count++;
        require(count==1,"original SDL opcode did not call exact conserved hook");
        var annotation=new AnnotationNode("Ldev/veltraph/client/service/mc/RootNativeSdlOpcodeBinding;");
        annotation.values=new java.util.ArrayList<>(List.of("kind",kind,"sourceName",body.name,"sourceDescriptor",body.desc));
        if(hook.visibleAnnotations==null)hook.visibleAnnotations=new java.util.ArrayList<>();hook.visibleAnnotations.add(annotation);
    }
    private static void refuseExistingSdlBindings(ClassNode target){
        for(var method:target.methods)for(var list:java.util.Arrays.asList(method.visibleAnnotations,method.invisibleAnnotations))if(list!=null)
            for(var annotation:list)require(!annotation.desc.equals("Ldev/veltraph/client/service/mc/RootNativeSdlOpcodeBinding;"),"foreign/duplicate SDL source binding");
    }
    private static boolean inputEnterHook(MethodNode hook,InputRole role){
        if(!mergedSource(hook,role.mixin))return false;int count=0;
        for(var node:hook.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals(SDL_INPUT)&&call.name.equals(role.enter))count++;
        if(count!=1)return false;
        if(role.kind.equals("KEY_DISPATCH")||role.kind.equals("BUTTON_DISPATCH")||role.kind.equals("MOTION_DISPATCH")){
            String marker=role.kind.substring(0,role.kind.indexOf('_'));marker=marker.equals("KEY")?"KEYBOARD":marker;
            int kinds=0;for(var node:hook.instructions)if(node instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC
                &&field.owner.equals(SDL_INPUT+"$Kind")&&field.name.equals(marker))kinds++;
            if(kinds!=1)return false;
        }
        return true;
    }
    private static void attachInputBinding(ClassNode target,InputSource source){
        var role=source.role;var body=role.whole?source.original:retainedBody(target,source.instructions,role.descriptor);
        require(target.methods.contains(body)&&body.desc.equals(role.descriptor),"original SDL input body not conserved");
        MethodNode hook=null;int calls=0;
        for(var node:body.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals(target.name)){
            for(var candidate:target.methods)if(candidate.name.equals(call.name)&&candidate.desc.equals(call.desc)&&inputEnterHook(candidate,role)){
                require(hook==null||hook==candidate,"ambiguous original SDL input opcode producer");hook=candidate;calls++;
            }
        }
        require(hook!=null&&calls==role.count,"wrong original SDL input opcode count for "+role.kind);
        if(role.whole)require(uniqueHookCaller(target,hook,role.descriptor)==body,"whole SDL input wrapper source not exact");
        var annotation=new AnnotationNode("Ldev/veltraph/client/observation/nativeinput/NativeSdlInputOpcodeBinding;");
        annotation.values=new java.util.ArrayList<>(List.of("kind",role.kind,"sourceName",body.name,"sourceDescriptor",body.desc));
        if(hook.visibleAnnotations==null)hook.visibleAnnotations=new java.util.ArrayList<>();hook.visibleAnnotations.add(annotation);
    }
    private void bindOriginalInputSources(ClassNode target){
        for(var method:target.methods)for(var list:java.util.Arrays.asList(method.visibleAnnotations,method.invisibleAnnotations))if(list!=null)
            for(var annotation:list)require(!annotation.desc.equals("Ldev/veltraph/client/observation/nativeinput/NativeSdlInputOpcodeBinding;"),"foreign/duplicate original SDL input binding");
        int expected=0,actual=0;
        for(var role:INPUT_ROLES)if(role.target.equals(target.name)){
            expected++;var source=originalInputSources.get(role.kind);require(source!=null,"missing retained original input role "+role.kind);
            attachInputBinding(target,source);actual++;
        }
        require(expected==actual&&(target.name.equals(SDL_HANDLER)?actual==15:actual==1),"incomplete original SDL input source map");
    }
    private static boolean exactVoidOperation(InvokeDynamicInsnNode indy){
        return indy.name.equals("call")&&Type.getReturnType(indy.desc).equals(Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation"))
            &&indy.bsm.getTag()==Opcodes.H_INVOKESTATIC&&indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
            &&indy.bsm.getName().equals("metafactory")&&!indy.bsm.isInterface()
            &&indy.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
            &&indy.bsmArgs.length==3&&indy.bsmArgs[0]instanceof Type sam&&sam.equals(Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Object;"))
            &&indy.bsmArgs[1]instanceof Handle&&indy.bsmArgs[2]instanceof Type instantiated
            &&instantiated.equals(Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Void;"));
    }
    private static int originalMouseTargetCount(MethodNode method,String owner,String name,String descriptor){
        int count=0;for(var node:method.instructions)if(node instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
            &&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor)&&!call.itf)count++;return count;
    }
    private static MethodNode originalMouseInvoker(ClassNode caller,InsnList original,String sourceDescriptor,String targetOwner,String targetName,String targetDescriptor){
        var source=retainedBody(caller,original,sourceDescriptor);int direct=originalMouseTargetCount(source,targetOwner,targetName,targetDescriptor);
        require(direct<=1,"duplicate direct original mouse target");
        var matches=new java.util.ArrayList<MethodNode>();var inspected=new java.util.HashSet<Handle>();
        for(var node:source.instructions)if(node instanceof InvokeDynamicInsnNode indy&&exactVoidOperation(indy)){
            var handle=(Handle)indy.bsmArgs[1];
            require(handle.getOwner().equals(caller.name)&&(handle.getTag()==Opcodes.H_INVOKESPECIAL||handle.getTag()==Opcodes.H_INVOKESTATIC)
                &&handle.isInterface()==((caller.access&Opcodes.ACC_INTERFACE)!=0),"foreign/unsupported exact original operation Handle");
            if(!inspected.add(handle))continue;
            var bridge=uniqueMethod(caller,handle.getName(),handle.getDesc());
            require((bridge.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC))==(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC)
                &&((bridge.access&Opcodes.ACC_STATIC)!=0)==(handle.getTag()==Opcodes.H_INVOKESTATIC),"original operation Handle/access mismatch");
            int count=originalMouseTargetCount(bridge,targetOwner,targetName,targetDescriptor);
            require(count<=1,"duplicate original mouse opcode in invoker");if(count==1)matches.add(bridge);
        }
        require(direct+matches.size()==1,"missing/ambiguous structurally resolved original mouse invoker");
        return direct==1?source:matches.get(0);
    }
    private static MethodNode mouseHook(ClassNode target,String mixin,String enter){
        MethodNode found=null;
        for(var method:target.methods)if(mergedSource(method,mixin))for(var node:method.instructions)
            if(node instanceof MethodInsnNode call&&call.owner.equals(MOUSE_PHASE)&&call.name.equals(enter)){
                require(found==null,"duplicate actual mouse entry hook");found=method;}
        require(found!=null,"missing actual mouse entry hook");return found;
    }
    private static void attachMouseCallee(ClassNode target,MethodNode original,String mixin,String enter,String kind,String descriptor){
        require(original!=null&&target.methods.contains(original)&&original.desc.equals(descriptor),"original whole mouse wrapper not conserved");
        var hook=mouseHook(target,mixin,enter);
        require(uniqueHookCaller(target,hook,descriptor)==original,"whole mouse hook caller not exact original wrapper");
        var annotation=new AnnotationNode("Ldev/veltraph/client/observation/nativeinput/NativeMouseInvocationBinding;");
        annotation.values=new java.util.ArrayList<>(List.of("kind",kind,"sourceName",original.name,"sourceDescriptor",original.desc));
        if(hook.visibleAnnotations==null)hook.visibleAnnotations=new java.util.ArrayList<>();hook.visibleAnnotations.add(annotation);
    }
    private static void attachMouseInvoker(MethodNode invoker,String kind){
        var annotation=new AnnotationNode("Ldev/veltraph/client/observation/nativeinput/NativeMouseOriginalInvokerBinding;");
        annotation.values=new java.util.ArrayList<>(List.of("kind",kind));
        if(invoker.visibleAnnotations==null)invoker.visibleAnnotations=new java.util.ArrayList<>();invoker.visibleAnnotations.add(annotation);
    }
    private static void refuseExistingMouseBindings(ClassNode target){
        for(var method:target.methods)for(var list:java.util.Arrays.asList(method.visibleAnnotations,method.invisibleAnnotations))if(list!=null)
            for(var annotation:list)require(!annotation.desc.equals("Ldev/veltraph/client/observation/nativeinput/NativeMouseInvocationBinding;")
                &&!annotation.desc.equals("Ldev/veltraph/client/observation/nativeinput/NativeMouseOriginalInvokerBinding;"),"foreign/duplicate mouse source binding");
    }
    /** Diagnostic only: do not replace, suppress or wrap the original failed binding operation. */
    static void observeBindingBody(ClassNode target, Runnable originalBinding) {
        try { originalBinding.run(); }
        catch (RuntimeException | Error original) {
            try {
                System.err.println("VELTRAPH_ORIGINAL_BINDING_FAILURE target=" + (target == null ? "<unavailable>" : target.name)
                        + " exceptionType=" + original.getClass().getName());
                original.printStackTrace(System.err);
            } catch (Throwable diagnosticUnavailable) { /* Preserve the exact original exception if logging fails. */ }
            throw original;
        }
    }
    private final class FinalSourceBindings implements IExtension {
        @Override public boolean checkActive(MixinEnvironment environment) { return true; }
        @Override public void preApply(ITargetClassContext context) {}
        @Override public void postApply(ITargetClassContext context) {
            var target = context.getClassNode();
            observeBindingBody(target, () -> bindOriginalSources(target));
        }
        private void bindOriginalSources(ClassNode target) { if (!target.name.equals(MC) && !target.name.equals(RENDERER) && !target.name.equals(MAIN) && !target.name.equals(RENDER_SYSTEM) && !target.name.equals(SDL_HANDLER) && !target.name.equals(TASK_LOOP) && !target.name.equals(MOUSE) && !target.name.equals(ENTITY)) return;
            var extensions = MixinInternals.getExtensions().getActiveExtensions();
            require(extensions.indexOf(wrapMethodExtension) >= 0 && extensions.indexOf(this) > extensions.indexOf(wrapMethodExtension),
                    "final source binding was not after the retained deferred WrapMethod transformer");
            if(target.name.equals(MOUSE)||target.name.equals(ENTITY)){
                refuseExistingMouseBindings(target);
                if(target.name.equals(ENTITY)){
                    attachMouseCallee(target,originalEntityTurnMethod,"dev.veltraph.client.mixin.NativeManualMouseTurnOpcodeMixin","beginEntityTurn","ENTITY_TURN","(DD)V");return;
                }
                attachMouseCallee(target,originalAccumulationMethod,"dev.veltraph.client.mixin.NativeMouseAccumulationEntryMixin","beginAccumulation","ACCUMULATION","()V");
                attachMouseCallee(target,originalTurnPlayerMethod,"dev.veltraph.client.mixin.NativeMouseTurnPlayerEntryMixin","beginTurnPlayer","TURN_PLAYER","(D)V");
                attachMouseInvoker(originalMouseInvoker(target,originalAccumulation,"()V",MOUSE,"turnPlayer","(D)V"),"TURN_PLAYER");
                attachMouseInvoker(originalMouseInvoker(target,originalTurnPlayer,"(D)V","net/minecraft/client/player/LocalPlayer","turn","(DD)V"),"ENTITY_TURN");return;
            }
            if(target.name.equals(SDL_HANDLER)||target.name.equals(TASK_LOOP)){bindOriginalInputSources(target);return;}
            refuseExistingSdlBindings(target);
            if(target.name.equals(MAIN)){
                require(originalMainRun!=null,"missing retained original Main run source");
                attachSdlBinding(target,sdlHook(target,"dev.veltraph.client.mixin.RootNativeSdlMainMixin","enterOriginalMainRunOpcode"),
                    retainedBody(target,originalMainRun,"([Ljava/lang/String;)V"),"MAIN_RUN");return;
            }
            if(target.name.equals(RENDER_SYSTEM)){
                require(originalSdlPoll!=null,"missing retained original SDL inner source");
                attachSdlBinding(target,sdlHook(target,"dev.veltraph.client.mixin.RootNativeSdlPollMixin","enterOriginalSdlInnerOpcode"),
                    retainedBody(target,originalSdlPoll,"(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V"),"SDL_INNER");return;
            }
            refuseExistingCameraBindings(target);
            if (target.name.equals(RENDERER)) {
                require(originalCameraFrame != null, "missing retained original frame source");
                attachCameraBinding(target, cameraHook(target,"dev.veltraph.client.mixin.OwnedRotationFrameMixin","enterFrame"),
                        retainedBody(target,originalCameraFrame,"(Lnet/minecraft/client/DeltaTracker;)V"),"FRAME","update");return;
            }
            require(originalRun != null && originalRunTick != null && originalTick != null && nativeEndHandler != null, "missing retained original source");
            for (var method : target.methods) if (method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) require(!annotation.desc.equals("Ldev/veltraph/client/service/mc/RootNativeTickOpcodeBinding;"), "foreign/duplicate opcode binding");
            attachBinding(phaseHook(target, "enterRunTickOpcode"), retainedBody(target, originalRun, "()V"), "RUN", "run");
            attachBinding(phaseHook(target, "enterTickOpcode"), retainedBody(target, originalRunTick, "(Z)V"), "TICK", "runTick");
            require(target.methods.contains(nativeEndHandler), "original Fabric event handler not conserved");
            attachBinding(nativeEndHandler, retainedBody(target, originalTick, "()V"), "END", "tick");
            require(originalCameraTick == originalTick && originalRenderFrame != null, "missing original camera tick/render sources");
            attachCameraBinding(target,cameraHook(target,"dev.veltraph.client.mixin.OwnedRotationTickMixin","enterTick"),
                    retainedBody(target,originalCameraTick,"()V"),"TICK","tick");
            attachCameraBinding(target,cameraHook(target,"dev.veltraph.client.mixin.RootNativeCameraIngressMixin","enterRenderOpcode"),
                    retainedBody(target,originalRunTick,"(Z)V"),"RENDER","runTick");
            attachCameraBinding(target,cameraHook(target,"dev.veltraph.client.mixin.RootNativeCameraIngressMixin","enterFrameOpcode"),
                    retainedBody(target,originalRenderFrame,"(Z)V"),"UPDATE","renderFrame");
            refuseExistingMouseBindings(target);
            attachMouseInvoker(originalMouseInvoker(target,originalRunTick,"(Z)V",MOUSE,"handleAccumulatedMovement","()V"),"ACCUMULATION");
            var whole=sdlHook(target,"dev.veltraph.client.mixin.RootNativeSdlClientMixin","enterOriginalClientRun");
            attachSdlBinding(target,whole,uniqueHookCaller(target,whole,"()V"),"WHOLE_RUN");
            attachSdlBinding(target,sdlHook(target,"dev.veltraph.client.mixin.RootNativeSdlClientMixin","enterOriginalSdlOuterOpcode"),
                retainedBody(target,originalRun,"()V"),"SDL_OUTER");
            attachSdlBinding(target,sdlHook(target,"dev.veltraph.client.mixin.RootNativeSdlClientMixin","enterMouseAccumulationOpcode"),
                retainedBody(target,originalRunTick,"(Z)V"),"MOUSE_ACCUMULATE");
        }
        @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    }
}
