package dev.veltraph.client.combat.ghost;
/** Pure continuity model; positive native receipt is minted only by the owned wrapper's actual close observation. */
public final class GhostSourceJumpBoostContinuation {
    public enum State { ACTIVE, CONSUMED, REVOKED }
    public record Scope(Object parent,Object child,long generation,long settingsRevision,Object player,Object world,Object connection,long cameraEpoch){
        public Scope {java.util.Objects.requireNonNull(parent);java.util.Objects.requireNonNull(child);java.util.Objects.requireNonNull(player);java.util.Objects.requireNonNull(world);java.util.Objects.requireNonNull(connection);}
        public boolean same(Scope other){return other!=null&&parent==other.parent&&child==other.child&&generation==other.generation&&settingsRevision==other.settingsRevision
            &&player==other.player&&world==other.world&&connection==other.connection&&cameraEpoch==other.cameraEpoch;}
    }
    private final Scope issued;private State state=State.ACTIVE;
    GhostSourceJumpBoostContinuation(Scope actual){issued=java.util.Objects.requireNonNull(actual);}
    public Scope issued(){return issued;}public State state(){return state;}
    boolean consumeActualClose(Scope actual,boolean genuineNativeClose){if(state!=State.ACTIVE)return false;
        if(!issued.same(actual)){state=State.REVOKED;return false;}if(!genuineNativeClose)return false;state=State.CONSUMED;return true;}
    void revoke(){state=State.REVOKED;}
}
