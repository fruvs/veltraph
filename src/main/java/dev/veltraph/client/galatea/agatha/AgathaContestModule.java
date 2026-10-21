package dev.veltraph.client.galatea.agatha;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.service.FarmServices;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Concrete current-source chat invitation/claim GUI intermediary, with real NPC reward receipts. */
public final class AgathaContestModule extends AbstractModule {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private McAgathaContest runtime;private AgathaContestCycle cycle;private int collected;
    public AgathaContestModule(String id,String name,Minecraft client,ConfigService config,FarmServices services){super(id,name,ModuleCategory.FORAGING);this.client=client;this.config=config;this.services=services;}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GALATEA);}
    @Override public Optional<String> preStartCheck(){if(hasPendingCleanup())return Optional.of("Finish owned Agatha menu/cursor cleanup first.");runtime=new McAgathaContest(client,config,services);return runtime.preStart();}
    /** Read-only autostart admission probe; does not replace the active runtime or mutate input. */
    public Optional<String> activationReadiness(){if(isEnabled()||hasPendingCleanup())return Optional.of("Agatha collector already owns work or cleanup");return new McAgathaContest(client,config,services).preStart();}
    @Override public void onEnable(){var invalid=runtime==null?preStartCheck():runtime.preStart();if(invalid.isPresent()){setEnabled(false);return;}collected=0;cycle=new AgathaContestCycle();runtime.start();}
    @Override public void onTick(){if(!isEnabled()||runtime==null||cycle==null)return;cycle.tick(runtime.read(),new AgathaContestCycle.Settings(Math.max(1000,Math.min(60000,config.get(AgathaConfig.AGATHA_STAGE_TIMEOUT_MS))),Math.max(100,Math.min(5000,config.get(AgathaConfig.AGATHA_ACK_MS))),Math.max(1,config.get(AgathaConfig.AGATHA_MIN_REWARD)),Math.max(1,Math.min(3,config.get(AgathaConfig.AGATHA_MAX_CLAIM_ATTEMPTS)))),runtime);if(cycle.terminal()){if(cycle.stage()==AgathaContestCycle.Stage.COMPLETE)collected++;setEnabled(false);}}
    @Override public void onDisable(){if(runtime!=null){if(cycle!=null&&!cycle.terminal())cycle.cancel(runtime);runtime.beginCleanup();}}
    @Override public void maintenanceTick(){if(!isEnabled()&&runtime!=null&&runtime.pendingCleanup())runtime.cleanupTick();}
    @Override public boolean hasPendingCleanup(){return runtime!=null&&runtime.pendingCleanup();}
    @Override public boolean allowsScreen(String title){return runtime!=null&&runtime.allowsScreen();}
    public AgathaContestCycle.Stage stage(){return cycle==null?AgathaContestCycle.Stage.CANCELLED:cycle.stage();}public int contestsCollected(){return collected;}public int claimAttempts(){return cycle==null?0:cycle.claims();}
    @Override public String diagnostics(){return id()+" | "+stage()+" | contests="+collected+" | claim attempts="+claimAttempts()+" | cleanup="+hasPendingCleanup()+" | "+(cycle==null?"":cycle.reason())+" | "+(runtime==null?"":runtime.problem());}
}
