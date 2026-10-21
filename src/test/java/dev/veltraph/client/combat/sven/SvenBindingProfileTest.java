package dev.veltraph.client.combat.sven;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvenBindingProfileTest {
    static String binding(){return """
        {"schema":1,"profile":"DARK_OAK","warpCommand":"verified_park_warp","travelToolLoreToken":"Verified Ether Transmission","operationTimeoutMs":10000,"modifyJumpBoost":false,
         "bazaar":{"command":"verified_bazaar","searchMenu":"Verified Bazaar","searchButton":"Verified search","searchInput":"verified resource","resultsMenu":"Verified results",
         "productName":"Verified resource","productMenu":"Verified resource details","buyInstantly":"Verified instant buy","buyMenu":"Verified buy options","buyOne":"Verified buy one","priceLorePrefix":"Verified price:","maxCoins":1000},
         "inventory":{"protectedToolTag":"verified:tools","protectedRegistryPathSubstring":"verified_sword"}}
        """;}
    @Test void blankBindingAndWrongProfileNeverGrantReadiness(){
        assertFalse(SvenBindingProfile.parse("",SvenCaveProfile.DARK_OAK).bound());assertFalse(SvenBindingProfile.parse(binding(),SvenCaveProfile.ACACIA).bound());
        assertTrue(SvenBindingProfile.parse(binding(),SvenCaveProfile.DARK_OAK).bound());
    }
    @Test void userReadinessBooleanAndArbitraryRouteCoordinatesAreRejected(){
        assertFalse(SvenBindingProfile.parse(binding().replace("\"schema\":1","\"schema\":1,\"ready\":true"),SvenCaveProfile.DARK_OAK).bound());
        assertFalse(SvenBindingProfile.parse(binding().replace("\"schema\":1","\"schema\":1,\"route\":[[0,0,0]]"),SvenCaveProfile.DARK_OAK).bound());
    }
    @Test void unsafeCommandsIncompleteMenuBindingsAndMissingPriceCapsAreRejected(){
        assertFalse(SvenBindingProfile.parse(binding().replace("verified_park_warp","verified_park_warp; other"),SvenCaveProfile.DARK_OAK).bound());
        assertFalse(SvenBindingProfile.parse(binding().replace("Verified buy one",""),SvenCaveProfile.DARK_OAK).bound());
        assertFalse(SvenBindingProfile.parse(binding().replace("\"maxCoins\":1000","\"maxCoins\":0"),SvenCaveProfile.DARK_OAK).bound());
    }
    @Test void numericCoercionAndStringBooleanAreRejected(){
        assertFalse(SvenBindingProfile.parse(binding().replace("\"schema\":1","\"schema\":1.9"),SvenCaveProfile.DARK_OAK).bound());
        assertFalse(SvenBindingProfile.parse(binding().replace("\"modifyJumpBoost\":false","\"modifyJumpBoost\":\"false\""),SvenCaveProfile.DARK_OAK).bound());
    }
    @Test void nativeBuffModificationRequiresExplicitCooldown(){
        assertFalse(SvenBindingProfile.parse(binding().replace("\"modifyJumpBoost\":false","\"modifyJumpBoost\":true"),SvenCaveProfile.DARK_OAK).bound());
    }
    @Test void sourceBfsRoutesUseActualEdgesAndPreserveDuplicateJungleCandidate(){
        var dark=SvenParkRoutes.route(SvenCaveProfile.DARK_OAK);assertEquals(1,dark.size());assertEquals(SvenParkRoutes.Tree.BIRCH,dark.getFirst().from());assertEquals(SvenParkRoutes.Tree.DARK_OAK,dark.getFirst().to());
        var acacia=SvenParkRoutes.route(SvenCaveProfile.ACACIA);assertEquals(List.of(SvenParkRoutes.Tree.SPRUCE,SvenParkRoutes.Tree.ACACIA),acacia.stream().map(SvenParkRoutes.Edge::to).toList());
        var jungle=SvenParkRoutes.route(SvenCaveProfile.JUNGLE);assertEquals(List.of(SvenParkRoutes.Tree.DARK_OAK,SvenParkRoutes.Tree.JUNGLE),jungle.stream().map(SvenParkRoutes.Edge::to).toList());
        assertEquals(jungle.getLast().etherwarpCandidates().get(0),jungle.getLast().etherwarpCandidates().get(1));
        assertEquals(new dev.veltraph.client.world.BlockPos(-320,82,-26),dark.getFirst().standingStart());
    }
}
