package dev.veltraph.client.scheduling;

import java.util.random.RandomGenerator;

/** Source range syntax min:max with half-open SplittableRandom sampling. */
public record SourceScheduleRange(double minimum,double maximum){
    public SourceScheduleRange {
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<0||maximum<=minimum)
            throw new IllegalArgumentException("Schedule range must be finite nonnegative min:max with max>min");
    }
    public static SourceScheduleRange parse(String value){
        if(value==null)throw new IllegalArgumentException("Schedule range is missing");
        String[] parts=value.split(":",-1);if(parts.length!=2)throw new IllegalArgumentException("Expected schedule min:max");
        try{return new SourceScheduleRange(Double.parseDouble(parts[0].trim()),Double.parseDouble(parts[1].trim()));}
        catch(NumberFormatException error){throw new IllegalArgumentException("Invalid schedule range",error);}
    }
    public double sample(RandomGenerator random){return random.nextDouble(minimum,maximum);}
    public long cooldownMillis(RandomGenerator random){double millis=sample(random)*3_600_000;if(millis>Long.MAX_VALUE)throw new IllegalArgumentException("Rest cooldown is too large");return (long)millis;}
    /** Source duration is max(1,(long)sampledMinutes), not fractional-minute milliseconds. */
    public long restMillis(RandomGenerator random){double minutes=sample(random);if(minutes>Long.MAX_VALUE/60_000)throw new IllegalArgumentException("Rest duration is too large");return Math.max(1,(long)minutes)*60_000;}
}
