package dev.veltraph.client.combat.quiver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuiverNpcIngressRuntimeTest {
    private static final QuiverNpcIngressRuntime.Contract CONTRACT=new QuiverNpcIngressRuntime.Contract("Local Arrow Merchant","minecraft:villager",16,20,"Quiver Shop");
    private static QuiverNpcIngressRuntime.Candidate npc(String id){return new QuiverNpcIngressRuntime.Candidate(id,"Local Arrow Merchant","minecraft:villager",true,true,5);}
    private static QuiverNpcIngressRuntime started(){return new QuiverNpcIngressRuntime(CONTRACT,List.of(npc("original")),0);}
    private static QuiverNpcIngressRuntime.Observation observed(List<QuiverNpcIngressRuntime.Candidate> candidates,boolean cursor,boolean unchanged,String screen,boolean container,boolean reachable,boolean visible,boolean hit){return new QuiverNpcIngressRuntime.Observation(candidates,cursor,unchanged,screen,container,reachable,visible,hit);}
    private static QuiverNpcIngressRuntime.Observation clear(boolean reachable,boolean visible,boolean hit){return observed(List.of(npc("original")),true,true,null,false,reachable,visible,hit);}
    @Test void reachableVisibleRaycastRequiredBeforeSingleInteractionAndExactMenu(){
        var r=started();assertEquals(QuiverNpcIngressRuntime.Action.APPROACH,r.tick(clear(false,true,false),1).orElseThrow());
        assertEquals(QuiverNpcIngressRuntime.Action.APPROACH,r.tick(clear(true,false,true),2).orElseThrow());
        assertEquals(QuiverNpcIngressRuntime.Action.AIM,r.tick(clear(true,true,false),3).orElseThrow());
        assertEquals(QuiverNpcIngressRuntime.Action.INTERACT,r.tick(clear(true,true,true),4).orElseThrow());
        assertTrue(r.tick(clear(true,true,true),5).isEmpty());r.applied(true);
        for(int i=6;i<19;i++)assertTrue(r.tick(clear(true,true,true),i).isEmpty());
        r.tick(observed(List.of(),true,true,"Quiver Shop",true,false,false,false),19);
        assertEquals(QuiverNpcIngressRuntime.State.OPEN,r.state());
    }
    @Test void missingDuplicateAndGenericIdentityFailBeforeApproach(){
        for(var list:List.of(List.<QuiverNpcIngressRuntime.Candidate>of(),List.of(npc("one"),npc("two")),
                List.of(new QuiverNpcIngressRuntime.Candidate("generic","Local Arrow Merchant","minecraft:villager",false,true,5)),
                List.of(new QuiverNpcIngressRuntime.Candidate("wrong-type","Local Arrow Merchant","minecraft:zombie",true,true,5)),
                List.of(new QuiverNpcIngressRuntime.Candidate("far","Local Arrow Merchant","minecraft:villager",true,true,17)))){
            var r=new QuiverNpcIngressRuntime(CONTRACT,list,0);assertEquals(QuiverNpcIngressRuntime.State.FAILED,r.state());assertTrue(r.tick(clear(true,true,true),1).isEmpty());
        }
    }
    @Test void replacementDisappearanceRenameDuplicateAndOutOfBoundsCancel(){
        for(var list:List.of(List.<QuiverNpcIngressRuntime.Candidate>of(),List.of(npc("replacement")),List.of(npc("original"),npc("duplicate")),
                List.of(new QuiverNpcIngressRuntime.Candidate("original","Renamed","minecraft:villager",true,true,5)),
                List.of(new QuiverNpcIngressRuntime.Candidate("original","Local Arrow Merchant","minecraft:villager",true,true,17)))){
            var r=started();assertTrue(r.tick(observed(list,true,true,null,false,true,true,true),1).isEmpty());assertEquals(QuiverNpcIngressRuntime.State.CANCELLED,r.state());
        }
    }
    @Test void matchingMenuBeforeInteractionIsNotOwned(){var r=started();r.tick(observed(List.of(npc("original")),true,true,"Quiver Shop",true,true,true,true),1);assertEquals(QuiverNpcIngressRuntime.State.CANCELLED,r.state());}
    @Test void wrongTitleOrNoncontainerAfterInteractionIsNotOwned(){for(boolean container:new boolean[]{true,false}){var r=started();r.tick(clear(true,true,true),1);r.applied(true);r.tick(observed(List.of(npc("original")),true,true,container?"Other Shop":"Quiver Shop",container,true,true,true),2);assertEquals(QuiverNpcIngressRuntime.State.CANCELLED,r.state());}}
    @Test void cursorOrInventoryChangesCancelBeforeInteraction(){for(boolean cursor:new boolean[]{true,false}){var r=started();r.tick(observed(List.of(npc("original")),cursor,!cursor,null,false,true,true,true),1);assertEquals(QuiverNpcIngressRuntime.State.CANCELLED,r.state());}}
    @Test void menuArrivalCannotBypassInventoryOwnership(){var r=started();r.tick(clear(true,true,true),1);r.applied(true);r.tick(observed(List.of(),true,false,"Quiver Shop",true,true,true,true),2);assertEquals(QuiverNpcIngressRuntime.State.CANCELLED,r.state());}
    @Test void timeoutAfterSendDoesNotRetry(){var r=started();r.tick(clear(true,true,true),1);r.applied(true);assertTrue(r.tick(clear(true,true,true),21).isEmpty());assertEquals(QuiverNpcIngressRuntime.State.FAILED,r.state());assertTrue(r.tick(clear(true,true,true),22).isEmpty());}
    @Test void rejectedNativeInteractionFailsWithoutRetry(){var r=started();r.tick(clear(true,true,true),1);r.applied(false);assertEquals(QuiverNpcIngressRuntime.State.FAILED,r.state());assertTrue(r.tick(clear(true,true,true),2).isEmpty());}
    @Test void unknownIdentityAndUnboundedDistanceCannotConstructContract(){assertThrows(IllegalArgumentException.class,()->new QuiverNpcIngressRuntime.Contract("","minecraft:villager",16,20,"Quiver Shop"));assertThrows(IllegalArgumentException.class,()->new QuiverNpcIngressRuntime.Contract("Merchant","",16,20,"Quiver Shop"));assertThrows(IllegalArgumentException.class,()->new QuiverNpcIngressRuntime.Contract("Merchant","minecraft:villager",33,20,"Quiver Shop"));assertThrows(IllegalArgumentException.class,()->new QuiverNpcIngressRuntime.Contract("Merchant","minecraft:villager",16,2401,"Quiver Shop"));}
    @Test void prolongedTravelDoesNotInteractUntilNativeRangeVisibilityAndRayAllAgree(){
        var r=started();
        for(int tick=1;tick<=17;tick++)assertEquals(QuiverNpcIngressRuntime.Action.APPROACH,r.tick(clear(false,true,false),tick).orElseThrow());
        assertEquals(QuiverNpcIngressRuntime.Action.AIM,r.tick(clear(true,true,false),18).orElseThrow());
        assertEquals(QuiverNpcIngressRuntime.Action.INTERACT,r.tick(clear(true,true,true),19).orElseThrow());
        r.applied(true);
        r.tick(observed(List.of(),true,true,"Quiver Shop",true,false,false,false),20);
        assertEquals(QuiverNpcIngressRuntime.State.OPEN,r.state());
    }
    @Test void approachProgressCannotExtendOriginalMenuDeadline(){
        var r=started();
        for(int tick=1;tick<=20;tick++)assertEquals(QuiverNpcIngressRuntime.Action.APPROACH,r.tick(clear(false,true,false),tick).orElseThrow());
        assertTrue(r.tick(clear(true,true,true),21).isEmpty());
        assertEquals(QuiverNpcIngressRuntime.State.FAILED,r.state());
    }
    @Test void toolLeaseCannotChangeWhileWalkingToNpc(){
        var r=started();assertEquals(QuiverNpcIngressRuntime.Action.APPROACH,r.tick(clear(false,true,false),1).orElseThrow());
        assertTrue(r.tick(observed(List.of(npc("original")),true,false,null,false,false,true,false),2).isEmpty());
        assertEquals(QuiverNpcIngressRuntime.State.CANCELLED,r.state());
        assertTrue(r.tick(clear(true,true,true),3).isEmpty());
    }

}
