package dev.veltraph.client.observation.nativeinput;
import java.lang.annotation.*;
/** Exact original caller MethodNode resolved structurally when its own class final transformation completes. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface NativeMouseOriginalInvokerBinding {String kind();}
