package dev.veltraph.client.fishing;

import dev.veltraph.client.navigation.SharedMovementPolicy;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FishingTravelIntentTest {
    @Test void onlyBetweenJobOutboundTripsVary(){
        var general=java.util.Arrays.stream(FishingTravelIntent.values()).filter(v->v.purpose()==SharedMovementPolicy.Purpose.GENERAL_TRAVEL).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(FishingTravelIntent.TRASH_CLIFF,FishingTravelIntent.VANESSA_APPROACH),general);
    }
    @Test void SavedCastAndStandingCursorsRemainExactAfterDeviceApproach(){
        for(var intent:java.util.List.of(FishingTravelIntent.TRASH_RETURN,FishingTravelIntent.RAIN_RETURN,FishingTravelIntent.OBSTRUCTION_RETURN,FishingTravelIntent.GRAPPLE_RETURN,FishingTravelIntent.COMBAT_RETURN,FishingTravelIntent.OFFENSE_DRY_RETURN)){
            assertEquals(SharedMovementPolicy.Purpose.EXACT_RETURN,intent.purpose());
            var standing=new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK),intent.purpose(),0,1000,intent.tolerance(),1.25);
            assertEquals(intent.tolerance(),standing.terminalRadius());
            for(var mode:Set.of(SharedMovementPolicy.Mode.AOTE,SharedMovementPolicy.Mode.AOTV)){
                // Advisory approach data grants no native source, slot loan, use receipt or cast permission.
                var approach=assertDoesNotThrow(()->new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,mode),intent.purpose(),1,1000,intent.tolerance(),1.25));
                assertEquals(Set.of(SharedMovementPolicy.Mode.WALK,mode),approach.allowed());
                assertEquals(1,approach.toolUses());
                assertEquals(standing.terminalRadius(),approach.terminalRadius());
                var endpoint=new dev.veltraph.client.world.Vec3(8,70,0);
                var savedCursor=new dev.veltraph.client.world.Vec3(30,70,0);
                var device=new SharedMovementPolicy.Candidate("approach",mode,endpoint,1,1,new Object(),new Object(),new Object());
                var walk=new SharedMovementPolicy.Candidate("final-entry",SharedMovementPolicy.Mode.WALK,savedCursor,1,0,new Object(),new Object(),null);
                assertEquals(.30,SharedMovementPolicy.legTerminalRadius(device,approach));
                assertEquals(intent.tolerance(),SharedMovementPolicy.legTerminalRadius(walk,approach));
                assertEquals(intent.tolerance(),approach.terminalRadius());
                assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(Set.of(mode),intent.purpose(),1,1000,intent.tolerance(),1.25));
                assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,mode),intent.purpose(),9,1000,intent.tolerance(),1.25));
            }
            for(var mode:SharedMovementPolicy.Mode.values())if(mode!=SharedMovementPolicy.Mode.WALK&&mode!=SharedMovementPolicy.Mode.AOTE&&mode!=SharedMovementPolicy.Mode.AOTV)
                assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,mode),intent.purpose(),1,1000,intent.tolerance(),1.25));
        }
    }
    @Test void TrashRetainsOriginalSearchHorizonBothWays(){
        for(var v:java.util.List.of(FishingTravelIntent.TRASH_CLIFF,FishingTravelIntent.TRASH_RETURN)){assertEquals(5000,v.nodes());assertEquals(50,v.radius());assertEquals(.35,v.tolerance());}
    }
    @Test void CombatReturnRetainsStrictFinalPose(){
        for(var v:java.util.List.of(FishingTravelIntent.COMBAT_RETURN,FishingTravelIntent.OFFENSE_DRY_RETURN)){assertEquals(.18,v.tolerance());assertEquals(2500,v.nodes());assertEquals(32,v.radius());}
    }
}
