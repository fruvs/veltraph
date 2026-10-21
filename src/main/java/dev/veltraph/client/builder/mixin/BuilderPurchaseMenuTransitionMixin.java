package dev.veltraph.client.builder.mixin;
import dev.veltraph.client.builder.Source95MenuTransitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class BuilderPurchaseMenuTransitionMixin {
 @Inject(method="handleOpenScreen",at=@At("TAIL"))
 private void veltraph$sourceContainer(ClientboundOpenScreenPacket packet,CallbackInfo ci){record(Source95MenuTransitions.Kind.CONTAINER);}
 @Inject(method="handleOpenSignEditor",at=@At("TAIL"))
 private void veltraph$sourceSign(ClientboundOpenSignEditorPacket packet,CallbackInfo ci){record(Source95MenuTransitions.Kind.SIGN);}
 private void record(Source95MenuTransitions.Kind kind){var c=Minecraft.getInstance();if(!c.isSameThread()||c.player==null||c.level==null||c.getConnection()!=(Object)this||c.player.level()!=c.level||kind==Source95MenuTransitions.Kind.CONTAINER&&!(c.gui.screen() instanceof AbstractContainerScreen)||kind==Source95MenuTransitions.Kind.SIGN&&!(c.gui.screen() instanceof AbstractSignEditScreen))return;Source95MenuTransitions.received(this,kind,c.player,c.level,c.player.containerMenu,c.gui.screen(),c.player.containerMenu.containerId);}
}
