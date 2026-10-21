package dev.veltraph.client.fishing.trash;

/** Source WALK/ROTATE/OPEN/REMOVE/CLOSE plus safe initial reel and physical saved-cast continuation. */
public final class FishingTrashWorkflow {
    public enum State { IDLE,WAIT_REEL,SELECT_CLIFF,WALK_TO_CLIFF,ROTATE_PROPERLY,OPEN_INVENTORY,REMOVE_TRASH,CLOSE_INVENTORY,RETURN,RESTORE_AIM,COMPLETE,FAILED }
    public interface Port {
        boolean valid();boolean bobber();void reel();
        /** -1 no eligible reachable source candidate,0 scanning,1 navigation started. */
        int selectCliff();int walkCliff();boolean aimCliff();boolean openInventory();
        void discardTick();boolean discarded();String discardFailure();boolean closeInventory();
        boolean startReturn();int returnTick();boolean restoreAim();void release();
    }
    private State state=State.IDLE;private long began;private FishingTrashProfile profile;private String failure="";
    public State state(){return state;}public String failure(){return failure;}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED;}
    public boolean start(FishingTrashProfile contract,long now,Port port){
        if(state!=State.IDLE&&state!=State.COMPLETE||!port.valid())return false;
        profile=contract;began=now;failure="";state=port.bobber()?State.WAIT_REEL:State.SELECT_CLIFF;
        if(state==State.WAIT_REEL)port.reel();return true;
    }
    public void tick(long now,Port port) {
        if(!active())return;
        if(!port.valid()){fail("Fishing trash context, rod, inventory or cliff ownership changed",port);return;}
        if(now<began||now-began>profile.stageTimeoutMillis()){fail("Fishing trash explicit stage bound reached",port);return;}
        switch(state) {
            case WAIT_REEL->{if(!port.bobber())state=State.SELECT_CLIFF;else if(now-began>profile.requestTimeoutMillis())fail("Fishing trash initial reel acknowledgment missing",port);}
            case SELECT_CLIFF->{int result=port.selectCliff();if(result<0)fail("No loaded source-eligible cliff with a physical path",port);else if(result>0)state=State.WALK_TO_CLIFF;}
            case WALK_TO_CLIFF->{int result=port.walkCliff();if(result<0)fail("Fishing trash cliff route blocked",port);else if(result>0)state=State.ROTATE_PROPERLY;}
            case ROTATE_PROPERLY->{if(port.aimCliff())state=State.OPEN_INVENTORY;}
            case OPEN_INVENTORY->{if(port.openInventory())state=State.REMOVE_TRASH;else fail("Fishing trash owned inventory opening refused",port);}
            case REMOVE_TRASH->{port.discardTick();if(!port.discardFailure().isEmpty())fail(port.discardFailure(),port);else if(port.discarded())state=State.CLOSE_INVENTORY;}
            case CLOSE_INVENTORY->{if(!port.closeInventory()||!port.startReturn())fail("Fishing trash close or saved-position return refused",port);else state=State.RETURN;}
            case RETURN->{int result=port.returnTick();if(result<0)fail("Fishing trash return path blocked",port);else if(result>0)state=State.RESTORE_AIM;}
            case RESTORE_AIM->{if(port.restoreAim()){port.release();state=State.COMPLETE;}}
            default->{}
        }
    }
    public void fail(String reason,Port port){failure=reason;state=State.FAILED;port.release();}
    public void stop(Port port){port.release();state=State.IDLE;profile=null;began=0;failure="";}
}
