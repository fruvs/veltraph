package dev.veltraph.client.combat.heal.mc;

import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import static org.junit.jupiter.api.Assertions.*;

class McReceivedHealthTest {
    @Test void nativeLiteralSourceLegacyFormatRetainsFullMaximumAndRawValue(){var value=McReceivedHealth.parseLegacy(Component.literal("§c1,234/2,000❤   §b200/300✎ Mana")).orElseThrow();assertEquals(1234,value.health());assertEquals(2000,value.maximum());assertEquals('❤',value.glyph());}
    @Test void asciiNumberFullMaxAndSingleLeadingCueAreRequired(){for(String bad:new String[]{"§c1,,234/2,000❤","§c١٢٣/200❤","other 50/100❤","50/0❤","50/100❤  60/100❤"})assertTrue(McReceivedHealth.parseLegacy(Component.literal(bad)).isEmpty());assertTrue(McReceivedHealth.parseLegacy(Component.literal("50/100❤").withStyle(ChatFormatting.BOLD)).isEmpty());}
}
