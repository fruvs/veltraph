package dev.veltraph.client.combat.ghost;

import java.util.Optional;

/** Client-thread immutable value reuse. Calling this never establishes current ownership:
 * the real parent invokes it only after its entire unchanged native guard body succeeds.
 * Arguments remain freshly read each call and use Stamp.same's exact identity/value rules. */
final class GhostSourceStampValues {
    private GhostSourceOwnedParentArea.Stamp last;
    private Optional<GhostSourceOwnedParentArea.Stamp> optional;
    Optional<GhostSourceOwnedParentArea.Stamp> observed(Object parent,long generation,long settingsRevision,
            Object player,Object world,Object connection){
        var prior=last;
        if(prior!=null&&prior.parent()==parent&&prior.generation()==generation&&prior.settingsRevision()==settingsRevision
                &&prior.player()==player&&prior.world()==world&&prior.connection()==connection)return optional;
        // Preserve the real Stamp constructor's validation and exception; publish nothing on failure.
        var actual=new GhostSourceOwnedParentArea.Stamp(parent,generation,settingsRevision,player,world,connection);
        var value=Optional.of(actual);last=actual;optional=value;return value;
    }
    /** Existing parent invalidation releases references; no old context is retained across disable/PWC changes. */
    void clear(){last=null;optional=null;}
}
