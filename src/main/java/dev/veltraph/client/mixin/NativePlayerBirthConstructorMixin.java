package dev.veltraph.client.mixin;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class NativePlayerBirthConstructorMixin {
 // The receiver is initialized here, before any LocalPlayer field initializer or body write.
 @Inject(method="<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/ClientPacketListener;Lnet/minecraft/stats/StatsCounter;Lnet/minecraft/client/ClientRecipeBook;Lnet/minecraft/world/entity/player/Input;ZLnet/minecraft/client/multiplayer/chat/ChatAbilities;Lnet/minecraft/client/player/ItemActivation;)V",
         at=@At(value="CTOR_HEAD",unsafe=true,args="enforce=POST_DELEGATE"),require=1,allow=1)
 private void veltraph$birthConstructedReceiver(CallbackInfo ci){try{NativePlayerBirthProof.constructorEntered((LocalPlayer)(Object)this);}catch(RuntimeException|Error unavailable){}}
}
