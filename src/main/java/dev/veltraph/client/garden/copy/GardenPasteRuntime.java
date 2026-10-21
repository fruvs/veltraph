package dev.veltraph.client.garden.copy;

/** Local replacement for ordered USE_RESOURCE/observed-state/ghost recovery, with explicit resource pause. */
public final class GardenPasteRuntime {
    public enum Action{WAIT,MOVE,SELECT,PLACE,PAUSE,FAIL,COMPLETE}
    public record Facts(boolean loaded,boolean exact,boolean air,boolean resource,boolean selected,boolean reachable,boolean aligned,long now){}
    private int index,attempts,observations;private long started=-1,issued=-1;private String status="Prepared";
    public Action tick(int cells,Facts f,long ack,long timeout,int maxAttempts){
        if(index>=cells)return Action.COMPLETE;if(!f.loaded())return fail("Destination chunk unloaded; checkpoint retained");
        if(started<0)started=f.now();
        if(f.exact()){
            if(issued>=0&&(f.now()-issued<ack||++observations<2))return Action.WAIT;
            index++;started=issued=-1;attempts=observations=0;status="Confirmed "+index+"/"+cells;return index==cells?Action.COMPLETE:Action.WAIT;
        }
        observations=0;if(!f.air())return fail("Destination mask changed/occupied; refusing overwrite");
        if(issued>=0&&f.now()-issued<ack)return Action.WAIT;
        if(!f.resource()){status="Waiting for explicit material refill; checkpoint "+index;return Action.PAUSE;}
        if(f.now()-started>=timeout)return fail("Operation acknowledgement timed out; checkpoint retained");
        if(!f.reachable())return Action.MOVE;if(!f.selected())return Action.SELECT;if(!f.aligned())return Action.WAIT;
        if(issued>=0&&f.now()-issued<Math.max(500,ack))return Action.WAIT;
        if(attempts>=maxAttempts)return fail("Placement attempts exhausted; no fabricated acknowledgement");
        attempts++;issued=f.now();status="USE_RESOURCE attempt "+attempts+" at cell "+index;return Action.PLACE;
    }
    private Action fail(String s){status=s;return Action.FAIL;}
    public void resume(){started=issued=-1;attempts=observations=0;status="Explicit continuation at "+index;}
    public int index(){return index;}public String status(){return status;}
    public boolean awaitingAcknowledgement(){return issued>=0;}
}
