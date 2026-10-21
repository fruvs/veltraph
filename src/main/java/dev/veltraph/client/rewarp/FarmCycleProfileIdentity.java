package dev.veltraph.client.rewarp;

import dev.veltraph.client.farming.module.*;
import java.util.Locale;

/** Saved normal-cycle markers belong to the actual captured design/crop, not just a grouped card. */
public final class FarmCycleProfileIdentity {
    private FarmCycleProfileIdentity() {}
    public static String of(FarmModule farm) {
        String key=farm.activeProfile().id();
        if(farm instanceof WartCropsFarmModule wart)key+="_"+wart.variety().name().toLowerCase(Locale.ROOT);
        if(farm instanceof SugarcaneFlowersFarmModule cane) {
            var selected=cane.capturedSelection();
            if(selected.design()!=SugarcaneFlowersFarmModule.Design.RECOVERED
                    ||selected.crop()!=SugarcaneFlowersFarmModule.Crop.CANE)
                key+="_"+selected.design().name().toLowerCase(Locale.ROOT)+"_"+selected.crop().name().toLowerCase(Locale.ROOT);
        }
        return key;
    }
}
