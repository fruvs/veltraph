package dev.veltraph.client.builder;

/** In-memory subprocess/cell checkpoint. Issued clicks, transfers, or predictions never advance it. */
public final class GeneratedBuildRuntime {
    public enum State { READY, APPROACH, SELECT_TOOL, RESTOCK, EXECUTE, AWAIT_WORLD, BLOCKED, COMPLETE }
    private int process,cell;
    private State state=State.READY;
    private String reason="Ready";
    public void confirmed(GeneratedBuildPlan plan) {
        var p=plan.processes().get(process);
        if(++cell>=p.cells().size()){process++;cell=0;}
        state=process==plan.processes().size()?State.COMPLETE:State.READY;
        reason="Confirmed process "+process+"/"+plan.processes().size()+", cell "+cell;
    }
    public void state(State next,String message){state=next;reason=message;}
    public void resume(){state=State.READY;reason="Resume process "+process+", cell "+cell;}
    public int process(){return process;}
    public int cell(){return cell;}
    public State state(){return state;}
    public String status(){return state+": "+reason;}
    public boolean complete(GeneratedBuildPlan plan){return process>=plan.processes().size();}
}
