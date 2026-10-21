package dev.veltraph.client.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only source invitation identity/history check; no incoming message replacement or injection. */
@Mixin(ChatComponent.class)
public interface AgathaChatAccessor {
    @Accessor("allMessages") List<GuiMessage> veltraphAgathaMessages();
}
