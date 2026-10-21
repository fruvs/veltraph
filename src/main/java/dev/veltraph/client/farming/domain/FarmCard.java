package dev.veltraph.client.farming.domain;













public enum FarmCard {
    WART_CROPS("Wart/Crops"),
    COCOA("Cocoa"),
    PUMPKIN_MELON("Pumpkin/Melon"),
    SUGARCANE_FLOWERS("Sugarcane/Flowers"),
    MUSHROOM("Mushroom"),
    CACTUS("Cactus");

    private final String displayName;

    FarmCard(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
