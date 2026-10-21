package dev.veltraph.client.garden.jacob;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class JacobRewardClaimTest {
    private static final JacobRewardClaim.Contract CONTRACT=new JacobRewardClaim.Contract("Private reward","Selected wheat",List.of("Selected local claim"),List.of("Selected local received"),"Private server delivered 3 tickets",3,1000);
    private static MenuSnapshot menu(int rev,String lore,int count){var slots=new ArrayList<MenuSnapshot.Slot>();for(int i=0;i<count;i++)slots.add(new MenuSnapshot.Slot(4+i,"Selected wheat",List.of(lore),false));return new MenuSnapshot(7,"Private reward",slots,rev);}
    private static final class Port implements JacobRewardClaim.Port {
        boolean valid=true,closed=true,close=true,begin=true,resume=true,cursorEmpty=true;int opens,clicks,closes,returns,resumes,cancels;long tickets=1;MenuSnapshot menu;
        JacobRewardClaim.Return progress=JacobRewardClaim.Return.ACTIVE;
        public boolean valid(){return valid;}public boolean closed(){return closed;}public boolean open(){opens++;closed=false;return true;}
        public JacobRewardClaim.Observation observe(){return new JacobRewardClaim.Observation(menu,tickets,cursorEmpty);}
        public boolean click(MenuSnapshot expected,int slot){clicks++;return true;}public boolean close(MenuSnapshot expected){closes++;closed=true;return close;}
        public boolean beginReturn(){returns++;return begin;}public JacobRewardClaim.Return returning(){return progress;}
        public boolean resume(){resumes++;return resume;}public void cancel(){cancels++;}
    }
    private static JacobRewardClaim started(Port port){var flow=new JacobRewardClaim(port);assertTrue(flow.start(CONTRACT,10,4));return flow;}
    private static void click(Port port,JacobRewardClaim flow){port.menu=menu(1,"Selected local claim",1);flow.tick(20);assertEquals(1,port.clicks);}
    @Test void clickDoesNotCreditReward(){var p=new Port();var f=started(p);click(p,f);f.tick(21);assertTrue(f.receipt().isEmpty());assertEquals(1,p.clicks);assertEquals(0,p.resumes);}
    @Test void exactAckRowAndInventoryThenPhysicalReturnAndSameResume(){var p=new Port();var f=started(p);click(p,f);p.menu=menu(2,"Selected local received",1);p.tickets=4;f.message(CONTRACT.acknowledgment(),5);f.tick(30);assertEquals(3,f.receipt().orElseThrow().tickets());assertEquals(1,p.closes);f.tick(40);assertEquals(1,p.returns);assertEquals(0,p.resumes);f.tick(45);assertEquals(0,p.resumes);p.progress=JacobRewardClaim.Return.READY;f.tick(50);assertEquals(JacobRewardClaim.State.COMPLETE,f.state());assertEquals(1,p.resumes);f.tick(60);assertEquals(1,p.clicks);assertEquals(1,p.resumes);}
    @Test void preRequestAckCannotAuthorize(){var p=new Port();var f=started(p);f.message(CONTRACT.acknowledgment(),5);click(p,f);p.menu=menu(2,"Selected local received",1);p.tickets=4;f.tick(30);assertTrue(f.receipt().isEmpty());}
    @Test void oldAckAndDifferentTextCannotAuthorize(){var p=new Port();var f=started(p);click(p,f);p.menu=menu(2,"Selected local received",1);p.tickets=4;f.message(CONTRACT.acknowledgment(),4);f.message(CONTRACT.acknowledgment()+"!",5);f.tick(30);assertTrue(f.receipt().isEmpty());}
    @Test void inventoryWithoutClaimedRowCannotAuthorize(){var p=new Port();var f=started(p);click(p,f);p.tickets=4;f.message(CONTRACT.acknowledgment(),5);f.tick(30);assertTrue(f.receipt().isEmpty());}
    @Test void rowAndAckWithoutTicketDeltaCannotAuthorize(){var p=new Port();var f=started(p);click(p,f);p.menu=menu(2,"Selected local received",1);f.message(CONTRACT.acknowledgment(),5);f.tick(30);assertTrue(f.receipt().isEmpty());}
    @Test void unexpectedDeltaCannotAuthorize(){var p=new Port();var f=started(p);click(p,f);p.menu=menu(2,"Selected local received",1);p.tickets=5;f.message(CONTRACT.acknowledgment(),5);f.tick(30);assertTrue(f.receipt().isEmpty());}
    @Test void duplicateExactRowsAreRefusedWithoutClick(){var p=new Port();var f=started(p);p.menu=menu(1,"Selected local claim",2);f.tick(20);assertEquals(JacobRewardClaim.State.FAILED,f.state());assertEquals(0,p.clicks);}
    @Test void emptyCompletedUnknownRowsStayRefused(){var p=new Port();var f=started(p);p.menu=menu(1,"Contest completed",1);f.tick(20);assertEquals(JacobRewardClaim.State.FAILED,f.state());assertEquals(0,p.clicks);}
    @Test void replacedMenuCannotAuthorize(){var p=new Port();var f=started(p);click(p,f);p.menu=new MenuSnapshot(8,"Private reward",menu(2,"Selected local received",1).slots(),2);p.tickets=4;f.message(CONTRACT.acknowledgment(),5);f.tick(30);assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertTrue(f.receipt().isEmpty());}
    @Test void clickDeadlineLatchesUnknownWithoutRetry(){var p=new Port();var f=started(p);click(p,f);f.tick(1010);assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertFalse(f.start(CONTRACT,1011,5));assertEquals(1,p.clicks);assertEquals(0,p.resumes);}
    @Test void ownerLossBeforeRequestStopsWithoutClick(){var p=new Port();var f=started(p);p.valid=false;p.menu=menu(1,"Selected local claim",1);f.tick(20);assertEquals(JacobRewardClaim.State.FAILED,f.state());assertEquals(0,p.clicks);}
    @Test void ownerLossAfterRequestCannotResume(){var p=new Port();var f=started(p);click(p,f);p.valid=false;f.tick(25);assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertEquals(0,p.resumes);}
    @Test void manualStopAfterRequestCannotResume(){var p=new Port();var f=started(p);click(p,f);f.cancel();assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertEquals(0,p.resumes);}
    @Test void physicalReturnFailureStopsBothWithoutRestart(){var p=new Port();var f=started(p);click(p,f);p.menu=menu(2,"Selected local received",1);p.tickets=4;f.message(CONTRACT.acknowledgment(),5);f.tick(30);f.tick(40);p.progress=JacobRewardClaim.Return.FAILED;f.tick(50);assertEquals(JacobRewardClaim.State.FAILED,f.state());assertEquals(0,p.resumes);}
    @Test void fixedDeadlineCeilingRejectsPersistedNegativeOrOverMaximum(){
        for(long timeout:List.of(-1L,0L,999L,60001L,120000L))assertThrows(IllegalArgumentException.class,()->new JacobRewardClaim.Contract("x","row",List.of("a"),List.of("b"),"ack",3,timeout));
        assertDoesNotThrow(()->new JacobRewardClaim.Contract("x","row",List.of("a"),List.of("b"),"ack",3,JacobRewardClaim.MAX_TIMEOUT_MS));
    }
    @Test void storedReceiptDiagnosticsCannotObserveOrCredit(){
        var p=new Port();var f=started(p);click(p,f);
        String status=f.receiptStatus();assertTrue(status.contains("requestLatched=true"));assertTrue(status.contains("matchedAckSerial=0"));
        assertEquals(JacobRewardClaim.State.WAITING_RECEIPT,f.state());assertTrue(f.receipt().isEmpty());assertEquals(1,p.clicks);assertEquals(0,p.closes);assertEquals(0,p.returns);assertEquals(0,p.resumes);
    }
    @Test void originalCursorFaultStaysUnknownAfterLateGoodReceipt(){
        var p=new Port();var f=started(p);click(p,f);p.menu=menu(2,"Selected local received",1);p.tickets=4;p.cursorEmpty=false;f.tick(30);
        assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());var original=f.lastReceiptGuardTrace().orElseThrow();
        assertEquals(7,original.requestMenuId());assertEquals(7,original.observedMenuId());assertEquals(1,original.requestRevision());assertEquals(2,original.observedRevision());
        assertTrue(original.sameTitle());assertFalse(original.cursorEmpty());assertEquals(4,original.observedTickets());assertEquals(1,original.beforeTickets());assertEquals(3,original.expectedTicketDelta());assertEquals(4,original.ackBaseline());assertEquals(0,original.matchedAckSerial());
        p.cursorEmpty=true;f.message(CONTRACT.acknowledgment(),5);f.tick(40);
        assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertSame(original,f.lastReceiptGuardTrace().orElseThrow());assertTrue(f.receipt().isEmpty());
        assertEquals(1,p.clicks);assertEquals(0,p.closes);assertEquals(0,p.returns);assertEquals(0,p.resumes);
    }
    @Test void invalidGenuineChainCannotAdoptLateOtherwiseGoodData(){
        var p=new Port();var f=started(p);click(p,f);f.rejectInFlight("Missing normally-returned submission or foreign/local mutation");
        assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());p.menu=menu(2,"Selected local received",1);p.tickets=4;f.message(CONTRACT.acknowledgment(),5);f.tick(40);
        assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertTrue(f.receipt().isEmpty());assertEquals(1,p.clicks);assertEquals(0,p.closes);assertEquals(0,p.returns);assertEquals(0,p.resumes);
    }
    @Test void capturedOldRunRevocationCannotInvokeReplacementCancellation(){
        var p=new Port();var f=started(p);click(p,f);f.revokeUnconfirmed("Original run replaced during callback");
        assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertEquals(0,p.cancels);p.menu=menu(2,"Selected local received",1);p.tickets=4;f.message(CONTRACT.acknowledgment(),5);f.tick(40);
        assertEquals(JacobRewardClaim.State.UNKNOWN,f.state());assertTrue(f.receipt().isEmpty());assertEquals(0,p.cancels);assertEquals(0,p.resumes);
    }
    @Test void unavailableContractRefused(){assertThrows(IllegalArgumentException.class,()->new JacobRewardClaim.Contract("","row",List.of("a"),List.of("b"),"ack",3,1000));assertThrows(IllegalArgumentException.class,()->new JacobRewardClaim.Contract("x","row",List.of("a"),List.of("a"),"ack",3,1000));}
}
