package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlayerCocoonTest {
    @Test void onlyActualStandaloneOwnBossHeaderCanArmTheLifecycle(){
        assertTrue(SlayerCocoon.receipt("§e YOU COCOONED YOUR SLAYER BOSS!"));
        assertTrue(SlayerCocoon.receipt("Header\nYOU COCOONED YOUR SLAYER BOSS"));
        assertFalse(SlayerCocoon.receipt("Player: YOU COCOONED YOUR SLAYER BOSS"));
        assertFalse(SlayerCocoon.receipt("YOU COCOONED SOMEONE ELSE'S SLAYER BOSS"));
    }
    @Test void UnrecoveredTextureAndTimingCannotGetAnImplicitDefault(){
        var c=new MapConfigService();assertTrue(SlayerCocoon.validation(c).isEmpty());c.set(SlayerConfig.SLAYER_COCOON_RECOVERY,true);
        assertTrue(SlayerCocoon.validation(c).isPresent());c.set(SlayerConfig.SLAYER_QUEST_BOSS_NAMES,"Atoned Horror");c.set(SlayerConfig.SLAYER_COCOON_HEAD_HASHES,"b".repeat(64));
        c.set(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS,true);c.set(SlayerConfig.SLAYER_QUEST_DROP_NAMES,"Rotten Flesh");c.set(SlayerConfig.SLAYER_QUEST_LOOT_TIMEOUT_MS,15000L);c.set(SlayerConfig.SLAYER_QUEST_DROP_RADIUS,6.0);
        assertTrue(SlayerCocoon.validation(c).isPresent());c.set(SlayerConfig.SLAYER_COCOON_TIMEOUT_MS,10000L);c.set(SlayerConfig.SLAYER_COCOON_RECEIPT_WINDOW_MS,1000L);c.set(SlayerConfig.SLAYER_COCOON_REPLACEMENT_RADIUS,6.0);
        assertTrue(SlayerCocoon.validation(c).isEmpty());c.set(SlayerConfig.SLAYER_COCOON_HEAD_HASHES,"unknown");assertTrue(SlayerCocoon.validation(c).isPresent());
    }
}
