package dev.veltraph.client.inventory.selling;

import java.util.Objects;

/** Explicit child outcome, cleanup and return must all acknowledge before the preserved engine resumes. */
public final class FarmAutoSellRuntime {
    public enum State { IDLE, SELLING, CLEANUP, RETURN, COMPLETE, FAILED, CANCELLED }
    public enum Progress { ACTIVE, COMPLETE, FAILED }
    public interface Actions {
        boolean acquire();boolean valid();boolean startChild();Progress child();Progress cleanup();
        boolean beginReturn();Progress returned();boolean resume();void cancel();
    }
    private final Actions actions;private State state=State.IDLE;private long deadline,last;private String reason="Idle";
    public FarmAutoSellRuntime(Actions actions){this.actions=Objects.requireNonNull(actions);}
    public boolean start(long now,long timeout){
        if(active())throw new IllegalStateException("Auto-sell maintenance already active");
        if(timeout<=0)throw new IllegalArgumentException("Bounded timeout required");last=now;deadline=Math.addExact(now,timeout);
        if(!actions.acquire()||!actions.valid())return fail("Exact committed farm lease unavailable");
        if(!actions.startChild())return fail("Selling child readiness/activation refused");
        state=State.SELLING;reason="Waiting for explicit sale/discard acknowledgment";return true;
    }
    public void tick(long now){
        if(!active())return;if(now<last||now>=deadline||!actions.valid()){fail("Auto-sell ownership/settings/health/deadline changed");return;}last=now;
        switch(state){
            case SELLING->{var p=actions.child();if(p==Progress.FAILED)fail("Child did not acknowledge a complete transaction");else if(p==Progress.COMPLETE){state=State.CLEANUP;reason="Checking child cleanup and native closed inventory";}}
            case CLEANUP->{var p=actions.cleanup();if(p==Progress.FAILED)fail("Child cleanup ownership changed");else if(p==Progress.COMPLETE){
                if(!actions.beginReturn()){fail("Captured gear/pose return refused");return;}state=State.RETURN;reason="Returning to exact captured farm pose";}}
            case RETURN->{var p=actions.returned();if(p==Progress.FAILED)fail("Exact saved farm return failed");else if(p==Progress.COMPLETE){
                if(!actions.resume()){fail("Final same-engine restoration proof refused");return;}state=State.COMPLETE;reason="Acknowledged sale, cleanup, gear/pose restoration and same-engine resume";}}
            default->{}
        }
    }
    private boolean fail(String why){state=State.FAILED;reason=why;actions.cancel();return false;}
    public void cancel(){if(!active())return;state=State.CANCELLED;reason="Stopped; no farm resume";actions.cancel();}
    public boolean active(){return state==State.SELLING||state==State.CLEANUP||state==State.RETURN;}
    public State state(){return state;}public String status(){return state+": "+reason;}
}
