package dev.veltraph.client.ui.content;

import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for the Automations content model (no Minecraft). Asserts UI content parity: every
 * non-INTERNAL category has content, farming cards expose layout/area/tool/state, and the recovered
 * card-labelling rules hold (Pumpkin/Melon combined; Sugarcane/Flowers grouped; Wart/Crops lists the
 * 4 auto-detected crops without a behavior-changing crop selector).
 */
class AutomationsContentTest {

    private static final Set<String> CROP_MODULE_IDS = Set.of(
            dev.veltraph.client.farming.module.WartCropsFarmModule.ID,
            dev.veltraph.client.farming.module.MushroomFarmModule.ID,
            dev.veltraph.client.farming.module.PumpkinMelonFarmModule.ID,
            dev.veltraph.client.farming.module.SugarcaneFlowersFarmModule.ID,
            dev.veltraph.client.farming.module.CocoaFarmModule.ID,
            dev.veltraph.client.farming.module.CactusFarmModule.ID);

    private final AutomationsContent content = AutomationsContent.build(FarmLayoutRegistry.createDefault());

    private CardModel card(ModuleCategory cat, String title) {
        for (CardModel c : content.forCategory(cat).cards()) {
            if (c.title().equals(title)) {
                return c;
            }
        }
        throw new AssertionError("No card titled '" + title + "' in " + cat);
    }

    @Test
    void custodyAndLegacyBazaarCardsKeepSeparateStartContracts() {
        var legacy=card(ModuleCategory.MISC,"Bazaar Inventory Sale");
        var custody=card(ModuleCategory.MISC,"Bazaar Inventory and Sacks");
        assertNotEquals(legacy.moduleId(),custody.moduleId());
        assertEquals("auto_sell_bazaar_custody",custody.moduleId());
        assertTrue(custody.enabled());
        assertTrue(custody.detail().requirements().stream().anyMatch(s->s.contains("Off by default")));
        assertTrue(custody.detail().requirements().stream().anyMatch(s->s.contains("Empty Sacks")&&s.contains("profile")));
        assertTrue(custody.detail().requirements().stream().anyMatch(s->s.contains("missing results")&&s.contains("without retry")));
        assertTrue(legacy.detail().requirements().stream().anyMatch(s->s.contains("Open Bazaar")&&s.contains("disable sacks")));
    }

    @Test
    void galateaSourceHelpersHaveExplicitResetAndGuardedParentCards() {
        var cards=AutomationsContent.build(FarmLayoutRegistry.createDefault()).forCategory(ModuleCategory.FORAGING).cards();
        var reset=cards.stream().filter(c->"galatea_source_reset".equals(c.moduleId())).findFirst().orElseThrow();
        var murk=cards.stream().filter(c->"galatea_murkwater".equals(c.moduleId())).findFirst().orElseThrow();
        assertTrue(reset.enabled());assertTrue(murk.enabled());
        assertTrue(reset.detail().requirements().stream().anyMatch(line->line.contains("explicit reset request")));
        assertTrue(murk.detail().requirements().stream().anyMatch(line->line.contains("missing receipts")));
    }

    @Test
    void everyNonInternalCategoryHasAtLeastOneEntry() {
        int seen = 0;
        for (ModuleCategory cat : ModuleCategory.values()) {
            if (cat == ModuleCategory.INTERNAL) {
                continue;
            }
            CategoryContent cc = content.forCategory(cat);
            assertFalse(cc.isEmpty(), "Category empty: " + cat);
            assertTrue(cc.entryCount() >= 1, "No entries for " + cat);
            seen++;
        }
        assertEquals(seen, content.categories().size());
        assertFalse(content.categories().contains(ModuleCategory.INTERNAL));
    }

    @Test
    void farmingCategoryHasSixCropControllersAndConfiguredGardenTransactions() {
        List<CardModel> cards = content.forCategory(ModuleCategory.FARMING).cards();
        // The 6 source-backed crop controllers are real, module-backed, clickable cards.
        long real = cards.stream().filter(CardModel::enabled).count();
        assertEquals(19, real, "Expected existing Garden workflows, contest trap maintenance and Greenhouse care/combinations");
        for (String id : List.of("garden_pest_tracker_cycle", "garden_pest_philip", "garden_pest_philip_cycle", "garden_pest_trap_cycle")) {
            assertEquals(1, cards.stream().filter(CardModel::enabled).filter(c -> id.equals(c.moduleId())).count(),
                    "Garden workflow must have one selectable card: " + id);
        }
        assertEquals(CROP_MODULE_IDS, cards.stream().filter(CardModel::enabled).map(CardModel::moduleId)
                .filter(CROP_MODULE_IDS::contains).collect(java.util.stream.Collectors.toSet()));
        assertEquals(6, cards.stream().filter(CardModel::enabled).filter(c -> CROP_MODULE_IDS.contains(c.moduleId())).count());
        for (CardModel c : cards) {
            if (c.enabled()) {
                assertNotNull(c.moduleId(), "Farming card needs a module id: " + c.title());
                assertFalse(c.comingSoon());
            }
        }
        // Spot-check the 6 expected controller titles survive (regression).
        for (String title : List.of("Wart/Crops", "Mushroom", "Pumpkin/Melon",
                "Sugarcane/Flowers", "Cocoa", "Cactus")) {
            assertTrue(card(ModuleCategory.FARMING, title).enabled(), title + " should be a real card");
        }
    }

