package dev.veltraph.client.mixin;
import com.mojang.blaze3d.platform.Window;
import dev.veltraph.client.observation.nativeinput.NativeSdlInputLineage;
import org.spongepowered.asm.mixin.*;
@Mixin(Window.class)
public abstract class NativeSdlWindowHandleMixin implements NativeSdlInputLineage.WindowCarrier {
 @Shadow @Final private long handle;
 public long veltraph$originalSdlHandle(){return handle;}
}
