package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorServedHistoryTestSupport extends VisitorHistoryTestSupport {
    List<VisitorServedHistoryJournal.Stack> inventory(int wheat){var list=new ArrayList<VisitorServedHistoryJournal.Stack>();for(int i=0;i<36;i++)list.add(new VisitorServedHistoryJournal.Stack("",0));list.set(0,new VisitorServedHistoryJournal.Stack("wheat full native component identity",wheat));list.set(1,new VisitorServedHistoryJournal.Stack("sword full native component identity",1));return list;}
    VisitorServedHistoryJournal.Request serveRequest(){return new VisitorServedHistoryJournal.Request(context(),"Beth",menu,4,5,10,0,Map.of("wheat full native component identity",3),inventory(8),true);}
    VisitorServedHistoryJournal journal(){var j=new VisitorServedHistoryJournal(1000);assertTrue(j.begin(serveRequest(),context(),0));return j;}
    VisitorServedHistoryJournal.InventoryReceipt consumed(long sequence){return new VisitorServedHistoryJournal.InventoryReceipt(context(),sequence,10,1,inventory(5),true,true,true);}
    VisitorServedHistoryJournal.MenuReceipt ack(long sequence){return new VisitorServedHistoryJournal.MenuReceipt(context(),menu,4,6,sequence,10,1,true,false,true,true);}
}
