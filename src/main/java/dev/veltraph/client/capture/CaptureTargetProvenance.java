package dev.veltraph.client.capture;

import com.google.gson.JsonObject;

/** Bounded temporal evidence only. No menu is declared causally bound to a world target. */
public final class CaptureTargetProvenance {
    public static final long MAX_ACTION_TO_MENU_MILLIS = 3000;
    private long sequence;
    private JsonObject candidate, menuEvidence = unknown("no_menu_boundary_observed");
    private long actionAt;
    private int actions;
    private String invalid = "no_action_observed";

    public JsonObject begin(String kind, JsonObject target, JsonObject context, long now, boolean screenFree) {
        if (actions > 0 && (now < actionAt || now - actionAt > MAX_ACTION_TO_MENU_MILLIS)) reset("previous_action_expired");
        actions++;
        if (actions > 1) invalid = "multiple_actions_before_menu";
        else invalid = screenFree ? "action_result_pending" : "action_while_screen_open";
        var event = new JsonObject();
        event.addProperty("eventKind", "native_action_begin");
        event.addProperty("actionSequence", ++sequence);
        event.addProperty("actionKind", kind);
        event.addProperty("observedAtEpochMillis", now);
        event.addProperty("callerAttribution", "unknown_native_api_caller");
        event.add("context", context.deepCopy());
        event.add("target", target.deepCopy());
        if (actions == 1) { candidate = event.deepCopy(); actionAt = now; }
        return event;
    }
    public JsonObject finish(long actionSequence, Boolean consumed, long now) {
        var event = new JsonObject();
        event.addProperty("eventKind", "native_action_return");
        event.addProperty("actionSequence", actionSequence);
        event.addProperty("observedAtEpochMillis", now);
        event.addProperty("localResult", consumed == null ? "no_result_available" : consumed ? "consumes_action" : "does_not_consume_action");
        event.addProperty("serverOutcome", "unknown");
        if (candidate != null && candidate.get("actionSequence").getAsLong() == actionSequence && actions == 1 && invalid.equals("action_result_pending"))
            invalid = Boolean.TRUE.equals(consumed) ? "single_consumed_action" : "action_not_consumed_or_no_result";
        return event;
    }
    public void reset(String reason) { candidate = null; actions = 0; invalid = reason; }
    public JsonObject menuInput(long now) {
        reset("container_input_before_world_menu"); menuEvidence = unknown(invalid);
        var event = menuEvidence.deepCopy(); event.addProperty("eventKind", "native_container_input"); event.addProperty("observedAtEpochMillis", now); return event;
    }
    public void contextChanged() { reset("player_world_or_connection_changed"); menuEvidence = unknown(invalid); }
    public JsonObject screenBoundary(boolean nativeMenu, JsonObject context, long now) {
        String reason = invalid;
        if (!nativeMenu) reason = "screen_closed_or_non_container";
        else if (candidate != null && (now < actionAt || now - actionAt > MAX_ACTION_TO_MENU_MILLIS)) reason = "action_to_menu_timeout";
        else if (candidate != null && !candidate.getAsJsonObject("context").equals(context)) reason = "context_changed_before_menu";
        else if (candidate != null && !knownTarget(candidate.getAsJsonObject("target"))) reason = "action_has_no_exact_world_target";
        menuEvidence = unknown(reason);
        menuEvidence.addProperty("eventKind", "screen_boundary");
        menuEvidence.addProperty("observedAtEpochMillis", now);
        menuEvidence.addProperty("nativeContainerMenu", nativeMenu);
        menuEvidence.add("context", context.deepCopy());
        if (nativeMenu && reason.equals("single_consumed_action") && actions == 1) {
            menuEvidence.addProperty("temporalEvidence", "single_consumed_action_before_menu");
            menuEvidence.add("candidateAction", candidate.deepCopy());
            menuEvidence.addProperty("associationNote", "Temporal candidate only; local consumption and menu opening do not prove server causality, plant identity, or successful care.");
        }
        reset("menu_navigation_or_no_new_world_action");
        return menuEvidence.deepCopy();
    }
    public JsonObject menuEvidence() { return menuEvidence.deepCopy(); }
    private static boolean knownTarget(JsonObject target) {
        String type = target.has("kind") ? target.get("kind").getAsString() : "unknown";
        return type.equals("block") || type.equals("entity");
    }
    private static JsonObject unknown(String reason) {
        var result = new JsonObject();
        result.addProperty("causalTargetBinding", "unknown");
        result.addProperty("reason", reason);
        result.addProperty("temporalEvidence", "unknown");
        return result;
    }
}
