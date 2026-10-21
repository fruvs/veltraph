package dev.veltraph.client.foraging.scheduling;

import java.util.Optional;

/** Explicit quiescent family boundary; no generic disable/re-enable or arbitrary mid-action pause. */
public interface ForagingRestOwner {
    Optional<String> scheduledRestReadiness();
    boolean pauseForScheduledRest(Object ticket);
    boolean scheduledRestOwnedBy(Object ticket);
    boolean rebindForScheduledRest(Object ticket);
    boolean resumeFromScheduledRest(Object ticket);
}
