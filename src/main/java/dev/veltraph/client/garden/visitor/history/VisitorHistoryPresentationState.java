package dev.veltraph.client.garden.visitor.history;

/** Current read/lifecycle presentation only; it never confirms native scope or authorizes an action. */
public final class VisitorHistoryPresentationState {
    private VisitorHistoryStore.Observation value=VisitorHistoryResolver.UNKNOWN.observe("");
    public VisitorHistoryStore.Observation update(VisitorHistoryStore.Observation value){this.value=value==null||value.newness()==null||value.provenance()==null?VisitorHistoryResolver.UNKNOWN.observe(""):value;return this.value;}
    public void clear(){value=VisitorHistoryResolver.UNKNOWN.observe("");}
    public VisitorHistoryStore.Observation current(){return value;}
}
