package dev.veltraph.client.service.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.input.ScriptingInputLock;
import net.minecraft.client.Minecraft;

/** Cleanup routing only. Neither a public stop call nor this scope grants native publication. */
public final class McFarmOwnedCleanup {
    private McFarmOwnedCleanup() {}
    private static final ThreadLocal<Stop> STOP = new ThreadLocal<>();
    private static final ThreadLocal<FarmModule> TOOL_REFUSAL = new ThreadLocal<>();
    private static final ThreadLocal<FarmModule> FARM_TICK = new ThreadLocal<>();
    /** Negative-only invocation routing; an old farm callback never falls through a raw non-farm adapter. */
    public static void runFarmingTick(FarmModule farm,Runnable tick) {
        var previous=FARM_TICK.get();FARM_TICK.set(farm);
        try {
            // A callback between base admission and the genuine family body can invalidate the tool/control.
            // Pure planner adapters cannot use this branch to mint a native publication scope.
            if(farm.usesNativeFarmingInventory()&&NativeScope.capture(Minecraft.getInstance(),farm)==null) {
                runToolRefusal(farm,farm::releaseRefusedFarmingInputs);return;
            }
            tick.run();
        } finally{if(previous==null)FARM_TICK.remove();else FARM_TICK.set(previous);}
    }
    static FarmModule farmingTickFarm(){return FARM_TICK.get();}
    /** Negative-only routing: no held input or foreign/raw release may be published from a failed tool tick. */
    public static void runToolRefusal(FarmModule farm,Runnable release) {
        var previous=TOOL_REFUSAL.get();TOOL_REFUSAL.set(farm);
        try{release.run();}finally{if(previous==null)TOOL_REFUSAL.remove();else TOOL_REFUSAL.set(previous);}
    }
    static FarmModule toolRefusalFarm(){return TOOL_REFUSAL.get();}
    private static final class Stop {
        final FarmModule farm;
        boolean revoked;
        Stop(FarmModule farm) { this.farm = farm; }
    }
    /** Covers concrete planner stops before the shared farm tail, including a raw public stop. */
    public static void run(FarmModule farm, Runnable logicalStop) {
        var previous = STOP.get();
        var stop = new Stop(farm);
        if (previous != null) { previous.revoked = true; stop.revoked = true; }
        STOP.set(stop);
        try { logicalStop.run(); }
        finally { if (previous == null) STOP.remove(); else STOP.set(previous); }
    }
    static boolean stopping() { return STOP.get() != null; }
    static FarmModule stoppingFarm() { var stop = STOP.get(); return stop == null ? null : stop.farm; }
    static boolean stopRouteCurrent(FarmModule farm) {
        var stop = STOP.get(); return stop != null && !stop.revoked && stop.farm == farm;
    }

