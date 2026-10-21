package dev.veltraph.client.scheduling;
/** Explicit engine-preservation contract implemented per family, never generic disable/re-enable. */
public interface ScheduledPausePort {
    /** Optional due-policy stopping only; full stationary readiness still gates pause/transport. */
    default boolean supportsDueRestPreparation(){return false;}
    default boolean prepareForDueRest(Object cause){return false;}
    default void cancelDueRestPreparation(Object cause){}
    boolean pause(Object ticket);
    boolean owns(Object ticket);
    /** Rebind/flush this family's transient native references after the matched new-world join. */
    boolean rebindAfterOwnedArrival(Object ticket);
    boolean resume(Object ticket);
    void stop(Object ticket);
}
