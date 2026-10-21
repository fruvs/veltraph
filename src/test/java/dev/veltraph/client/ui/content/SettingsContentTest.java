package dev.veltraph.client.ui.content;

import dev.veltraph.client.config.DebugConfig;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.HydraConfig;
import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.log.DebugLogMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the Settings content model (no Minecraft). Every category must have content. */
class SettingsContentTest {

    private final SettingsContent content = SettingsContent.build();

    @Test
    void bazaarCustodySettingsRequireOptInAndDescribeTheDistinctCards() {
        var rows=content.forCategory("Misc").rows();
        assertFalse(dev.veltraph.client.config.SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED.defaultValue());
        assertEquals("",dev.veltraph.client.config.SellingConfig.SELLING_BAZAAR_PROFILE_JSON.defaultValue());
        for(var key:List.of(dev.veltraph.client.config.SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED,
                dev.veltraph.client.config.SellingConfig.SELLING_BAZAAR_PROFILE_JSON,
                dev.veltraph.client.config.MiscConfig.AUTO_SELL_SACKS))
            assertEquals(1,rows.stream().filter(r->key.name().equals(r.configKey())).count());
        assertTrue(rows.stream().anyMatch(r->r.kind()==RowModel.Kind.INFO&&r.actionId().contains("Bazaar Inventory Sale")
                &&r.actionId().contains("Bazaar Inventory and Sacks")&&r.actionId().contains("Start the selected card separately")));
    }

    @Test
    void observedPestParentHasExactlyTwoEditableRowsAndClosedDefaults() {
        var rows=content.forCategory("Farming").rows();
        assertEquals(1,rows.stream().filter(r->r.kind()==RowModel.Kind.TOGGLE
                &&dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_ENABLED.name().equals(r.configKey())).count());
        assertEquals(1,rows.stream().filter(r->r.kind()==RowModel.Kind.VALUE
                &&dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_BINDINGS.name().equals(r.configKey())).count());
        assertFalse(dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_ENABLED.defaultValue());
        assertEquals("",dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_BINDINGS.defaultValue());
    }

    @Test
    void sourceParentSettingsCoverEveryExplicitBindingAndExplainPermissions() {
        var rows=content.forCategory("Foraging").rows();
        assertGuiCoversHolder(rows,dev.veltraph.client.config.GalateaWorkflowConfig.class);
        assertGuiCoversHolder(rows,dev.veltraph.client.foraging.hub.HubForagingConfig.class);
        assertTrue(rows.stream().anyMatch(r->r.kind()==RowModel.Kind.INFO&&r.actionId().contains("/veltraph galatea reset")&&r.actionId().contains("preference alone grants no command permission")));
        var experiments=content.forCategory("Experiments").rows();
        assertGuiCoversHolder(experiments,dev.veltraph.client.gui.ExperimentsParentConfig.class);
        assertTrue(experiments.stream().anyMatch(r->r.kind()==RowModel.Kind.INFO&&r.actionId().contains("explicit prices")&&r.actionId().contains("XP credit mode")&&r.actionId().contains("native parent execution is pending")));
    }

