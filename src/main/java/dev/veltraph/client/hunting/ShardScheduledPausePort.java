package dev.veltraph.client.hunting;

import dev.veltraph.client.scheduling.ScheduledPausePort;
import java.util.Objects;
import java.util.function.Predicate;

/** Exact HUNTING engine capability; Root's matched native arrival proof must precede rebind/resume. */
public final class ShardScheduledPausePort implements ScheduledPausePort {
    private final ShardWorkflowModule owner;private final Predicate<Object> ownedArrivalProof;
    /** McScriptScheduler calls rebind only after its complete owned arrival proof. */
    public ShardScheduledPausePort(ShardWorkflowModule owner){this(owner,ticket->true);}
    /** Optional explicit arrival-authority predicate for other authorized coordinators/native fixtures. */
    public ShardScheduledPausePort(ShardWorkflowModule owner,Predicate<Object> ownedArrivalProof){this.owner=Objects.requireNonNull(owner);this.ownedArrivalProof=Objects.requireNonNull(ownedArrivalProof);}
    public boolean pause(Object ticket){return owner.suspendForScheduledRest(ticket);}
    public boolean owns(Object ticket){return owner.scheduledPauseOwnedBy(ticket);}
    public boolean rebindAfterOwnedArrival(Object ticket){return owns(ticket)&&ownedArrivalProof.test(ticket)&&owner.rebindAfterScheduledArrival(ticket);}
    public boolean resume(Object ticket){return owns(ticket)&&ownedArrivalProof.test(ticket)&&owner.resumeAfterScheduledRest(ticket);}
    public void stop(Object ticket){if(owns(ticket))owner.setEnabled(false);}
}
