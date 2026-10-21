package dev.veltraph.client.observation.nativeinput;
import java.lang.annotation.*;
/** Final transformed original invocation route, never a caller's source label. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface NativeMouseInvocationBinding {
 String kind();String sourceName();String sourceDescriptor();
}
