package dev.veltraph.client.galatea.agatha;

/** Five readable current-source stages with strict native reward/receipt/menu acknowledgement. */
public final class AgathaContestCycle {
    public enum Stage { CLOSE_UI_STARTING,CLICK_CLAIM_MESSAGE,WAIT_FOR_UI_OPEN,CLICK_BULK_CLAIM,CLOSE_UI_ENDING,COMPLETE,FAILED,CANCELLED }
    public record Observation(boolean available,boolean invitationPresent,boolean invitationFresh,boolean menuOpen,boolean menuOwned,
                              boolean cursorSafe,boolean cursorEmpty,boolean claimEnabled,boolean claimChanged,boolean revisionChanged,
                              boolean receipt,boolean failure,int rewardCount,long now) {}
    public record Settings(long timeout,long ack,int minReward,int maxClaims) {}
    public interface Port { void release();boolean closeStarting();boolean claimMessage();boolean claimAll();boolean closeEnding(); }
    private Stage stage=Stage.CLOSE_UI_STARTING;
    private long entered=-1,sent=-1,stable=-1;
    private int rewardBefore,claims;
    private String reason="";
    public Stage stage(){return stage;}public int claims(){return claims;}public String reason(){return reason;}
    public boolean terminal(){return stage==Stage.COMPLETE||stage==Stage.FAILED||stage==Stage.CANCELLED;}
    private void move(Stage next,long now){stage=next;entered=now;stable=-1;}
    private void fail(String text,Port port){port.release();reason=text;stage=Stage.FAILED;}
    public void cancel(Port port){port.release();stage=Stage.CANCELLED;reason="Cancelled";}
    public void tick(Observation o,Settings s,Port p){
        if(terminal())return;if(entered<0)entered=o.now();
        if(!o.available()){fail("Original player/world unavailable",p);return;}
        if(!o.cursorSafe()){fail("Unrelated occupied cursor",p);return;}
        if(o.failure()){fail("Agatha rejected the claim",p);return;}
        if(o.menuOpen()&&!o.menuOwned()){fail("Unexpected or replaced menu",p);return;}
        if(stage!=Stage.CLOSE_UI_ENDING&&(!o.invitationPresent()||!o.invitationFresh())){fail("Invitation missing or expired",p);return;}
        if(o.now()-entered>s.timeout()){fail("Agatha stage timed out: "+stage,p);return;}
        switch(stage){
            case CLOSE_UI_STARTING -> {p.release();if(o.cursorEmpty()&&p.closeStarting()){rewardBefore=o.rewardCount();move(Stage.CLICK_CLAIM_MESSAGE,o.now());}}
            case CLICK_CLAIM_MESSAGE -> {if(o.cursorEmpty()&&p.claimMessage())move(Stage.WAIT_FOR_UI_OPEN,o.now());}
            case WAIT_FOR_UI_OPEN -> {if(o.menuOwned()&&o.cursorEmpty()&&o.claimEnabled())move(Stage.CLICK_BULK_CLAIM,o.now());}
            case CLICK_BULK_CLAIM -> {
                if(sent>=0){boolean confirmed=o.cursorEmpty()&&o.revisionChanged()&&o.claimChanged()&&o.receipt()&&o.rewardCount()-rewardBefore>=s.minReward();
                    if(confirmed){if(stable<0)stable=o.now();if(o.now()-stable>=s.ack())move(Stage.CLOSE_UI_ENDING,o.now());}
                    else stable=-1;
                    // No repeat after a partial result. Only a fully unchanged, acknowledged menu
                    // may retry; the exact outcome window and max attempts are declared local data.
                    if(!confirmed&&o.now()-sent>=s.timeout()/2&&o.cursorEmpty()&&o.menuOwned()&&o.claimEnabled()&&!o.claimChanged()&&!o.receipt()&&o.rewardCount()==rewardBefore&&o.revisionChanged()&&claims<s.maxClaims())sent=-1;
                }else if(o.cursorEmpty()&&o.menuOwned()&&o.claimEnabled()&&claims<s.maxClaims()&&p.claimAll()){claims++;sent=o.now();}
            }
            case CLOSE_UI_ENDING -> {if(o.cursorEmpty()&&p.closeEnding()){p.release();move(Stage.COMPLETE,o.now());}}
            default -> {}
        }
    }
}
