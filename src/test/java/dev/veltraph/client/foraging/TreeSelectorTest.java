package dev.veltraph.client.foraging;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TreeSelectorTest {
    @Test void filtersUnavailableTreesBeforeRanking() {
        var near=new BlockPos(1,1,0);var far=new BlockPos(4,1,0);
        var candidates=List.of(new TreeSelector.Candidate(near,true,false,false),new TreeSelector.Candidate(far,true,false,false),
                new TreeSelector.Candidate(new BlockPos(0,1,0),false,false,false));
        assertEquals(List.of(far),TreeSelector.ranked(candidates,new Vec3(0,1,0),0,Set.of(near)));
    }
    @Test void turningCostBreaksEqualDistanceTie() {
        assertTrue(TreeSelector.score(new BlockPos(0,1,3),new Vec3(.5,1,.5),0)
                <TreeSelector.score(new BlockPos(0,1,-3),new Vec3(.5,1,.5),0));
    }
    @Test void recoveredRouteAndRegionTermsCanOutweighDistance() {
        var p=new Vec3(0,70,0);
        var near=new TreeSelector.Candidate(new BlockPos(0,70,1),true,false,false,1,100,0,0,false,false,0);
        var far=new TreeSelector.Candidate(new BlockPos(0,70,5),true,false,false,4,0,0,0,false,false,0);
        assertEquals(List.of(far.trunk(),near.trunk()),TreeSelector.ranked(List.of(near,far),p,0,Set.of()));
        var danger=new TreeSelector.Candidate(far.trunk(),true,false,false,4,0,0,0,true,true,10);
        assertEquals(270,TreeSelector.score(danger,p,0)-TreeSelector.score(far,p,0),1e-9);
    }
}
