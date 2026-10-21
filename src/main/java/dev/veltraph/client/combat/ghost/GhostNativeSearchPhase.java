package dev.veltraph.client.combat.ghost;

import java.util.Objects;

/** Actual owned source-search child identity. Dormant until a mapped parent/search handler publishes selection. */
public final class GhostNativeSearchPhase {
    public enum State { UNBOUND, SEARCHING }
    public final class Receipt {
        private final long epoch;private final GhostSourceOwnedParentArea.Stamp stamp;private final Object target;
        private Receipt(long epoch,GhostSourceOwnedParentArea.Stamp stamp,Object target){this.epoch=epoch;this.stamp=stamp;this.target=target;}
        public Object childIdentity(){return GhostNativeSearchPhase.this;}
        public boolean current(GhostSourceOwnedParentArea.Stamp actual,Object actualSelectedTarget){
            return state==State.SEARCHING&&epoch==GhostNativeSearchPhase.this.epoch&&target==selectedTarget
                    &&target==actualSelectedTarget&&stamp.same(actual)&&stamp.same(parentStamp);
        }
    }
    private final Object parent;
    private State state=State.UNBOUND;private long epoch;
    private Object selectedTarget;private GhostSourceOwnedParentArea.Stamp parentStamp;
    public GhostNativeSearchPhase(Object parent){this.parent=Objects.requireNonNull(parent);}
    public State state(){return state;}
    /** Only the same-package mapped source handler may publish its observed parent selection; starts no actions. */
    void observeSelected(GhostSourceOwnedParentArea.Stamp actual,Object actualSelectedTarget){
        invalidate();
        if(actual==null||actual.parent()!=parent||actualSelectedTarget==null)throw new IllegalArgumentException("Actual parent/source selection required");
        parentStamp=actual;selectedTarget=actualSelectedTarget;state=State.SEARCHING;
    }
    public Receipt capture(GhostSourceOwnedParentArea.Stamp actual,Object actualSelectedTarget){
        if(state!=State.SEARCHING||parentStamp==null||!parentStamp.same(actual)||selectedTarget!=actualSelectedTarget)return null;
        return new Receipt(epoch,parentStamp,selectedTarget);
    }
    public void invalidate(){epoch++;state=State.UNBOUND;parentStamp=null;selectedTarget=null;}
}
