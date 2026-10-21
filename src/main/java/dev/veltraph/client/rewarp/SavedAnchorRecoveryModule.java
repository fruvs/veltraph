package dev.veltraph.client.rewarp;

import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import java.util.Optional;
import java.util.function.Supplier;

/** Internal input owner: existing stop-all, screen/world guards and activation arbitration apply. */
public final class SavedAnchorRecoveryModule extends AbstractModule {
    public interface Driver {
        boolean start(RewarpPoint point,GameArea area);
        void tick(GameArea area);
        boolean active();
        SavedAnchorRecovery.State state();
        String status();
        void cancel();
        default void onServerMessage(String line) {}
        default boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return false;}
    }
    private final ModuleManager manager;
    private final Supplier<GameArea> area;
    private final Driver driver;
    private final Runnable fallback;
    private RewarpPoint target;
    private boolean started;
    private String status="Idle";
    public SavedAnchorRecoveryModule(ModuleManager manager,Supplier<GameArea> area,Driver driver,Runnable fallback) {
        super("saved_anchor_recovery","Return to saved position",ModuleCategory.INTERNAL);
        this.manager=manager;this.area=area;this.driver=driver;this.fallback=fallback;
    }
    public boolean begin(RewarpPoint point) {
        manager.stopAll();target=point;started=false;setEnabled(true);return started;
    }
    @Override public boolean requiresSkyBlock() {return true;}
    @Override public Optional<GameArea> requiredArea() {return target==null?Optional.empty():Optional.of(target.area());}
    @Override public Optional<String> preStartCheck() {return target==null?Optional.of("No saved return point selected."):Optional.empty();}
    @Override public void onEnable() {
        try {started=driver.start(target,area.get());status=driver.status();}
        catch(RuntimeException failure) {started=false;status="Could not start saved return: "+failure.getMessage();}
        if(!started)setEnabled(false);
    }
    @Override public void onTick() {
        driver.tick(area.get());status=driver.status();
        if(!driver.active()) {
            boolean failed=driver.state()==SavedAnchorRecovery.State.FAILED;
            setEnabled(false);
            if(failed)fallback.run();
        }
    }
    @Override public void onDisable() {driver.cancel();}
    @Override public void onServerMessage(String line){if(isEnabled())driver.onServerMessage(line);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&driver.consumeExpectedTeleport(position,now);}
    @Override public String diagnostics() {return status;}
}