    @Test
    void greenhouseCardIsSelectableInFarmingWithExactObservationAndNoRetryRequirements() {
        var greenhouse = card(ModuleCategory.FARMING, "Greenhouse Care / Combinations");
        assertTrue(greenhouse.enabled());
        assertEquals("greenhouse", greenhouse.moduleId());
        assertTrue(greenhouse.detail().requirements().stream().anyMatch(s -> s.contains("Actions default off") && s.contains("deadlines")));
        assertTrue(greenhouse.detail().requirements().stream().anyMatch(s -> s.contains("/vgreenhouse") && s.contains("exact proximity")));
        assertTrue(greenhouse.detail().requirements().stream().anyMatch(s -> s.contains("check then explicit start") && s.contains("shared activation guard")));
        assertTrue(greenhouse.detail().requirements().stream().anyMatch(s -> s.contains("unresolved journals are system-managed") && s.contains("no automatic retry")));
        assertTrue(greenhouse.detail().requirements().stream().anyMatch(s -> s.contains("Native execution and live Hypixel acceptance remain pending")));
        assertTrue(content.forCategory(ModuleCategory.INTERMEDIARY).cards().stream().noneMatch(c -> "greenhouse".equals(c.moduleId())), "The intermediary module has one Farming display card");
    }

    @Test
    void chocolateCardDescribesPlannerAndDeliberatelySelectedQueueWithPendingNativeProof() {
        var factory = card(ModuleCategory.MISC, "Chocolate Factory Planner / Selected Queue");
        assertTrue(factory.enabled());
        assertEquals("chocolate_factory", factory.moduleId());
        assertTrue(factory.detail().requirements().stream().anyMatch(s -> s.contains("Actions default off") && s.contains("Factory tools")));
        assertTrue(factory.detail().requirements().stream().anyMatch(s -> s.contains("/vchocolate select") && s.contains("<totalQueueBudget>") && s.contains("<minimumWalletReserve>")));
        assertTrue(factory.detail().requirements().stream().anyMatch(s -> s.contains("Only EMPLOYEE, PRODUCTION and CAPACITY") && s.contains("independent receipts")));
        assertTrue(factory.detail().requirements().stream().anyMatch(s -> s.contains("UNKNOWN with no retry") && s.contains("manual native inspection")));
        assertTrue(factory.detail().requirements().stream().anyMatch(s -> s.contains("Native/live acceptance is pending") && s.contains("prestige")));
        assertEquals(1, content.all().values().stream().flatMap(c -> c.cards().stream()).filter(c -> "chocolate_factory".equals(c.moduleId())).count());
        assertEquals(1, content.all().values().stream().flatMap(c -> c.cards().stream()).filter(c -> "greenhouse".equals(c.moduleId())).count());
    }

    @Test
    void everyCardEitherHasRuntimeOrAnExplicitBlocker() {
        for (ModuleCategory cat : content.categories()) for (CardModel c : content.forCategory(cat).cards()) {
            assertFalse(c.title().isBlank());
            if (c.enabled()) {
                assertNotNull(c.moduleId(), c.title());
                assertFalse(c.moduleId().isBlank(), c.title());
                assertFalse(c.comingSoon());
            } else {
                assertEquals(null,c.moduleId());
                assertTrue(c.hasBlocker(),c.title());
                assertFalse(c.blocker().isBlank(),c.title());
            }
        }
    }

    @Test
    void disabledFarmingCardsAlsoCarryBlockers() {
        for (CardModel c : content.forCategory(ModuleCategory.FARMING).cards()) {
            if (!c.enabled()) {
                assertTrue(c.hasBlocker(), c.title() + " (disabled farming) needs a blocker");
                assertNotEquals("Coming Soon", c.title());
            }
        }
    }

    @Test
    void fishingSupportCardsUseCoreOwnerAndPreserveContractAndAcceptanceLimits() {
        var orb = card(ModuleCategory.FISHING, "Power Orb Support");
        var rain = card(ModuleCategory.FISHING, "Park Rain Support");
        for (var support : List.of(orb, rain)) {
            assertFalse(support.comingSoon());
            assertEquals(dev.veltraph.client.fishing.module.FishingModule.ID, support.moduleId());
            assertTrue(support.detail().sources().contains("docs/FISHING_SUPPORT_SOURCE_AUDIT.md"));
        }
        assertTrue(orb.detail().stateMachine().stream().anyMatch(s -> s.contains("EFFECT + MANA/CONSUMPTION ACK") && s.contains("RESTORE ROD")));
        assertTrue(orb.detail().requirements().stream().anyMatch(s -> s.contains("[] is not a working profile")));
        assertTrue(orb.detail().sources().contains("Veltraph fishing support"));
        assertTrue(orb.detail().requirements().stream().anyMatch(s -> s.contains("55% mana") && s.contains("explicit profile data")));
        assertTrue(orb.detail().requirements().stream().anyMatch(s -> s.contains("consumable-item and live server variants remain unverified")));
        assertTrue(rain.detail().stateMachine().stream().anyMatch(s -> s.contains("PARK/WEATHER GUARDS") && s.contains("PURSE + TIMER/RAIN ACK")));
        assertTrue(rain.detail().requirements().stream().anyMatch(s -> s.contains("NPC UUID") && s.contains("{} cannot buy")));
        assertTrue(rain.detail().requirements().stream().anyMatch(s -> s.contains("positive total cost cap")));
        assertTrue(rain.detail().requirements().stream().anyMatch(s -> s.contains("original skin classifier") && s.contains("remain unverified")));
        assertTrue(card(ModuleCategory.FISHING, "Core Fishing").detail().requirements().stream()
                .anyMatch(s -> s.contains("both default off and require explicit profiles")));
    }

