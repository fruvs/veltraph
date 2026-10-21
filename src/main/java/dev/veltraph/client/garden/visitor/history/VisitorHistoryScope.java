package dev.veltraph.client.garden.visitor.history;

import java.util.Objects;
import java.util.UUID;

/** Persisted identity only. A farm layout name, account display name or world seed is not a profile. */
public record VisitorHistoryScope(UUID accountId, String confirmedProfileId, String confirmedGardenId) {
    public VisitorHistoryScope {
        Objects.requireNonNull(accountId);
        confirmedProfileId = identity(confirmedProfileId);
        confirmedGardenId = identity(confirmedGardenId);
    }
    private static String identity(String s) {
        if (s == null || s.isBlank() || s.length() > 160 || s.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Unknown visitor history scope");
        return s; // Exact confirmed server identity; no case folding across profiles.
    }
}
