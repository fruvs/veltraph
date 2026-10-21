package dev.veltraph.client.garden.jacob.mc;

import dev.veltraph.client.garden.jacob.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class McJacobSidebarRowsTest {
    private final Scoreboard board=new Scoreboard();
    private final Objective objective=board.addObjective("native_jacob",ObjectiveCriteria.DUMMY,Component.literal("SKYBLOCK"),ObjectiveCriteria.RenderType.INTEGER,false,null);
    private ScoreAccess score(String owner,int value){var score=board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner),objective);score.set(value);return score;}
    @Test void exactVNativeScoreboardOrderBugIsCorrectedBeforeAdjacentRowParser(){
        score("Garden",4);score("Jacob's Contest",3);score("Wheat 0m10s",2);
        var oldRows=board.listPlayerScores(objective).stream().map(PlayerScoreEntry::owner).toList();
        assertEquals(List.of("Jacob's Contest","Garden","Wheat 0m10s"),oldRows);
        assertFalse(JacobContestObservation.read(oldRows).clockKnown());
        var observed=JacobContestObservation.read(McJacobSidebarRows.read(board,objective));
        assertTrue(observed.clockKnown());assertEquals(10,observed.remainingSeconds());
    }
    @Test void tiesFollowStableSourceCaseInsensitiveOwnerOrdering(){
        score("zeta",10);score("BRAVO",10);score("alpha",10);
        assertEquals(List.of("alpha","BRAVO","zeta"),McJacobSidebarRows.read(board,objective));
    }
    @Test void hiddenNativeRowsAreFilteredBeforeActualDisplayLimit(){
        for(int i=0;i<20;i++)score("#hidden"+i,100+i);
        score("Garden",4);score("Jacob's Contest",3);score("Wheat 0m10s",2);
        assertEquals(List.of("Garden","Jacob's Contest","Wheat 0m10s"),McJacobSidebarRows.read(board,objective));
    }
    @Test void offscreenNativeRowsCannotAuthorizeAContestWindow(){
        for(int i=0;i<15;i++)score("Visible filler "+i,100-i);
        score("Jacob's Contest",1);score("Wheat 0m10s",0);
        var rows=McJacobSidebarRows.read(board,objective);assertEquals(15,rows.size());assertFalse(JacobContestObservation.read(rows).active());
    }
    @Test void actualDisplayedOwnerNameAndTeamFormattingArePreservedThenStripped(){
        score("location_alias",4).display(Component.literal(" Garden "));
        score("header_alias",3).display(Component.literal("Jacob's Contest"));
        score("crop_alias",2).display(Component.literal("Wheat 0m10s"));
        var team=board.addPlayerTeam("native_header");team.setPlayerPrefix(Component.literal("§a"));team.setPlayerSuffix(Component.literal("§r"));board.addPlayerToTeam("header_alias",team);
        var rows=McJacobSidebarRows.read(board,objective);assertEquals(List.of("Garden","Jacob's Contest","Wheat 0m10s"),rows);assertEquals(10,JacobContestObservation.read(rows).remainingSeconds());
    }
    @Test void malformedImmediateRowDoesNotBorrowAnUnrelatedClockAfterOrdering(){
        score("Jacob's Contest",4);score("Garden",3);score("Wheat 0m10s",2);
        var observed=JacobContestObservation.read(McJacobSidebarRows.read(board,objective));assertTrue(observed.active());assertFalse(observed.clockKnown());assertEquals(-1,observed.remainingSeconds());
    }
    @Test void nativeRowIngressPreservesActualTwentyCallbackDelayAndStrictCutoff(){
        score("Garden",4);score("Jacob's Contest",3);score("Wheat 0m10s",2);var tracker=new JacobContestTracker();var scope=new JacobContestTracker.Scope(new Object(),new Object(),new Object(),"native-farm");
        for(long tick=0;tick<19;tick++){tracker.tick(scope,true,McJacobSidebarRows.read(board,objective),tick);assertFalse(tracker.consumeTrapWindow(scope,tick,true,true,1,11));}
        tracker.tick(scope,true,McJacobSidebarRows.read(board,objective),19);assertFalse(tracker.consumeTrapWindow(scope,19,true,true,1,10));assertTrue(tracker.consumeTrapWindow(scope,19,true,true,1,11));assertFalse(tracker.consumeTrapWindow(scope,19,true,true,1,11));
    }
    @Test void unavailableOrForeignObjectiveCannotSupplyNativeRows(){
        assertTrue(McJacobSidebarRows.read(null,objective).isEmpty());assertTrue(McJacobSidebarRows.read(board,null).isEmpty());assertTrue(McJacobSidebarRows.read(new Scoreboard(),objective).isEmpty());
    }
}
