package dev.veltraph.client.mixin;
import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.platform.Window;
import dev.veltraph.client.observation.nativeinput.NativeSdlInputLineage;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
@Mixin(Minecraft.class)
public abstract class NativeSdlInstalledInputMixin implements NativeSdlInputLineage.InstalledCarrier {
 @Shadow @Final private SDLEventHandler sdlEventHandler;
 @Shadow @Final private Window window;
 public SDLEventHandler veltraph$originalSdlHandler(){return sdlEventHandler;}
 public Window veltraph$originalSdlWindow(){return window;}
}
