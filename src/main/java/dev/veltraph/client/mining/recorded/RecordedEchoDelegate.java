package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.config.*;
import java.util.Optional;

/** Existing Echo module may delegate its lifecycle here while keeping its original public identity. */
public final class RecordedEchoDelegate {
    private final ConfigService config;private final RecordedEchoPort port;
    private final RecordedEchoController engine;
    private String startedProfile="";private Object guard;
    public RecordedEchoDelegate(ConfigService config,RecordedEchoPort port){this.config=config;this.port=port;engine=new RecordedEchoController(port);}
    public boolean configured(){return !config.get(RecordedEchoConfig.PROFILE).isBlank();}
    public Optional<String> preStartCheck() {
        if(!port.mining().available())return Optional.of("Recorded Echo requires a loaded world and closed screen");
        try {
            var profile=RecordedEchoProfile.parse(config.get(RecordedEchoConfig.PROFILE));
            if(!engine.startPosition(profile,config.get(MiningConfig.ECHO_REMEMBER_LAST_SESSION)).equals(port.mining().farm().player().blockPos()))return Optional.of("Stand at the recorded Echo start/resume checkpoint");
            if(!profile.checkpoints().stream().allMatch(p->port.mining().farm().world().isLoaded(p.position())))return Optional.of("Recorded Echo checkpoint chunks are unloaded");
            return Optional.empty();
        }catch(RuntimeException invalid){return Optional.of("Invalid recorded Echo design: "+invalid.getMessage());}
    }
    public void start(Object owner) {
        if(owner==null)throw new IllegalArgumentException("Recorded Echo requires an input owner");
        startedProfile=config.get(RecordedEchoConfig.PROFILE);guard=owner;
        engine.start(RecordedEchoProfile.parse(startedProfile),config.get(MiningConfig.ECHO_REMEMBER_LAST_SESSION));
        port.mining().farm().breaking().installTargetGuard(owner,p->engine.allowsTarget(p,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE))));
    }
    /** False requests disable when the configured recording changes or its final arrival completes. */
    public boolean tick() {
        if(!startedProfile.equals(config.get(RecordedEchoConfig.PROFILE))){stop();return false;}
        engine.tick(config.get(MiningConfig.ECHO_ALLOW_CHECKPOINT_SKIPPING),Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE)),config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS),config.get(MiningConfig.AUTO_BREAK_MINING));
        return engine.state()!=RecordedEchoController.State.COMPLETE;
    }
    public void stop(){engine.stop();if(guard!=null)port.mining().farm().breaking().clearTargetGuard(guard);guard=null;port.mining().cleanup();}
    public void resetAtRewarp(){if(config.get(MiningConfig.ECHO_RESET_AT_REWARP))engine.resetAtRewarp();}
    /** An active recorded list/outcome remains owned until a dedicated full-component pause adapter exists. */
    public Optional<String> scheduledRestReadiness(){return Optional.of("Recorded Echo owns checkpoint input and bound click resources");}
    public RecordedEchoController engine(){return engine;}
    public String diagnostics(){return "RECORDED "+engine.state()+" | "+engine.reason()+" | checkpoint="+engine.checkpoint()+" removed="+engine.removed()+" placed="+engine.placed();}
}
