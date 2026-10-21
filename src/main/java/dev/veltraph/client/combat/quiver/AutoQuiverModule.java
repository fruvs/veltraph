package dev.veltraph.client.combat.quiver;
import dev.veltraph.client.core.*;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.Optional;

/** Explicit one-refill entrypoint; a configured owned menu may already be open. */
public final class AutoQuiverModule extends AbstractModule {
    public static final String ID="auto_quiver";private final McQuiverRefill refill;
    public AutoQuiverModule(Minecraft client,FarmServices services,ConfigService config){super(ID,"Quiver Refill",ModuleCategory.COMBAT);refill=new McQuiverRefill(client,services,config);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&refill.consumeExpectedTeleport(position,now);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<String> preStartCheck(){return refill.configError();}
    @Override public boolean allowsScreen(String title){return refill.allowsScreen(title);}
    @Override public void onEnable(){if(!refill.request())setEnabled(false);}
    @Override public void onTick(){refill.tick();if(!refill.active())setEnabled(false);}
    @Override public void onServerMessage(String line){refill.onMessage(line);}
    @Override public void onDisable(){refill.cancel("Quiver refill stopped");}
    @Override public String diagnostics(){return refill.status();}
}
