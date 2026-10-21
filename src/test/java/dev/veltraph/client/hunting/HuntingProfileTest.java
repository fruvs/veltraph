package dev.veltraph.client.hunting;

import dev.veltraph.client.game.GameArea;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

final class HuntingProfileTest {
    private static HuntingProfile.Target fish(String entity,HuntingProfile.Outcome outcome,Pattern receipt){return new HuntingProfile.Target("Cod",entity,Pattern.compile("Cod"),new HuntingProfile.Item("minecraft:paper","Configured tool"),1,null,null,500,outcome,new HuntingProfile.Item("minecraft:cod","Raw Cod"),1,receipt,new HuntingProfile.Item("minecraft:wheat_seeds","Wheat Seeds"),1,4);}
    private static HuntingProfile profile(List<HuntingProfile.Node> route,List<HuntingProfile.Target> targets,double reach){return new HuntingProfile(GameArea.GALATEA,"minecraft:overworld",new HuntingProfile.Point(0,70,0),new HuntingProfile.Point(20,80,20),route,targets,20,70,reach,650,5000,18000,45000,4,1,1,20000,0);}
    @Test void explicitNativeFamilyAndBoundedRegion(){var p=profile(List.of(new HuntingProfile.Node(new HuntingProfile.Point(5,70,5),HuntingProfile.Medium.SWIM)),List.of(fish("minecraft:cod",HuntingProfile.Outcome.DEATH_DROP,null)),3);assertTrue(p.inside(new HuntingProfile.Point(5,75,5)));assertFalse(p.inside(new HuntingProfile.Point(21,75,5)));assertEquals(1,p.targets().getFirst().slot());}
    @Test void neverInventUnsupportedGenericMobOrBlankTool(){assertThrows(IllegalArgumentException.class,()->fish("minecraft:zombie",HuntingProfile.Outcome.DEATH_DROP,null));assertThrows(IllegalArgumentException.class,()->new HuntingProfile.Item("minecraft:paper",""));assertThrows(IllegalArgumentException.class,()->HuntingProfile.parse(""));}
    @Test void routeOutsideRegionAndDuplicateCatalogDenied(){assertThrows(IllegalArgumentException.class,()->profile(List.of(new HuntingProfile.Node(new HuntingProfile.Point(21,70,5),HuntingProfile.Medium.SWIM)),List.of(fish("minecraft:cod",HuntingProfile.Outcome.DEATH_DROP,null)),3));var f=fish("minecraft:cod",HuntingProfile.Outcome.DEATH_DROP,null);assertThrows(IllegalArgumentException.class,()->profile(List.of(),List.of(f,f),3));}
    @Test void attributedOutcomeNeedsExactOneGroupContract(){assertThrows(IllegalArgumentException.class,()->fish("minecraft:cod",HuntingProfile.Outcome.ATTRIBUTED_RECEIPT,null));assertThrows(IllegalArgumentException.class,()->fish("minecraft:cod",HuntingProfile.Outcome.ATTRIBUTED_RECEIPT,Pattern.compile("generic success")));assertDoesNotThrow(()->fish("minecraft:cod",HuntingProfile.Outcome.ATTRIBUTED_RECEIPT,Pattern.compile("CHARM You charmed a (.+) and captured 1 Shards\\.")));}
    @Test void finitePhysicalBoundsAndNativeReachRequired(){assertThrows(IllegalArgumentException.class,()->new HuntingProfile.Point(Double.NaN,70,0));assertThrows(IllegalArgumentException.class,()->profile(List.of(),List.of(fish("minecraft:cod",HuntingProfile.Outcome.DEATH_DROP,null)),Double.NaN));assertThrows(IllegalArgumentException.class,()->profile(List.of(),List.of(fish("minecraft:cod",HuntingProfile.Outcome.DEATH_DROP,null)),3.1));}
    private static String protocolProfile(String flight){return """
        {"area":"GALATEA","dimension":"minecraft:overworld","min":[0,70,0],"max":[20,80,20],"route":[],"radius":20,"minimumY":70,"reach":3,
        "actionDelayMs":650,"actionAckMs":5000,"targetMs":18000,"runMs":45000,"maxActions":4,"maxOutcomes":1,"maxFailures":1,"blacklistMs":20000,"airReserve":0,
        "targets":[{"id":"Cod","entity":"minecraft:cod","nameRegex":"Cod","tool":{"id":"minecraft:paper","name":"Configured tool"},"slot":2,"singleHitGraceMs":500,
        "outcome":"DEATH_DROP","drop":{"id":"minecraft:cod","name":"Raw Cod"},"quantity":1,"costPerUse":0,"maxCost":0%s}]}
        """.formatted(flight);}
    @Test void absenceOfFlightRemainsUnknownAndExplicitModelNeverGuessesServerNet(){
        assertNull(HuntingProfile.parse(protocolProfile("")).targets().getFirst().flight());
        var known=HuntingProfile.parse(protocolProfile(",\"flight\":{\"model\":\"VANILLA_SNOWBALL_26_3\",\"maxTicks\":15,\"maxTravel\":3.3}"));
        assertEquals(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,known.targets().getFirst().flight().model());
        assertEquals(15,known.targets().getFirst().flight().maxTicks());
        assertThrows(IllegalArgumentException.class,()->HuntingProfile.parse(protocolProfile(",\"flight\":{\"model\":\"REMOTE_NET_UNKNOWN\",\"maxTicks\":15,\"maxTravel\":3.3}")));
    }
    @Test void malformedOrUnboundedFlightMetadataDeniedRatherThanDefaulted(){
        assertThrows(RuntimeException.class,()->HuntingProfile.parse(protocolProfile(",\"flight\":{\"model\":\"VANILLA_SNOWBALL_26_3\"}")));
        assertThrows(IllegalArgumentException.class,()->HuntingProfile.parse(protocolProfile(",\"flight\":{\"model\":\"VANILLA_SNOWBALL_26_3\",\"maxTicks\":999,\"maxTravel\":3.3}")));
    }
    @Test void observationAgeMustBeDeclaredAndRoundTripExactly(){
        String legacy=",\"flight\":{\"model\":\"VANILLA_SNOWBALL_26_3\",\"maxTicks\":15,\"maxTravel\":3.3}";
        assertEquals(-1,HuntingProfile.parse(protocolProfile(legacy)).targets().getFirst().flight().maxObservationLagTicks());
        String explicit=legacy.replace("3.3}","3.3,\"maxObservationLagTicks\":3}");
        assertEquals(3,HuntingProfile.parse(protocolProfile(explicit)).targets().getFirst().flight().maxObservationLagTicks());
    }
}
