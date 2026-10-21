package dev.veltraph.client.mining.vein;
import dev.veltraph.client.config.MapConfigService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VeinMiningConfigTest {
 @Test void configChangeAndRevertInvalidatesStamp(){var c=new MapConfigService();var before=VeinMiningConfig.stamp(c);c.set(VeinMiningConfig.SCAN_RADIUS,5);c.set(VeinMiningConfig.SCAN_RADIUS,4);assertNotEquals(before,VeinMiningConfig.stamp(c));}
 @Test void defaultIsDisabled(){assertFalse(new MapConfigService().get(VeinMiningConfig.ENABLED));}
 @Test void prioritiesAreExplicitMaterialScoped(){assertEquals(10,VeinMiningConfig.priorities("GRAY_MITHRIL=1;TITANIUM=10").get(MiningMaterial.TITANIUM));}
 @Test void duplicateUnknownNegativeAndAllZeroAreDenied(){for(var value:new String[]{"GRAY_MITHRIL=1;GRAY_MITHRIL=2","NOT_ORE=1","GRAY_MITHRIL=-1","GRAY_MITHRIL=0","GRAY_MITHRIL=101",""})assertThrows(RuntimeException.class,()->VeinMiningConfig.priorities(value));}
}