    /** The native producer retains this exact original scope before its actual publication. */
    static final class NativeScope {
        final Minecraft client;
        final FarmModule farm;
        final ModuleManager root;
        final ModuleManager.LifecycleSnapshot lifecycle;
        final ScriptingInputLock.ControlSnapshot control;
        final McOwnedRotation.InstallationSnapshot installation;
        final Object player, world, connection, options, gameMode, config;
        private NativeScope(Minecraft client, FarmModule farm, ModuleManager root,
                ModuleManager.LifecycleSnapshot lifecycle, ScriptingInputLock.ControlSnapshot control,
                McOwnedRotation.InstallationSnapshot installation) {
            this.client = client; this.farm = farm; this.root = root; this.lifecycle = lifecycle;
            this.control = control; this.installation = installation;
            player = client.player; world = client.level; connection = client.getConnection();
            options = client.options; gameMode = client.gameMode; config = VeltraphBootstrap.config();
        }
        /** Copy only the lifecycle proved by the same actual passive operation; retain every native/control field. */
        private NativeScope(NativeScope original, ModuleManager.LifecycleSnapshot lifecycle) {
            client=original.client;farm=original.farm;root=original.root;this.lifecycle=lifecycle;
            control=original.control;installation=original.installation;
            player=original.player;world=original.world;connection=original.connection;
            options=original.options;gameMode=original.gameMode;config=original.config;
        }
        NativeScope continueAfterPassive(VeltraphBootstrap.RootPassiveTransition actual) {
            if(actual==null||actual.registryIdentity()!=root||!actual.normalReturnedCurrent(client)
                    ||!root.lifecycleSameHistory(lifecycle,actual.beforeLifecycle())||!nativeCurrent())return null;
            var after=actual.normalReturnedLifecycle();
            if(after==null||!after.known()||!root.lifecycleCurrent(after))return null;
            var next=new NativeScope(this,after);
            return next.activeReleaseCurrent()&&actual.normalReturnedCurrent(client)?next:null;
        }
        static NativeScope capture(Minecraft client, FarmModule farm) {
            if (stopping() || client == null || !client.isSameThread() || farm == null
                    || !McInputService.canonicalFarmRootCurrent(client)) return null;
            var root = VeltraphBootstrap.moduleManager();
            if (root == null || root.get(farm.id()).orElse(null) != farm || !farm.isEnabled()
                    || farm.isSuspended() || root.inputOwner().orElse(null) != farm
                    || McOwnedRotation.authority(client) != farm) return null;
            var lifecycle = root.lifecycleSnapshot();
            var control = ScriptingInputLock.controlSnapshot();
            var installation = McOwnedRotation.installationSnapshot(client);
            if (!lifecycle.known() || control == null || !control.active()
                    || !farm.id().equals(control.ownerModuleId()) || installation == null) return null;
            var scope = new NativeScope(client, farm, root, lifecycle, control, installation);
            return scope.activeCurrent() ? scope : null;
        }
        private boolean nativeCurrent() {
            return client == Minecraft.getInstance() && client.isSameThread()
                    && McInputService.canonicalFarmRootCurrent(client)
                    && VeltraphBootstrap.moduleManager() == root && root.get(farm.id()).orElse(null) == farm
                    && control.current() && installation.current() && VeltraphBootstrap.config() == config
                    && player != null && world != null && connection != null && options != null
                    && client.player == player && client.level == world && client.getConnection() == connection
                    && client.options == options && client.gameMode == gameMode;
        }
        boolean activeCurrent() {
            return nativeCurrent() && !stopping() && root.lifecycleCurrent(lifecycle)
                    && farm.isEnabled() && !farm.isSuspended() && farm.nativeSelectedFarmingToolCurrent(client);
        }
        /** Same genuine producer may retain its own unchanged key history after a normal no-op publication. */
        boolean sameNativeProducer(NativeScope next) {
            return next!=null&&farm==next.farm&&root==next.root&&client==next.client
                    &&nativeCurrent()&&next.activeCurrent();
        }
        /** An enabled suspension may release its old keys, but can never publish a new held input. */
        boolean activeReleaseCurrent() {
            return nativeCurrent() && !stopping() && root.lifecycleCurrent(lifecycle) && farm.isEnabled();
        }
        boolean activeReleaseProvidersCurrent() {
            if(!activeReleaseCurrent())return false;
            var input=root.inputOwner().orElse(null);var camera=McOwnedRotation.authority(client);
            return (input==null||input==farm)&&(camera==null||camera==farm)&&activeReleaseCurrent();
        }
        /** Callback-free final leaf. Only the original actual Core disable can cross lifecycle. */
        boolean stopCurrent() {
            return stopRouteCurrent(farm) && !farm.isEnabled()
                    && nativeCurrent() && root.pendingOwnDisableFromMatches(lifecycle, farm);
        }
        /** Providers can only refuse; original native identity is checked again after both. */
        boolean cleanupProvidersCurrent() {
            if (!stopCurrent()) return false;
            var input = root.inputOwner().orElse(null);
            var camera = McOwnedRotation.authority(client);
            return (input == null || input == farm) && (camera == null || camera == farm) && stopCurrent();
        }
    }
}
