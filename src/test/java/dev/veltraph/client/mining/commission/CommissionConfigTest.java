package dev.veltraph.client.mining.commission;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class CommissionConfigTest {
    @Test void onlyEnabledKnownFinalStoreCanAdmit(){var map=new MapConfigService();assertThrows(IllegalStateException.class,()->CommissionConfig.capture(map));map.set(CommissionConfig.ENABLED,true);assertSame(map,CommissionConfig.capture(map).store());}
    @Test void unknownStoreRejectedBeforeAnyGetter(){var calls=new AtomicInteger();ConfigService unknown=new ConfigService(){public <T>T get(ConfigKey<T> key){calls.incrementAndGet();return key.defaultValue();}public <T>void set(ConfigKey<T> key,T v){}public boolean isSet(ConfigKey<?> key){return false;}public void reset(ConfigKey<?> key){}};assertThrows(IllegalStateException.class,()->CommissionConfig.capture(unknown));assertEquals(0,calls.get());}
    @SuppressWarnings({"rawtypes","unchecked"}) @Test void rawObjectCannotBecomeStampOrExecuteEquals(){var map=new MapConfigService();map.set(CommissionConfig.ENABLED,true);var calls=new AtomicInteger();Object raw=new Object(){@Override public boolean equals(Object other){calls.incrementAndGet();return true;}};map.set((ConfigKey)CommissionConfig.OBSERVATION_MAX_AGE_MS,raw);assertThrows(IllegalStateException.class,()->CommissionConfig.capture(map));assertEquals(0,calls.get());}
    @Test void configChangedAndRevertedHasDistinctStamp(){var map=new MapConfigService();map.set(CommissionConfig.ENABLED,true);var before=CommissionConfig.capture(map);map.set(CommissionConfig.OBSERVATION_MAX_AGE_MS,501L);map.set(CommissionConfig.OBSERVATION_MAX_AGE_MS,500L);assertNotEquals(before,CommissionConfig.capture(map));}
    @Test void malformedScalarAndRangeRejected(){var map=new MapConfigService();map.set(CommissionConfig.ENABLED,true);for(long age:new long[]{0,49,5001,Long.MAX_VALUE}){map.set(CommissionConfig.OBSERVATION_MAX_AGE_MS,age);assertThrows(IllegalStateException.class,()->CommissionConfig.capture(map));}}
}
