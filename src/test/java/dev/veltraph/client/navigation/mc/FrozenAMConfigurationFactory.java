package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.config.OrderedConfigurationData;
import java.util.List;
/** Source-pinned original private wrapper factory only. No SDK/native admission. */
final class FrozenAMConfigurationFactory {
    record Configuration(List<Long> epochs,List<Object> values){}
    static Configuration create(OrderedConfigurationData.Data observed){return new Configuration(observed.epochs(),observed.values());}
}
