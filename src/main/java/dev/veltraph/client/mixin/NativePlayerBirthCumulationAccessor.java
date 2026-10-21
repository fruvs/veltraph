package dev.veltraph.client.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Original native field only; this accessor supplies no permission or source enrollment. */
@Mixin(value = ByteToMessageDecoder.class, remap = false)
public interface NativePlayerBirthCumulationAccessor {
    @Accessor("cumulation") ByteBuf veltraph$birthWireCumulation();
}
