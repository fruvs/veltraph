package dev.veltraph.client.builder;

/** One operation at a time, with observed-state confirmation and a bounded failure path.
 * Source: FarmBuilderPlaceDestroy.md; timing defaults are local, not recovered literals.
 */
public final class BuildRuntime {
    public enum Action { NONE, MOVE, SELECT, PLACE, BREAK, COMPLETE, STOP }
    public record Facts(boolean loaded,String block,boolean inReach,boolean itemAvailable,
                        boolean itemSelected,boolean aligned,long now) {}
    private int index;
    private long started,lastPlace;
    private boolean issued;
    private String reason="Idle";
    public Action tick(BuildLayout layout,Facts in) {
        if(index>=layout.entries().size()) return Action.COMPLETE;
        if(started==0) started=in.now();
        var entry=layout.entries().get(index);
        if(!in.loaded()) return stop("Target chunk unloaded");
        if(in.now()-started>30_000) return stop("Operation timed out");
        boolean air="minecraft:air".equals(in.block())||"minecraft:cave_air".equals(in.block())||"minecraft:void_air".equals(in.block());
        boolean complete=entry.operation()==BuildLayout.Operation.PLACE?entry.block().equals(in.block()):air;
        if(complete) {
            if(issued&&in.now()-lastPlace<250) return Action.NONE;
            index++;started=0;issued=false;reason="Confirmed "+index+"/"+layout.entries().size();
            return index==layout.entries().size()?Action.COMPLETE:Action.NONE;
        }
        if(entry.operation()==BuildLayout.Operation.PLACE&&!air) return stop("Placement target is occupied; refusing to overwrite");
        if(entry.operation()==BuildLayout.Operation.DESTROY&&!entry.block().equals(in.block())) return stop("Destroy target differs from the expected block");
        if(!in.itemAvailable()) return stop("Required material/tool missing from hotbar");
        if(!in.inReach()) { reason="Move to operation "+(index+1);return Action.MOVE; }
        if(!in.itemSelected()) return Action.SELECT;
        if(!in.aligned()) return Action.NONE;
        if(entry.operation()==BuildLayout.Operation.DESTROY) { issued=true;lastPlace=in.now();reason="Breaking "+entry.block();return Action.BREAK; }
        if(in.now()-lastPlace<500) return Action.NONE;
        issued=true;lastPlace=in.now();reason="Placing "+entry.block();return Action.PLACE;
    }
    private Action stop(String message) { reason=message;return Action.STOP; }
    public int index() { return index; }
    public String status() { return reason; }
    public void reset() { index=0;started=0;lastPlace=0;issued=false;reason="Ready"; }
}
