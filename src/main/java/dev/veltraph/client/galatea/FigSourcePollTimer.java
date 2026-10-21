package dev.veltraph.client.galatea;

/** Exact readable long-range timer polling, with randomness supplied by caller.
 * Construction leaves timestamp and interval0; a ready poll resets before returning true.
 */
public final class FigSourcePollTimer {
    public interface RandomSource {
        long nextLong(long lowInclusive,long highExclusive);
        double nextDouble();
        double nextDouble(double lowInclusive,double highExclusive);
    }
    public record Distraction(double chance,double multiplierHighExclusive) {
        public Distraction {
            if(!Double.isFinite(chance) || !Double.isFinite(multiplierHighExclusive) || chance<.005 || chance>=.02 || multiplierHighExclusive<2 || multiplierHighExclusive>=2.5)
                throw new IllegalArgumentException("Source static distraction draw ranges required");
        }
    }
    private final long low,high;
    private final boolean allowDistraction;
    private final RandomSource random;
    private final Distraction distraction;
    private long timestamp,currentInterval;
    private boolean distracted;
    public FigSourcePollTimer(long low,long high,boolean allowDistraction,RandomSource random,Distraction distraction) {
        if(high<=low)throw new IllegalArgumentException("Source timer random range required");
        this.low=low;this.high=high;this.allowDistraction=allowDistraction;
        this.random=java.util.Objects.requireNonNull(random);this.distraction=java.util.Objects.requireNonNull(distraction);
    }
    public void reset(long now){timestamp=now;currentInterval=random.nextLong(low,high);}
    public boolean poll(long now) {
        if(now-timestamp<currentInterval)return false;
        reset(now);
        distracted=allowDistraction && random.nextDouble()<distraction.chance();
        if(distracted)currentInterval*=(long)random.nextDouble(1.0,distraction.multiplierHighExclusive());
        return true;
    }
    public long timestamp(){return timestamp;}
    public long currentInterval(){return currentInterval;}
    public boolean distracted(){return distracted;}
}
