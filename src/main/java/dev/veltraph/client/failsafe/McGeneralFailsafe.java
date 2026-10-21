package dev.veltraph.client.failsafe;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.hydra.Whitelist;
import java.util.*;
import net.minecraft.client.Minecraft;

public final class McGeneralFailsafe {
    private final Minecraft client;private final ConfigService config;private final ModuleManager modules;
    private final GeneralFailsafe runtime;
    private java.util.function.BooleanSupplier externalControlOwned = () -> false;
    public void externalControlOwned(java.util.function.BooleanSupplier owned) {
        externalControlOwned = java.util.Objects.requireNonNull(owned);
    }
    private boolean externalOwned() {
        try { return externalControlOwned.getAsBoolean(); }
        catch (RuntimeException unavailable) { return true; }
    }
    public McGeneralFailsafe(Minecraft client,ConfigService config,ModuleManager modules,GeneralFailsafe.Actions actions) {
        this.client=client;this.config=config;this.modules=modules;runtime=new GeneralFailsafe(config,actions);
    }
    public void tick() {
        boolean external = externalOwned();
        boolean active=(!modules.enabledControllers().isEmpty()||external)&&client.player!=null&&client.level!=null;
        if(!active) { runtime.tick(false,false,List.of());return; }
        var self=client.player;var whitelist=Whitelist.parse(config.get(HydraConfig.LOCAL_WHITELIST));
        List<GeneralFailsafe.Player> nearby=new ArrayList<>();
        for(var other:client.level.players()) {
            if(other==self||other.isSpectator()||other.isRemoved()||client.getConnection()==null
                    ||client.getConnection().getPlayerInfo(other.getUUID())==null) continue;
            var toOther=other.getEyePosition().subtract(self.getEyePosition()).normalize();
            nearby.add(new GeneralFailsafe.Player(self.distanceTo(other),whitelist.contains(other.getName().getString()),
                    other.getViewVector(1).dot(toOther.scale(-1))>.95,
                    self.getViewVector(1).dot(toOther)>0,self.hasLineOfSight(other),other.getUUID().toString()));
        }
        runtime.tick(true,self.getY()<client.level.getMinY()-4,nearby,external);
    }
    public void onMessage(String line) {
        boolean external = externalOwned();
        if(client.player!=null&&client.level!=null) runtime.mention(!modules.enabledControllers().isEmpty()||external,
                client.player.getName().getString(),line,external);
    }
    public void sourcePolicies(java.util.function.Supplier<FailsafeSourcePolicy> player,
                               java.util.function.Supplier<FailsafeSourcePolicy> mention) {
        runtime.sourcePolicies(player, mention);
    }
    public void onWorldStop() { runtime.reset(); }
    public void mentionDelays(java.util.function.LongSupplier minimum, java.util.function.LongSupplier maximum) {
        runtime.mentionDelays(minimum, maximum);
    }
    public void onMentionObserved() {
        boolean external=externalOwned();
        runtime.mentionObserved((!modules.enabledControllers().isEmpty()||external)&&client.player!=null&&client.level!=null,external);
    }
    public void onSuspiciousObservations(int count) {
        boolean external=externalOwned();
        runtime.suspicious((!modules.enabledControllers().isEmpty()||external)&&client.player!=null&&client.level!=null,count,external);
    }
}
