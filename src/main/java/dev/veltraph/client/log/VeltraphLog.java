package dev.veltraph.client.log;

import java.util.function.Supplier;

/**
 * Static facade over the active {@link VeltraphLogger} so call sites across the codebase (including the
 * pure engine/geometry/aim classes that take no Minecraft dependency) can log tersely without threading
 * a logger through every constructor. The bootstrap installs the real logger via {@link #set}; until
 * then (and in unit tests) the default is a disabled no-op logger.
 *
 * <p>Tag convention for major call sites is {@code "Class.method"} (e.g.
 * {@code "SShapeRowEngine.tickFarmingSideways"}); module-level logs pass the module name as the tag.
 */
public final class VeltraphLog {

    private static volatile VeltraphLogger instance = VeltraphLogger.disabled();

    private VeltraphLog() {
    }

    public static void set(VeltraphLogger logger) {
        instance = logger != null ? logger : VeltraphLogger.disabled();
    }

    public static VeltraphLogger get() {
        return instance;
    }

    public static boolean shouldLog(DebugLogMode level) {
        return instance.shouldLog(level);
    }

    public static void basic(String tag, String message) {
        instance.basic(tag, message);
    }

    public static void details(String tag, String message) {
        instance.details(tag, message);
    }

    public static void verbose(String tag, String message) {
        instance.verbose(tag, message);
    }

    public static void basic(String tag, Supplier<String> message) {
        instance.basic(tag, message);
    }

    public static void details(String tag, Supplier<String> message) {
        instance.details(tag, message);
    }

    public static void verbose(String tag, Supplier<String> message) {
        instance.verbose(tag, message);
    }
}
