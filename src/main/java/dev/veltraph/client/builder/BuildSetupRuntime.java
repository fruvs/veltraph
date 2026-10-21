package dev.veltraph.client.builder;

/** Local acknowledgement and recovery policy, independent of opaque native expansion/timings. */
public final class BuildSetupRuntime {
    public enum Action { NONE, MOVE, SELECT, BREAK, WATER, COMPLETE, STOP }
    public record Facts(boolean loaded,String block,boolean sourceWater,boolean blockEntity,boolean reachable,
                        boolean available,boolean selected,boolean aligned,long now) {}
    private int index;
    private long started=-1,lastIssued=-1;
    private int attempts;
    private boolean waiting;
    private String status="Ready";
    public Action tick(BuildSetupPlan plan,Facts f) {
        if(index>=plan.steps().size())return Action.COMPLETE;
        var step=plan.steps().get(index);
        if(!f.loaded())return stop("Target chunk unloaded; resume after loading");
        if(f.blockEntity())return stop("Target contains inventory/data; refused");
        if(started<0)started=f.now();
        boolean air=f.block().equals("minecraft:air")||f.block().equals("minecraft:cave_air")||f.block().equals("minecraft:void_air");
        boolean fluid=f.block().equals("minecraft:water");
        boolean complete=step.operation()==BuildSetupPlan.Operation.CLEAR?(air||fluid):f.sourceWater();
        if(complete) {
            if(waiting&&f.now()-lastIssued<250)return Action.NONE;
            index++;started=-1;lastIssued=-1;attempts=0;waiting=false;status="Confirmed "+index+"/"+plan.steps().size();
            return index==plan.steps().size()?Action.COMPLETE:Action.NONE;
        }
        if(f.now()-started>30000)return stop("Process timed out; checkpoint retained");
        if(step.operation()==BuildSetupPlan.Operation.CLEAR&&!step.expectedBlock().equals(f.block()))return stop("Clear target differs from expected "+step.expectedBlock());
        if(step.operation()==BuildSetupPlan.Operation.WATER&&!air&&!fluid)return stop("Water pocket occupied; refused");
        // A successful bucket may already be empty while the server acknowledgement is in flight.
        if(waiting&&step.operation()==BuildSetupPlan.Operation.WATER&&f.now()-lastIssued<1500)return Action.NONE;
        if(!f.available())return stop("Required tool/water bucket missing; checkpoint retained");
        if(!f.reachable()){status="Approach "+step.stage();return Action.MOVE;}
        if(!f.selected())return Action.SELECT;
        if(!f.aligned()){status="Aim "+step.stage();return Action.NONE;}
        if(step.operation()==BuildSetupPlan.Operation.CLEAR){waiting=true;lastIssued=f.now();status="Breaking "+step.stage();return Action.BREAK;}
        if(attempts>=3)return stop("Water not acknowledged after three uses; checkpoint retained");
        waiting=true;lastIssued=f.now();attempts++;status="Awaiting source water "+step.stage();return Action.WATER;
    }
    public void resume(){started=-1;lastIssued=-1;attempts=0;waiting=false;status="Resume checkpoint "+index;}
    public void reset(){index=0;resume();}
    public int index(){return index;}
    public String status(){return status;}
    private Action stop(String reason){status=reason;return Action.STOP;}
}
