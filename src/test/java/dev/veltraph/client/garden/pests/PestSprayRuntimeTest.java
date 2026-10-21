package dev.veltraph.client.garden.pests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PestSprayRuntimeTest {
    static final PestSprayRuntime.Settings C=new PestSprayRuntime.Settings(true,1,1,100,100,false,8000);
    static PestSprayRuntime.Snapshot s(int count,int selected){return new PestSprayRuntime.Snapshot(true,true,true,true,3,"tool","material",count,true,selected);}
    static class Actions implements PestSprayRuntime.Actions {
        int selections,uses,releases;
        public boolean select(int slot){selections++;return true;}
        public boolean use(PestSprayRuntime.Snapshot s){uses++;return true;}
        public void release(){releases++;}
    }
    @Test void waitsForBothServerEvidenceAndDoesNotDuplicate(){
        var a=new Actions();var r=new PestSprayRuntime(a);assertTrue(r.start(s(4,0),C,10));
        r.message("SPRAYONATOR! You sprayed Plot - 1");r.tick(s(4,0),C,11);r.tick(s(4,3),C,12);
        r.tick(s(3,3),C,13);assertEquals(PestSprayRuntime.State.WAITING_ACK,r.state());
        for(int i=14;i<30;i++)r.tick(s(3,3),C,i);
        assertEquals(1,a.uses);r.message("SPRAYONATOR! You sprayed Plot - 1");r.tick(s(3,3),C,30);
        assertEquals(PestSprayRuntime.State.COMPLETE,r.state());assertEquals(1,a.releases);
    }
    @Test void chatAloneTimesOut(){
        var a=new Actions();var r=new PestSprayRuntime(a);r.start(s(4,0),C,0);r.tick(s(4,0),C,1);r.tick(s(4,3),C,2);
        r.message("SPRAYONATOR! You sprayed Plot - 1");r.tick(s(4,3),C,8000);
        assertEquals(PestSprayRuntime.State.FAILED,r.state());assertEquals(1,a.uses);assertEquals(1,a.releases);
    }
    @Test void rejectsValueAndOverflowBeforeAnyAction(){
        var a=new Actions();var r=new PestSprayRuntime(a);
        assertFalse(r.start(s(4,0),new PestSprayRuntime.Settings(true,1,2,100,199,false,8000),0));
        assertFalse(r.start(s(4,0),new PestSprayRuntime.Settings(true,1,2,Long.MAX_VALUE,Long.MAX_VALUE,false,8000),0));
        assertEquals(0,a.selections);assertEquals(0,a.uses);
    }
    @Test void settingsAndSelectionChangesCancelBeforeUse(){
        var a=new Actions();var r=new PestSprayRuntime(a);r.start(s(4,0),C,0);r.tick(s(4,0),C,1);r.tick(s(4,2),C,2);
        assertEquals(PestSprayRuntime.State.CANCELLED,r.state());assertEquals(0,a.uses);
        r.start(s(4,0),C,3);r.tick(s(4,0),new PestSprayRuntime.Settings(false,1,1,100,100,false,8000),4);
        assertEquals(PestSprayRuntime.State.CANCELLED,r.state());assertEquals(2,a.releases);
    }
    @Test void recoveredRejectionsDoNotRetryOrReportSuccess(){
        for(String message:new String[]{"This ability is on cooldown", "You don't have any Compost", "This plot was sprayed with that item recently! Try again soon!"}){
            var a=new Actions();var r=new PestSprayRuntime(a);r.start(s(4,0),C,0);r.tick(s(4,0),C,1);r.tick(s(4,3),C,2);
            r.message(message);r.tick(s(3,3),C,3);assertEquals(PestSprayRuntime.State.FAILED,r.state());assertEquals(1,a.uses);
        }
    }
    @Test void excessiveConsumptionFails(){
        var a=new Actions();var r=new PestSprayRuntime(a);r.start(s(4,0),C,0);r.tick(s(4,0),C,1);r.tick(s(4,3),C,2);
        r.message("SPRAYONATOR! You sprayed Plot - 1");r.tick(s(2,3),C,3);assertEquals(PestSprayRuntime.State.FAILED,r.state());
    }
    @Test void allPlotRectanglesHaveUniqueCentersAndEdges(){
        assertEquals(25,GardenPlotBounds.ALL.size());
        for(var b:GardenPlotBounds.ALL){assertEquals(b.id(),GardenPlotBounds.at((b.minX()+b.maxX())/2.0,(b.minZ()+b.maxZ())/2.0).orElse(-1));
            assertTrue(b.contains(b.minX(),b.minZ()));assertFalse(b.contains(b.maxX(),b.minZ()));}
        assertFalse(GardenPlotBounds.contains(0,Double.NaN,0));
        assertTrue(GardenPlotBounds.sourceSprayContains(1,0,60,-96));
        assertTrue(GardenPlotBounds.sourceSprayContains(1,-49,91.9,-96));
        assertFalse(GardenPlotBounds.sourceSprayContains(1,0,59.9,-96));
        assertFalse(GardenPlotBounds.sourceSprayContains(1,0,92,-96));
        assertTrue(GardenContestSignal.active(java.util.List.of("§eJacob's Contest")));
        assertFalse(GardenContestSignal.active(java.util.List.of("Next Jacob's Contest", "Jacob's Contest Rewards")));
    }
}
