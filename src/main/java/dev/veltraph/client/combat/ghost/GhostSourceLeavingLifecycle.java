package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.ArrayList;
import java.util.function.IntUnaryOperator;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

/** Readable distinct sixth-failure child lifecycle. Native actions are emitted only by its actual parent adapter. */
public final class GhostSourceLeavingLifecycle {
    public enum Policy { LEAVE_INSTANTLY, ETHERWARP_OUT }
    public enum State { NEW, CAPTURING_PATH, WALKING, ETHERWARPING, FINISH_PARENT, REVOKED }
    public enum Action { NONE, SELECT_SLOT_EIGHT, GATED_LEFT_CLICK, SNEAK, GATED_JUMP, AIM_ETHER, ENABLE_PATH, FINISH_PARENT }
    public record Tick(Action action,boolean enablePath) {}
    public static final int SOURCE_VISIT_LIMIT=2000;
    private State state=State.NEW;private long toolAt=Long.MIN_VALUE;private int events,jitter;private Cell chosen;
    public State state(){return state;}public Cell chosen(){return chosen;}
    /** Original sampled absolute deadline; passive read never checks/resamples it. */
    public long cachedToolAtTick(){return toolAt;}
    /** Exact X outer/Z inner121 cells, cell OR above source standability; original candidate retained even above qualifies. */
    public static List<Cell> admitted(Cell sourceFoot,java.util.function.Predicate<Cell> standable){var result=new ArrayList<Cell>();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){var c=new Cell(sourceFoot.x()+x,sourceFoot.y(),sourceFoot.z()+z);if(standable.test(c)||standable.test(new Cell(c.x(),c.y()+1,c.z())))result.add(c);}return List.copyOf(result);}
    public Cell begin(List<Cell> actualCandidates,IntUnaryOperator randomIndex){if(state!=State.NEW)throw new IllegalStateException("Leaving child enters once");if(actualCandidates.isEmpty()){state=State.FINISH_PARENT;return null;}
        int index=randomIndex.applyAsInt(actualCandidates.size());if(index<0||index>=actualCandidates.size())throw new IllegalArgumentException("Source random admitted cell index");chosen=actualCandidates.get(index);state=State.CAPTURING_PATH;return chosen;}
    /** Source computes2000 path BEFORE policy/tool check. No route substitution when path unavailable. */
    public Tick captured(boolean actualPathFound,Policy actualPolicy,boolean actualEtherItem,long tick,IntUnaryOperator random){
        if(state!=State.CAPTURING_PATH)throw new IllegalStateException("Actual leaving path completion required");
        if(!actualPathFound||actualPolicy==Policy.LEAVE_INSTANTLY||!actualEtherItem){state=State.FINISH_PARENT;return new Tick(Action.FINISH_PARENT,false);}
        events=0;jitter=range(random,5);toolAt=tick+range(random,20)+5;state=State.WALKING;return new Tick(Action.NONE,false);
    }
    public boolean toolDue(long tick){return (state==State.WALKING||state==State.ETHERWARPING)&&toolAt!=Long.MIN_VALUE&&tick>=toolAt;}
    public void toolSelected(){toolAt=Long.MIN_VALUE;}
    public void pathSucceeded(){if(state!=State.WALKING)throw new IllegalStateException("Actual owned path success required");events=0;state=State.ETHERWARPING;}
    public void pathFailed(){if(state==State.WALKING||state==State.ETHERWARPING)state=State.FINISH_PARENT;}
    /** Source cancels queued-job event; path-active branch returns before event counter. */
    public Tick event(boolean queuedNativeJob,boolean pathActive,boolean clearEtherAim,boolean distanceBelowTen,int actualSlot,IntUnaryOperator random,java.util.function.DoubleSupplier randomDouble,java.util.function.BooleanSupplier randomBoolean){
        if(state!=State.WALKING&&state!=State.ETHERWARPING)return new Tick(state==State.FINISH_PARENT?Action.FINISH_PARENT:Action.NONE,false);
        if(queuedNativeJob)return new Tick(Action.NONE,false);
        if(pathActive){if(!clearEtherAim)return new Tick(Action.NONE,false);return new Tick(Action.AIM_ETHER,distanceBelowTen);}
        ++events;
        if(events==9+jitter){boolean select=randomDouble.getAsDouble()<.2;jitter=range(random,8);return new Tick(select?Action.SELECT_SLOT_EIGHT:Action.NONE,false);}
        if(events==15+jitter){boolean toggle=randomBoolean.getAsBoolean()&&actualSlot!=8;jitter=range(random,5);return new Tick(toggle?Action.GATED_LEFT_CLICK:Action.NONE,false);}
        if(events==27+jitter)return new Tick(randomDouble.getAsDouble()<.5?Action.SNEAK:Action.NONE,false);
        if(events<38+jitter)return new Tick(randomDouble.getAsDouble()<.2?Action.GATED_JUMP:Action.NONE,false);
        state=State.FINISH_PARENT;return new Tick(Action.FINISH_PARENT,false);
    }
    public void revoke(){state=State.REVOKED;toolAt=Long.MIN_VALUE;}
    private static int range(IntUnaryOperator random,int bound){int n=random.applyAsInt(bound);if(n<0||n>=bound)throw new IllegalArgumentException("Actual source random bound");return n;}
}
