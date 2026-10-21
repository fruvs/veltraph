package dev.veltraph.client.farming.domain;





public enum RecoveryStatus {
    /** Behavior confirmed from decompiled/bytecode source. */
    CONFIRMED("Confirmed from source/bytecode"),
    /** Behavior fully understood and ready for a clean deterministic rebuild (not byte-faithful). */
    READY("Ready for clean rebuild"),
    /** Behavior inferred from surrounding evidence; not directly confirmed. */
    INFERRED("Inferred"),
    /** Partially recovered; some details still open. */
    PARTIAL("Partially recovered"),
    /** Not present / not recoverable from the jar. */
    NOT_FOUND("Not found in source"),
    
    LOCAL_CUSTOM("Local/custom (not original)");

    private final String description;

    RecoveryStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
