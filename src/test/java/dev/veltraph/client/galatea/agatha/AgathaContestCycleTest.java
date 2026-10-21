package dev.veltraph.client.galatea.agatha;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgathaContestCycleTest {
    private static final AgathaContestCycle.Settings SETTINGS=new AgathaContestCycle.Settings(1000,100,2,2);
    private static final class World implements AgathaContestCycle.Port {
        boolean available=true,present=true,fresh=true,open,owned,cursorSafe=true,cursorEmpty=true,enabled=true,changed,revision,receipt,failure;
        boolean pay=true,sendReceipt=true,change=true,sendRevision=true;int rewards,commands,claims,closed;
        AgathaContestCycle.Observation read(long now){return new AgathaContestCycle.Observation(available,present,fresh,open,owned,cursorSafe,cursorEmpty,enabled,changed,revision,receipt,failure,rewards,now);}
        public void release(){}public boolean closeStarting(){return !open&&cursorEmpty;}
        public boolean claimMessage(){commands++;open=owned=true;return true;}
        public boolean claimAll(){claims++;if(pay)rewards+=2;receipt=sendReceipt;changed=change;revision=sendRevision;if(change)enabled=false;return true;}
        public boolean closeEnding(){open=owned=false;closed++;return true;}
    }
    private void drive(AgathaContestCycle c,World w){for(int i=0;i<40&&!c.terminal();i++)c.tick(w.read(i*100L),SETTINGS,w);}
    @Test void fiveSourceStagesRequireAllNativeAcknowledgements(){var c=new AgathaContestCycle();var w=new World();drive(c,w);assertEquals(AgathaContestCycle.Stage.COMPLETE,c.stage());assertEquals(1,w.commands);assertEquals(1,w.claims);assertEquals(2,w.rewards);assertEquals(1,w.closed);}
    @Test void receiptWithoutRewardCannotCompleteOrRetry(){var c=new AgathaContestCycle();var w=new World();w.pay=false;drive(c,w);assertEquals(AgathaContestCycle.Stage.FAILED,c.stage());assertEquals(1,w.claims);}
    @Test void rewardWithoutReceiptCannotCompleteOrRetry(){var c=new AgathaContestCycle();var w=new World();w.sendReceipt=false;drive(c,w);assertEquals(AgathaContestCycle.Stage.FAILED,c.stage());assertEquals(1,w.claims);}
    @Test void unacknowledgedButtonOrRevisionCannotComplete(){for(int i=0;i<2;i++){var c=new AgathaContestCycle();var w=new World();if(i==0)w.change=false;else w.sendRevision=false;drive(c,w);assertEquals(AgathaContestCycle.Stage.FAILED,c.stage());assertEquals(1,w.claims);}}
    @Test void fullyUnchangedAckMayRetryOnlyBoundedClaims(){var c=new AgathaContestCycle();var w=new World();w.pay=w.sendReceipt=w.change=false;drive(c,w);assertEquals(AgathaContestCycle.Stage.FAILED,c.stage());assertEquals(2,w.claims);}
    @Test void staleMissingForeignMenuOrCursorNeverExecutesCommand(){for(int i=0;i<4;i++){var c=new AgathaContestCycle();var w=new World();if(i==0)w.fresh=false;if(i==1)w.present=false;if(i==2)w.open=true;if(i==3)w.cursorSafe=false;drive(c,w);assertEquals(AgathaContestCycle.Stage.FAILED,c.stage());assertEquals(0,w.commands);assertEquals(0,w.claims);}}
    @Test void serverRejectionAndCancellationAreTerminal(){var c=new AgathaContestCycle();var w=new World();w.failure=true;drive(c,w);assertEquals(AgathaContestCycle.Stage.FAILED,c.stage());var stop=new AgathaContestCycle();stop.cancel(w);drive(stop,w);assertEquals(AgathaContestCycle.Stage.CANCELLED,stop.stage());assertEquals(0,w.commands);}
}
