package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import java.util.List;
import java.util.Objects;

/** Activation snapshot of immutable typed scalar settings; no reflection or serialization per tick. */
final class SlayerActiveSetup {
    private static final List<ConfigKey<?>> KEYS=List.copyOf(PersistableKeys.collect(
            List.of(SlayerConfig.class,CombatConfig.class,RotationConfig.class,TravelConfig.class)));
    private final Object[] values;
    private SlayerActiveSetup(ConfigService config){
        values=new Object[KEYS.size()];
        for(int i=0;i<values.length;i++)values[i]=config.get(KEYS.get(i));
    }
    static SlayerActiveSetup capture(ConfigService config){return new SlayerActiveSetup(Objects.requireNonNull(config));}
    /** One fresh lookup per selected key, with immediate rejection; stored defaults remain typed. */
    boolean current(ConfigService config){
        for(int i=0;i<values.length;i++)if(!Objects.equals(values[i],config.get(KEYS.get(i))))return false;
        return true;
    }
}
