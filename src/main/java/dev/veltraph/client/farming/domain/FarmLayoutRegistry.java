package dev.veltraph.client.farming.domain;

import dev.veltraph.client.config.FarmingConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Source-backed catalog of farming layout profiles. Built from the deep-recovery reports; every
 * profile's behavior, defaults, and caveats trace to {@code FARMING_LAYOUT_PLANNER_DEEP_RECOVERY.md}
 * and its spec parts.
 *
 * <p>Contains eight recovered controller profiles and an additional creator-referenced water design: Wart/Crops S-shape, Wart/Crops Vertical,
 * Cocoa, Pumpkin/Melon, Sugarcane/Flowers (Flowers = shared Sugarcane code), Mushroom Classical,
 * Mushroom Staircase, Cactus Straight.
 */
public final class FarmLayoutRegistry {

    // Stable profile ids.
    public static final String WART_CROPS_SSHAPE = "wart_crops_sshape";
    public static final String WART_CROPS_VERTICAL = "wart_crops_vertical";
    public static final String WART_CROPS_ACE_TWO_KEY = "wart_crops_ace_two_key";
    public static final String COCOA = "cocoa";
    public static final String COCOA_TWO_KEY_CURRENT = "cocoa_two_key_current";
    public static final String PUMPKIN_MELON = "pumpkin_melon";
    public static final String PUMPKIN_MELON_CAPTURED_DEFAULT = "pumpkin_melon_captured_default";
    public static final String PUMPKIN_MELON_MELONKINGDE_WATER = "pumpkin_melon_melonkingde_water";
    public static final String PUMPKIN_MELON_SAIGO_CURRENT = "pumpkin_melon_saigo_current";
    public static final String SUGARCANE_FLOWERS = "sugarcane_flowers";
    public static final String MUSHROOM_CLASSICAL = "mushroom_classical";
    public static final String MUSHROOM_STAIRCASE = "mushroom_staircase";
    public static final String MUSHROOM_FIXED_45 = "mushroom_farmhelper_fixed45";
    public static final String MUSHROOM_ROTATE_30 = "mushroom_farmhelper_rotate30";
    public static final String MUSHROOM_SDS = "mushroom_farmhelper_sds";
    public static final String MUSHROOM_RING_CURRENT = "mushroom_ring_current";
    public static final String CACTUS_STRAIGHT = "cactus_straight";

    private final Map<String, FarmLayoutProfile> byId;

    private FarmLayoutRegistry(List<FarmLayoutProfile> profiles) {
        Map<String, FarmLayoutProfile> map = new LinkedHashMap<>();
        for (FarmLayoutProfile p : profiles) {
            if (map.putIfAbsent(p.id(), p) != null) {
                throw new IllegalArgumentException("Duplicate profile id: " + p.id());
            }
        }
        this.byId = map;
    }

