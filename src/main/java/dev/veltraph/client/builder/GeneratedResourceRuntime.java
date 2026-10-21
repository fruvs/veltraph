package dev.veltraph.client.builder;

/** Source resource-helper subset with actual tool gauge and native transfer acknowledgement. */
public final class GeneratedResourceRuntime {
    public enum State { SWITCH_TO_TOOL, LOOK_AWAY, OPEN_TOOL, CHECK_ALREADY_HAS_REQUIRED_IN_TOOL, MOVE_FROM_INV_TO_TOOL, BUY_RESOURCE, CLOSE_GUI, COMPLETE, BLOCKED }
    public enum Action { SELECT, AIM_AWAY, OPEN, TRANSFER, CLOSE, NONE, COMPLETE, STOP }
    public record Facts(boolean selected,boolean lookingAway,boolean open,boolean correctMenu,int gauge,
                        boolean resourceAvailable,int revision,long now) {}
    private State state=State.SWITCH_TO_TOOL;
    private int issuedRevision=-1,issuedGauge=-1;
    private long started=-1,issued=-1;
    private boolean proved;
    private boolean requestedOpen;
    private String reason="Select bound source tool";
    public Action tick(int required,Facts f) {
        if(started<0)started=f.now();
        if(f.now()-started>30000)return stop("Tool resource setup timed out");
        if(proved){state=State.CLOSE_GUI;if(f.open())return Action.CLOSE;state=State.COMPLETE;return Action.COMPLETE;}
        if(f.open()) {
            if(!requestedOpen)return stop("Unowned source tool menu; tool opening was not requested by this workflow");
            if(!f.correctMenu()||f.gauge()<0)return stop("Source tool menu/gauge binding does not match");
            state=State.CHECK_ALREADY_HAS_REQUIRED_IN_TOOL;
            if(issued>=0) {
                if(f.revision()==issuedRevision||f.gauge()<=issuedGauge)return f.now()-issued>3000?stop("Tool transfer was not acknowledged by menu revision and gauge growth"):Action.NONE;
                issued=-1;
            }
            if(f.gauge()>=required){proved=true;state=State.CLOSE_GUI;return Action.CLOSE;}
            if(!f.resourceAvailable()){state=State.BUY_RESOURCE;reason="BUY_RESOURCE / REMOVE_FROM_TOOL / SELL_OTHER_RESOURCES / inventory dissolution needs configured acquisition contract; tool gauge below required count";return Action.STOP;}
            state=State.MOVE_FROM_INV_TO_TOOL;issued=f.now();issuedRevision=f.revision();issuedGauge=f.gauge();return Action.TRANSFER;
        }
        if(!f.selected()){state=State.SWITCH_TO_TOOL;return Action.SELECT;}
        if(!f.lookingAway()){state=State.LOOK_AWAY;return Action.AIM_AWAY;}
        state=State.OPEN_TOOL;if(issued<0){issued=f.now();requestedOpen=true;return Action.OPEN;}
        if(f.now()-issued>3000)return stop("Source tool did not open bound menu");
        return Action.NONE;
    }
    public String status(){return state+": "+reason;}
    public void inheritObservedMenuReceipt(){requestedOpen=true;}
    public State state(){return state;}
    private Action stop(String text){state=State.BLOCKED;reason=text;return Action.STOP;}
}
