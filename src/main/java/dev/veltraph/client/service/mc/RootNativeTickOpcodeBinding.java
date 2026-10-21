package dev.veltraph.client.service.mc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/** Immutable metadata emitted only after exact retained original opcode bodies are resolved. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RootNativeTickOpcodeBinding {
    String kind();
    String original();
    String sourceName();
    String sourceDescriptor();
}
