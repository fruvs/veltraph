package dev.veltraph.client.mining.commission;

import dev.veltraph.client.config.*;
import java.util.*;

/** Commissions is separately opt-in. Existing vein tool/capacity/geometry settings remain mandatory. */
public final class CommissionConfig {
    private CommissionConfig() {}
    public static final ConfigKey<Boolean> ENABLED=ConfigKey.ofBool("COMMISSIONS_ENABLED",false);
    public static final ConfigKey<Long> OBSERVATION_MAX_AGE_MS=ConfigKey.ofLong("COMMISSIONS_OBSERVATION_MAX_AGE_MS",500);
    public record Stamp(ConfigService store,List<Long> revisions,List<Object> values) {}
    public static Stamp capture(ConfigService config){
        // Only the installed final stores have callback-free field-backed getters. Unknown adapters
        // cannot provide the last native refusal leaf and are unavailable to this runtime.
        if(!(config instanceof MapConfigService)&&!(config instanceof JsonConfigService))throw new IllegalStateException("Unknown native commission configuration store");
        var keys=List.<ConfigKey<?>>of(ENABLED,OBSERVATION_MAX_AGE_MS);
        var epochs=new ArrayList<Long>();var values=new ArrayList<Object>();
        for(var key:keys){long epoch=config.revision(key).orElseThrow();if(epoch<0||epoch==Long.MAX_VALUE)throw new IllegalStateException("Unknown commission config");
            Object raw=config.get(key);
            if(key==ENABLED&&(!(raw instanceof Boolean enabled)||!enabled))throw new IllegalStateException("Commissions disabled or nonboolean setting");
            if(key==OBSERVATION_MAX_AGE_MS&&(!(raw instanceof Long age)||age<50||age>5000))throw new IllegalStateException("Invalid native commission freshness scalar");
            epochs.add(epoch);values.add(raw);}
        for(int i=0;i<keys.size();i++)if(config.revision(keys.get(i)).orElse(-1)!=epochs.get(i))throw new IllegalStateException("Commission configuration changed");
        return new Stamp(config,List.copyOf(epochs),List.copyOf(values));
    }
}
