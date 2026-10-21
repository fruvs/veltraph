package dev.veltraph.client.gui;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentsRuntimeTest {
    private final ExperimentSettings settings=new ExperimentSettings(0,1000,"Remember","Timer:","?","Claim Rewards","Rewards Claimed","Experiment Failed");
    private MenuSnapshot.Slot tile(int id,String name,String item,int count,boolean foil) {
        return new MenuSnapshot.Slot(id,name,List.of(),false,item,count,foil);
    }
    private MenuSnapshot menu(String title,int revision,String phase,MenuSnapshot.Slot... tiles) {
        var all=new ArrayList<>(List.of(tiles));all.add(tile(49,phase,"minecraft:clock",1,false));
        return new MenuSnapshot(7,title,all,revision);
    }
    @Test void stackCountsSetAscendingOrderAndUnrelatedRevisionCannotAcknowledgeClick() {
        var r=new ExperimentsRuntime();
        r.tick(menu("Ultrasequencer",1,"Remember",tile(12,"Tile","minecraft:red_dye",2,false),tile(31,"Tile","minecraft:blue_dye",1,false)),settings,0);
        var play=menu("Ultrasequencer",2,"Timer: 30",tile(12,"Tile","minecraft:gray_dye",1,false),tile(31,"Tile","minecraft:gray_dye",1,false));
        assertEquals(31,r.tick(play,settings,1).slot());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Ultrasequencer",3,"Timer: 29",play.slots().get(0),play.slots().get(1)),settings,2).kind());
        assertEquals(12,r.tick(menu("Ultrasequencer",4,"Timer: 28",tile(12,"Tile","minecraft:gray_dye",1,false),tile(31,"Clicked","minecraft:lime_dye",1,false)),settings,3).slot());
    }
    @Test void chainAllowsSameTileAgainOnlyAfterHighlightFallsAndResetsEveryRound() {
        var r=new ExperimentsRuntime();
        r.tick(menu("Chronomatron",1,"Remember",tile(15,"Tile","minecraft:red_wool",1,true)),settings,0);
        r.tick(menu("Chronomatron",2,"Remember",tile(15,"Tile","minecraft:red_wool",1,true)),settings,1);
        r.tick(menu("Chronomatron",3,"Remember",tile(15,"Tile","minecraft:red_wool",1,false)),settings,2);
        r.tick(menu("Chronomatron",4,"Remember",tile(15,"Tile","minecraft:red_wool",1,true)),settings,3);
        assertEquals(15,r.tick(menu("Chronomatron",5,"Timer:",tile(15,"Tile","minecraft:red_wool",1,false)),settings,4).slot());
        assertEquals(15,r.tick(menu("Chronomatron",6,"Timer:",tile(15,"Accepted","minecraft:red_wool",1,false)),settings,5).slot());
        r.tick(menu("Chronomatron",7,"Remember",tile(22,"Tile","minecraft:green_wool",1,true)),settings,6);
        assertEquals(22,r.tick(menu("Chronomatron",8,"Timer:",tile(22,"Tile","minecraft:green_wool",1,false)),settings,7).slot());
    }
    @Test void matchingUsesFullRewardIdentityAndLearnsRevealBeforeNextClick() {
        var r=new ExperimentsRuntime();
        assertEquals(10,r.tick(menu("Superpairs",1,"",tile(10,"?","minecraft:gray_stained_glass_pane",1,false),tile(11,"?","minecraft:gray_stained_glass_pane",1,false),tile(12,"?","minecraft:gray_stained_glass_pane",1,false)),settings,0).slot());
        assertEquals(11,r.tick(menu("Superpairs",2,"",tile(10,"500 XP","minecraft:experience_bottle",1,false),tile(11,"?","minecraft:gray_stained_glass_pane",1,false),tile(12,"?","minecraft:gray_stained_glass_pane",1,false)),settings,1).slot());
        r.tick(menu("Superpairs",3,"",tile(10,"500 XP","minecraft:experience_bottle",1,false),tile(11,"1000 XP","minecraft:experience_bottle",1,false),tile(12,"?","minecraft:gray_stained_glass_pane",1,false)),settings,2);
        assertEquals(12,r.tick(menu("Superpairs",4,"",tile(10,"?","minecraft:gray_stained_glass_pane",1,false),tile(11,"?","minecraft:gray_stained_glass_pane",1,false),tile(12,"?","minecraft:gray_stained_glass_pane",1,false)),settings,3).slot());
        assertEquals(10,r.tick(menu("Superpairs",5,"",tile(10,"?","minecraft:gray_stained_glass_pane",1,false),tile(11,"?","minecraft:gray_stained_glass_pane",1,false),tile(12,"500 XP","minecraft:experience_bottle",1,false)),settings,4).slot());
    }
    @Test void claimNeedsObservedOutcomeAndClosesOnlyAfterConfirmation() {
        var r=new ExperimentsRuntime();var reward=menu("Superpairs",1,"Claim Rewards");
        assertEquals(49,r.tick(reward,settings,0).slot());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(reward,settings,1).kind());
        assertEquals(ExperimentsRuntime.State.WAITING_ACK,r.state());
        assertEquals(ExperimentsRuntime.Kind.CLOSE,r.tick(menu("Superpairs",2,"Rewards Claimed"),settings,2).kind());
        assertEquals(ExperimentsRuntime.State.COMPLETE,r.state());
    }
    @Test void noRetryAfterTimeoutOrSessionReplacementAndNoPlayerInventoryCards() {
        var r=new ExperimentsRuntime();
        var menu=menu("Superpairs",1,"",tile(10,"?","minecraft:gray_stained_glass_pane",1,false));
        assertEquals(10,r.tick(menu,settings,0).slot());assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu,settings,1001).kind());
        assertEquals(ExperimentsRuntime.State.FAILED,r.state());
        r.reset();r.tick(menu,settings,0);
        r.tick(new MenuSnapshot(8,menu.title(),menu.slots(),2),settings,1);assertEquals(ExperimentsRuntime.State.FAILED,r.state());
        r.reset();assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Superpairs",1,"",new MenuSnapshot.Slot(10,"?",List.of(),true,"minecraft:gray_stained_glass_pane",1,false)),settings,0).kind());
    }
    @Test void duplicateNumbersAndMissingShowPhaseStopWithoutClick() {
        var r=new ExperimentsRuntime();r.tick(menu("Ultrasequencer",1,"Remember",tile(10,"Tile","minecraft:red_dye",1,false),tile(11,"Tile","minecraft:blue_dye",1,false)),settings,0);
        assertEquals(ExperimentsRuntime.State.FAILED,r.state());
        r.reset();assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Chronomatron",1,"Timer:",tile(10,"Tile","minecraft:red_wool",1,false)),settings,0).kind());
        assertEquals(ExperimentsRuntime.State.FAILED,r.state());
    }
    @Test void rewardVisualChangeWithoutOutcomeTimesOutAndNeverClaimsTwice() {
        var r=new ExperimentsRuntime();r.tick(menu("Superpairs",1,"Claim Rewards"),settings,0);
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Superpairs",2,"Processing reward"),settings,1).kind());
        assertEquals(ExperimentsRuntime.State.CLAIMING,r.state());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Superpairs",3,"Claim Rewards"),settings,1001).kind());
        assertEquals(ExperimentsRuntime.State.FAILED,r.state());
    }
    @Test void staleRewardMarkerCannotBeReusedAsNewOutcome() {
        var r=new ExperimentsRuntime();
        var old=menu("Superpairs",1,"Claim Rewards",tile(5,"Rewards Claimed","minecraft:paper",1,false));
        assertEquals(ExperimentsRuntime.Kind.CLICK,r.tick(old,settings,0).kind());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(old,settings,1).kind());
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Superpairs",2,"Processing",tile(5,"Rewards Claimed","minecraft:paper",1,false)),settings,2).kind());
        assertNotEquals(ExperimentsRuntime.State.COMPLETE,r.state());
    }
    @Test void labelsAreNormalizedAndEmptyFormattingCannotMatchEverything() {
        assertThrows(IllegalArgumentException.class,()->new ExperimentSettings(0,1000,"§a","Timer:","?","Claim Rewards","Rewards Claimed","Experiment Failed"));
        var normalized=new ExperimentSettings(0,1000,"§aRemember","Timer:","§b?","Claim Rewards","Rewards Claimed","Experiment Failed");
        assertEquals("Remember",normalized.showLabel());assertEquals("?",normalized.hiddenLabel());
    }
    @Test void closedMenuIsFailureAndReleaseDropsPendingSession() {
        var r=new ExperimentsRuntime();r.tick(menu("Superpairs",1,"",tile(10,"?","minecraft:gray_stained_glass_pane",1,false)),settings,0);
        r.tick(null,settings,1);assertEquals(ExperimentsRuntime.State.FAILED,r.state());
        r.releaseSession();r.reset();
        assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu("Chronomatron",1,"Timer:",tile(10,"Tile","minecraft:red_wool",1,false)),settings,2).kind());
        assertEquals(ExperimentsRuntime.State.FAILED,r.state());
    }
}
