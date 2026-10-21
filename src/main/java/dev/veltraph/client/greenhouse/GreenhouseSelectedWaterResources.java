package dev.veltraph.client.greenhouse;

/** Exact eligibility for a user-selected ordinary count-cost proposal. Not evidence of a server watering-tool role or effect area. */
public final class GreenhouseSelectedWaterResources {
    private GreenhouseSelectedWaterResources() {}
    public record Selection(int toolSlot,int resourceSlot,int units,String exactTool,String exactResource,int resourceBefore,int resourceAfter) {}
    public static Selection select(GreenhouseNativeInventory actual,int toolSlot,int resourceSlot,int units){
        if(actual==null||!actual.cursorEmpty()||toolSlot<0||toolSlot>8||resourceSlot<0||resourceSlot==toolSlot||units<1||units>64)
            throw new IllegalArgumentException("Exact distinct native tool/resource slots, empty cursor and bounded selected units required");
        var tool=actual.inventorySlot(toolSlot);var resource=actual.inventorySlot(resourceSlot);
        if(tool==null||resource==null||tool.empty()||resource.empty()||tool.nativeCount()<1||resource.nativeCount()<units
                ||tool.identity().equals(resource.identity())||tool.persistentEncoding().isBlank()||resource.persistentEncoding().isBlank())
            throw new IllegalArgumentException("Existing distinct full native tool/resource identities and selected supply required");
        return new Selection(toolSlot,resourceSlot,units,tool.persistentEncoding(),resource.persistentEncoding(),resource.nativeCount(),Math.subtractExact(resource.nativeCount(),units));
    }
}
