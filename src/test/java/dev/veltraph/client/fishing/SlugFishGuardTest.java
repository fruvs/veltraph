package dev.veltraph.client.fishing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlugFishGuardTest {
    private final SlugFishGuard.Profile profile = new SlugFishGuard.Profile("Fixture reduction [", "]");

    @Test void recoveredTwentySecondsPlusOneAndStatReduction() {
        assertEquals(21, profile.thresholdSeconds(List.of("Other: 150")));
        assertEquals(21, profile.thresholdSeconds(List.of("Fixture reduction [0]")));
        assertEquals(16, profile.thresholdSeconds(List.of("§bFixture reduction [50]")));
        assertEquals(11, profile.thresholdSeconds(List.of("Fixture reduction [100]")));
        assertEquals(2, profile.thresholdSeconds(List.of("Fixture reduction [180]")));
        assertEquals(1, profile.thresholdSeconds(List.of("Fixture reduction [200]")));
    }
    @Test void incompleteContractAndAmbiguousOrMalformedStatCannotAllowEarlyCatch() {
        assertThrows(IllegalArgumentException.class, () -> SlugFishGuard.Profile.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> profile.thresholdSeconds(List.of("Fixture reduction [100]", "Fixture reduction [100]")));
        for (String invalid : List.of("201", "-1", "5.0", "NaN", "", "20] ignored"))
            assertThrows(IllegalArgumentException.class, () -> profile.thresholdSeconds(List.of("Fixture reduction [" + invalid + "]")));
        assertEquals(profile, SlugFishGuard.Profile.parse("{\"levelPrefix\":\"Fixture reduction [\",\"levelSuffix\":\"]\"}"));
    }
    @Test void ageStartsAtNativeHookObservationAndRepeatedSamplesDoNotResetIt() {
        var guard = new SlugFishGuard(); Object hook = new Object();
        guard.observe(hook, 21, 1000);
        assertFalse(guard.ready(21999));
        guard.observe(hook, 21, 20000);
        assertTrue(guard.ready(22000));
        guard.observe(hook, 11, 20000);
        assertTrue(guard.ready(20000));
        assertFalse(guard.ready(999));
    }
    @Test void newHookAndRemovalResetAgeWithoutBorrowingOldCastAge() {
        var guard = new SlugFishGuard();
        guard.observe(new Object(), 1, 0); assertTrue(guard.ready(1000));
        guard.observe(new Object(), 1, 1000); assertFalse(guard.ready(1000));
        guard.observe(null, 1, 2000); assertFalse(guard.ready(90000));
        assertEquals(0, guard.remainingMillis(90000));
    }
}
