package dev.veltraph.client.observation.nativeinput;
import java.lang.annotation.*;
/** Emitted only by the final transformed original-opcode binder, never by a runtime event label. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface NativeSdlInputOpcodeBinding {String kind();String sourceName();String sourceDescriptor();}