    @Test
    void orderedVisitorCardPreservesStandaloneServingAndExactFarmResumeContract() {
        var ordered = card(ModuleCategory.FARMING, "Ordered Garden Visitors");
        assertTrue(ordered.enabled());
        assertEquals("garden_visitor_cycle", ordered.moduleId());
        assertEquals("visitor_serve_ready", card(ModuleCategory.FARMING, "Garden Visitors").moduleId());
        assertTrue(ordered.detail().stateMachine().stream().anyMatch(s -> s.contains("EXACT FARM/HELPER LEASE")
                && s.contains("ORIGINAL GEAR ACK") && s.contains("PHYSICAL POSE/VIEW RETURN") && s.contains("RESUME SAME ENGINE")));
        assertTrue(ordered.detail().requirements().stream().anyMatch(s -> s.contains("positive aggregate cycle/offer caps") && s.contains("separate opt-in")));
        assertTrue(ordered.detail().requirements().stream().anyMatch(s -> s.contains("descending is farthest first") && s.contains("20-block range")));
        assertTrue(ordered.detail().requirements().stream().anyMatch(s -> s.contains("Receipts alone") && s.contains("post-cleanup proof")));
        assertTrue(ordered.detail().requirements().stream().anyMatch(s -> s.contains("reward autosell") && s.contains("remain incomplete")));
    }

    @Test
    void plotCleanerCardRequiresPreparedScopeAndDoesNotClaimBulkNativeParity() {
        var cleaner = card(ModuleCategory.SETUPS, "Plot Cleaner");
        assertTrue(cleaner.enabled());
        assertEquals("plot_cleaner_workflow", cleaner.moduleId());
        assertTrue(cleaner.detail().stateMachine().stream().anyMatch(s -> s.contains("IMMUTABLE PREPARED BOUNDS") && s.contains("FRESH TARGETED PROGRESS >=90") && s.contains("GROUNDED PAD/SLOT/VIEW RETURN")));
        assertTrue(cleaner.detail().requirements().stream().anyMatch(s -> s.contains("/vplotcleaner prepare") && s.contains("shared guard")));
        assertTrue(cleaner.detail().requirements().stream().anyMatch(s -> s.contains("All three opt-ins default false") && s.contains("strings start empty")));
        assertTrue(cleaner.detail().requirements().stream().anyMatch(s -> s.contains("preflight repeated before confirmation") && s.contains("block entities protected")));
        assertTrue(cleaner.detail().requirements().stream().anyMatch(s -> s.contains("not original native bulk-row ability") && s.contains("live Hypixel Superflat/menu parity")));
        assertFalse(card(ModuleCategory.SETUPS, "Plot Preset Copy/Paste").enabled());
    }

    @Test
    void alchemyBrewChainExplainsConfiguredSaleAndCollectionOnlyModes() {
        var chain = card(ModuleCategory.MISC, "Configured Alchemy Brew Chain");
        assertTrue(chain.enabled());
        assertEquals("alchemy_brew_chain", chain.moduleId());
        assertEquals("alchemy_prepared_brew", card(ModuleCategory.MISC, "Prepared Alchemy Brew").moduleId());
        assertTrue(chain.detail().requirements().stream().anyMatch(s -> s.contains("standard brewing stand") && s.contains("custom brewing menu")));
        assertTrue(chain.detail().requirements().stream().anyMatch(s -> s.contains("inventory must confirm") && s.contains("missing purchase receipts")));
        assertTrue(chain.detail().requirements().stream().anyMatch(s -> s.contains("terminalSale") && s.contains("otherwise the chain collects only")));
        assertTrue(chain.detail().requirements().stream().anyMatch(s -> s.contains("one cookie menu per batch") && s.contains("every sale and menu close is confirmed")));
        assertTrue(chain.detail().requirements().stream().anyMatch(s -> s.contains("Foreign menus, cursors") && s.contains("actual menu titles")));
    }

