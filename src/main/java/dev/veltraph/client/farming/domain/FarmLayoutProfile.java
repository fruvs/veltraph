package dev.veltraph.client.farming.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A selectable farming layout/profile: how one card+mode maps to a crop and movement behavior, plus
 * which config keys drive it and any recovery caveat.
 *
 * <p>Field set follows the goal spec: id, display name, card, crop type, movement mode,
 * source/recovery status, config keys used, and a note/caveat. {@code configKeys} holds
 * {@link dev.veltraph.client.config.FarmingConfig} key names.
 */
public record FarmLayoutProfile(
        String id,
        String displayName,
        FarmCard card,
        CropType cropType,
        FarmMovementMode movementMode,
        RecoveryStatus recoveryStatus,
        List<String> configKeys,
        String note) {

    public FarmLayoutProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(cropType, "cropType");
        Objects.requireNonNull(movementMode, "movementMode");
        Objects.requireNonNull(recoveryStatus, "recoveryStatus");
        configKeys = configKeys == null ? List.of() : List.copyOf(configKeys);
        note = note == null ? "" : note;
    }

    public boolean usesConfigKey(String configKeyName) {
        return configKeys.contains(configKeyName);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Small fluent builder so the registry definitions read clearly. */
    public static final class Builder {
        private final String id;
        private String displayName;
        private FarmCard card;
        private CropType cropType;
        private FarmMovementMode movementMode;
        private RecoveryStatus recoveryStatus = RecoveryStatus.INFERRED;
        private final List<String> configKeys = new ArrayList<>();
        private String note = "";

        private Builder(String id) {
            this.id = id;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder card(FarmCard card) {
            this.card = card;
            return this;
        }

        public Builder cropType(CropType cropType) {
            this.cropType = cropType;
            return this;
        }

        public Builder movementMode(FarmMovementMode movementMode) {
            this.movementMode = movementMode;
            return this;
        }

        public Builder recoveryStatus(RecoveryStatus recoveryStatus) {
            this.recoveryStatus = recoveryStatus;
            return this;
        }

        public Builder configKeys(String... keys) {
            for (String k : keys) {
                this.configKeys.add(k);
            }
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public FarmLayoutProfile build() {
            return new FarmLayoutProfile(
                    id, displayName, card, cropType, movementMode, recoveryStatus, configKeys, note);
        }
    }
}
