package dev.veltraph.client.ui.content;

import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.farming.domain.CropType;
import dev.veltraph.client.farming.domain.FarmCard;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.domain.FarmMovementMode;
import dev.veltraph.client.farming.module.CactusFarmModule;
import dev.veltraph.client.farming.module.CocoaFarmModule;
import dev.veltraph.client.farming.module.MushroomFarmModule;
import dev.veltraph.client.farming.module.PumpkinMelonFarmModule;
import dev.veltraph.client.farming.module.SugarcaneFlowersFarmModule;
import dev.veltraph.client.farming.module.WartCropsFarmModule;
import dev.veltraph.client.fishing.module.FishingModule;
import dev.veltraph.client.foraging.module.TreeChopModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (Minecraft-free) content model for the Automations screen. Builds one {@link CategoryContent}
 * per non-{@link ModuleCategory#INTERNAL} category. Runnable cards identify their current local
 * workflows; unfinished branches retain inspectable disabled cards. Farm cards derive their modes
 * from the {@link FarmLayoutRegistry}.
 *
 * <p>Card facts are source-backed (see {@code FARMING_LAYOUT_PLANNER_DEEP_RECOVERY.md},
 * {@code _spec_parts/FarmingUiLayoutSelection.md}, {@code _spec_parts/FarmingWartCropsLayout.md} and
 * {@code docs/LOCATION_GATES.md}):
 * <ul>
 *   <li>All farming controllers self-declare area {@code GARDEN}.</li>
 *   <li>Wart/Crops auto-detects its crop variety from world geometry — it is <b>not</b> a behavior
 *       switch — so the card surfaces a "Supported crops" info line, not a per-crop selector. It does
 *       keep the real S-Shape&harr;Vertical mode cycler (two separate source modules).</li>
 *   <li>Pumpkin/Melon is one card farming the combined pumpkin+melon set ({@code CropType.PUMPKIN}).</li>
 *   <li>Sugarcane/Flowers is a single grouped card (Flowers reuses Sugarcane movement code).</li>
 * </ul>
 *
 * <p>Recovered automations without an active workflow surface as an <b>inspectable disabled card</b>
 * ({@link CardModel#disabled}) that names the real macro, summarises what it does, tags the recovery
 * evidence, and states its {@code blocker}. The per-card facts and evidence tags trace to
 * {@code reports/MACRO_INVENTORY.md} and the per-family {@code MACRO_DECODE_*.md} decodes (cited inline
 * on each card list below). Evidence tags follow {@code MACRO_INVENTORY.md} §"Evidence types":
 * {@code [E]} plaintext enum/config name, {@code [C]} readable decompiled control flow, {@code [S]}
 * decrypted DES string.
 */
public final class AutomationsContent {

    /** Area label shared by every farming controller (CONFIRMED/INFERRED-strong, LOCATION_GATES.md). */
    public static final String FARMING_AREA = "Garden";

    /** Default blocker for a recovered-but-not-yet-ported automation. */
    public static final String BLOCKER_NOT_PORTED = "Runtime not yet ported";

    /** Crops the Wart/Crops controller auto-detects from world geometry (variety-by-block-match). */
    public static final List<String> WART_SUPPORTED_CROPS =
            List.of("Nether Wart", "Potato", "Carrot", "Wheat");

    private final Map<ModuleCategory, CategoryContent> byCategory;

    private AutomationsContent(Map<ModuleCategory, CategoryContent> byCategory) {
        this.byCategory = byCategory;
    }

    /** Build the Automations content from the recovered farming registry. */
    public static AutomationsContent build(FarmLayoutRegistry registry) {
        Map<ModuleCategory, CategoryContent> map = new LinkedHashMap<>();
        for (ModuleCategory cat : ModuleCategory.values()) {
            if (cat == ModuleCategory.INTERNAL) {
                continue;
            }
            map.put(cat, CategoryContent.ofCards(cat.displayName(), cardsFor(cat, registry)));
        }
        return new AutomationsContent(map);
    }

    /**
     * Cards for one category, including current local workflows and explicit remaining branches.
     */
    private static List<CardModel> cardsFor(ModuleCategory cat, FarmLayoutRegistry registry) {
        return switch (cat) {
            case FARMING -> {
                List<CardModel> cards = new ArrayList<>(farmingCards(registry));
                cards.addAll(gardenSupportCards());
                yield cards;
            }
            case COMBAT -> combatCards();
            case FORAGING -> foragingCards();
            case FISHING -> fishingCards();
            case HUNTING -> huntingCards();
            case MINING -> miningCards();
            case BUILDERS -> buildersCards();
            case MISC -> miscCards();
            case SETUPS -> setupsCards();
            case INTERMEDIARY -> intermediaryCards();
            default -> List.of(CardModel.comingSoon(cat.displayName() + " automations"));
        };
    }

    /**
     * Inspectable disabled card naming a recovered automation. {@code area} + {@code what} + an
     * evidence tag become the meta lines; {@code blocker} states why it is not yet active.
     */
    private static CardModel disabled(String title, String area, String what, String evidence, String blocker) {
        return CardModel.disabled(title,
                        List.of("Area: " + area, what, "Evidence: " + evidence), blocker)
                .withDetail(CardDetail.of(
                        List.of(what),
                        List.of("Area gate: " + area, "Blocked: " + blocker),
                        List.of("Evidence " + evidence + " — MACRO_INVENTORY.md + per-family MACRO_DECODE_*.md")));
    }

    private static CardModel disabled(String title, String area, String what, String evidence) {
        return disabled(title, area, what, evidence, BLOCKER_NOT_PORTED);
    }

    private static CardModel workflow(String title, String id, String area, String summary,
                                      List<String> steps, List<String> requirements, String... sources) {
        return CardModel.module(title, id, List.of("Area: " + area, summary))
                .withDetail(CardDetail.of(steps, requirements, List.of(sources)));
    }

    // ---- FARMING (extra recovered Garden automations beyond the live crop controllers) ------------
    // Source: MACRO_INVENTORY.md §1 (Farming) + §6 (Pest control & Garden support);
    // _spec_parts/VisitorGui.md, MACRO_DECODE_pest.md, _spec_parts/HarpGui.md.
    private static List<CardModel> gardenSupportCards() {
        return List.of(
                workflow("Greenhouse Care / Combinations", "greenhouse", "Garden",
                        "Review exact native diagnostics and Guide recipes; explicitly prepare bounded cultivation",
                        List.of("NATIVE DIAGNOSTICS / GUIDE -> SCOPED CARE / COMBINATION REVIEW -> EXPLICIT PLAN -> DURABLE JOURNAL -> EXACT PLANT + INVENTORY RECEIPT -> PHYSICAL RETURN"),
                        List.of("Actions default off; configure local profile/plot, exact observed Diagnostic Tool bindings, plan, action cap and deadlines in Farming",
                                "/vgreenhouse inspect / show / capture / care; guide / combination require current visible ingredient counts and exact proximity",
                                "/vgreenhouse water or plan prepares only; check then explicit start uses the shared activation guard; stop releases owned inputs",
                                "Captured blocks and historical recipes do not establish plant identity, maturity, timers or current combination rules",
                                "Fresh independent native diagnostics, exact existing tool/resources, protected inventory, loaded supported approach and physical return required",
                                "Archived observations and unresolved journals are system-managed; inspect actual result then reconcile the exact refreshed cell, with no automatic retry",
                                "Native execution and live Hypixel acceptance remain pending; entity cultivation and unobserved recipes remain unsupported"),
                        "docs/features/GREENHOUSE.md"),
                CardModel.module("Garden Visitors", "visitor_serve_ready",
                        List.of("Area: Garden", "Quote, optionally buy missing items, return and serve one visitor",
                                "Enable serving in Settings; open an offer or configure the exact loaded NPC name"))
                        .withDetail(CardDetail.of(
                                List.of("OPEN -> QUOTE -> optional SACKS/BUY -> RETURN -> SERVE -> inventory/menu acknowledgment",
                                        "Accept/skip advice remains available through Visitor Advisor"),
                                List.of("Auto Serve, Max Prices, quantity/value caps and accept filters apply",
                                        "Buy Missing and Try Sacks First require separate opt-ins; returning requires the same loaded NPC",
                                        "This card serves one offer. Ordered Garden Visitors can sell approved items after the queue; automatic barn discovery is unavailable"),
                                List.of("_spec_parts/VisitorGui.md", "verification/2026-09-29/INVENTORY_READY_VISITOR_SERVE.md"))),
                workflow("Ordered Garden Visitors", "garden_visitor_cycle", "Garden",
                        "Pause farming, serve configured visitors, optionally sell approved items, then return to your saved position and resume",
                        List.of("EXACT FARM/HELPER LEASE -> OPTIONAL NAMED LOADOUT -> ORDERED QUOTE/BUY/SERVE JOBS -> ORIGINAL GEAR ACK -> PHYSICAL POSE/VIEW RETURN -> RESUME SAME ENGINE"),
                        List.of("Enable Ordered Visitor Cycle and Auto Serve; start from an eligible running Garden farm with closed menus",
                                "Configure unique loaded NPC names, count cap and positive aggregate cycle/offer caps; buying remains a separate opt-in",
                                "Distance order uses the explicit x,y,z origin or initial pose; descending is farthest first within the source 20-block range",
                                "Named loadout swap defaults off; shared exact menu and full gear profiles must include the visitor set and current farming set",
                                "Receipts alone cannot restore gear or resume farming; tool, slot, world, profile, physical standing/view and post-cleanup proof must agree",
                                "Automatic threshold triggers, original barn-model scanning, reward autosell and live server vocabulary remain incomplete"), "docs/verification/2026-09-30/VISITOR_CYCLE_LOADOUT.md"),
                workflow("Spray Current Plot", "pest_spray_current_plot", "Garden",
                        "Use an already selected Sprayonator and inventory spray on the configured current plot",
                        List.of("VALIDATE -> SELECT -> USE ONCE -> WAIT FOR SERVER MESSAGE + MATERIAL CONSUMPTION -> RESTORE SLOT"),
                        List.of("Enable Sprayonator; set exact material name and selected spray lore",
                                "Configured plot, units, unit value and total value cap must match; optional contest guard",
                                "Use Garden Supply Refill for separately configured acquisition; this card applies already selected inventory spray"), "MACRO_DECODE_pest.md"),
                workflow("Contest Trap Maintenance", "garden_pest_trap_cycle", "Garden running farm",
                        "Release and bait one configured loaded trap, then restore and return to the same farm",
                        List.of("20 SCOPED CALLBACKS -> EXACT TAB # + STRICT CONTEST WINDOW -> GUARDED FARM LEASE -> NATIVE RELEASE/BAIT + READY DECREMENT -> RESTORE/RETURN -> SAME FARM"),
                        List.of("Enable automatic trap maintenance, contest-only dispatch and Collect Traps; configure exact trap contracts in Farming",
                                "Ready-tab prefix defaults blank and contest cutoff defaults -1; unknown bindings cannot dispatch",
                                "A receipt proves the trap action; physical return must complete before continuation. Multi-trap/capacity alternatives and Jacob reward claims are not provided"), "docs/verification/2026-10-01/trap-contest/HANDOFF.md"),
                workflow("Garden Traps", "garden_pest_traps", "Garden current plot",
                        "Release a configured trap's pests and optionally place its exact bait quantity",
                        List.of("APPROACH/OPEN -> RELEASE ONCE -> COUNT ZERO + RELEASED MODELS + RECEIPT -> PICK/PLACE BAIT -> ACK/RETURN REMAINDER"),
                        List.of("Enable Collect Traps and/or Refill Trap Bait; configure exact trap/entity/menu/material bindings in Farming",
                                "Menus closed, empty cursor and selected plot/contest guards; missing bait requires separately opted-in supply refill and positive caps",
                                "Released models do not count as vacuumed pests; automatic multi-plot trap orchestration remains separate"), "docs/GARDEN_PEST_PHASE2_WORKFLOWS.md"),
                workflow("Garden Repellent", "garden_pest_repellent", "Garden current plot",
                        "Acquire one configured repellent through Desk/Skymart and observe its consumption and effect",
                        List.of("DESK/SKYMART ROUTE -> QUOTE -> BUY ONCE -> COPPER + INVENTORY ACK -> RELOCATE/USE -> CONSUMPTION + EFFECT ACK -> RESTORE"),
                        List.of("Enable Use Repellent and configure exact route, item, price/currency and effect bindings in Farming",
                                "Positive unit/total caps, selected plot and optional contest guard; no unacknowledged purchase retry",
                                "MAX-repellent upgrade recipes, multi-plot transport and live labels remain unverified"), "docs/GARDEN_PEST_PHASE2_WORKFLOWS.md"),
                workflow("Garden Pest Vacuum", "garden_pest_cleaner", "Garden current plot",
                        "Vacuum a bounded batch of exact loaded pest models and verify their bag increase",
                        List.of("OWN/RELOCATE VACUUM -> APPROACH/AIM -> BOUNDED USE HOLD -> SAME MODEL REMOVAL + BAG DELTA + RECEIPT -> RESTORE"),
                        List.of("Enable Pest Cleaner; configure exact vacuum, pest identities, bag counter, reach and hold budget in Farming",
                                "Loaded selected plot, empty cursor and permitted movement; decoys and uncertain captures are refused",
                                "Tracker particle following, roof paths and Philip exchange remain separate; Box2 is available through leased maintenance"), "verification/2026-09-29/garden-pests-phase2.json"),
                workflow("Pest Farm Maintenance / Box2", "garden_pest_maintenance", "Garden",
                        "Lease one running farm, clean its configured plot or assigned Box2, restore named gear and physically return before resuming",
                        List.of("EXACT FARM/HELPER LEASE -> OPTIONAL NAMED PEST/KILLING GEAR -> CURRENT-PLOT BATCH OR ASSIGNED SPAWN RECEIPT -> BOX OCCUPIED/EMPTY + BAG ACK -> ORIGINAL GEAR PROOF -> PHYSICAL RETURN -> RESUME SAME ENGINE"),
                        List.of("Maintenance defaults off; existing Pest Cleaner opt-in, exact vacuum/model/bag bindings, configured plot and bounded deadline are required",
                                "Named swap defaults off; shared full gear profiles must include the current Farming Loadout Name and selected pest/killing name",
                                "Box2 defaults off; explicit grid, unique first-enabled assignment, exact spawn/yaw/pitch and delay/spawn-wait bounds required",
                                "Same-position spawn still needs a fresh native receipt; an initially empty box cannot finish. Remaining plot models require assigned-spawn return before regular cleaning",
                                "Actual original named gear, copied tool/slot and supported saved pose/view are rechecked after cleanup before the same farm engine resumes",
                                "Manual stop or uncertain delivery keeps farming stopped; early swap, automatic spray scheduling and live vocabulary remain separate"),
                        "verification/2026-09-30/garden-loadout-box.json"),
                workflow("Particle-Guided Pest Maintenance", "garden_pest_tracker_cycle", "Garden",
                        "Follow native tracker particles, capture configured pests and return to the same farm engine",
                        List.of("EXACT FARM LEASE -> PLOT RECEIPT -> NATIVE TRACKER CLICK/PARTICLES -> MOVING PEST CAPTURE -> PROTECTED RETURN -> RESUME"),
                        List.of("Enable tracker maintenance and configure exact plot, vacuum, model and receipt bindings",
                                "Component native outcomes verified; production factory checks, roof/unstuck and automatic thresholds remain separate"),
                        "docs/GARDEN_PEST_TRACKER_PHILIP_CURRENT.md"),
                workflow("Philip Pest-Bag Exchange", "garden_pest_philip", "Garden",
                        "Contact Philip through the configured source route and confirm an exact bag exchange",
                        List.of("COMMAND / LEGACY PHONE / BARN NPC -> OWNED MENU -> ONE EXCHANGE -> BAG/RECEIPT ACK -> CLOSE"),
                        List.of("Enable Philip exchange and configure exact contact/menu/receipt metadata",
                                "Native component routes and failure guards verified; current command route is distinct from legacy phone use"),
                        "docs/GARDEN_PEST_TRACKER_PHILIP_CURRENT.md"),
                workflow("Philip Farm Maintenance", "garden_pest_philip_cycle", "Garden",
                        "Exchange the pest bag under the running farm's exact lease and physically return before resuming",
                        List.of("EXACT FARM LEASE -> PHILIP EXCHANGE -> PROTECTED INVENTORY/MENU PROOF -> SAVED POSE RETURN -> SAME ENGINE"),
                        List.of("Enable Philip exchange and maintenance; set bounded deadline and explicit bindings",
                                "Component return verified; production factory and automatic threshold scheduling remain separate"),
                        "docs/GARDEN_PEST_TRACKER_PHILIP_CURRENT.md"),
                workflow("Garden Supply Refill", "pest_supply_refill", "Garden selected plot / barn",
                        "Acquire one exact configured Garden supply deficit from sacks or a guarded Bazaar purchase",
                        List.of("OPTIONAL SACKS -> EXACT DEFICIT/QUOTE -> NATIVE AMOUNT INPUT -> ONE CONFIRM -> INVENTORY + RECEIPT + PURSE ACK"),
                        List.of("Enable Supply Refill; configure exact supply/menu/receipt/purse bindings and positive unit/total caps in Farming",
                                "Try Sacks First is opt-in; a silent withdrawal timeout does not trigger a purchase fallback",
                                "This supplies the declared material; automatic spray selection and full Garden scheduling remain separate"), "docs/GARDEN_PEST_PHASE2_WORKFLOWS.md"),
                disabled("Pest Control", FARMING_AREA,
                        "Current-plot transactions and leased named-loadout/Box2 maintenance have their own live cards",
                        "[E][C]", "Full automatic control remains incomplete: particle tracker, roof paths, Philip and automatic multi-plot scheduling"),
                disabled("Auto-Harp", "SkyBlock",
                        "Available in Misc: start the helper, then open Melody and choose a song",
                        "[C]", "Use the live Auto-Harp card in Misc"),
                disabled("Jacob/Agatha Contests", FARMING_AREA,
                        "Farming/foraging contest automation (Jacob & Agatha)",
                        "[E]"));
    }

    // ---- COMBAT -----------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §4 (Slayer/combat); MACRO_DECODE_slayers.md.
    private static CardModel melee(String title,String id,String area,String targets) {
        return CardModel.module(title,id,List.of("Area: "+area,"Local melee: find -> walk -> aim -> attack", "Targets: "+targets))
                .withDetail(CardDetail.of(List.of("LOCATE -> WALK -> AIM -> ATTACK -> WAIT", "Stops on blocked paths, invalid location or missing weapon"),
                        List.of("Area gate: "+area,"Set Combat Weapon Slot (1-9)","Melee only; quest acquisition, abilities and ranged weapons are separate"),
                        List.of("MACRO_DECODE_combat.md", "MACRO_DECODE_slayers.md", "MACRO_DECODE_ghost.md")));
    }
    private static CardModel ghost(String title) {
        return workflow(title, "ghost_grind", "Dwarven Mines", "Local Ghost routes, owned-hit combat, drops and acknowledged heal/physical return",
                List.of("LOCAL WALK/ETHER ROUTE -> SEARCH/APPROACH -> GUARDED ATTACK -> OBSERVED DEATH -> INVENTORY PICKUP",
                        "LOW HEALTH -> OWNED HEAL ACK -> EXACT PHYSICAL STANDING/VIEW RETURN -> RESUME"),
                List.of("Record/save Dwarven routes with /vroute; set Ghost Local Route Names and Ghost Weapon Slot in Combat",
                        "Ghost healing is opt-in, needs a matching Wither Impact sword/slot, and waits for global-healer ownership release",
                        "Chat Sorrow notices are separate from collected inventory; full original ingress/merchant restock remains open"), "docs/GHOST_GRIND_SOURCE_AUDIT.md");
    }
    private static List<CardModel> combatCards() {
        return List.of(
                melee("Zealot Grinding","zealot_grind","The End","Zealots / Bruisers / Special Zealots"),
                workflow("Zealot Ranged", "zealot_ranged", "The End",
                        "Aim at moving Zealots with a bow or Shortbow; optional saved patrol and eye collection",
                        List.of("PATROL/LOCATE -> APPROACH -> AIM -> DRAW/SHOOT -> COLLECT -> INVENTORY ACK",
                                "Wait through Eye Beam; protect other players' targets; release bow input on stop"),
                        List.of("Set Zealot weapon slot; ordinary bows need arrows",
                                "Optional saved End route; flight/etherwarp nodes require their normal prerequisites",
                                "Optional quiver interruption requires its exact quote, supply and receipt contracts",
                                "Automatic arena setup and dragon combat remain incomplete"), "MACRO_DECODE_combat.md"),
                workflow("Quiver Refill", "auto_quiver", "SkyBlock",
                        "Request one quoted refill and verify its exact supply, receipt and cost outcome",
                        List.of("OPEN/ADOPT MENU -> READ/RECHECK QUOTE -> ONE FILL -> SUPPLY + RECEIPT + COST ACK -> RESTORE"),
                        List.of("Enable refill and configure ammo/opener IDs, menu, quote/receipt templates and positive budget in Combat",
                                "Choose Container supply for observed quiver contents, or Player Inventory for explicit vanilla arrows",
                                "Choose a hotbar opener or exact named merchant; private native merchant travel/refill/return passed; configure live shop vocabulary explicitly",
                                "Zealot auto-refill is a separate opt-in"), "docs/QUIVER_REFILL_SOURCE_AUDIT.md"),
                ghost("Ghost Grinding"),
                melee("Tarantula Grinding","tarantula_grind","Spider's Den","Spiders / Tarantula bosses"),
                melee("Graveyard Grinding","graveyard_grind","Hub","Zombies / Revenant bosses"),
                melee("Bestiary Grinding","bestiary_grind","SkyBlock","Configured target names"),
                workflow("Sven Cave", "sven_cave", "The Park",
                        "Prepare the selected cave and continue with owned bounded combat and patrol",
                        List.of("PREPARATION -> WARP/ROUTE -> ENTRANCE -> RESOURCE/PURCHASE RECEIPT -> OBSERVED OPEN GATE -> INSIDE -> COMBAT"),
                        List.of("Choose DARK_OAK, ACACIA or JUNGLE; configure target names and exact source helper/resource/menu bindings in Combat",
                                "The parent owns ingress, patrol and combat under one input lease",
                                "Issued-warp Root continuity and native end-to-end acceptance are pending; automatic quest policy and source tool ranking remain unbound"),
                        "docs/verification/2026-09-30/SVEN_PARENT_DISPATCH_RECOVERY.md"),
                CardModel.module("Auto-Slayer Quest Management","auto_slayer",List.of("Area: SkyBlock", "Batphone -> quest selection -> combat -> claim/restart", "Configure menu labels, targets and cost limit in Combat settings"))
                        .withDetail(CardDetail.of(List.of("OPEN -> BOSS -> TIER -> CONFIRM -> SERVER START -> COMBAT -> SERVER COMPLETE -> REPEAT", "Cancellation: /vslayer cancel; awaits the server's cancellation message"),
                                List.of("Batphone in hotbar, combat weapon slot, matching menu labels", "Unreadable/over-budget prices or ambiguous menu items stop execution", "Special boss abilities and travel remain separate work"),
                                List.of("_spec_parts/BatphoneQuestState.md", "Veltraph quest setup", "MACRO_DECODE_slayers.md"))),
                disabled("Sven","Mountain / Wolf's Den","Sven cave positioning and ranged weapon behavior","[E]"));
    }

    // ---- FORAGING ---------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §2; MACRO_DECODE_foraging.md;
    // _spec_parts/TreeBreaker.md, _spec_parts/ParkTreeScore.md.
    private static List<CardModel> foragingCards() {
        return List.of(
                // Live module (Batch 7): the BREAK_TREE executor with reach/timeout/blacklist failsafes.
                CardModel.module("Tree Chopping", TreeChopModule.ID,
                        List.of("Area: Galatea / Park / Hub (SkyBlock)",
                                "Hold-to-break the crosshair log (reach 4.5 / timeout / blacklist failsafes)",
                                "Tool: Axe",
                                "Crosshair chopping; use Park/Hub Foraging for automatic tree finding"))
                        .withDetail(CardDetail.of(
                                List.of("BREAK_TREE: new-target restarts timer; reach > 4.5 + not moving -> abort+blacklist; break-timeout -> abort+blacklist; blacklisted -> skip"),
                                List.of("Area gate: any SkyBlock area", "Tool: Axe (crosshair on a log keyword)",
                                        "Automatic ground tree finding is available in Park/Hub Foraging"),
                                List.of("_spec_parts/TreeBreaker.md", "MACRO_DECODE_foraging.md"))),
                workflow("Private Island Dark Oak", "island_foraging_replant", "Private Island",
                        "Plant four Dark Oak saplings, observe native bonemeal growth, harvest the owned tree and return for the next cycle",
                        List.of("SWITCH_TO_SAPLINGS -> PLANTING_SAPLINGS -> SWITCH_TO_BONEMEAL -> USE_BONEMEAL -> GO_TO_AXE -> USE_AXE -> RESETTING"),
                        List.of("Start explicitly from this card; configure a local x,y,z anchor in Foraging. Blank anchor cannot start; cycles 0 repeats until stopped",
                                "Stand within ordinary reach of four dirt/grass supports and a clear loaded 2x2 footprint/growth volume; four Dark Oak saplings, ordinary bonemeal and a hotbar axe required",
                                "Planting needs both sapling placement and resource consumption; bonemeal consumption and physical growth must both arrive before harvest",
                                "Source default attempt limit is 2; the private vanilla positive fixture used 32 for stochastic growth. Shared observed axe/Sweep policy does not predict native log geometry",
                                "Two native cycles produced and collected 89 actual logs with a declared test-only delayed whole-tree axe counterpart; full Hypixel Sweep behavior remains unverified",
                                "Normal owned-sapling reset and native inventory loan restitution preserve tools; busy cursor or uncertain state keeps cleanup pending without dropping items",
                                "Side-chest stocking/auto-fill, custom Enchanted Bonemeal, opaque source setup and full Galatea fall/replant workflows remain unaccepted"),
                        "docs/FORAGING_REPLANT.md", "docs/verification/2026-09-30/FORAGING_REPLANT.md"),
                workflow("Fig Tree", "galatea_fig", "Galatea",
                        "Find connected loaded logs and progress through stem, jump, scaffold and throwing-axe modes",
                        List.of("SEARCH -> APPROACH -> BREAK TREE -> OBSERVE REMAINING LOGS -> NEXT MODE/TREE"),
                        List.of("Configure target block IDs and optional six-coordinate region bounds; axe in hotbar",
                                "Scaffold/jump modes require their items/effects; throwing uses an observed Throwing Axe profile and server cooldown guard",
                                "Optional positive Sweep requirement; fresh removals and configured log-reward collection are observed",
                                "Source-region Etherwarp fall recovery has native acceptance; configure observed mana cost and recovery permission",
                                "Exact atlas and source-gated Agatha parent handoff remain separately verified workflows"), "MACRO_DECODE_foraging.md"),
                workflow("Galatea Source Reset", "galatea_source_reset", "Galatea",
                        "Request explicitly with /veltraph galatea reset while the current registered parent is idle",
                        List.of("EXPLICIT REQUEST -> GUARDED PAUSE -> SOURCE COMMAND -> EXACT RECEIPT/ARRIVAL -> SAME PARENT RESUME"),
                        List.of("Source command permission and exact command/destination/receipt/rejection bindings default unset",
                                "Start requires an explicit reset request; no automatic reset trigger is inferred"), "docs/GALATEA_PARENT_ROOT_INTEGRATION.md"),
                workflow("Murkwater Parent Handoff", "galatea_murkwater", "Galatea",
                        "Guarded source travel for the same registered idle Mangrove parent",
                        List.of("MARSH/ANCHOR ADMISSION -> GUARDED PAUSE -> EXACT COMMAND/RECEIPT -> NATIVE ARRIVAL -> SAME ENGINE RESUME"),
                        List.of("Enable source permission and configure exact command, receipt, rejection and native Marsh region line",
                                "Higher-priority owners, missing receipts and manual cancellation deny resume"), "docs/GALATEA_PARENT_ROOT_INTEGRATION.md"),
                workflow("Mangrove", "galatea_mangrove", "Galatea",
                        "Approach connected mangrove logs with gated etherwarp and configured tree movement",
                        List.of("SEARCH -> APPROACH/ETHERWARP -> OBSERVED TREE REMOVAL -> MATCHING DROPS/INVENTORY -> NEXT TREE"),
                        List.of("Target block IDs, axe and Ether Transmission tool; optional region bounds",
                                "Move Around changes access policy; guarded Murkwater handoff requires source command permission and exact command, receipt, rejection and Marsh region bindings"), "MACRO_DECODE_foraging.md"),
                CardModel.module("Park Foraging", "park_foraging", List.of("Area: The Park", "Select configured region/density, observe axe outcomes and collect matching drops", "Tool: configured observed axe profile"))
                        .withDetail(CardDetail.of(List.of("SEARCH_TREE -> WALK -> AIM/BREAK/ABILITY -> QUIET RESULT WINDOW -> DROP COLLECTION",
                                        "Loaded terrain; ghost-block checks and blacklist/collection limits apply"),
                                List.of("Area gate: The Park", "Configure region, density, axe mode and Sweep/result-window policy in Foraging",
                                        "Original atlas and live ability/cooldown variants require separate acceptance"), List.of("_spec_parts/ParkTreeScore.md", "_spec_parts/TreeBreaker.md"))),
                CardModel.module("Hub Foraging", "hub_foraging", List.of("Area: Hub", "Find and walk to nearby logs, then chop", "Tool: Axe / Treecapitator"))
                        .withDetail(CardDetail.of(List.of("SEARCH_TREE -> WALK_TO_TREE -> BREAK_TREE"),List.of("Area gate: Hub", "Walking or server-permitted flight"),List.of("_spec_parts/TreeBreaker.md"))),
                workflow("Galatea Mats", "galatea_materials", "Galatea",
                        "Approach configured loaded material blocks and observe collection outcomes",
                        List.of("SEARCH -> APPROACH -> LEFT/RIGHT INTERACTION -> CONFIRM RESPONSE -> MATCHING DROP/INVENTORY COLLECTION"),
                        List.of("Configure observed bush block IDs, optional region bounds, reward names/token and confirmation window",
                                "Use an observed eligible tool; Assumed Click remains unconfirmed",
                                "Lushlilac bush flow only; Oceandy crafting, Sorrow combat drops and Deep Root mechanics are separate"), "docs/GALATEA_ABILITIES_AND_COLLECTION.md"),
                disabled("Oceandy / Deep Root Workflows", "SkyBlock",
                        "Oceandy requires a concrete crafting contract; global Deep Root item declarations do not define a harvest action",
                        "[E][C]", "No separate Galatea harvest routine is established for these item names"),
                workflow("Lushlilac Trash", "lushlilac_trash", "Galatea",
                        "Travel to an explicit local discard spot and confirm one exact inventory-stack discard",
                        List.of("CHOOSE LOADED SPOT -> WALK -> AIM -> INVENTORY THROW -> SERVER ITEM/INVENTORY ACK -> CLOSE"),
                        List.of("Enable explicit trash permission; configure supported spots and exact stack rules",
                                "Automatic material-parent handoff requires separate permission; the registered parent remains reserved through cleanup and exact fractional pose return"),
                        "docs/GALATEA_REMAINING_WORKFLOWS.md"));
    }

    // ---- FISHING ----------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §3; MACRO_DECODE_fishing.md;
    // _spec_parts/FishingBitePredicate.md, FishingBiteWire.md, FishingConstants.md.
    private static List<CardModel> fishingCards() {
        return List.of(
                // Live module (Batch 4): cast -> wait bite -> reel -> recast, local bobber detection.
                CardModel.module("Core Fishing", FishingModule.ID,
                        List.of("Area: Water bodies (SkyBlock)",
                                "Cast -> wait for bite -> reel -> recast (local bobber detection)",
                                "Tool: Fishing Rod",
                                "Tracked bite detection; optional surfacing and sea-creature melee"))
                        .withDetail(CardDetail.of(
                                List.of("CAST -> WAIT_BITE -> REEL -> DELAY -> CAST; synchronized bite flag, optional fallback; minimum cast age and post-catch delay"),
                                List.of("Area gate: any SkyBlock water", "Tool: Fishing Rod",
                                        "Optional low-air surfacing, underwater hunting, Golden Fish and hotbar totem placement",
                                        "Configured sea-creature melee and optional configured bait restocking",
                                        "Optional configured power orb and Park rain support; both default off and require explicit profiles",
                                        "Optional exact-profile Grappleshot Blazes in Crimson Isle; owned native topology, pull and return must be acknowledged",
                                        "Dreadlord/spin and other ability branches remain incomplete; the historical TNT route belongs to Sven travel"),
                                List.of("_spec_parts/FishingBiteWire.md", "_spec_parts/FishingBitePredicate.md",
                                        "_spec_parts/FishingConstants.md"))),
                shardCaptureCard(),
                disabled("Auto-Killer", "SkyBlock",
                        "Melee matching sea creatures, then return to the fishing spot",
                        "[E][C]", "Built into Core Fishing: enable Sea-Creature Auto-Killer and set creature names in Fishing settings"),
                disabled("Sea-Creature Notify", "Water bodies",
                        "Sea-creature / boss notify (FISHING_BOSS, notify type)",
                        "[E]"),
                workflow("Power Orb Support", FishingModule.ID, "SkyBlock fishing",
                        "Core Fishing can reel, use an exact configured orb, observe its effect and restore the rod",
                        List.of("REEL ACK -> MANA/TOOL GUARDS -> AIM/USE ONCE -> EFFECT + MANA/CONSUMPTION ACK -> RESTORE ROD -> CAST"),
                        List.of("Enable Auto Power Orb and supply ordered exact JSON profiles; [] is not a working profile",
                                "Declare tool/effect identities, recovered duration, mana/item cost, special mana gate and minimum interval",
                                "At least 55% mana and the declared cost are required; item names and renewal multiplier/interval remain explicit profile data",
                                "Received effect and exact cost must agree; active effects suppress reuse until the declared renewal window",
                                "Configured reusable-tool flow verified; consumable-item and live server variants remain unverified"),
                        "docs/FISHING_SUPPORT_SOURCE_AUDIT.md", "Veltraph fishing support"),
                workflow("Park Rain Support", FishingModule.ID, "The Park",
                        "Core Fishing can walk to an exact configured NPC, buy bounded rain and return to its saved cast position",
                        List.of("PARK/WEATHER GUARDS -> WALK/TALK -> EXACT MENU/QUOTE -> BUY ONE MINUTE -> RECEIPT + PURSE + TIMER/RAIN ACK -> RETURN/RESTORE -> CAST"),
                        List.of("Enable Auto Rain and supply exact NPC UUID/type/name/anchor, menu, bucket, quote, purse and receipt JSON contracts; {} cannot buy",
                                "Declare target minutes, minimum check interval and positive total cost cap; each minute waits for native delivery before another purchase",
                                "Owned menu, position, heading and rod are restored; missing delivery stops without retrying",
                                "Private configured workflow verified; original skin classifier, salted intervals and live server vocabulary remain unverified"), "docs/FISHING_SUPPORT_SOURCE_AUDIT.md"),
                workflow("Grappleshot Blazes", FishingModule.ID, "Crimson Isle",
                        "Core Fishing can pull an eligible native Blaze with an exact configured tool, then restore the rod and physically return before casting",
                        List.of("AREA/TOOL/HEALTH GATES -> REEL REMOVAL ACK -> TEMPORARY TOOL -> OPTIONAL STANDING RETURN -> AIM <=3 DEG -> ONE USE -> FRESH BAT/STAND/OWNED LEASH + RECEIVED PULL/CONSUMPTION ACK -> RESTORE ROD -> PHYSICAL CAST POSITION/VIEW RETURN"),
                        List.of("Enable Grappleshot Blazes and supply complete exact tool/ability JSON in Fishing; false and {} are the defaults",
                                "Native Blaze only: distance >14 or source hazard 5/2-sample hysteresis; configured maximum range, received health, line of sight and native cooldown must permit use",
                                "A fresh Bat rides a fresh ArmorStand and that Bat is leashed to the owned player; existing or merely nearby entities cannot acknowledge a request",
                                "Pinned Blaze must move more than one block into 14 blocks and declared item consumption must agree; tether alone is insufficient",
                                "Missing outcome stops without retry/cast; manual hotbar choice and replacement world are preserved, owned rod and physical position/view restored before resume",
                                "Private reusable-tool counterpart verified; generic player grappling, consumable-tool branch, salted names/timings and full live/original fishing parity remain unverified"),
                        "docs/FISHING_GRAPPLE_SOURCE_AUDIT.md", "docs/verification/2026-09-30/FISHING_GRAPPLE.md"),
                workflow("Bait Buying", FishingModule.ID, "SkyBlock",
                        "Core Fishing can restock exact configured bait in inventory or its fishing bag, then resume casting",
                        List.of("REEL ACK -> OPEN/READ BAG -> GUARDED STACK/FILL BUY -> RECEIPT + INVENTORY ACK -> ENABLE/TRANSFER -> RESTORE ROD"),
                        List.of("Enable Auto Bait and supply a complete exact JSON profile in Fishing settings",
                                "Dry ground, four empty main slots and exact menu opener in hotbar slot 9; bag mode also checks storage",
                                "Unit/session/stack caps apply; Ironman setting blocks purchases; fixture vocabulary is not a live Hypixel profile"), "docs/FISHING_BAIT_SOURCE_AUDIT.md"),
                shardWorkflowCard());
    }

    private static CardModel shardCaptureCard() {
        return workflow("Shard Capture Tracking", "shard_capture", "SkyBlock",
                "Count recovered capture messages against an explicit local shard catalog",
                List.of("SERVER CAPTURE MESSAGE -> EXACT CATALOG IDENTITY -> RUN-SCOPED COUNT"),
                List.of("Enable Capture Tracking and configure name=RARITY catalog entries",
                        "Tracking observes chat; it does not perform creature capture or buy shards"), "MACRO_DECODE_fishing.md");
    }

    private static CardModel shardWorkflowCard() {
        return workflow("Shard Leveling / Mass Syphon", "shards", "SkyBlock",
                "Read attribute levels or syphon known shards through configured menus",
                List.of("OPEN/READ -> COST/QUANTITY GUARDS -> optional BUY/CONSUME -> SERVER ACK -> EXIT",
                        "Mass Syphon: OPEN -> MASS BUTTON -> CONFIRM -> OBSERVED ACK"),
                List.of("Enable workflow; configure catalog, commands, exact menu/button labels and lore patterns in Shards settings",
                        "Buying and consumption require separate opt-ins and PPA/session/quantity limits",
                        "Unknown identities, unreadable prices and missing acknowledgments stop the workflow"), "_spec_parts/ShardCostTable.md + MACRO_DECODE_fishing.md");
    }

    // ---- HUNTING ----------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §3 (worm/hunting fishing) + §7 (bestiary grinding).
    private static List<CardModel> huntingCards() {
        return List.of(
                workflow("Active Fish Hunting", "hunting_active", "Configured SkyBlock region",
                        "Approach moving fish, aim gradually and use the configured capture tool with bounded physical charges",
                        List.of("REGION / TOOL GUARDS -> ROUTE / APPROACH -> SOURCE AIM / COD SPIN -> ONE USE -> DAMAGE / DROP / INVENTORY / RECEIPT -> SAVED RETURN"),
                        List.of("Enable Active Hunting and configure the exact target, tool, region and outcome profile in Shards",
                                "Unknown delivery, ambiguous targets and exhausted action or resource budgets stop without retrying",
                                "Native component physics and return verified; production factory and live server vocabulary remain separate"),
                        "docs/ACTIVE_HUNTING_SOURCE_AUDIT.md"),
                workflow("Underwater Worm Hunting", FishingModule.ID, "SkyBlock underwater",
                        "Core Fishing can reel, surface/refill, melee configured creatures and dive to the saved cast position",
                        List.of("REEL ACK -> SURFACE -> REFILL -> MELEE -> DIVE/RETURN -> RESTORE ROD/AIM"),
                        List.of("Enable Underwater Worm Hunting in Fishing; start underwater with a rod",
                                "Configure permitted creature names and combat weapon slot",
                                "Dreadlord/spin mechanics and source worm count/time triggers remain incomplete"), "MACRO_DECODE_fishing.md"),
                shardCaptureCard(), shardWorkflowCard(),
                melee("Bestiary Grinding", "bestiary_grind", "SkyBlock", "Configured target names"));
    }

    // ---- MINING -----------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §5; MACRO_DECODE_ghost.md;
    // _spec_parts/GhostAutoHeal.md, _spec_parts/GhostRouteLoader.md.
    private static List<CardModel> miningCards() {
        return List.of(
                ghost("Ghost Grinding (The Mist)"),
                workflow("Dwarven Commissions", "commissions", "Dwarven Mines",
                        "Mine global Mithril and Titanium commissions; stop when their tasks finish or change",
                        List.of("OBSERVE NATIVE TASKS -> SELECT MATERIALS -> OWNED APPROACH -> MINE -> VERIFY WEAR/REMOVAL/PICKUP",
                                "Stop when tasks complete or change; claim automation awaits a captured claim contract"),
                        List.of("Enable Commissions, Vein Mining and Auto Break Mining; complete tab commission section required",
                                "Regional commissions, mob commissions and automatic claims are not supported yet"), "mightyminer-review-2026-10-05/commissions-e/REVIEW.md"),
                workflow("Cobblestone Mining", "cobblestone", "Private Island",
                        "Mine generated cobblestone with tool, inventory and supported movement guards",
                        List.of("FRONT SCAN -> SELECT TOOL -> AIM/BREAK -> OBSERVE REMOVAL -> COLLECT/SETTLE",
                                "Optional configured craft/store: real picked-up inputs -> acknowledged recipe -> fresh chest storage -> same mining engine resume"),
                        List.of("Aim at generated cobblestone; suitable tool, durability reserve and drop capacity",
                                "Optional walking/sprint/direction changes; auto-craft, storage and rejoin remain incomplete"), "_spec_parts/CobblestoneGenerator.md"),
                workflow("Mine Down", "mine_down", "SkyBlock",
                        "Break the supported floor with minimum-depth and safe landing checks",
                        List.of("VALIDATE FLOOR/LANDING -> AIM/BREAK -> OBSERVE REMOVAL -> LAND/SETTLE"),
                        List.of("Suitable tool, inventory capacity, configured minimum Y and supported landing",
                                "Stops or pauses on fluids, hazards, unloaded terrain or missing capacity"), "MACRO_INVENTORY.md §5"),
                disabled("Crystal/HOTF", "Crystal Hollows",
                        "HOTF_LEVEL_UP, breezily bridger",
                        "[E]"),
                workflow("Echo", "echo", "SkyBlock",
                        "Walk an explicit standing/target checkpoint list and mine each guarded target",
                        List.of("CHECKPOINT -> WALK -> AIM/BREAK -> OBSERVE REMOVAL -> NEXT"),
                        List.of("Configure standing x,y,z>target x,y,z checkpoints in Mining settings",
                                "Checkpoint skipping/session memory are opt-in; loaded safe targets and tools required"), "MACRO_INVENTORY.md §5"),
                disabled("Cobblestone Craft / Store / Rejoin", "Private Island",
                        "Inventory-full crafting, ender chest storage and optional rejoin", "[E][C]"),
                melee("Zealot Grinding", "zealot_grind", "The End", "Zealots / Bruisers"));
    }

    // ---- BUILDERS ---------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §7 (Builder BUILDER_*); _spec_parts/FarmBuilderLayout.md.
    private static List<CardModel> buildersCards() {
        return List.of(
                CardModel.module("Farm Builder", "farm_builder", List.of("Area: Garden", "Execute a loaded local layout; rotate/translate, walk, place or destroy", "Load with /vbuild load <name>"))
                        .withDetail(CardDetail.of(List.of("LOAD -> TRANSFORM -> MOVE -> SELECT -> PLACE/DESTROY -> VERIFY", "Stops on unexpected blocks, missing resources or blocked paths"),
                                List.of("Area gate: Garden", "Explicit layout and matching hotbar materials/tools", "Walking or server-permitted flight; supply materials in the hotbar"),
                                List.of("_spec_parts/FarmBuilderLayout.md", "_spec_parts/FarmBuilderPlaceDestroy.md"))));
    }

    // ---- MISC -------------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §7-8; _spec_parts/ExperimentsSolver.md, BatphoneQuestState.md.
    private static List<CardModel> miscCards() {
        return List.of(
                workflow("Chocolate Factory Planner / Selected Queue", "chocolate_factory", "SkyBlock",
                        "Plan from exact visible Factory fields and run a deliberately selected bounded upgrade queue",
                        List.of("NATIVE MENU CAPTURE -> LOCAL PROFILE -> PLAN / GOAL -> EXPLICIT IDS + BUDGET + RESERVE -> GUARDED START -> NEW EXACT RECEIPT OR UNKNOWN"),
                        List.of("Actions default off; use the actual native Factory menu and Factory tools with /vchocolate inspect / show / begin / field / upgrade / save",
                                "Exact title, profile-specific marker, item identities, numeric fields and current prices required; saved local profiles do not switch game profiles",
                                "/vchocolate plan <reserve> / goal <id> <walletGoal> <reserve>; missing rate deltas or evidence remain unknown and supplied rate deltas are planning assumptions",
                                "/vchocolate select <id[,id...]> <totalQueueBudget> <minimumWalletReserve> prepares 1–16 distinct observed upgrades; start uses the shared guard",
                                "Only EMPLOYEE, PRODUCTION and CAPACITY upgrades execute; configured freshness and receipt deadlines, unchanged exact selected quotes and independent receipts required",
                                "Saved profile map and transaction history are system-managed; missing results latch UNKNOWN with no retry, reconcile only after manual native inspection",
                                "Native/live acceptance is pending; prestige, consumables, upkeep payment and globally optimal planning remain unsupported"),
                        "docs/features/CHOCOLATE_FACTORY.md"),
                CardModel.module("Auto-Harp","auto_harp",List.of("Area: SkyBlock", "Choose a song in Melody's menu; the helper plays its note grid"))
                        .withDetail(CardDetail.of(List.of("Observe grid changes -> queue note -> delayed click", "Exact recovered note+9 mapping; delay configurable"),
                                List.of("Area gate: SkyBlock", "Harp - screen; manual controls remain available", "Automatic timing is UNSUPPORTED: encrypted retention/common-input cooldown limits remain unknown", "Manual delay defaults to 10 and uses local timing; native THROW input targets the recovered note+9 slot"),
                                List.of("Veltraph Harp helper"))),
                workflow("Experiments Table", "experiments", "SkyBlock",
                        "Solve an opened Chronomatron, Ultrasequencer or Superpairs game and claim matched rewards",
                        List.of("OBSERVE SHOW PHASE -> GUARDED INPUT -> SERVER REVEAL/ACK -> CLAIM -> COMPLETION"),
                        List.of("Open/select the experiment before its show phase; configure phase/reward labels and timing",
                                "Optional Guardian preparation requires exact pet/menu/tab bindings and receipt checks; private native preflight and solver continuation passed",
                                "Source-prepared renewal/XP buying/table parent requires separate opt-in and explicit profile prices, XP credit mode and exact receipts; native parent execution is pending"), "_spec_parts/ExperimentsSolver.md"),
                workflow("Bazaar Inventory Sale", "auto_sell_bazaar", "SkyBlock",
                        "Confirm one quoted Bazaar inventory sale and observe its result",
                        List.of("READ QUOTE -> QUANTITY/PROCEEDS GUARDS -> CONFIRM -> SERVER ACK/INVENTORY CHANGE -> CLOSE"),
                        List.of("Open Bazaar, enable Auto-Sell, choose Bazaar and disable sacks; empty cursor required",
                                "Configure exact menu/button labels, limits and timing in Misc",
                                "For an explicit inventory/sacks profile, use Bazaar Inventory and Sacks; NPC, George and trash use Guarded Inventory Selling"), "MACRO_DECODE_misc.md: auto-sell"),
                workflow("Bazaar Inventory and Sacks", "auto_sell_bazaar_custody", "SkyBlock",
                        "Sell the exact inventory stacks and optional sack contents in one configured workflow",
                        List.of("GUARDED START -> CONFIGURED BAZAAR COMMAND -> INVENTORY CONFIRMATION -> OPTIONAL SACKS CONFIRMATION -> RECEIPT/INVENTORY/PURSE CHECKS -> CLOSE"),
                        List.of("Off by default; enable Auto-Sell, choose Bazaar and enable Bazaar Inventory / Sacks Custody in Misc",
                                "Supply the exact Bazaar Custody Profile with command, menu/button/receipt labels, full stack identities, prices and limits",
                                "Empty Sacks must match whether the profile contains a sacks stage; sack counters must have exact observed component identities",
                                "Protected inventory and selected slot must stay unchanged except for the authorized sale; foreign screens, changed settings or missing results stop without retry",
                                "Start uses the shared activation guard and ordinary Root ticks; live Hypixel bindings and this registered workflow require separate validation"), "docs/verification/2026-09-30/SELLING_BAZAAR_SOURCE_TRACE.md"),
                workflow("Farm Auto-sell", "farm_auto_sell", "Garden",
                        "Pause the exact farm engine, sell one authorized stack, restore and resume after confirmed cleanup",
                        List.of("NATIVE COOKIE/INVENTORY TRIGGER -> EXACT FARM LEASE -> SALE/RECEIPT -> CLEANUP -> SAVED POSE/GEAR -> SAME ENGINE RESUME"),
                        List.of("Enable Farm Auto-sell and configure exact cookie footer evidence, pressure/cooldown/deadline and existing NPC/Trash rules in Misc",
                                "Production Garden NPC auto-start and exact farm resume are accepted; terminal stop and emergency healing take precedence",
                                "Missing receipts stop both controllers. Trash wrapper, Bazaar/George travel, bulk and sacks remain separate"), "docs/AUTOSELL_MAINTENANCE.md"),
                workflow("Guarded Inventory Selling", "auto_sell_inventory", "SkyBlock; George requires Hub",
                        "Sell or discard one explicitly allowlisted main-inventory stack through the selected channel",
                        List.of("NPC: EXACT STACK/QUOTE -> ONE CLICK -> INVENTORY + PURSE + RECEIPT ACK",
                                "GEORGE: PICKUP/DEPOSIT -> PRICED ACCEPT/CONFIRM -> INVENTORY + PURSE + RECEIPT + ESCROW ACK",
                                "TRASH: DECLARED SAFE ANCHOR -> ONE THROW -> EXACT NEW ITEM ENTITY + INVENTORY DECREMENT"),
                        List.of("Enable Auto-Sell, choose NPC/George Pets/Trash and disable sacks; configure one matching exact rule plus channel contracts in Misc",
                                "Only main slots 9-35; tools/wearables are protected except an explicitly marked single George pet",
                                "Use /vdebug selling for a read-only identity template; click returned JSON to copy, then explicitly supply its unset value/quote",
                                "Quantity/value/proceeds caps and empty cursor apply; stop can restore only exact owned pet escrow under unchanged conditions",
                                "Bulk/cookie/warp/Abiphone and unobserved lava/void destruction remain incomplete; live pricing/lore unverified"), "docs/verification/2026-09-29/SELLING_CHANNELS.md"),
                workflow("Inventory & Equipment", "equipment_workflow", "SkyBlock",
                        "Run one configured compactor/deletor state change, pet equip or wardrobe selection",
                        List.of("OWN DEVICE/OPEN MENU -> EXACT ACTION -> SERVER STATE/ARMOR/PET ACK -> CLOSE -> RESTORE DEVICE/SLOT"),
                        List.of("Close other menus; enable workflow and configure its kind, exact labels/IDs/state markers and positive timing",
                                "Device mode needs one exact device and a free hotbar slot; wardrobe needs four armor identities; pets need acknowledgment/page contracts",
                                "Personal Deletor is a configured local counterpart; its original body was not recovered",
                                "Bounded restoration can remain pending after stop; automatic macro handoffs and Abiphone calls remain separate"), "docs/verification/2026-09-29/EQUIPMENT_ACCEPTANCE.json"),
                workflow("Abiphone Contact Call", "abiphone_call", "SkyBlock",
                        "Make one exact native phone/contact call, observe NPC dialogue and restore the phone and selected slot",
                        List.of("EXACT INVENTORY/PHONE -> OPTIONAL FREE-HOTBAR SWAP -> NATIVE AIR USE ONCE -> EXACT CONTACT CLICK -> RECEIVED RECEIPT + NPC DIALOGUE -> NATIVE RESTORATION ACK -> OWNED CLOSE OR EXPLICIT DIALOGUE LEASE"),
                        List.of("Enable Abiphone Contact Call; configure exact single phone/contact/dialogue marker identities with full components, distinct menu titles and received receipt in Misc",
                                "Default contract is blank and delay/timeout are -1; explicit delay >=1 and timeout greater than delay, at most 1,200 ticks required",
                                "Closed menus and empty cursor required; main-inventory phone needs a free hotbar slot, occupied tools are never displaced",
                                "Receipt alone or dialogue alone cannot complete; main-slot restoration needs a received post-SWAP revision and exact original inventory pair/selection",
                                "Retained dialogue is an explicit caller-managed lease; release and manual recovery acknowledgment are local APIs, not automatic merchant completion",
                                "Silent restoration stays RECOVERY_REQUIRED with no retry; same-context custody can reserve other automation until explicitly verified recovery",
                                "Merchant/George/Phillip workflows, their salted command prefix, general contact pagination and live vocabulary remain unverified"),
                        "docs/ABIPHONE_CALL_AUDIT.md", "docs/verification/2026-09-30/ABIPHONE.md"),
                workflow("Prepared Alchemy Brew", "alchemy_prepared_brew", "Private Island",
                        "Use a prepared native brewer for one declared ingredient-to-potion transaction",
                        List.of("PICKUP INGREDIENT -> SERVER CURSOR ACK -> PLACE -> WAIT FINAL BREW -> COLLECT EXACT OUTPUTS -> CLOSE"),
                        List.of("Configure exact recipe IDs/names/value in Skills; prepare one single-item ingredient and matching base bottles",
                                "Open the native brewer and press its assigned container shortcut; starting from this page cannot prepare that menu",
                                "Use supplied bottles and ingredients here; choose Configured Alchemy Brew Chain for stand travel, buying supplies, repeated batches and optional potion sales"), "docs/SKILL_WORKFLOWS_SOURCE_AUDIT.md"),
                workflow("Configured Alchemy Brew Chain", "alchemy_brew_chain", "Private Island",
                        "Brew at configured stands, collect potions, optionally sell through the cookie menu, and continue within set limits",
                        List.of("TRAVEL -> SUPPLIES -> BREW -> COLLECT -> OPTIONAL COOKIE SALES -> CONFIRMED CLOSE -> NEXT STAND/RETURN"),
                        List.of("Enable the chain and enter a complete profile in Skills; it starts off with an empty profile",
                                "Set stand coordinates, exact bottles, fuel, reagents and potion outputs; include supply quotes, receipts, and batch/spending/time limits",
                                "Choose a standard brewing stand or configure exact slot details for a custom brewing menu",
                                "Stand contents and inventory must confirm placement and collection; missing purchase receipts stop further buying",
                                "Add terminalSale details to sell collected potions through the SkyBlock cookie menu; otherwise the chain collects only",
                                "Collected potions sell in order through one cookie menu per batch; the next stand starts only after every sale and menu close is confirmed",
                                "Foreign menus, cursors, changed items or uncertain results stop the chain; configure actual menu titles, button identities, Cookie Buff and prices"),
                        "docs/verification/2026-10-01/alchemy-sale-parent/SOURCE_AND_HANDOFF.md"),
                workflow("Inventory Carpentry Craft", "carpentry_inventory_craft", "SkyBlock",
                        "Request one exact inventory-funded recipe and observe server-delivered output",
                        List.of("EXACT RECIPE/LORE -> NORMAL REQUEST -> MATERIAL CONSUMPTION + OUTPUT INVENTORY ACK -> CLOSE"),
                        List.of("Configure exact menu/recipe/input/output identities, quantities and value in Skills",
                                "Open the native recipe menu and press its assigned container shortcut",
                                "Automatic buying/selling, cursor batching and concrete Oceandy recipes remain separate; no recipe is invented"), "docs/SKILL_WORKFLOWS_SOURCE_AUDIT.md"),
                workflow("Carpentry Craft Batches", "carpentry_craft_batches", "SkyBlock",
                        "Buy configured materials, craft batches, optionally sell through Bazaar confirmation, and return",
                        List.of("OPEN -> QUOTED RESOURCES -> CRAFT/DEPOSIT -> OPTIONAL BAZAAR INVENTORY + CONFIRMATION -> ACKNOWLEDGE SALE -> NEXT BATCH/RETURN"),
                        List.of("Enable the batch workflow and configure an exact profile in Skills; defaults are off and blank",
                                "Set exact recipes, output items, resource quotes and receipts, batch count and spending limits",
                                "Optional sales need the actual Bazaar menu, inventory quote, separate confirmation and receipt details",
                                "Optional cookie fallback needs the configured rejection and Bazaar marker plus an active Cookie Buff; an uncertain sale stops the next batch",
                                "Update older NPC sale profiles before starting; menu labels, recipes and prices require explicit configuration"),
                        "docs/verification/2026-10-01/carpentry-bazaar-parent/SOURCE_AND_HANDOFF.md"),
                workflow("Bottle Acquisition / Storage", "bottle_buy_store", "SkyBlock",
                        "Buy one explicitly identified quoted bundle and store it in a configured real chest",
                        List.of("QUOTE -> ONE BUY -> INVENTORY ACK -> WALK/OPEN CHEST -> STORE -> REOPEN -> FRESH SERVER CONTENTS"),
                        List.of("Configure exact bottle/trade/chest IDs, names, XYZ, quantity and cost in Skills; start with no matching bottles",
                                "Open the native quoted trade menu and press its assigned container shortcut",
                                "Original salted predicate does not establish an XP identity; NPC/warp setup and bottle filling remain incomplete"), "docs/SKILL_WORKFLOWS_SOURCE_AUDIT.md"),
                workflow("Water Bottle Chest Filler", "bottle_fill_chest", "Configured SkyBlock area",
                        "Fill prepared glass bottles at ordinary source water and store confirmed water bottles",
                        List.of("WALK TO WATER -> VANILLA FILL + INVENTORY ACK -> WALK/OPEN CHEST -> STORE -> REOPEN/VERIFY -> REPEAT/RESTORE"),
                        List.of("Configure full glass/water identities, source/chest and standing coordinates, title, quantity and timings in Skills",
                                "Native conversion, repeated storage and known stack restoration are accepted; uncertain outcomes stop without retry",
                                "NPC buying, original trough atlas and source warp setup remain separate"), "docs/BOTTLE_FILL_AUDIT.md"),
                workflow("Agatha Contest Claim", "galatea_agatha_claim", "SkyBlock",
                        "Claim a recent exact server invitation through its configured command and native menu",
                        List.of("SERVER INVITATION -> OWNED COMMAND -> MENU -> REWARD/RECEIPT/REVISION ACK -> CLOSE"),
                        List.of("Configure exact invitation/command/menu/reward metadata in Foraging",
                                "Standalone claiming is accepted; automatic parent-foraging interruption remains under integration"), "docs/GALATEA_AGATHA.md"),
                workflow("Local Fairy Souls", "fairy_souls_local", "Configured SkyBlock area",
                        "Follow configured soul coordinates or Etherwarp/walking routes and save only confirmed collections",
                        List.of("COORDINATE OR OUTBOUND ROUTE -> REACH MARKER -> INTERACT -> SERVER CONFIRMATION -> SAVE CLAIM -> DECLARED RETURN/NEXT"),
                        List.of("Set area, soul coordinates, profile and exact marker name in Skills; close menus before starting",
                                "Route entries need a start point and ordered outbound/return Etherwarp or walk hops; supply the Etherwarp face and required travel item",
                                "Each walking hop has ten seconds; Etherwarp needs a new matching teleport receipt",
                                "Route entries always follow their declared return path; coordinate entries use the Return to Start setting",
                                "Unavailable or ambiguous markers and unsupported routes are skipped before travel; a failure after travel begins stops collection"),
                        "docs/verification/2026-10-01/fairy-typed-route/SOURCE_AND_NATIVE_SCOPE.md"),
                disabled("Abiphone Routines", "Anywhere",
                        "Abiphone routines (George pets sell, Philip, Beth)",
                        "[E]", "Exact native contact calling is available separately; caller jobs and the latest George/Phillip command-call prefix remain unported"));
    }

    // ---- SETUPS -----------------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §1 (plot preset copy/paste, plot-cleaner).
    private static List<CardModel> setupsCards() {
        return List.of(
                disabled("Plot Preset Copy/Paste", FARMING_AREA,
                        "Copy/paste a plot preset across Garden plots",
                        "[E]"),
                workflow("Local Garden Blueprint Scan", "garden_analyzer", "Garden",
                        "Read every loaded cell and save a coherent local state blueprint",
                        List.of("EXPLICIT BOUNDS -> LOADED READ -> SECOND-PASS VALIDATION -> LOCAL SAVE"),
                        List.of("Prepare with /vgarden scan, then use status/save; no blocks, inventory or inputs are changed",
                                "Retains AIR, block properties and block-entity presence; payload copying is unsupported"), "docs/verification/2026-09-30/GARDEN_ANALYZER_COPY.md"),
                workflow("Local Garden Structural Copy", "garden_plot_copy", "Garden",
                        "Place configured ordinary structural materials from a prepared rotated blueprint",
                        List.of("LOCAL BLUEPRINT -> VALIDATE DESTINATION -> REAL RESOURCES/PLACEMENT ACK -> PAUSE/EXPLICIT RESUME"),
                        List.of("Use /vgarden paste or paste-current, enable copy in Builder settings, then start/resume explicitly",
                                "Protects existing nonmatching blocks and AIR holes; missing crop/fluid/special states are refused",
                                "Arbitrary full-farm construction and original multi-plot copy/preset behavior remain incomplete"), "docs/verification/2026-09-30/GARDEN_ANALYZER_COPY.md"),
                workflow("Plot Cleaner", "plot_cleaner_workflow", "Garden",
                        "Use explicitly prepared bounds for desk/preset acknowledgment, ordinary row mining, dirt cleanup and physical return",
                        List.of("IMMUTABLE PREPARED BOUNDS -> REVALIDATE -> DESK/PLOT/SUPERFLAT/READY CONFIRM -> FRESH TARGETED PROGRESS >=90 -> ORDINARY ROW/CLEANUP MINING -> FULL RESCAN -> GROUNDED PAD/SLOT/VIEW RETURN"),
                        List.of("Prepare explicitly with /vplotcleaner prepare <plot> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>; inspect/clear only inactive, start uses the shared guard, stop cancels",
                                "All three opt-ins default false; enable workflow, ordinary mining counterpart and owner-verified exact bindings in Builder settings. Eleven command/menu strings start empty",
                                "Immutable loaded plot 1-24 bounds, Garden X/Z -240..239, floor >=66, maximum 96x32x96 and scan budget; unchanged return pad must be outside deletion bounds",
                                "Exact selected scope/ready lore and fresh targeted progress are required; old 100% or unrelated sidebar changes cannot acknowledge this operation",
                                "Existing flight permission and actual shovel required; only dirt/grass/AIR are admitted, with crops/stone/fluids/block entities protected and full preflight repeated before confirmation",
                                "Native private mining removed 11 cells with matching shovel damage and grounded physical return; runtime grants no flight and writes no pose/velocity",
                                "Explicit ordinary survival mining counterpart, not original native bulk-row ability or live Hypixel Superflat/menu parity; source salted bindings and dispatcher gaps remain"),
                        "docs/verification/2026-09-30/PLOT_CLEANER_WORKFLOW.md"));
    }

    // ---- INTERMEDIARY -----------------------------------------------------------------------------
    // Source: MACRO_INVENTORY.md §1 (desk teleport DESK/TPTOPLOT/FLIGHT), §2 (etherwarp shortcuts),
    // §9 (STOP_AND_RETURN_TO_LOCATION). Note: the rewarp setter is already live (Settings -> Rewarper).
    private static List<CardModel> intermediaryCards() {
        return List.of(
                CardModel.module("Local Route", "local_route", List.of("Area: recorded SkyBlock area", "Record, save and follow checkpoints with /vroute"))
                        .withDetail(CardDetail.of(List.of("WALK / FLY / ETHERWARP -> ALIGN -> NEXT CHECKPOINT; optional loop"),
                                List.of("/vroute add [walk|fly|etherwarp] | save <name> | load <name> | start | stop", "Loaded terrain; server flight permission or Ether Transmission tool for the selected mode"),
                                List.of("_spec_parts/GhostRouteLoader.md", "MACRO_DECODE_fishing.md: location setup"))),
                disabled("Desk/Plot Travel", FARMING_AREA,
                        "Desk teleport + plot travel (DESK / TPTOPLOT / FLIGHT)",
                        "[E]"),
                disabled("Etherwarp Shortcuts", "SkyBlock",
                        "Explicit target block/face, stable aim, one item use and server-confirmed arrival",
                        "[E][C]", "Available through Local Route: /vroute add etherwarp"),
                disabled("Return-to-Location", "Anywhere",
                        "Automatic saved XYZ/heading recovery with authoritative cancellation and suspended farm restoration",
                        "[E][S][C]", "Partial: saved-anchor recovery is active; source desk/plot transport remains incomplete"));
    }

    private static List<CardModel> farmingCards(FarmLayoutRegistry registry) {
        List<CardModel> cards = new ArrayList<>();

        // Wart/Crops — S-Shape + Vertical are two real source modules (mode cycler kept). Crop variety
        // is AUTO-DETECTED from world geometry, so we surface a "Supported crops" line, NOT a selector.
        FarmLayoutProfile sShape = profile(registry, FarmLayoutRegistry.WART_CROPS_SSHAPE, FarmCard.WART_CROPS);
        cards.add(new CardModel(
                "Wart/Crops",
                WartCropsFarmModule.ID,
                false,
                farmingMeta("S-Shape / Vertical", sShape, List.of("hoe", "wart")),
                List.of(
                        new CardModel.ModeOption("S-Shape", FarmMovementMode.S_SHAPE.name()),
                        new CardModel.ModeOption("Vertical", FarmMovementMode.VERTICAL.name())),
                WART_SUPPORTED_CROPS,
                null,
                CardDetail.of(
                        List.of("S-Shape: PREPARE_TOOL -> AIMING -> FARMING_SIDEWAYS -> observed row destination -> reverse side",
                                "Vertical: 40/20 two-counter observations; independent horizontal 1..255 probes, persistent lateral inputs and observed fall/drop arrival",
                                "Ace full-layout acceptance remains pending; supported primitives and drop fixtures do not prove its complete design"),
                        farmingRequirements("hoe"),
                        List.of("_spec_parts/FarmingForwardExecutor.md", "_spec_parts/MovementDirectionDecoded.md",
                                "_spec_parts/FarmingMovementPlannerHotspots.md §5a", "WartCropsFarmModule.java:scanDirection"))));

        // Mushroom — Classical + Staircase are two real source modes (mode cycler kept).
        FarmLayoutProfile mushroom = profile(registry, FarmLayoutRegistry.MUSHROOM_CLASSICAL, FarmCard.MUSHROOM);
        cards.add(new CardModel(
                "Mushroom",
                MushroomFarmModule.ID,
                false,
                farmingMeta("Recovered / Ring Current", mushroom, List.of("hoe", "mushroom")),
                List.of(
                        new CardModel.ModeOption("Classical", FarmMovementMode.CLASSICAL.name()),
                        new CardModel.ModeOption("Staircase", FarmMovementMode.STAIRCASE.name())),
                List.of(),
                null,
                CardDetail.of(
                        List.of("Choose Recovered or Ring Current farm design in Farming settings",
                                "Ring Current: W in a verified non-trapdoor lane; S in a verified trapdoor lane; unknown or ambiguous geometry pauses, verified corners use wall contact and supported outgoing guides",
                                "Unchanged creator map: fixed published yaw -26.57 / pitch 0 at nominal 259 speed (.259 local counterpart), from a supported east trapdoor entry",
                                "Full unchanged-map loop accepted: all 24 lower and 21 upper corners on client/server, natural stairs/four-block drop and physical closed return within 0.705 blocks",
                                "Actual native loot: 4,592 generated, 2,240 delivered with a test-only server Auto-pickup counterpart; excess real loot remains in the world",
                                "Fixed view and structure preserved; Ring suppresses optional jitter only, with shared saved preferences unchanged",
                                "Classical: fixed-side row traversal (MUSHROOM_DIR)",
                                "Staircase: depth-3 FOUND/BLOCKED/NULL probe -> MUSHROOM_DIR (side+forward step) or FORWARD; EXTREME/SLIGHT path-mode"),
                        mushroomRequirements(),
                        List.of("_spec_parts/FarmingMovementPlannerHotspots.md §5d", "FARMING_LAYOUT_PLANNER_DEEP_RECOVERY.md §4",
                                "docs/verification/2026-09-30/MUSHROOM_FULL_AUTHOR_LOOP.md"))));

        // Pumpkin/Melon — ONE card, combined pumpkin+melon harvest (CropType.PUMPKIN).
        FarmLayoutProfile pumpkin = profile(registry, FarmLayoutRegistry.PUMPKIN_MELON, FarmCard.PUMPKIN_MELON);
        cards.add(simpleFarmCard("Pumpkin/Melon", PumpkinMelonFarmModule.ID, "Captured / MelonKingDE target / regression modes",
                pumpkin, List.of("dicer", "cleaver"),
                "Combined pumpkin + melon harvest (" + CropType.PUMPKIN.displayName() + ")",
                CardDetail.of(
                        List.of("The requested non-captured pumpkin design is MelonKingDE; SAIGO remains a distinct regression option.",
                                "MelonKingDE uses the requested three-layer, one-plot tutorial reconstruction; a creator-provided schematic has not been obtained. Full crop coverage and repeated resets remain under verification.",
                                "Captured modified-default diagonal wall-contact traversal accepted: all 3,350 original crops, all 19 bands and 31 legitimate boundary crops; the previous one-lane fixture does not establish this result.",
                                "Its diagonal pitch defaults to 40 degrees; previously saved 26-degree settings must be adjusted manually, with custom settings preserved.",
                                "16 facing cases and actual saved-world reset/fresh natural harvest accepted; complete pickup, stock default and the three-layer MelonKingDE course remain unverified."),
                        farmingRequirements("dicer / cleaver"),
                        List.of("_spec_parts/PumpkinOnEndRowTrigger.md", "_spec_parts/CactusPumpkinCrossCheck.md",
                                "verification/2026-09-29/FARMING_CAPTURED_DEFAULT_ACCEPTED.md"))));

        // Sugarcane/Flowers — ONE grouped card (Flowers reuses the Sugarcane controller).
        FarmLayoutProfile cane = profile(registry, FarmLayoutRegistry.SUGARCANE_FLOWERS, FarmCard.SUGARCANE_FLOWERS);
        cards.add(simpleFarmCard("Sugarcane/Flowers", SugarcaneFlowersFarmModule.ID, "Recovered / Ace Two-Plot",
                cane, List.of("hoe", "cane"),
                "Sugarcane + Flowers grouped (shared Sugarcane movement)",
                CardDetail.of(
                        List.of("Select design and exact crop while stopped; the captured choice stays fixed during the run.",
                                "RECOVERED: configured-side <-> BACK toggle at each row end; aim = baseYaw +/- 45.",
                                "ACE_TWO_PLOT: creator diagonal row chords, carpet-guided return and native walking corners at published speed 350; full cane and sunflower courses accepted.",
                                "Rose Bush is source-supported but lacks a separate full native course. Flower regrowth is observed, never fabricated."),
                        farmingRequirements("hoe"),
                        List.of("_spec_parts/FarmingMovementPlannerHotspots.md §5c", "_spec_parts/FarmingSugarcaneFlowersLayout.md",
                                "docs/verification/2026-09-30/ACE_CANEFLOWER_FULL_AUTHOR.md"))));

        FarmLayoutProfile cocoa = profile(registry, FarmLayoutRegistry.COCOA, FarmCard.COCOA);
        cards.add(simpleFarmCard("Cocoa", CocoaFarmModule.ID, "Lane-Switch",
                cocoa, List.of("axe", "cocoa"), null,
                CardDetail.of(
                        List.of("FARMING <-> SWITCHING_ROW: lane side via player.up(2) probe + COCOA_LANE_SWITCH_DIRECTION; same-tick sneak latch; aim pitch -45"),
                        farmingRequirements("axe"),
                        List.of("_spec_parts/FarmingMovementPlannerHotspots.md §5b", "_spec_parts/FarmingCocoaLayout.md"))));

        FarmLayoutProfile cactus = profile(registry, FarmLayoutRegistry.CACTUS_STRAIGHT, FarmCard.CACTUS);
        cards.add(simpleFarmCard("Cactus", CactusFarmModule.ID, "Straight",
                cactus, List.of("cactus", "knife"), null,
                CardDetail.of(
                        List.of("Shared MovementDirection row engine: FARMING -> GOING_FORWARD -> TURNING (boustrophedon), turn cooldown"),
                        farmingRequirements("cactus knife"),
                        List.of("_spec_parts/CactusPumpkinCrossCheck.md", "_spec_parts/MovementDirectionDecoded.md"))));

        return cards;
    }

    /** Shared requirement lines for the Garden farming family (area gate + tool + on-ground). */
    private static List<String> farmingRequirements(String tool) {
        return List.of("Area gate: " + FARMING_AREA + " (Hypixel SkyBlock)",
                "Tool: " + tool + " in the hotbar (auto-selected or FARMING_TOOL_SLOT)",
                "On the ground; macro stops on GUI / world change / Limbo (failsafe)");
    }

    private static List<String> mushroomRequirements() {
        List<String> requirements = new ArrayList<>(farmingRequirements("hoe"));
        requirements.add("Classical/Staircase mode cycler applies only to the Recovered design");
        requirements.add("Ring Current follows loaded local guide geometry; no coordinate route or camera turns are required for the accepted unchanged-map course");
        requirements.add("Use a supported east trapdoor lane for the accepted reference orientation; the decoration is not a confirmed author START marker");
        requirements.add("Other rotations, full separate recovered geometry, complete pickup and live Hypixel remain unverified; earlier 63.43 straight segments are historical bounded evidence");
        return requirements;
    }

    private static CardModel simpleFarmCard(String title, String moduleId, String layout,
                                            FarmLayoutProfile profile, List<String> toolKeywords,
                                            String extraNote, CardDetail detail) {
        List<String> meta = farmingMeta(layout, profile, toolKeywords);
        if (extraNote != null) {
            meta.add(extraNote);
        }
        return CardModel.module(title, moduleId, meta).withDetail(detail);
    }

    /** Layout / area / tool / implementation-state meta lines required for every farming card. */
    private static List<String> farmingMeta(String layout, FarmLayoutProfile profile,
                                            List<String> toolKeywords) {
        List<String> meta = new ArrayList<>();
        meta.add("Layout: " + layout);
        meta.add("Area: " + FARMING_AREA);
        meta.add("Tool: " + String.join(" / ", toolKeywords));
        meta.add("State: " + profile.recoveryStatus().description());
        return meta;
    }

    private static FarmLayoutProfile profile(FarmLayoutRegistry registry, String id, FarmCard expectedCard) {
        FarmLayoutProfile p = registry.byId(id).orElseThrow(
                () -> new IllegalStateException("Missing farming profile: " + id));
        if (p.card() != expectedCard) {
            throw new IllegalStateException("Profile " + id + " card mismatch: " + p.card());
        }
        return p;
    }

    public CategoryContent forCategory(ModuleCategory category) {
        CategoryContent c = byCategory.get(category);
        if (c == null) {
            throw new IllegalArgumentException("No Automations content for category " + category);
        }
        return c;
    }

    public Map<ModuleCategory, CategoryContent> all() {
        return Map.copyOf(byCategory);
    }

    /** Non-INTERNAL categories in declaration order (the rail order). */
    public List<ModuleCategory> categories() {
        return List.copyOf(byCategory.keySet());
    }
}
