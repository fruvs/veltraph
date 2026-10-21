package dev.veltraph.client.service.mc;

import java.lang.annotation.*;

/** Read-only metadata issued by the final exact original-instruction transform. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RootNativeSdlOpcodeBinding {
    String kind();
    String sourceName();
    String sourceDescriptor();
}
