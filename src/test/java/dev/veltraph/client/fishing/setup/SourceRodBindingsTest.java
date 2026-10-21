package dev.veltraph.client.fishing.setup;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceRodBindingsTest {
    @Test void emptyObjectUsesRecoveredBindingsButExplicitFailuresAreNotRepaired() {
        var source = SourceRodBindings.profile();
        assertEquals(source, RodSelectionProfile.parse("{}"));
        assertEquals(source, RodSelectionProfile.parse(" { \n } "));
        assertFalse(FishingSetupConfig.FISHING_AUTO_ROD.defaultValue());
        for (var input : List.of("null", "[]", "", "{", "{\"water\":[]}", "{\"excludedNameContains\":\"Grappling\"}"))
            assertThrows(IllegalArgumentException.class, () -> RodSelectionProfile.parse(input), input);
        var override = RodSelectorTest.profile();
        assertEquals(override, RodSelectionProfile.parse(new Gson().toJson(override)));
        assertNotEquals(source, override);
    }
    @Test void realNamesDriveNativeWaterAndLavaStartupChoicesWithoutIncludingSlotEight() {
        var profile = RodSelectionProfile.parse("{}");
        var hotbar = RodSelectorTest.hotbar();
        hotbar.set(0, new RodSelector.Candidate(0, true, "§bGrappling Rod of the Sea", true));
        hotbar.set(1, new RodSelector.Candidate(1, false, "Rod of the Sea", true));
        hotbar.set(2, new RodSelector.Candidate(2, true, "§6Rod of Legends", false));
        hotbar.set(4, new RodSelector.Candidate(4, true, "Rod of Shredder", false));
        hotbar.set(7, new RodSelector.Candidate(7, true, "Hellfire Rod", false));
        hotbar.set(8, new RodSelector.Candidate(8, true, "Rod of the Sea", true));
        assertEquals(2, RodSelector.select(hotbar, false, profile));
        assertEquals(7, RodSelector.select(hotbar, true, profile));
        assertEquals(0, RodSelector.score(hotbar.get(0), false, profile));
        assertEquals(0, RodSelector.score(hotbar.get(1), false, profile));
        assertEquals(60, RodSelector.score(hotbar.get(8), false, profile));
    }
    @Test void recoveredTableMatchesEverySourceLabelAndWeightAndKeepsHashMapIteration() {
        var profile = SourceRodBindings.profile();
        assertEquals("Grappling", profile.excludedNameContains());
        assertEquals(Map.of("Rod of the Sea",50,"Rod of Legends",30,"Rod of Shredder",30,
                "Rod of Champions",20,"Challenging Rod",10,"Speedster Rod",5,"Fishing Rod",1), profile.table(false));
        assertEquals(Map.of("Hellfire Rod",50,"Inferno Rod",30,"Magma Rod",20,
                "Starter Lava Rod",10,"Speedster Rod",3,"Fishing Rod",1), profile.table(true));
        String overlapping="Rod of the Sea Rod of Legends Fishing Rod";
        var table=profile.table(false);
        int expected=table.keySet().stream().filter(overlapping::contains).map(table::get).findFirst().orElseThrow();
        assertEquals(HashMap.class, table.getClass());
        assertEquals(expected, RodSelector.score(new RodSelector.Candidate(0,true,overlapping,false),false,profile));
    }
}
