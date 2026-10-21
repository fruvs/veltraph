package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.*;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(MultiPlayerGameMode.class)
public abstract class NativePlayerBirthCreationMixin {
 @WrapOperation(method="createPlayer(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/stats/StatsCounter;Lnet/minecraft/client/ClientRecipeBook;Lnet/minecraft/world/entity/player/Input;ZLnet/minecraft/client/player/ItemActivation;)Lnet/minecraft/client/player/LocalPlayer;",at=@At(value="NEW",target="(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/ClientPacketListener;Lnet/minecraft/stats/StatsCounter;Lnet/minecraft/client/ClientRecipeBook;Lnet/minecraft/world/entity/player/Input;ZLnet/minecraft/client/multiplayer/chat/ChatAbilities;Lnet/minecraft/client/player/ItemActivation;)Lnet/minecraft/client/player/LocalPlayer;"),require=1)
 private LocalPlayer veltraph$birthCreatedPlayer(Minecraft c,ClientLevel level,ClientPacketListener listener,StatsCounter stats,ClientRecipeBook recipes,Input input,boolean sprint,ChatAbilities abilities,ItemActivation activation,Operation<LocalPlayer> original){try{NativePlayerBirthProof.creationEntered();}catch(RuntimeException|Error unavailable){}var player=original.call(c,level,listener,stats,recipes,input,sprint,abilities,activation);try{NativePlayerBirthProof.created(player);}catch(RuntimeException|Error unavailable){}return player;}
}
