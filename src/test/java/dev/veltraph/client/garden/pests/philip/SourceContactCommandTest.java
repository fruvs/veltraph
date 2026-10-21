package dev.veltraph.client.garden.pests.philip;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceContactCommandTest {
    static final SourceContactCommand.Settings SETTINGS=new SourceContactCommand.Settings("privatecall ","Philip","Philip",PestBagExchangeTest.MARKER,"Philip answered",List.of("Philip unavailable"),20);
    static class Port implements SourceContactCommand.Port {
        Object player=new Object(),world=new Object(),connection=new Object(),menu;int revision=1,bag=4,count=1,closes;boolean empty=true;String title="Philip";
        List<AbiphoneCall.Row> rows=List.of(new AbiphoneCall.Row(43,false,false,PestBagExchangeTest.MARKER));
        public SourceContactCommand.Observation read(){var inv=new ArrayList<>(Collections.nCopies(36,AbiphoneCall.Item.EMPTY));inv.set(10,new AbiphoneCall.Item("minecraft:stick","Vacuum",1,"Bag "+bag));return new SourceContactCommand.Observation(true,player,world,connection,menu,title,revision,rows,empty,inv,2);}

        int sends;Port(){menu=null;}public boolean send(SourceContactCommand.Observation expected,String cmd){assertEquals("privatecall Philip",cmd);sends++;return true;}
        void dialogue(){menu=new Object();revision++;}
    }
    @Test void onePrefixPlusContactAndFreshExactMenuReceiptHandoff(){var p=new Port();var f=new SourceContactCommand(p);assertTrue(f.start(SETTINGS,0));assertEquals(1,p.sends);p.dialogue();f.tick(1);assertEquals(SourceContactCommand.State.WAIT_MENU,f.state());f.message("§aPhilip answered");f.tick(2);assertEquals(SourceContactCommand.State.HANDOFF,f.state());assertSame(p.menu,f.handoff().orElseThrow().menu());assertTrue(f.handoffValid());p.bag=0;assertFalse(f.handoffValid());assertTrue(f.release());assertEquals(1,p.sends);}
    @Test void receiptBeforeRequestAndMenuWithoutReceiptNeverCompleteOrRetry(){var p=new Port();var f=new SourceContactCommand(p);f.message("Philip answered");f.start(SETTINGS,0);p.dialogue();f.tick(1);f.tick(21);assertEquals(SourceContactCommand.State.FAILED,f.state());assertEquals(1,p.sends);}
    @Test void receiptAloneCannotComplete(){var p=new Port();var f=new SourceContactCommand(p);f.start(SETTINGS,0);f.message("Philip answered");f.tick(1);assertEquals(SourceContactCommand.State.WAIT_MENU,f.state());f.tick(21);assertEquals(SourceContactCommand.State.FAILED,f.state());}
    @Test void wrongMenuAndChangedConnectionArePreserved(){for(boolean wrongMenu:new boolean[]{false,true}){var p=new Port();var f=new SourceContactCommand(p);f.start(SETTINGS,0);p.dialogue();if(wrongMenu)p.title="George";else p.connection=new Object();f.message("Philip answered");f.tick(1);assertEquals(SourceContactCommand.State.FAILED,f.state());assertEquals(0,p.closes);assertEquals(1,p.sends);}}
    @Test void startsOnlyClosedAndEmptyCursorThenProtectsBagDuringCall(){var p=new Port();p.dialogue();var f=new SourceContactCommand(p);assertFalse(f.start(SETTINGS,0));assertEquals(0,p.sends);p.menu=null;p.empty=false;assertFalse(f.start(SETTINGS,0));p.empty=true;assertTrue(f.start(SETTINGS,0));p.bag=0;f.tick(1);assertEquals(SourceContactCommand.State.FAILED,f.state());}
    @Test void replacedExactDialogueAndManualStopDoNotAdoptNewMenu(){var p=new Port();var f=new SourceContactCommand(p);f.start(SETTINGS,0);p.dialogue();f.tick(1);p.dialogue();f.message("Philip answered");f.tick(2);assertEquals(SourceContactCommand.State.FAILED,f.state());var p2=new Port();var f2=new SourceContactCommand(p2);f2.start(SETTINGS,0);f2.cancel();p2.dialogue();f2.message("Philip answered");f2.tick(1);assertEquals(SourceContactCommand.State.CANCELLED,f2.state());assertEquals(1,p2.sends);assertEquals(0,p2.closes);}
    @Test void explicitCommandFieldsRejectInjectionOrMissingMetadata(){for(String prefix:List.of("","call; ","call\n ","/call "))assertThrows(IllegalArgumentException.class,()->new SourceContactCommand.Settings(prefix,"Philip","Philip",PestBagExchangeTest.MARKER,"Philip answered",List.of(),20));assertThrows(IllegalArgumentException.class,()->new SourceContactCommand.Settings("call ","Philip\nother","Philip",PestBagExchangeTest.MARKER,"Philip answered",List.of(),20));}
}