    @Test
    void grappleCardUsesFishingOwnerAndDoesNotInferGenericPlayerPullOrTetherSuccess() {
        var grapple = card(ModuleCategory.FISHING, "Grappleshot Blazes");
        assertTrue(grapple.enabled());
        assertEquals(dev.veltraph.client.fishing.module.FishingModule.ID, grapple.moduleId());
        assertTrue(grapple.metaLines().stream().anyMatch(s -> s.contains("Crimson Isle")));
        assertTrue(grapple.detail().stateMachine().stream().anyMatch(s -> s.contains("AIM <=3 DEG") && s.contains("PHYSICAL CAST POSITION/VIEW RETURN")));
        assertTrue(grapple.detail().requirements().stream().anyMatch(s -> s.contains("distance >14") && s.contains("5/2-sample hysteresis")));
        assertTrue(grapple.detail().requirements().stream().anyMatch(s -> s.contains("that Bat is leashed to the owned player") && s.contains("merely nearby entities")));
        assertTrue(grapple.detail().requirements().stream().anyMatch(s -> s.contains("move more than one block into 14 blocks") && s.contains("tether alone is insufficient")));
        assertTrue(grapple.detail().requirements().stream().anyMatch(s -> s.contains("generic player grappling") && s.contains("consumable-tool branch") && s.contains("remain unverified")));
        assertTrue(grapple.detail().sources().contains("docs/FISHING_GRAPPLE_SOURCE_AUDIT.md"));
    }

    @Test
    void privateIslandReplantCardNamesAllSevenStagesAndKeepsNativeFixtureLimits() {
        var replant = card(ModuleCategory.FORAGING, "Private Island Dark Oak");
        assertTrue(replant.enabled());
        assertEquals("island_foraging_replant", replant.moduleId());
        String steps = String.join(" ", replant.detail().stateMachine());
        for (String stage : List.of("SWITCH_TO_SAPLINGS", "PLANTING_SAPLINGS", "SWITCH_TO_BONEMEAL", "USE_BONEMEAL", "GO_TO_AXE", "USE_AXE", "RESETTING")) assertTrue(steps.contains(stage), stage);
        assertTrue(replant.detail().requirements().stream().anyMatch(s -> s.contains("sapling placement and resource consumption")));
        assertTrue(replant.detail().requirements().stream().anyMatch(s -> s.contains("consumption and physical growth") && s.contains("before harvest")));
        assertTrue(replant.detail().requirements().stream().anyMatch(s -> s.contains("89 actual logs") && s.contains("test-only delayed whole-tree axe counterpart")));
        assertTrue(replant.detail().requirements().stream().anyMatch(s -> s.contains("Side-chest") && s.contains("Enchanted Bonemeal") && s.contains("full Galatea") && s.contains("remain unaccepted")));
        assertTrue(replant.detail().sources().contains("docs/FORAGING_REPLANT.md"));
    }

    @Test
    void abiphoneCardLimitsNativeContactIngressWithoutCompletingCallerJobs() {
        var call = card(ModuleCategory.MISC, "Abiphone Contact Call");
        assertTrue(call.enabled());
        assertEquals("abiphone_call", call.moduleId());
        assertTrue(call.detail().stateMachine().stream().anyMatch(s -> s.contains("NATIVE AIR USE ONCE") && s.contains("RECEIVED RECEIPT + NPC DIALOGUE") && s.contains("NATIVE RESTORATION ACK")));
        assertTrue(call.detail().requirements().stream().anyMatch(s -> s.contains("full components") && s.contains("received receipt")));
        assertTrue(call.detail().requirements().stream().anyMatch(s -> s.contains("main-slot restoration") && s.contains("received post-SWAP revision")));
        assertTrue(call.detail().requirements().stream().anyMatch(s -> s.contains("caller-managed lease") && s.contains("local APIs") && s.contains("not automatic merchant completion")));
        assertTrue(call.detail().requirements().stream().anyMatch(s -> s.contains("RECOVERY_REQUIRED") && s.contains("no retry")));
        assertTrue(call.detail().requirements().stream().anyMatch(s -> s.contains("Merchant/George/Phillip") && s.contains("salted command prefix") && s.contains("remain unverified")));
        assertFalse(card(ModuleCategory.MISC, "Abiphone Routines").enabled());
        assertTrue(call.detail().sources().contains("docs/ABIPHONE_CALL_AUDIT.md"));
    }

    @Test
    void pestMaintenanceCardKeepsBoxAcknowledgmentsAndStandaloneScopesDistinct() {
        var maintenance = card(ModuleCategory.FARMING, "Pest Farm Maintenance / Box2");
        assertTrue(maintenance.enabled());
        assertEquals("garden_pest_maintenance", maintenance.moduleId());
        assertTrue(maintenance.detail().stateMachine().stream().anyMatch(s -> s.contains("EXACT FARM/HELPER LEASE")
                && s.contains("BOX OCCUPIED/EMPTY + BAG ACK") && s.contains("RESUME SAME ENGINE")));
        assertTrue(maintenance.detail().requirements().stream().anyMatch(s -> s.contains("fresh native receipt") && s.contains("initially empty box cannot finish")));
        assertTrue(maintenance.detail().requirements().stream().anyMatch(s -> s.contains("same farm engine") && s.contains("rechecked after cleanup")));
        assertTrue(maintenance.detail().requirements().stream().anyMatch(s -> s.contains("early swap, automatic spray scheduling") && s.contains("remain separate")));
        var tracker = card(ModuleCategory.FARMING, "Particle-Guided Pest Maintenance");
        assertEquals("garden_pest_tracker_cycle", tracker.moduleId());
        assertTrue(tracker.detail().requirements().stream().anyMatch(s -> s.contains("roof/unstuck") && s.contains("remain separate")));
        assertEquals("garden_pest_cleaner", card(ModuleCategory.FARMING, "Garden Pest Vacuum").moduleId());
        assertTrue(card(ModuleCategory.FARMING, "Garden Traps").enabled());
        assertFalse(card(ModuleCategory.FARMING, "Pest Control").enabled());
    }

