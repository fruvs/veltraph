package dev.veltraph.client.failsafe.response.mc;

import dev.veltraph.client.failsafe.response.FailsafeActionSink;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.ServerCommandService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Minecraft execution seam for the local failsafe response routine. Applies the runner's decided local
 * effects: look via {@link RotationService}, movement/jump/sneak via {@link InputService}, attack/use clicks
 * via the vanilla attack/use key bindings, and — only for a {@code COMMAND} action — a vanilla in-game
 * command via {@link ServerCommandService}. Action failures release owned inputs and propagate to the
 * runner so it aborts; release itself remains best-effort across independent key bindings. <b>No network</b> beyond the one named command method (same vanilla path the
 * rewarp command uses).
 */
public final class McFailsafeActionSink implements FailsafeActionSink {

    private final dev.veltraph.client.navigation.AngularRateLimiter yawRate = new dev.veltraph.client.navigation.AngularRateLimiter();
    private final dev.veltraph.client.navigation.AngularRateLimiter pitchRate = new dev.veltraph.client.navigation.AngularRateLimiter();
    private final Minecraft client;
    private final RotationService rotation;
    private final InputService input;
    private final ServerCommandService serverCommand;
    private long lookGeneration,actionGeneration;
    private final class Action {
        final long generation=++actionGeneration;
        final Cleanup before=(Cleanup)captureOwnedControls();
        final java.util.ArrayList<OwnedKey> issued=new java.util.ArrayList<>();
        boolean current(){return generation==actionGeneration;}
        Cleanup cleanup(){var keys=new java.util.ArrayList<>(before.keys());keys.addAll(issued);return new Cleanup(McFailsafeActionSink.this,java.util.List.copyOf(keys),before.lookGeneration());}
    }
    private final java.util.IdentityHashMap<KeyMapping,OwnedKey> ownedKeys=new java.util.IdentityHashMap<>();
    private final java.util.Set<KeyMapping> releasing=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final class Scene {
        final Minecraft client;final dev.veltraph.client.core.ModuleManager root;
        final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        final Object player,world,connection,options,config,authority;
        Scene(Minecraft client,dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle){
            this.client=client;this.root=root;this.lifecycle=lifecycle;player=client.player;world=client.level;connection=client.getConnection();options=client.options;
            config=dev.veltraph.client.VeltraphBootstrap.config();authority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
        }
        static Scene capture(Minecraft client){
            if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||client.options==null)return null;
            var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
            if(life==null||!life.known())return null;var scene=new Scene(client,root,life);return scene.current()?scene:null;
        }
        boolean storedCurrent(){return client.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.lifecycleCurrent(lifecycle)
                &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.options==options&&dev.veltraph.client.VeltraphBootstrap.config()==config;}
        boolean current(){if(!storedCurrent())return false;var live=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);return live==authority&&storedCurrent();}
        boolean responseControl(){return authority==null||authority==dev.veltraph.client.VeltraphBootstrap.hydra();}
    }
    private record OwnedKey(KeyMapping key,Scene scene,dev.veltraph.client.service.mc.McHeldKeys.ScopedHeldReceipt receipt){
        boolean current(){return scene.current()&&receipt.confirmed()&&receipt.held()&&receipt.key()==key&&receipt.normalIdleHistoryCurrent();}
    }
    private record Cleanup(McFailsafeActionSink owner,java.util.List<OwnedKey> keys,long lookGeneration){}
    @Override public boolean hasOwnedControls(){return !ownedKeys.isEmpty();}
    @Override public Object captureOwnedControls(){return new Cleanup(this,java.util.List.copyOf(ownedKeys.values()),lookGeneration);}
    @Override public void releaseOwnedControls(Object exact){if(exact instanceof Cleanup c&&c.owner()==this){for(var key:c.keys())releaseOwned(key);if(lookGeneration==c.lookGeneration()){yawRate.reset();pitchRate.reset();}}}

    public McFailsafeActionSink(Minecraft client, RotationService rotation, InputService input,
                                ServerCommandService serverCommand) {
        this.client = client;
        this.rotation = rotation;
        this.input = input;
        this.serverCommand = serverCommand;
    }

    @Override
    public float currentYaw() {
        try {
            return rotation == null ? Float.NaN : rotation.yaw();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    @Override
    public float currentPitch() {
        try {
            return rotation == null ? Float.NaN : rotation.pitch();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    @Override
    public void applyLook(float yaw, float pitch) {
        var action=new Action();long applying=++lookGeneration;
        try {
            if (rotation != null) {
                float actualYaw=rotation.yaw();if(lookGeneration!=applying||!action.current())return;
                float actualPitch=rotation.pitch();if(lookGeneration!=applying||!action.current())return;
                rotation.aim(yawRate.apply(actualYaw,yaw,1),Math.max(-90,Math.min(90,pitchRate.apply(actualPitch,pitch,1))));
            }
        } catch (Throwable t) {
            throw failedAction("applyLook",t,action);
        }
    }

    @Override
    public void setMovement(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {
        var action=new Action();var scene=Scene.capture(client);
        try {
            if (input == null) {
                return;
            }
            if (forward || back || left || right || jump) {
                var player = client == null ? null : client.player;
                boolean ready = player != null && client.level != null && client.gui.screen() == null
                        && player.isAlive() && player.onGround();
                if (ready) {
                    var terrain = new dev.veltraph.client.navigation.mc.McNavigator(client, input);
                    var position = new dev.veltraph.client.world.Vec3(player.getX(), player.getY(), player.getZ());
                    var v = player.getDeltaMovement();
                    ready = dev.veltraph.client.failsafe.response.FailsafeMovementGuard.allows(terrain, position,
                            new dev.veltraph.client.world.Vec3(v.x, v.y, v.z), player.getYRot(), forward, back, left, right);
                    if (jump && !dev.veltraph.client.failsafe.response.FailsafeMovementGuard.allowsJump(terrain, position)) jump = false;
                }
                if(!ready||scene==null||!scene.current())forward=back=left=right=jump=false;
            }
            if(!(input instanceof dev.veltraph.client.service.mc.McInputService))throw new IllegalStateException("Unknown input service cannot certify native response ownership");
            if(client==null||client.options==null)return;
            pressOwned(client.options.keyUp,forward,scene,action);if(!action.current())return;pressOwned(client.options.keyDown,back,scene,action);if(!action.current())return;
            pressOwned(client.options.keyLeft,left,scene,action);if(!action.current())return;pressOwned(client.options.keyRight,right,scene,action);if(!action.current())return;
            pressOwned(client.options.keyJump,jump,scene,action);if(!action.current())return;pressOwned(client.options.keyShift,sneak,scene,action);
        } catch (Throwable t) {
            throw failedAction("setMovement",t,action);
        }
    }

    @Override
    public void setAttack(boolean held) {
        var action=new Action();try{pressOwned(attackKey(),held,Scene.capture(client),action);}catch(RuntimeException failure){throw failedAction("setAttack",failure,action);}
    }

    @Override
    public void setUse(boolean held) {
        var action=new Action();try{pressOwned(useKey(),held,Scene.capture(client),action);}catch(RuntimeException failure){throw failedAction("setUse",failure,action);}
    }

    @Override
    public void sendCommand(String command) {
        try {
            if (serverCommand != null && command != null && !command.isBlank()) {
                serverCommand.sendCommand(command);
            }
        } catch (Throwable t) {
            VeltraphLog.details("McFailsafeActionSink.sendCommand", "failed: " + t.getClass().getSimpleName());
        }
    }

    @Override public void releaseAll(){
        // Snapshot exact issued publications. Nested/newer writes cannot be cleared by this old reset.
        releaseOwnedControls(captureOwnedControls());
    }
    private void pressOwned(KeyMapping key,boolean held,Scene scene,Action action){
        if(key==null||!action.current())return;
        if(!held){var own=ownedKeys.get(key);if(own!=null)releaseOwned(own);return;}
        var prior=ownedKeys.get(key);
        // A real prologue may have released last frame's key. Retire that stale logical claim;
        // the next true must be a fresh issued publication, never a history exemption/adoption.
        if(prior!=null&&!prior.current()){
            if(key.isDown())throw new IllegalStateException("Response refused foreign replacement of owned key");
            ownedKeys.remove(key,prior);prior=null;
        }
        final var original=prior;
        if(scene==null||!scene.responseControl()||!scene.current()||client.player==null||!client.player.isAlive()||client.gui.screen()!=null)throw new IllegalStateException("Native response scene unavailable or foreign control active");
        // A matching held value alone cannot adopt another source's publication.
        if(key.isDown()&&(prior==null||!prior.current()))throw new IllegalStateException("Response refused already-held foreign key");
        var receipt=dev.veltraph.client.service.mc.McHeldKeys.setHeldScoped(key,true,
                ()->action.current()&&scene.responseControl()&&scene.current()&&client.player!=null&&client.player.isAlive()&&client.gui.screen()==null&&ownedKeys.get(key)==original&&(original==null||original.current()));
        if(!receipt.confirmed()||!receipt.normalIdleHistoryCurrent()||!scene.current()||original==null&&receipt.nativeSetAttempts()==0)throw new IllegalStateException("Native response publication refused");
        if(action.current()&&ownedKeys.get(key)==original){var publication=new OwnedKey(key,scene,receipt);ownedKeys.put(key,publication);action.issued.add(publication);}
    }
    private void releaseOwned(OwnedKey exact){
        var key=exact.key();
        if(ownedKeys.get(key)!=exact||!releasing.add(key))return;
        try{
            if(!exact.current()){ownedKeys.remove(key,exact);return;}
            var receipt=dev.veltraph.client.service.mc.McHeldKeys.releaseOwnedScoped(exact.receipt(),
                    ()->ownedKeys.get(key)==exact&&exact.scene().current());
            if(receipt.confirmed()||!exact.current())ownedKeys.remove(key,exact);
        }catch(RuntimeException unavailable){VeltraphLog.details("McFailsafeActionSink.releaseOwned","refused: "+unavailable.getClass().getSimpleName());}
        finally{releasing.remove(key);}
    }

    private RuntimeException failedAction(String name,Throwable failure,Action action){
        releaseOwnedControls(action.cleanup());
        VeltraphLog.details("McFailsafeActionSink."+name,"aborted: "+failure.getClass().getSimpleName());
        return new IllegalStateException("Failsafe action failed: "+name,failure);
    }

    private KeyMapping attackKey() {
        return client != null && client.options != null ? client.options.keyAttack : null;
    }

    private KeyMapping useKey() {
        return client != null && client.options != null ? client.options.keyUse : null;
    }

 }
