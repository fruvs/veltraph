package dev.veltraph.client.garden.pests.philip;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PestBagExchangeTest {
    static final AbiphoneCall.Item MARKER=new AbiphoneCall.Item("minecraft:player_head","Philip",1,"observed marker");
    static final AbiphoneCall.Item BUTTON=new AbiphoneCall.Item("minecraft:chest","Empty Vacuum Bag",1,"observed exchange");
    static final PestBagExchange.Settings SETTINGS=new PestBagExchange.Settings("Philip",MARKER,BUTTON,"Bag exchanged",List.of("Bag rejected"),10,1,12);
    static class Port implements PestBagExchange.Port {
        Object player=new Object(),world=new Object(),connection=new Object(),menu=new Object();int revision=1,bag=4,count=1,clicks,closes;boolean empty=true,prediction;String title="Philip";
        List<AbiphoneCall.Row> rows=List.of(new AbiphoneCall.Row(43,false,false,MARKER),new AbiphoneCall.Row(19,false,false,BUTTON));
        public PestBagExchange.Observation read(){return new PestBagExchange.Observation(player,world,connection,menu,title,revision,rows,empty,prediction,bag,count);}
        public boolean click(PestBagExchange.Observation expected,int slot){assertEquals(19,slot);clicks++;return true;}
        public boolean close(PestBagExchange.Observation expected){closes++;menu=null;return true;}
    }
    @Test void dynamicNativeSlotAndAllOutcomeEvidenceRequired(){var p=new Port();var f=new PestBagExchange(p);assertTrue(f.start(SETTINGS,0));f.message("Bag exchanged");f.tick(1);assertEquals(1,p.clicks);p.bag=0;p.revision=2;f.tick(2);assertEquals(PestBagExchange.State.WAIT_OUTCOME,f.state());f.message("§aBag exchanged");f.tick(3);assertEquals(PestBagExchange.State.CLOSE,f.state());f.tick(4);assertEquals(PestBagExchange.State.COMPLETE,f.state());assertEquals(4,f.exchanged());assertEquals(1,p.closes);}
    @Test void receiptWithoutNativeZeroNeverCompletesOrRetries(){var p=new Port();var f=new PestBagExchange(p);f.start(SETTINGS,0);f.tick(1);f.message("Bag exchanged");p.revision++;f.tick(13);assertEquals(PestBagExchange.State.FAILED,f.state());assertEquals(1,p.clicks);assertEquals(0,f.exchanged());}
    @Test void nativeBagZeroWithoutReceivedRevisionRemainsPending(){var p=new Port();var f=new PestBagExchange(p);f.start(SETTINGS,0);f.tick(1);p.bag=0;f.message("Bag exchanged");f.tick(2);assertEquals(PestBagExchange.State.WAIT_OUTCOME,f.state());f.tick(13);assertEquals(PestBagExchange.State.FAILED,f.state());}
    @Test void menuConnectionOrVacuumReplacementCannotClickOrClose(){for(int kind=0;kind<3;kind++){var p=new Port();var f=new PestBagExchange(p);f.start(SETTINGS,0);if(kind==0)p.menu=new Object();if(kind==1)p.connection=new Object();if(kind==2)p.count=2;f.tick(1);assertEquals(PestBagExchange.State.FAILED,f.state());assertEquals(0,p.clicks);assertEquals(0,p.closes);}}
    @Test void ambiguousLockedAndPlayerInventoryRowsCannotStart(){for(int kind=0;kind<3;kind++){var p=new Port();if(kind==0)p.rows=List.of(new AbiphoneCall.Row(43,false,false,MARKER),new AbiphoneCall.Row(19,false,false,BUTTON),new AbiphoneCall.Row(20,false,false,BUTTON));else p.rows=List.of(new AbiphoneCall.Row(43,false,false,MARKER),new AbiphoneCall.Row(19,kind==1,kind==2,BUTTON));var f=new PestBagExchange(p);assertFalse(f.start(SETTINGS,0));assertEquals(0,p.clicks);}}
    @Test void emptyAndExcessiveBagDoNotStart(){for(int amount:new int[]{0,11}){var p=new Port();p.bag=amount;var f=new PestBagExchange(p);assertFalse(f.start(SETTINGS,0));assertEquals(0,p.clicks);}}
    @Test void unexpectedPartialAndIncreasedBagFail(){for(int amount:new int[]{2,5}){var p=new Port();var f=new PestBagExchange(p);f.start(SETTINGS,0);f.tick(1);p.bag=amount;p.revision++;f.tick(2);assertEquals(PestBagExchange.State.FAILED,f.state());assertEquals(0,p.closes);}}
    @Test void onlyOwnedPredictionWaitsForCursorCorrection(){var p=new Port();var f=new PestBagExchange(p);f.start(SETTINGS,0);f.tick(1);p.empty=false;p.prediction=true;f.tick(2);assertEquals(PestBagExchange.State.WAIT_OUTCOME,f.state());p.prediction=false;f.tick(3);assertEquals(PestBagExchange.State.FAILED,f.state());}
    @Test void explicitRejectionAndManualStopNeverRetry(){var p=new Port();var f=new PestBagExchange(p);f.start(SETTINGS,0);f.tick(1);f.message("Bag rejected");f.tick(2);assertEquals(PestBagExchange.State.FAILED,f.state());assertEquals(1,p.clicks);var p2=new Port();var f2=new PestBagExchange(p2);f2.start(SETTINGS,0);f2.cancel();f2.tick(1);assertEquals(PestBagExchange.State.CANCELLED,f2.state());assertEquals(0,p2.clicks);}
}
