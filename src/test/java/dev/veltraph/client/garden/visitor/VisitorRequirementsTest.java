package dev.veltraph.client.garden.visitor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VisitorRequirementsTest {
    @Test void readsRealSectionQuantitiesBeyondStackSizeAndIgnoresRewards(){
        var items=VisitorRequirements.read(List.of("Required:","§fWheat §8x1,024","64x Enchanted Carrot","","Rewards:","+20 Copper")).orElseThrow();
        assertEquals(List.of(VisitorItem.of("Wheat",1024),VisitorItem.of("Enchanted Carrot",64)),items);
    }
    @Test void malformedDuplicateZeroOrOverflowRequirementsAreRejected(){
        for(var lines:List.of(List.of("Required:","Wheat x0"),List.of("Required:","Wheat x4","wheat x4"),List.of("Required:","Wheat x999999999999999"),List.of("Required:","Wheat xinvalid")))assertTrue(VisitorRequirements.read(lines).isEmpty());
    }
    @Test void menuInstructionsAndCoinRewardsAreNotRequirements(){assertTrue(VisitorRequirements.read(List.of("Accept Offer","+10 Coins","Click to serve!")).isEmpty());}
    @Test void oneItemWithoutQuantityIsOneInsideRequiredSection(){assertEquals(List.of(VisitorItem.of("Enchanted Hay Bale",1)),VisitorRequirements.read(List.of("Items Required:","Enchanted Hay Bale","","Rewards:")).orElseThrow());}
}
