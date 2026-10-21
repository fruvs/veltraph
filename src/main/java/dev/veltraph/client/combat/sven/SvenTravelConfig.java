package dev.veltraph.client.combat.sven;

import dev.veltraph.client.config.*;
import java.util.function.Supplier;

/** Bind shared native etherwarp's actual item-lore lookup to the same source prerequisite token. */
final class SvenTravelConfig implements ConfigService {
    private final ConfigService underlying;private final Supplier<String> token;
    SvenTravelConfig(ConfigService underlying,Supplier<String> token){this.underlying=underlying;this.token=token;}
    @SuppressWarnings("unchecked") public <T>T get(ConfigKey<T> key){if(key.name().equals(TravelConfig.ETHERWARP_ABILITY_TOKEN.name())&&token.get()!=null)return (T)token.get();return underlying.get(key);}
    public <T>void set(ConfigKey<T> key,T value){throw new UnsupportedOperationException("Sven travel config is a frozen read adapter");}
    public boolean isSet(ConfigKey<?> key){return key.name().equals(TravelConfig.ETHERWARP_ABILITY_TOKEN.name())&&token.get()!=null||underlying.isSet(key);}
    public void reset(ConfigKey<?> key){throw new UnsupportedOperationException("Sven travel config is a frozen read adapter");}
}
