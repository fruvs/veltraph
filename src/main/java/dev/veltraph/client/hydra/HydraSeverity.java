package dev.veltraph.client.hydra;










public enum HydraSeverity {
    /** Informational: a watcher event that is not, by itself, a proximity threat (e.g. a routing line). */
    STAFF_CHECK,
    /** A non-whitelisted player is within the configured alert distance but not dangerously close. */
    POSSIBLE,
    /** A non-whitelisted player is dangerously close, or a confirmed hostile server action occurred. */
    DANGEROUS
}
