package dev.veltraph.client.combat.grind;

import java.util.List;
import java.util.Objects;
import dev.veltraph.client.service.InventoryService;

/** Passive readiness observation must still be exact when an authorized enable commits selection. */
public final class BestiaryWeaponStart {
    private final InventoryService inventory;
    private Proposal proposal;
    public record Proposal(Frame frame, BestiaryWeaponSelector.Choice choice) {}
    public BestiaryWeaponStart(InventoryService inventory) { this.inventory = Objects.requireNonNull(inventory); }
    /** Readiness stages a local proposal only, including on repeated or denied-start evaluations. */
    public void prepare(Frame frame, BestiaryWeaponSelector.Choice choice) {
        proposal = frame == null || choice == null ? null : new Proposal(frame, choice);
    }
    public Proposal proposal() { return proposal; }
    public void cancel() { proposal = null; }
    /** The sole write is deferred until the activation owner commits a still-exact observation. */
    public boolean commit(Frame current) {
        var prepared = proposal; proposal = null;
        if (prepared == null || prepared.choice().slot() < 0 || prepared.choice().slot() > 8
                || !mayCommit(prepared.frame(), current)) return false;
        inventory.selectSlot(prepared.choice().slot());
        return inventory.selectedSlot() == prepared.choice().slot();
    }
    public record Frame(Object player, Object world, Object connection, int heldSlot,
                        List<?> hotbar, String settings, String preferred, boolean idle) {
        public Frame { hotbar = hotbar == null ? List.of() : List.copyOf(hotbar); }
    }
    public static boolean mayCommit(Frame proposal, Frame current) {
        return proposal != null && current != null && proposal.idle() && current.idle()
                && proposal.player() != null && proposal.world() != null && proposal.connection() != null
                && proposal.player() == current.player() && proposal.world() == current.world()
                && proposal.connection() == current.connection()
                && proposal.heldSlot() >= 0 && proposal.heldSlot() < 9 && proposal.heldSlot() == current.heldSlot()
                && proposal.hotbar().size() == 9 && proposal.hotbar().equals(current.hotbar())
                && proposal.settings() != null && Objects.equals(proposal.settings(), current.settings())
                && proposal.preferred() != null && Objects.equals(proposal.preferred(), current.preferred());
    }
}
