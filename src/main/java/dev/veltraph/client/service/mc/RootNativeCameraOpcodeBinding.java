package dev.veltraph.client.service.mc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/** Exact transformed original camera opcode/body mapping, emitted after deferred transformation. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RootNativeCameraOpcodeBinding {
    String kind();String original();String sourceName();String sourceDescriptor();
}
