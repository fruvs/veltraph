package dev.veltraph.client.hunting;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import net.minecraft.client.Minecraft;
import java.util.Optional;

/** Read-only, run-scoped exact capture accounting. Does not alter ordinary vanilla interaction. */
public final class ShardCaptureModule extends AbstractModule {
    public static final String ID="shard_capture";
    private final Minecraft client;private final ConfigService config;private final ShardCaptureTracker tracker=new ShardCaptureTracker();
    public ShardCaptureModule(Minecraft client,ConfigService config){super(ID,"Shard capture tracking",ModuleCategory.HUNTING);this.client=client;this.config=config;}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean ownsGameplayInput(){return false;}
    @Override public boolean isPassiveObserver(){return true;}
    @Override public Optional<String> preStartCheck(){
        if(!config.get(ShardConfig.SHARD_CAPTURE_ENABLED))return Optional.of("Enable shard capture tracking first");
        try{if(ShardCatalog.parse(config.get(ShardConfig.SHARD_CATALOG)).names().isEmpty())return Optional.of("Configure catalog-known shard identities");}
        catch(RuntimeException bad){return Optional.of(bad.getMessage());}return Optional.empty();
    }
    @Override public void onEnable(){tracker.reset();if(preStartCheck().isPresent())setEnabled(false);}
    @Override public void onTick(){if(client.player==null||!client.player.isAlive()||!config.get(ShardConfig.SHARD_CAPTURE_ENABLED))setEnabled(false);}
    @Override public void onServerMessage(String line){if(isEnabled())try{tracker.observe(line,ShardCatalog.parse(config.get(ShardConfig.SHARD_CATALOG)));}catch(RuntimeException bad){setEnabled(false);}}
    @Override public String diagnostics(){return "Shard captures (server chat, run scoped): "+tracker.counts();}
    public ShardCaptureTracker tracker(){return tracker;}
}
