package dev.veltraph.client.fishing.support;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlackHoleProfileTest {
    private final BlackHoleProfile profile=new BlackHoleProfile("Fixture Black Hole","minecraft:blaze_rod",
            "Fixture singularity","Fixture owner {player}",50,0,10000L,2000L);
    private final UUID effect=UUID.randomUUID(),owner=UUID.randomUUID();
    private List<BlackHoleProfile.Marker> pair(){return List.of(new BlackHoleProfile.Marker(effect,"Fixture singularity"),
            new BlackHoleProfile.Marker(owner,"Fixture owner Steve"));}
    @Test void completeProfileRoundTripsAndEmptyOrAbsentContractIsDenied() {
        assertEquals(profile,BlackHoleProfile.parse(new Gson().toJson(profile)));
        for(String invalid:List.of("{}","[]","null","{\"itemName\":\"Fixture Black Hole\"}"))
            assertThrows(IllegalArgumentException.class,()->BlackHoleProfile.parse(invalid));
    }
    @Test void sourceBothPredicatesNeedDistinctFreshNativeEntitiesAndExactOwner() {
        assertTrue(profile.markerPair(pair(),"Steve",Set.of()));
        assertFalse(profile.markerPair(pair(),"Alex",Set.of()));
        assertFalse(profile.markerPair(pair(),"Steve",Set.of(effect)));
        assertFalse(profile.markerPair(pair(),"Steve",Set.of(owner)));
        assertFalse(profile.markerPair(pair().subList(0,1),"Steve",Set.of()));
        assertFalse(profile.markerPair(List.of(new BlackHoleProfile.Marker(effect,"Fixture singularity"),new BlackHoleProfile.Marker(effect,"Fixture owner Steve")),"Steve",Set.of()));
    }
    @Test void duplicateOrPartialNameCannotAcknowledgeNativeAbility() {
        var extra=new ArrayList<>(pair());extra.add(new BlackHoleProfile.Marker(UUID.randomUUID(),"Fixture singularity"));
        assertFalse(profile.markerPair(extra,"Steve",Set.of()));
        assertFalse(profile.markerPair(List.of(new BlackHoleProfile.Marker(effect,"Fixture singularity extra"),pair().get(1)),"Steve",Set.of()));
        assertTrue(profile.markerPair(List.of(new BlackHoleProfile.Marker(effect,"§dFixture singularity"),pair().get(1)),"Steve",Set.of()));
    }
    @Test void zeroConsumptionMissingOwnershipAndUnboundedTimingAreDenied() {
        assertThrows(IllegalArgumentException.class,()->new BlackHoleProfile("Tool","minecraft:blaze_rod","Effect","Owner {player}",0,0,0L,2000L));
        assertThrows(IllegalArgumentException.class,()->new BlackHoleProfile("Tool","minecraft:blaze_rod","Effect","Owner",50,0,0L,2000L));
        assertThrows(IllegalArgumentException.class,()->new BlackHoleProfile("Tool","minecraft:blaze_rod","Effect","Owner {player}",50,0,-1L,2000L));
        assertThrows(IllegalArgumentException.class,()->new BlackHoleProfile("Tool","minecraft:blaze_rod","Effect","Owner {player}",50,0,0L,10001L));
    }
}
