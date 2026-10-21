package dev.veltraph.client.observation.unexpected;
/** Exact client-frame fence, separate from persistent settings and later manual activation. */
public final class UnexpectedStopFrame {
    private long frame;
    private long stopped=Long.MIN_VALUE;
    public void begin() { frame++; }
    public void stop() { stopped=frame; }
    public boolean stopped() { return stopped==frame; }
}