    @Test
    void fishingHasLiveCoreModuleCardPlusDisabledExtras() {
        CardModel core = card(ModuleCategory.FISHING, "Core Fishing");
        assertFalse(core.comingSoon(), "Core Fishing is a live module card now");
        assertTrue(core.enabled());
        assertEquals(dev.veltraph.client.fishing.module.FishingModule.ID, core.moduleId());
        // The other recovered fishing automations remain inspectable disabled cards.
        long disabled = content.forCategory(ModuleCategory.FISHING).cards().stream()
                .filter(c -> !c.enabled()).count();
        assertTrue(disabled >= 1, "fishing should still list disabled extras");
    }

    @Test
    void foragingHasLiveTreeChopModuleCardPlusDisabledExtras() {
        CardModel chop = card(ModuleCategory.FORAGING, "Tree Chopping");
        assertFalse(chop.comingSoon(), "Tree Chopping is a live module card now");
        assertTrue(chop.enabled());
        assertEquals(dev.veltraph.client.foraging.module.TreeChopModule.ID, chop.moduleId());
        long disabled = content.forCategory(ModuleCategory.FORAGING).cards().stream()
                .filter(c -> !c.enabled()).count();
        assertTrue(disabled >= 1, "foraging should still list disabled extras");
    }

    @Test
    void spotChecksForKeyRecoveredAutomations() {
        // COMBAT contains "Auto-Slayer" (still disabled).
        assertTrue(card(ModuleCategory.COMBAT, "Auto-Slayer Quest Management").enabled());
        // Bounded serving and current-plot pest transactions are wired; full orchestration remains separate.
        assertTrue(card(ModuleCategory.FARMING, "Garden Visitors").enabled());
        assertEquals("visitor_serve_ready",card(ModuleCategory.FARMING,"Garden Visitors").moduleId());
        assertFalse(card(ModuleCategory.FARMING, "Pest Control").enabled());
        // MINING surfaces "Ghost Grinding (The Mist)".
        assertTrue(card(ModuleCategory.MINING, "Ghost Grinding (The Mist)").enabled());
        // INTERMEDIARY "Return-to-Location" is marked partially implemented, not blocked.
        CardModel rtl = card(ModuleCategory.INTERMEDIARY, "Return-to-Location");
        assertTrue(rtl.hasBlocker());
        assertTrue(rtl.blocker().toLowerCase().contains("partial"),
                "Return-to-Location should note partial rewarp implementation: " + rtl.blocker());
    }

    @Test
    void everyCropControllerExposesLayoutAreaToolAndState() {
        // Crop controllers carry layout/area/tool/state; serving carries offer/budget requirements.
        for (CardModel c : content.forCategory(ModuleCategory.FARMING).cards()) {
            if (!c.enabled() || !CROP_MODULE_IDS.contains(c.moduleId())) {
                continue;
            }
            assertTrue(hasMetaPrefix(c, "Layout:"), c.title() + " missing layout");
            assertTrue(hasMeta(c, "Area: " + AutomationsContent.FARMING_AREA), c.title() + " missing Garden area");
            assertTrue(hasMetaPrefix(c, "Tool:"), c.title() + " missing tool");
            assertTrue(hasMetaPrefix(c, "State:"), c.title() + " missing recovery state");
        }
    }

    @Test
    void pumpkinMelonIsOneCombinedCardNotTwo() {
        List<CardModel> cards = content.forCategory(ModuleCategory.FARMING).cards();
        long pumpkinish = cards.stream()
                .filter(c -> c.title().toLowerCase().contains("pumpkin") || c.title().toLowerCase().contains("melon"))
                .count();
        assertEquals(1, pumpkinish, "Pumpkin/Melon must be a single card");
        CardModel c = card(ModuleCategory.FARMING, "Pumpkin/Melon");
        assertTrue(c.metaLines().stream().anyMatch(m ->
                        m.toLowerCase().contains("pumpkin") && m.toLowerCase().contains("melon")),
                "Card must declare combined pumpkin + melon");
    }

    @Test
    void sugarcaneFlowersIsASingleGroupedCard() {
        List<CardModel> cards = content.forCategory(ModuleCategory.FARMING).cards();
        long sugarcaneish = cards.stream()
                .filter(c -> c.title().toLowerCase().contains("sugarcane") || c.title().toLowerCase().contains("flower"))
                .count();
        assertEquals(1, sugarcaneish, "Sugarcane/Flowers must be a single grouped card");
        assertEquals("Sugarcane/Flowers", card(ModuleCategory.FARMING, "Sugarcane/Flowers").title());
    }

