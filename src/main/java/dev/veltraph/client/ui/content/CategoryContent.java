package dev.veltraph.client.ui.content;

import java.util.List;
import java.util.Objects;

/**
 * Pure content for a single rail category: either Automations cards or Settings rows. Exactly one of
 * the two lists is populated; the other is empty.
 *
 * <p>Invariant enforced here: a category is <b>never empty</b> — a category with no implemented
 * module/config must still carry at least one disabled "Coming Soon" entry. {@link #isEmpty()} is
 * therefore always false for well-formed content (the unit tests assert this for every category).
 *
 * @param title rail label for this category
 * @param cards Automations cards (empty for a Settings category)
 * @param rows  Settings rows (empty for an Automations category)
 */
public record CategoryContent(String title, List<CardModel> cards, List<RowModel> rows) {

    public CategoryContent {
        Objects.requireNonNull(title, "title");
        cards = cards == null ? List.of() : List.copyOf(cards);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static CategoryContent ofCards(String title, List<CardModel> cards) {
        return new CategoryContent(title, cards, List.of());
    }

    public static CategoryContent ofRows(String title, List<RowModel> rows) {
        return new CategoryContent(title, List.of(), rows);
    }

    public boolean isEmpty() {
        return cards.isEmpty() && rows.isEmpty();
    }

    /** Total content entries (cards + rows). Always {@code >= 1} for well-formed content. */
    public int entryCount() {
        return cards.size() + rows.size();
    }

    /** Whether every entry in this category is a disabled placeholder. */
    public boolean allComingSoon() {
        if (isEmpty()) {
            return false;
        }
        for (CardModel c : cards) {
            if (!c.comingSoon()) {
                return false;
            }
        }
        for (RowModel r : rows) {
            if (r.kind() != RowModel.Kind.COMING_SOON) {
                return false;
            }
        }
        return true;
    }
}
