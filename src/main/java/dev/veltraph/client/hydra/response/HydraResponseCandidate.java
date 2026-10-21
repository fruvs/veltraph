package dev.veltraph.client.hydra.response;

















public record HydraResponseCandidate(HydraResponseKind kind, String value, int stage) {

    /** Whether this candidate's value is blank (the original filter keeps only non-empty values). */
    public boolean isBlankValue() {
        return value == null || value.isBlank();
    }

    public static HydraResponseCandidate generic(String value, int stage) {
        return new HydraResponseCandidate(HydraResponseKind.GENERIC, value, stage);
    }

    public static HydraResponseCandidate subtler(String value, int stage) {
        return new HydraResponseCandidate(HydraResponseKind.SUBTLER_GENERIC, value, stage);
    }

    /** Whether this candidate is a chat reply (a non-directional, non-empty response). */
    public boolean isReply() {
        return (kind == HydraResponseKind.GENERIC || kind == HydraResponseKind.SUBTLER_GENERIC
                || kind == HydraResponseKind.REALIGNMENT) && !isBlankValue();
    }

    public static HydraResponseCandidate look(boolean up, String value) {
        return new HydraResponseCandidate(up ? HydraResponseKind.LOOK_UP : HydraResponseKind.LOOK_DOWN, value, 0);
    }

    public static HydraResponseCandidate realignment(String value) {
        return new HydraResponseCandidate(HydraResponseKind.REALIGNMENT, value, 0);
    }
}