    @Test
    void wartCropsListsFourCropsAndHasNoBehaviorChangingCropSelector() {
        CardModel wart = card(ModuleCategory.FARMING, "Wart/Crops");
        // The 4 auto-detected crops are surfaced as info, not as a per-crop behavior selector.
        assertTrue(wart.hasSupportedCrops());
        assertEquals(List.of("Nether Wart", "Potato", "Carrot", "Wheat"), wart.supportedCrops());

        // The only in-card selector is the real S-Shape <-> Vertical mode cycler (two source modules),
        // never a crop selector.
        assertTrue(wart.hasModeCycler());
        assertEquals(2, wart.modeOptions().size());
        for (CardModel.ModeOption opt : wart.modeOptions()) {
            String label = opt.label().toLowerCase();
            assertFalse(label.contains("wart") || label.contains("potato")
                            || label.contains("carrot") || label.contains("wheat") || label.contains("crop"),
                    "Mode cycler must not be a crop selector: " + opt.label());
        }
        assertTrue(wart.modeOptions().stream().anyMatch(o -> o.label().equals("S-Shape")));
        assertTrue(wart.modeOptions().stream().anyMatch(o -> o.label().equals("Vertical")));
    }

    @Test
    void mushroomKeepsItsTwoModeCyclerAndNoCropList() {
        CardModel mushroom = card(ModuleCategory.FARMING, "Mushroom");
        assertTrue(mushroom.hasModeCycler());
        assertEquals(2, mushroom.modeOptions().size());
        assertFalse(mushroom.hasSupportedCrops());
    }

    @Test
    void authorLaneRolesAndNonCapturedPumpkinTargetDoNotClaimUnverifiedFullAssets() {
        var mushroom = card(ModuleCategory.FARMING, "Mushroom").detail();
        assertTrue(mushroom.stateMachine().stream().anyMatch(s -> s.contains("W in a verified non-trapdoor") && s.contains("S in a verified trapdoor") && s.contains("geometry pauses") && s.contains("verified corners")));
        assertTrue(mushroom.requirements().stream().anyMatch(s -> s.contains("mode cycler applies only to the Recovered")));
        assertTrue(mushroom.stateMachine().stream().anyMatch(s -> s.contains("Full unchanged-map loop accepted")
                && s.contains("24 lower and 21 upper") && s.contains("natural stairs/four-block drop") && s.contains("physical closed return")));
        assertTrue(mushroom.stateMachine().stream().anyMatch(s -> s.contains("yaw -26.57 / pitch 0") && s.contains("supported east trapdoor entry")));
        assertTrue(mushroom.stateMachine().stream().anyMatch(s -> s.contains("4,592 generated, 2,240 delivered")
                && s.contains("test-only server Auto-pickup counterpart") && s.contains("excess real loot")));
        assertTrue(mushroom.requirements().stream().anyMatch(s -> s.contains("Other rotations") && s.contains("live Hypixel remain unverified") && s.contains("63.43 straight segments")));
        assertTrue(mushroom.requirements().stream().anyMatch(s -> s.contains("not a confirmed author START marker")));
        assertTrue(mushroom.sources().contains("docs/verification/2026-09-30/MUSHROOM_FULL_AUTHOR_LOOP.md"));
        assertFalse(mushroom.stateMachine().stream().anyMatch(s -> s.contains("full start/stair/loop coverage remains pending")));
        var pumpkin = card(ModuleCategory.FARMING, "Pumpkin/Melon").detail();
        assertTrue(pumpkin.stateMachine().stream().anyMatch(s -> s.contains("non-captured pumpkin design is MelonKingDE") && s.contains("SAIGO remains a distinct regression")));
        assertTrue(pumpkin.stateMachine().stream().anyMatch(s -> s.contains("three-layer, one-plot tutorial reconstruction") && s.contains("creator-provided schematic has not been obtained") && s.contains("under verification")));
    }

