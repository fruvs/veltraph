package dev.veltraph.client.fishing.trash;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FishingTrashRecoveryTest {
    private final FishingTrashProfile profile=new FishingTrashProfile(List.of(new FishingTrashProfile.Rule("minecraft:enchanted_book","Fixture junk")),0L,1000L,60000L,1000L);
    private final FishingTrashDiscard.Context context=new FishingTrashDiscard.Context(new Object(),new Object(),new Object(),new Object(),new Object());
    private FishingTrashProfile.Stack stack(String id,String name,int count,String identity){return new FishingTrashProfile.Stack(id,name,List.of(),count,identity);}
    private FishingTrashDiscard.Observation observation(int revision,Map<Integer,FishingTrashProfile.Stack> rows){return new FishingTrashDiscard.Observation(context,0,revision,true,rows,Set.of(9,10,11,12,13));}
    private Map<Integer,FishingTrashProfile.Stack> rows(){var rows=new TreeMap<Integer,FishingTrashProfile.Stack>();for(int i=9;i<14;i++)rows.put(i,stack("minecraft:enchanted_book","Fixture junk",3,"components"+i));rows.put(36,stack("minecraft:fishing_rod","Rod",1,"rod"));return rows;}
    private FishingTrashProfile.Stack empty(){return stack("minecraft:air","Air",0,"empty");}
    private FishingTrashDiscard.Receipt receipt(int revision,long sequence,int slot,FishingTrashProfile.Stack value){return new FishingTrashDiscard.Receipt(context,0,revision,sequence,Map.of(slot,value),true);}
    @Test void nativeTypeAndExactDeclaredTextPairIsMandatory() {
        assertTrue(profile.candidate(stack("minecraft:enchanted_book","§dFixture junk",1,"enchanted")));
        assertFalse(profile.candidate(stack("minecraft:book","Fixture junk",1,"ordinary")));
        assertFalse(profile.candidate(stack("minecraft:enchanted_book","Valuable book",1,"sameNative")));
        assertTrue(profile.candidate(new FishingTrashProfile.Stack("minecraft:enchanted_book","Book",List.of("§aFixture junk IV"),1,"lore")));
        assertFalse(profile.candidate(stack("minecraft:enchanted_book","Fixture junk",0,"empty")));
        assertEquals(profile,FishingTrashProfile.parse(new Gson().toJson(profile)));
        assertThrows(IllegalArgumentException.class,()->FishingTrashProfile.parse("{}"));
        assertThrows(IllegalArgumentException.class,()->new FishingTrashProfile.Rule("minecraft:diamond_sword","Fixture junk"));
        assertThrows(IllegalArgumentException.class,()->new FishingTrashProfile.Rule("minecraft:enchanted_book",""));
    }
    @Test void exactFiveStackTriggerPreservesProtectedEquipmentAndStableMenuOrder() {
        var tooFew=rows();tooFew.remove(13);assertFalse(new FishingTrashDiscard().start(observation(1,tooFew),profile,0,0));
        var value=rows();value.put(5,stack("minecraft:enchanted_book","Fixture junk",1,"equipped"));
        var runtime=new FishingTrashDiscard();assertTrue(runtime.start(observation(1,value),profile,0,0));
        assertEquals(new FishingTrashDiscard.Action(9,1),runtime.tick(observation(1,value),null,0).orElseThrow());
    }
    @Test void localPredictionStaleReceiptAndPartialStackReceiptCannotAcknowledgeWholeStackDrop() {
        var value=rows();var runtime=new FishingTrashDiscard();runtime.start(observation(5,value),profile,12,0);
        assertEquals(9,runtime.tick(observation(5,value),null,0).orElseThrow().slot());
        value.put(9,empty());
        assertTrue(runtime.tick(observation(5,value),null,10).isEmpty());
        assertTrue(runtime.tick(observation(6,value),receipt(6,12,9,empty()),20).isEmpty());
        assertTrue(runtime.tick(observation(6,value),receipt(5,13,9,empty()),30).isEmpty());
        assertTrue(runtime.tick(observation(6,value),receipt(6,13,9,stack("minecraft:enchanted_book","Fixture junk",2,"components9")),40).isEmpty());
        assertEquals(FishingTrashDiscard.State.WAIT_RECEIPT,runtime.state());
        assertEquals(10,runtime.tick(observation(6,value),receipt(6,13,9,empty()),50).orElseThrow().slot());
    }
    @Test void changedUnrelatedInventoryOrNativeContextStopsWithoutRetry() {
        var value=rows();var runtime=new FishingTrashDiscard();runtime.start(observation(1,value),profile,0,0);runtime.tick(observation(1,value),null,0);
        value.put(9,empty());value.put(36,stack("minecraft:diamond","Diamond",1,"foreignManualItem"));
        assertTrue(runtime.tick(observation(2,value),receipt(2,1,9,empty()),10).isEmpty());assertEquals(FishingTrashDiscard.State.FAILED,runtime.state());
        var other=new FishingTrashDiscard();other.start(observation(1,rows()),profile,0,0);
        var foreign=new FishingTrashDiscard.Context(context.player(),new Object(),context.connection(),context.menu(),context.screen());
        assertTrue(other.tick(new FishingTrashDiscard.Observation(foreign,0,1,true,rows(),Set.of(9,10,11,12,13)),null,0).isEmpty());
        assertEquals(FishingTrashDiscard.State.FAILED,other.state());
    }
    @Test void requestTimeoutDoesNotIssueRepeatClickOrResume() {
        var runtime=new FishingTrashDiscard();var value=rows();runtime.start(observation(1,value),profile,0,0);runtime.tick(observation(1,value),null,0);
        assertTrue(runtime.tick(observation(1,value),null,1001).isEmpty());assertEquals(FishingTrashDiscard.State.FAILED,runtime.state());
        assertTrue(runtime.tick(observation(2,value),receipt(2,1,9,empty()),1010).isEmpty());
    }
    @Test void foreignMenuContextAndNonemptyCursorReceiptsCannotAuthorizeAnotherDrop() {
        var value=rows();var runtime=new FishingTrashDiscard();runtime.start(observation(1,value),profile,0,0);runtime.tick(observation(1,value),null,0);value.put(9,empty());
        var foreign=new FishingTrashDiscard.Context(context.player(),context.world(),new Object(),context.menu(),context.screen());
        assertTrue(runtime.tick(observation(2,value),new FishingTrashDiscard.Receipt(foreign,0,2,1,Map.of(9,empty()),true),10).isEmpty());
        assertTrue(runtime.tick(observation(2,value),new FishingTrashDiscard.Receipt(context,3,2,1,Map.of(9,empty()),true),20).isEmpty());
        assertTrue(runtime.tick(observation(2,value),new FishingTrashDiscard.Receipt(context,0,2,1,Map.of(9,empty()),false),30).isEmpty());
        assertEquals(FishingTrashDiscard.State.WAIT_RECEIPT,runtime.state());
        assertTrue(runtime.tick(new FishingTrashDiscard.Observation(context,0,2,false,value,Set.of(9,10,11,12,13)),receipt(2,1,9,empty()),40).isEmpty());
        assertEquals(FishingTrashDiscard.State.FAILED,runtime.state());
    }
    @Test void allFiveAcknowledgedDropsCompleteWithExactlyOneWholeStackClickEach() {
        var value=rows();var runtime=new FishingTrashDiscard();runtime.start(observation(1,value),profile,0,0);
        var action=runtime.tick(observation(1,value),null,0);long sequence=0;
        for(int slot=9;slot<14;slot++){assertEquals(slot,action.orElseThrow().slot());assertEquals(1,action.orElseThrow().button());value.put(slot,empty());action=runtime.tick(observation(slot,value),receipt(slot,++sequence,slot,empty()),sequence*10);}
        assertTrue(action.isEmpty());assertEquals(FishingTrashDiscard.State.COMPLETE,runtime.state());
    }
    @Test void cliffScanPreservesBoundsScoringSparseCandidatesAndPathFallback() {
        var origin=new FishingTrashCliffSelector.Pos(0,70,0);var valid=new HashSet<FishingTrashCliffSelector.Pos>();for(int x=-50;x<=50;x+=5)valid.add(origin.offset(x,0,0));
        var reads=new int[]{0};
        var facts=new FishingTrashCliffSelector.Facts(){
            public boolean passable(FishingTrashCliffSelector.Pos p){return true;}
            public boolean standable(FishingTrashCliffSelector.Pos p){reads[0]++;return valid.contains(p);}
            public FishingTrashCliffSelector.Kind kind(FishingTrashCliffSelector.Pos p){return FishingTrashCliffSelector.Kind.OTHER;}
            public double yawDifferenceToCenter(FishingTrashCliffSelector.Pos p){return 0;}
        };
        var candidates=FishingTrashCliffSelector.scan(facts,origin,new Random(0));assertEquals(163216,reads[0]);assertEquals(5,candidates.size());
        assertEquals(origin,candidates.getFirst().position());assertEquals(List.of(FishingTrashCliffSelector.Direction.WEST),candidates.getFirst().throwDirections());
        var attempts=new ArrayList<FishingTrashCliffSelector.Candidate>();var chosen=FishingTrashCliffSelector.choose(candidates,c->{attempts.add(c);return attempts.size()==3;});
        assertEquals(candidates.get(2),chosen.orElseThrow());assertEquals(candidates.subList(0,3),attempts);
    }
    @Test void incrementalNativeScanPreservesSourceCandidateOrderAndRandomCalls() {
        var origin=new FishingTrashCliffSelector.Pos(0,70,0);
        var facts=new FishingTrashCliffSelector.Facts(){
            public boolean passable(FishingTrashCliffSelector.Pos p){return true;}
            public boolean standable(FishingTrashCliffSelector.Pos p){return p.y()==70&&p.x()%9==0&&p.z()%9==0;}
            public FishingTrashCliffSelector.Kind kind(FishingTrashCliffSelector.Pos p){return FishingTrashCliffSelector.Kind.OTHER;}
            public double yawDifferenceToCenter(FishingTrashCliffSelector.Pos p){return 0;}
        };
        var expected=FishingTrashCliffSelector.scan(facts,origin,new Random(17));var incremental=new FishingTrashCliffSelector.Scan(origin,new Random(17));
        assertThrows(IllegalStateException.class,incremental::ranked);
        while(!incremental.complete())incremental.advance(facts,2048);
        assertEquals(expected,incremental.ranked());
    }
    @Test void cliffTwoColumnEightSampleRuleRejectsAnObstructedFaceAndKeepsRecoveredPenalties() {
        var origin=new FishingTrashCliffSelector.Pos(0,70,0);
        var blocked=Set.of(origin.offset(0,-5,1),origin.offset(0,-5,-1),origin.offset(-1,-5,0));
        var facts=new FishingTrashCliffSelector.Facts(){
            public boolean passable(FishingTrashCliffSelector.Pos p){return !blocked.contains(p);}
            public boolean standable(FishingTrashCliffSelector.Pos p){return p.equals(origin);}
            public FishingTrashCliffSelector.Kind kind(FishingTrashCliffSelector.Pos p){return p.equals(origin)?FishingTrashCliffSelector.Kind.LAVA:p.equals(origin.offset(0,-1,0))?FishingTrashCliffSelector.Kind.SOUL_SAND:FishingTrashCliffSelector.Kind.OTHER;}
            public double yawDifferenceToCenter(FishingTrashCliffSelector.Pos p){return 90;}
        };
        var c=FishingTrashCliffSelector.scan(facts,origin,new Random(0)).getFirst();
        assertEquals(List.of(FishingTrashCliffSelector.Direction.EAST),c.clearDirections());
        assertTrue(c.score()>=63&&c.score()<65,"(4-1)*10 + lava10 + soul20 + yaw90/30 + jitter[0,2)");
    }
}
