package dev.veltraph.client.navigation;

/** Keeps velocity continuous when two steering behaviors hand camera ownership to one another. */
public final class AngularRateLimiter {
    private float velocity;
    public void reset() { velocity=0; }
    public float apply(float current,float requested,double speed) {
        double cap=6*Math.max(.25,Math.min(2,Double.isFinite(speed)?speed:1));
        float wanted=(float)Math.max(-cap,Math.min(cap,WalkController.wrap(requested-current)));
        velocity+=(float)Math.max(-1.2,Math.min(1.2,wanted-velocity));
        return current+velocity;
    }
}
