package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Actual Root-created read-only timer producer. Timing frames never grant native gear authority. */
public final class McPestEarlyLoadoutObservation {
    private final Minecraft client;private final ConfigService config;private final VeltraphBootstrap.RootEarlyLoadoutObservationSource source;
    private Session session;private TimingFrame latest;private String status="Observed early loadout bindings unavailable";
    private final class Session {
        final Object player=client.player,world=client.level,connection=client.getConnection(),gameMode=client.gameMode;final UUID uuid=client.player.getUUID();final FarmModule farm;
        final ModuleManager registry=VeltraphBootstrap.moduleManager();final ModuleManager.LifecycleSnapshot lifecycle=registry.lifecycleSnapshot();
        final MapConfigService.StoredStateSnapshot settings;final PestEarlyLoadoutBindings bindings;final String profile;
        final PestEarlyLoadoutPolicy.Thresholds thresholds;final PestEquipmentCooldown cooldown=new PestEquipmentCooldown();boolean revoked;long messageFloor=-1;
        Session(FarmModule farm,MapConfigService.StoredStateSnapshot settings,PestEarlyLoadoutBindings bindings){this.farm=farm;this.settings=settings;this.bindings=bindings;
            profile=McFarmCycleCoordinator.profileId(farm);thresholds=new PestEarlyLoadoutPolicy.Thresholds(java.util.concurrent.ThreadLocalRandom.current().nextInt(5,15),java.util.concurrent.ThreadLocalRandom.current().nextInt(-2,2));}
        boolean storedCurrent(){return !revoked&&client.isSameThread()&&source.affiliates(McPestEarlyLoadoutObservation.this,client,config)
            &&client.player==player&&client.level==world&&client.gameMode==gameMode&&client.getConnection()==connection&&VeltraphBootstrap.moduleManager()==registry&&VeltraphBootstrap.config()==config
            &&lifecycle!=null&&lifecycle.known()&&registry.lifecycleCurrent(lifecycle)&&settings!=null&&settings.current();}
        boolean current(){return storedCurrent()
            &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&uuid.equals(client.player.getUUID())
            &&client.player.isAlive()&&client.player.level()==world&&VeltraphBootstrap.moduleManager()==registry&&VeltraphBootstrap.config()==config
            &&lifecycle!=null&&lifecycle.known()&&registry.lifecycleCurrent(lifecycle)&&settings!=null&&settings.current()
            &&registry.get(farm.id()).orElse(null)==farm&&farm.isEnabled()&&profile.equals(McFarmCycleCoordinator.profileId(farm));}
        PestEquipmentCooldown.Scope timingScope(){return new PestEquipmentCooldown.Scope(player,world,connection,farm,this,profile);}
        void revoke(){revoked=true;cooldown.invalidate();}
    }
    /** Opaque native observation only; Root's separate closed dispatch and gear receipts are required for effects. */
    public static final class TimingFrame {
        private final McPestEarlyLoadoutObservation issuer;private final Session session;private final long tick;private final PestEarlyLoadoutPolicy.Desired desired;
        private TimingFrame(McPestEarlyLoadoutObservation issuer,Session session,long tick,PestEarlyLoadoutPolicy.Desired desired){this.issuer=issuer;this.session=session;this.tick=tick;this.desired=desired;}
        public FarmModule farm(){return session.farm;}public long tick(){return tick;}public PestEarlyLoadoutPolicy.Desired desired(){return desired;}
        /** Read-only refusal check; native profile/context reads are not a callback-free final action leaf. */
        public boolean currentOriginal(){return issuer.latest==this&&issuer.session==session&&session.current();}
    }
    public McPestEarlyLoadoutObservation(Minecraft client,ConfigService config,VeltraphBootstrap.RootEarlyLoadoutObservationSource source){this.client=Objects.requireNonNull(client);this.config=Objects.requireNonNull(config);this.source=Objects.requireNonNull(source);}
    private boolean available(FarmModule farm){var root=VeltraphBootstrap.moduleManager();var ctx=VeltraphBootstrap.gameContext();
        return source.affiliates(this,client,config)&&client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&root!=null&&ctx!=null
            &&client.player.isAlive()&&client.getCameraEntity()==client.player&&!client.isPaused()&&!client.player.isSpectator()&&!client.player.isPassenger()&&!client.player.isScoping()&&!client.player.isInLava()
            &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
            &&root.get(farm.id()).orElse(null)==farm&&farm.isEnabled()&&!farm.isSuspended()&&root.inputOwner().orElse(null)==farm
            &&ctx.current().onHypixel()&&ctx.current().inSkyBlock()&&ctx.current().isArea(GameArea.GARDEN)
            &&config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)&&config.get(PestLoadoutConfig.PEST_SWAP_EARLY);}
    private Session capture(FarmModule farm){try{var settings=config instanceof MapConfigService map?map.storedStateSnapshot():config instanceof JsonConfigService json?json.storedStateSnapshot():null;if(settings==null)return null;
            var bindings=PestEarlyLoadoutBindings.read(config.get(PestLoadoutConfig.PEST_EARLY_OBSERVED_BINDINGS));var next=new Session(farm,settings,bindings);return next.current()?next:null;
        }catch(RuntimeException unknown){return null;}}
    private void receive(Session s){
        var receipts=source.received(this);if(receipts==null){s.revoke();return;}
        if(s.messageFloor<0){s.messageFloor=receipts.isEmpty()?0:receipts.getLast().serial();return;} // Never seed from pre-admission history.
        for(var receipt:receipts){if(receipt.serial()<=s.messageFloor)continue;
            if(receipt.receiver()!=s.connection||receipt.observationScope()!=s){s.revoke();return;}
            if(!s.current()||receipt.serial()!=s.messageFloor+1){s.revoke();return;}
            s.messageFloor=receipt.serial();s.cooldown.received(s.timingScope(),s.bindings.equipment(),receipt.serial(),receipt.message(),receipt.observedMillis());
        }
    }
    /** Called only by actual Root tick; all produced decisions remain observational until closed dispatch. */
    public TimingFrame observe(FarmModule farm,long tick,List<String> actualColorStrippedTabRows,long now){
        latest=null;if(farm==null||tick<0||now<0||!available(farm)){revoke();return null;}
        if(session==null){session=capture(farm);if(session==null){status="Exact current timer/spawn bindings and known config history required";return null;}}
        if(session.farm!=farm||!session.current()){revoke();status="Original early timing scene/config/lifecycle changed";return null;}
        var s=session;receive(s);if(!s.current()){revoke();return null;}var equipment=s.cooldown.remaining(s.timingScope(),s.bindings.equipment(),now);
        var observation=PestEarlyLoadoutPolicy.read(s.bindings.timer(),actualColorStrippedTabRows,equipment.isPresent()?equipment.getAsInt():null);
        var desired=PestEarlyLoadoutPolicy.decide(true,true,s.thresholds,observation);
        if(!s.current())return null;latest=new TimingFrame(this,s,tick,desired);status="Observed early loadout desired="+desired+"; native gear custody required";return latest;
    }
    public void revoke(){latest=null;if(session!=null)session.revoke();session=null;status="Original early timing context retired; new native observation required";}
    /** Private-state observation identity only; no callback or native permission. */
    public Object receiveScopeIdentity(){return session!=null&&session.storedCurrent()?session:null;}
    public TimingFrame latestObservation(){return latest;}
    public String status(){return status;}
}
