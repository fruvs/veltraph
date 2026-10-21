package dev.veltraph.client.foraging.supply;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** This contract fails on the baseline that has no standalone source utility. */
final class ForagingSupplyPresenceTest {
    @Test void standaloneUtilityHasSelectedSourceOptionAndOrdinaryParent() throws Exception {
        var config=Class.forName("dev.veltraph.client.foraging.supply.ForagingSupplyConfig");
        var key=config.getField("FORAGING_AUTO_FILL_TYPE").get(null);
        assertEquals("SAPLING",key.getClass().getMethod("defaultValue").invoke(key).toString());
        var module=Class.forName("dev.veltraph.client.foraging.supply.ForagingSupplyModule");
        assertEquals("foraging_chest_fill",module.getField("ID").get(null));
    }
}
