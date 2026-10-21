package dev.veltraph.client.hydra;

/**
 * Pure rising-edge detector for the local BARRIER signal: a solid obstacle that <b>newly appears</b> in the
 * player's forward path while they are eligible (scripting + roughly stationary). The Minecraft adapter
 * supplies, each tick, whether the block(s) directly in front of the player are solid now and whether the
 * player is eligible; this class handles only the edge detection + re-arm so it is unit-testable on a plain
 * JDK with no Minecraft.
 *
 * <p>Semantics: fire once on a {@code not-blocked → blocked} transition while eligible (a barrier was just
 * placed in front of a held position — the client-visible analogue of the original {@code BARRIER}
 * situation, which in the original came from the C2-fed detection pool, EXCLUDE_NETWORK). Re-arm when the
 * path clears. Ineligible ticks (moving, not scripting, no world) reset the baseline so normal walking into
 * existing walls cannot trip it.
 */
public final class BarrierWatcher {

    /** Opaque native scope plus exact forward feet/head cell and collision identity. */
    public record Cell(int x,int y,int z){}
    public record Sample(Object player,Object world,Object connection,Object owner,long generation,Cell feet,Cell head,
                         Object feetState,Object headState,Object feetShape,Object headShape,boolean loaded,boolean blocked){}
    private Sample previous;
    public Sample previousSample(){return previous;}
    public boolean observe(Sample current,boolean eligible){
        if(!eligible||current==null||!current.loaded()){reset();return false;}
        boolean same=previous!=null&&previous.player()==current.player()&&previous.world()==current.world()
                &&previous.connection()==current.connection()&&previous.owner()==current.owner()&&previous.generation()==current.generation()
                &&previous.feet().equals(current.feet())&&previous.head().equals(current.head());
        if(!same){reset();previous=current;return false;}
        boolean actualChanged=!java.util.Objects.equals(previous.feetState(),current.feetState())||!java.util.Objects.equals(previous.headState(),current.headState())
                ||!java.util.Objects.equals(previous.feetShape(),current.feetShape())||!java.util.Objects.equals(previous.headShape(),current.headShape());
        boolean fire=!previous.blocked()&&current.blocked()&&actualChanged&&!latched;
        if(!current.blocked())latched=false;else if(fire)latched=true;
        previous=current;return fire;
    }
    private boolean prevBlocked;
    private boolean hadBaseline;
    private boolean latched;

    /**
     * Observe one tick.
     *
     * @param forwardBlocked whether a solid block occupies the forward feet/body path right now
     * @param eligible       whether the player is scripting and roughly stationary this tick
     * @return true exactly on the tick a fresh barrier appears (rising edge, not yet latched)
     */
    public boolean observe(boolean forwardBlocked, boolean eligible) {
        if (!eligible) {
            // Not eligible: drop the baseline so re-entering eligibility doesn't fabricate an edge, and
            // clear the latch so a real barrier after this is detectable.
            reset();
            return false;
        }
        boolean fire = false;
        if (!forwardBlocked) {
            latched = false; // path clear → re-arm
        } else if (hadBaseline && !prevBlocked && !latched) {
            // not-blocked last tick → blocked this tick, while eligible: a barrier just appeared.
            latched = true;
            fire = true;
        }
        prevBlocked = forwardBlocked;
        hadBaseline = true;
        return fire;
    }

    /** Reset transient state (called on world/dimension/script stop). */
    public void reset() {
        previous=null;
        prevBlocked = false;
        hadBaseline = false;
        latched = false;
    }
}