    /** Build the default registry from the recovered farming specs. */
    public static FarmLayoutRegistry createDefault() {
        List<FarmLayoutProfile> profiles = new ArrayList<>();

        profiles.add(FarmLayoutProfile.builder(WART_CROPS_SSHAPE)
                .displayName("Wart/Crops (S-Shape)")
                .card(FarmCard.WART_CROPS)
                .cropType(CropType.NETHER_WART)
                .movementMode(FarmMovementMode.S_SHAPE)
                .recoveryStatus(RecoveryStatus.READY)
                .configKeys(
                        FarmingConfig.DROP_AND_TURN.name(),
                        FarmingConfig.ALLOW_IRREGULAR_CORNERS.name(),
                        FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY.name(),
                        FarmingConfig.S_SHAPE_PAUSE_ANYWHERE.name(),
                        FarmingConfig.TURN_COOLDOWN_MS.name(),
                        FarmingConfig.NETHERWART_STARTING_DIRECTION.name(),
                        FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP.name(),
                        FarmingConfig.WART_FIX_PITCH_AFTER_TP.name())
                .note("One card with a 4-value variety selector (NETHER_WART/POTATO/CARROT/WHEAT). "
                        + "S-shape uses the shared MovementDirection row engine.")
                .build());

        profiles.add(FarmLayoutProfile.builder(WART_CROPS_VERTICAL)
                .displayName("Wart/Crops (Vertical)")
                .card(FarmCard.WART_CROPS)
                .cropType(CropType.NETHER_WART)
                .movementMode(FarmMovementMode.VERTICAL)
                .recoveryStatus(RecoveryStatus.CONFIRMED)
                .configKeys(
                        FarmingConfig.VERTICAL_HOLD_FORWARD_POLICY.name(),
                        FarmingConfig.VERT_WART_SWITCH_DIRECTION_IF_STILL.name(),
                        FarmingConfig.NETHERWART_STARTING_DIRECTION.name(),
                        FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP.name(),
                        FarmingConfig.WART_FIX_PITCH_AFTER_TP.name())
                .note("Specialized 40/20-tick two-counter still-detection machine; NOT the shared "
                        + "MovementDirection engine. Still-detection (delta <= 0.25) triggers a "
                        + "two horizontal gap/wall probes; ordinary movement persists until observed geometry or height changes.")
                .build());

        profiles.add(FarmLayoutProfile.builder(WART_CROPS_ACE_TWO_KEY)
                .displayName("Ace Two-Key Wheat Loop")
                .card(FarmCard.WART_CROPS)
                .cropType(CropType.WHEAT)
                .movementMode(FarmMovementMode.S_SHAPE)
                .recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.WART_CROPS_DESIGN.name(),FarmingConfig.NETHERWART_STARTING_DIRECTION.name())
                .note("Adaptation for the unchanged 2KeyAceWheat1Plotv2_fixed author reference, including fractional guides and levels. "
                        + "This option is distinct from the recovered classical/vertical planners and captured modified-default wheat.")
                .build());

        profiles.add(FarmLayoutProfile.builder(COCOA)
                .displayName("Cocoa")
                .card(FarmCard.COCOA)
                .cropType(CropType.COCOA_BEANS)
                .movementMode(FarmMovementMode.LANE_SWITCH)
                .recoveryStatus(RecoveryStatus.CONFIRMED)
                .configKeys(
                        FarmingConfig.COCOA_PLACER_AUTO_WALK.name(),
                        FarmingConfig.COCOA_LANE_SWITCH_DIRECTION.name(),
                        FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING.name())
                .note("Lane-switch state machine; sneak is a same-tick state latch (press at "
                        + "SWITCHING_ROW, release at FARMING re-entry). COCOA_PLACER_AUTO_WALK runtime "
                        + "consumer NOT_FOUND in source; default true is documented, no hidden behavior.")
                .build());

