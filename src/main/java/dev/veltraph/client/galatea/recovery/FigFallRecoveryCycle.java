package dev.veltraph.client.galatea.recovery;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Source containing-area/filter/random-goal lifecycle, with bounded local outcome gates. */
public final class FigFallRecoveryCycle {
    public enum State { IDLE, TRAVELLING, RECOVERED, FAILED, CANCELLED }
    private State state=State.IDLE;
    private BlockPos goal;
    private long started;
    private String status="Idle";
    public boolean start(Vec3 position,Predicate<BlockPos> reachable,RandomGenerator random,long now) {
        if(state!=State.IDLE)return false;
        var area=FigFallAtlas.containing(position);
        if(area.isEmpty()){fail("Outside source Fig recovery areas");return false;}
        var goals=area.get().goals().stream().filter(reachable).toList();
        if(goals.isEmpty()){fail("No reachable source Fig recovery goal");return false;}
        goal=goals.get(random.nextInt(goals.size()));started=now;state=State.TRAVELLING;status="Recovering to "+goal;return true;
    }
    public void observe(boolean owned,boolean goalLoaded,boolean arrived,boolean travelFailed,long now,long timeout) {
        if(state!=State.TRAVELLING)return;
        if(!owned){fail("Recovery ownership changed");return;}
        if(!goalLoaded){fail("Recovery goal unloaded");return;}
        if(travelFailed){fail("Recovery travel failed");return;}
        if(now<started||now-started>Math.max(1000,timeout)){fail("Recovery timed out");return;}
        if(arrived){state=State.RECOVERED;status="Source recovery goal reached";}
    }
    public void fail(String why){state=State.FAILED;status=why;}
    public void cancel(){if(state==State.TRAVELLING){state=State.CANCELLED;status="Recovery cancelled";}}
    public void reset(){state=State.IDLE;goal=null;started=0;status="Idle";}
    public State state(){return state;}
    public BlockPos goal(){return goal;}
    public String status(){return status;}
}
