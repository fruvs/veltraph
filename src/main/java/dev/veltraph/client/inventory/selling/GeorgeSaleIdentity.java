package dev.veltraph.client.inventory.selling;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Canonical financial UUID and separately retained source identity for one actually acknowledged pet. */
public record GeorgeSaleIdentity(UUID activationUuid, UUID petUuid, String outcomeId, String sourceIdentity) {
    private static final String NAMESPACE = "dev.veltraph.george.acknowledged-pet.v1";

    public GeorgeSaleIdentity {
        Objects.requireNonNull(activationUuid);
        Objects.requireNonNull(petUuid);
        String raw = activationUuid + ":" + petUuid;
        String canonical = UUID.nameUUIDFromBytes((NAMESPACE + "\n" + raw).getBytes(StandardCharsets.UTF_8)).toString();
        if (!raw.equals(sourceIdentity) || !canonical.equals(outcomeId)) {
            throw new IllegalArgumentException("Exact namespaced acknowledged-pet identity required");
        }
    }

    public static GeorgeSaleIdentity acknowledged(String activation, UUID petUuid) {
        var epoch = UUID.fromString(activation);
        if (!epoch.toString().equals(activation)) throw new IllegalArgumentException("Canonical George activation UUID required");
        String raw = epoch + ":" + Objects.requireNonNull(petUuid);
        String id = UUID.nameUUIDFromBytes((NAMESPACE + "\n" + raw).getBytes(StandardCharsets.UTF_8)).toString();
        return new GeorgeSaleIdentity(epoch, petUuid, id, raw);
    }
}
