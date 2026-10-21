package dev.veltraph.client.gui;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.inventory.equipment.McEquipmentWorkflow;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import dev.veltraph.client.service.mc.McHeldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import java.util.*;

/** Opt-in experiment solver with optional Guardian and table/renewal/XP flows under one owner. */
public final class ExperimentsModule extends AbstractModule {
    public static final String ID="experiments";
    private final Minecraft client;
    private final ConfigService config;
    private final McMenuAccess menus;
    private final ExperimentsRuntime runtime=new ExperimentsRuntime();
    private final ExperimentsGuardianPreflight guardian;
    private final ExperimentsParentRuntime parent=new ExperimentsParentRuntime();
    private final McExperimentsParentAccess parentAccess;
    private ExperimentsParentSettings parentSettings;
    private boolean parentStarted;
    private ExperimentSettings settings;
    private ExperimentsGuardianSettings guardianSettings;
    private Object player,world,connection;
    private long tick;
    private String stoppedReason="";
    public ExperimentsModule(Minecraft client,ConfigService config) {
        super(ID,"Experiments Table",ModuleCategory.MISC);this.client=client;this.config=config;menus=new McMenuAccess(client);
        guardian=new ExperimentsGuardianPreflight(new McEquipmentWorkflow(client));parentAccess=new McExperimentsParentAccess(client,menus);
    }
    @Override public boolean requiresSkyBlock() { return true; }
    @Override public boolean ownsGameplayInput() { return true; }
    @Override public boolean allowsScreen(String title) {
        return (settings!=null&&settings.rewardThreshold()!=null&&MenuSnapshot.clean(title).equals(settings.rewardThreshold().rewardMenuTitle()))||(parentSettings!=null&&parentSettings.allowsScreen(title))||guardian.allowsScreen(title)||ExperimentsRuntime.identify(title)!=null||MenuSnapshot.clean(title).equals("Experimentation Table");
    }
    @Override public Optional<String> preStartCheck() {
        try { ExperimentsConfig.settings(config);ExperimentsGuardianConfig.settings(config);ExperimentsParentConfig.settings(config);return Optional.empty(); }
        catch(IllegalArgumentException invalid) { return Optional.of(invalid.getMessage()); }
    }
    private void releaseKeys(){var o=client.options;for(KeyMapping key:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse})McHeldKeys.setHeld(key,false);}
    @Override public void onEnable() {
        releaseKeys();menus.clear();parentAccess.clear();runtime.reset();tick=0;stoppedReason="";parentStarted=false;
        try {settings=ExperimentsConfig.settings(config);guardianSettings=ExperimentsGuardianConfig.settings(config);parentSettings=ExperimentsParentConfig.settings(config);}
        catch(IllegalArgumentException invalid){stoppedReason=invalid.getMessage();setEnabled(false);return;}
        player=client.player;world=client.level;connection=client.getConnection();guardian.start(guardianSettings,0);
    }
    @Override public void onDisable() { releaseKeys();settings=null;guardian.cancel();parent.cancel();parentAccess.clear();runtime.releaseSession();menus.clear();player=null;world=null;connection=null; }
    @Override public void onTick() {
        releaseKeys();
        if(client.player==null||!client.player.isAlive()||client.gameMode==null||client.player!=player||client.level!=world||client.getConnection()!=connection) {
            stop("Experiment player/world/connection changed");return;
        }
        try {
            if(!Objects.equals(settings,ExperimentsConfig.settings(config))||!Objects.equals(guardianSettings,ExperimentsGuardianConfig.settings(config))||!Objects.equals(parentSettings,ExperimentsParentConfig.settings(config))) {
                stop("Frozen experiment/Guardian/parent bindings changed; restart with fresh observations");return;
            }
        }catch(IllegalArgumentException invalid){stop("Experiment/Guardian config changed: "+invalid.getMessage());return;}
        if(!guardian.ready()) {
            guardian.tick(tabRows(),++tick);
            if(guardian.state()==ExperimentsGuardianPreflight.State.FAILED||guardian.state()==ExperimentsGuardianPreflight.State.CANCELLED)setEnabled(false);
            // Solver starts on the next fresh menu observation after Guardian has returned.
            return;
        }
        if(parentSettings!=null) {
            long now=System.currentTimeMillis();
            if(!parentStarted){parent.start(parentSettings,now);parentStarted=true;}
            var observation=parentAccess.read();var parentAction=parent.tick(observation,settings,now);
            if(parentAction.kind()!=ExperimentsParentRuntime.Kind.NONE&&!parentAccess.dispatch(parentAction,observation))parent.rejectedAction();
            if(parent.state()==ExperimentsParentRuntime.State.FAILED||parent.state()==ExperimentsParentRuntime.State.COMPLETE)setEnabled(false);
            return;
        }
        var action=runtime.tick(menus.read(),settings,System.currentTimeMillis());
        if(action.kind()==ExperimentsRuntime.Kind.CLICK&&!menus.click(action.menu(),action.slot())) runtime.rejectedClick();
        if(action.kind()==ExperimentsRuntime.Kind.CLOSE) menus.close(action.menu().id());
        if(runtime.state()==ExperimentsRuntime.State.FAILED||runtime.state()==ExperimentsRuntime.State.COMPLETE) setEnabled(false);
    }
    private List<String> tabRows(){
        if(!(client.gui.hud.getTabList() instanceof PlayerListHudAccessor accessor))return List.of();
        return accessor.veltraph$getPlayerInfos().stream().map(info->info.getTabListDisplayName()).filter(Objects::nonNull)
                .map(component->MenuSnapshot.clean(component.getString())).toList();
    }
    @Override public void onServerMessage(String line){if(isEnabled()&&parentStarted)parent.onServerMessage(line);}
    private void stop(String why){stoppedReason=why;setEnabled(false);}
    @Override public String diagnostics() {
        if(!stoppedReason.isBlank())return "FAILED: "+stoppedReason+"; "+guardian.diagnostics();
        String flow=parentSettings==null?runtime.diagnostics():parent.diagnostics()+"; "+parentAccess.diagnostics();
        return guardianSettings!=null&&guardianSettings.enabled()
                ?(guardian.ready()?flow+"; ":"")+guardian.diagnostics():flow;
    }
}
