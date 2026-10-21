package dev.veltraph.client.fishing.worm;

import java.util.random.RandomGenerator;

/** Readable stable helper: sampled count OR elapsed minutes, with no kill acknowledgment inference. */
public final class WormAdmission {
    public record Range(double minimum,double maximum) {
        public Range {
            if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<=0||maximum<minimum)
                throw new IllegalArgumentException("Worm range must have positive finite min:max");
        }
        public static Range parse(String text) {
            if(text==null)throw new IllegalArgumentException("Missing worm range");
            String[] parts=text.split(":",-1);
            if(parts.length!=2)throw new IllegalArgumentException("Expected worm min:max range");
            try{return new Range(Double.parseDouble(parts[0].trim()),Double.parseDouble(parts[1].trim()));}
            catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid worm range",invalid);}
        }
        double sample(RandomGenerator random){return minimum==maximum?minimum:random.nextDouble(minimum,maximum);}
    }
    public record Sample(int entityThreshold,double minutes,long resetAt) {
        public Sample {
            if(entityThreshold<1||!Double.isFinite(minutes)||minutes<=0)
                throw new IllegalArgumentException("Invalid sampled worm thresholds");
        }
        public double progress(int entities,long now) {
            if(entities<0)throw new IllegalArgumentException("Negative worm count");
            // Preserve the reference order: elapsed milliseconds / 1000 / 60 / sampled minutes.
            return Math.min(1,Math.max((double)entities/entityThreshold,((double)now-resetAt)/1000/60/minutes));
        }
        public boolean ready(int entities,long now){return progress(entities,now)>=1;}
    }
    private Sample sample;
    public Sample reset(Range entities,Range minutes,long now,RandomGenerator random) {
        double count=entities.sample(random);
        if(count>Integer.MAX_VALUE-.5)throw new IllegalArgumentException("Worm entity threshold too large");
        int rounded=(int)Math.floor(count+.5); // kotlin MathKt.roundToInt for positive finite values.
        if(rounded<1)throw new IllegalArgumentException("Worm entity threshold rounds to zero");
        sample=new Sample(rounded,minutes.sample(random),now);return sample;
    }
    public Sample sample(){return sample;}
    public boolean ready(int entities,long now){return sample!=null&&sample.ready(entities,now);}
    /** Wrapper source compares native entity positions using full 3D Euclidean distance <15. */
    public static boolean nearby(double dx,double dy,double dz) {
        return Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz)&&Math.sqrt(dx*dx+dy*dy+dz*dz)<15;
    }
}
