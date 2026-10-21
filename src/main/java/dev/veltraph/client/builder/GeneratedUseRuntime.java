package dev.veltraph.client.builder;

/** Tilling/planting acknowledgement policy. Predicted inventory changes are never world completion. */
public final class GeneratedUseRuntime {
    public enum Action { NONE, MOVE, SELECT, USE, COMPLETE, STOP }
    public record Facts(boolean loaded, boolean blockEntity, String block, boolean settled, boolean supportReady,
                        boolean available, boolean selected, boolean reachable, boolean aligned, long now) {}
    private long started=-1,lastUse=-1;
    private int attempts;
    private String status="Ready";
    public Action tick(GeneratedBuildPlan.Process process,Facts f) {
        return tick(process,f,process.after().equals(f.block()));
    }
    public Action tick(GeneratedBuildPlan.Process process,Facts f,boolean outputMatches) {
        if(!f.loaded()||f.blockEntity())return stop("Unloaded target or block containing data");
        if(started<0)started=f.now();
        if(f.now()-started>30000)return stop("Use timed out; checkpoint retained");
        if(outputMatches) {
            if(!f.settled()||lastUse>=0&&f.now()-lastUse<250)return Action.NONE;
            status="Observed "+process.after();return Action.COMPLETE;
        }
        if(!process.before().equals(f.block()))return stop("Target differs from expected "+process.before());
        if(!f.supportReady())return stop("Tilling/planting support is occupied or differs from expected farmland");
        if(lastUse>=0&&f.now()-lastUse<1500)return Action.NONE;
        if(!f.available())return stop("Required tool/plant item missing");
        if(!f.reachable())return Action.MOVE;
        if(!f.selected())return Action.SELECT;
        if(!f.aligned())return Action.NONE;
        if(attempts>=3)return stop("Use not acknowledged after three attempts");
        attempts++;lastUse=f.now();status="Await observed "+process.after();return Action.USE;
    }
    public String status(){return status;}
    private Action stop(String reason){status=reason;return Action.STOP;}
}
