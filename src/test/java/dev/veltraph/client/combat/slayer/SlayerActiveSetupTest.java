package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlayerActiveSetupTest {
    @Test void unchangedRepeatedBoundariesReadEachCachedKeyExactlyOnce(){
        var config=new CountingConfig();var frozen=SlayerActiveSetup.capture(config);
        var keys=PersistableKeys.collect(List.of(SlayerConfig.class,CombatConfig.class,RotationConfig.class,TravelConfig.class));
        assertEquals(keys.size(),config.reads.size());assertTrue(config.reads.values().stream().allMatch(n->n==1));
        for(int boundary=0;boundary<1000;boundary++){
            config.reads.clear();assertTrue(frozen.current(config));
            assertEquals(keys.size(),config.reads.size());assertTrue(config.reads.values().stream().allMatch(n->n==1));
        }
    }
    @Test void everyRelevantDeclaredValueIsFrozenAndResetRestoresTheOriginalDefault(){
        var config=new MapConfigService();var frozen=SlayerActiveSetup.capture(config);
        for(var key:PersistableKeys.collect(List.of(SlayerConfig.class,CombatConfig.class,RotationConfig.class,TravelConfig.class))){
            alter(config,key);assertFalse(frozen.current(config),key.name());config.reset(key);assertTrue(frozen.current(config),key.name());
        }
    }
    @Test void typedValuesCannotAliasThroughTheirStringRepresentation(){
        var config=new CountingConfig();var frozen=SlayerActiveSetup.capture(config);
        config.overrideKey=SlayerConfig.SLAYER_QUEST_TIER;config.overrideValue="1";
        assertFalse(frozen.current(config));
    }
    @Test void unrelatedConfigurationDoesNotCancelTheCapturedQuest(){
        var config=new MapConfigService();var frozen=SlayerActiveSetup.capture(config);
        config.set(HydraConfig.STAFF_ALERT_ENABLED,false);assertTrue(frozen.current(config));
    }
    @Test void capturedNonDefaultValuesRemainStableAcrossReadOnlyPauseChecks(){
        var config=new MapConfigService();config.set(CombatConfig.COMBAT_RETREAT_DISTANCE,9.0);
        var frozen=SlayerActiveSetup.capture(config);for(int i=0;i<100;i++)assertTrue(frozen.current(config));
        config.set(CombatConfig.COMBAT_RETREAT_DISTANCE,8.0);assertFalse(frozen.current(config));
        config.set(CombatConfig.COMBAT_RETREAT_DISTANCE,9.0);assertTrue(frozen.current(config));
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void alter(ConfigService c,ConfigKey key){
        Object old=c.get(key),next;
        if(old instanceof String s)next=s+" changed";
        else if(old instanceof Boolean b)next=!b;
        else if(old instanceof Integer n)next=n+1;
        else if(old instanceof Long n)next=n+1;
        else if(old instanceof Double n)next=n+1;
        else if(old instanceof Enum e){var options=e.getDeclaringClass().getEnumConstants();next=Arrays.stream(options).filter(v->v!=e).findFirst().orElseThrow();}
        else throw new AssertionError("Unexpected mutable or unsupported setting "+key);
        c.set(key,next);
    }
    private static final class CountingConfig implements ConfigService {
        final MapConfigService delegate=new MapConfigService();final Map<String,Integer> reads=new HashMap<>();
        ConfigKey<?> overrideKey;Object overrideValue;
        @Override @SuppressWarnings("unchecked") public <T>T get(ConfigKey<T> key){reads.merge(key.name(),1,Integer::sum);return key==overrideKey?(T)overrideValue:delegate.get(key);}
        @Override public <T>void set(ConfigKey<T> key,T value){delegate.set(key,value);}
        @Override public boolean isSet(ConfigKey<?> key){return delegate.isSet(key);}
        @Override public void reset(ConfigKey<?> key){delegate.reset(key);}
    }
}
