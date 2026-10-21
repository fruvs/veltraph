package dev.veltraph.client.fishing.support;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceBlackHolePresenceTest {
    private static SourceBlackHolePresence.Observation in(String name) {
        return new SourceBlackHolePresence.Observation(true, true, name);
    }
    @Test void onlyExplicitSourceVocabularyEnablesTheQualifiedLocalIdentityBinding() {
        var source = new BlackHoleProfile("Pocket Black Hole","minecraft:blaze_rod","Black Hole",
                "Spawned by: {player}",50,0,160000L,2000L);
        assertTrue(SourceBlackHolePresence.applies(source));
        assertFalse(SourceBlackHolePresence.applies(null));
        assertFalse(SourceBlackHolePresence.applies(new BlackHoleProfile("Fixture Black Hole","minecraft:blaze_rod",
                "Fixture singularity","Fixture owner {player}",50,0,10000L,2000L)));
        assertFalse(SourceBlackHolePresence.applies(new BlackHoleProfile("Pocket Black Hole","minecraft:blaze_rod",
                "Black Hole","Spawned by: {player}",50,0,10000L,2000L)));
    }
    @Test void sameStandAndDuplicateStandsSatisfySourceAvailabilityPredicates() {
        assertTrue(SourceBlackHolePresence.present(List.of(in("§dBlack Hole §7Spawned by: Steve")), "Steve"));
        assertTrue(SourceBlackHolePresence.present(List.of(in("§dBlack Hole 120s"), in("Spawned by: Steve"),
                in("Black Hole"), in("Spawned by: Steve")), "Steve"));
    }
    @Test void rawCaseSensitiveSubstringsAndPlayerIdentityAreRetained() {
        assertTrue(SourceBlackHolePresence.present(List.of(in("Black Hole (active)"),in("Spawned by: [MVP+] Steve")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(in("Black Hole"),in("Spawned by: Alex")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(in("black hole"),in("Spawned by: Steve")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(in("Black Hole"),in("spawned by: Steve")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(in("Black §dHole"),in("Spawned by: Steve")),"Steve"));
        // Source uses contains(player), not a reconstructed exact owner template.
        assertTrue(SourceBlackHolePresence.present(List.of(in("Black Hole"),in("Spawned by: SteveOther")),"Steve"));
    }
    @Test void sourceEntityAndBoundingBoxRequirementsAndUnavailableNamesDoNotProducePresence() {
        assertFalse(SourceBlackHolePresence.present(List.of(new SourceBlackHolePresence.Observation(false,true,"Black Hole"),in("Spawned by: Steve")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(new SourceBlackHolePresence.Observation(true,false,"Black Hole"),in("Spawned by: Steve")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(in("Black Hole"),new SourceBlackHolePresence.Observation(true,false,"Spawned by: Steve")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(Arrays.asList(null,in(null),in("Black Hole")),"Steve"));
        assertFalse(SourceBlackHolePresence.present(null,"Steve"));
        assertFalse(SourceBlackHolePresence.present(List.of(in("Black Hole Spawned by: Steve")),null));
        assertFalse(SourceBlackHolePresence.present(List.of(in("Black Hole Spawned by: Steve")),""));
    }
    @Test void sourcePresenceCannotSatisfyTheConfiguredFreshUseAcknowledgement() {
        var id=UUID.randomUUID();var name="Black Hole Spawned by: Steve";
        assertTrue(SourceBlackHolePresence.present(List.of(in(name)),"Steve"));
        var profile=new BlackHoleProfile("Pocket Black Hole","minecraft:blaze_rod","Black Hole",
                "Spawned by: {player}",50,0,160000L,2000L);
        assertFalse(profile.markerPair(List.of(new BlackHoleProfile.Marker(id,name)),"Steve",Set.of()));
        var effect=UUID.randomUUID();var owner=UUID.randomUUID();
        var exact=List.of(new BlackHoleProfile.Marker(effect,"Black Hole"),new BlackHoleProfile.Marker(owner,"Spawned by: Steve"));
        assertTrue(profile.markerPair(exact,"Steve",Set.of()));
        assertFalse(profile.markerPair(exact,"Steve",Set.of(effect)));
        assertFalse(profile.markerPair(exact,"Alex",Set.of()));
    }
}
