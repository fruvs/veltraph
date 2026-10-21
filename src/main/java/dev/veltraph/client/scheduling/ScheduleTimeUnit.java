package dev.veltraph.client.scheduling;

/** The four explicit TimeUnit branches of the source stop timer. */
public enum ScheduleTimeUnit {
    SECONDS(1),MINUTES(60),HOURS(3600),DAYS(86400);
    private final long seconds;ScheduleTimeUnit(long seconds){this.seconds=seconds;}
    public long seconds(){return seconds;}
}
