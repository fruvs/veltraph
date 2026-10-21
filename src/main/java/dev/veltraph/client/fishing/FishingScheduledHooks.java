package dev.veltraph.client.fishing;
/** Mandatory native family hooks. Root proves owned arrival/physical gear and pose before rebind. */
public interface FishingScheduledHooks {
    boolean ready();
    boolean pause(Object ticket);
    boolean rebind(Object ticket);
    boolean resume(Object ticket);
    void cancel(Object ticket);
}
