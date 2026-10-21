package dev.veltraph.client.fishing.offense;

/** Caller-declared shallow supported-water contract. This never broadens dry navigation. */
public enum FishingReturnMedium {
    WAIT, DRY_GROUND, SUPPORTED_WATER;
    public static FishingReturnMedium select(boolean waterDeclared,boolean available,boolean onGround,
            boolean lava,boolean submerged,double verticalSpeed,double horizontalSpeed,
            boolean dryCurrent,boolean dryDestination,boolean supportedWaterCurrent,boolean supportedWaterDestination,
            boolean nativeWaterOccupancy) {
        if(!available||!onGround||lava||!Double.isFinite(verticalSpeed)||!Double.isFinite(horizontalSpeed)
                ||Math.abs(verticalSpeed)>=.08||horizontalSpeed<0||horizontalSpeed>=.05)return WAIT;
        if(!nativeWaterOccupancy&&dryCurrent&&dryDestination)return DRY_GROUND;
        if(waterDeclared&&!submerged&&nativeWaterOccupancy&&supportedWaterCurrent&&supportedWaterDestination)return SUPPORTED_WATER;
        return WAIT;
    }
}
