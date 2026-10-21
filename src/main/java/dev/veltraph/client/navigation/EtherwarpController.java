package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;

/** One use attempt followed by actual server-position confirmation. Never changes player position. */
public final class EtherwarpController {
    public enum State { IDLE, AIMING, WAITING_TELEPORT, ARRIVED, FAILED }
    public enum Action { NONE, AIM_AND_SNEAK, USE }
    public record Input(Vec3 position,boolean available,boolean toolReady,boolean sneaking,
                        EtherwarpGate.Observation hit,long now) {}
    private State state=State.IDLE; private BlockPos target;private Direction face;private Vec3 landing;
    private long started,usedAt;private int stable;private String reason="Idle";
    public void start(BlockPos target,Direction face,Vec3 landing,long now) {
        this.target=target;this.face=face;this.landing=landing;started=now;usedAt=0;stable=0;state=State.AIMING;reason="Aiming";
    }
    public Action tick(Input in,int stableTicks,long timeoutMs) {
        if(state!=State.AIMING&&state!=State.WAITING_TELEPORT) return Action.NONE;
        if(!in.available()) return fail("World unavailable");
        if(in.position().distanceTo(landing)<.9) { state=State.ARRIVED;reason="Server position reached destination";return Action.NONE; }
        if(!in.toolReady()) return fail("Etherwarp tool unavailable");
        long timeout=Math.max(500,Math.min(30000,timeoutMs));
        if(state==State.WAITING_TELEPORT) {
            if(in.now()-usedAt>timeout) return fail("Server did not confirm etherwarp");
            return Action.NONE;
        }
        if(in.now()-started>timeout) return fail("Could not stabilize etherwarp target: "+reason);
        reason=EtherwarpGate.rejection(target,face,in.hit(),target.y(),target.y()+1d);
        if(!reason.isEmpty()||!in.sneaking()) { stable=0;return Action.AIM_AND_SNEAK; }
        if(++stable<Math.max(1,Math.min(20,stableTicks))) { reason="Stabilizing";return Action.AIM_AND_SNEAK; }
        state=State.WAITING_TELEPORT;usedAt=in.now();reason="Waiting for server teleport";return Action.USE;
    }
    private Action fail(String why) { state=State.FAILED;reason=why;return Action.NONE; }
    public void reset() { state=State.IDLE;stable=0;reason="Stopped"; }
    public State state() { return state; }
    public String status() { return reason; }
}
