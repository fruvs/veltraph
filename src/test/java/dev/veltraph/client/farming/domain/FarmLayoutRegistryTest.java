package dev.veltraph.client.farming.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmLayoutRegistryTest {

    private final FarmLayoutRegistry registry = FarmLayoutRegistry.createDefault();

    @Test
    void registryContainsRecoveredProfilesAndSeparateCreatorDesign() {
        assertEquals(17, registry.size());
        assertTrue(registry.ids().containsAll(Set.of(
                FarmLayoutRegistry.WART_CROPS_SSHAPE,
                FarmLayoutRegistry.WART_CROPS_VERTICAL,
                FarmLayoutRegistry.COCOA,
                FarmLayoutRegistry.PUMPKIN_MELON,
                FarmLayoutRegistry.SUGARCANE_FLOWERS,
                FarmLayoutRegistry.MUSHROOM_CLASSICAL,
                FarmLayoutRegistry.MUSHROOM_STAIRCASE,
                FarmLayoutRegistry.CACTUS_STRAIGHT,
                FarmLayoutRegistry.PUMPKIN_MELON_MELONKINGDE_WATER,
                FarmLayoutRegistry.PUMPKIN_MELON_SAIGO_CURRENT,
                FarmLayoutRegistry.COCOA_TWO_KEY_CURRENT,
                FarmLayoutRegistry.PUMPKIN_MELON_CAPTURED_DEFAULT,
                FarmLayoutRegistry.MUSHROOM_RING_CURRENT,
                FarmLayoutRegistry.MUSHROOM_FIXED_45,FarmLayoutRegistry.MUSHROOM_ROTATE_30,FarmLayoutRegistry.MUSHROOM_SDS)));
    }

    @Test
    void everyProfileIsFullyPopulated() {
        for (FarmLayoutProfile p : registry.all()) {
            assertNotNull(p.id(), "id");
            assertNotNull(p.displayName(), "displayName");
            assertNotNull(p.card(), "card");
            assertNotNull(p.cropType(), "cropType");
            assertNotNull(p.movementMode(), "movementMode");
            assertNotNull(p.recoveryStatus(), "recoveryStatus");
            assertNotNull(p.configKeys(), "configKeys");
            assertNotNull(p.note(), "note");
        }
    }

    @Test
    void wartCropsCardHasSShapeAndVerticalBothNetherWart() {
        List<FarmLayoutProfile> wart = registry.byCard(FarmCard.WART_CROPS);
        assertEquals(3, wart.size());
        assertTrue(wart.stream().anyMatch(p -> p.movementMode() == FarmMovementMode.S_SHAPE));
        assertTrue(wart.stream().anyMatch(p -> p.movementMode() == FarmMovementMode.VERTICAL));
        assertTrue(wart.stream().filter(p->p.recoveryStatus()!=RecoveryStatus.LOCAL_CUSTOM).allMatch(p -> p.cropType() == CropType.NETHER_WART));
        assertEquals(CropType.WHEAT,registry.byId(FarmLayoutRegistry.WART_CROPS_ACE_TWO_KEY).orElseThrow().cropType());
    }

    @Test
    void flowersIsPresentAsSugarcaneSharedCodeProfile() {
        FarmLayoutProfile p = registry.byId(FarmLayoutRegistry.SUGARCANE_FLOWERS).orElseThrow();
        assertEquals(FarmCard.SUGARCANE_FLOWERS, p.card());
        assertEquals(CropType.SUGAR_CANE, p.cropType());
        assertEquals(FarmMovementMode.BACK_OPPOSITE, p.movementMode(), "Flowers reuses Sugarcane movement code");
        assertTrue(p.displayName().contains("Flowers"));
        assertTrue(p.note().contains("Flowers"));
        assertTrue(p.note().toLowerCase().contains("shared-code"));
        assertTrue(p.usesConfigKey("SUGARCANE_DIR"));
    }

    @Test
    void noSeparateOriginalFlowersControllerIsClaimed() {
        // Any profile that mentions flowers must flag it as shared-code / not original.
        for (FarmLayoutProfile p : registry.all()) {
            String text = (p.displayName() + " " + p.note()).toLowerCase();
            if (text.contains("flower")) {
                String note = p.note().toLowerCase();
                assertTrue(note.contains("shared-code") && note.contains("not"),
                        "Flowers mention must be labeled shared-code/not original: " + p.id());
            }
        }
        // Grouped flower selection uses SugarcaneFarmCrop; no standalone master card/type is added.
        for (CropType c : CropType.values()) {
            assertFalse(c.name().toLowerCase().contains("flower"), "no flower CropType");
        }
        // No card is a standalone "FLOWERS" card.
        for (FarmCard card : FarmCard.values()) {
            assertNotEquals("FLOWERS", card.name());
        }
    }

    @Test
    void cocoaMapsToBeansAndLaneSwitch() {
        FarmLayoutProfile p = registry.byId(FarmLayoutRegistry.COCOA).orElseThrow();
        assertEquals(CropType.COCOA_BEANS, p.cropType());
        assertEquals(FarmMovementMode.LANE_SWITCH, p.movementMode());
    }

    @Test
    void cactusUsesStraightMode() {
        FarmLayoutProfile p = registry.byId(FarmLayoutRegistry.CACTUS_STRAIGHT).orElseThrow();
        assertEquals(FarmMovementMode.STRAIGHT, p.movementMode());
        assertEquals(CropType.CACTUS, p.cropType());
    }

    @Test
    void pumpkinUsesOnEndRow() {
        FarmLayoutProfile p = registry.byId(FarmLayoutRegistry.PUMPKIN_MELON).orElseThrow();
        assertEquals(FarmMovementMode.ON_END_ROW, p.movementMode());
        assertEquals(CropType.PUMPKIN, p.cropType());
    }

    @Test
    void mushroomHasClassicalAndStaircaseProfiles() {
        List<FarmLayoutProfile> mushroom = registry.byCard(FarmCard.MUSHROOM);
        assertEquals(6, mushroom.size());
        assertTrue(mushroom.stream().anyMatch(p -> p.movementMode() == FarmMovementMode.CLASSICAL));
        assertTrue(mushroom.stream().anyMatch(p -> p.movementMode() == FarmMovementMode.STAIRCASE));
    }

    @Test
    void masterCropTypeAndWartVarietyAreDistinctEnums() {
        assertEquals(17, CropType.values().length, "recovered master CropType count");
        assertEquals(4, WartVariety.values().length, "recovered 4-value wart selector");
        for (WartVariety v : WartVariety.values()) {
            assertNotNull(v.cropType());
        }
        assertNotEquals(CropType.values().length, WartVariety.values().length);
    }
}
