package dev.veltraph.client.combat.ghost;

/** Possession of an already produced global fallback during scheduled SEARCH.
 * This is only registration identity, never a permission/geometry/cache observation.
 * The native cursor and producer independently retain all original fresh guards. */
final class GhostSourceRetryMovement<P,C> {
    record Retained<P,C>(P producer,C cursor) {}
    private Retained<P,C> retained;
    boolean active(){return retained!=null;}
    Retained<P,C> retained(){return retained;}
    boolean registered(P actual){return retained!=null&&retained.producer()==actual;}
    void retain(P actualProducer,C actualCursor){
        if(retained!=null)throw new IllegalStateException("Retained fallback must finish/revoke before replacement");
        retained=new Retained<>(java.util.Objects.requireNonNull(actualProducer),java.util.Objects.requireNonNull(actualCursor));
    }
    Retained<P,C> clear(){var actual=retained;retained=null;return actual;}
}