        profiles.add(FarmLayoutProfile.builder(COCOA_TWO_KEY_CURRENT)
                .displayName("Two-Key Cocoa Loop (2026)")
                .card(FarmCard.COCOA)
                .cropType(CropType.COCOA_BEANS)
                .movementMode(FarmMovementMode.LANE_SWITCH)
                .recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.COCOA_FARM_MODE.name(),FarmingConfig.COCOA_TWO_KEY_START_SIDE.name())
                .note("Author-schematic adaptation, not a recovered built-in controller. "
                        + "Fixed upward view with alternating A/D and authored partial-height end transitions. "
                        + "The exact 2026 2KeyCocoa1Plot schematic is the acceptance reference.")
                .build());

        profiles.add(FarmLayoutProfile.builder(PUMPKIN_MELON)
                .displayName("Pumpkin/Melon")
                .card(FarmCard.PUMPKIN_MELON)
                .cropType(CropType.PUMPKIN)
                .movementMode(FarmMovementMode.ON_END_ROW)
                .recoveryStatus(RecoveryStatus.READY)
                .configKeys(
                        FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK.name(),
                        FarmingConfig.DROP_AND_TURN.name(),
                        FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY.name(),
                        FarmingConfig.TURN_COOLDOWN_MS.name())
                .note("One card farming both pumpkin and melon (combined harvest set). Unique "
                        + "PumpkinRowState{FARMING, ON_END_ROW} trigger. No named plot/map presets recovered.")
                .build());

        profiles.add(FarmLayoutProfile.builder(PUMPKIN_MELON_CAPTURED_DEFAULT)
                .displayName("Captured Modified Default Gourd")
                .card(FarmCard.PUMPKIN_MELON)
                .cropType(CropType.PUMPKIN)
                .movementMode(FarmMovementMode.ON_END_ROW)
                .recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.PUMPKIN_FARM_DESIGN.name())
                .note("Adaptation for the user's captured Garden plot with modified paths. "
                        + "Preserves stem-connected crop targets and uses observed corridor clearance. "
                        + "This capture does not establish an unchanged default preset.")
                .build());

        profiles.add(FarmLayoutProfile.builder(PUMPKIN_MELON_MELONKINGDE_WATER)
                .displayName("MelonKingDE Three-Layer One-Plot (2023)")
                .card(FarmCard.PUMPKIN_MELON)
                .cropType(CropType.PUMPKIN)
                .movementMode(FarmMovementMode.WATER_LANES)
                .recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.PUMPKIN_FARM_DESIGN.name(),FarmingConfig.PUMPKIN_MELONKING_START_SIDE.name())
                .note("Three-layer, one-plot tutorial reconstruction with south view, upward crop wall "
                        + "and Forward plus alternating side. Local full-crop coverage and three resets passed; "
                        + "the test uses a historical-water counterpart. Live server behavior remains unverified.")
                .build());

        profiles.add(FarmLayoutProfile.builder(PUMPKIN_MELON_SAIGO_CURRENT)
                .displayName("SAIGO Melon/Pumpkin (2026)")
                .card(FarmCard.PUMPKIN_MELON)
                .cropType(CropType.PUMPKIN)
                .movementMode(FarmMovementMode.WATER_LANES)
                .recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.PUMPKIN_FARM_DESIGN.name(),FarmingConfig.PUMPKIN_SAIGO_START_SIDE.name())
                .note("Author-schematic adaptation, not a recovered built-in controller. "
                        + "Cardinal heading, downward crop view, sprint Forward plus alternating side. "
                        + "The actual 2026 schematic and end sections are the acceptance reference.")
                .build());

        profiles.add(FarmLayoutProfile.builder(SUGARCANE_FLOWERS)
                .displayName("Sugarcane/Flowers")
                .card(FarmCard.SUGARCANE_FLOWERS)
                .cropType(CropType.SUGAR_CANE)
                .movementMode(FarmMovementMode.BACK_OPPOSITE)
                .recoveryStatus(RecoveryStatus.CONFIRMED)
                .configKeys(FarmingConfig.SUGARCANE_DIR.name(), FarmingConfig.SUGARCANE_FARM_DESIGN.name(), FarmingConfig.SUGARCANE_FARM_CROP.name())
                .note("The recovered shared-code controller registers Sugarcane, Sunflower and Rose Bush "
                        + "in stable 4.0.30; Flowers is not a separate original controller. "
                        + "RECOVERED keeps the configured side/BACK planner. ACE_TWO_PLOT follows the "
                        + "creator's diagonal row and return chords; full cane and sunflower courses are accepted, "
                        + "while a separate full rose course remains unverified.")
                .build());

        profiles.add(FarmLayoutProfile.builder(MUSHROOM_CLASSICAL)
                .displayName("Mushroom (Classical)")
                .card(FarmCard.MUSHROOM)
                .cropType(CropType.RED_MUSHROOM)
                .movementMode(FarmMovementMode.CLASSICAL)
                .recoveryStatus(RecoveryStatus.CONFIRMED)
                .configKeys(
                        FarmingConfig.MUSHROOM_DIR.name(),
                        FarmingConfig.STAIRCASE_MUSHROOM_MAIN_DIR.name())
                .note("Fixed-side row traversal using MUSHROOM_DIR (default LEFT). Red/brown are drop "
                        + "variants, not separate traversals (BUILDER_MUSHROOM_TYPE picks the build block).")
                .build());

        profiles.add(FarmLayoutProfile.builder(MUSHROOM_STAIRCASE)
                .displayName("Mushroom (Staircase)")
                .card(FarmCard.MUSHROOM)
                .cropType(CropType.RED_MUSHROOM)
                .movementMode(FarmMovementMode.STAIRCASE)
                .recoveryStatus(RecoveryStatus.CONFIRMED)
                .configKeys(
                        FarmingConfig.MUSHROOM_DIR.name(),
                        FarmingConfig.STAIRCASE_MUSHROOM_MAIN_DIR.name())
                .note("Staircase selector keys on a FOUND/BLOCKED/NULL probe -> MUSHROOM_DIR vs FORWARD. "
                        + "It does NOT read STAIRCASE_MUSHROOM_MAIN_DIR directly (resolved upstream).")
                .build());

        profiles.add(FarmLayoutProfile.builder(MUSHROOM_RING_CURRENT)
                .displayName("Mushroom Ring (2026)")
                .card(FarmCard.MUSHROOM)
                .cropType(CropType.RED_MUSHROOM)
                .movementMode(FarmMovementMode.CLASSICAL)
                .recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.MUSHROOM_FARM_DESIGN.name())
                .note("Author-schematic W/S spiral adaptation. Each layer uses its configured starting heading; "
                        + "automatic transfer between layers requires separate acceptance.")
                .build());

        profiles.add(FarmLayoutProfile.builder(MUSHROOM_FIXED_45)
                .displayName("Mushroom Fixed 45").card(FarmCard.MUSHROOM).cropType(CropType.RED_MUSHROOM)
                .movementMode(FarmMovementMode.CLASSICAL).recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.MUSHROOM_FARM_DESIGN.name(),FarmingConfig.MUSHROOM_DIR.name())
                .note("Explicit FarmHelper behavior reference; no creator layout or universal speed claimed. Fixed diagonal W/A or W/D with supported wall reversal.")
                .build());

        profiles.add(FarmLayoutProfile.builder(MUSHROOM_ROTATE_30)
                .displayName("Mushroom Rotating 30").card(FarmCard.MUSHROOM).cropType(CropType.RED_MUSHROOM)
                .movementMode(FarmMovementMode.CLASSICAL).recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.MUSHROOM_FARM_DESIGN.name(),FarmingConfig.MUSHROOM_DIR.name())
                .note("Explicit FarmHelper behavior reference; no creator layout or universal speed claimed. Forward traversal with signed 30-degree wall turns and native collision gating.")
                .build());

        profiles.add(FarmLayoutProfile.builder(MUSHROOM_SDS)
                .displayName("Mushroom SDS").card(FarmCard.MUSHROOM).cropType(CropType.RED_MUSHROOM)
                .movementMode(FarmMovementMode.CLASSICAL).recoveryStatus(RecoveryStatus.LOCAL_CUSTOM)
                .configKeys(FarmingConfig.MUSHROOM_FARM_DESIGN.name(),FarmingConfig.MUSHROOM_DIR.name())
                .note("Explicit FarmHelper behavior reference; no creator layout or universal speed claimed. Cardinal A/D rows and supported backward lane transfer, pitch 7.")
                .build());

        profiles.add(FarmLayoutProfile.builder(CACTUS_STRAIGHT)
                .displayName("Cactus (Straight)")
                .card(FarmCard.CACTUS)
                .cropType(CropType.CACTUS)
                .movementMode(FarmMovementMode.STRAIGHT)
                .recoveryStatus(RecoveryStatus.READY)
                .configKeys(
                        FarmingConfig.DROP_AND_TURN.name(),
                        FarmingConfig.ALLOW_IRREGULAR_CORNERS.name(),
                        FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY.name(),
                        FarmingConfig.TURN_COOLDOWN_MS.name())
                .note("Inherits the shared MovementDirection row engine wholesale; cactus-specific "
                        + "registration + warning only.")
                .build());

        return new FarmLayoutRegistry(profiles);
    }

    public List<FarmLayoutProfile> all() {
        return List.copyOf(byId.values());
    }

    public Optional<FarmLayoutProfile> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<FarmLayoutProfile> byCard(FarmCard card) {
        List<FarmLayoutProfile> out = new ArrayList<>();
        for (FarmLayoutProfile p : byId.values()) {
            if (p.card() == card) {
                out.add(p);
            }
        }
        return out;
    }

    public List<FarmLayoutProfile> byMovementMode(FarmMovementMode mode) {
        List<FarmLayoutProfile> out = new ArrayList<>();
        for (FarmLayoutProfile p : byId.values()) {
            if (p.movementMode() == mode) {
                out.add(p);
            }
        }
        return out;
    }

    public Set<String> ids() {
        return Set.copyOf(byId.keySet());
    }

    public int size() {
        return byId.size();
    }
}
