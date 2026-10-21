package dev.veltraph.client.foraging.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.foraging.ForagingServices;
import dev.veltraph.client.foraging.TreeBreakAction;
import dev.veltraph.client.foraging.TreeBreakInputs;
import dev.veltraph.client.foraging.TreeBreakTuning;
import dev.veltraph.client.foraging.TreeBreaker;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.log.VeltraphLog;

import java.util.Optional;

/**
 * Tree-chop foraging macro (Batch 7): hold-to-break the log under the crosshair, with the recovered
 * {@code TreeBreaker} failsafes (reach gate, break timeout, blacklist). Driven by the pure
 * {@link TreeBreaker}. Source: {@code reports/_spec_parts/TreeBreaker.md}.
 *
 * <p><b>Scope:</b> this breaks the tree you are aimed at (you position/aim). Park/Hub finding, travel and collection are provided by
 * {@link AutoForagingModule}. This module keeps manual positioning/aiming. Runs in any SkyBlock area (foraging happens in Park/Galatea/Hub); the only
 * effect is the attack-key hold. Local — no network.
 */
public final class TreeChopModule extends AbstractModule implements dev.veltraph.client.foraging.scheduling.ForagingRestOwner {

    public static final String ID = "tree_chop";

    private final ForagingServices services;
    private final ConfigService config;

    private TreeBreaker breaker;
    private long lastBreakAt;
    private final dev.veltraph.client.foraging.scheduling.ForagingPauseGate rest=new dev.veltraph.client.foraging.scheduling.ForagingPauseGate();

    public TreeChopModule(ForagingServices services, ConfigService config) {
        super(ID, "Tree Chopping", ModuleCategory.FORAGING);
        this.services = services;
        this.config = config;
    }

    @Override
    public Optional<GameArea> requiredArea() {
        return Optional.empty(); // foraging spans Park / Galatea / Hub
    }

    @Override
    public boolean requiresSkyBlock() {
        return true;
    }

    @Override public Optional<String> preStartCheck() {
        return services.hasAxe()?Optional.empty():Optional.of("Hold an axe or Treecapitator.");
    }

    @Override
    public void onEnable() {
        rest.clear();
        lastBreakAt=0;
        breaker = new TreeBreaker();
        VeltraphLog.basic("Foraging", "tree-chop started");
    }

    @Override
    public void onDisable() {
        rest.clear();
        lastBreakAt=0;
        services.setBreaking(false);
        if (breaker != null) {
            breaker.reset();
        }
        VeltraphLog.basic("Foraging", "tree-chop stopped");
    }

    @Override
    public void onTick() {
        if(rest.paused())return;
        if (breaker == null) {
            return;
        }
        if(!services.hasAxe()) { setEnabled(false);return; }
        TreeBreakInputs in = new TreeBreakInputs(
                isEnabled(), services.available(), services.lookingAtLog(), services.targetLog(),
                services.eyeDistance(), services.moving(), System.currentTimeMillis());
        TreeBreakAction action = breaker.tick(in, TreeBreakTuning.fromConfig(config));
        if(action==TreeBreakAction.BREAK)lastBreakAt=System.currentTimeMillis();
        services.setBreaking(action == TreeBreakAction.BREAK);
        if (action == TreeBreakAction.RELEASE && breaker.lastReason() != null) {
            VeltraphLog.details("Foraging", "abandoned tree: " + breaker.lastReason());
        }
    }

    public void onFeedback(String message) {
        if(isEnabled() && breaker!=null&&!rest.paused()) {
            breaker.onFeedback(message,System.currentTimeMillis());
            if(breaker.currentTarget()==null) services.setBreaking(false);
        }
    }

    @Override public boolean ownsGameplayInput(){return !rest.paused();}
    @Override public Optional<String> scheduledRestReadiness(){
        if(!isEnabled()||breaker==null)return Optional.of("Tree chopping is not enabled");
        if(breaker.currentTarget()!=null)return Optional.of("Tree chopping has an active break target");
        if(lastBreakAt>0&&System.currentTimeMillis()-lastBreakAt<Math.max(250,config.get(dev.veltraph.client.config.ForagingConfig.FORAGING_ABILITY_RESULT_WINDOW_MS)))return Optional.of("Tree chopping is waiting for its break/ability outcome quiet window");
        if(!services.scheduledAbilityIdle())return Optional.of("Tree chopping native ability cooldown support is unavailable or busy");
        return services.available()&&services.hasAxe()?Optional.empty():Optional.of("Healthy native context and axe required");
    }
    @Override public boolean pauseForScheduledRest(Object ticket){return rest.pause(ticket,scheduledRestReadiness().isEmpty(),System.currentTimeMillis(),()->services.setBreaking(false));}
    @Override public boolean scheduledRestOwnedBy(Object ticket){return isEnabled()&&rest.owns(ticket);}
    @Override public boolean rebindForScheduledRest(Object ticket){return scheduledRestOwnedBy(ticket)&&services.rebindForScheduledRest()&&rest.rebound(ticket);}
    @Override public boolean resumeFromScheduledRest(Object ticket){return scheduledRestOwnedBy(ticket)&&rest.resume(ticket,System.currentTimeMillis())>=0;}
    public Object scheduledEngineIdentity(){return breaker;}

    @Override
    public String diagnostics() {
        if (breaker == null) {
            return ID + " off";
        }
        return ID + " " + (isEnabled() ? "ON" : "off")
                + " | target=" + (breaker.currentTarget() == null ? "none" : breaker.currentTarget())
                + " | onLog=" + services.lookingAtLog()
                + " | dist=" + fmt(services.eyeDistance())
                + " | blacklist=" + breaker.blacklist().size()
                + " | aborted=" + breaker.abortedCount();
    }

    private static String fmt(double d) {
        return Double.isNaN(d) ? "n/a" : String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
