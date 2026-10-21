package dev.veltraph.client.navigation;

/** Passive scalar clock evidence. These are ORIGINAL clock samples, not new timestamps at
 * copy/creation boundaries. Initial-journal and proof-creation fields identify the last available
 * sample already observed when the original operation returned/constructor ran. Null means skipped.
 * Raw deadline timestamps share the original System.nanoTime source; no time grants authority. */
public record GroundPublicationObservedOriginalClocks(Long originalScopeBeganNanos,
        Long firstOriginalQueryPreElapsedNanos,Long lastOriginalQueryPreElapsedNanos,Long lastOriginalQueryPostElapsedNanos,
        Long lastOriginalAvailableAtInitialJournalReturnElapsedNanos,Long lastOriginalAvailableAtProofCreationElapsedNanos,
        Long firstOriginalFinalPublicationAvailableElapsedNanos,
        Long originalAfterPublicationContextDeadlineNanoTime,Long originalAfterPublicationFrameDeadlineNanoTime,
        Long originalBeforeFinalSelectedProofDeadlineNanoTime,Long originalAfterFinalSelectedProofDeadlineNanoTime,
        Long originalBeforeObserverCommitDeadlineNanoTime) {}