    @Test
    void currentLocalWorkflowCardsUseTheRegisteredModuleIds() {
        for (var expected : List.of(
                List.of(ModuleCategory.FARMING, "Spray Current Plot", "pest_spray_current_plot"),
                List.of(ModuleCategory.FARMING, "Garden Traps", "garden_pest_traps"),
                List.of(ModuleCategory.FARMING, "Garden Repellent", "garden_pest_repellent"),
                List.of(ModuleCategory.FARMING, "Garden Pest Vacuum", "garden_pest_cleaner"),
                List.of(ModuleCategory.FARMING, "Garden Supply Refill", "pest_supply_refill"),
                List.of(ModuleCategory.COMBAT, "Zealot Ranged", "zealot_ranged"),
                List.of(ModuleCategory.COMBAT, "Quiver Refill", "auto_quiver"),
                List.of(ModuleCategory.FORAGING, "Fig Tree", "galatea_fig"),
                List.of(ModuleCategory.FORAGING, "Mangrove", "galatea_mangrove"),
                List.of(ModuleCategory.FORAGING, "Galatea Mats", "galatea_materials"),
                List.of(ModuleCategory.FISHING, "Shard Capture Tracking", "shard_capture"),
                List.of(ModuleCategory.FISHING, "Shard Leveling / Mass Syphon", "shards"),
                List.of(ModuleCategory.HUNTING, "Underwater Worm Hunting", "core_fishing"),
                List.of(ModuleCategory.MINING, "Cobblestone Mining", "cobblestone"),
                List.of(ModuleCategory.MINING, "Mine Down", "mine_down"),
                List.of(ModuleCategory.MINING, "Echo", "echo"),
                List.of(ModuleCategory.MISC, "Experiments Table", "experiments"),
                List.of(ModuleCategory.MISC, "Bazaar Inventory Sale", "auto_sell_bazaar"),
                List.of(ModuleCategory.MISC, "Bazaar Inventory and Sacks", "auto_sell_bazaar_custody"),
                List.of(ModuleCategory.MISC, "Guarded Inventory Selling", "auto_sell_inventory"),
                List.of(ModuleCategory.MISC, "Inventory & Equipment", "equipment_workflow"),
                List.of(ModuleCategory.MISC, "Prepared Alchemy Brew", "alchemy_prepared_brew"),
                List.of(ModuleCategory.MISC, "Inventory Carpentry Craft", "carpentry_inventory_craft"),
                List.of(ModuleCategory.MISC, "Bottle Acquisition / Storage", "bottle_buy_store"),
                List.of(ModuleCategory.MISC, "Local Fairy Souls", "fairy_souls_local"))) {
            var current = card((ModuleCategory) expected.get(0), (String) expected.get(1));
            assertTrue(current.enabled(), current.title());
            assertEquals(expected.get(2), current.moduleId());
            assertTrue(current.hasDetail(), current.title());
            assertFalse(current.detail().requirements().isEmpty(), current.title());
        }
    }

    @Test
    void liveSubsetsStillExplainRequiredConfigurationAndUnfinishedBranches() {
        assertTrue(card(ModuleCategory.FISHING, "Shard Leveling / Mass Syphon").detail().requirements().stream()
                .anyMatch(s -> s.contains("exact menu/button labels")));
        assertTrue(card(ModuleCategory.MISC, "Experiments Table").detail().requirements().stream()
                .anyMatch(s -> s.contains("renewal/XP") && s.contains("native parent execution is pending")));
        assertTrue(card(ModuleCategory.MISC, "Bazaar Inventory Sale").detail().requirements().stream()
                .anyMatch(s -> s.contains("Bazaar Inventory and Sacks")));
        assertFalse(card(ModuleCategory.FARMING, "Pest Control").enabled());
        assertFalse(card(ModuleCategory.MINING, "Cobblestone Craft / Store / Rejoin").enabled());
        assertFalse(card(ModuleCategory.MISC, "Abiphone Routines").enabled());
    }

    @Test
    void skillNativeMenuStartsAndMaterialClassificationRemainExplicit() {
        for (String title : List.of("Prepared Alchemy Brew", "Inventory Carpentry Craft", "Bottle Acquisition / Storage")) {
            assertTrue(card(ModuleCategory.MISC, title).detail().requirements().stream()
                    .anyMatch(s -> s.contains("native") && s.contains("container shortcut")), title);
        }
        assertTrue(card(ModuleCategory.FISHING, "Bait Buying").enabled());
        var materials = card(ModuleCategory.FORAGING, "Galatea Mats");
        assertTrue(materials.detail().requirements().stream().anyMatch(s -> s.contains("Lushlilac bush flow only") && s.contains("Sorrow combat drops")));
        assertFalse(card(ModuleCategory.FORAGING, "Oceandy / Deep Root Workflows").enabled());
        var ghost = card(ModuleCategory.COMBAT, "Ghost Grinding");
        assertTrue(ghost.metaLines().stream().anyMatch(s -> s.contains("Ghost")));
        assertTrue(ghost.detail().requirements().stream().anyMatch(s -> s.contains("Sorrow notices") && s.contains("separate from collected inventory")));
    }

    @Test
    void acceptedBoundedTransactionsKeepTheirRequiredContractsAndOriginalAlternatives() {
        var traps = card(ModuleCategory.FARMING, "Garden Traps");
        assertTrue(traps.detail().requirements().stream().anyMatch(s -> s.contains("exact trap/entity/menu/material bindings")));
        assertTrue(traps.detail().requirements().stream().anyMatch(s -> s.contains("do not count as vacuumed pests")));
        assertTrue(card(ModuleCategory.FARMING, "Garden Pest Vacuum").detail().requirements().stream()
                .anyMatch(s -> s.contains("Tracker") && s.contains("Philip")));
        var selling = card(ModuleCategory.MISC, "Guarded Inventory Selling");
        assertTrue(selling.detail().requirements().stream().anyMatch(s -> s.contains("Only main slots 9-35")));
        assertTrue(selling.detail().requirements().stream().anyMatch(s -> s.contains("Bulk/cookie/warp/Abiphone") && s.contains("unverified")));
        assertFalse(card(ModuleCategory.FARMING, "Pest Control").enabled());
        assertFalse(card(ModuleCategory.FARMING, "Pest Control").blocker().contains("acceptance pending"));
    }

