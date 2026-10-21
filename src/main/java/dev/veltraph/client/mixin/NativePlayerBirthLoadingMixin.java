package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class NativePlayerBirthLoadingMixin {
 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;startWaitingForNewLevel(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)V"),require=1)
 private void veltraph$birthLoading(ClientPacketListener listener,LocalPlayer player,ClientLevel world,LevelLoadingScreen.Reason reason,Operation<Void> original){Object token=null;try{token=NativePlayerBirthProof.loadingEntered(player,world);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(listener,player,world,reason);normal=true;}finally{try{NativePlayerBirthProof.loadingExited(token,normal);}catch(RuntimeException|Error unavailable){}}}

 @WrapOperation(method="startWaitingForNewLevel(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/LevelLoadTracker;startClientLoad(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;)V"),require=1,allow=1)
 private void veltraph$birthLoadingTrackerStart(LevelLoadTracker tracker,LocalPlayer player,ClientLevel world,Operation<Void> original){NativePlayerBirthProof.LoadingUpdateScope scope=null;try{scope=NativePlayerBirthProof.loadingTrackerEntered(tracker,player,world);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(tracker,player,world);normal=true;}finally{try{NativePlayerBirthProof.loadingTrackerExited(scope,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="startWaitingForNewLevel(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;"),require=1,allow=1)
 private Screen veltraph$birthLoadingOriginalScreen(Gui gui,Operation<Screen> original){Screen returned=original.call(gui);try{NativePlayerBirthProof.loadingOriginalScreenReturned(returned);}catch(RuntimeException|Error unavailable){}return returned;}
 @WrapOperation(method="startWaitingForNewLevel(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/LevelLoadingScreen;update(Lnet/minecraft/client/multiplayer/LevelLoadTracker;Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)V"),require=1,allow=1)
 private void veltraph$birthLoadingOriginalUpdate(LevelLoadingScreen screen,LevelLoadTracker tracker,LevelLoadingScreen.Reason reason,Operation<Void> original){NativePlayerBirthProof.LoadingUpdateScope scope=null;try{scope=NativePlayerBirthProof.loadingUpdateEntered(screen,tracker,reason);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(screen,tracker,reason);normal=true;}finally{try{NativePlayerBirthProof.loadingUpdateExited(scope,normal);}catch(RuntimeException|Error unavailable){}}}
}
