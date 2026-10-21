package dev.veltraph.client.movement.device;

/** One actual issuer-owned start lifecycle. Model identity is not native cleanup authority. */
public final class DeviceStartCustodyState {
    private enum Phase { OPEN, INTERRUPTED, AIMING, CLOSED }
    private final Object owner;private Phase phase=Phase.OPEN;
    private boolean selectionReturned,cleanupClaimed,cleanupInFlight;
    public DeviceStartCustodyState(Object owner){this.owner=owner;}
    public boolean open(Object exact){return owner!=null&&owner==exact&&phase==Phase.OPEN;}
    public boolean selectionReturned(Object exact){if(!open(exact))return false;selectionReturned=true;return true;}
    public boolean interrupt(Object exact){if(!open(exact))return false;phase=Phase.INTERRUPTED;return true;}
    public boolean beginCleanup(Object exact){if(owner!=exact||phase!=Phase.INTERRUPTED||cleanupClaimed)return false;cleanupClaimed=true;cleanupInFlight=true;return true;}
    public void endCleanup(Object exact){if(owner==exact)cleanupInFlight=false;}
    public boolean cleanupInFlight(Object exact){return owner==exact&&phase==Phase.INTERRUPTED&&cleanupInFlight;}
    public boolean finish(Object exact){if(!open(exact)||!selectionReturned)return false;phase=Phase.AIMING;return true;}
    public void close(Object exact){if(owner==exact){phase=Phase.CLOSED;cleanupInFlight=false;}}
}
