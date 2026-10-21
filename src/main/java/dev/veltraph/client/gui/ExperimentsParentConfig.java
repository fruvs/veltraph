package dev.veltraph.client.gui;

import com.google.gson.Gson;
import dev.veltraph.client.config.*;

/** Independently opt-in parent. Profile is a typed JSON contract, blank until locally observed. */
public final class ExperimentsParentConfig {
    private ExperimentsParentConfig() {}
    public static final ConfigKey<Boolean> EXPERIMENTS_PARENT_ENABLED=ConfigKey.ofBool("EXPERIMENTS_PARENT_ENABLED",false);
    public static final ConfigKey<String> EXPERIMENTS_PARENT_PROFILE=new ConfigKey<>("EXPERIMENTS_PARENT_PROFILE","");
    public static ExperimentsParentSettings settings(ConfigService c) {
        if(!c.get(EXPERIMENTS_PARENT_ENABLED))return null;
        try {
            var p=new Gson().fromJson(c.get(EXPERIMENTS_PARENT_PROFILE),ExperimentsParentSettings.class);
            if(p==null)throw new IllegalArgumentException("Experiments parent needs explicit local JSON contracts");
            return p;
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid Experiments parent profile: "+invalid.getMessage(),invalid);}
    }
}
