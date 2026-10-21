package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceLeavingActionMappingTest {
    @Test void sourceFifteenBranchRequestsLeftClickRatherThanSlotOrRotation(){var l=walking();for(int i=1;i<15;i++)l.event(false,false,false,false,1,n->0,()->1,()->true);assertEquals(GhostSourceLeavingLifecycle.Action.GATED_LEFT_CLICK,l.event(false,false,false,false,1,n->0,()->1,()->true).action());}
    @Test void sourceOrdinaryRandomBranchRequestsJumpRatherThanRandomLook(){var l=walking();assertEquals(GhostSourceLeavingLifecycle.Action.GATED_JUMP,l.event(false,false,false,false,1,n->0,()->0,()->false).action());}
    private static GhostSourceLeavingLifecycle walking(){var l=new GhostSourceLeavingLifecycle();l.begin(java.util.List.of(new GhostSourceWalkFrontier.Cell(1,2,3)),n->0);l.captured(true,GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT,true,0,n->0);return l;}
}