    @Test
    void capturedGourdCorrectionDoesNotReuseTheRejectedOneLaneClaim() {
        var detail = card(ModuleCategory.FARMING, "Pumpkin/Melon").detail();
        assertTrue(detail.stateMachine().stream()
                .anyMatch(s -> s.contains("diagonal wall-contact") && s.contains("previous one-lane") && s.contains("does not establish")));
        assertTrue(detail.stateMachine().stream().anyMatch(s -> s.contains("all 3,350 original crops")
                && s.contains("all 19 bands") && s.contains("31 legitimate boundary crops")));
        assertTrue(detail.stateMachine().stream().anyMatch(s -> s.contains("16 facing cases")
                && s.contains("actual saved-world reset/fresh natural harvest") && s.contains("three-layer MelonKingDE course remain unverified")));
        assertTrue(detail.sources().contains("verification/2026-09-29/FARMING_CAPTURED_DEFAULT_ACCEPTED.md"));
        assertFalse(detail.stateMachine().stream().anyMatch(s -> s.contains("full captured-design acceptance remain under verification")));
    }

    @Test
    void verticalWartDetailUsesActualHorizontalProbesAndObservedMovement() {
        var detail = card(ModuleCategory.FARMING, "Wart/Crops").detail();
        assertTrue(detail.stateMachine().stream().anyMatch(s -> s.contains("horizontal 1..255") && s.contains("persistent lateral") && s.contains("observed fall/drop")));
        assertFalse(detail.stateMachine().stream().anyMatch(s -> s.contains("256-deep DOWN")));
        assertTrue(detail.stateMachine().stream().anyMatch(s -> s.contains("Ace full-layout acceptance remains pending")));
    }

    // ---- Batch 10: card detail drawers ----

    @Test
    void everyFarmingLiveCardHasDetailWithStateMachineAndSources() {
        for (CardModel c : content.forCategory(ModuleCategory.FARMING).cards()) {
            if (!c.enabled()) {
                continue;
            }
            assertTrue(c.hasDetail(), c.title() + " (live) should have a detail drawer");
            assertFalse(c.detail().stateMachine().isEmpty(), c.title() + " detail needs a state machine");
            assertFalse(c.detail().requirements().isEmpty(), c.title() + " detail needs requirements");
            assertFalse(c.detail().sources().isEmpty(), c.title() + " detail needs source citations");
        }
    }

    @Test
    void liveFishingAndForagingCardsHaveDetail() {
        assertTrue(card(ModuleCategory.FISHING, "Core Fishing").hasDetail());
        assertTrue(card(ModuleCategory.FORAGING, "Tree Chopping").hasDetail());
        // Source citations are present (e.g. the recovered spec parts).
        assertTrue(card(ModuleCategory.FISHING, "Core Fishing").detail().sources().stream()
                .anyMatch(s -> s.contains("FishingBiteWire")));
        assertTrue(card(ModuleCategory.FORAGING, "Tree Chopping").detail().sources().stream()
                .anyMatch(s -> s.contains("TreeBreaker")));
    }

    @Test
    void everyDisabledCardHasInspectableDetail() {
        for (ModuleCategory cat : ModuleCategory.values()) {
            if (cat == ModuleCategory.INTERNAL) {
                continue;
            }
            for (CardModel c : content.forCategory(cat).cards()) {
                if (c.enabled()) {
                    continue;
                }
                assertTrue(c.hasDetail(), c.title() + " in " + cat + " should have an inspectable detail drawer");
                // Disabled cards carry their recovered FSM (the "what" line) + an area requirement + an
                // evidence-tagged source, so the drawer is never empty.
                assertFalse(c.detail().stateMachine().isEmpty(), c.title() + " detail needs a behavior line");
                assertFalse(c.detail().requirements().isEmpty(), c.title() + " detail needs requirements");
                assertFalse(c.detail().sources().isEmpty(), c.title() + " detail needs sources");
            }
        }
    }

    @Test
    void wartCropsDetailNamesBothLayoutStateMachines() {
        CardDetail d = card(ModuleCategory.FARMING, "Wart/Crops").detail();
        assertTrue(d.stateMachine().stream().anyMatch(s -> s.contains("FARMING_SIDEWAYS")),
                "Wart/Crops detail should name the S-shape FSM");
        assertTrue(d.stateMachine().stream().anyMatch(s -> s.toLowerCase().contains("two-counter")
                        || s.toLowerCase().contains("still-check")),
                "Wart/Crops detail should name the vertical two-counter machine");
        assertTrue(d.requirements().stream().anyMatch(s -> s.contains("Garden")),
                "Wart/Crops detail should state the Garden area gate");
    }

    private static boolean hasMeta(CardModel c, String exact) {
        return c.metaLines().contains(exact);
    }

    private static boolean hasMetaPrefix(CardModel c, String prefix) {
        return c.metaLines().stream().anyMatch(m -> m.startsWith(prefix));
    }
}
