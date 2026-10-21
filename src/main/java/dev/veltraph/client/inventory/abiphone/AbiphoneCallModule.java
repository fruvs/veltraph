package dev.veltraph.client.inventory.abiphone;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.service.mc.McHeldKeys;
import net.minecraft.client.*;
import java.util.*;

/** Standalone local contact call. Shared ingress callers can instead own an AbiphoneCall/Port pair. */
public final class AbiphoneCallModule extends AbstractModule {
    public static final String ID="abiphone_call";
    private final Minecraft client;private final ConfigService config;private final AbiphoneCall call;private long tick;private AbiphoneCall.Request authorized;
    public AbiphoneCallModule(Minecraft client,ConfigService config){super(ID,"Abiphone Contact Call",ModuleCategory.MISC);this.client=client;this.config=config;call=new AbiphoneCall(new McAbiphonePort(client));}
    public AbiphoneCall call(){return call;}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean allowsScreen(String title){return call.request()!=null&&call.request().allowsTitle(title);}
    @Override public Optional<String> preStartCheck(){
        if(call.active()||call.state()==AbiphoneCall.State.HANDOFF||call.state()==AbiphoneCall.State.RECOVERY_REQUIRED)return Optional.of("Abiphone call, dialogue lease or manual recovery still owned");
        if(!config.get(AbiphoneConfig.ABIPHONE_CALL_ENABLED))return Optional.of("Enable Abiphone Contact Call explicitly");
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null)return Optional.of("Living loaded player required");
        if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Close other menus and empty cursor");
        try{AbiphoneConfig.request(config);}catch(IllegalArgumentException e){return Optional.of(e.getMessage());}return Optional.empty();
    }
    private void release(){var o=client.options;for(KeyMapping key:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse})McHeldKeys.setHeld(key,false);}
    @Override public void onEnable(){release();tick=0;var blocker=preStartCheck();if(blocker.isPresent()){setEnabled(false);return;}authorized=AbiphoneConfig.request(config);call.start(authorized,tick);if(call.terminal())setEnabled(false);}
    @Override public void onTick(){release();boolean unchanged=false;try{unchanged=config.get(AbiphoneConfig.ABIPHONE_CALL_ENABLED)&&authorized.equals(AbiphoneConfig.request(config));}catch(IllegalArgumentException ignored){}
        if(!unchanged)call.cancel();call.tick(++tick);if(call.terminal()&&call.state()!=AbiphoneCall.State.HANDOFF)setEnabled(false);}
    @Override public void onServerMessage(String line){call.onServerMessage(line);}
    @Override public void onDisable(){release();if(call.active()||call.state()==AbiphoneCall.State.HANDOFF)call.cancel();}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&(call.active()||call.recoveryReservesCurrentContext());}
    @Override public void maintenanceTick(){if(!isEnabled()&&call.active()){release();call.tick(++tick);}}
    @Override public String diagnostics(){return call.state()+": "+call.reason();}
}
