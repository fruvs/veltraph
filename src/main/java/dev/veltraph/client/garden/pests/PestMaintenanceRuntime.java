package dev.veltraph.client.garden.pests;

import java.util.Objects;

/** One complete leased loaded-plot batch, followed by named gear and exact farm return. */
public final class PestMaintenanceRuntime {
    public enum State { IDLE, APPLY_LOADOUT, CLEAN, RESTORE_LOADOUT, RETURN, COMPLETE, FAILED, CANCELLED }
    public enum Progress { ACTIVE, COMPLETE, FAILED }
    public record Settings(boolean swap,String target,long timeoutMs) {
        public Settings {if(timeoutMs<=0||swap&&(target==null||target.isBlank()))throw new IllegalArgumentException("Bounded maintenance and named target required");}
    }
    public interface Actions {
        boolean acquire();boolean valid();boolean apply(String name);Progress loadout(boolean restore);
        boolean clean();Progress cleaned();boolean restore();boolean beginReturn();Progress returned();boolean resume();void cancel();
    }
    private final Actions actions;private State state=State.IDLE;private Settings settings;private long deadline,last;private String reason="Idle";
    public PestMaintenanceRuntime(Actions actions){this.actions=Objects.requireNonNull(actions);}
    public boolean start(Settings settings,long now){
        if(active())throw new IllegalStateException("Pest maintenance already active");this.settings=Objects.requireNonNull(settings);last=now;deadline=Math.addExact(now,settings.timeoutMs());
        if(!actions.acquire())return fail("Exact farm maintenance lease unavailable");
        if(settings.swap()){if(!actions.apply(settings.target()))return fail("Named pest loadout request refused");state=State.APPLY_LOADOUT;}
        else if(!startCleaning())return false;reason="Leased bounded pest maintenance";return true;
    }
    private boolean startCleaning(){if(!actions.clean())return fail("Loaded pest batch refused");state=State.CLEAN;return true;}
    public void tick(long now){
        if(!active())return;if(now<last||now>=deadline||!actions.valid()){fail("Pest maintenance ownership/settings/time changed");return;}last=now;
        switch(state){
            case APPLY_LOADOUT->{var p=actions.loadout(false);if(p==Progress.FAILED)fail("Named pest loadout not acknowledged");else if(p==Progress.COMPLETE)startCleaning();}
            case CLEAN->{var p=actions.cleaned();if(p==Progress.FAILED)fail("Pest batch outcome not verified");else if(p==Progress.COMPLETE){
                if(settings.swap()){if(!actions.restore()){fail("Original named loadout restoration refused");return;}state=State.RESTORE_LOADOUT;}
                else startReturn();}}
            case RESTORE_LOADOUT->{var p=actions.loadout(true);if(p==Progress.FAILED)fail("Original named gear not restored");else if(p==Progress.COMPLETE)startReturn();}
            case RETURN->{var p=actions.returned();if(p==Progress.FAILED)fail("Exact saved farm return failed");else if(p==Progress.COMPLETE){
                if(!actions.resume()){fail("Final farm identity/gear/pose revalidation failed");return;}state=State.COMPLETE;reason="Pests removed; original farm gear and exact pose restored";}}
            default->{}
        }
    }
    private void startReturn(){if(!actions.beginReturn()){fail("Restoration proof or saved farm return refused");return;}state=State.RETURN;}
    private boolean fail(String text){state=State.FAILED;reason=text;actions.cancel();return false;}
    public void cancel(){if(!active())return;state=State.CANCELLED;reason="Pest maintenance stopped; farm remains stopped";actions.cancel();}
    public boolean active(){return state==State.APPLY_LOADOUT||state==State.CLEAN||state==State.RESTORE_LOADOUT||state==State.RETURN;}
    public State state(){return state;}public String status(){return state+": "+reason;}
}
