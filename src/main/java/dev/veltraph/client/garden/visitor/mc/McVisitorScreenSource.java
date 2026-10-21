package dev.veltraph.client.garden.visitor.mc;

import dev.veltraph.client.garden.visitor.BazaarPriceParser;
import dev.veltraph.client.garden.visitor.VisitorItem;
import dev.veltraph.client.garden.visitor.VisitorLoreReader;
import dev.veltraph.client.garden.visitor.VisitorOffer;
import dev.veltraph.client.garden.visitor.VisitorRarity;
import dev.veltraph.client.garden.visitor.VisitorRequirements;
import dev.veltraph.client.garden.visitor.VisitorUnitQuote;
import dev.veltraph.client.garden.visitor.VisitorScreenSource;
import dev.veltraph.client.game.LocationText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Read-only MC adapter that reads the currently-open Garden visitor menu into a pure {@link VisitorOffer}.
 * Source: {@code reports/_spec_parts/VisitorGui.md} — the visitor screen's buttons/items are matched by
 * <b>display name</b> (not fixed salted slot indices), and the accept button reads "Accept Offer".
 *
 * <p><b>Read-only and conservative.</b> It inspects the open {@code HandledScreen}'s slots only; it never
 * clicks, buys, or serves. It returns a visitor offer <i>only</i> when it can confirm a visitor menu by
 * finding an "Accept Offer" slot — so a normal chest/menu never produces a false offer. Every MC access is
 * wrapped in try/catch and degrades to {@link Optional#empty()} on any error, so a UI/version change can
 * never crash the client.
 *
 * <p><b>What it reads (by display name / lore, never fixed slots):</b> the visitor name (screen title),
 * the required item names + counts (slot stacks), and — from the "Accept Offer" item's lore — the
 * rarity word, the "new visitor" flag, and an explicit coin reward (delegated to the pure
 * {@link VisitorLoreReader}). Per-item Bazaar prices are read from each material's own lore when a coins
 * line is present ({@link BazaarPriceParser}); in a vanilla visitor menu the items carry no price line, so
 * prices usually stay unknown and the {@link dev.veltraph.client.garden.visitor.VisitorPolicy} treats that
 * fail-closed (won't claim affordability it cannot prove). The {@code BARRIER} sentinel item is
 * skipped (mirrors {@code BazaarConstants}'s early-out). Reward-via-copper conversion needs a live copper
 * price (a documented follow-up; see {@code docs/GARDEN_VISITOR_NOTES.md}).
 */
public final class McVisitorScreenSource implements VisitorScreenSource {

    private static final String ACCEPT_OFFER = "accept offer";
    private static final String REFUSE_OFFER = "refuse offer";

    private final Minecraft client;
    private final dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver history;
    private dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Observation lastHistory=
            dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN.observe("");
    @Override public dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Observation historyObservation(){return lastHistory;}

    public McVisitorScreenSource(Minecraft client) {
        this(client, dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN);
    }

    public McVisitorScreenSource(Minecraft client,
            dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver history) {
        this.client = client;
        this.history = java.util.Objects.requireNonNull(history);
    }

    @Override
    public Optional<VisitorOffer> read() {
        lastHistory=dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN.observe("");
        try {
            Screen screen = client.gui.screen();
            if (!(screen instanceof AbstractContainerScreen<?> handled)) {
                return Optional.empty();
            }
            AbstractContainerMenu handler = handled.getMenu();
            if (handler == null) {
                return Optional.empty();
            }

            boolean isVisitorScreen = false;
            List<String> acceptLore = List.of();
            List<VisitorItem> items = new ArrayList<>();
            for (Slot slot : handler.slots) {
                if (slot == null || (client.player!=null && slot.container==client.player.getInventory())) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (stack.is(Items.BARRIER)) {
                    continue; // Verified Yarn mapping: BazaarConstants field_8077 is BARRIER, not a book.
                }
                String name = LocationText.stripColorCodes(stack.getHoverName().getString()).trim();
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.equals(ACCEPT_OFFER)) {
                    isVisitorScreen = true;
                    acceptLore = readLore(stack);
                    continue;
                }
                if (lower.equals(REFUSE_OFFER) || isFiller(name)) {
                    continue;
                }
                // A required-material stack: name + needed count + (rare) a Bazaar price line.
                List<String> itemLore = readLore(stack);
                OptionalLong price = VisitorUnitQuote.read(itemLore);
                if(price.isPresent())items.add(new VisitorItem(name, Math.max(1, stack.getCount()),price.getAsLong()));
            }

            if (!isVisitorScreen) {
                return Optional.empty();
            }
            var required=VisitorRequirements.read(acceptLore);
            if(VisitorRequirements.hasSection(acceptLore)&&required.isEmpty())return Optional.empty();
            if(required.isPresent()){
                var quoted=items;items=required.get().stream().map(item->quoted.stream().filter(q->q.nameMatches(item.name()))
                        .findFirst().map(q->item.withUnitPrice(q.unitPrice())).orElse(item)).toList();
            }
            String visitorName = LocationText.stripColorCodes(screen.getTitle().getString()).trim();
            Component npcName=handler.slots.stream().filter(slot->client.player==null||slot.container!=client.player.getInventory())
                    .map(Slot::getItem).filter(stack->!stack.isEmpty()&&LocationText.stripColorCodes(stack.getHoverName().getString()).trim().equals(visitorName))
                    .map(ItemStack::getHoverName).findFirst().orElse(null);
            VisitorRarity rarity = McVisitorIdentity.rarity(visitorName, acceptLore,npcName);
            // Literal "new visitor" lore is not the readable source's served-history predicate.
            lastHistory=history.observe(visitorName);
            boolean isNew = lastHistory.newness()
                    == dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Newness.NEW;
            long reward = VisitorLoreReader.parseCoinReward(acceptLore).orElse(-1L);
            return Optional.of(new VisitorOffer(visitorName, rarity, isNew, items, reward));
        } catch (Throwable t) {
            // Never let a UI/mapping hiccup crash the client; just report "no visitor menu".
            return Optional.empty();
        }
    }

    /** Read an item's lore lines, color-stripped. Empty when the item has no lore / on any error. */
    private static List<String> readLore(ItemStack stack) {
        try {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (Component line : lore.lines()) {
                out.add(LocationText.stripColorCodes(line.getString()));
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Filler panes / decorative slots have blank or single-glyph names. */
    private static boolean isFiller(String name) {
        return name.isBlank() || name.equals("?") || name.equals("-");
    }
}