    @Test
    void greenhouseControlsCoverEditableFieldsAndProtectManagedObservationsAndJournal() {
        var rows = content.forCategory("Farming").rows();
        var managed = Set.of("GREENHOUSE_OBSERVATIONS", "GREENHOUSE_UNRESOLVED_ACTIONS", "GREENHOUSE_CAPTURED_MENUS");
        assertGuiCoversHolder(rows, dev.veltraph.client.config.GreenhouseConfig.class, managed);
        assertFalse(dev.veltraph.client.config.GreenhouseConfig.GREENHOUSE_ENABLED.defaultValue());
        for (var key : List.of(dev.veltraph.client.config.GreenhouseConfig.GREENHOUSE_PROFILE,
                dev.veltraph.client.config.GreenhouseConfig.GREENHOUSE_PLOT,
                dev.veltraph.client.config.GreenhouseConfig.GREENHOUSE_PLAN,
                dev.veltraph.client.config.GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS)) {
            assertEquals("", key.defaultValue(), "No guessed profile, plan or native binding: " + key.name());
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.VALUE && key.name().equals(r.configKey())).count());
        }
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("/vgreenhouse guide / combination") && r.actionId().contains("exact proximity")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("clearing a plan never clears this journal")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Native execution and live Hypixel acceptance remain pending")));
    }

    @Test
    void chocolateControlsExposeCurrentContractAndBudgetCommandsWithoutJournalEditors() {
        var rows = content.forCategory("Misc").rows();
        var managed = Set.of("CHOCOLATE_FACTORY_PROFILES", "CHOCOLATE_FACTORY_HISTORY");
        assertEquals(4, holderKeyNames(dev.veltraph.client.config.ChocolateFactoryConfig.class).size());
        assertGuiCoversHolder(rows, dev.veltraph.client.config.ChocolateFactoryConfig.class, managed);
        assertFalse(dev.veltraph.client.config.ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED.defaultValue());
        assertEquals("", dev.veltraph.client.config.ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("<totalQueueBudget>") && r.actionId().contains("<minimumWalletReserve>") && r.actionId().contains("separate guarded action")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("freshnessMillis") && r.actionId().contains("receiptTimeoutMillis")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("UNKNOWN") && r.actionId().contains("preserves other scopes") && r.actionId().contains("Manual Chocolate debit") && r.actionId().contains("native ACKs") && r.actionId().contains("Coin value") && r.actionId().contains("never automatically rearms")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("queue-inspect <intent> <original-scope> declare-original-account") && r.actionId().contains("reconcile <intent> purchased|not-purchased <original-scope> declare-original-account") && r.actionId().contains("Account identity remains unproven") && r.actionId().contains("fresh deliberate selection")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("prestige-confirm reset-all") && r.actionId().contains("maximum only bounds the observed parent snapshot")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("action-reconcile <id>") && r.actionId().contains("not persistent account proof") && r.actionId().contains("never automatically rearms")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("III-to-IV prestige") && r.actionId().contains("upkeep payments need new exact bindings") && r.actionId().contains("not Coin valuations")));
    }

    @Test
    void observationControlsArePassiveByDefaultAndExplainCausalIdentityLimits() {
        var rows = content.forCategory("Failsafe").rows();
        var holder = dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.class;
        assertEquals(4, holderKeyNames(holder).size());
        assertGuiCoversHolder(rows, holder);
        assertTrue(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.ENABLED.defaultValue());
        assertTrue(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.NOTIFY.defaultValue());
        assertFalse(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.AUTO_STOP.defaultValue());
        assertEquals(5000L, dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("EXPLAINED") && r.actionId().contains("UNEXPECTED") && r.actionId().contains("UNCERTAIN") && r.actionId().contains("no immunity")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("do not identify a human") && r.actionId().contains("do not identify staff") && r.actionId().contains("item-component identity")));
    }

    @Test
    void sourceFailsafePoliciesExposeExactEnumsAndExplicitStoppedRouteRequirement() {
        var rows = content.forCategory("Failsafe").rows();
        for (var key : List.of(dev.veltraph.client.config.FailsafeConfig.FAILSAFE_PLAYER_POLICY,
                dev.veltraph.client.config.FailsafeConfig.FAILSAFE_MENTION_POLICY)) {
            assertEquals(dev.veltraph.client.config.FailsafeSourcePolicy.LOCAL_POLICY, key.defaultValue());
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.ENUM && key.name().equals(r.configKey()) && r.enumType() == dev.veltraph.client.config.FailsafeSourcePolicy.class).count());
        }
        assertEquals(3000L, dev.veltraph.client.config.FailsafeConfig.FAILSAFE_MENTION_MIN_DELAY_MS.defaultValue());
        assertEquals(10000L, dev.veltraph.client.config.FailsafeConfig.FAILSAFE_MENTION_MAX_DELAY_MS.defaultValue());
        assertEquals("", dev.veltraph.client.config.FailsafeConfig.FAILSAFE_SWITCH_LOBBY_COMMAND.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("only when an explicit command is configured") && r.actionId().contains("no route is guessed") && r.actionId().contains("automation remains stopped")));
    }

    @Test
    void configuredTrackerAndPhilipControlsCoverTheirPersistedContracts() {
        var rows = content.forCategory("Farming").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.PestTrackerConfig.class);
        assertGuiCoversHolder(rows, dev.veltraph.client.config.PestPhilipConfig.class);
        assertFalse(dev.veltraph.client.config.PestTrackerConfig.PEST_TRACKED_MAINTENANCE_ENABLED.defaultValue());
        assertFalse(dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_ENABLED.defaultValue());
        assertFalse(dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_MAINTENANCE_ENABLED.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestTrackerConfig.PEST_TRACKED_BINDINGS.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestPhilipConfig.PEST_PHILIP_BINDINGS.defaultValue());
    }

    @Test
    void everyCategoryHasAtLeastOneEntry() {
        for (String cat : content.categories()) {
            CategoryContent cc = content.forCategory(cat);
            assertFalse(cc.isEmpty(), "Settings category empty: " + cat);
            assertTrue(cc.entryCount() >= 1, "No rows for " + cat);
        }
    }

    @Test
    void farmingCategoryHasRealConfigRowsForBothSections() {
        List<RowModel> rows = content.forCategory("Farming").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.SECTION && r.label().equals("Farming")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.SECTION && r.label().equals("Rotation / Aim")));
        // A toggle and an enum row both bind to real FarmingConfig keys.
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && FarmingConfig.DROP_AND_TURN.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.ENUM
                && FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY.name().equals(r.configKey())));
        // No coming-soon placeholders in a real category.
        assertFalse(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.COMING_SOON));
    }

    @Test
    void scriptingCategoryExposesDebugLogToggleAndModeCycler() {
        List<RowModel> rows = content.forCategory("Scripting").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && DebugConfig.DEBUG_LOG_ENABLED.name().equals(r.configKey())));
        RowModel modeRow = rows.stream()
                .filter(r -> r.kind() == RowModel.Kind.ENUM && DebugConfig.DEBUG_LOG_MODE.name().equals(r.configKey()))
                .findFirst().orElseThrow();
        assertEquals(DebugLogMode.class, modeRow.enumType());
    }

    @Test
    void noSettingsCategoryIsAllPlaceholders() {
        // Batch 11: every Settings category is now a real editable page (Visual/Failsafe/Builder/Misc
        // were the last placeholders). A category may still carry an inspectable disabled row for an
        // EXCLUDED bit (Discord, build executor, sell executor), but it must not be ALL coming-soon.
        for (String cat : content.categories()) {
            assertFalse(content.forCategory(cat).allComingSoon(),
                    cat + " should be a real settings page, not all placeholders");
        }
    }

    @Test
    void anyRemainingDisabledRowStaysInspectable() {
        // Where a category keeps a disabled row (an excluded executor / network bit), it must still name
        // the recovered group and carry a blocker — never a bare "Coming Soon".
        for (String cat : content.categories()) {
            for (RowModel r : content.forCategory(cat).rows()) {
                if (r.kind() == RowModel.Kind.COMING_SOON) {
                    assertNotEquals("Coming Soon", r.label(), cat + " disabled row must name a real group");
                    assertFalse(r.label().isBlank(), cat + " disabled row needs a label");
                    assertTrue(r.hasBlocker(), cat + " disabled row needs a blocker: " + r.label());
                }
            }
        }
    }

    @Test
    void failsafeBuilderVisualMiscAreRealAndCoverTheirHolders() {
        assertGuiCoversHolder(content.forCategory("Combat").rows(),dev.veltraph.client.config.SlayerConfig.class);
        assertGuiCoversHolder(content.forCategory("Combat").rows(),dev.veltraph.client.combat.sven.SvenConfig.class);
        assertGuiCoversHolder(content.forCategory("Scripting").rows(),dev.veltraph.client.config.TravelConfig.class);
        assertGuiCoversHolder(content.forCategory("Scripting").rows(),dev.veltraph.client.config.RotationConfig.class);
        assertFalse(content.forCategory("Failsafe").allComingSoon());
        assertGuiCoversHolder(content.forCategory("Failsafe").rows(),
                dev.veltraph.client.config.FailsafeConfig.class);
        // Reaction policy enum carries its class on the model.
        assertTrue(content.forCategory("Failsafe").rows().stream()
                .anyMatch(r -> r.kind() == RowModel.Kind.ENUM
                        && dev.veltraph.client.config.FailsafeConfig.FAILSAFE_REACTION_POLICY.name().equals(r.configKey())
                        && r.enumType() == dev.veltraph.client.config.FailsafeReactionPolicy.class));

        assertFalse(content.forCategory("Builder").allComingSoon());
        assertGuiCoversHolder(content.forCategory("Builder").rows(),
                dev.veltraph.client.config.BuilderConfig.class);
        assertGuiCoversHolder(content.forCategory("Builder").rows(),
                dev.veltraph.client.config.GeneratedBuilderConfig.class);

        assertFalse(content.forCategory("Visual").allComingSoon());
        assertGuiCoversHolder(content.forCategory("Visual").rows(),
                dev.veltraph.client.config.NotificationConfig.class);

        assertFalse(content.forCategory("Misc").allComingSoon());
        assertGuiCoversHolder(content.forCategory("Misc").rows(),
                dev.veltraph.client.config.MiscConfig.class);
    }

    @Test
    void fishingCategoryExposesRealConfigRows() {
        List<RowModel> rows = content.forCategory("Fishing").rows();
        assertGuiCoversHolder(rows,dev.veltraph.client.fishing.setup.FishingSetupConfig.class);
        assertFalse(content.forCategory("Fishing").allComingSoon());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.ENUM
                && dev.veltraph.client.config.FishingConfig.FISHING_BITE_DETECTION.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && dev.veltraph.client.config.FishingConfig.FISHING_CATCH_HUMANIZE_DELAY.name().equals(r.configKey())));
    }

    @Test
    void foragingCategoryHasRealSectionPlusInfoRows() {
        List<RowModel> rows = content.forCategory("Foraging").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.SECTION
                && r.label().equals("Tree Chopping")), "Foraging should have a Tree Chopping section");
        // The info rows are inspectable disabled rows with blockers (numerics are JSON-editable).
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.COMING_SOON && r.hasBlocker()));
    }

    @Test
    void combatCategoryExposesAutoHealToggle() {
        List<RowModel> rows = content.forCategory("Combat").rows();
        assertFalse(content.forCategory("Combat").allComingSoon());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && dev.veltraph.client.config.GhostConfig.GHOST_AUTO_HEAL_ENABLED.name().equals(r.configKey())));
    }

    // ---- Batch 9: full ConfigKey coverage with editable controls ----

    @Test
    void hudCategoryIsRealAndExposesEveryHudKey() {
        CategoryContent cc = content.forCategory("HUD");
        assertFalse(cc.allComingSoon(), "HUD is now a real settings page");
        assertGuiCoversHolder(cc.rows(), dev.veltraph.client.config.HudConfig.class);
        // The corner cycler carries its enum class on the (pure) model.
        RowModel corner = cc.rows().stream()
                .filter(r -> r.kind() == RowModel.Kind.ENUM
                        && dev.veltraph.client.config.HudConfig.HUD_CORNER.name().equals(r.configKey()))
                .findFirst().orElseThrow();
        assertEquals(dev.veltraph.client.hud.HudCorner.class, corner.enumType());
    }

    @Test
    void farmingExposesNumericTuningValueRows() {
        List<RowModel> rows = content.forCategory("Farming").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.SECTION && r.label().equals("Aim Tuning")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.SECTION && r.label().equals("Row / Break Tuning")));
        for (String key : List.of(
                FarmingConfig.CROP_SIGHTLINE_DISTANCE.name(),
                FarmingConfig.CROP_AIM_HEIGHT_OFFSET.name(),
                FarmingConfig.ROW_TRANSITION_FORWARD_TICKS.name(),
                FarmingConfig.ATTACK_CADENCE_TICKS.name(),
                FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK.name(),
                FarmingConfig.FARMING_TOOL_SLOT.name())) {
            assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.VALUE && key.equals(r.configKey())),
                    "Farming should expose a value row for " + key);
        }
    }

    @Test
    void visitorExposesMaxCostValueRow() {
        List<RowModel> rows = content.forCategory("Farming").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.VALUE
                && dev.veltraph.client.config.VisitorConfig.VISITOR_MAX_COST.name().equals(r.configKey())));
    }

    @Test
    void developerPageExposesToggleAndTestButtons() {
        List<RowModel> rows = content.forCategory("Developer").rows();
        // the Developer Mode toggle is present and bound to the persisted key
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                        && dev.veltraph.client.config.DeveloperConfig.DEV_MODE_ENABLED.name().equals(r.configKey())),
                "Developer page must expose the Dev Mode toggle");
        // a log status info row is present
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO),
                "Developer page must expose a log status row");
        // every registered dev-test action has a button row carrying its id
        Set<String> buttonIds = new HashSet<>();
        for (RowModel r : rows) {
            if (r.kind() == RowModel.Kind.BUTTON) {
                buttonIds.add(r.actionId());
            }
        }
        assertTrue(buttonIds.size() >= 20, "expected a button per dev-test action, got " + buttonIds.size());
        for (dev.veltraph.client.dev.DevTestAction a : dev.veltraph.client.dev.DevTestActions.standard().actions()) {
            assertTrue(buttonIds.contains(a.id()), "missing dev-test button for " + a.id());
        }
    }

    @Test
    void fishingExposesEveryFishingKey() {
        assertGuiCoversHolder(content.forCategory("Fishing").rows(),
                dev.veltraph.client.config.FishingConfig.class);
        assertGuiCoversHolder(content.forCategory("Fishing").rows(),
                dev.veltraph.client.fishing.trash.FishingTrashConfig.class);
        assertGuiCoversHolder(content.forCategory("Fishing").rows(),
                dev.veltraph.client.fishing.offense.FishingOffenseConfig.class);
    }

    @Test
    void fishingSupportControlsRequireExplicitProfilesAndKeepSafeDefaults() {
        var rows = content.forCategory("Fishing").rows();
        for (var key : List.of(dev.veltraph.client.config.FishingConfig.FISHING_AUTO_POWER_ORB,
                dev.veltraph.client.config.FishingConfig.FISHING_AUTO_RAIN)) {
            assertFalse(key.defaultValue(), key.name());
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.TOGGLE && key.name().equals(r.configKey())).count(), key.name());
        }
        assertEquals("[]", dev.veltraph.client.config.FishingConfig.FISHING_ORB_PROFILES.defaultValue());
        assertEquals("{}", dev.veltraph.client.config.FishingConfig.FISHING_RAIN_PROFILE.defaultValue());
        for (var key : List.of(dev.veltraph.client.config.FishingConfig.FISHING_ORB_PROFILES,
                dev.veltraph.client.config.FishingConfig.FISHING_RAIN_PROFILE)) {
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.VALUE && key.name().equals(r.configKey())).count(), key.name());
        }
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("[] cannot enable support") && r.actionId().contains("minimum interval")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("{} cannot buy") && r.actionId().contains("Park only") && r.actionId().contains("NPC UUID")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("positive total cost cap") && r.actionId().contains("timer increase and actual rain")));
    }

    @Test
    void terminalTimerExposesOnlySupportedScheduleFieldsAndKeepsRestUnregistered() {
        var rows = content.forCategory("Misc").rows();
        var supported = Set.of(dev.veltraph.client.config.SchedulingConfig.TIMER_ENABLED.name(),
                dev.veltraph.client.config.SchedulingConfig.TIMER_DURATION.name(), dev.veltraph.client.config.SchedulingConfig.TIMER_UNIT.name());
        for (String key : holderKeyNames(dev.veltraph.client.config.SchedulingConfig.class)) {
            if(supported.contains(key)) assertEquals(1, rows.stream().filter(r -> key.equals(r.configKey())).count(),key);
            else assertFalse(content.categories().stream().flatMap(c -> content.forCategory(c).rows().stream()).anyMatch(r -> key.equals(r.configKey())),key);
        }
        assertFalse(dev.veltraph.client.config.SchedulingConfig.TIMER_ENABLED.defaultValue());
        assertEquals(1.0, dev.veltraph.client.config.SchedulingConfig.TIMER_DURATION.defaultValue());
        assertEquals(dev.veltraph.client.scheduling.ScheduleTimeUnit.HOURS, dev.veltraph.client.config.SchedulingConfig.TIMER_UNIT.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.ENUM && r.enumType() == dev.veltraph.client.scheduling.ScheduleTimeUnit.class));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().equals("timerStatus")));
        assertEquals(Set.of("SECONDS","MINUTES","HOURS","DAYS"), java.util.Arrays.stream(dev.veltraph.client.scheduling.ScheduleTimeUnit.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Farming/Combat/Foraging/Mining/Fishing/Hunting") && r.actionId().contains("Passive observers")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("do not rearm") && r.actionId().contains("manual stop, death and world change")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Only terminal TIMER") && r.actionId().contains("unsupported enabled rest fails readiness")));
        assertEquals("SCHEDULED_REST_MINING", dev.veltraph.client.config.SchedulingConfig.AUTO_BREAK_MINING.name());
        assertNotEquals(dev.veltraph.client.config.MiningConfig.AUTO_BREAK_MINING.name(), dev.veltraph.client.config.SchedulingConfig.AUTO_BREAK_MINING.name());
    }

    @Test
    void plotCleanerControlsRequireTypedInactivePreparationAndExplicitMiningCounterpart() {
        var rows = content.forCategory("Builder").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.PlotCleanerConfig.class);
        assertEquals(17, holderKeyNames(dev.veltraph.client.config.PlotCleanerConfig.class).size());
        for (var key : List.of(dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_ENABLED,
                dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_VANILLA_ROWS,
                dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_BINDINGS_VERIFIED)) assertFalse(key.defaultValue(),key.name());
        assertEquals("", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_DESK_COMMAND.defaultValue());
        assertEquals("", dev.veltraph.client.config.PlotCleanerConfig.PLOT_CLEANER_CONFIRM_READY_LORE.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("/vplotcleaner prepare <plot> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>") && r.actionId().contains("no operation starts")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("inactive only") && r.actionId().contains("start uses the shared guard")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("ordinary guarded dirt/grass mining") && r.actionId().contains("native bulk-row ability and live preset are unrecovered")));
    }

    @Test
    void alchemyFlowControlsKeepExplicitSeparateLayoutAndResourceContract() {
        var rows = content.forCategory("Skills").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.AlchemyFlowConfig.class);
        assertFalse(dev.veltraph.client.config.AlchemyFlowConfig.ALCHEMY_FLOW_ENABLED.defaultValue());
        assertEquals("", dev.veltraph.client.config.AlchemyFlowConfig.ALCHEMY_FLOW_PROFILE.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("blank profile cannot start") && r.actionId().contains("batch/spending/time limits")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("standard brewing stand") && r.actionId().contains("ingredient slot 13")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Reopened stand contents") && r.actionId().contains("exact receipts")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("terminalSale") && r.actionId().contains("the chain collects only")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("observed prices") && r.actionId().contains("Unknown details block starting")));
    }

    @Test
    void grappleControlsKeepExactProfileAndNativeOwnedTopologyRequirements() {
        var rows = content.forCategory("Fishing").rows();
        assertFalse(dev.veltraph.client.config.FishingConfig.FISHING_GRAPPLESHOT_BLAZES.defaultValue());
        assertEquals("{}", dev.veltraph.client.config.FishingConfig.FISHING_GRAPPLE_PROFILE.defaultValue());
        assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.TOGGLE && dev.veltraph.client.config.FishingConfig.FISHING_GRAPPLESHOT_BLAZES.name().equals(r.configKey())).count());
        assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.VALUE && dev.veltraph.client.config.FishingConfig.FISHING_GRAPPLE_PROFILE.name().equals(r.configKey())).count());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Crimson Isle/native Blaze only") && r.actionId().contains("{} cannot act")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("14-block") && r.actionId().contains("5/2-sample") && r.actionId().contains("3 degrees")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Bat riding ArmorStand") && r.actionId().contains("Bat leashed to owned player") && r.actionId().contains("tether alone cannot complete")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("TNT route belongs to Sven travel")));
        assertFalse(rows.stream().anyMatch(r -> r.hasBlocker() && r.blocker().contains("grapple-blaze workflows remain incomplete")));
    }

    @Test
    void privateIslandReplantSettingsExposeActualAnchorAndSourceDefaultsWithoutInventedProfiles() {
        var rows = content.forCategory("Foraging").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.ForagingReplantConfig.class);
        assertEquals("", dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_ANCHOR.defaultValue());
        assertEquals(0, dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_CYCLES.defaultValue());
        assertEquals(2, dev.veltraph.client.config.ForagingReplantConfig.ISLAND_REPLANT_MAX_BONEMEAL_USES.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Starts only through") && r.actionId().contains("blank default anchor blocks start") && r.actionId().contains("2x2 footprint")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("source attempt limit 2") && r.actionId().contains("stochastic vanilla growth")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Side-chest auto-fill, Enchanted Bonemeal and full Galatea replanting remain unverified")));
    }

    @Test
    void abiphoneSettingsRequireObservedContractTimingAndExplicitLeaseRecovery() {
        var rows = content.forCategory("Misc").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.AbiphoneConfig.class);
        assertFalse(dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_ENABLED.defaultValue());
        assertFalse(dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_RETAIN_DIALOGUE.defaultValue());
        assertEquals("", dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_CONTRACT.defaultValue());
        assertEquals(-1, dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_DELAY_TICKS.defaultValue());
        assertEquals(-1, dev.veltraph.client.config.AbiphoneConfig.ABIPHONE_CALL_TIMEOUT_TICKS.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("blank contract and -1 timings cannot start") && r.actionId().contains("1,200 ticks")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Main-slot restoration") && r.actionId().contains("received post-SWAP revision") && r.actionId().contains("RECOVERY_REQUIRED")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("explicit caller release") && r.actionId().contains("without completing or retrying the call")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("unrecovered command-call prefix") && r.actionId().contains("global healing/Hydra")));
        assertFalse(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.BUTTON && (r.label().contains("Abiphone") || r.label().contains("Manual Recovery"))));
    }

    @Test
    void visitorCycleControlsKeepOptInsAndAggregateBoundsExplicit() {
        var rows = content.forCategory("Farming").rows();
        for (var key : List.of(dev.veltraph.client.config.VisitorConfig.VISITOR_MULTI_SERVE,
                dev.veltraph.client.config.VisitorConfig.VISITOR_LOADOUT_SWAP)) {
            assertFalse(key.defaultValue(), key.name());
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.TOGGLE && key.name().equals(r.configKey())).count(), key.name());
        }
        for (var key : List.of(dev.veltraph.client.config.VisitorConfig.VISITOR_NPC_NAMES,
                dev.veltraph.client.config.VisitorConfig.VISITOR_MAX_VISITORS,
                dev.veltraph.client.config.VisitorConfig.VISITOR_CYCLE_MAX_COST,
                dev.veltraph.client.config.VisitorConfig.VISITOR_ORDER_ORIGIN,
                dev.veltraph.client.config.VisitorConfig.VISITOR_LOADOUT_NAME)) {
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.VALUE && key.name().equals(r.configKey())).count(), key.name());
        }
        assertEquals("", dev.veltraph.client.config.VisitorConfig.VISITOR_NPC_NAMES.defaultValue());
        assertEquals("", dev.veltraph.client.config.VisitorConfig.VISITOR_ORDER_ORIGIN.defaultValue());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.ENUM
                && dev.veltraph.client.config.VisitorConfig.VISITOR_ORDER.name().equals(r.configKey())
                && r.enumType() == dev.veltraph.client.garden.visitor.SortOrder.class));
        assertEquals(Set.of("ASCENDING", "DESCENDING", "RANDOM"), java.util.Arrays.stream(dev.veltraph.client.garden.visitor.SortOrder.values())
                .map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("positive cycle/offer caps") && r.actionId().contains("helper start pose")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Swap defaults off") && r.actionId().contains("both visitor and current farming equipment")));
    }

    @Test
    void pestMaintenanceExposesAllSupportedKeysWithoutPretendingStoredFlagsExecute() {
        var rows = content.forCategory("Farming").rows();
        var unsupported = Set.of(dev.veltraph.client.config.PestLoadoutConfig.PEST_SWAP_EARLY.name(),
                dev.veltraph.client.config.PestLoadoutConfig.PESTS_SPRAY_DURING_FARMING.name());
        // These are internal observed-protocol prerequisites, not validated live user controls.
        var internalPrerequisites = Set.of(dev.veltraph.client.config.PestLoadoutConfig.PEST_EARLY_OBSERVED_BINDINGS.name(),
                dev.veltraph.client.config.PestLoadoutConfig.PEST_RETAINED_LOADOUT_CONTRACTS.name());
        for (var key : holderKeyNames(dev.veltraph.client.config.PestLoadoutConfig.class)) {
            if (unsupported.contains(key) || internalPrerequisites.contains(key))
                assertFalse(rows.stream().anyMatch(r -> key.equals(r.configKey())), key);
            else assertEquals(1, rows.stream().filter(r -> key.equals(r.configKey())
                    && (r.kind() == RowModel.Kind.VALUE || r.kind() == RowModel.Kind.TOGGLE)).count(), key);
        }
        assertFalse(dev.veltraph.client.config.PestLoadoutConfig.PEST_MAINTENANCE_ENABLED.defaultValue());
        assertFalse(dev.veltraph.client.config.PestLoadoutConfig.PEST_LOADOUT_SWAP.defaultValue());
        assertFalse(dev.veltraph.client.config.PestLoadoutConfig.PESTS_USE_BOX_2.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestLoadoutConfig.PEST_NAMED_LOADOUT_BINDINGS.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestLoadoutConfig.PEST_BOX_BINDINGS.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestLoadoutConfig.PEST_EARLY_OBSERVED_BINDINGS.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestLoadoutConfig.PEST_RETAINED_LOADOUT_CONTRACTS.defaultValue());
        // Verify real empty-prerequisite rejection, not merely the number of rows rendered.
        assertThrows(IllegalArgumentException.class, () -> dev.veltraph.client.garden.pests.PestEarlyLoadoutBindings.read(
                dev.veltraph.client.config.PestLoadoutConfig.PEST_EARLY_OBSERVED_BINDINGS.defaultValue()));
        assertThrows(IllegalArgumentException.class, () -> dev.veltraph.client.garden.pests.PestBindings.read(
                dev.veltraph.client.config.PestLoadoutConfig.PEST_RETAINED_LOADOUT_CONTRACTS.defaultValue(), java.util.Map.class));
        var availability = rows.stream().filter(r -> r.kind() == RowModel.Kind.INFO
                && r.label().equals("Early Loadout Availability")).toList();
        assertEquals(1, availability.size());
        assertNull(availability.getFirst().configKey());
        assertTrue(availability.getFirst().actionId().contains("not available in live play yet"));
        assertTrue(availability.getFirst().actionId().contains("configured farming and pest loadouts"));
        assertTrue(availability.getFirst().actionId().contains("Empty bindings cannot start a swap"));
        assertTrue(availability.getFirst().actionId().contains("current equipment is kept"));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("same pose") && r.actionId().contains("occupied then empty")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("full armor/offhand") && r.actionId().contains("fresh receipt")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.label().contains("Early Swap")
                && r.actionId().contains("remain unvalidated")
                && r.actionId().contains("automatic spraying while farming remains unsupported")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.label().equals("Full Pest Tracker Travel") && r.actionId().contains("remain incomplete")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("local delay 2-30 ticks and spawn wait 100-30,000 ms")));
    }

    @Test
    void newLocalWorkflowSettingsCoverTheirActualConfigHolders() {
        assertGuiCoversHolder(content.forCategory("Foraging").rows(), dev.veltraph.client.config.GalateaConfig.class);
        assertGuiCoversHolder(content.forCategory("Mining").rows(), dev.veltraph.client.config.MiningConfig.class);
        assertGuiCoversHolder(content.forCategory("Shards").rows(), dev.veltraph.client.config.ShardConfig.class);
        assertGuiCoversHolder(content.forCategory("Shards").rows(), dev.veltraph.client.config.HuntingConfig.class);
        assertGuiCoversHolder(content.forCategory("Experiments").rows(), dev.veltraph.client.config.ExperimentsConfig.class);
        assertGuiCoversHolder(content.forCategory("Experiments").rows(), dev.veltraph.client.gui.ExperimentsGuardianConfig.class);
        assertGuiCoversHolder(content.forCategory("Misc").rows(), dev.veltraph.client.config.InventoryUtilityConfig.class);
        assertGuiCoversHolder(content.forCategory("Combat").rows(), dev.veltraph.client.config.ZealotConfig.class);
        assertGuiCoversHolder(content.forCategory("Farming").rows(), dev.veltraph.client.config.PestConfig.class);
        assertGuiCoversHolder(content.forCategory("Farming").rows(), dev.veltraph.client.config.VisitorConfig.class);
        assertGuiCoversHolder(content.forCategory("Combat").rows(), dev.veltraph.client.config.QuiverConfig.class);
        assertGuiCoversHolder(content.forCategory("Combat").rows(), dev.veltraph.client.combat.quiver.QuiverIngressConfig.class);
        assertGuiCoversHolder(content.forCategory("Misc").rows(), dev.veltraph.client.config.EquipmentConfig.class);
        assertGuiCoversHolder(content.forCategory("Skills").rows(), dev.veltraph.client.config.SkillAutomationConfig.class);
        assertGuiCoversHolder(content.forCategory("Misc").rows(), dev.veltraph.client.config.SellingConfig.class);
        assertGuiCoversHolder(content.forCategory("Misc").rows(), dev.veltraph.client.config.AutoSellMaintenanceConfig.class);
        assertGuiCoversHolder(content.forCategory("Misc").rows(), dev.veltraph.client.config.AbiphoneConfig.class);
        assertGuiCoversHolder(content.forCategory("Foraging").rows(), dev.veltraph.client.config.ForagingReplantConfig.class);
        assertGuiCoversHolder(content.forCategory("Foraging").rows(), dev.veltraph.client.config.AgathaConfig.class);
        assertGuiCoversHolder(content.forCategory("Skills").rows(), dev.veltraph.client.config.CarpentryFlowConfig.class);
        assertGuiCoversHolder(content.forCategory("Skills").rows(), dev.veltraph.client.config.BottleFillConfig.class);
        assertGuiCoversHolder(content.forCategory("Builder").rows(), dev.veltraph.client.config.GardenCopyConfig.class);
    }

    @Test
    void farmingDesignSelectorsAndLegacyFallbackAreClearlyBound() {
        var rows = content.forCategory("Farming").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.ENUM
                && FarmingConfig.MUSHROOM_FARM_DESIGN.name().equals(r.configKey())
                && r.enumType() == dev.veltraph.client.config.MushroomFarmDesign.class));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.ENUM
                && FarmingConfig.PUMPKIN_FARM_DESIGN.name().equals(r.configKey())
                && r.enumType() == dev.veltraph.client.config.PumpkinFarmDesign.class));
        assertTrue(rows.stream().anyMatch(r -> FarmingConfig.ROW_TRANSITION_FORWARD_TICKS.name().equals(r.configKey())
                && r.label().contains("Legacy") && r.label().contains("Synthetic")));
    }

    @Test
    void mushroomLegacyStartPreferenceIsHiddenWhileGeometryRuleAndPersistenceRemain() {
        var rows = content.forCategory("Farming").rows();
        assertFalse(rows.stream().anyMatch(r -> FarmingConfig.MUSHROOM_RING_START_FORWARD.name().equals(r.configKey())));
        assertTrue(dev.veltraph.client.config.PersistableKeys.all().stream().anyMatch(k -> k.name().equals(FarmingConfig.MUSHROOM_RING_START_FORWARD.name())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("W on non-trapdoor lane") && r.actionId().contains("S on trapdoor lane")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Unknown or ambiguous geometry pauses") && r.actionId().contains("verified corners continue")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("yaw -26.57 deg / pitch 0") && r.actionId().contains("supported east trapdoor entry")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("24 lower + 21 upper corners") && r.actionId().contains("closed return") && r.actionId().contains("Other rotations remain unverified")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Test-only server Auto-pickup") && r.actionId().contains("2,240 delivered") && r.actionId().contains("excess world loot")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("suppresses optional jitter only") && r.actionId().contains("preferences stay unchanged")));
    }

    @Test
    void capturedGourdAcceptanceStaysDistinctFromAcceptedRegularModelAndCustomPitch() {
        var rows = content.forCategory("Farming").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                && r.actionId().contains("all 3,350 original crops across 19 bands") && r.actionId().contains("private capture")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                && r.actionId().contains("16 facing cases") && r.actionId().contains("actual saved-world reset/fresh natural harvest")
                && r.actionId().contains("stock default, complete pickup and MelonKingDE remain separate")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                && r.actionId().contains("Non-captured pumpkin target: MelonKingDE")
                && r.actionId().contains("3,445 original crops") && r.actionId().contains("zero jumps")
                && r.actionId().contains("Full author coordinate asset") && r.actionId().contains("pending")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                && r.actionId().contains("saved 26 deg must be adjusted manually")));
        assertFalse(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                && r.actionId().contains("diagonal wall-contact correction in progress")));
    }

    @Test
    void requiredUnknownShardIdentitiesRemainEditableWithoutInventedDefaults() {
        var rows = content.forCategory("Shards").rows();
        for (var key : List.of(dev.veltraph.client.config.ShardConfig.SHARD_CATALOG,
                dev.veltraph.client.config.ShardConfig.SHARD_MENU_TITLE,
                dev.veltraph.client.config.ShardConfig.SHARD_OPEN_COMMAND,
                dev.veltraph.client.config.ShardConfig.SHARD_MASS_BUTTON,
                dev.veltraph.client.config.ShardConfig.SHARD_PRICE_REGEX)) {
            assertTrue(key.defaultValue().isBlank(), key.name());
            assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.VALUE && key.name().equals(r.configKey())), key.name());
        }
    }

    @Test
    void opaqueWorkflowContractsKeepBlankDefaultsAndClearSetupGuidance() {
        for (var key : List.of(dev.veltraph.client.config.QuiverConfig.QUIVER_MENU_TITLE,
                dev.veltraph.client.config.QuiverConfig.QUIVER_AMMO_ID,
                dev.veltraph.client.config.EquipmentConfig.EQUIPMENT_MENU_TITLE,
                dev.veltraph.client.config.EquipmentConfig.EQUIPMENT_ACTION_ITEM_ID,
                dev.veltraph.client.config.SkillAutomationConfig.ALCHEMY_INGREDIENT_ID,
                dev.veltraph.client.config.SkillAutomationConfig.CARPENTRY_MENU_TITLE,
                dev.veltraph.client.config.SkillAutomationConfig.BOTTLE_ITEM_ID,
                dev.veltraph.client.config.SkillAutomationConfig.FAIRY_LOCAL_CATALOG)) {
            assertTrue(key.defaultValue().isBlank(), key.name());
            assertTrue(content.categories().stream().flatMap(c -> content.forCategory(c).rows().stream())
                    .anyMatch(r -> r.kind() == RowModel.Kind.VALUE && key.name().equals(r.configKey())), key.name());
        }
        assertEquals("{}", dev.veltraph.client.config.FishingConfig.FISHING_BAIT_PROFILE.defaultValue());
        assertTrue(content.forCategory("Skills").rows().stream()
                .anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("container shortcut")));
    }

    @Test
    void observedAbilityControlsDoNotAuthorizeThrowingThroughLegacyToken() {
        var rows = content.forCategory("Foraging").rows();
        assertTrue(rows.stream().anyMatch(r -> dev.veltraph.client.config.ForagingConfig.FORAGING_REQUIRE_SWEEP.name().equals(r.configKey()) && r.kind() == RowModel.Kind.TOGGLE));
        assertTrue(rows.stream().anyMatch(r -> dev.veltraph.client.config.ForagingConfig.FORAGING_ABILITY_RESULT_WINDOW_MS.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> dev.veltraph.client.config.GalateaConfig.GALATEA_THROW_ABILITY_TOKEN.name().equals(r.configKey()) && r.label().contains("ignored")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("observed ability policy")));
        assertTrue(rows.stream().anyMatch(r -> dev.veltraph.client.config.GalateaConfig.GALATEA_USE_COOLDOWN_MS.name().equals(r.configKey()) && r.label().startsWith("Material Click")));
    }

    @Test
    void acceptedPestAndSellingSetupDoesNotRemainLabeledAwaitingAcceptance() {
        for (String category : List.of("Farming", "Misc")) {
            for (var row : content.forCategory(category).rows()) {
                assertFalse(row.label().contains("acceptance pending"), row.label());
                if (row.kind() == RowModel.Kind.INFO) assertFalse(row.actionId().contains("await acceptance"), row.actionId());
            }
        }
        assertEquals("", dev.veltraph.client.config.PestConfig.PEST_TRAP_BINDINGS.defaultValue());
        assertEquals("", dev.veltraph.client.config.PestConfig.PEST_SUPPLY_BINDINGS.defaultValue());
        assertEquals("[]", dev.veltraph.client.config.SellingConfig.SELLING_RULES_JSON.defaultValue());
        assertFalse(dev.veltraph.client.config.SellingConfig.SELLING_TRASH_ENABLED.defaultValue());
    }

    @Test
    void sellingInspectorGuidanceDescribesReadOnlyOutputWithoutADeadActionButton() {
        var rows = content.forCategory("Misc").rows();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().equals("Read-only inspector: /vdebug selling")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("unit value and quote remain unset")));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("no automatic clipboard write")));
        assertFalse(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.BUTTON && r.label().contains("Selling Inspector")));
        assertTrue(content.forCategory("Farming").rows().stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("Captured modified-default") && r.actionId().contains("diagonal wall contact")));
        assertTrue(content.forCategory("Farming").rows().stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO && r.actionId().contains("default 40") && r.actionId().contains("saved 26") && r.actionId().contains("manually")));
    }

    @Test
    void editableControlsRetainTheirConfigTypesAndAreNotDuplicatedWithinACategory() {
        var keys = new java.util.HashMap<String, ConfigKey<?>>();
        dev.veltraph.client.config.PersistableKeys.all().forEach(k -> keys.put(k.name(), k));
        for (String category : content.categories()) {
            var seen = new HashSet<String>();
            for (var row : content.forCategory(category).rows()) {
                if (row.configKey() == null) continue;
                assertTrue(seen.add(row.configKey()), "Duplicate control: " + category + "/" + row.configKey());
                var key = keys.get(row.configKey());
                assertTrue(key != null, "Unregistered control: " + row.configKey());
                if (row.kind() == RowModel.Kind.TOGGLE) assertTrue(key.defaultValue() instanceof Boolean, row.label());
                if (row.kind() == RowModel.Kind.ENUM) assertEquals(key.defaultValue().getClass(), row.enumType(), row.label());
            }
        }
    }

    @Test
    void dedicatedGhostControlsCoverDestinationAndHelperSettingsWithoutEnablingThem() {
        var rows = content.forCategory("Combat").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.GhostGrindConfig.class);
        var destination = dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_DESTINATION;
        assertEquals(dev.veltraph.client.combat.ghost.GhostSourceDestination.MAIN_AREA, destination.defaultValue());
        assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.ENUM
                && destination.name().equals(r.configKey())
                && r.enumType() == dev.veltraph.client.combat.ghost.GhostSourceDestination.class).count());
        for (var key : java.util.List.of(
                dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_MODIFY_JUMP_BOOST,
                dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY,
                dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_ACTIVATE_VISITOR_INSTANTLY)) {
            assertFalse(key.defaultValue());
            assertEquals(1, rows.stream().filter(r -> r.kind() == RowModel.Kind.TOGGLE
                    && key.name().equals(r.configKey())).count());
        }
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                && r.actionId().contains("Choose local routes or destination walking")
                && r.actionId().contains("requires an explicit avoided-cell list; []")
                && r.actionId().contains("wait until the required helper can run and return control")
                && r.actionId().contains("configured route names and search radius")));
    }

    @Test
    void combatExposesEveryGhostKey() {
        assertGuiCoversHolder(content.forCategory("Combat").rows(),
                dev.veltraph.client.config.GhostConfig.class);
    }

    @Test
    void foragingExposesEveryForagingKey() {
        List<RowModel> rows = content.forCategory("Foraging").rows();
        assertGuiCoversHolder(rows, dev.veltraph.client.config.ForagingConfig.class);
        // The pathfinding-excluded finder stays an inspectable disabled row.
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.COMING_SOON && r.hasBlocker()));
    }

    @Test
    void everyEditableRowKeyResolvesToARealConfigKey() {
        // Any TOGGLE/ENUM/VALUE row must reference a real persisted ConfigKey, so the screen (which binds
        // by PersistableKeys) can always resolve it — no row can point at a non-existent/unregistered key.
        Set<String> persisted = new HashSet<>();
        for (ConfigKey<?> k : dev.veltraph.client.config.PersistableKeys.all()) {
            persisted.add(k.name());
        }
        for (String cat : content.categories()) {
            for (RowModel r : content.forCategory(cat).rows()) {
                if (r.configKey() != null) {
                    assertTrue(persisted.contains(r.configKey()),
                            "Row key not in PersistableKeys (screen can't bind it): " + cat + "/" + r.configKey());
                }
            }
        }
    }

    /** Assert every {@code ConfigKey} declared on {@code holder} has an editable GUI row in {@code rows}. */
    private static void assertGuiCoversHolder(List<RowModel> rows, Class<?> holder) {
        assertGuiCoversHolder(rows, holder, Set.of());
    }

    /** Explicitly partition persisted settings and safety data; managed keys must have only read-only explanations. */
    private static void assertGuiCoversHolder(List<RowModel> rows, Class<?> holder, Set<String> managed) {
        Set<String> guiKeys = new HashSet<>();
        for (RowModel r : rows) {
            if (r.configKey() != null) {
                assertTrue(r.kind() == RowModel.Kind.TOGGLE || r.kind() == RowModel.Kind.ENUM
                                || r.kind() == RowModel.Kind.VALUE,
                        "Config row must be editable in GUI: " + r);
                guiKeys.add(r.configKey());
            }
        }
        assertTrue(holderKeyNames(holder).containsAll(managed), "Managed keys must belong to the tested holder");
        for (String key : holderKeyNames(holder)) {
            if (managed.contains(key)) {
                assertFalse(guiKeys.contains(key), "Safety data must have no editable GUI row: " + key);
                assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.INFO
                        && r.actionId().contains(key) && r.actionId().contains("System-managed")
                        && r.actionId().contains("read-only")), "Missing managed-data explanation: " + key);
            } else {
                assertTrue(guiKeys.contains(key), "Missing GUI row for " + holder.getSimpleName() + "." + key);
            }
        }
    }

    private static Set<String> holderKeyNames(Class<?> holder) {
        Set<String> names = new HashSet<>();
        for (Field f : holder.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && ConfigKey.class.isAssignableFrom(f.getType())) {
                try {
                    names.add(((ConfigKey<?>) f.get(null)).name());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return names;
    }

    @Test
    void antiStaffCategoryExposesRealDefensiveToggles() {
        List<RowModel> rows = content.forCategory("Anti-Staff").rows();
        // Master watcher + the four extra defensive detection triggers all bind to live HydraConfig keys.
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && HydraConfig.STAFF_ALERT_ENABLED.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && HydraConfig.DETECT_LAGBACK.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && HydraConfig.DETECT_FORCED_ADVENTURE.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && HydraConfig.DETECT_ROUTING_TEXT.name().equals(r.configKey())));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.TOGGLE
                && HydraConfig.DETECT_FLOOR_CHANGE.name().equals(r.configKey())));
        // It is NOT all coming-soon (it is a real page now), and it has section headers.
        assertFalse(content.forCategory("Anti-Staff").allComingSoon());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.SECTION
                && r.label().equals("Detection Triggers")));
    }

    @Test
    void antiStaffCategoryExposesEveryHydraConfigKeyInGui() {
        List<RowModel> rows = content.forCategory("Anti-Staff").rows();
        Set<String> guiKeys = new HashSet<>();
        for (RowModel row : rows) {
            if (row.configKey() != null) {
                guiKeys.add(row.configKey());
                assertTrue(row.kind() == RowModel.Kind.TOGGLE
                                || row.kind() == RowModel.Kind.ENUM
                                || row.kind() == RowModel.Kind.VALUE,
                        "Hydra config row must be editable in GUI: " + row);
            }
        }
        for (String key : hydraConfigKeyNames()) {
            assertTrue(guiKeys.contains(key), "Missing Hydra GUI row for " + key);
        }
        assertFalse(rows.stream().anyMatch(r -> r.kind() == RowModel.Kind.COMING_SOON),
                "Hydra/Anti-Staff tab should not use config.json-only placeholder rows");
    }

    private static Set<String> hydraConfigKeyNames() {
        Set<String> names = new HashSet<>();
        for (Field f : HydraConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && ConfigKey.class.isAssignableFrom(f.getType())) {
                try {
                    names.add(((ConfigKey<?>) f.get(null)).name());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return names;
    }
}
