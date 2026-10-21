package dev.veltraph.client.navigation;

/** Refusal-only time bounds; these values never grant source, inventory or native authority. */
public final class ReturnDeviceDeadlines {
    private ReturnDeviceDeadlines() {}
    public static long originalBound(long started,long sharedDeadline,long originalReturnDeadline) {
        if(started<0||sharedDeadline<=started||originalReturnDeadline<=started||started>Long.MAX_VALUE-5000)return -1;
        long bound=Math.min(started+5000,Math.min(sharedDeadline,originalReturnDeadline));
        // The genuine executor requires at least 500ms. Never extend a shorter original window.
        return bound-started>=500?bound:-1;
    }
    /** The actual native-source leaf uses fresh wall time only to refuse, never to renew its bounds. */
    public static boolean permitsOriginalWindow(long actual,long started,long previous,long sharedFloor,long deadline) {
        return started>=0&&deadline>started&&actual>=started&&actual>=previous&&actual>=sharedFloor&&actual<deadline;
    }
}
