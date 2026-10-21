package dev.veltraph.client.combat.sven;

import java.util.Optional;

/** Optional shared quest policy: inject only after its scoreboard/caller contract is known. */
public interface SvenParentPolicy {
    Optional<String> readiness();
    /** Source: capability + AUTO_SLAYER + scoreboard predicate. No Batphone assumption here. */
    boolean pickQuestBeforePreparation();
    void beginQuest(Runnable prepare,SvenIngressPort.Ticket ticket);
    void tick();
    void message(String line);
    void cancel();
}
