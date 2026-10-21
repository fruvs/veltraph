package dev.veltraph.client.farming.domain;

/**
 * The 4-value crop selector exposed by the Wart/Crops card. Recovered as a distinct enum from the
 * 17-value master {@link CropType} (see {@code FarmingUiLayoutSelection.md} §2): declaration order
 * equals dropdown order. Each value maps to a master {@link CropType} run target.
 *
 * <p>Keeping this separate from {@link CropType} preserves the recovered "two enums, do not conflate"
 * distinction.
 */
public enum WartVariety {
    NETHER_WART("Nether Wart", CropType.NETHER_WART),
    POTATO("Potato", CropType.POTATO),
    CARROT("Carrot", CropType.CARROT),
    WHEAT("Wheat", CropType.WHEAT);

    private final String displayName;
    private final CropType cropType;

    WartVariety(String displayName, CropType cropType) {
        this.displayName = displayName;
        this.cropType = cropType;
    }

    public String displayName() {
        return displayName;
    }

    /** The master run target this variety selects. */
    public CropType cropType() {
        return cropType;
    }
}
