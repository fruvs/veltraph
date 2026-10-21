package dev.veltraph.client.fishing.setup;

import java.util.List;

/** Stable 4.0.30 gameplay literals recovered from inert class bytes; no original code is loaded. */
public final class SourceRodBindings {
    private SourceRodBindings() {}
    private static final RodSelectionProfile PROFILE = new RodSelectionProfile("Grappling",
            List.of(new RodSelectionProfile.Binding("Rod of the Sea", 50),
                    new RodSelectionProfile.Binding("Rod of Legends", 30),
                    new RodSelectionProfile.Binding("Rod of Shredder", 30),
                    new RodSelectionProfile.Binding("Rod of Champions", 20),
                    new RodSelectionProfile.Binding("Challenging Rod", 10),
                    new RodSelectionProfile.Binding("Speedster Rod", 5),
                    new RodSelectionProfile.Binding("Fishing Rod", 1)),
            List.of(new RodSelectionProfile.Binding("Hellfire Rod", 50),
                    new RodSelectionProfile.Binding("Inferno Rod", 30),
                    new RodSelectionProfile.Binding("Magma Rod", 20),
                    new RodSelectionProfile.Binding("Starter Lava Rod", 10),
                    new RodSelectionProfile.Binding("Speedster Rod", 3),
                    new RodSelectionProfile.Binding("Fishing Rod", 1)));
    public static RodSelectionProfile profile() { return PROFILE; }
}
