package dev.veltraph.client.ui.content;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.config.BuilderConfig;
import dev.veltraph.client.config.BuilderCropType;
import dev.veltraph.client.config.BuilderFlowerType;
import dev.veltraph.client.config.BuilderMushroomType;
import dev.veltraph.client.config.DebugConfig;
import dev.veltraph.client.config.DeveloperConfig;
import dev.veltraph.client.config.DirectionMode;
import dev.veltraph.client.config.FailsafeConfig;
import dev.veltraph.client.config.FailsafeReactionPolicy;
import dev.veltraph.client.config.FailsafeSourcePolicy;
import dev.veltraph.client.config.GreenhouseConfig;
import dev.veltraph.client.config.ChocolateFactoryConfig;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.ExperimentsConfig;
import dev.veltraph.client.config.GalateaConfig;
import dev.veltraph.client.config.InventoryUtilityConfig;
import dev.veltraph.client.config.EquipmentConfig;
import dev.veltraph.client.config.QuiverConfig;
import dev.veltraph.client.combat.quiver.QuiverIngressConfig;
import dev.veltraph.client.gui.ExperimentsGuardianConfig;
import dev.veltraph.client.config.SkillAutomationConfig;
import dev.veltraph.client.config.SellingConfig;
import dev.veltraph.client.config.MiningConfig;
import dev.veltraph.client.config.PestConfig;
import dev.veltraph.client.config.ShardConfig;
import dev.veltraph.client.config.ZealotConfig;
import dev.veltraph.client.config.HoldForwardPolicy;
import dev.veltraph.client.config.HydraConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.config.FishingConfig;
import dev.veltraph.client.config.ForagingConfig;
import dev.veltraph.client.config.GhostConfig;
import dev.veltraph.client.config.HudConfig;
import dev.veltraph.client.config.MiscConfig;
import dev.veltraph.client.config.NotificationConfig;
import dev.veltraph.client.config.VisitorConfig;
import dev.veltraph.client.dev.DevTestAction;
import dev.veltraph.client.dev.DevTestActions;
import dev.veltraph.client.dev.DevTestCategory;
import dev.veltraph.client.fishing.FishingDetectionMode;
import dev.veltraph.client.garden.visitor.SortOrder;
import dev.veltraph.client.hud.HudCorner;
import dev.veltraph.client.hydra.response.HydraOriginalPolicy;
import dev.veltraph.client.hydra.response.HydraResponsePolicy;
import dev.veltraph.client.log.DebugLogMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure content model for persistent, editable runtime settings. Unimplemented local branches and
 * excluded external services retain explicit disabled explanations; they are not runtime controls.
 */
public final class SettingsContent {

    /** Rail categories in display order; the screen obtains its rail from this model. */
    public static final List<String> CATEGORY_ORDER = List.of(
            "Visual", "HUD", "Scripting", "Developer", "Anti-Staff", "Failsafe",
            "Farming", "Foraging", "Combat", "Fishing", "Shards", "Mining", "Experiments", "Skills", "Builder", "Misc");

    /** Default blocker for a recovered config group that is not yet wired to a runtime. */
    public static final String BLOCKER_NOT_PORTED = "Runtime not yet ported";

    private final Map<String, CategoryContent> byCategory;

    private SettingsContent(Map<String, CategoryContent> byCategory) {
        this.byCategory = byCategory;
    }

