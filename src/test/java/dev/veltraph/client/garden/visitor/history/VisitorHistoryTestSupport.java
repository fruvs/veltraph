package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryTestSupport {
    @TempDir Path temp;
    final Object player=new Object(),world=new Object(),connection=new Object(),menu=new Object();
    final VisitorHistoryScope scope=new VisitorHistoryScope(UUID.fromString("11111111-1111-1111-1111-111111111111"),"confirmed-profile-A","confirmed-Garden-A");
    VisitorHistoryContext context(){return new VisitorHistoryContext(scope,player,world,connection,1,1,"declared pure test confirmed producer");}
    VisitorLogbookReader.Bindings bindings(){return new VisitorLogbookReader.Bindings("TEST logbook","TEST next","TEST page",List.of("TEST excluded1","TEST excluded2"),List.of("TEST unseen1","TEST unseen2"),Set.of("minecraft:gray_stained_glass_pane"),"declared test vocabulary, no real server caption claim");}
    VisitorLogbookReader reader(){return new VisitorLogbookReader(bindings(),4,1000);}
    VisitorLogbookReader.Row visitor(int slot,String name,boolean seen){return new VisitorLogbookReader.Row(slot,true,true,"minecraft:player_head",name,seen?List.of("TEST served metadata"):List.of("TEST unseen1"));}
    VisitorLogbookReader.Row next(int page){return new VisitorLogbookReader.Row(53,true,true,"minecraft:arrow","TEST next",List.of("TEST page "+(page+1)));}
    VisitorLogbookReader.NativeRequest request(long sequence,long time,int slot,boolean first){return new VisitorLogbookReader.NativeRequest(context(),menu,4,sequence,time,slot,first);}
    VisitorLogbookReader.Page page(long seq,long request,long time,int revision,List<VisitorLogbookReader.Row> rows){return new VisitorLogbookReader.Page(context(),menu,4,revision,"TEST logbook",true,true,seq,time,request,rows);}
    VisitorLogbookReader.CompletedBook book(boolean seen,long now){var r=reader();assertTrue(r.start(request(1,now,-1,true),now));r.observe(page(2,1,now,0,List.of(visitor(10,"Beth",seen))),context(),now);return r.completion().orElseThrow();}
    VisitorHistoryStore store(){return new VisitorHistoryStore(temp.resolve("history.json"));}
}
