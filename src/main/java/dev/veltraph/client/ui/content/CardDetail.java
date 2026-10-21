package dev.veltraph.client.ui.content;

import java.util.List;

/**
 * Pure, Minecraft-free structured detail for one Automations {@link CardModel}, surfaced in the card's
 * expandable detail drawer (the "Details" toggle on {@code AutomationsScreen}). Everything here is
 * <i>static</i> recovered/source information; the card's <i>live</i> runtime state (current macro state,
 * last transition, diagnostics line) is read from the running module by the screen at render time, so it
 * is intentionally not stored on this pure model.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #stateMachine} — the recovered step/state sequence (e.g. the FSM the macro runs), one
 *       entry per step;</li>
 *   <li>{@link #requirements} — what must hold for the automation to run (area gate, tool, on-ground,
 *       etc.);</li>
 *   <li>{@link #sources} — the report/spec files the behavior was recovered from (citations).</li>
 * </ul>
 *
 * <p>All lists are defensively copied and never null; {@link #isEmpty()} is true when there is nothing
 * worth a detail drawer.
 */
public record CardDetail(List<String> stateMachine, List<String> requirements, List<String> sources) {

    public CardDetail {
        stateMachine = stateMachine == null ? List.of() : List.copyOf(stateMachine);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public boolean isEmpty() {
        return stateMachine.isEmpty() && requirements.isEmpty() && sources.isEmpty();
    }

    public static CardDetail of(List<String> stateMachine, List<String> requirements, List<String> sources) {
        return new CardDetail(stateMachine, requirements, sources);
    }
}
