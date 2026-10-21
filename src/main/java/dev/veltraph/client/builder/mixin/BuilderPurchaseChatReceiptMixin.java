package dev.veltraph.client.builder.mixin;
import dev.veltraph.client.builder.Source95ChatReceipts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Passive TAIL journal of actually handled native system-message events. No sending/cancellation/rewrites. */
@Mixin(ClientPacketListener.class)
public abstract class BuilderPurchaseChatReceiptMixin {
 @Inject(method="handleSystemChat",at=@At("TAIL"))
 private void veltraph$sourcePurchaseMessage(ClientboundSystemChatPacket packet,CallbackInfo ci){var c=Minecraft.getInstance();if(!c.isSameThread()||c.player==null||c.level==null||c.getConnection()!=(Object)this||c.player.level()!=c.level)return;Source95ChatReceipts.received(this,new Source95ChatReceipts.Scope(c.player,c.level,this,c.player.containerMenu,c.gui.screen(),c.player.containerMenu.containerId),packet.content().getString(),packet.overlay());}
}
