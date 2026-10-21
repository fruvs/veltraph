package dev.veltraph.client.hydra.response;


































public enum HydraReaction {
    /** Trapped/encased in blocks ("caged"): jump to try to break free + reposition. */
    CAGE,
    /** A barrier/block placed in the path: back off and step around it. */
    BARRIER,
    /** The floor changed under you (block swap / drop): step back to stable ground and hop. */
    FLOOR_CHANGE,
    /** Pushed/rubber-banded (lagback): back away from where you were pushed. */
    BACKUP
}
