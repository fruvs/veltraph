package dev.veltraph.client.observation.unexpected;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

/** Passive client-thread adapter. It never writes native state or weakens another safety monitor. */
public final class McUnexpectedBehaviorMonitor implements AutoCloseable {
    private final Minecraft client;
    private final Supplier<Activation> activation;
    private final UnexpectedBehaviorMonitor monitor;
    private final McUnexpectedBehaviorMonitor priorObserver;
    private Object player,world,connection;
    private long playerEpoch,worldEpoch,connectionEpoch;
    private Context current;
    private Snapshot previous;
    private dev.veltraph.client.rewarp.mc.IssuedFarmExpectedObservation issuedFarmOrigin,issuedFarmViewOrigin;
    private Object sampledPlayer,sampledWorld,sampledConnection;
    private Context sampledContext;
    private boolean observingTick;
    private boolean wasFalling;
    private long lastOriginTime=Long.MIN_VALUE;
    private record HeldOrigin(boolean down,String binding,BooleanSupplier actualHistoryCurrent) {
        boolean current(KeyMapping key){try{return key.isDown()==down&&binding.equals(key.saveString())&&actualHistoryCurrent.getAsBoolean();}catch(RuntimeException unavailable){return false;}}
    }
    private HeldOrigin nativeHeldOrigin(NativeInputFrame frame,KeyMapping key,boolean down){
        if((key.getClass()!=KeyMapping.class&&key.getClass()!=ToggleKeyMapping.class)||!(key instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier))return null;
        var exact=carrier.normalIdleKeySnapshot();if(exact==null||!carrier.normalIdleKeyCurrent(exact)||key.isDown()!=down)return null;
        return new HeldOrigin(down,key.saveString(),()->frame.nativeCurrent()&&Objects.equals(current,frame.context)&&carrier.normalIdleKeyCurrent(exact));
    }
    private final PendingNativeMouseOrigin nativeMouseOrigin=new PendingNativeMouseOrigin();
    private final IdentityHashMap<KeyMapping,HeldOrigin> modHeld=new IdentityHashMap<>();
    private final IdentityHashMap<KeyMapping,HeldOrigin> nativeHeld=new IdentityHashMap<>();
    private final IdentityHashMap<KeyMapping,HeldOrigin> nativeMouseHeld=new IdentityHashMap<>();
    private final PendingNativeInputOrigin pendingKeyboard=new PendingNativeInputOrigin(),pendingButton=new PendingNativeInputOrigin();
    private NativeInputFrame keyboardFrame,buttonFrame;
    private long keyboardWindow,buttonWindow;private int keyboardAction,buttonAction;
    private KeyEvent keyboardEvent;private net.minecraft.client.input.MouseButtonInfo buttonEvent;
    /** Captures actual source/native fields before callback-capable activation reads. Not authority. */
    private final class NativeInputFrame {
        final Object player=client.player,world=client.level,connection=client.getConnection(),options=client.options,screen=client.gui.screen(),store=dev.veltraph.client.VeltraphBootstrap.config();
        final Object nativeConnection=client.getConnection()==null?null:client.getConnection().getConnection();
        final dev.veltraph.client.core.ModuleManager registry=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle=registry==null?null:registry.lifecycleSnapshot();
        final KeyMapping[] keys=inputKeys();final String[] bindings=java.util.Arrays.stream(keys).map(KeyMapping::saveString).toArray(String[]::new);
        Context context;long matched,created;
        boolean nativeCurrent(){
            if(!client.isSameThread()||player!=client.player||world!=client.level||connection!=client.getConnection()||options!=client.options||screen!=client.gui.screen()
                    ||nativeConnection==null||nativeConnection!=client.getConnection().getConnection()||!((net.minecraft.network.Connection)nativeConnection).isConnected()
                    ||store!=dev.veltraph.client.VeltraphBootstrap.config()||registry==null||registry!=dev.veltraph.client.VeltraphBootstrap.moduleManager()
                    ||lifecycle==null||!lifecycle.known()||!registry.lifecycleCurrent(lifecycle))return false;
            var actual=inputKeys();for(int i=0;i<keys.length;i++)if(actual[i]!=keys[i]||!bindings[i].equals(keys[i].saveString()))return false;return true;
        }
    }
    private record Snapshot(Context context,Value position,Value rotation,Value mode,Value selected,Value inventory,Value block,Value inputs) {}
    public McUnexpectedBehaviorMonitor(Minecraft client,Supplier<Activation> activation,Consumer<Finding> sink) {
        this.client=Objects.requireNonNull(client);this.activation=Objects.requireNonNull(activation);monitor=new UnexpectedBehaviorMonitor(sink);
        priorObserver=UnexpectedActions.attach(this);
    }
    private Context context(long now) {
        if(now<lastOriginTime){modHeld.clear();nativeHeld.clear();nativeMouseHeld.clear();pendingKeyboard.clear();pendingButton.clear();keyboardFrame=null;buttonFrame=null;keyboardEvent=null;buttonEvent=null;nativeMouseOrigin.clear();}
        lastOriginTime=now;
        if(client.player!=player) {player=client.player;playerEpoch++;}
        if(client.level!=world) {world=client.level;worldEpoch++;}
        if(client.getConnection()!=connection) {connection=client.getConnection();connectionEpoch++;}
        var next=new Context(activation.get(),playerEpoch,worldEpoch,connectionEpoch);
        if(!next.equals(current)) {modHeld.clear();nativeHeld.clear();nativeMouseHeld.clear();pendingKeyboard.clear();pendingButton.clear();keyboardFrame=null;buttonFrame=null;keyboardEvent=null;buttonEvent=null;nativeMouseOrigin.clear();wasFalling=false;current=next;}
        monitor.context(current,now);return current;
    }
    private boolean available() {return client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null;}
    public void tick(long now) { tick(now,null); }
    /** A typed actual native transition proof; this overload cannot manufacture an expected transition. */
    public void tick(long now,dev.veltraph.client.rewarp.mc.IssuedFarmExpectedObservation exact) {
        if(!client.isSameThread())throw new IllegalStateException("Observe on the client thread");
        if(observingTick)throw new IllegalStateException("Native observer tick reentered");
        observingTick=true;issuedFarmOrigin=exact;issuedFarmViewOrigin=null;
        sampledPlayer=player;sampledWorld=world;sampledConnection=connection;sampledContext=current;
        try { tickObserved(now); }
        finally { issuedFarmOrigin=null;issuedFarmViewOrigin=null;sampledPlayer=null;sampledWorld=null;sampledConnection=null;sampledContext=null;observingTick=false; }
    }
    private void tickObserved(long now) {
        if(!client.isSameThread())throw new IllegalStateException("Observe on the client thread");
        var ctx=context(now);
        if(keyboardFrame!=null&&(now<keyboardFrame.created||now-keyboardFrame.created<0||now-keyboardFrame.created>200)){keyboardFrame=null;keyboardEvent=null;pendingKeyboard.clear();}
        if(buttonFrame!=null&&(now<buttonFrame.created||now-buttonFrame.created<0||now-buttonFrame.created>200)){buttonFrame=null;buttonEvent=null;pendingButton.clear();}
        if(!available()) {if(previous!=null&&!ctx.sameNativeContext(previous.context))observe(Kind.CONTEXT,Value.number(previous.context.world()),Value.number(ctx.world()),now);previous=null;return;}
        var p=client.player;
        // A canceled or uninstrumented write cannot leave an origin claim on a different held state.
        pruneHeld(modHeld,Cause.MOD_MOVEMENT_INPUT);
        pruneHeld(nativeHeld,Cause.NATIVE_KEY_EVENT);pruneHeld(nativeMouseHeld,Cause.NATIVE_MOUSE_EVENT);
        if(movementHeld(modHeld))signal(Kind.POSITION,Cause.MOD_MOVEMENT_INPUT,now,150);
        if(movementHeld(nativeHeld))signal(Kind.POSITION,Cause.NATIVE_KEY_EVENT,now,150);
        if(movementHeld(nativeMouseHeld))signal(Kind.POSITION,Cause.NATIVE_MOUSE_EVENT,now,150);
        if(previous!=null&&Math.abs(p.getX()-previous.position.x())<.15&&Math.abs(p.getZ()-previous.position.z())<.15&&p.getY()<previous.position.y()&&!p.onGround()&&p.getDeltaMovement().y<0&&!p.getAbilities().flying)signal(Kind.POSITION,Cause.GRAVITY,now,150);
        if(previous!=null&&p.getY()<previous.position.y()&&(wasFalling||(!p.onGround()&&p.getDeltaMovement().y<0&&!p.getAbilities().flying)))signal(Kind.POSITION,Cause.FALLING_STATE,now,150);
        if(p.getAbilities().flying)signal(Kind.POSITION,Cause.FLIGHT,now,150);
        if(p.horizontalCollision||p.verticalCollision)signal(Kind.POSITION,Cause.COLLISION,now,150);
        if(p.isInWater()||p.isInLava())signal(Kind.POSITION,Cause.FLUID,now,150);
        if(p.isPassenger())signal(Kind.POSITION,Cause.VEHICLE,now,150);
        var snapshot=snapshot(ctx);
        // The previous snapshot is intentionally absent after disconnect. A genuine joined transition
        // still has its exact old native context, so consume only its private native origin once here.
        if(previous==null&&sampledContext!=null&&!ctx.sameNativeContext(sampledContext)&&issuedFarmOrigin!=null
                &&issuedFarmOrigin.kind()==dev.veltraph.client.rewarp.mc.IssuedFarmExpectedObservation.Kind.CONTEXT)
            observe(Kind.CONTEXT,Value.number(sampledContext.world()),Value.number(ctx.world()),now);
        if(previous!=null) {
            if(!ctx.sameNativeContext(previous.context))observe(Kind.CONTEXT,Value.number(previous.context.world()),Value.number(ctx.world()),now);
            else if(UnexpectedSampleBoundary.comparable(previous.context,ctx)) {
                // Owner changes start a fresh baseline; cleanup from the previous owner is not
                // evidence against a newly admitted module. Repeated unchanged samples emit nothing.
                changed(Kind.POSITION,previous.position,snapshot.position,now,.01);
                if(activationChanged(ctx,now)) { previous=null;return; }
                changed(Kind.ROTATION,previous.rotation,snapshot.rotation,now,.5);
                if(activationChanged(ctx,now)) { previous=null;return; }
                changed(Kind.GAMEMODE,previous.mode,snapshot.mode,now,0);
                if(activationChanged(ctx,now)) { previous=null;return; }
                changed(Kind.SELECTED_SLOT,previous.selected,snapshot.selected,now,0);
                if(activationChanged(ctx,now)) { previous=null;return; }
                changed(Kind.INVENTORY,previous.inventory,snapshot.inventory,now,0);
                // Moving across blocks is not a world edit. Compare this local cell only while stationary.
                if(previous.position.equals(snapshot.position))changed(Kind.BLOCK,previous.block,snapshot.block,now,0);
                changed(Kind.INPUT_STATE,previous.inputs,snapshot.inputs,now,0);
            }
        }
        previous=snapshot;wasFalling=!p.onGround()&&p.getDeltaMovement().y<0&&!p.getAbilities().flying;
    }
    /** Successful installed activation boundary, after setup and before outside input can occur. */
    public void baseline(long now) {
        if(!client.isSameThread())throw new IllegalStateException("Capture baseline on client thread");
        previous=null; // A failed native read must not retain the prior owner/frame sample.
        var ctx=context(now);
        previous=available()?snapshot(ctx):null;
        wasFalling=available()&&!client.player.onGround()&&client.player.getDeltaMovement().y<0&&!client.player.getAbilities().flying;
    }
    private Snapshot snapshot(Context ctx) {
        var p=client.player;
        long inventory=1;
        for(int slot=0;slot<p.getInventory().getContainerSize();slot++) {
            var stack=p.getInventory().getItem(slot);
            // No names, lore, book text, chat, or item component retention.
            inventory=31*inventory+net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem());
            inventory=31*inventory+stack.getCount();
        }
        var floor=p.blockPosition().below();
        long block=net.minecraft.world.level.block.Block.getId(client.level.getBlockState(floor));
        block=31*block+net.minecraft.world.level.block.Block.getId(client.level.getBlockState(p.blockPosition()));
        return new Snapshot(ctx,Value.position(p.getX(),p.getY(),p.getZ()),Value.rotation(p.getYRot(),p.getXRot()),
                Value.number(client.gameMode==null?-1:client.gameMode.getPlayerMode().getId()),Value.number(p.getInventory().getSelectedSlot()),
                Value.number(inventory),Value.number(block),Value.number(inputBits()));
    }
    private boolean activationChanged(Context sampled,long now) {
        if(sampled.activation().equals(activation.get()))return false;
        context(now); // An immediate stop can synchronously restore slots/view while publishing.
        return true;
    }
    private void changed(Kind kind,Value a,Value b,long now,double threshold) {
        boolean differs=UnexpectedSampleBoundary.changed(a,b,kind,threshold);
        if(differs)observe(kind,a,b,now);
    }
    /** Matched only after all context/activation/native sampling callbacks and immediately before classification. */
    private void observe(Kind kind,Value before,Value after,long now) {
        var event=new Observation(current,kind,before,after,now);
        if(kind==Kind.ROTATION){
            var paired=issuedFarmViewOrigin;
            // A real ACKed packet may leave position unchanged. Consume its exact position proof
            // without manufacturing a POSITION finding before considering that same packet's view.
            if(paired==null&&issuedFarmOrigin!=null
                    &&issuedFarmOrigin.kind()==dev.veltraph.client.rewarp.mc.IssuedFarmExpectedObservation.Kind.POSITION
                    &&client.player!=null){
                var position=new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
                var unchanged=issuedFarmOrigin;
                if(unchanged.consumeObserved(client,sampledPlayer,sampledWorld,sampledConnection,
                        client.player,client.level,client.getConnection(),position)
                        &&unchanged.currentScene(client,dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator())){
                    paired=unchanged;issuedFarmOrigin=null;issuedFarmViewOrigin=paired;
                }
            }
            if(paired!=null&&paired.consumeObservedRotation(client,sampledPlayer,sampledWorld,sampledConnection,
                    client.player,client.level,client.getConnection(),(float)after.x(),(float)after.y())
                    &&paired.currentScene(client,dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator())
                    &&client.player!=null&&Math.abs(Math.IEEEremainder(client.player.getYRot()-after.x(),360))<=.01
                    &&Math.abs(client.player.getXRot()-after.y())<=.01){
                issuedFarmViewOrigin=null;monitor.observeIssuedReturn(new IssuedReturnObservation(event,true));return;
            }
        }
        var exact=issuedFarmOrigin;
        boolean expectedKind=exact!=null&&(kind==Kind.POSITION&&exact.kind()==dev.veltraph.client.rewarp.mc.IssuedFarmExpectedObservation.Kind.POSITION
                ||kind==Kind.CONTEXT&&exact.kind()==dev.veltraph.client.rewarp.mc.IssuedFarmExpectedObservation.Kind.CONTEXT);
        var observed=kind==Kind.POSITION?new dev.veltraph.client.world.Vec3(after.x(),after.y(),after.z()):null;
        if(expectedKind&&exact.consumeObserved(client,sampledPlayer,sampledWorld,sampledConnection,
                client.player,client.level,client.getConnection(),observed)
                &&exact.currentScene(client,dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator())
                &&(kind!=Kind.POSITION||client.player!=null&&observed.distanceTo(new dev.veltraph.client.world.Vec3(
                    client.player.getX(),client.player.getY(),client.player.getZ()))<=.01)) {
            issuedFarmOrigin=null;
            if(kind==Kind.POSITION)issuedFarmViewOrigin=exact;
            monitor.observeIssuedReturn(new IssuedReturnObservation(event,false));
        } else monitor.observe(event);
    }
    /** Package-private one-event handout. Only this actual native adapter can mint it. */
    static final class IssuedReturnObservation implements UnexpectedBehaviorMonitor.IssuedReturnObservation {
        private final Observation event;private final boolean pairedView;private boolean consumed;
        private IssuedReturnObservation(Observation event,boolean pairedView){this.event=event;this.pairedView=pairedView;}
        public boolean pairedView(){return pairedView;}
        public Observation consume(){if(consumed)return null;consumed=true;return event;}
    }
    void ownedLandingPacket(long now){if(available())monitor.ownedLandingPacket(context(now),Value.position(client.player.getX(),client.player.getY(),client.player.getZ()),Value.rotation(client.player.getYRot(),client.player.getXRot()),now);}
    Runnable scopedTeleportIntent(Value target,double tolerance,long now,long ttl,java.util.function.BooleanSupplier valid){
        if(!available())return ()->{};long receipt=monitor.ownedLandingIntent(context(now),target,tolerance,now,ttl,valid);
        return ()->{if(client.isSameThread())monitor.revokeIntent(receipt);};
    }
    void intent(Kind kind,Value target,double tolerance,long now,long ttl) {if(available())monitor.intent(context(now),kind,target,tolerance,now,ttl);}
    void held(KeyMapping key,boolean down,long now) {held(key,down,now,()->false);}
    private boolean held(KeyMapping key,boolean down,long now,BooleanSupplier afterNormalCurrent) {
        if(!available()||inputIndex(key)<0)return false;
        var ctx=context(now);
        if(key.isDown()==down)return false;
        int bit=1<<inputIndex(key);long target=down?inputBits()|bit:inputBits()&~bit;
        monitor.intent(ctx,Kind.INPUT_STATE,Value.number(target),0,now,500,afterNormalCurrent);
        // Intent alone cannot prove a successful persistent native held origin.
        modHeld.remove(key);nativeHeld.remove(key);nativeMouseHeld.remove(key);retireMovementOrigins(key);return true;
    }
    private final class HeldPublicationFrame {
        final NativeInputFrame nativeFrame;final KeyMapping key;final boolean down;boolean consumed;dev.veltraph.client.service.mc.McHeldKeys.HeldPublicationReceipt actual;
        HeldPublicationFrame(NativeInputFrame nativeFrame,KeyMapping key,boolean down){this.nativeFrame=nativeFrame;this.key=key;this.down=down;}
        boolean current(){return actual!=null&&actual.historyCurrent()&&nativeFrame.nativeCurrent()&&Objects.equals(McUnexpectedBehaviorMonitor.this.current,nativeFrame.context);}
    }
    Object beginHeldPublication(KeyMapping key,boolean down,long now){
        if(!available()||inputIndex(key)<0){held(key,down,now);return null;}
        NativeInputFrame nativeFrame;try{nativeFrame=new NativeInputFrame();}catch(RuntimeException unavailable){held(key,down,now);return null;}
        var frame=new HeldPublicationFrame(nativeFrame,key,down);boolean changed=held(key,down,now,frame::current);
        try{if(!changed||!nativeFrame.nativeCurrent())return null;nativeFrame.context=current;nativeFrame.created=now;return frame;}catch(RuntimeException unavailable){return null;}
    }
    void completeHeldPublication(Object opaque,dev.veltraph.client.service.mc.McHeldKeys.HeldPublicationReceipt actual,long now){
        if(!(opaque instanceof HeldPublicationFrame frame)||frame.consumed)return;frame.consumed=true;
        if(actual==null||!available()||actual.key()!=frame.key||actual.held()!=frame.down)return;
        var nativeFrame=frame.nativeFrame;if(now<nativeFrame.created||now-nativeFrame.created<0||now-nativeFrame.created>500||!nativeFrame.nativeCurrent()||!actual.historyCurrent())return;
        var ctx=context(now);if(!ctx.equals(nativeFrame.context)||!nativeFrame.nativeCurrent()||!actual.historyCurrent()||frame.key.isDown()!=frame.down)return;
        frame.actual=actual;modHeld.put(frame.key,new HeldOrigin(frame.down,frame.key.saveString(),frame::current));nativeHeld.remove(frame.key);nativeMouseHeld.remove(frame.key);retireMovementOrigins(frame.key);
    }
    void keyboard(long window,int action,KeyEvent event,long now) {
        pendingKeyboard.clear();keyboardFrame=null;
        if(!available()||window!=client.getWindow().handle()||client.gui.screen()!=null||ScriptingInputLock.shouldSuppressKeyboard(false,true))return;
        var frame=new NativeInputFrame();var ctx=context(now);if(!frame.nativeCurrent())return;frame.context=ctx;frame.created=now;
        for(int i=0;i<frame.keys.length;i++)if(frame.keys[i].matches(event))frame.matched|=1L<<i;
        if(frame.matched!=0){pendingKeyboard.record(ctx,inputBits(),frame.matched,action!=com.mojang.blaze3d.platform.InputConstants.RELEASE,now);keyboardFrame=frame;keyboardWindow=window;keyboardAction=action;keyboardEvent=event;}
        // Hotbar click dispatch is asynchronous in vanilla; preserve its exact existing intended slot receipt.
        for(int slot=0;slot<client.options.keyHotbarSlots.length;slot++)if(client.options.keyHotbarSlots[slot].matches(event)&&action!=com.mojang.blaze3d.platform.InputConstants.RELEASE)monitor.nativeReceipt(ctx,Kind.SELECTED_SLOT,Cause.NATIVE_KEY_EVENT,Value.number(slot),0,now,200);
    }
    void nativeNavigationCommand(String submitted,long now) {
        if(!available())return;
        var correlation=NativeNavigationCommand.parse(submitted);if(correlation.isEmpty())return;
        var ctx=context(now);var target=correlation.get().exactTarget();
        if(target.isPresent())monitor.nativeReceipt(ctx,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,target.get(),.5,now,5000);
        else monitor.signal(ctx,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,now,5000);
    }
    void keyboardApplied(long window,int action,KeyEvent event,long now) {
        var frame=keyboardFrame;keyboardFrame=null;
        if(frame==null||window!=keyboardWindow||action!=keyboardAction||event!=keyboardEvent||!available()) {pendingKeyboard.clear();return;}
        var ctx=context(now);long bits=inputBits();
        if(!frame.nativeCurrent()||!ctx.equals(frame.context)||client.gui.screen()!=null||ScriptingInputLock.shouldSuppressKeyboard(false,true)||!pendingKeyboard.consume(ctx,bits,now)){pendingKeyboard.clear();return;}
        recordNativeHeld(frame,bits,nativeHeld,nativeMouseHeld);
        monitor.nativeReceipt(ctx,Kind.INPUT_STATE,Cause.NATIVE_KEY_EVENT,Value.number(bits),0,now,200);
    }
    private void recordNativeHeld(NativeInputFrame frame,long bits,IdentityHashMap<KeyMapping,HeldOrigin> chosen,IdentityHashMap<KeyMapping,HeldOrigin> other){
        for(int i=0;i<frame.keys.length;i++)if((frame.matched&(1L<<i))!=0){var key=frame.keys[i];var origin=nativeHeldOrigin(frame,key,(bits&(1L<<i))!=0);if(origin==null)chosen.remove(key);else chosen.put(key,origin);other.remove(key);modHeld.remove(key);retireMovementOrigins(key);}
    }
    void mouse(long window,double deltaX,double deltaY,long now) {
        if(!available()||window!=client.getWindow().handle()||!Double.isFinite(deltaX)||!Double.isFinite(deltaY)||(deltaX==0&&deltaY==0))return;
        context(now);
        if(client.gui.screen()==null&&client.isWindowActive()&&client.mouseHandler.isMouseGrabbed()&&!ScriptingInputLock.shouldSuppressLook(false,true))nativeMouseOrigin.record(current,now);
    }
    void mouseApplied(long now) {
        if(!available()){nativeMouseOrigin.clear();return;}
        var appliedContext=context(now);
        // Resolving context can retire the entry. Never rebind old native input to a new owner.
        if(!nativeMouseOrigin.consume(appliedContext,now))return;
        if(client.gui.screen()==null&&client.isWindowActive()&&client.mouseHandler.isMouseGrabbed()
                &&!ScriptingInputLock.shouldSuppressLook(false,true))monitor.nativeReceipt(appliedContext,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(client.player.getYRot(),client.player.getXRot()),.01,now,150);
    }
    void scroll(long window,long now) {
        if(available()&&window==client.getWindow().handle()&&client.gui.screen()==null)monitor.nativeReceipt(context(now),Kind.SELECTED_SLOT,Cause.NATIVE_MOUSE_EVENT,Value.number(client.player.getInventory().getSelectedSlot()),0,now,150);
    }
    void mouseButton(long window,int action,long now){
        if(available()&&window==client.getWindow().handle()&&action!=com.mojang.blaze3d.platform.InputConstants.RELEASE&&client.gui.screen()!=null)signal(Kind.INVENTORY,Cause.NATIVE_CONTAINER_INPUT,now,250);
    }
    void mouseButton(long window,int action,net.minecraft.client.input.MouseButtonInfo button,long now){
        pendingButton.clear();buttonFrame=null;mouseButton(window,action,now);
        if(!available()||window!=client.getWindow().handle()||client.gui.screen()!=null||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed())return;
        var frame=new NativeInputFrame();var ctx=context(now);if(!frame.nativeCurrent())return;frame.context=ctx;frame.created=now;
        var event=new net.minecraft.client.input.MouseButtonEvent(client.mouseHandler.xpos(),client.mouseHandler.ypos(),button);
        for(int i=0;i<frame.keys.length;i++)if(frame.keys[i].matchesMouse(event))frame.matched|=1L<<i;
        if(frame.matched!=0){pendingButton.record(ctx,inputBits(),frame.matched,action!=com.mojang.blaze3d.platform.InputConstants.RELEASE,now);buttonFrame=frame;buttonWindow=window;buttonAction=action;buttonEvent=button;}
    }
    void mouseButtonApplied(long window,int action,net.minecraft.client.input.MouseButtonInfo button,long now){
        var frame=buttonFrame;buttonFrame=null;
        if(frame==null||window!=buttonWindow||action!=buttonAction||button!=buttonEvent||!available()){pendingButton.clear();return;}
        var ctx=context(now);long bits=inputBits();
        if(!frame.nativeCurrent()||!ctx.equals(frame.context)||client.gui.screen()!=null||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed()||!pendingButton.consume(ctx,bits,now)){pendingButton.clear();return;}
        recordNativeHeld(frame,bits,nativeMouseHeld,nativeHeld);monitor.nativeReceipt(ctx,Kind.INPUT_STATE,Cause.NATIVE_MOUSE_EVENT,Value.number(bits),0,now,200);
    }
    void signal(Kind kind,Cause cause,long now,long ttl) {if(available())monitor.signal(context(now),kind,cause,now,ttl);}
    private KeyMapping[] movementKeys() {var o=client.options;return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint};}
    private KeyMapping[] inputKeys(){var o=client.options;return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};}
    private boolean movementHeld(IdentityHashMap<KeyMapping,HeldOrigin> values){for(var key:movementKeys()){var origin=values.get(key);if(origin!=null&&origin.down()&&origin.current(key))return true;}return false;}
    private void retireMovementOrigins(KeyMapping key){
        int index=inputIndex(key);if(index<0||index>=7)return;
        monitor.retireHeldMovementSignals(Cause.MOD_MOVEMENT_INPUT);monitor.retireHeldMovementSignals(Cause.NATIVE_KEY_EVENT);monitor.retireHeldMovementSignals(Cause.NATIVE_MOUSE_EVENT);
    }
    private void pruneHeld(IdentityHashMap<KeyMapping,HeldOrigin> values,Cause cause){
        if(values.entrySet().removeIf(e->inputIndex(e.getKey())<0||!e.getValue().current(e.getKey())))monitor.retireHeldMovementSignals(cause);
    }
    private int inputIndex(KeyMapping key) {var keys=inputKeys();for(int i=0;i<keys.length;i++)if(keys[i]==key)return i;return -1;}
    private long inputBits() {long bits=0;var keys=inputKeys();for(int i=0;i<keys.length;i++)if(keys[i].isDown())bits|=1L<<i;return bits;}
    public UnexpectedBehaviorMonitor monitor() {return monitor;}
    public List<Finding> history() {return monitor.history();}
    /** Up to 20 safe local lines for an on-demand diagnostic command or screen. */
    public List<String> diagnosticLines(int limit) {
        int bounded=Math.max(0,Math.min(20,limit));var entries=history();
        return entries.stream().skip(Math.max(0,entries.size()-bounded)).map(f->"#"+f.serial()+" "+f.classification()+" "+f.kind()+" "+f.evidence()+" — "+f.detail()).toList();
    }
    @Override public void close() {UnexpectedActions.detach(this,priorObserver);monitor.clear();previous=null;modHeld.clear();nativeHeld.clear();nativeMouseHeld.clear();pendingKeyboard.clear();pendingButton.clear();keyboardFrame=null;buttonFrame=null;keyboardEvent=null;buttonEvent=null;nativeMouseOrigin.clear();}
}