    public static SettingsContent build() {
        Map<String, CategoryContent> map = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) {
            List<RowModel> rows = switch (cat) {
                case "Farming" -> farmingRows();
                case "Scripting" -> scriptingRows();
                case "Developer" -> developerRows();
                case "Anti-Staff" -> antiStaffRows();
                case "Fishing" -> fishingRows();
                case "Combat" -> combatRows();
                case "Foraging" -> foragingRows();
                case "Shards" -> shardRows();
                case "Mining" -> miningRows();
                case "Experiments" -> experimentsRows();
                case "Skills" -> skillRows();
                case "HUD" -> hudRows();
                case "Visual" -> visualRows();
                case "Failsafe" -> failsafeRows();
                case "Builder" -> builderRows();
                case "Misc" -> miscRows();
                default -> disabledRows(cat);
            };
            map.put(cat, CategoryContent.ofRows(cat, rows));
        }
        return new SettingsContent(map);
    }

    /**
     * Disabled (inspectable) rows naming the recovered config groups for a not-yet-ported category.
     * Group names cite {@code MACRO_INVENTORY.md} / per-family decodes (see class javadoc).
     */
    private static List<RowModel> disabledRows(String category) {
        List<String> groups = switch (category) {
            // §11 (notification system) + Discord/visual surface from the 440-key config enum.
            case "Visual" -> List.of(
                    "Notification types / colors (29 notify types — see MACRO_INVENTORY §11)",
                    "Discord alerts / rich presence / screenshot");
            // HUD stat overlay (§8 stats tracked).
            case "HUD" -> List.of(
                    "HUD overlay toggles (SKILL_XP / TOTAL_PROFIT / BPS / KILLS / RNG — see MACRO_INVENTORY §8)");
            // §9 Failsafes.
            case "Failsafe" -> List.of(
                    "Disconnect/lagback/teleport failsafes",
                    "Player-nearby failsafe (radius, count, only-looking-at, raytraced-only)",
                    "Reaction policy (STOP_SCRIPT / STOP_AND_RETURN_TO_LOCATION / DO_NOTHING)");
            // §7 Builder (BUILDER_* config group).
            case "Builder" -> List.of(
                    "Builder config (BUILDER_* building/indicator/slab blocks, crop/flower/mushroom type, buy resources, bounds)");
            // §8 cross-cutting utility config (auto-sell, auto-reconnect, rewarp, rest/anti-AFK).
            case "Misc" -> List.of(
                    "Auto-sell (BAZAAR/NPC/TRASH/GEORGE_PETS, sacks, force-type)",
                    "Auto-reconnect / dynamic rest / per-skill anti-AFK");
            default -> List.of();
        };
        if (groups.isEmpty()) {
            return List.of(RowModel.comingSoon());
        }
        List<RowModel> rows = new ArrayList<>();
        for (String group : groups) {
            rows.add(RowModel.disabled(group, BLOCKER_NOT_PORTED));
        }
        return rows;
    }

    private static List<RowModel> farmingRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Farming"));
        rows.add(RowModel.enumRow("Wart/Crops Farm Design",FarmingConfig.WART_CROPS_DESIGN.name(),
                dev.veltraph.client.farming.domain.WartCropsDesign.class));
        rows.add(RowModel.toggle("Normal Farm Cycle Reset", FarmingConfig.FARM_CYCLE_RESET_ENABLED.name()));
        rows.add(RowModel.value("Cycle End Marker Radius", FarmingConfig.FARM_CYCLE_MARKER_RADIUS.name()));
        rows.add(RowModel.toggle("Drop And Turn", FarmingConfig.DROP_AND_TURN.name()));
        rows.add(RowModel.toggle("Allow Irregular Corners", FarmingConfig.ALLOW_IRREGULAR_CORNERS.name()));
        rows.add(RowModel.toggle("S-Shape Pause Anywhere", FarmingConfig.S_SHAPE_PAUSE_ANYWHERE.name()));
        rows.add(RowModel.enumRow("S-Shape Hold Forward", FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY.name(), HoldForwardPolicy.class));
        rows.add(RowModel.enumRow("Vertical Hold Forward", FarmingConfig.VERTICAL_HOLD_FORWARD_POLICY.name(), HoldForwardPolicy.class));
        rows.add(RowModel.enumRow("Sugarcane Direction", FarmingConfig.SUGARCANE_DIR.name(), LaneSide.class));
        rows.add(RowModel.enumRow("Sugarcane/Flower Design", FarmingConfig.SUGARCANE_FARM_DESIGN.name(), dev.veltraph.client.config.SugarcaneFarmDesign.class));
        rows.add(RowModel.enumRow("Sugarcane/Flower Crop", FarmingConfig.SUGARCANE_FARM_CROP.name(), dev.veltraph.client.config.SugarcaneFarmCrop.class));
        rows.add(RowModel.info("Ace Two-Plot", "Select while stopped. Cane and sunflower completed full native courses on the creator's layout; rose uses the source-supported selector but has no separate full-course acceptance. Published 350 speed and diagonal view are required."));
        rows.add(RowModel.enumRow("Mushroom Direction", FarmingConfig.MUSHROOM_DIR.name(), LaneSide.class));
        rows.add(RowModel.enumRow("Mushroom Farm Design", FarmingConfig.MUSHROOM_FARM_DESIGN.name(), dev.veltraph.client.config.MushroomFarmDesign.class));
        rows.add(RowModel.info("Mushroom Ring Lane", "Ring Current: W on non-trapdoor lane; S on trapdoor lane."));
        rows.add(RowModel.info("Mushroom Ring View", "Unchanged creator map: fixed published yaw -26.57 deg / pitch 0; nominal 259 speed (.259 local counterpart), supported east trapdoor entry."));
        rows.add(RowModel.info("Mushroom Ring Course", "Full unchanged-map loop accepted: 24 lower + 21 upper corners, natural stairs/four-block drop and closed return. Other rotations remain unverified."));
        rows.add(RowModel.info("Mushroom Ring Pickup", "Test-only server Auto-pickup transferred actual native loot; 2,240 delivered items, excess world loot. Complete pickup/live Hypixel remain unverified."));
        rows.add(RowModel.info("Mushroom Ring Aim", "Ring suppresses optional jitter only to hold the authored view; shared saved preferences stay unchanged."));
        rows.add(RowModel.info("Mushroom Ring Pause", "Unknown or ambiguous geometry pauses; verified corners continue using incoming wall contact and loaded outgoing support."));
        rows.add(RowModel.enumRow("Staircase Mushroom Main Dir", FarmingConfig.STAIRCASE_MUSHROOM_MAIN_DIR.name(), DirectionMode.class));
        rows.add(RowModel.enumRow("Netherwart Starting Direction", FarmingConfig.NETHERWART_STARTING_DIRECTION.name(), LaneSide.class));
        rows.add(RowModel.toggle("Netherwart Switch Dir On TP", FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP.name()));
        rows.add(RowModel.toggle("Vert Wart Switch If Still", FarmingConfig.VERT_WART_SWITCH_DIRECTION_IF_STILL.name()));
        rows.add(RowModel.toggle("Wart Fix Pitch After TP", FarmingConfig.WART_FIX_PITCH_AFTER_TP.name()));
        rows.add(RowModel.enumRow("Cocoa Farm Design", FarmingConfig.COCOA_FARM_MODE.name(),dev.veltraph.client.config.CocoaFarmMode.class));
        rows.add(RowModel.enumRow("Two-Key Cocoa Starting Side", FarmingConfig.COCOA_TWO_KEY_START_SIDE.name(),LaneSide.class));
        rows.add(RowModel.enumRow("Cocoa Lane Switch Direction", FarmingConfig.COCOA_LANE_SWITCH_DIRECTION.name(), LaneSide.class));
        rows.add(RowModel.toggle("Cocoa Sneak When Switching", FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING.name()));
        rows.add(RowModel.toggle("Cocoa Placer Auto Walk", FarmingConfig.COCOA_PLACER_AUTO_WALK.name()));
        rows.add(RowModel.enumRow("Pumpkin/Melon Farm Design", FarmingConfig.PUMPKIN_FARM_DESIGN.name(), dev.veltraph.client.config.PumpkinFarmDesign.class));
        rows.add(RowModel.info("Non-Captured Pumpkin Target", "Non-captured pumpkin target: MelonKingDE regular three-layer, one-plot model. All 3,445 original crops, three resets, zero jumps and fresh natural harvest passed. Full author coordinate asset and later variants remain pending; captured default is separate."));
        rows.add(RowModel.value("Captured Default Gourd Pitch", FarmingConfig.PUMPKIN_CAPTURED_PITCH.name()));
        rows.add(RowModel.info("Captured Gourd Method", "Captured modified-default: diagonal wall contact; all 3,350 original crops across 19 bands accepted in the private capture."));
        rows.add(RowModel.info("Captured Gourd Acceptance", "16 facing cases and actual saved-world reset/fresh natural harvest accepted; stock default, complete pickup and MelonKingDE remain separate."));
        rows.add(RowModel.info("Captured Gourd Pitch Migration", "Diagonal default 40 deg; saved 26 deg must be adjusted manually."));
        rows.add(RowModel.enumRow("MelonKingDE Starting Side", FarmingConfig.PUMPKIN_MELONKING_START_SIDE.name(), LaneSide.class));
        rows.add(RowModel.enumRow("SAIGO Regression Starting Side", FarmingConfig.PUMPKIN_SAIGO_START_SIDE.name(), LaneSide.class));
        rows.add(RowModel.toggle("Pumpkin Anti Ghost Block", FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK.name()));

        rows.add(RowModel.section("Rotation / Aim"));
        rows.add(RowModel.toggle("Smooth Mouse-GCD Quantize", FarmingConfig.ROTATION_USE_MOUSE_GCD.name()));
        rows.add(RowModel.toggle("Humanized Jitter", FarmingConfig.ROTATION_JITTER.name()));
        rows.add(RowModel.toggle("S-Shape Flip Heading On Turn", FarmingConfig.SSHAPE_FLIP_HEADING_ON_TURN.name()));
        rows.add(RowModel.toggle("Require Near-Middle Before Turn", FarmingConfig.REQUIRE_NEAR_MIDDLE_FOR_TURN.name()));

        // Batch 9 — numeric tuning is now editable in-GUI via the click-to-type value rows (the same
        // editor the Anti-Staff thresholds use), no longer config.json-only. Each binds to a live
        // FarmingConfig key (Target 6 sightline/cadence + the RotationController feel + tool slot).
        rows.add(RowModel.section("Aim Tuning"));
        rows.add(RowModel.toggle("Reachable Mature Wheat Completion",FarmingConfig.WHEAT_REACH_COMPLETION.name()));
        rows.add(RowModel.value("Crop Sightline Distance", FarmingConfig.CROP_SIGHTLINE_DISTANCE.name()));
        rows.add(RowModel.value("Crop Aim Height Offset", FarmingConfig.CROP_AIM_HEIGHT_OFFSET.name()));
        rows.add(RowModel.value("Farm Aim Pitch", FarmingConfig.FARM_AIM_PITCH.name()));
        rows.add(RowModel.value("Max Yaw °/Tick", FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK.name()));
        rows.add(RowModel.value("Max Pitch °/Tick", FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK.name()));
        rows.add(RowModel.value("Aim Acceleration", FarmingConfig.ROTATION_ACCELERATION.name()));
        rows.add(RowModel.value("Aim Tolerance °", FarmingConfig.ROTATION_TOLERANCE_DEG.name()));
        rows.add(RowModel.value("Pause-On-Error °", FarmingConfig.ROTATION_PAUSE_ERROR_DEG.name()));
        rows.add(RowModel.value("Jitter Magnitude °", FarmingConfig.ROTATION_JITTER_MAX_DEG.name()));
        rows.add(RowModel.value("Jitter Min Interval Ticks", FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS.name()));
        rows.add(RowModel.value("Jitter Max Interval Ticks", FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS.name()));

        rows.add(RowModel.section("Row / Break Tuning"));
        rows.add(RowModel.value("Legacy Synthetic Row-Step Ticks", FarmingConfig.ROW_TRANSITION_FORWARD_TICKS.name()));
        rows.add(RowModel.value("Attack Cadence Ticks", FarmingConfig.ATTACK_CADENCE_TICKS.name()));
        rows.add(RowModel.value("Near-Middle Radius", FarmingConfig.NEAR_MIDDLE_RADIUS.name()));
        rows.add(RowModel.value("Higher-Corner Radius", FarmingConfig.HIGHER_CORNER_BLOCK_RADIUS.name()));
        rows.add(RowModel.value("Turn Cooldown Ms", FarmingConfig.TURN_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Stuck Reconsider Ms", FarmingConfig.STUCK_RECONSIDER_MS.name()));
        rows.add(RowModel.value("Farming Tool Slot (-1=auto)", FarmingConfig.FARMING_TOOL_SLOT.name()));

        // Batch 4 — Garden Visitors (advisory). Target 1 (Garden Visitors full parity): the recovered six
        // independent accept-filter toggles (Config.java:1618-1623) + serve order + budget guard.
        rows.add(RowModel.section("Greenhouse Care / Combinations"));
        rows.add(RowModel.toggle("Greenhouse Actions (opt-in)", GreenhouseConfig.GREENHOUSE_ENABLED.name()));
        rows.add(RowModel.value("Greenhouse Local Profile", GreenhouseConfig.GREENHOUSE_PROFILE.name()));
        rows.add(RowModel.value("Greenhouse Plot", GreenhouseConfig.GREENHOUSE_PLOT.name()));
        rows.add(RowModel.value("Greenhouse Selected Plan (exact JSON)", GreenhouseConfig.GREENHOUSE_PLAN.name()));
        rows.add(RowModel.value("Greenhouse Observed Diagnostic Bindings (exact JSON)", GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS.name()));
        rows.add(RowModel.value("Greenhouse Maximum Actions", GreenhouseConfig.GREENHOUSE_MAX_ACTIONS.name()));
        rows.add(RowModel.value("Greenhouse Observation Maximum Age (ms)", GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS.name()));
        rows.add(RowModel.value("Greenhouse Action Deadline (ms)", GreenhouseConfig.GREENHOUSE_ACTION_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Greenhouse Physical Return Deadline (ms)", GreenhouseConfig.GREENHOUSE_RETURN_TIMEOUT_MS.name()));
        rows.add(RowModel.info("Greenhouse Preparation", "Actions default off; select profile/plot and exact native Diagnostic Tool bindings. /vgreenhouse inspect / show / fingerprint / capture supply observations; care reviews observed plant state and separate water/decay deadlines."));
        rows.add(RowModel.info("Greenhouse Guide / Plan", "/vgreenhouse guide / combination require current visible ingredient counts and exact proximity. water or plan prepares a bounded selection; check then explicit start uses the shared guard. Captured blocks and historical recipes do not establish readiness."));
        rows.add(RowModel.info("Greenhouse Archived Observations", "System-managed GREENHOUSE_OBSERVATIONS is persisted archival data, read-only here; /vgreenhouse status reports it separately. Only refreshed native diagnostics can authorize care after restart."));
        rows.add(RowModel.info("Greenhouse Captured Menus", "System-managed GREENHOUSE_CAPTURED_MENUS retains versioned Guide and Diagnostics observations. It is read-only here; menu data without a known target cannot authorize planting or care actions."));
        rows.add(RowModel.info("Greenhouse Unresolved Actions", "System-managed GREENHOUSE_UNRESOLVED_ACTIONS is a durable no-retry journal, read-only here. Check the actual result, refresh the exact cell, then /vgreenhouse reconcile applied|not-applied; clearing a plan never clears this journal."));
        rows.add(RowModel.info("Greenhouse Validation", "Exact tool/resource fingerprints, independent newer plant and inventory receipts, loaded supported approach and physical return are required. Native execution and live Hypixel acceptance remain pending; entity cultivation and unobserved recipes remain unsupported."));

        rows.add(RowModel.section("Garden Visitors"));
        rows.add(RowModel.toggle("Visitor Advisor", VisitorConfig.VISITOR_ADVISOR_ENABLED.name()));
        rows.add(RowModel.toggle("Visitor Transactions", VisitorConfig.VISITOR_AUTO_SERVE.name()));
        rows.add(RowModel.value("Visitor NPC Name (exact loaded name)", VisitorConfig.VISITOR_NPC_NAME.name()));
        rows.add(RowModel.toggle("Ordered Visitor Cycle", VisitorConfig.VISITOR_MULTI_SERVE.name()));
        rows.add(RowModel.value("Cycle NPC Names (comma-separated exact names)", VisitorConfig.VISITOR_NPC_NAMES.name()));
        rows.add(RowModel.value("Max Visitors per Cycle", VisitorConfig.VISITOR_MAX_VISITORS.name()));
        rows.add(RowModel.value("Cycle Total Cost Cap (coins)", VisitorConfig.VISITOR_CYCLE_MAX_COST.name()));
        rows.add(RowModel.value("Cycle Sort Origin (x,y,z; blank=start)", VisitorConfig.VISITOR_ORDER_ORIGIN.name()));
        rows.add(RowModel.toggle("Visitor Named Loadout Swap", VisitorConfig.VISITOR_LOADOUT_SWAP.name()));
        rows.add(RowModel.value("Visitor Loadout Name", VisitorConfig.VISITOR_LOADOUT_NAME.name()));
        rows.add(RowModel.value("Named Loadout Bindings (shared exact JSON)", dev.veltraph.client.config.PestLoadoutConfig.PEST_NAMED_LOADOUT_BINDINGS.name()));
        rows.add(RowModel.info("Visitor Cycle Setup", "Separate opt-in; exact loaded NPC queue and positive cycle/offer caps required. Empty sort origin uses the helper start pose."));
        rows.add(RowModel.info("Visitor Loadout Setup", "Swap defaults off; configure shared exact named gear profiles for both visitor and current farming equipment before enabling."));
        rows.add(RowModel.toggle("Buy Missing Visitor Items", VisitorConfig.VISITOR_BUY_MISSING.name()));
        rows.add(RowModel.toggle("Try Visitor Sacks First", VisitorConfig.VISITOR_TRY_SACKS_FIRST.name()));
        rows.add(RowModel.value("Max Serve Item Quantity", VisitorConfig.VISITOR_MAX_SERVE_QUANTITY.name()));
        rows.add(RowModel.toggle("Accept: All", VisitorConfig.VISITOR_ACCEPT_FILTER_ALL.name()));
        rows.add(RowModel.toggle("Accept: Valuable Drops", VisitorConfig.VISITOR_ACCEPT_FILTER_VALUABLE_DROPS.name()));
        rows.add(RowModel.toggle("Accept: New Visitors", VisitorConfig.VISITOR_ACCEPT_FILTER_NEW_VISITORS.name()));
        rows.add(RowModel.toggle("Accept: Profitable (copper)", VisitorConfig.VISITOR_ACCEPT_FILTER_PROFITABLE.name()));
        rows.add(RowModel.toggle("Accept: Beth", VisitorConfig.VISITOR_ACCEPT_FILTER_BETH.name()));
        rows.add(RowModel.toggle("Accept: Seymour", VisitorConfig.VISITOR_ACCEPT_FILTER_SEYMOUR.name()));
        rows.add(RowModel.enumRow("Cycle Distance Order", VisitorConfig.VISITOR_ORDER.name(), SortOrder.class));
        rows.add(RowModel.toggle("Enforce Max Cost", VisitorConfig.VISITOR_HAVE_MAX_PRICES.name()));
        rows.add(RowModel.value("Visitor Max Total Cost (coins)", VisitorConfig.VISITOR_MAX_COST.name()));
        rows.add(RowModel.toggle("Auto-Sell Rewards After", VisitorConfig.VISITOR_AUTO_SELL_AFTER.name()));
        rows.add(RowModel.section("Spray Current Garden Plot"));
        rows.add(RowModel.toggle("Sprayonator Enabled", PestConfig.PESTS_USE_SPRAYONATOR.name()));
        rows.add(RowModel.toggle("Selected-Plot Sprayonator Cycle", PestConfig.PEST_SPRAY_PARENT_ENABLED.name()));
        rows.add(RowModel.value("Selected-Plot Spray Bindings (JSON)", PestConfig.PEST_SPRAY_PARENT_BINDINGS.name()));
        rows.add(RowModel.value("Spray Parent Hotbar Slot (0-8)", PestConfig.PEST_SPRAY_PARENT_HOTBAR_SLOT.name()));
        rows.add(RowModel.value("Spray Parent Deadline (ms)", PestConfig.PEST_SPRAY_PARENT_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Current Plot Number", PestConfig.PEST_SPRAY_PLOT.name()));
        rows.add(RowModel.value("Spray Material Name (exact)", PestConfig.PEST_SPRAY_MATERIAL.name()));
        rows.add(RowModel.value("Selected Spray Lore (exact line)", PestConfig.PEST_SPRAY_SELECTION_LORE.name()));
        rows.add(RowModel.value("Expected Spray Units", PestConfig.PEST_SPRAY_UNITS.name()));
        rows.add(RowModel.value("Spray Unit Value", PestConfig.PEST_SPRAY_UNIT_VALUE.name()));
        rows.add(RowModel.value("Spray Total Value Cap", PestConfig.PEST_SPRAY_MAX_VALUE.name()));
        rows.add(RowModel.toggle("Spray Only During Contest", PestConfig.PEST_SPRAY_CONTEST_ONLY.name()));
        rows.add(RowModel.value("Spray Acknowledgment Timeout (ms)", PestConfig.PEST_SPRAY_ACK_TIMEOUT_MS.name()));
        rows.add(RowModel.section("Configured Garden Transactions"));
        rows.add(RowModel.info("Pest Setup", "Exact bindings required; current-plot transactions only."));
        rows.add(RowModel.toggle("Collect Pest Traps", PestConfig.COLLECT_PEST_TRAPS.name()));
        rows.add(RowModel.section("Contest-triggered Trap Maintenance"));
        rows.add(RowModel.toggle("Selected Jacob reward after contest", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_ENABLED.name()));
        rows.add(RowModel.value("Jacob claim menu command (blank unset)", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_COMMAND.name()));
        rows.add(RowModel.value("Jacob exact claim menu title", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_TITLE.name()));
        rows.add(RowModel.value("Jacob exact selected row", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_ROW.name()));
        rows.add(RowModel.value("Jacob before lore (exact lines)", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_BEFORE_LORE.name()));
        rows.add(RowModel.value("Jacob claimed lore (exact lines)", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_AFTER_LORE.name()));
        rows.add(RowModel.value("Jacob exact server acknowledgment", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_ACK.name()));
        rows.add(RowModel.value("Jacob ticket item registry identity", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_TICKET_ITEM.name()));
        rows.add(RowModel.value("Jacob ticket full component identity", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_TICKET_COMPONENTS.name()));
        rows.add(RowModel.value("Jacob exact expected tickets", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_TICKETS.name()));
        rows.add(RowModel.value("Jacob claim deadline (ms)", dev.veltraph.client.config.JacobRewardConfig.JACOB_REWARD_TIMEOUT_MS.name()));
        rows.add(RowModel.toggle("Automatic Trap Maintenance", dev.veltraph.client.config.PestTrapMaintenanceConfig.PEST_TRAP_MAINTENANCE_ENABLED.name()));
        rows.add(RowModel.toggle("Contest-only Trap Dispatch", dev.veltraph.client.config.PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_ONLY.name()));
        rows.add(RowModel.value("Exact Ready-trap Tab Prefix (blank unset)", dev.veltraph.client.config.PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX.name()));
        rows.add(RowModel.value("Strict Contest Cutoff (seconds; -1 unset)", dev.veltraph.client.config.PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_CUTOFF_SECONDS.name()));
        rows.add(RowModel.value("Trap Maintenance Deadline (ms)", dev.veltraph.client.config.PestTrapMaintenanceConfig.PEST_TRAP_MAINTENANCE_TIMEOUT_MS.name()));
        rows.add(RowModel.info("Trap Dispatch Evidence", "Requires one enabled Garden farm, 20 actual scoped callbacks, a known contest clock strictly below the configured cutoff and an exact native tab prefix with literal # markers. Unknown prefix or cutoff blocks dispatch."));
        rows.add(RowModel.info("Trap Completion", "Processes one configured loaded trap. Native release/bait evidence and one ready-marker decrement create the action receipt; exact saved tool, gear, cursor, pose and same-farm return must then complete before resume. No Jacob reward claim is made."));
        rows.add(RowModel.toggle("Refill Trap Bait", PestConfig.PEST_REFILL_TRAP_BAIT.name()));
        rows.add(RowModel.toggle("Use Repellent", PestConfig.PESTS_USE_REPELLENT.name()));
        rows.add(RowModel.toggle("Pest Cleaner Enabled", PestConfig.PEST_CLEANER_ENABLED.name()));
        rows.add(RowModel.toggle("Leave One Pest at Automatic Start", PestConfig.LEAVE_ONE_PEST.name()));
        rows.add(RowModel.section("Automatic Garden Threshold Admission"));
        rows.add(RowModel.toggle("Start Visitors While Farming", dev.veltraph.client.config.GardenAdmissionConfig.AUTOSTART_VISITOR_DURING_FARMING.name()));
        rows.add(RowModel.toggle("Start Pests While Farming", dev.veltraph.client.config.GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING.name()));
        rows.add(RowModel.value("Visitor Threshold Range", dev.veltraph.client.config.GardenAdmissionConfig.ACTIVATE_VISITOR_THRESHOLD.name()));
        rows.add(RowModel.value("Pest Threshold Range", dev.veltraph.client.config.GardenAdmissionConfig.ACTIVATE_PEST_THRESHOLD.name()));
        rows.add(RowModel.toggle("Allow Visitors During Contest", dev.veltraph.client.config.GardenAdmissionConfig.DO_VISITORS_IN_CONTEST_2.name()));
        rows.add(RowModel.toggle("Allow Pests During Contest", dev.veltraph.client.config.GardenAdmissionConfig.DO_PESTS_IN_CONTEST_2.name()));
        rows.add(RowModel.value("Garden Count and Capability Mapping (exact JSON)", dev.veltraph.client.config.GardenAdmissionConfig.GARDEN_ADMISSION_BINDINGS.name()));
        rows.add(RowModel.info("Automatic Admission Evidence", "Visitor and pest JSON mappings are independently optional. An enabled visitor with unknown priority evidence blocks automatic dispatch. The first admitted family keeps priority; unsupported instant scheduling cannot substitute another helper. Existing helper setup, return proof and safety checks remain required."));
        rows.add(RowModel.toggle("Observed Pest Automatic Admission", dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_ENABLED.name()));
        rows.add(RowModel.value("Observed Pest Parent Bindings (exact JSON)", dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_BINDINGS.name()));
        rows.add(RowModel.section("Nearby Visible Pest Admission"));
        rows.add(RowModel.toggle("Nearby Pest Admission", dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_NEARBY_ENABLED.name()));
        rows.add(RowModel.value("Nearby FOV (degrees; 0 unset)", dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_NEARBY_FOV_DEGREES.name()));
        rows.add(RowModel.value("Continuous Target Dwell (ticks; 0 unset)", dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_NEARBY_DWELL_TICKS.name()));
        rows.add(RowModel.value("Maximum Motion per Tick (blocks; 0 unset)", dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_NEARBY_MAXIMUM_STEP.name()));
        rows.add(RowModel.info("Nearby Pest Setup", "Off until explicit local FOV, dwell and motion limits are set. Uses the exact full tracker model, vacuum range and received census below; requires Leave One Pest off. The same visible nearby entity must persist throughout dwell. No menu, particle or server cooldown is inferred."));
        rows.add(RowModel.toggle("Pest Supply Refill", PestConfig.PEST_SUPPLY_REFILL_ENABLED.name()));
        rows.add(RowModel.value("Trap Bindings (exact JSON)", PestConfig.PEST_TRAP_BINDINGS.name()));
        rows.add(RowModel.value("Repellent Bindings (exact JSON)", PestConfig.PEST_REPELLENT_BINDINGS.name()));
        rows.add(RowModel.value("Cleaner Bindings (exact JSON)", PestConfig.PEST_CLEANER_BINDINGS.name()));
        rows.add(RowModel.value("Supply Bindings (exact JSON)", PestConfig.PEST_SUPPLY_BINDINGS.name()));
        rows.add(RowModel.value("Pest Action Plot", PestConfig.PEST_ACTION_PLOT.name()));
        rows.add(RowModel.toggle("Pest Actions Contest Only", PestConfig.PEST_ACTION_CONTEST_ONLY.name()));
        rows.add(RowModel.value("Pest Action Timeout (ms)", PestConfig.PEST_ACTION_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Pest Action Hotbar Slot (0-8)", PestConfig.PEST_ACTION_HOTBAR_SLOT.name()));
        rows.add(RowModel.value("Pest Supply Total Cost Cap", PestConfig.PEST_SUPPLY_MAX_COST.name()));
        rows.add(RowModel.value("Pest Supply Unit Cost Cap", PestConfig.PEST_SUPPLY_MAX_UNIT_COST.name()));
        rows.add(RowModel.toggle("Sprayonator Try Sacks First", PestConfig.SPRAYONATOR_TRY_SACKS_FIRST.name()));
        rows.add(RowModel.section("Leased Pest / Box2 Maintenance"));
        rows.add(RowModel.toggle("Pest Farm Maintenance", dev.veltraph.client.config.PestLoadoutConfig.PEST_MAINTENANCE_ENABLED.name()));
        rows.add(RowModel.value("Pest Maintenance Deadline (ms)", dev.veltraph.client.config.PestLoadoutConfig.PEST_MAINTENANCE_TIMEOUT_MS.name()));
        rows.add(RowModel.toggle("Pest Named Loadout Swap", dev.veltraph.client.config.PestLoadoutConfig.PEST_LOADOUT_SWAP.name()));
        rows.add(RowModel.value("Farming Loadout Name (current original)", dev.veltraph.client.config.PestLoadoutConfig.PEST_FARMING_LOADOUT.name()));
        rows.add(RowModel.value("Pest Loadout Name", dev.veltraph.client.config.PestLoadoutConfig.PEST_PEST_LOADOUT.name()));
        rows.add(RowModel.toggle("Use Killing Loadout Name (requires swap)", dev.veltraph.client.config.PestLoadoutConfig.PEST_LOADOUT_SWAP_KILLING.name()));
        rows.add(RowModel.value("Killing Loadout Name", dev.veltraph.client.config.PestLoadoutConfig.PEST_LOADOUT_SWAP_KILLING_LOADOUT.name()));
        rows.add(RowModel.toggle("Assigned Box2 Cleaning", dev.veltraph.client.config.PestLoadoutConfig.PESTS_USE_BOX_2.name()));
        rows.add(RowModel.value("Box2 Bindings (exact grid/spawn JSON)", dev.veltraph.client.config.PestLoadoutConfig.PEST_BOX_BINDINGS.name()));
        rows.add(RowModel.info("Pest Maintenance Setup", "Off by default; one eligible running Garden farm, cleaner opt-in, exact bindings and configured plot required. Deadline must be at least 1,000 ms."));
        rows.add(RowModel.info("Pest Named Gear Proof", "Use the shared Named Loadout Bindings above. Current original name must match Farming Loadout Name; fresh receipt and full armor/offhand must prove restoration."));
        rows.add(RowModel.info("Box2 Setup", "Empty bindings cannot start. First enabled grid assignment needs exact plot/spawn/yaw/pitch; local delay 2-30 ticks and spawn wait 100-30,000 ms."));
        rows.add(RowModel.info("Box2 Completion", "Fresh native spawn receipt is required even at the same pose. Observe occupied then empty plus exact bag/receipt proof; remaining plot pests return through assigned spawn before normal cleaning."));
        rows.add(RowModel.info("Early Loadout Availability", "Early loadout swapping is not available in live play yet. It requires configured farming and pest loadouts, matching equipment and validated Garden signals. Empty bindings cannot start a swap; current equipment is kept."));
        rows.add(RowModel.info("Early Swap / Spray During Farming", "Early swap controls are unavailable while game signals and gear changes remain unvalidated. Swapping defaults off; automatic spraying while farming remains unsupported."));
        rows.add(RowModel.section("Particle-Guided Pest Maintenance"));
        rows.add(RowModel.toggle("Particle Tracker Maintenance", dev.veltraph.client.config.PestTrackerConfig.PEST_TRACKED_MAINTENANCE_ENABLED.name()));
        rows.add(RowModel.value("Tracker Bindings (JSON)", dev.veltraph.client.config.PestTrackerConfig.PEST_TRACKED_BINDINGS.name()));
        rows.add(RowModel.value("Tracker Deadline (ms)", dev.veltraph.client.config.PestTrackerConfig.PEST_TRACKED_TIMEOUT_MS.name()));
        rows.add(RowModel.toggle("Tracker During Contests Only", dev.veltraph.client.config.PestTrackerConfig.PEST_TRACKED_CONTEST_ONLY.name()));
        rows.add(RowModel.toggle("Pest Roof Etherwarping", dev.veltraph.client.config.PestTrackerConfig.PESTS_USE_ROOF_ETHERWARPING.name()));
        rows.add(RowModel.value("Pest Roof Bindings (JSON)", dev.veltraph.client.config.PestTrackerConfig.PEST_ROOF_BINDINGS.name()));
        rows.add(RowModel.info("Full Pest Tracker Travel", "Particle-guided capture and exact farm return have native component acceptance. Production registration is being verified. Roof, unstuck and automatic threshold policies remain incomplete."));
        rows.add(RowModel.section("Philip Pest-Bag Exchange"));
        rows.add(RowModel.toggle("Philip Exchange Permission", dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_ENABLED.name()));
        rows.add(RowModel.value("Philip Bindings (JSON)", dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_BINDINGS.name()));
        rows.add(RowModel.toggle("Philip Farm Maintenance", dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_MAINTENANCE_ENABLED.name()));
        rows.add(RowModel.value("Philip Maintenance Deadline (ms)", dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_MAINTENANCE_TIMEOUT_MS.name()));
        rows.add(RowModel.info("Philip Exchange Scope", "Current command contact, separate legacy phone contact, barn interaction, exact bag exchange and same-farm return have native component acceptance. Configure explicit metadata; automatic thresholds and production-factory verification remain separate."));
        return rows;
    }

    /** Core fishing and its opt-in local subflows. */
    private static List<RowModel> fishingRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Fishing"));
        rows.add(RowModel.toggle("Select Rod Automatically", dev.veltraph.client.fishing.setup.FishingSetupConfig.FISHING_AUTO_ROD.name()));
        rows.add(RowModel.value("Observed Rod Rankings (JSON)", dev.veltraph.client.fishing.setup.FishingSetupConfig.FISHING_ROD_PROFILE.name()));
        rows.add(RowModel.info("Automatic Rod Selection", "Configure observed water/lava rod names and the excluded name before enabling selection. Uses hotbar slots 1–8; slot 9 remains reserved. Fishing starts after the selected rod is observed, and manual slot changes stop setup."));
        rows.add(RowModel.toggle("Golden Fish", FishingConfig.FISHING_GOLDEN_FISH.name()));
        rows.add(RowModel.toggle("Underwater Worm Hunting", FishingConfig.FISHING_WORM_HUNTING.name()));
        rows.add(RowModel.enumRow("Worm Hunting Mode", FishingConfig.FISHING_WORM_MODE.name(), dev.veltraph.client.fishing.worm.WormHuntingMode.class));
        rows.add(RowModel.value("Worm Count Range (min:max)", FishingConfig.WORM_ENTITY_THRESHOLD_RANGE_2.name()));
        rows.add(RowModel.value("Worm Wait Range (minutes, min:max)", FishingConfig.WORM_TIME_THRESHOLD_RANGE_2.name()));
        rows.add(RowModel.toggle("Turn Before Dreadlord Use", FishingConfig.WORM_DREADLORD_TURN.name()));
        rows.add(RowModel.info("Worm Hunting", "Source Threshold waits for the nearby worm count or elapsed time, then uses your Dreadlord Sword and returns to the fishing view. Turning aims behind and below you; with turning off, it aims down. Legacy Melee uses your selected creature names and melee weapon."));
        rows.add(RowModel.toggle("Sea-Creature Auto-Killer", FishingConfig.FISHING_AUTO_KILLER.name()));
        rows.add(RowModel.enumRow("Auto-Killer Weapon", FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE.name(), dev.veltraph.client.fishing.mc.FishingAutoKillerWeaponType.class));
        rows.add(RowModel.toggle("Ignore Squid", FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID.name()));
        rows.add(RowModel.toggle("Use Hyperion for Fishing Combat", dev.veltraph.client.fishing.offense.FishingOffenseConfig.FISHING_USE_HYPERION.name()));
        rows.add(RowModel.value("Fishing Combat Profile (exact JSON)", dev.veltraph.client.fishing.offense.FishingOffenseConfig.FISHING_OFFENSIVE_PROFILE.name()));
        rows.add(RowModel.info("Fishing Combat Setup", "Configure the actual ability item, target, mana cost, pacing and receipt. Each cast needs fresh mana, ability and damage confirmation; uncertain results stop combat. Emergency healing takes priority."));
        rows.add(RowModel.value("Creature Names", FishingConfig.FISHING_KILL_TARGETS.name()));
        rows.add(RowModel.enumRow("Bite Detection", FishingConfig.FISHING_BITE_DETECTION.name(),
                FishingDetectionMode.class));
        rows.add(RowModel.toggle("Humanize Catch Delay", FishingConfig.FISHING_CATCH_HUMANIZE_DELAY.name()));
        // Batch 9: the numeric tunables are now editable in-GUI (was config.json-only).
        rows.add(RowModel.value("Catch Delay (s)", FishingConfig.FISHING_CATCH_DELAY.name()));
        rows.add(RowModel.value("Minimum Cast Age (s)", FishingConfig.FISHING_MIN_CATCH_SECONDS.name()));
        rows.add(RowModel.toggle("Slug Fish Timing", FishingConfig.FISHING_SLUG_FISH.name()));
        rows.add(RowModel.value("Slug Fish Profile (exact tab JSON)", FishingConfig.FISHING_SLUG_PROFILE.name()));
        rows.add(RowModel.value("Cast Timeout (s)", FishingConfig.FISHING_CAST_TIMEOUT_SECONDS.name()));
        rows.add(RowModel.value("Bobber Dip Threshold (Y-vel)", FishingConfig.FISHING_BOBBER_DIP_THRESHOLD.name()));
        rows.add(RowModel.toggle("Surface on Low Bubbles", FishingConfig.FISHING_SURFACE_ON_LOW_BUBBLES.name()));
        rows.add(RowModel.value("Surface Below Bubbles (0-10)", FishingConfig.FISHING_SURFACE_BUBBLES.name()));
        rows.add(RowModel.value("Surface Debounce (ms)", FishingConfig.FISHING_SURFACE_DEBOUNCE_MS.name()));
        rows.add(RowModel.section("Crimson Isle Grappleshot Blazes"));
        rows.add(RowModel.toggle("Grappleshot Blazes", FishingConfig.FISHING_GRAPPLESHOT_BLAZES.name()));
        rows.add(RowModel.value("Grapple Profile (exact tool/ability JSON)", FishingConfig.FISHING_GRAPPLE_PROFILE.name()));
        rows.add(RowModel.info("Grapple Setup", "Off by default; {} cannot act. Crimson Isle/native Blaze only: exact tool, range/health/consumption, pacing and request/stage timeouts required."));
        rows.add(RowModel.info("Grapple Source Gates", "14-block far threshold or hazard 5/2-sample hysteresis; aim within 3 degrees. Existing tethers suppress a new request."));
        rows.add(RowModel.info("Grapple Outcome", "Fresh Bat riding ArmorStand with Bat leashed to owned player, received Blaze pull and declared consumption required; tether alone cannot complete. Rod and physical cast position/view return before casting."));
        rows.add(RowModel.info("Grapple Limits", "Received health, cooldown, loaded collision and line of sight remain guarded; manual slot/world changes cancel. No generic player grapple or consumable-tool/live parity claim."));
        rows.add(RowModel.section("Configured Power Orb Support"));
        rows.add(RowModel.toggle("Black Hole Support", FishingConfig.FISHING_BLACK_HOLE.name()));
        rows.add(RowModel.value("Black Hole Profile (exact tool/effect JSON)", FishingConfig.FISHING_BLACK_HOLE_PROFILE.name()));
        rows.add(RowModel.section("Fishing Junk Disposal"));
        rows.add(RowModel.toggle("Discard Configured Fishing Junk", dev.veltraph.client.fishing.trash.FishingTrashConfig.FISHING_THROW_TRASH.name()));
        rows.add(RowModel.toggle("Find Nearby Cliff Automatically", dev.veltraph.client.fishing.trash.FishingTrashConfig.FISHING_USE_AUTOMATIC_TRASH_LOCATIONS.name()));
        rows.add(RowModel.value("Fishing Junk Profile", dev.veltraph.client.fishing.trash.FishingTrashConfig.FISHING_TRASH_PROFILE.name()));
        rows.add(RowModel.info("Junk Disposal Setup", "Disabled by default. Configure the exact eight source item/lore rules and timings. The workflow finds a loaded safe cliff, verifies each whole-stack drop, then returns to the saved fishing location. Private native acceptance is pending."));
        rows.add(RowModel.toggle("Auto Power Orb", FishingConfig.FISHING_AUTO_POWER_ORB.name()));
        rows.add(RowModel.value("Orb Profiles (ordered exact JSON array)", FishingConfig.FISHING_ORB_PROFILES.name()));
        rows.add(RowModel.info("Orb Setup", "Off by default; [] cannot enable support. Declare exact tool/effect identities, duration, mana/item cost and minimum interval."));
        rows.add(RowModel.info("Orb Acknowledgment", "Fresh mana, received effect and declared consumption must agree; missing acknowledgment stops fishing and restores the owned rod."));
        rows.add(RowModel.section("Configured Park Rain Support"));
        rows.add(RowModel.toggle("Auto Rain", FishingConfig.FISHING_AUTO_RAIN.name()));
        rows.add(RowModel.value("Rain Profile (exact JSON contracts)", FishingConfig.FISHING_RAIN_PROFILE.name()));
        rows.add(RowModel.info("Rain Setup", "Off by default; {} cannot buy. Park only: exact NPC UUID/type/name/anchor, menu, bucket, quote, purse and receipt contracts required."));
        rows.add(RowModel.info("Rain Purchase Bounds", "Declare target minutes, minimum check interval and positive total cost cap. Each purchase needs receipt, purse debit, timer increase and actual rain before another click."));
        rows.add(RowModel.section("Hotbar Totem Placement"));
        rows.add(RowModel.toggle("Auto Totem", FishingConfig.FISHING_AUTO_TOTEM.name()));
        rows.add(RowModel.value("Totem Item Name (exact)", FishingConfig.FISHING_TOTEM_ITEM_NAME.name()));
        rows.add(RowModel.value("Totem Marker Name (exact)", FishingConfig.FISHING_TOTEM_MARKER_NAME.name()));
        rows.add(RowModel.value("Totem Refresh Interval (ms)", FishingConfig.FISHING_TOTEM_REFRESH_MS.name()));
        rows.add(RowModel.section("Configured Bait Restocking"));
        rows.add(RowModel.info("Bait Setup", "Setup: exact bait JSON profile required; {} cannot buy."));
        rows.add(RowModel.toggle("Auto Bait", FishingConfig.FISHING_AUTO_BAIT.name()));
        rows.add(RowModel.value("Bait Profile (exact JSON contracts)", FishingConfig.FISHING_BAIT_PROFILE.name()));
        rows.add(RowModel.enumRow("Bait Destination", FishingConfig.FISHING_BAIT_LOCATION.name(), dev.veltraph.client.fishing.bait.BaitLocation.class));
        rows.add(RowModel.value("Bait Bought/Transfer Stack Cap", FishingConfig.FISHING_BAIT_MAX_STACKS.name()));
        rows.add(RowModel.value("Bait Maximum Unit Price", FishingConfig.FISHING_BAIT_MAX_UNIT_PRICE.name()));
        rows.add(RowModel.value("Bait Session Cost Cap", FishingConfig.FISHING_BAIT_MAX_COST.name()));
        rows.add(RowModel.value("Bait Check Interval (ms)", FishingConfig.FISHING_BAIT_CHECK_INTERVAL_MS.name()));
        rows.add(RowModel.toggle("Bait Ironman (blocks buying)", FishingConfig.FISHING_BAIT_IRONMAN.name()));
        rows.add(RowModel.info("Fishing Travel Scope", "Grappleshot, hotbar totem and bait flows are available. The historical TNT route belongs to Sven travel; fishing location setup compilation remains separate."));
        return rows;
    }

    /** Local melee, ranged Zealot, healing and quest settings; full boss choreography remains separate. */
    private static List<RowModel> combatRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Sven Cave Parent"));
        rows.add(RowModel.enumRow("Sven Cave", dev.veltraph.client.combat.sven.SvenConfig.SVEN_CAVE_TYPE.name(), dev.veltraph.client.combat.sven.SvenCaveProfile.class));
        rows.add(RowModel.value("Sven Target Names", dev.veltraph.client.combat.sven.SvenConfig.SVEN_TARGET_NAMES.name()));
        rows.add(RowModel.value("Sven Source Binding Profile", dev.veltraph.client.combat.sven.SvenConfig.SVEN_SOURCE_BINDING_PROFILE.name()));
        rows.add(RowModel.value("Sven Resource Name", dev.veltraph.client.combat.sven.SvenConfig.SVEN_RESOURCE_NAME.name()));
        rows.add(RowModel.value("Sven Alternate Resource Name", dev.veltraph.client.combat.sven.SvenConfig.SVEN_RESOURCE_ALTERNATIVE_NAME.name()));
        rows.add(RowModel.value("Sven Purchase Receipt Prefix", dev.veltraph.client.combat.sven.SvenConfig.SVEN_PURCHASE_RECEIPT_PREFIX.name()));
        rows.add(RowModel.value("Sven Purchased Item Receipt", dev.veltraph.client.combat.sven.SvenConfig.SVEN_PURCHASE_RECEIPT_ITEM.name()));
        rows.add(RowModel.value("Sven Travel Denial Message", dev.veltraph.client.combat.sven.SvenConfig.SVEN_TRAVEL_DENIED_MESSAGE.name()));
        rows.add(RowModel.value("Sven Bazaar Retry Messages", dev.veltraph.client.combat.sven.SvenConfig.SVEN_BAZAAR_RETRY_MESSAGES.name()));
        rows.add(RowModel.toggle("Sven Source Tool Swap", dev.veltraph.client.combat.sven.SvenConfig.SVEN_AUTO_TOOL_SWAP.name()));
        rows.add(RowModel.toggle("Sven Automatic Quest Policy", dev.veltraph.client.combat.sven.SvenConfig.SVEN_AUTO_SLAYER.name()));
        rows.add(RowModel.info("Sven Setup", "Exact resource, menu and helper bindings are required. DARK_OAK, ACACIA and JUNGLE use recovered cave geometry. Private native proof and issued-warp continuity are pending; automatic quest policy and tool ranking remain unbound."));
        rows.add(RowModel.section("Melee Grinding"));
        rows.add(RowModel.value("Weapon Slot (1-9)", dev.veltraph.client.config.CombatConfig.COMBAT_WEAPON_SLOT.name()));
        rows.add(RowModel.value("Search Radius", dev.veltraph.client.config.CombatConfig.COMBAT_SEARCH_RADIUS.name()));
        rows.add(RowModel.value("Approach View Window (degrees)", dev.veltraph.client.config.CombatConfig.COMBAT_VIEW_WINDOW.name()));
        rows.add(RowModel.value("Explosion Retreat Distance", dev.veltraph.client.config.CombatConfig.COMBAT_RETREAT_DISTANCE.name()));
        rows.add(RowModel.value("Melee Reach", dev.veltraph.client.config.CombatConfig.COMBAT_MELEE_REACH.name()));
        rows.add(RowModel.value("Target Timeout (ms)", dev.veltraph.client.config.CombatConfig.COMBAT_TARGET_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Bestiary Targets", dev.veltraph.client.config.CombatConfig.BESTIARY_TARGET_NAMES.name()));
        rows.add(RowModel.value("Contextual Combat Weapon Profile", dev.veltraph.client.config.CombatConfig.COMBAT_CONTEXTUAL_WEAPON_PROFILE.name()));
        rows.add(RowModel.value("Preferred Bestiary Weapon", dev.veltraph.client.config.CombatConfig.BESTIARY_PREFERRED_WEAPON.name()));
        rows.add(RowModel.section("Zealot Ranged"));
        rows.add(RowModel.toggle("Zealot Arena (workflow in development)", ZealotConfig.ZEALOT_ARENA_ENABLED.name()));
        rows.add(RowModel.value("Zealot Arena Profile (exact JSON)", ZealotConfig.ZEALOT_ARENA_PROFILE.name()));
        rows.add(RowModel.toggle("Arena Dragon Failsafe", ZealotConfig.PRECURSOR_DRAGON_FAILSAFE.name()));
        rows.add(RowModel.toggle("Arena Displacement Failsafe", ZealotConfig.PRECURSOR_NOT_AT_POS_FAILSAFE.name()));
        rows.add(RowModel.toggle("Arena Idle Look Around", ZealotConfig.PRECURSOR_LOOK_AROUND.name()));
        rows.add(RowModel.info("Zealot Arena Scope", "Off by default. Source arena routes, Precursor beam lifecycle and dragon yielding are under native verification; exact route, tool, helmet, mana and receipt metadata is required."));
        rows.add(RowModel.value("Zealot Weapon Slot (1-9)", ZealotConfig.ZEALOT_WEAPON_SLOT.name()));
        rows.add(RowModel.value("Zealot Target Radius", ZealotConfig.ZEALOT_RANGED_RADIUS.name()));
        rows.add(RowModel.value("Zealot Preferred Distance", ZealotConfig.ZEALOT_RANGED_DISTANCE.name()));
        rows.add(RowModel.value("Zealot Shot Interval (ms)", ZealotConfig.ZEALOT_SHOT_INTERVAL_MS.name()));
        rows.add(RowModel.toggle("Zealot Always Jump", ZealotConfig.ZEALOT_ALWAYS_BE_JUMPING.name()));
        rows.add(RowModel.toggle("Juju Occasional Sneak", ZealotConfig.JUJU_RANDOMLY_SNEAK.name()));
        rows.add(RowModel.toggle("Collect Summoning Eyes", ZealotConfig.ZEALOT_COLLECT_EYES.name()));
        rows.add(RowModel.value("Zealot Saved Route Name", ZealotConfig.ZEALOT_RANGED_ROUTE.name()));
        rows.add(RowModel.toggle("Zealot Quiver Refill", ZealotConfig.ZEALOT_REFILL_QUIVER.name()));
        rows.addAll(quiverRows());
        rows.add(RowModel.section("George Pet Batch"));
        rows.add(RowModel.value("George Pet Batch Profile (JSON)", dev.veltraph.client.config.GeorgeBatchConfig.GEORGE_BATCH_PROFILE.name()));
        rows.add(RowModel.value("George Hub and NPC Profile (JSON)", dev.veltraph.client.config.GeorgeBatchConfig.GEORGE_SOURCE_HUB_PROFILE.name()));
        rows.add(RowModel.value("George Garden Warp Landing (JSON)", dev.veltraph.client.config.GeorgeBatchConfig.GEORGE_GARDEN_WARP_LANDING.name()));
        rows.add(RowModel.info("George Route Setup", "Provide the known Hub NPC and Garden warp landing. The original farming position is saved separately for the final return. An empty Hub profile keeps the configured contact route."));
        rows.add(RowModel.section("Dedicated Ghost Grind"));
        rows.add(RowModel.enumRow("Ghost Movement Workflow", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW.name(), dev.veltraph.client.combat.ghost.GhostMovementWorkflow.class));
        rows.add(RowModel.enumRow("Ghost Destination", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_DESTINATION.name(), dev.veltraph.client.combat.ghost.GhostSourceDestination.class));
        rows.add(RowModel.toggle("Ghost Jump Boost Adjustment", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_MODIFY_JUMP_BOOST.name()));
        rows.add(RowModel.toggle("Ghost Immediate Pest Handling", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY.name()));
        rows.add(RowModel.toggle("Ghost Immediate Visitor Handling", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_ACTIVATE_VISITOR_INSTANTLY.name()));
        rows.add(RowModel.info("Ghost Destination Search", "Choose local routes or destination walking. Destination walking requires an explicit avoided-cell list; [] allows all otherwise valid cells. Enabled helper options wait until the required helper can run and return control. Local grinding continues to use its configured route names and search radius."));
        rows.add(RowModel.value("Ghost Ingress and Potion Profile (JSON)", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE.name()));
        rows.add(RowModel.info("Ghost Ingress Setup", "Configure the current warp, arrival and potion menu identities before ingress. Leaving the profile empty keeps local route grinding available."));
        rows.add(RowModel.value("Ghost Local Route Names (comma-separated)", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_ROUTES.name()));
        rows.add(RowModel.value("Ghost Weapon Slot (1-9)", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_WEAPON_SLOT.name()));
        rows.add(RowModel.value("Ghost Search Radius", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_RADIUS.name()));
        rows.add(RowModel.toggle("Smooth Ghost Walking Routes", dev.veltraph.client.config.GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED.name()));
        rows.add(RowModel.value("Ghost Route Shortcut Distance (blocks)", dev.veltraph.client.config.GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE.name()));
        rows.add(RowModel.toggle("Keep Fine Ghost Route Adjustments", dev.veltraph.client.config.GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS.name()));
        rows.add(RowModel.value("Ghost Avoided Walking Cells (JSON)", dev.veltraph.client.config.GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS.name()));
        rows.add(RowModel.toggle("Natural Ghost Walking", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_OPEN_WALK_VARIATION.name()));
        rows.add(RowModel.value("Ghost Drop Path Search Limit", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_LOOT_GRAPH_CAP.name()));
        rows.add(RowModel.value("Ghost Drop Pickup Timeout (ms)", dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_LOOT_TIMEOUT_MS.name()));
        rows.add(RowModel.toggle("Ghost Owned Heal and Return", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_HEAL.name()));
        rows.add(RowModel.value("Ghost Heal Slot (1-9)", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_HEAL_SLOT.name()));
        rows.add(RowModel.value("Ghost Heal/Return Timeout (ms)", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_HEAL_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Ghost Daedalus Slot (0 disables)", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_DAEDALUS_SLOT.name()));
        rows.add(RowModel.value("Ghost Daedalus Target Health %", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_DAEDALUS_HEALTH_PCT.name()));
        rows.add(RowModel.toggle("Ghost Collect Drops", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_COLLECT_DROPS.name()));
        rows.add(RowModel.toggle("Ghost Walk Fallback after Ether", dev.veltraph.client.config.GhostGrindConfig.GHOST_GRIND_WALK_FALLBACK.name()));
        rows.add(RowModel.info("Ghost Healing Ownership", "Waits for active shared healing to restore. Dedicated Ghost healing uses the configured heal slot and item name."));
        rows.add(RowModel.section("Shared Auto-Heal"));
        rows.add(RowModel.toggle("Shared Auto-Heal", GhostConfig.GHOST_AUTO_HEAL_ENABLED.name()));
        rows.add(RowModel.toggle("Require Fresh HUD HP/Mana (No Fallback)", GhostConfig.GHOST_CAPTURED_HUD_REQUIRED.name()));
        rows.add(RowModel.enumRow("Heal Stacking", GhostConfig.AUTO_HEAL_STACKING.name(), dev.veltraph.client.combat.heal.HealStackingMode.class));
        // Batch 9: the auto-heal thresholds/delays + heal-weapon name are now editable in-GUI.
        rows.add(RowModel.value("Shared Heal Activate HP", GhostConfig.GHOST_HEALTH_TO_ACTIVATE.name()));
        rows.add(RowModel.value("Shared Heal Emergency HP", GhostConfig.GHOST_EMERGENCY_HEALTH_TO_ACTIVATE.name()));
        rows.add(RowModel.value("Activate Health %", GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT.name()));
        rows.add(RowModel.value("Emergency Health %", GhostConfig.GHOST_EMERGENCY_HEALTH_PCT.name()));
        rows.add(RowModel.value("Post-Cast Wait Ms", GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS.name()));
        rows.add(RowModel.value("Heal Cooldown Ms", GhostConfig.GHOST_HEAL_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Dedicated Ghost Heal Item", GhostConfig.GHOST_HEAL_WEAPON_NAME.name()));
        rows.add(RowModel.section("Slayer Finishing Tool"));
        rows.add(RowModel.toggle("Slayer Daedalus Finishing Swap",dev.veltraph.client.config.DaedalusSwapConfig.USE_DAEDALUS_SWAP.name()));
        rows.add(RowModel.value("Slayer Finishing Health %",dev.veltraph.client.config.DaedalusSwapConfig.DAEDALUS_SWAP_HEALTH_THRESHOLD_PERCENTAGE.name()));
        rows.add(RowModel.section("Slayer Quest Flow"));
        rows.add(RowModel.value("Batphone Item", dev.veltraph.client.config.SlayerConfig.SLAYER_PHONE_NAME.name()));
        rows.add(RowModel.value("Slayer NPC Name", dev.veltraph.client.config.SlayerConfig.SLAYER_NPC_NAME.name()));
        rows.add(RowModel.value("Root Menu", dev.veltraph.client.config.SlayerConfig.SLAYER_ROOT_TITLE.name()));
        rows.add(RowModel.value("Boss Button", dev.veltraph.client.config.SlayerConfig.SLAYER_BOSS_LABEL.name()));
        rows.add(RowModel.value("Boss Menu", dev.veltraph.client.config.SlayerConfig.SLAYER_BOSS_TITLE.name()));
        rows.add(RowModel.value("Quest Tier (1-5)",dev.veltraph.client.config.SlayerConfig.SLAYER_QUEST_TIER.name()));
        rows.add(RowModel.value("Tier Pattern ({tier})", dev.veltraph.client.config.SlayerConfig.SLAYER_TIER_LABEL.name()));
        rows.add(RowModel.value("Confirm Menu", dev.veltraph.client.config.SlayerConfig.SLAYER_CONFIRM_TITLE.name()));
        rows.add(RowModel.value("Confirm Button", dev.veltraph.client.config.SlayerConfig.SLAYER_CONFIRM_LABEL.name()));
        rows.add(RowModel.value("Claim Button", dev.veltraph.client.config.SlayerConfig.SLAYER_CLAIM_LABEL.name()));
        rows.add(RowModel.value("Cancel Button", dev.veltraph.client.config.SlayerConfig.SLAYER_CANCEL_LABEL.name()));
        rows.add(RowModel.value("Cancel Menu", dev.veltraph.client.config.SlayerConfig.SLAYER_CANCEL_TITLE.name()));
        rows.add(RowModel.value("Cancel Confirm Button", dev.veltraph.client.config.SlayerConfig.SLAYER_CANCEL_CONFIRM_LABEL.name()));
        rows.add(RowModel.value("Slayer Targets", dev.veltraph.client.config.SlayerConfig.SLAYER_TARGET_NAMES.name()));
        rows.add(RowModel.value("Max Quest Cost", dev.veltraph.client.config.SlayerConfig.SLAYER_MAX_QUEST_COST.name()));
        rows.add(RowModel.enumRow("Quest Currency", dev.veltraph.client.config.SlayerConfig.SLAYER_QUEST_CURRENCY.name(), dev.veltraph.client.combat.slayer.QuestCurrency.class));
        rows.add(RowModel.value("Menu Timeout (ms)", dev.veltraph.client.config.SlayerConfig.SLAYER_MENU_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Click Delay (ms)", dev.veltraph.client.config.SlayerConfig.SLAYER_CLICK_DELAY_MS.name()));
        rows.add(RowModel.value("Phone Attempts", dev.veltraph.client.config.SlayerConfig.SLAYER_PHONE_ATTEMPTS.name()));
        rows.add(RowModel.toggle("Repeat Quests", dev.veltraph.client.config.SlayerConfig.SLAYER_REPEAT.name()));
        rows.add(RowModel.toggle("Cancel Conflicting Quest", dev.veltraph.client.config.SlayerConfig.SLAYER_CANCEL_EXISTING.name()));
        rows.add(RowModel.section("Tarantula Whip and Bow"));
        rows.add(RowModel.info("Tara Contract", "Configure the Whip tools, Bow, readiness signal and outcome before enabling. Whip prepares the boss; Bow clears its eggs."));
        rows.add(RowModel.toggle("Use Soul Whip", dev.veltraph.client.config.TaraConfig.TARA_USE_SOUL_WHIP.name()));
        rows.add(RowModel.value("Primary Name", dev.veltraph.client.config.TaraConfig.TARA_PRIMARY_NAME.name()));
        rows.add(RowModel.value("Primary Item", dev.veltraph.client.config.TaraConfig.TARA_PRIMARY_ITEM.name()));
        rows.add(RowModel.value("Fallback Name", dev.veltraph.client.config.TaraConfig.TARA_FALLBACK_NAME.name()));
        rows.add(RowModel.value("Fallback Item", dev.veltraph.client.config.TaraConfig.TARA_FALLBACK_ITEM.name()));
        rows.add(RowModel.value("Bow Name", dev.veltraph.client.config.TaraConfig.TARA_BOW_NAME.name()));
        rows.add(RowModel.value("Ready Plate", dev.veltraph.client.config.TaraConfig.TARA_READY_PLATE.name()));
        rows.add(RowModel.value("Outcome Message", dev.veltraph.client.config.TaraConfig.TARA_OUTCOME_MESSAGE.name()));
        rows.add(RowModel.value("Egg Outcome", dev.veltraph.client.config.TaraConfig.TARA_EGG_OUTCOME.name()));
        rows.add(RowModel.value("Whip Preparation Ticks", dev.veltraph.client.config.TaraConfig.TARA_WHIP_PREPARATION_TICKS.name()));
        rows.add(RowModel.value("Bow Preparation Ticks", dev.veltraph.client.config.TaraConfig.TARA_BOW_PREPARATION_TICKS.name()));
        rows.add(RowModel.value("Bow Draw Ticks", dev.veltraph.client.config.TaraConfig.TARA_BOW_DRAW_TICKS.name()));
        rows.add(RowModel.value("Minimum Health Delta", dev.veltraph.client.config.TaraConfig.TARA_MINIMUM_HEALTH_DELTA.name()));
        rows.add(RowModel.value("Group Buffer Ms", dev.veltraph.client.config.TaraConfig.TARA_GROUP_BUFFER_MS.name()));
        rows.add(RowModel.value("Whip Retry Ms", dev.veltraph.client.config.TaraConfig.TARA_WHIP_RETRY_MS.name()));
        rows.add(RowModel.value("Whip Timeout Ms", dev.veltraph.client.config.TaraConfig.TARA_WHIP_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Egg Retry Ms", dev.veltraph.client.config.TaraConfig.TARA_EGG_RETRY_MS.name()));
        rows.add(RowModel.value("Egg Timeout Ms", dev.veltraph.client.config.TaraConfig.TARA_EGG_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Whip Max Uses", dev.veltraph.client.config.TaraConfig.TARA_WHIP_MAX_USES.name()));
        rows.add(RowModel.value("Bow Max Uses", dev.veltraph.client.config.TaraConfig.TARA_BOW_MAX_USES.name()));
        rows.add(RowModel.value("Native Ray Limit", dev.veltraph.client.config.TaraConfig.TARA_NATIVE_RAY_LIMIT.name()));
        rows.add(RowModel.section("Quest Drop Setup"));
        rows.add(RowModel.toggle("Collect Quest Drops", dev.veltraph.client.config.SlayerConfig.SLAYER_COLLECT_QUEST_DROPS.name()));
        rows.add(RowModel.value("Quest Boss Names", dev.veltraph.client.config.SlayerConfig.SLAYER_QUEST_BOSS_NAMES.name()));
        rows.add(RowModel.value("Quest Drop Names", dev.veltraph.client.config.SlayerConfig.SLAYER_QUEST_DROP_NAMES.name()));
        rows.add(RowModel.value("Quest Loot Timeout (ms; -1 unset)", dev.veltraph.client.config.SlayerConfig.SLAYER_QUEST_LOOT_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Quest Drop Radius (-1 unset)", dev.veltraph.client.config.SlayerConfig.SLAYER_QUEST_DROP_RADIUS.name()));
        rows.add(RowModel.info("Quest Drop Scope", "Off by default. Explicit boss/drop names and timing/radius are required. Moving-boss combat, native loot pickup, reward and quest restart have private acceptance with strict camera/hit checks. Full boss mechanics, cocoon/replacement phases and Vampire Motes quest protocol remain separate."));
        rows.add(RowModel.section("Cocoon Recovery"));
        rows.add(RowModel.toggle("Recover Owned Boss from Cocoon", dev.veltraph.client.config.SlayerConfig.SLAYER_COCOON_RECOVERY.name()));
        rows.add(RowModel.value("Cocoon Head Hashes (comma list)", dev.veltraph.client.config.SlayerConfig.SLAYER_COCOON_HEAD_HASHES.name()));
        rows.add(RowModel.value("Cocoon Timeout (ms; -1 unset)", dev.veltraph.client.config.SlayerConfig.SLAYER_COCOON_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Cocoon Receipt Window (ms; -1 unset)", dev.veltraph.client.config.SlayerConfig.SLAYER_COCOON_RECEIPT_WINDOW_MS.name()));
        rows.add(RowModel.value("Cocoon Replacement Radius (-1 unset)", dev.veltraph.client.config.SlayerConfig.SLAYER_COCOON_REPLACEMENT_RADIUS.name()));
        rows.add(RowModel.info("Cocoon Recovery Scope", "Disabled by default. Recovery requires configured head hashes and bounds, a fresh own-boss receipt and verified ownership of a unique new replacement. Configured native lifecycle and failure cases are verified; live metadata remains required."));
        rows.add(RowModel.disabled("Boss-specific abilities / ranged combat", "Quest cycling is implemented; specialized combat mechanics remain incomplete"));
        return rows;
    }

    /**
     * Foraging (tree-chop) settings (Batch 7). All foraging tunables are numeric/string (reach, break
     * timeout, blacklist TTL, log keywords) with no numeric/text widget yet, so the page is a section
     * header + an inspectable info row pointing at {@code config.json}. The values are live (used by the
     * Tree Chopping macro), not missing. The SEARCH/WALK/WARP-to-tree finder is pathfinding-excluded.
     */
    private static List<RowModel> foragingRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Tree Chopping"));
        rows.add(RowModel.toggle("Enable Foraging Chest Supply / Dark Oak Refill", dev.veltraph.client.foraging.supply.ForagingSupplyConfig.FORAGING_CHEST_FILL_ENABLED.name()));
        rows.add(RowModel.enumRow("Foraging Chest Supply Type", dev.veltraph.client.foraging.supply.ForagingSupplyConfig.FORAGING_AUTO_FILL_TYPE.name(), dev.veltraph.client.foraging.supply.ForagingSupplyConfig.Type.class));
        rows.add(RowModel.value("Foraging Chest Supply Bindings (exact JSON)", dev.veltraph.client.foraging.supply.ForagingSupplyConfig.FORAGING_CHEST_FILL_BINDINGS.name()));
        rows.add(RowModel.value("Tree Search Radius", ForagingConfig.FORAGING_SEARCH_RADIUS.name()));
        // Batch 9: the tree-break tunables + log keywords are now editable in-GUI.
        rows.add(RowModel.value("Tree Reach (blocks)", ForagingConfig.FORAGING_TREE_REACH.name()));
        rows.add(RowModel.value("Break Timeout Ms", ForagingConfig.FORAGING_TREE_BREAK_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Blacklist TTL Ms", ForagingConfig.FORAGING_BLACKLIST_TTL_MS.name()));
        rows.add(RowModel.value("Log Keywords (comma)", ForagingConfig.FORAGING_LOG_KEYWORDS.name()));
        rows.add(RowModel.section("Park / Hub Tree Selection"));
        rows.add(RowModel.enumRow("Park Region", ForagingConfig.FORAGING_PARK_REGION.name(), dev.veltraph.client.foraging.ForagingRegion.class));
        rows.add(RowModel.enumRow("Log Density", ForagingConfig.FORAGING_LOG_DENSITY.name(), dev.veltraph.client.foraging.ForagingRegion.Density.class));
        rows.add(RowModel.enumRow("Axe Mode", ForagingConfig.FORAGING_AXE_MODE.name(), ForagingConfig.AxeMode.class));
        rows.add(RowModel.value("Explicit Axe-Mode Fallback Cooldown (ms)", ForagingConfig.FORAGING_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Drop Collection Timeout (ms)", ForagingConfig.FORAGING_COLLECTION_TIMEOUT_MS.name()));
        rows.add(RowModel.toggle("Use Source Region Bounds", ForagingConfig.FORAGING_USE_REGION_BOUNDS.name()));
        rows.add(RowModel.toggle("Check Ghost Blocks", ForagingConfig.FORAGING_GHOST_BLOCK_CHECKER.name()));
        rows.add(RowModel.toggle("Stop on Ghost Block Spam", ForagingConfig.FORAGING_STOP_ON_GHOST_BLOCK_SPAM.name()));
        rows.add(RowModel.toggle("Require Observed Sweep", ForagingConfig.FORAGING_REQUIRE_SWEEP.name()));
        rows.add(RowModel.value("Ability Result Window (ms)", ForagingConfig.FORAGING_ABILITY_RESULT_WINDOW_MS.name()));
        rows.add(RowModel.section("Private Island Dark Oak Replant"));
        rows.add(RowModel.value("Dark Oak Planting Anchor (x,y,z)", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_ANCHOR.name()));
        rows.add(RowModel.value("Replant Cycles (0=repeat)", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_CYCLES.name()));
        rows.add(RowModel.value("Maximum Bonemeal Uses per Growth", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_MAX_BONEMEAL_USES.name()));
        rows.add(RowModel.value("Replant Stage Timeout (ms)", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_STAGE_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Replant Acknowledgment Stability (ms)", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_ACK_MS.name()));
        rows.add(RowModel.value("Bonemeal Spacing (ms)", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_BONEMEAL_INTERVAL_MS.name()));
        rows.add(RowModel.toggle("Reset Owned Saplings on Long Change", dev.veltraph.client.config.ForagingReplantConfig.FORAGING_RESET_ON_LONG_CHANGE.name()));
        rows.add(RowModel.info("Replant Activation", "Starts only through the Private Island Dark Oak card; blank default anchor blocks start. Anchor is one sapling cell, expanded +X/+Z into a 2x2 footprint."));
        rows.add(RowModel.info("Replant Resources", "Stand within reach of four dirt/grass supports; clear loaded growth volume, four Dark Oak saplings, ordinary bonemeal and hotbar axe required."));
        rows.add(RowModel.info("Replant Defaults", "Cycles 0 repeat until stopped; source attempt limit 2 may be too small for stochastic vanilla growth. Shared observed axe/Sweep and collection controls apply."));
        rows.add(RowModel.info("Replant Scope", "Seven source stages with native resource/growth/harvest acknowledgments; exact loan restoration can remain pending behind a busy cursor. Side-chest auto-fill, Enchanted Bonemeal and full Galatea replanting remain unverified."));
        rows.add(RowModel.section("Agatha Contest Claim"));
        rows.add(RowModel.toggle("Collect Contest Invitations", dev.veltraph.client.config.AgathaConfig.FORAGING_COLLECT_CONTESTS.name()));
        rows.add(RowModel.value("Agatha Required Native Region Line", dev.veltraph.client.config.ForagingMaintenanceConfig.AGATHA_REQUIRED_REGION_LINE.name()));
        rows.add(RowModel.value("Agatha Invitation Prefix", dev.veltraph.client.config.AgathaConfig.AGATHA_INVITATION_PREFIX.name()));
        rows.add(RowModel.value("Claim Link Text", dev.veltraph.client.config.AgathaConfig.AGATHA_LINK_LABEL.name()));
        rows.add(RowModel.value("Exact Claim Command", dev.veltraph.client.config.AgathaConfig.AGATHA_CLAIM_COMMAND.name()));
        rows.add(RowModel.value("Claim Menu Title", dev.veltraph.client.config.AgathaConfig.AGATHA_MENU_TITLE.name()));
        rows.add(RowModel.value("Claim Button Text", dev.veltraph.client.config.AgathaConfig.AGATHA_CLAIM_LABEL.name()));
        rows.add(RowModel.value("Claim Button Item ID", dev.veltraph.client.config.AgathaConfig.AGATHA_CLAIM_ITEM_ID.name()));
        rows.add(RowModel.value("Contest Reward Item ID", dev.veltraph.client.config.AgathaConfig.AGATHA_REWARD_ITEM_ID.name()));
        rows.add(RowModel.value("Contest Receipt Prefix", dev.veltraph.client.config.AgathaConfig.AGATHA_RECEIPT_PREFIX.name()));
        rows.add(RowModel.value("Contest Failure Prefix", dev.veltraph.client.config.AgathaConfig.AGATHA_FAILURE_PREFIX.name()));
        rows.add(RowModel.value("Minimum Contest Reward", dev.veltraph.client.config.AgathaConfig.AGATHA_MIN_REWARD.name()));
        rows.add(RowModel.value("Maximum Claim Attempts", dev.veltraph.client.config.AgathaConfig.AGATHA_MAX_CLAIM_ATTEMPTS.name()));
        rows.add(RowModel.value("Invitation Lifetime (ms)", dev.veltraph.client.config.AgathaConfig.AGATHA_INVITATION_TTL_MS.name()));
        rows.add(RowModel.value("Claim Stage Timeout (ms)", dev.veltraph.client.config.AgathaConfig.AGATHA_STAGE_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Claim Outcome Stability (ms)", dev.veltraph.client.config.AgathaConfig.AGATHA_ACK_MS.name()));
        rows.add(RowModel.info("Agatha Claim Scope", "Standalone claiming and the Fig parent handoff have native acceptance. Configure exact command/menu/reward/receipt metadata and the received Moonglade region line. Other parent routes remain separate; reward disappearance alone cannot confirm a claim."));
        rows.add(RowModel.section("Hub Travel"));
        rows.add(RowModel.value("Hub Warp Command", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_COMMAND.name()));
        rows.add(RowModel.value("Hub Arrival Message (optional)", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_ACCEPTED_MESSAGE.name()));
        rows.add(RowModel.value("Hub Rejection Messages", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_REJECTED_MESSAGES.name()));
        rows.add(RowModel.value("Hub Dimension", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_DIMENSION.name()));
        rows.add(RowModel.value("Hub Arrival Position (XYZ)", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_DESTINATION.name()));
        rows.add(RowModel.value("Hub Arrival Radius", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_DESTINATION_RADIUS.name()));
        rows.add(RowModel.value("Hub Warp Deadline (ms)", dev.veltraph.client.foraging.hub.HubForagingConfig.HUB_WARP_TIMEOUT_MS.name()));
        rows.add(RowModel.info("Hub Travel Requirements", "Warping requires a configured command, dimension and loaded supported destination. An optional arrival message adds an exact receipt check. Cancellation, a rejection or an unknown outcome stops the request."));
        rows.add(RowModel.section("Galatea Tree Access"));
        rows.add(RowModel.toggle("Fig Jump Boost", GalateaConfig.FIG_JUMP_BOOST.name()));
        rows.add(RowModel.enumRow("Fig Jump Boost Aiming", GalateaConfig.FIG_JUMP_BOOST_AIMING.name(), dev.veltraph.client.galatea.FigJumpAimPolicy.Mode.class));
        rows.add(RowModel.toggle("Fig Scaffolding", GalateaConfig.FIG_USE_SCAFFOLDING.name()));
        rows.add(RowModel.value("Fig Scaffold Ability Binding", dev.veltraph.client.config.FigSourceSelectionConfig.FIG_OBSERVED_SCAFFOLD_BINDING.name()));
        rows.add(RowModel.value("Fig Source Region Binding", dev.veltraph.client.config.FigSourceSelectionConfig.FIG_SOURCE_REGION_BINDING.name()));
        rows.add(RowModel.value("Fig Observed Tree Descriptors", dev.veltraph.client.config.FigSourceSelectionConfig.FIG_OBSERVED_TREE_DESCRIPTORS.name()));
        rows.add(RowModel.toggle("Prefer Small Fig Trees", GalateaConfig.FIG_PREFER_SMALL_TREES.name()));
        rows.add(RowModel.toggle("Prefer Sneaking", GalateaConfig.FIG_PREFER_SNEAKING.name()));
        rows.add(RowModel.toggle("Skip Axe Throwing", GalateaConfig.FIG_SKIP_AXE_THROWING.name()));
        rows.add(RowModel.toggle("Aim Higher at Stem", GalateaConfig.FIG_AIM_HIGHER_AT_STEM.name()));
        rows.add(RowModel.toggle("Restrict Fig Region", GalateaConfig.FIG_RESTRICT_REGION.name()));
        rows.add(RowModel.value("Fig Region (min XYZ,max XYZ)", GalateaConfig.FIG_REGION_BOUNDS.name()));
        rows.add(RowModel.value("Fig Log Block IDs", GalateaConfig.FIG_LOG_IDS.name()));
        rows.add(RowModel.toggle("Fig Fall Recovery", dev.veltraph.client.config.FigRecoveryConfig.FIG_FALL_RECOVERY.name()));
        rows.add(RowModel.value("Fig Recovery Mana Cost", dev.veltraph.client.config.FigRecoveryConfig.FIG_RECOVERY_MANA_COST.name()));
        rows.add(RowModel.value("Fig Recovery Timeout (ms)", dev.veltraph.client.config.FigRecoveryConfig.FIG_RECOVERY_TIMEOUT_MS.name()));
        rows.add(RowModel.info("Fig Recovery Scope", "Source-region fall recovery and parent interception have native acceptance. A positive configured mana cost, fresh observed mana, the matching held ability and a loaded safe destination are required. Disabled by default; exact live atlas and server metadata remain separate."));
        rows.add(RowModel.toggle("Restrict Mangrove Region", GalateaConfig.MANGROVE_RESTRICT_REGION.name()));
        rows.add(RowModel.value("Mangrove Region (min XYZ,max XYZ)", GalateaConfig.MANGROVE_REGION_BOUNDS.name()));
        rows.add(RowModel.value("Mangrove Log Block IDs", GalateaConfig.MANGROVE_LOG_IDS.name()));
        rows.add(RowModel.value("Galatea Log Reward Names (comma list)", GalateaConfig.GALATEA_LOG_REWARD_NAMES.name()));
        rows.add(RowModel.toggle("Mangrove Move Around", GalateaConfig.MANGROVE_MOVE_AROUND.name()));
        rows.add(RowModel.toggle("Warp to Murkwater Preference", GalateaConfig.MANGROVE_WARP_TO_MURKWATER.name()));
        rows.add(RowModel.section("Galatea Parent Workflows"));
        rows.add(RowModel.toggle("Source Command Permission", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_SOURCE_COMMANDS.name()));
        rows.add(RowModel.value("Exact Reset Command", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_RESET_COMMAND.name()));
        rows.add(RowModel.value("Exact Reset Destination", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_RESET_DESTINATION.name()));
        rows.add(RowModel.value("Exact Reset Receipt", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_RESET_RECEIPT.name()));
        rows.add(RowModel.value("Exact Murkwater Command", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_MURKWATER_COMMAND.name()));
        rows.add(RowModel.value("Exact Murkwater Receipt", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_MURKWATER_RECEIPT.name()));
        rows.add(RowModel.value("Exact Command Rejection", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_COMMAND_REJECTION.name()));
        rows.add(RowModel.value("Exact Marsh Region Line", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_MARSH_REGION_LINE.name()));
        rows.add(RowModel.value("Source Command Timeout (ms)", dev.veltraph.client.config.GalateaWorkflowConfig.GALATEA_COMMAND_TIMEOUT_MS.name()));
        rows.add(RowModel.toggle("Automatic Material Trash Handoff", dev.veltraph.client.config.GalateaWorkflowConfig.LUSHLILAC_AUTO_TRASH_HANDOFF.name()));
        rows.add(RowModel.info("Explicit Reset", "Use /veltraph galatea reset while the registered parent is idle. Source permission and exact bindings are required; Murkwater preference alone grants no command permission."));
        rows.add(RowModel.value("Galatea Observations per Second", GalateaConfig.GALATEA_FORAGING_SPEED.name()));
        rows.add(RowModel.enumRow("Galatea Etherwarp Usage", GalateaConfig.GALATEA_ETHERWARP_USAGE.name(), dev.veltraph.client.galatea.GalateaPlanner.EtherwarpUsage.class));
        rows.add(RowModel.toggle("Abandon Trees with Other Players", GalateaConfig.FORAGING_ABANDON_TREES_WITH_OTHERS.name()));
        rows.add(RowModel.value("Galatea Scan Radius", GalateaConfig.GALATEA_SCAN_RADIUS.name()));
        rows.add(RowModel.value("Galatea Action Timeout (ms)", GalateaConfig.GALATEA_ACTION_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Galatea Blacklist Time (ms)", GalateaConfig.GALATEA_BLACKLIST_MS.name()));
        rows.add(RowModel.value("Axe Throw Range", GalateaConfig.GALATEA_AXE_THROW_RANGE.name()));
        rows.add(RowModel.toggle("Galatea Require Observed Sweep", GalateaConfig.GALATEA_REQUIRE_SWEEP.name()));
        rows.add(RowModel.value("Legacy Throw Token (ignored)", GalateaConfig.GALATEA_THROW_ABILITY_TOKEN.name()));
        rows.add(RowModel.info("Throwing Policy", "Throwing uses observed ability policy; legacy token is ignored."));
        rows.add(RowModel.section("Galatea Materials"));
        rows.add(RowModel.value("Material Click Cooldown (ms)", GalateaConfig.GALATEA_USE_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Material Block IDs", GalateaConfig.GALATEA_MATERIAL_IDS.name()));
        rows.add(RowModel.value("Material Reward Token", GalateaConfig.GALATEA_MATERIAL_REWARD_TOKEN.name()));
        rows.add(RowModel.value("Material Reward Names (comma list)", GalateaConfig.GALATEA_MATERIAL_REWARD_NAMES.name()));
        rows.add(RowModel.value("Material Region (min XYZ,max XYZ)", GalateaConfig.GALATEA_MATERIAL_REGION_BOUNDS.name()));
        rows.add(RowModel.value("Material Confirmation Window (ms)", GalateaConfig.GALATEA_MATERIAL_CONFIRM_MS.name()));
        rows.add(RowModel.info("Material Setup", "Lushlilac bush flow; Sorrow is a Ghost drop and Oceandy needs a recipe."));
        rows.add(RowModel.toggle("Assume Material Click (unconfirmed)", GalateaConfig.LUSHLILAC_ASSUME_CLICKED.name()));
        rows.add(RowModel.enumRow("Material Click Type", GalateaConfig.LUSHLILAC_CLICK_TYPE.name(), dev.veltraph.client.galatea.GalateaPlanner.ClickType.class));
        rows.add(RowModel.section("Lushlilac Trash Route"));
        rows.add(RowModel.toggle("Lushlilac Trash Permission", dev.veltraph.client.config.LushlilacTrashConfig.LUSHLILAC_THROW_TRASH.name()));
        rows.add(RowModel.value("Exact Trash Spots (JSON)", dev.veltraph.client.config.LushlilacTrashConfig.LUSHLILAC_TRASH_SPOTS_JSON.name()));
        rows.add(RowModel.value("Exact Trash Stack Rules (JSON)", dev.veltraph.client.config.LushlilacTrashConfig.LUSHLILAC_TRASH_RULES_JSON.name()));
        rows.add(RowModel.value("Trash Route Timeout (ticks)", dev.veltraph.client.config.LushlilacTrashConfig.LUSHLILAC_TRASH_TIMEOUT_TICKS.name()));
        rows.add(RowModel.info("Lushlilac Trash Scope", "Standalone travel, inventory discard, server item acknowledgment and owned menu cleanup have native acceptance. Exact local spots and stack rules are required. Automatic material-parent interruption and resume remain incomplete."));
        rows.add(RowModel.disabled("Galatea Atlas / Contests / Replanting", "Loaded-world collection is available; original atlas data, contest collection, replanting and trash routes remain incomplete"));
        return rows;
    }

    /**
     * In-world HUD overlay settings (Target 5 / Batch 9). All three {@link dev.veltraph.client.config.HudConfig}
     * keys are editable in-GUI: enabled toggle, corner enum cycler, and pixel margin value.
     */
    private static List<RowModel> hudRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("In-World HUD"));
        rows.add(RowModel.toggle("HUD Enabled", HudConfig.HUD_ENABLED.name()));
        rows.add(RowModel.enumRow("HUD Corner", HudConfig.HUD_CORNER.name(), HudCorner.class));
        rows.add(RowModel.value("HUD Margin (px)", HudConfig.HUD_MARGIN.name()));
        rows.add(RowModel.section("Local Session Metrics"));
        rows.add(RowModel.toggle("Local Metrics Enabled", dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED.name()));
        rows.add(RowModel.value("Exact Item Roles / Values (JSON)", dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ITEMS.name()));
        rows.add(RowModel.value("Trusted Metric Signals (JSON)", dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_SIGNALS.name()));
        rows.add(RowModel.value("Native Block Rate Window (ms)", dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_BPS_WINDOW_MS.name()));
        rows.add(RowModel.value("Pickup Confirmation Window (ms)", dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_PICKUP_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Notice Deduplication Window (ms)", dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_NOTICE_DEDUP_MS.name()));
        rows.add(RowModel.info("Local Metrics Scope", "Local statistics and the session HUD track received native events, confirmed item pickups and supported activity counters. Unreceived values and unpriced items remain unknown; exact XP, estimated XP and luck ratio are shown separately. Realized net stays unknown until a confirmed financial outcome is available."));
        return rows;
    }

    /**
     * Visual / notifications page (Batch 11). Local alert mechanisms + named local event toggles
     * ({@link NotificationConfig}, MACRO_INVENTORY §10/§11). Discord (alerts/RP/screenshot) is network —
     * EXCLUDED — so it stays an inspectable disabled row, not a key.
     */
    private static List<RowModel> visualRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Notifications"));
        rows.add(RowModel.toggle("Notifications Enabled", NotificationConfig.NOTIFY_ENABLED.name()));
        rows.add(RowModel.toggle("In-Game Sound", NotificationConfig.NOTIFY_SOUND.name()));
        rows.add(RowModel.toggle("Screen Title", NotificationConfig.NOTIFY_TITLE.name()));
        rows.add(RowModel.toggle("OS Beep", NotificationConfig.NOTIFY_OS_BEEP.name()));
        rows.add(RowModel.section("Notify On Event"));
        rows.add(RowModel.toggle("Insane/Rare Drop", NotificationConfig.NOTIFY_INSANE_DROP.name()));
        rows.add(RowModel.toggle("Auto-Sell", NotificationConfig.NOTIFY_AUTO_SELL.name()));
        rows.add(RowModel.toggle("Pest Results", NotificationConfig.NOTIFY_PEST_RESULTS.name()));
        rows.add(RowModel.toggle("Fishing Boss", NotificationConfig.NOTIFY_FISHING_BOSS.name()));
        rows.add(RowModel.toggle("Banned", NotificationConfig.NOTIFY_BANNED.name()));
        rows.add(RowModel.disabled("Discord alerts / rich presence / screenshot (network)",
                "Excluded by hard rules (no java.net/Discord webhook); local alerts above are the supported path"));
        return rows;
    }

    /**
     * Failsafe page (Batch 11). The general failsafe surface ({@link FailsafeConfig}, MACRO_INVENTORY §9):
     * reaction policy + void/mention + the player-nearby failsafe. The anti-staff detector set (lagback/
     * teleport/world-change) lives on the Anti-Staff page ({@link HydraConfig}).
     */
    private static List<RowModel> failsafeRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Failsafe"));
        rows.add(RowModel.enumRow("Reaction Policy", FailsafeConfig.FAILSAFE_REACTION_POLICY.name(),
                FailsafeReactionPolicy.class));
        rows.add(RowModel.toggle("Void Check", FailsafeConfig.FAILSAFE_CHECK_VOID.name()));
        rows.add(RowModel.toggle("Mention Failsafe", FailsafeConfig.FAILSAFE_MENTION_ENABLED.name()));
        rows.add(RowModel.enumRow("Mention Source Policy", FailsafeConfig.FAILSAFE_MENTION_POLICY.name(), FailsafeSourcePolicy.class));
        rows.add(RowModel.value("Mention Minimum Delay (ms)", FailsafeConfig.FAILSAFE_MENTION_MIN_DELAY_MS.name()));
        rows.add(RowModel.value("Mention Maximum Delay (ms)", FailsafeConfig.FAILSAFE_MENTION_MAX_DELAY_MS.name()));
        rows.add(RowModel.value("Switch Lobby Command (explicit observed route)", FailsafeConfig.FAILSAFE_SWITCH_LOBBY_COMMAND.name()));
        rows.add(RowModel.info("Source Policy Defaults", "Player and mention policies default LOCAL_POLICY, preserving the selected local reaction policy. Mention delay defaults to 3,000–10,000 ms; NOTIFY reports locally."));
        rows.add(RowModel.info("Switch Lobby Route", "SWITCH_LOBBY requests a route only when an explicit command is configured. The default is blank, no route is guessed, and automation remains stopped; configuring a route grants no automatic resume."));
        rows.add(RowModel.value("Suspicious Sensitivity", FailsafeConfig.FAILSAFE_SUSPICIOUS_SENSITIVITY.name()));
        rows.add(RowModel.section("Player Nearby"));
        rows.add(RowModel.toggle("Player-Nearby Failsafe", FailsafeConfig.FAILSAFE_PLAYER_NEARBY_ENABLED.name()));
        rows.add(RowModel.enumRow("Player Source Policy", FailsafeConfig.FAILSAFE_PLAYER_POLICY.name(), FailsafeSourcePolicy.class));
        rows.add(RowModel.value("Trigger Radius (blocks)", FailsafeConfig.FAILSAFE_PLAYER_RADIUS.name()));
        rows.add(RowModel.value("Player Count", FailsafeConfig.FAILSAFE_PLAYER_COUNT.name()));
        rows.add(RowModel.toggle("Only If Looking At Me", FailsafeConfig.FAILSAFE_ONLY_LOOKING_AT.name()));
        rows.add(RowModel.toggle("Only If In Front", FailsafeConfig.FAILSAFE_ONLY_IN_FRONT.name()));
        rows.add(RowModel.toggle("Raytraced (line-of-sight) Only", FailsafeConfig.FAILSAFE_RAYTRACED_ONLY.name()));

        rows.add(RowModel.section("Unexpected Behavior Observation"));
        rows.add(RowModel.toggle("Local Observation Enabled", UnexpectedBehaviorConfig.ENABLED.name()));
        rows.add(RowModel.toggle("Notify Unexpected Observations", UnexpectedBehaviorConfig.NOTIFY.name()));
        rows.add(RowModel.toggle("Stop Immediately On Unexpected Observation (opt-in)", UnexpectedBehaviorConfig.AUTO_STOP.name()));
        rows.add(RowModel.value("Observation Alert Cooldown (ms)", UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS.name()));
        rows.add(RowModel.info("Observation Defaults", "Passive read-only observation and local notification default on; automatic stop defaults off and alert cooldown is 5,000 ms. These controls do not weaken existing safety stops."));
        rows.add(RowModel.info("Observation Classifications", "EXPLAINED means matching mod/native causal evidence; UNEXPECTED means an active-context discontinuity lacks matching intent; UNCERTAIN means evidence or context is incomplete. An explained change grants no immunity from other stops."));
        rows.add(RowModel.info("Observation Identity Limits", "Native UI events do not identify a human, and server packets do not identify staff or server motive. Local inventory/block summaries are bounded observations; they do not prove exact item-component identity, every world change or full causal coverage."));

        rows.add(RowModel.section("Automated Response (local)"));
        rows.add(RowModel.toggle("Automated Response", FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED.name()));
        rows.add(RowModel.enumRow("Response Profile", FailsafeConfig.FAILSAFE_RESPONSE_PROFILE.name(),
                dev.veltraph.client.failsafe.response.FailsafeResponseProfile.class));
        rows.add(RowModel.value("Look Sweeps", FailsafeConfig.FAILSAFE_RESPONSE_LOOK_SWEEPS.name()));
        rows.add(RowModel.value("Look Range (deg)", FailsafeConfig.FAILSAFE_RESPONSE_LOOK_RANGE.name()));
        rows.add(RowModel.value("Look Ticks Per Sweep", FailsafeConfig.FAILSAFE_RESPONSE_LOOK_TICKS.name()));
        rows.add(RowModel.toggle("Response: Jump", FailsafeConfig.FAILSAFE_RESPONSE_JUMP.name()));
        rows.add(RowModel.toggle("Response: Sneak", FailsafeConfig.FAILSAFE_RESPONSE_SNEAK.name()));
        rows.add(RowModel.toggle("Response: Attack Click", FailsafeConfig.FAILSAFE_RESPONSE_ATTACK.name()));
        rows.add(RowModel.toggle("Response: Use Click", FailsafeConfig.FAILSAFE_RESPONSE_USE.name()));
        rows.add(RowModel.toggle("Response: Move Shuffle", FailsafeConfig.FAILSAFE_RESPONSE_MOVE.name()));
        rows.add(RowModel.value("Move Ticks Per Step", FailsafeConfig.FAILSAFE_RESPONSE_MOVE_TICKS.name()));
        rows.add(RowModel.value("Response Cooldown Ms", FailsafeConfig.FAILSAFE_RESPONSE_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Response Max Ticks", FailsafeConfig.FAILSAFE_RESPONSE_MAX_TICKS.name()));

        rows.add(RowModel.section("Automated Response — Triggers"));
        rows.add(RowModel.toggle("On Boss Teleported", FailsafeConfig.FAILSAFE_RESPONSE_ON_BOSS_TELEPORTED.name()));
        rows.add(RowModel.toggle("On Teleported Into Block",
                FailsafeConfig.FAILSAFE_RESPONSE_ON_TELEPORTED_INTO_BLOCK.name()));
        rows.add(RowModel.toggle("On Invalid Movement", FailsafeConfig.FAILSAFE_RESPONSE_ON_INVALID_MOVEMENT.name()));
        rows.add(RowModel.toggle("On Forced Adventure", FailsafeConfig.FAILSAFE_RESPONSE_ON_FORCED_ADVENTURE.name()));
        rows.add(RowModel.toggle("On Repeated Suspicious",
                FailsafeConfig.FAILSAFE_RESPONSE_ON_REPEATED_SUSPICIOUS.name()));
        rows.add(RowModel.toggle("On Void", FailsafeConfig.FAILSAFE_RESPONSE_ON_VOID.name()));
        rows.add(RowModel.toggle("On Mention", FailsafeConfig.FAILSAFE_RESPONSE_ON_MENTION.name()));
        rows.add(RowModel.toggle("On Player Nearby", FailsafeConfig.FAILSAFE_RESPONSE_ON_PLAYER_NEARBY.name()));
        rows.add(RowModel.toggle("On Routing Text", FailsafeConfig.FAILSAFE_RESPONSE_ON_ROUTING_TEXT.name()));
        rows.add(RowModel.toggle("On Limbo Routing", FailsafeConfig.FAILSAFE_RESPONSE_ON_LIMBO_ROUTING.name()));
        rows.add(RowModel.toggle("On Floor Change", FailsafeConfig.FAILSAFE_RESPONSE_ON_FLOOR_CHANGE.name()));
        rows.add(RowModel.toggle("On Barrier", FailsafeConfig.FAILSAFE_RESPONSE_ON_BARRIER.name()));
        return rows;
    }

    /** Local builder materials, transforms and bounds; original native loading remains excluded. */
    private static List<RowModel> builderRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Farm Builder"));
        rows.add(RowModel.enumRow("Crop Type", BuilderConfig.BUILDER_CROP_TYPE.name(), BuilderCropType.class));
        rows.add(RowModel.enumRow("Flower Type", BuilderConfig.BUILDER_FLOWER_TYPE.name(), BuilderFlowerType.class));
        rows.add(RowModel.enumRow("Mushroom Type", BuilderConfig.BUILDER_MUSHROOM_TYPE.name(), BuilderMushroomType.class));
        rows.add(RowModel.toggle("Buy Resources (not active)", BuilderConfig.BUILDER_BUY_RESOURCES.name()));
        rows.add(RowModel.section("Bounds (max 481)"));
        rows.add(RowModel.value("Width", BuilderConfig.BUILDER_WIDTH.name()));
        rows.add(RowModel.value("Depth", BuilderConfig.BUILDER_DEPTH.name()));
        rows.add(RowModel.section("Blocks"));
        rows.add(RowModel.value("Building Block", BuilderConfig.BUILDER_BUILDING_BLOCK.name()));
        rows.add(RowModel.value("Indicator Block", BuilderConfig.BUILDER_INDICATOR_BLOCK.name()));
        rows.add(RowModel.value("Slab Block", BuilderConfig.BUILDER_SLAB_BLOCK.name()));
        rows.add(RowModel.section("Generated Farm Setup"));
        rows.add(RowModel.toggle("Prepare Materials", dev.veltraph.client.config.GeneratedBuilderConfig.PREPARATION.name()));
        rows.add(RowModel.toggle("Ironman Mode", dev.veltraph.client.config.GeneratedBuilderConfig.IRONMAN_MODE.name()));
        rows.add(RowModel.value("Restock Hotbar Slot", dev.veltraph.client.config.GeneratedBuilderConfig.RESTOCK_SLOT.name()));
        rows.add(RowModel.value("Temporary Block", dev.veltraph.client.config.GeneratedBuilderConfig.TEMPORARY_BLOCK.name()));
        rows.add(RowModel.value("Clearance Block", dev.veltraph.client.config.GeneratedBuilderConfig.CLEAR_BLOCK.name()));
        rows.add(RowModel.value("Tilling Tool", dev.veltraph.client.config.GeneratedBuilderConfig.TILL_ITEM.name()));
        rows.add(RowModel.value("Planting Item", dev.veltraph.client.config.GeneratedBuilderConfig.PLANT_ITEM.name()));
        rows.add(RowModel.value("Planted Block", dev.veltraph.client.config.GeneratedBuilderConfig.PLANT_BLOCK.name()));
        rows.add(RowModel.value("Compact Layout Materials (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE92_PALETTE.name()));
        rows.add(RowModel.value("Setup Tool Supply (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_RESOURCE_BINDING.name()));
        rows.add(RowModel.value("Planting Tool Supply (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_PLANT_RESOURCE_BINDING.name()));
        rows.add(RowModel.value("Marker Column", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_MARKER_X.name()));
        rows.add(RowModel.value("Marker Block", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_MARKER_BLOCK.name()));
        rows.add(RowModel.value("Setup Tool Output Block", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_TOOL_BLOCK.name()));
        rows.add(RowModel.value("Preparation-off Tool Contract (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_PREPARATION_OFF_TOOL_BINDING.name()));
        rows.add(RowModel.value("Resource Purchase Flow (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_ACQUISITION_FLOW.name()));
        rows.add(RowModel.value("Crop Purchase Contract (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_CROP_ACQUISITION_BINDING.name()));
        rows.add(RowModel.value("Inventory Sale Flow (JSON)", dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_SELL_FLOW.name()));
        rows.add(RowModel.info("Generated Setup Requirements", "Select exact materials, tools and menu quotes before starting a generated plan. Blank resource bindings block the affected step. Construction and tool-supply execution still require native verification."));
        rows.add(RowModel.section("Local Garden Blueprint Scan / Copy"));
        rows.add(RowModel.toggle("Garden Copy Enabled", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_ENABLED.name()));
        rows.add(RowModel.value("Scan Maximum Cells", dev.veltraph.client.config.GardenCopyConfig.GARDEN_SCAN_MAX_CELLS.name()));
        rows.add(RowModel.value("Scan Cells per Tick", dev.veltraph.client.config.GardenCopyConfig.GARDEN_SCAN_BATCH.name()));
        rows.add(RowModel.value("Copy Maximum Operations", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_MAX_OPERATIONS.name()));
        rows.add(RowModel.value("Copy Outcome Stability (ms)", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_ACK_MS.name()));
        rows.add(RowModel.value("Copy Operation Timeout (ms)", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_OPERATION_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Copy Total Timeout (ms)", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Copy Maximum Attempts", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_MAX_ATTEMPTS.name()));
        rows.add(RowModel.toggle("Copy Flight (native permission required)", dev.veltraph.client.config.GardenCopyConfig.GARDEN_COPY_ALLOW_FLIGHT.name()));
        rows.add(RowModel.info("Garden Blueprint Setup", "Use /vgarden scan with explicit bounds, then save or prepare a rotated local paste. Scan retains AIR and full block properties; copy requires explicit start/resume, matching resources and server-observed placement."));
        rows.add(RowModel.info("Garden Blueprint Scope", "Read-only dense scans and ordinary structural copying have native acceptance. Existing nonmatching blocks and unsupported missing crop/fluid/special states are refused. Arbitrary full-farm construction and the original multi-plot workflow remain incomplete."));
        rows.add(RowModel.section("Prepared Plot Cleaner"));
        rows.add(RowModel.toggle("Plot Cleaner Enabled", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_ENABLED.name()));
        rows.add(RowModel.toggle("Ordinary Mining Counterpart", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_VANILLA_ROWS.name()));
        rows.add(RowModel.toggle("Owner-Verified Cleaner Bindings", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_BINDINGS_VERIFIED.name()));
        rows.add(RowModel.value("Cleaner Deadline (ms)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Cleaner Scan Cell Budget", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_MAX_BLOCKS.name()));
        rows.add(RowModel.value("Cleaner Air Lift (local approach)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_AIR_LIFT.name()));
        rows.add(RowModel.value("Cleaner Desk Command (observed)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_DESK_COMMAND.name()));
        rows.add(RowModel.value("Cleaner Desk Title (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_DESK_TITLE.name()));
        rows.add(RowModel.value("Configure Plots Action (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_CONFIGURE_NAME.name()));
        rows.add(RowModel.value("Plot Configuration Title (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_PLOTS_TITLE.name()));
        rows.add(RowModel.value("Selected Plot Title ({plot})", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_PLOT_TITLE.name()));
        rows.add(RowModel.value("Selected Plot Action ({plot})", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_PLOT_NAME.name()));
        rows.add(RowModel.value("Superflat Action (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_SUPERFLAT_NAME.name()));
        rows.add(RowModel.value("Cleaner Confirmation Title (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_CONFIRM_TITLE.name()));
        rows.add(RowModel.value("Cleaner Confirmation Action (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_CONFIRM_NAME.name()));
        rows.add(RowModel.value("Ready Confirmation Lore (exact)", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_CONFIRM_READY_LORE.name()));
        rows.add(RowModel.value("Targeted Cleaning Progress Prefix", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_PROGRESS_PREFIX.name()));
        rows.add(RowModel.info("Cleaner Preparation", "/vplotcleaner prepare <plot> <minX> <minY> <minZ> <maxX> <maxY> <maxZ> stores immutable inactive bounds; no operation starts."));
        rows.add(RowModel.info("Cleaner Commands", "/vplotcleaner inspect / clear (inactive only); start uses the shared guard; stop cancels. Active requests cannot be replaced."));
        rows.add(RowModel.info("Cleaner Defaults", "All three opt-ins default off; eleven command/menu strings default empty. Observe exact scope/ready/progress bindings before start; the scope echo is a local safety contract."));
        rows.add(RowModel.info("Cleaner Bounds", "Plot 1-24, Garden X/Z -240..239, floor >=66, at most 96x32x96 and configured scan budget. Return pad must remain outside bounds."));
        rows.add(RowModel.info("Cleaner Counterpart", "Existing server flight permission and real shovel required; ordinary guarded dirt/grass mining only. Crops/stone/fluids/containers stay protected; native bulk-row ability and live preset are unrecovered."));
        return rows;
    }

    /** Explicit single-menu and configured inventory/sacks Bazaar workflows. */
    private static List<RowModel> miscRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.toggle("Automatic Harp Timing", dev.veltraph.client.gui.HarpConfig.AUTOMATIC_HARP_TICK_RATE_DETECTION.name()));
        rows.add(RowModel.value("Harp Manual Tick Delay", MiscConfig.AUTO_HARP_TICK_DELAY.name()));
        rows.add(RowModel.info("Harp Timing Availability", "Automatic mode is UNSUPPORTED until the source retention and common-input cooldown values are recovered. Manual mode uses local timing; source default delay is 10 ticks."));
        rows.add(RowModel.section("Chocolate Factory Planner / Selected Queue"));
        rows.add(RowModel.toggle("Chocolate Factory Actions (opt-in)", ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED.name()));
        rows.add(RowModel.value("Chocolate Factory Selected Profile (exact JSON)", ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE.name()));
        rows.add(RowModel.info("Factory Capture", "Actions default off. In the actual native Factory menu, use Factory tools and /vchocolate inspect / show / begin / field / upgrade / save to capture exact title, profile-specific lore, identities, prices and numeric fields. /vchocolate use selects a local contract."));
        rows.add(RowModel.info("Factory Planner / Budget", "/vchocolate plan <reserve> and goal <id> <walletGoal> <reserve> report observed affordability and estimates. select <id[,id...]> <totalQueueBudget> <minimumWalletReserve> prepares 1–16 distinct observed upgrades; start is a separate guarded action."));
        rows.add(RowModel.info("Factory Deadlines", "The exact profile contract supplies freshnessMillis and receiptTimeoutMillis: freshness 100–60,000 ms, receipt 1,000–60,000 ms. Select/start promptly; changed price, level, identity, profile or expired evidence requires fresh selection."));
        rows.add(RowModel.info("Factory Saved Profiles", "System-managed CHOCOLATE_FACTORY_PROFILES is the saved local profile map, read-only here. /vchocolate save / use preserves other scopes; selecting a local profile does not switch a game profile."));
        rows.add(RowModel.info("Factory Transaction History", "System-managed CHOCOLATE_FACTORY_HISTORY is read-only here and preserves UNKNOWN outcomes, native receipts and original queue intent IDs. queues lists pending IDs and original scopes. Explicit recovery preserves other scopes. Manual Chocolate debit remains separate from native spend, native ACKs and Coin value. Failed durable writes stay pending; reconciliation never automatically rearms an action."));
        rows.add(RowModel.info("Factory Queue Reconciliation", "Open and inspect the native result, then run queue-inspect <intent> <original-scope> declare-original-account. Record the result with reconcile <intent> purchased|not-purchased <original-scope> declare-original-account. Both commands repeat the exact ID, original scope and explicit human account declaration. Account identity remains unproven; start requires a fresh deliberate selection."));
        rows.add(RowModel.info("Factory Consequential Actions", "On the supported captured layouts: tower-select then start selects one XV Tower charge; prestige-preview <maximumObservedWallet> then start opens the eligible III-to-IV preview only. prestige-confirm reset-all separately accepts resetting all Chocolate and earned progress. The preview does not show the current wallet, so the maximum only bounds the observed parent snapshot."));
        rows.add(RowModel.info("Factory Action Reconciliation", "actions lists exact consequential intent IDs and original local scopes. After inspecting the native result, action-reconcile <id> applied|not-applied <original-scope> records a manual declaration against a fresh loaded inspection. A local scope is not persistent account proof; reconciliation never automatically rearms an action."));
        rows.add(RowModel.info("Factory Validation", "On the supported layouts, select employee or Barn upgrades, activate XV Tower charges, or explicitly confirm a III-to-IV prestige reset. Missing results latch UNKNOWN with no retry. Other tiers, faction decisions, permanent consumption and upkeep payments need new exact bindings. Planner rate deltas remain explicit assumptions; Chocolate amounts are not Coin valuations."));
        rows.add(RowModel.section("Leased Farm Auto-sell"));
        rows.add(RowModel.toggle("Farm Auto-sell Enabled", dev.veltraph.client.config.AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_ENABLED.name()));
        rows.add(RowModel.value("Exact Active Cookie Footer Line", dev.veltraph.client.config.AutoSellMaintenanceConfig.AUTOSELL_COOKIE_ACTIVE_LINE.name()));
        rows.add(RowModel.value("Auto Sell Cookie Grammar (JSON)", dev.veltraph.client.config.AutoSellMaintenanceConfig.AUTOSELL_COOKIE_GRAMMAR_JSON.name()));
        rows.add(RowModel.value("Maximum Empty Non-equipment Slots (0-2)", dev.veltraph.client.config.AutoSellMaintenanceConfig.AUTOSELL_MAX_EMPTY_SLOTS.name()));
        rows.add(RowModel.value("Farm Auto-sell Cooldown (ms)", dev.veltraph.client.config.AutoSellMaintenanceConfig.AUTOSELL_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Farm Auto-sell Deadline (ms)", dev.veltraph.client.config.AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS.name()));
        rows.add(RowModel.info("Farm Auto-sell Setup", "Off by default. Requires one exact positive-value authorized stack, current cookie evidence, inventory pressure, and the existing NPC/Trash rules. The owned helper restores the saved farm pose/gear and resumes the same engine after confirmed cleanup."));
        rows.add(RowModel.info("Farm Auto-sell Scope", "Production Garden NPC auto-start, terminal-stop precedence, emergency-heal exclusion and same-engine return/resume have native acceptance. Trash wrapper, Bazaar/George travel, bulk and sacks remain separate. Missing receipts stop both controllers without retry or resume."));
        rows.add(RowModel.section("Bazaar Inventory Sale"));
        rows.add(RowModel.toggle("Auto-Sell Enabled", MiscConfig.AUTO_SELL_ENABLED.name()));
        rows.add(RowModel.enumRow("Sell To", MiscConfig.AUTO_SELL_MODE.name(), AutoSellMode.class));
        rows.add(RowModel.toggle("Empty Sacks (configured custody profile only)", MiscConfig.AUTO_SELL_SACKS.name()));
        rows.add(RowModel.info("Bazaar Workflow Selection", "Bazaar Inventory Sale starts in an open menu and requires sacks off. Bazaar Inventory and Sacks opens the configured menu and requires the custody toggle/profile; Empty Sacks must match the profile. Start the selected card separately."));
        rows.add(RowModel.value("Sale Maximum Quantity", InventoryUtilityConfig.AUTO_SELL_MAX_QUANTITY.name()));
        rows.add(RowModel.value("Sale Minimum Proceeds", InventoryUtilityConfig.AUTO_SELL_MIN_PROCEEDS.name()));
        rows.add(RowModel.value("Sale Maximum Proceeds", InventoryUtilityConfig.AUTO_SELL_MAX_PROCEEDS.name()));
        rows.add(RowModel.value("Sale Click Delay (ticks)", InventoryUtilityConfig.AUTO_SELL_DELAY_TICKS.name()));
        rows.add(RowModel.value("Sale Timeout (ticks)", InventoryUtilityConfig.AUTO_SELL_TIMEOUT_TICKS.name()));
        rows.add(RowModel.value("Sale Menu Title", InventoryUtilityConfig.AUTO_SELL_MENU_TITLE.name()));
        rows.add(RowModel.value("Sell Inventory Button", InventoryUtilityConfig.AUTO_SELL_BUTTON.name()));
        rows.add(RowModel.value("Sale Confirm Menu Title", InventoryUtilityConfig.AUTO_SELL_CONFIRM_TITLE.name()));
        rows.add(RowModel.value("Sale Confirm Button", InventoryUtilityConfig.AUTO_SELL_CONFIRM_BUTTON.name()));
        rows.addAll(equipmentRows());
        rows.addAll(sellingSetupRows());
        rows.add(RowModel.section("Exact Abiphone Contact Call"));
        rows.add(RowModel.toggle("Abiphone Contact Call", dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_ENABLED.name()));
        rows.add(RowModel.value("Abiphone Call Contract (exact JSON)", dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_CONTRACT.name()));
        rows.add(RowModel.value("Call Delay (ticks; -1 unset)", dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_DELAY_TICKS.name()));
        rows.add(RowModel.value("Call Timeout (ticks; -1 unset)", dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_TIMEOUT_TICKS.name()));
        rows.add(RowModel.toggle("Retain Dialogue (caller-managed lease)", dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_RETAIN_DIALOGUE.name()));
        rows.add(RowModel.info("Abiphone Setup", "Off by default; blank contract and -1 timings cannot start. Declare delay >=1 and timeout greater than delay, at most 1,200 ticks."));
        rows.add(RowModel.info("Abiphone Identity", "Exact single phone/contact/NPC marker registry, name and full components; distinct menu titles and received receipt required. A main-inventory phone needs a free hotbar slot."));
        rows.add(RowModel.info("Abiphone Restoration", "Main-slot restoration needs a received post-SWAP revision and exact original pair; selection is restored too. Silent or uncertain restoration remains RECOVERY_REQUIRED; no retry."));
        rows.add(RowModel.info("Abiphone Dialogue Lease", "Leave retention off for standalone calls. Retained dialogue needs an explicit caller release; manual recovery acknowledgment verifies restored pair/selection, closed menu and empty cursor, without completing or retrying the call."));
        rows.add(RowModel.info("Abiphone Scope", "One native contact call only; merchant/George/Phillip jobs and their unrecovered command-call prefix remain separate. New calls cannot preempt active global healing/Hydra control."));
        rows.add(RowModel.section("Terminal Stop Timer"));
        rows.add(RowModel.toggle("Stop Timer Enabled", dev.veltraph.client.config.SchedulingConfig.TIMER_ENABLED.name()));
        rows.add(RowModel.value("Stop Timer Duration", dev.veltraph.client.config.SchedulingConfig.TIMER_DURATION.name()));
        rows.add(RowModel.enumRow("Stop Timer Unit", dev.veltraph.client.config.SchedulingConfig.TIMER_UNIT.name(), dev.veltraph.client.scheduling.ScheduleTimeUnit.class));
        rows.add(RowModel.info("Timer State", "timerStatus"));
        rows.add(RowModel.info("Timer Defaults", "Off by default; duration 1 Hour. Seconds/Minutes/Hours/Days use source client-update timing and expire once to stop the captured owner, not restart it."));
        rows.add(RowModel.info("Timer Applicability", "Registered primary Farming/Combat/Foraging/Mining/Fishing/Hunting owners only; underwater fishing maps to Hunting. Passive observers, maintenance helpers and unrelated GUI/inventory utilities do not get a timer."));
        rows.add(RowModel.info("Timer Rearming", "Only a successful fresh disabled-to-enabled start arms it. Duplicate enabled starts, maintenance resume and normal-cycle restart do not rearm; changed profile/settings, manual stop, death and world change cancel authority."));
        rows.add(RowModel.info("Dynamic Rest Scope", "Only terminal TIMER is integrated. Stored rest preferences do not register rest ports, disconnect/rejoin transport or world exemptions; unsupported enabled rest fails readiness."));
        rows.add(RowModel.section("Session Preferences (not active)"));
        rows.add(RowModel.toggle("Auto-Reconnect", MiscConfig.AUTO_RECONNECT_ENABLED.name()));
        rows.add(RowModel.value("Reconnect Cooldown Ms", MiscConfig.AUTO_RECONNECT_COOLDOWN_MS.name()));
        rows.add(RowModel.toggle("Stored Dynamic Rest Preference (unsupported)", MiscConfig.DYNAMIC_REST_ENABLED.name()));
        rows.add(RowModel.toggle("Anti-AFK", MiscConfig.ANTI_AFK_ENABLED.name()));
        rows.add(RowModel.disabled("Other Sell / Session Workflows",
                "Bazaar and configured NPC/George/trash transactions are available; bulk/cookie/warp/sacks, rest/anti-AFK and reconnect remain incomplete"));
        return rows;
    }

    private static List<RowModel> quiverRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Owned Quiver Refill"));
        rows.add(RowModel.info("Quiver Setup", "Setup: blank ammo/menu/receipt contracts block refill."));
        rows.add(RowModel.toggle("Quiver Refill Enabled", QuiverConfig.QUIVER_ENABLED.name()));
        rows.add(RowModel.enumRow("Quiver Opening Method", QuiverIngressConfig.QUIVER_OPENER_MODE.name(), QuiverIngressConfig.Opener.class));
        rows.add(RowModel.value("Quiver Merchant Name (exact)", QuiverIngressConfig.QUIVER_NPC_NAME.name()));
        rows.add(RowModel.value("Quiver Merchant Entity Type", QuiverIngressConfig.QUIVER_NPC_TYPE.name()));
        rows.add(RowModel.value("Quiver Merchant Search Distance (blocks)", QuiverIngressConfig.QUIVER_NPC_MAX_DISTANCE.name()));
        rows.add(RowModel.info("Merchant Opening", "Named merchant opening requires a unique loaded actor with the configured name and entity type, safe approach and a verified refill. Private native approach, refill, return and cancellation checks passed; live identities and shop text require configuration."));
        rows.add(RowModel.enumRow("Quiver Supply Mode", QuiverConfig.QUIVER_SUPPLY.name(), dev.veltraph.client.combat.quiver.QuiverSupply.class));
        rows.add(RowModel.value("Quiver Menu Title (exact)", QuiverConfig.QUIVER_MENU_TITLE.name()));
        rows.add(RowModel.value("Fill Quiver Button", QuiverConfig.QUIVER_FILL_BUTTON.name()));
        rows.add(RowModel.value("Ammo Registry ID", QuiverConfig.QUIVER_AMMO_ID.name()));
        rows.add(RowModel.value("Ammo Display Name", QuiverConfig.QUIVER_AMMO_NAME.name()));
        rows.add(RowModel.value("Quote ({quantity}, {item}, {cost})", QuiverConfig.QUIVER_QUOTE_TEMPLATE.name()));
        rows.add(RowModel.value("Filled Receipt Template", QuiverConfig.QUIVER_ACK_TEMPLATE.name()));
        rows.add(RowModel.value("Currency Sidebar Label (optional)", QuiverConfig.QUIVER_CURRENCY_LABEL.name()));
        rows.add(RowModel.value("Quiver Refill Quantity", QuiverConfig.QUIVER_QUANTITY.name()));
        rows.add(RowModel.value("Quiver Maximum Cost (required positive)", QuiverConfig.QUIVER_MAX_COST.name()));
        rows.add(RowModel.value("Quiver Delay (ticks)", QuiverConfig.QUIVER_DELAY_TICKS.name()));
        rows.add(RowModel.value("Quiver Timeout (ticks)", QuiverConfig.QUIVER_TIMEOUT_TICKS.name()));
        rows.add(RowModel.value("Quiver Maximum Refills per Run", QuiverConfig.QUIVER_MAX_REFILLS_PER_RUN.name()));
        rows.add(RowModel.value("Quiver Opener Slot (1-9)", QuiverConfig.QUIVER_OPENER_SLOT.name()));
        rows.add(RowModel.value("Quiver Opener Registry ID", QuiverConfig.QUIVER_OPENER_ID.name()));
        rows.add(RowModel.value("Quiver Opener Display Name", QuiverConfig.QUIVER_OPENER_NAME.name()));
        rows.add(RowModel.value("Container Supply Indicator Label", QuiverConfig.QUIVER_SUPPLY_BUTTON.name()));
        rows.add(RowModel.value("Container Supply Count Template", QuiverConfig.QUIVER_SUPPLY_TEMPLATE.name()));
        rows.add(RowModel.info("Supply Setup", "Container mode observes quiver contents, not player arrows."));
        return rows;
    }

    private static List<RowModel> equipmentRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Inventory & Equipment"));
        rows.add(RowModel.info("Equipment Setup", "Setup: close menus; set exact IDs, markers and pacing."));
        rows.add(RowModel.toggle("Equipment Workflow Enabled", EquipmentConfig.EQUIPMENT_WORKFLOW_ENABLED.name()));
        rows.add(RowModel.enumRow("Equipment Workflow Kind", EquipmentConfig.EQUIPMENT_WORKFLOW_KIND.name(), dev.veltraph.client.inventory.equipment.EquipmentWorkflow.Kind.class));
        rows.add(RowModel.value("Equipment Menu Title", EquipmentConfig.EQUIPMENT_MENU_TITLE.name()));
        rows.add(RowModel.value("Equipment Action Label", EquipmentConfig.EQUIPMENT_ACTION_LABEL.name()));
        rows.add(RowModel.value("Action Icon Registry ID", EquipmentConfig.EQUIPMENT_ACTION_ITEM_ID.name()));
        rows.add(RowModel.value("Target Icon Registry ID", EquipmentConfig.EQUIPMENT_TARGET_ITEM_ID.name()));
        rows.add(RowModel.value("Source State Marker", EquipmentConfig.EQUIPMENT_SOURCE_MARKER.name()));
        rows.add(RowModel.value("Target State Marker", EquipmentConfig.EQUIPMENT_TARGET_MARKER.name()));
        rows.add(RowModel.value("Click Delay (required positive ticks)", EquipmentConfig.EQUIPMENT_CLICK_DELAY_TICKS.name()));
        rows.add(RowModel.value("Timeout (ticks, greater than delay)", EquipmentConfig.EQUIPMENT_TIMEOUT_TICKS.name()));
        rows.add(RowModel.section("Compactor / Deletor Device"));
        rows.add(RowModel.value("Device Registry ID", EquipmentConfig.EQUIPMENT_DEVICE_ITEM_ID.name()));
        rows.add(RowModel.value("Device Display Name", EquipmentConfig.EQUIPMENT_DEVICE_ITEM_NAME.name()));
        rows.add(RowModel.section("Pet / Wardrobe Menu"));
        rows.add(RowModel.value("Pet/Wardrobe Menu Command", EquipmentConfig.EQUIPMENT_MENU_COMMAND.name()));
        rows.add(RowModel.value("Wardrobe Slot (1-18)", EquipmentConfig.EQUIPMENT_WARDROBE_SLOT.name()));
        rows.add(RowModel.value("Armor IDs (head,chest,legs,feet)", EquipmentConfig.EQUIPMENT_EXPECTED_ARMOR_IDS.name()));
        rows.add(RowModel.value("Armor Names (head,chest,legs,feet)", EquipmentConfig.EQUIPMENT_EXPECTED_ARMOR_NAMES.name()));
        rows.add(RowModel.value("Pet Server Acknowledgment (exact)", EquipmentConfig.EQUIPMENT_PET_SERVER_ACK.name()));
        rows.add(RowModel.value("Next Pet Page Label", EquipmentConfig.EQUIPMENT_NEXT_PAGE_LABEL.name()));
        rows.add(RowModel.value("Next Pet Page Icon ID", EquipmentConfig.EQUIPMENT_NEXT_PAGE_ITEM_ID.name()));
        rows.add(RowModel.value("Maximum Pet Pages", EquipmentConfig.EQUIPMENT_MAX_PET_PAGES.name()));
        return rows;
    }

    private static List<RowModel> sellingSetupRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Guarded NPC / George / Trash Selling"));
        rows.add(RowModel.info("Selling Setup", "One exact main-inventory rule; blank contracts block starts."));
        rows.add(RowModel.info("Selling Inspector Help", "Read-only inspector: /vdebug selling"));
        rows.add(RowModel.info("Selling Inspector Example", "/vdebug selling inspect npc <9..35>"));
        rows.add(RowModel.info("Selling Metadata Inspector", "/vdebug selling metadata <9..35>"));
        rows.add(RowModel.info("Selling Inspector Review", "Identity only; unit value and quote remain unset."));
        rows.add(RowModel.info("Selling Inspector Clipboard", "Click returned JSON to copy; no automatic clipboard write."));
        rows.add(RowModel.value("Selling Rules (exact JSON allowlist)", SellingConfig.SELLING_RULES_JSON.name()));
        rows.add(RowModel.toggle("NPC Selling through Cookie Menu", SellingConfig.SELLING_NPC_COOKIE_ENABLED.name()));
        rows.add(RowModel.toggle("Open NPC Selling Menu by Command", SellingConfig.AUTO_SELL_NPC_VIA_COMMAND.name()));
        rows.add(RowModel.value("NPC Cookie Menu Bindings (JSON)", SellingConfig.SELLING_NPC_COOKIE_BINDINGS.name()));
        rows.add(RowModel.value("NPC Name (exact)", SellingConfig.SELLING_NPC_NAME.name()));
        rows.add(RowModel.value("NPC Menu Title", SellingConfig.SELLING_NPC_MENU_TITLE.name()));
        rows.add(RowModel.value("NPC Receipt Template", SellingConfig.SELLING_NPC_RECEIPT_TEMPLATE.name()));
        rows.add(RowModel.value("Selling Currency Sidebar Prefix", SellingConfig.SELLING_CURRENCY_PREFIX.name()));
        rows.add(RowModel.enumRow("Selling Currency Denomination", SellingConfig.SELLING_CURRENCY_DENOMINATION.name(), dev.veltraph.client.inventory.selling.SellingAcknowledgment.Denomination.class));
        rows.add(RowModel.toggle("Bazaar Inventory / Sacks Custody", SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED.name()));
        rows.add(RowModel.value("Bazaar Custody Profile (JSON)", SellingConfig.SELLING_BAZAAR_PROFILE_JSON.name()));
        rows.add(RowModel.value("George Name (exact)", SellingConfig.SELLING_GEORGE_NAME.name()));
        rows.add(RowModel.toggle("Garden George Command Contact", SellingConfig.SELLING_GEORGE_CONTACT_ENABLED.name()));
        rows.add(RowModel.value("George Contact Profile (JSON)", SellingConfig.SELLING_GEORGE_CONTACT_JSON.name()));
        rows.add(RowModel.value("George Menu Title", SellingConfig.SELLING_GEORGE_MENU_TITLE.name()));
        rows.add(RowModel.value("George Accept Label", SellingConfig.SELLING_GEORGE_ACCEPT_LABEL.name()));
        rows.add(RowModel.value("George Confirm Title", SellingConfig.SELLING_GEORGE_CONFIRM_TITLE.name()));
        rows.add(RowModel.value("George Confirm Label", SellingConfig.SELLING_GEORGE_CONFIRM_LABEL.name()));
        rows.add(RowModel.value("George Receipt Template", SellingConfig.SELLING_GEORGE_RECEIPT_TEMPLATE.name()));
        rows.add(RowModel.value("George Deposit Slot", SellingConfig.SELLING_GEORGE_DEPOSIT_SLOT.name()));
        rows.add(RowModel.value("George Confirm Pet Slot (required)", SellingConfig.SELLING_GEORGE_CONFIRM_PET_SLOT.name()));
        rows.add(RowModel.toggle("Allow Exact-Stack Trash", SellingConfig.SELLING_TRASH_ENABLED.name()));
        rows.add(RowModel.value("Trash Anchor (dimension/XYZ/pose JSON)", SellingConfig.SELLING_TRASH_ANCHOR_JSON.name()));
        rows.add(RowModel.value("Selling Maximum Quantity", SellingConfig.SELLING_MAX_QUANTITY.name()));
        rows.add(RowModel.value("Selling Minimum Proceeds", SellingConfig.SELLING_MIN_PROCEEDS.name()));
        rows.add(RowModel.value("Selling Maximum Proceeds", SellingConfig.SELLING_MAX_PROCEEDS.name()));
        rows.add(RowModel.value("Trash Maximum Value", SellingConfig.SELLING_TRASH_MAX_VALUE.name()));
        rows.add(RowModel.value("Selling Action Delay (ticks)", SellingConfig.SELLING_DELAY_TICKS.name()));
        rows.add(RowModel.value("Selling Acknowledgment Timeout (ticks)", SellingConfig.SELLING_TIMEOUT_TICKS.name()));
        return rows;
    }

    private static List<RowModel> skillRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Local Skill Contracts"));
        rows.add(RowModel.section("Carpentry Craft Batches"));
        rows.add(RowModel.toggle("Carpentry Batches Enabled", dev.veltraph.client.config.CarpentryFlowConfig.CARPENTRY_FLOW_ENABLED.name()));
        rows.add(RowModel.value("Carpentry Batch Profile (exact JSON)", dev.veltraph.client.config.CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE.name()));
        rows.add(RowModel.info("Carpentry Batch Setup", "Off by default. Set exact recipes, menu and item details, resource quotes and receipts, batch/spending limits, and an optional Bazaar sale. Inventory and receipts must confirm each stage; unknown results stop without retry."));
        rows.add(RowModel.info("Carpentry Batch Scope", "Sales need a Bazaar inventory quote, separate confirmation and exact receipt. Optional cookie fallback needs the configured rejection, Bazaar marker and active Cookie Buff. Older NPC sale profiles must be updated."));
        rows.add(RowModel.section("Water Bottle Chest Filler"));
        rows.add(RowModel.toggle("Bottle Acquisition and Refill Enabled", dev.veltraph.client.config.BottleAcquisitionConfig.BOTTLE_ACQUISITION_ENABLED.name()));
        rows.add(RowModel.value("Bottle Acquisition Profile (exact JSON)", dev.veltraph.client.config.BottleAcquisitionConfig.BOTTLE_ACQUISITION_PROFILE.name()));
        rows.add(RowModel.toggle("Water Bottle Filler Enabled", dev.veltraph.client.config.BottleFillConfig.BOTTLE_FILL_ENABLED.name()));
        rows.add(RowModel.value("Bottle Fill Profile (exact JSON)", dev.veltraph.client.config.BottleFillConfig.BOTTLE_FILL_PROFILE.name()));
        rows.add(RowModel.value("Bottles to Fill (-1 unset)", dev.veltraph.client.config.BottleFillConfig.BOTTLE_FILL_QUANTITY.name()));
        rows.add(RowModel.value("Bottle Action Delay (ticks; -1 unset)", dev.veltraph.client.config.BottleFillConfig.BOTTLE_FILL_DELAY_TICKS.name()));
        rows.add(RowModel.value("Bottle Action Timeout (ticks; -1 unset)", dev.veltraph.client.config.BottleFillConfig.BOTTLE_FILL_TIMEOUT_TICKS.name()));
        rows.add(RowModel.info("Water Bottle Setup", "Prepare exact glass bottles and configure source-water/chest positions and standing points, full glass/water identities and chest title. Ordinary vanilla filling and fresh reopened chest contents confirm each stored bottle."));
        rows.add(RowModel.info("Water Bottle Scope", "Filling, repeated storage and known stack restoration have native acceptance. NPC buying, source warp/trough setup and salted item selectors remain separate; this is distinct from quoted bottle buying/storage."));
        rows.add(RowModel.info("Skill Setup", "Blank identities and unknown values block starting."));
        rows.add(RowModel.value("Skill Timeout (ms)", SkillAutomationConfig.SKILL_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Skill Click Delay (ms)", SkillAutomationConfig.SKILL_CLICK_DELAY_MS.name()));
        rows.add(RowModel.value("Skill Maximum Quantity", SkillAutomationConfig.SKILL_MAX_QUANTITY.name()));
        rows.add(RowModel.value("Skill Resource Value Cap", SkillAutomationConfig.SKILL_MAX_RESOURCE_VALUE.name()));
        rows.add(RowModel.section("Configured Alchemy Brew Chain"));
        rows.add(RowModel.toggle("Alchemy Brew Chain Enabled", dev.veltraph.client.config.AlchemyFlowConfig.ALCHEMY_FLOW_ENABLED.name()));
        rows.add(RowModel.value("Alchemy Flow Profile (exact JSON)", dev.veltraph.client.config.AlchemyFlowConfig.ALCHEMY_FLOW_PROFILE.name()));
        rows.add(RowModel.info("Alchemy Chain Setup", "Off by default; a blank profile cannot start. Set stands, exact bottles/fuel/reagents/results, observed values, supply quotes and receipts, and batch/spending/time limits."));
        rows.add(RowModel.info("Alchemy Layout", "Choose a standard brewing stand or a configured custom brewing menu. The custom layout reserves ingredient slot 13; set its other bottle, fuel and marker slots."));
        rows.add(RowModel.info("Alchemy Chain Acknowledgment", "Reopened stand contents and inventory must confirm placement and collection; purchases and optional sales need exact receipts. Unknown results or foreign menus/cursors stop the chain."));
        rows.add(RowModel.info("Alchemy Menu Details", "Supply matching menu titles, cookie button and item details, observed prices and receipt text. Unknown details block starting."));
        rows.add(RowModel.info("Alchemy Sale Mode", "Add terminalSale details to sell only this batch's collected potions through one cookie menu, then continue after all sales are confirmed. Without those details, the chain collects only."));
        rows.add(RowModel.section("Prepared Alchemy Brew"));
        rows.add(RowModel.info("Alchemy Start", "Open prepared brewer; use assigned container shortcut."));
        rows.add(RowModel.toggle("Prepared Brewing Enabled", SkillAutomationConfig.ALCHEMY_ENABLED.name()));
        rows.add(RowModel.value("Brewer Menu Title", SkillAutomationConfig.ALCHEMY_MENU_TITLE.name()));
        rows.add(RowModel.value("Ingredient Registry ID", SkillAutomationConfig.ALCHEMY_INGREDIENT_ID.name()));
        rows.add(RowModel.value("Ingredient Display Name", SkillAutomationConfig.ALCHEMY_INGREDIENT_NAME.name()));
        rows.add(RowModel.value("Ingredient Value (required positive)", SkillAutomationConfig.ALCHEMY_INGREDIENT_VALUE.name()));
        rows.add(RowModel.value("Ingredient Container Slot", SkillAutomationConfig.ALCHEMY_INGREDIENT_SLOT.name()));
        rows.add(RowModel.value("Prepared Bottle Registry ID", SkillAutomationConfig.ALCHEMY_BOTTLE_ID.name()));
        rows.add(RowModel.value("Prepared Bottle Display Name", SkillAutomationConfig.ALCHEMY_BOTTLE_NAME.name()));
        rows.add(RowModel.value("Brew Output Registry ID", SkillAutomationConfig.ALCHEMY_OUTPUT_ID.name()));
        rows.add(RowModel.value("Brew Output Display Name", SkillAutomationConfig.ALCHEMY_OUTPUT_NAME.name()));
        rows.add(RowModel.value("Prepared Bottle Count (1-3)", SkillAutomationConfig.ALCHEMY_BOTTLES.name()));
        rows.add(RowModel.section("Inventory Carpentry Craft"));
        rows.add(RowModel.info("Craft Start", "Open exact recipe menu; use its container shortcut."));
        rows.add(RowModel.toggle("Inventory Craft Enabled", SkillAutomationConfig.CARPENTRY_ENABLED.name()));
        rows.add(RowModel.value("Craft Menu Title", SkillAutomationConfig.CARPENTRY_MENU_TITLE.name()));
        rows.add(RowModel.value("Craft Result/Recipe Label", SkillAutomationConfig.CARPENTRY_RECIPE_LABEL.name()));
        rows.add(RowModel.value("Craft Recipe Lore (exact)", SkillAutomationConfig.CARPENTRY_RECIPE_LORE.name()));
        rows.add(RowModel.value("Craft Input Registry ID", SkillAutomationConfig.CARPENTRY_INPUT_ID.name()));
        rows.add(RowModel.value("Craft Input Display Name", SkillAutomationConfig.CARPENTRY_INPUT_NAME.name()));
        rows.add(RowModel.value("Craft Input Quantity", SkillAutomationConfig.CARPENTRY_INPUT_QUANTITY.name()));
        rows.add(RowModel.value("Craft Output Registry ID", SkillAutomationConfig.CARPENTRY_OUTPUT_ID.name()));
        rows.add(RowModel.value("Craft Output Display Name", SkillAutomationConfig.CARPENTRY_OUTPUT_NAME.name()));
        rows.add(RowModel.value("Craft Output Quantity", SkillAutomationConfig.CARPENTRY_OUTPUT_QUANTITY.name()));
        rows.add(RowModel.value("Craft Material Value", SkillAutomationConfig.CARPENTRY_CRAFT_VALUE.name()));
        rows.add(RowModel.section("Bottle Acquisition / Storage"));
        rows.add(RowModel.info("Bottle Start", "Open quoted trade menu; use its container shortcut."));
        rows.add(RowModel.toggle("Bottle Transaction Enabled", SkillAutomationConfig.BOTTLE_ENABLED.name()));
        rows.add(RowModel.value("Bottle Trade Menu Title", SkillAutomationConfig.BOTTLE_BUY_MENU_TITLE.name()));
        rows.add(RowModel.value("Bottle Buy Label", SkillAutomationConfig.BOTTLE_BUY_LABEL.name()));
        rows.add(RowModel.value("Bottle Registry ID (required)", SkillAutomationConfig.BOTTLE_ITEM_ID.name()));
        rows.add(RowModel.value("Bottle Display Name", SkillAutomationConfig.BOTTLE_ITEM_NAME.name()));
        rows.add(RowModel.value("Bottle Bundle Quantity", SkillAutomationConfig.BOTTLE_QUANTITY.name()));
        rows.add(RowModel.value("Bottle Maximum Purchase Cost", SkillAutomationConfig.BOTTLE_MAX_PURCHASE_COST.name()));
        rows.add(RowModel.value("Storage Chest XYZ", SkillAutomationConfig.BOTTLE_CHEST_POSITION.name()));
        rows.add(RowModel.value("Storage Chest Title", SkillAutomationConfig.BOTTLE_CHEST_TITLE.name()));
        rows.add(RowModel.section("Local Fairy Souls"));
        rows.add(RowModel.info("Fairy Setup", "Supply a local area/soul catalog, profile and exact marker name. Use coordinates or entries with a start point and outbound/return routes."));
        rows.add(RowModel.toggle("Local Fairy Collection", SkillAutomationConfig.FAIRY_ENABLED.name()));
        rows.add(RowModel.value("Fairy Catalog (coordinates or route JSON)", SkillAutomationConfig.FAIRY_LOCAL_CATALOG.name()));
        rows.add(RowModel.value("Fairy Local Profile ID", SkillAutomationConfig.FAIRY_PROFILE.name()));
        rows.add(RowModel.value("Fairy Marker Name (exact)", SkillAutomationConfig.FAIRY_MARKER_NAME.name()));
        rows.add(RowModel.toggle("Fairy Return to Start (coordinate entries)", SkillAutomationConfig.FAIRY_RETURN_TO_START.name()));
        rows.add(RowModel.info("Fairy Routes", "Route entries always follow both declared paths. Supply Etherwarp targets/faces and a travel item; walking hops have ten seconds and Etherwarp needs a fresh matching teleport receipt."));
        return rows;
    }

    private static List<RowModel> miningRows() {
        List<RowModel> rows = new ArrayList<>();
        
        rows.add(RowModel.section("Dwarven Commissions"));
        rows.add(RowModel.toggle("Enable Commissions", dev.veltraph.client.mining.commission.CommissionConfig.ENABLED.name()));
        rows.add(RowModel.value("Commission Observation Age (ms)", dev.veltraph.client.mining.commission.CommissionConfig.OBSERVATION_MAX_AGE_MS.name()));
        rows.add(RowModel.info("Supported Commission Work", "Global Mithril and Titanium mining. Stops at completion; regional tasks, mobs and automatic claims are not supported yet."));
        rows.add(RowModel.section("Vein Mining"));
        rows.add(RowModel.toggle("Enable Vein Mining", dev.veltraph.client.mining.vein.VeinMiningConfig.ENABLED.name()));
        rows.add(RowModel.value("Materials (MATERIAL=priority;...)", dev.veltraph.client.mining.vein.VeinMiningConfig.MATERIALS.name()));
        rows.add(RowModel.value("Nearby Scan Radius", dev.veltraph.client.mining.vein.VeinMiningConfig.SCAN_RADIUS.name()));
        rows.add(RowModel.value("Ore Respawn Wait (ms)", dev.veltraph.client.mining.vein.VeinMiningConfig.RESPAWN_WAIT_MS.name()));
        rows.add(RowModel.section("Mining Guards"));
        rows.add(RowModel.toggle("Auto Break Mining", MiningConfig.AUTO_BREAK_MINING.name()));
        rows.add(RowModel.value("Minimum Mining Y", MiningConfig.MINING_MIN_Y.name()));
        rows.add(RowModel.value("Break Timeout (ms)", MiningConfig.MINING_BREAK_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Tool Durability Reserve", MiningConfig.MINING_DURABILITY_RESERVE.name()));
        rows.add(RowModel.section("Cobblestone Generator"));
        rows.add(RowModel.toggle("Cobblestone Auto Walk", MiningConfig.COBBLE_AUTO_WALK.name()));
        rows.add(RowModel.toggle("Cobblestone Auto Sprint", MiningConfig.COBBLE_AUTO_SPRINT.name()));
        rows.add(RowModel.toggle("Cobblestone Hold Right Click", MiningConfig.COBBLE_HOLD_RIGHT_CLICK.name()));
        rows.add(RowModel.toggle("Cobblestone Craft/Store", MiningConfig.COBBLE_CRAFT_STORE_ENABLED.name()));
        rows.add(RowModel.value("Craft/Store Profile (exact JSON)", MiningConfig.COBBLE_CRAFT_STORE_PROFILE.name()));
        rows.add(RowModel.info("Craft/Store Scope", "Off by default; configure the exact recipe, menu, output, storage and resource limits. Native mining pickup, crafting, fresh chest storage and same-engine resume are accepted. Source slot 23, Super Compactor, paid recipes and ender-chest permissions remain unverified; no identity or price is inferred."));
        rows.add(RowModel.toggle("Cobblestone Change Direction", MiningConfig.COBBLE_AUTO_CHANGE_DIR.name()));
        rows.add(RowModel.section("Echo Checkpoints"));
        rows.add(RowModel.value("Recorded Echo Profile (JSON)", dev.veltraph.client.mining.recorded.RecordedEchoConfig.PROFILE.name()));
        rows.add(RowModel.info("Recorded Echo Scope", "Explicit yaw/pitch and recorded input checkpoints support native movement, jump, guarded breaking and component-pinned placement. The owned branch and existing Echo registration have native acceptance. Empty profile uses the legacy route. Recorded rest/pause scheduling remains incomplete."));
        rows.add(RowModel.value("Echo Route (standing XYZ>target XYZ;...)", MiningConfig.ECHO_ROUTE.name()));
        rows.add(RowModel.toggle("Allow Checkpoint Skipping", MiningConfig.ECHO_ALLOW_CHECKPOINT_SKIPPING.name()));
        rows.add(RowModel.toggle("Reset Echo at Rewarp", MiningConfig.ECHO_RESET_AT_REWARP.name()));
        rows.add(RowModel.toggle("Remember Echo Session", MiningConfig.ECHO_REMEMBER_LAST_SESSION.name()));
        rows.add(RowModel.disabled("Craft / Storage / HOTF", "Guarded block mining is available; auto-craft, chest storage, rejoin and HOTF/bridge workflows remain incomplete"));
        return rows;
    }

    private static List<RowModel> experimentsRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Opened Experiment Solver"));
        rows.add(RowModel.value("Experiment Click Delay (ms)", ExperimentsConfig.EXPERIMENTS_CLICK_DELAY_MS.name()));
        rows.add(RowModel.value("Experiment Acknowledgment Timeout (ms)", ExperimentsConfig.EXPERIMENTS_ACK_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Show Phase Label", ExperimentsConfig.EXPERIMENTS_SHOW_LABEL.name()));
        rows.add(RowModel.value("Input Phase Label", ExperimentsConfig.EXPERIMENTS_INPUT_LABEL.name()));
        rows.add(RowModel.value("Hidden Card Label", ExperimentsConfig.EXPERIMENTS_HIDDEN_LABEL.name()));
        rows.add(RowModel.value("Claim Rewards Label", ExperimentsConfig.EXPERIMENTS_REWARD_LABEL.name()));
        rows.add(RowModel.value("Rewards Completed Label", ExperimentsConfig.EXPERIMENTS_COMPLETED_LABEL.name()));
        rows.add(RowModel.value("Experiment Failed Label", ExperimentsConfig.EXPERIMENTS_FAILED_LABEL.name()));
        rows.add(RowModel.toggle("Maximize Experiment XP", ExperimentsConfig.MAXIMIZE_XP_EXPERIMENTS.name()));
        rows.add(RowModel.toggle("Avoid Observed XP in Superpairs", ExperimentsConfig.AVOID_XP_SUPERPAIRS.name()));
        rows.add(RowModel.value("Superpairs XP Reward Bindings (exact JSON)", ExperimentsConfig.SUPERPAIRS_XP_REWARD_BINDINGS.name()));
        rows.add(RowModel.value("Experiment Reward Threshold (JSON)", ExperimentsConfig.EXPERIMENTS_REWARD_THRESHOLD_PROFILE.name()));
        rows.add(RowModel.section("Guardian Pet Preparation"));
        rows.add(RowModel.toggle("Prepare Guardian Before Experiments", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_ENABLED.name()));
        rows.add(RowModel.value("Guardian Pet Tab Prefix", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_TAB_PREFIX.name()));
        rows.add(RowModel.value("Guardian Pet Tab Suffix", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_TAB_SUFFIX.name()));
        rows.add(RowModel.value("Guardian Pet Menu Title", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_MENU_TITLE.name()));
        rows.add(RowModel.value("Open Pet Menu Command", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_COMMAND.name()));
        rows.add(RowModel.value("Guardian Pet Name (exact)", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_PET_NAME.name()));
        rows.add(RowModel.value("Inactive Guardian Lore", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_INACTIVE_LORE.name()));
        rows.add(RowModel.value("Active Guardian Lore", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_ACTIVE_LORE.name()));
        rows.add(RowModel.value("Inactive Guardian Item ID", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_INACTIVE_ITEM_ID.name()));
        rows.add(RowModel.value("Active Guardian Item ID", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_ACTIVE_ITEM_ID.name()));
        rows.add(RowModel.value("Next Pet Page Button", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_NEXT_PAGE_NAME.name()));
        rows.add(RowModel.value("Next Pet Page Item ID", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_NEXT_PAGE_ITEM_ID.name()));
        rows.add(RowModel.value("Guardian Action Delay (ticks)", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_DELAY_TICKS.name()));
        rows.add(RowModel.value("Guardian Receipt Timeout (ticks)", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_TIMEOUT_TICKS.name()));
        rows.add(RowModel.value("Guardian Maximum Pet Pages", ExperimentsGuardianConfig.EXPERIMENTS_GUARDIAN_MAX_PAGES.name()));
        rows.add(RowModel.info("Guardian Setup", "Disabled by default. Exact pet, menu, tab and receipt bindings must be configured. Private native paging, activation, solver continuation and cancellation checks passed; choose the experiment manually before its show phase."));
        rows.add(RowModel.section("Prepared Experiment Parent"));
        rows.add(RowModel.toggle("Enable Prepared Experiment Parent", dev.veltraph.client.gui.ExperimentsParentConfig.EXPERIMENTS_PARENT_ENABLED.name()));
        rows.add(RowModel.value("Experiment Parent Profile (exact JSON)", dev.veltraph.client.gui.ExperimentsParentConfig.EXPERIMENTS_PARENT_PROFILE.name()));
        rows.add(RowModel.info("Parent Contracts", "Source-prepared parent requires explicit prices, XP credit mode and exact receipt contracts in the local profile. Permission defaults off and profile blank; native parent execution is pending. No live vocabulary or XP credit formula is inferred."));
        return rows;
    }

    private static List<RowModel> shardRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Active Fish Hunting"));
        rows.add(RowModel.toggle("Enable Active Hunting", dev.veltraph.client.config.HuntingConfig.HUNTING_ACTIVE_ENABLED.name()));
        rows.add(RowModel.value("Active Hunting Profile (JSON)", dev.veltraph.client.config.HuntingConfig.HUNTING_ACTIVE_PROFILE.name()));
        rows.add(RowModel.info("Active Hunting Requirements", "Disabled until an exact region, target, tool, cost and outcome profile is configured. Native component outcomes are verified; production factory verification is pending. Unknown delivery stops without another charge."));
        rows.add(RowModel.section("Shard Workflow"));
        rows.add(RowModel.toggle("Capture Tracking", ShardConfig.SHARD_CAPTURE_ENABLED.name()));
        rows.add(RowModel.toggle("Shard Workflow Enabled", ShardConfig.SHARD_WORKFLOW_ENABLED.name()));
        rows.add(RowModel.enumRow("Operation", ShardConfig.SHARD_OPERATION.name(), ShardConfig.Operation.class));
        rows.add(RowModel.toggle("Allow Shard Purchases", ShardConfig.SHARD_ALLOW_PURCHASES.name()));
        rows.add(RowModel.toggle("Allow Shard Consumption", ShardConfig.SHARD_ALLOW_CONSUMPTION.name()));
        rows.add(RowModel.value("Wanted Attribute Level", ShardConfig.SHARD_LEVEL_WANTED.name()));
        rows.add(RowModel.value("Maximum Price per Attribute", ShardConfig.SHARD_MAX_PPA.name()));
        rows.add(RowModel.value("Maximum Session Cost", ShardConfig.SHARD_MAX_SESSION_COST.name()));
        rows.add(RowModel.value("Maximum Shard Quantity", ShardConfig.SHARD_MAX_QUANTITY.name()));
        rows.add(RowModel.value("Shard Acknowledgment Timeout (ms)", ShardConfig.SHARD_ACK_TIMEOUT.name()));
        rows.add(RowModel.section("Local Identities and Commands (required)"));
        rows.add(RowModel.value("Shard Catalog (name=RARITY;...)", ShardConfig.SHARD_CATALOG.name()));
        rows.add(RowModel.value("Wanted Shard Names (semicolon list)", ShardConfig.SHARD_WANTED.name()));
        rows.add(RowModel.value("Open Shards Command", ShardConfig.SHARD_OPEN_COMMAND.name()));
        rows.add(RowModel.value("Open Attributes Command", ShardConfig.SHARD_ATTRIBUTE_COMMAND.name()));
        rows.add(RowModel.value("Shard Search Command ({shard})", ShardConfig.SHARD_SEARCH_COMMAND.name()));
        rows.add(RowModel.section("Exact Syphon Menu Labels"));
        rows.add(RowModel.value("Shards Menu Title", ShardConfig.SHARD_MENU_TITLE.name()));
        rows.add(RowModel.value("Syphon Confirm Title", ShardConfig.SHARD_CONFIRM_TITLE.name()));
        rows.add(RowModel.value("Mass Syphon Button", ShardConfig.SHARD_MASS_BUTTON.name()));
        rows.add(RowModel.value("Syphon Confirm Button", ShardConfig.SHARD_CONFIRM_BUTTON.name()));
        rows.add(RowModel.section("Attribute Reading"));
        rows.add(RowModel.value("Attribute Menu Title", ShardConfig.SHARD_ATTRIBUTE_TITLE.name()));
        rows.add(RowModel.value("Advanced Mode Button", ShardConfig.SHARD_ADVANCED_BUTTON.name()));
        rows.add(RowModel.value("Advanced Mode Marker", ShardConfig.SHARD_ADVANCED_MARKER.name()));
        rows.add(RowModel.value("Next Attribute Page Button", ShardConfig.SHARD_NEXT_BUTTON.name()));
        rows.add(RowModel.value("Attribute Progress Pattern", ShardConfig.SHARD_PROGRESS_REGEX.name()));
        rows.add(RowModel.section("Purchase Menu and Quote Labels"));
        rows.add(RowModel.value("Shard Buy Menu Title", ShardConfig.SHARD_BUY_TITLE.name()));
        rows.add(RowModel.value("Shard Buy Button", ShardConfig.SHARD_BUY_BUTTON.name()));
        rows.add(RowModel.value("Quantity Menu Title", ShardConfig.SHARD_QUANTITY_TITLE.name()));
        rows.add(RowModel.value("Buy One Button", ShardConfig.SHARD_BUY_ONE_BUTTON.name()));
        rows.add(RowModel.value("Custom Amount Button", ShardConfig.SHARD_CUSTOM_BUTTON.name()));
        rows.add(RowModel.value("Purchase Confirm Title", ShardConfig.SHARD_PURCHASE_CONFIRM_TITLE.name()));
        rows.add(RowModel.value("Purchase Confirm Button", ShardConfig.SHARD_PURCHASE_CONFIRM_BUTTON.name()));
        rows.add(RowModel.value("Shard Price Pattern", ShardConfig.SHARD_PRICE_REGEX.name()));
        return rows;
    }

    private static List<RowModel> scriptingRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Travel"));
        rows.add(RowModel.value("Shared Rotation Speed", dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER.name()));
        rows.add(RowModel.enumRow("Approach Method", dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE.name(),dev.veltraph.client.navigation.ApproachMode.class));
        rows.add(RowModel.value("Flight Speed", dev.veltraph.client.config.TravelConfig.FLIGHT_MAX_SPEED.name()));
        rows.add(RowModel.value("Flight Stall (ms)", dev.veltraph.client.config.TravelConfig.FLIGHT_STALL_MS.name()));
        rows.add(RowModel.value("Flight Search Radius", dev.veltraph.client.config.TravelConfig.FLIGHT_SEARCH_RADIUS.name()));
        rows.add(RowModel.value("Flight Search Budget", dev.veltraph.client.config.TravelConfig.FLIGHT_NODE_BUDGET.name()));
        rows.add(RowModel.value("Etherwarp Timeout (ms)", dev.veltraph.client.config.TravelConfig.ETHERWARP_TIMEOUT_MS.name()));
        rows.add(RowModel.value("Etherwarp Stable Ticks", dev.veltraph.client.config.TravelConfig.ETHERWARP_STABLE_TICKS.name()));
        rows.add(RowModel.value("Etherwarp Ability Lore", dev.veltraph.client.config.TravelConfig.ETHERWARP_ABILITY_TOKEN.name()));
        rows.add(RowModel.section("Scripting"));
        rows.add(RowModel.toggle("Debug Log Enabled", DebugConfig.DEBUG_LOG_ENABLED.name()));
        rows.add(RowModel.enumRow("Debug Log Mode", DebugConfig.DEBUG_LOG_MODE.name(), DebugLogMode.class));
        return rows;
    }

    /**
     * Developer / Local Test Harness page (Target 1). The Dev Mode toggle forces verbose logging; the test
     * buttons are generated from the shared {@code DevTestRegistry} (the same one {@code /vdebug dev} uses),
     * so UI and command never drift. Buttons run nothing until clicked, and every action is gated behind
     * Developer Mode (SERVER_BOUND ones additionally need the explicit Allow toggle / Execute control).
     */
    private static List<RowModel> developerRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Developer Mode"));
        rows.add(RowModel.toggle("Developer Mode (forces verbose log)", DeveloperConfig.DEV_MODE_ENABLED.name()));
        rows.add(RowModel.toggle("Allow Server-Bound Tests", DeveloperConfig.DEV_ALLOW_SERVER_BOUND.name()));
        rows.add(RowModel.info("Log Status", "logStatus"));

        // One section per category, one button per registered action (metadata only — the screen wires the
        // click to the live registry + MC context, applying the Dev-Mode / risk gate).
        DevTestCategory current = null;
        for (DevTestAction a : DevTestActions.standard().actions()) {
            if (a.category() != current) {
                current = a.category();
                rows.add(RowModel.section("Tests — " + current.displayName()));
            }
            String suffix = switch (a.risk()) {
                case LOCAL_ONLY -> "";
                case DRY_RUN -> "  (dry-run)";
                case SERVER_BOUND -> "  (server-bound)";
            };
            rows.add(RowModel.button(a.label() + suffix, a.id()));
        }
        return rows;
    }

    /** All Hydra / Anti-Staff settings exposed in-game under the Anti-Staff tab. */
    private static List<RowModel> antiStaffRows() {
        List<RowModel> rows = new ArrayList<>();
        rows.add(RowModel.section("Anti-Staff"));
        rows.add(RowModel.toggle("Anti-Staff Watcher", HydraConfig.STAFF_ALERT_ENABLED.name()));
        rows.add(RowModel.toggle("Alert Sound", HydraConfig.STAFF_ALERT_SOUND.name()));
        rows.add(RowModel.toggle("Alert Title", HydraConfig.STAFF_ALERT_TITLE.name()));
        rows.add(RowModel.toggle("Auto-Stop Macro On Trigger", HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO.name()));

        rows.add(RowModel.section("Alert Thresholds"));
        rows.add(RowModel.value("Alert Distance Blocks", HydraConfig.STAFF_ALERT_DISTANCE_BLOCKS.name()));
        rows.add(RowModel.value("Danger Distance Blocks", HydraConfig.STAFF_ALERT_DANGEROUS_DISTANCE_BLOCKS.name()));
        rows.add(RowModel.value("Alert Cooldown Ms", HydraConfig.STAFF_ALERT_COOLDOWN_MS.name()));
        rows.add(RowModel.value("Local Whitelist", HydraConfig.LOCAL_WHITELIST.name()));

        rows.add(RowModel.section("Detection Triggers"));
        rows.add(RowModel.toggle("Detect Lagback", HydraConfig.DETECT_LAGBACK.name()));
        rows.add(RowModel.value("Lagback Distance Blocks", HydraConfig.LAGBACK_DISTANCE_BLOCKS.name()));
        rows.add(RowModel.toggle("Detect Teleport-Into-Block", HydraConfig.DETECT_TELEPORT_INTO_BLOCK.name()));
        rows.add(RowModel.value("Teleport Distance Blocks", HydraConfig.TELEPORT_DISTANCE_BLOCKS.name()));
        rows.add(RowModel.toggle("Detect Forced Adventure", HydraConfig.DETECT_FORCED_ADVENTURE.name()));
        rows.add(RowModel.toggle("Detect Routing/Kick Text", HydraConfig.DETECT_ROUTING_TEXT.name()));
        rows.add(RowModel.value("Routing Text Keywords", HydraConfig.ROUTING_TEXT_KEYWORDS.name()));
        rows.add(RowModel.toggle("Detect Floor Change", HydraConfig.DETECT_FLOOR_CHANGE.name()));
        rows.add(RowModel.toggle("Detect Barrier In Path", HydraConfig.DETECT_BARRIER.name()));
        rows.add(RowModel.value("Barrier Stationary Speed", HydraConfig.BARRIER_STATIONARY_SPEED.name()));

        rows.add(RowModel.section("Staff Response"));
        rows.add(RowModel.enumRow("Response Policy (UI)", HydraConfig.STAFF_RESPONSE_POLICY.name(),
                HydraResponsePolicy.class));
        rows.add(RowModel.toggle("Auto-Reply Extreme Lines", HydraConfig.AUTO_REPLY_EXTREME.name()));
        rows.add(RowModel.toggle("Fix Angle On Check (Smooth)", HydraConfig.FIX_ANGLE_ENABLED.name()));
        rows.add(RowModel.value("Fix Angle Min Pitch (band)", HydraConfig.FIX_ANGLE_MIN_PITCH.name()));
        rows.add(RowModel.value("Fix Angle Max Pitch (band)", HydraConfig.FIX_ANGLE_MAX_PITCH.name()));
        rows.add(RowModel.value("Fix Angle Re-Entry Fraction", HydraConfig.FIX_ANGLE_REENTRY_FRACTION.name()));
        rows.add(RowModel.value("Fix Angle Yaw Jitter", HydraConfig.FIX_ANGLE_YAW_JITTER.name()));

        rows.add(RowModel.section("Movement Reactions"));
        rows.add(RowModel.toggle("Movement Reactions", HydraConfig.MOVEMENT_REACTIONS_ENABLED.name()));
        rows.add(RowModel.value("Cage Reaction Ticks", HydraConfig.REACTION_CAGE_TICKS.name()));
        rows.add(RowModel.value("Barrier Reaction Ticks", HydraConfig.REACTION_BARRIER_TICKS.name()));
        rows.add(RowModel.value("Floor-Change Reaction Ticks", HydraConfig.REACTION_FLOOR_CHANGE_TICKS.name()));
        rows.add(RowModel.value("Backup Reaction Ticks", HydraConfig.REACTION_BACKUP_TICKS.name()));

        rows.add(RowModel.section("Staff Response — Re-Route"));
        rows.add(RowModel.toggle("Avoid Evacuation Re-Route", HydraConfig.AVOID_EVACUATION_ENABLED.name()));
        rows.add(RowModel.value("Avoid Evacuation Command", HydraConfig.AVOID_EVACUATION_COMMAND.name()));
        rows.add(RowModel.toggle("After-Disconnect Re-Route", HydraConfig.AFTER_DISCONNECT_ENABLED.name()));
        rows.add(RowModel.value("After-Disconnect Command", HydraConfig.AFTER_DISCONNECT_COMMAND.name()));

        rows.add(RowModel.section("Staff Response — Parity"));
        rows.add(RowModel.toggle("Parity Mode", HydraConfig.HYDRA_PARITY_MODE.name()));
        rows.add(RowModel.enumRow("Parity Policy", HydraConfig.HYDRA_ORIGINAL_POLICY.name(),
                HydraOriginalPolicy.class));
        rows.add(RowModel.toggle("Always React (Rare Checks)",
                HydraConfig.HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS.name()));
        rows.add(RowModel.toggle("Parity Bypass Cooldown", HydraConfig.PARITY_BYPASS_COOLDOWN.name()));
        rows.add(RowModel.toggle("Exact Parity Templates", HydraConfig.PARITY_TEMPLATES_ENABLED.name()));
        rows.add(RowModel.value("After Responding Ms", HydraConfig.AFTER_RESPONDING_MS.name()));
        rows.add(RowModel.info("Parity Quiet Period", "The parity temporary-disable phase waits for 600 response updates before listening again. After Responding Ms controls REACT settling and the temporary-disable phase in local mode."));

        rows.add(RowModel.section("Reply Messages"));
        rows.add(RowModel.value("First Reply Messages", HydraConfig.AUTO_REPLY_FIRST_MESSAGES.name()));
        rows.add(RowModel.value("Second Reply Messages", HydraConfig.AUTO_REPLY_SECOND_MESSAGES.name()));
        rows.add(RowModel.value("Third Reply Messages", HydraConfig.AUTO_REPLY_THIRD_MESSAGES.name()));
        rows.add(RowModel.value("Realignment Messages", HydraConfig.REALIGNMENT_MESSAGES.name()));
        rows.add(RowModel.value("Look Messages", HydraConfig.LOOK_MESSAGES.name()));
        rows.add(RowModel.value("Barrier Messages", HydraConfig.BARRIER_MESSAGES.name()));
        rows.add(RowModel.value("Floor Change Messages", HydraConfig.FLOOR_CHANGE_MESSAGES.name()));

        rows.add(RowModel.section("Reply Timing"));
        rows.add(RowModel.value("Second Reply Chance", HydraConfig.AUTO_REPLY_SECOND_CHANCE.name()));
        rows.add(RowModel.value("Third Reply Chance", HydraConfig.AUTO_REPLY_THIRD_CHANCE.name()));
        rows.add(RowModel.value("Reply Min Delay Ms", HydraConfig.AUTO_REPLY_MIN_DELAY_MS.name()));
        rows.add(RowModel.value("Reply Max Delay Ms", HydraConfig.AUTO_REPLY_MAX_DELAY_MS.name()));
        rows.add(RowModel.value("Response Cooldown Ms", HydraConfig.RESPONSE_COOLDOWN_MS.name()));
        return rows;
    }

    public CategoryContent forCategory(String category) {
        CategoryContent c = byCategory.get(category);
        if (c == null) {
            throw new IllegalArgumentException("No Settings content for category " + category);
        }
        return c;
    }

    public Map<String, CategoryContent> all() {
        return Map.copyOf(byCategory);
    }

    /** Categories in display (rail) order. */
    public List<String> categories() {
        return List.copyOf(byCategory.keySet());
    }
}
