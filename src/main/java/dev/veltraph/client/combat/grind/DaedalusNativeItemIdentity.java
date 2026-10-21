package dev.veltraph.client.combat.grind;


public final class DaedalusNativeItemIdentity {
    private DaedalusNativeItemIdentity() {}
    public static boolean modernFinishingTool(String material,String category,String id) {
        return "GOLD_SWORD".equals(material) && "SWORD".equals(category)
                && ("DAEDALUS_AXE".equals(id) || "STARRED_DAEDALUS_AXE".equals(id));
    }
}
