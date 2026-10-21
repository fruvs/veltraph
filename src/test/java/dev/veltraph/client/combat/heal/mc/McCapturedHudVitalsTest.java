package dev.veltraph.client.combat.heal.mc;

import org.junit.jupiter.api.Test;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import dev.veltraph.client.service.mc.NativeActionBarSignal;
import static org.junit.jupiter.api.Assertions.*;

class McCapturedHudVitalsTest {
    private static final String FRAME="§c1,401/1,434\uE010     §a791§a\uE008 Defense     §b1,290/1,290\uE003 Mana     ";
    @Test void actualNativeLiteralDefaultStyleSystemOverlayIsAccepted(){var parsed=McCapturedHudVitals.parseNative(Component.literal(FRAME),NativeActionBarSignal.Origin.SYSTEM_OVERLAY).orElseThrow();assertEquals(1401,parsed.currentHealth());assertEquals(1290,parsed.currentMana());}
    @Test void actualNativeFontOverrideCannotMasqueradeAsCapturedDefaultLiteral(){var changed=Component.literal(FRAME).withStyle(style->style.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.parse("fixture:changed"))));assertTrue(McCapturedHudVitals.parseNative(changed,NativeActionBarSignal.Origin.SYSTEM_OVERLAY).isEmpty());}
    @Test void titleOriginStyledRootSiblingsAndTranslatableContentsAreUnknown(){assertTrue(McCapturedHudVitals.parseNative(Component.literal(FRAME),NativeActionBarSignal.Origin.TITLE_PACKET).isEmpty());assertTrue(McCapturedHudVitals.parseNative(Component.literal(FRAME).withStyle(ChatFormatting.BOLD),NativeActionBarSignal.Origin.SYSTEM_OVERLAY).isEmpty());assertTrue(McCapturedHudVitals.parseNative(Component.literal(FRAME).append(Component.literal("extra")),NativeActionBarSignal.Origin.SYSTEM_OVERLAY).isEmpty());assertTrue(McCapturedHudVitals.parseNative(Component.translatable(FRAME),NativeActionBarSignal.Origin.SYSTEM_OVERLAY).isEmpty());}
}
