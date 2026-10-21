package dev.veltraph.client.combat.slayer;

import java.util.Locale;

/** Observed client-visible phase labels. Family and spatial association are required by the adapter. */
public final class SlayerSignals {
    public enum Family { REVENANT, SPIDER, WOLF, ENDERMAN, BLAZE, VAMPIRE, OTHER }
    public enum Response { NONE, RETREAT, ESCAPE, DODGE, SAFE_FLOOR, GLYPH, LOOK, EGGS, PILLAR, ADDS, IMMUNE, ATTUNEMENT, ICHOR, SPRING, HITSHIELD, FINISH }
    public static boolean cryptBoss(String label) {
        String n=clean(label);
        return n.contains("primordial brood")||n.contains("conjoined brood");
    }
    public static Family family(String label) {
        String n=clean(label);
        if(n.contains("revenant horror")||n.contains("atoned horror"))return Family.REVENANT;
        if(n.contains("broodfather")||n.contains("broodmother")||n.contains("conjoined brood")||n.contains("primordial brood"))return Family.SPIDER;
        if(n.contains("sven packmaster"))return Family.WOLF;
        if(n.contains("voidgloom seraph"))return Family.ENDERMAN;
        if(n.contains("inferno demonlord")||n.contains("quazii")||n.contains("typhoeus"))return Family.BLAZE;
        if(n.matches(".*(?<![a-z])bloodfiend(?![a-z]).*"))return Family.VAMPIRE;
        return Family.OTHER;
    }
    public static Response response(Family family,String label) {
        String n=clean(label);
        return switch(family) {
            case REVENANT -> n.contains("boom!")||n.contains("thermonuclear")||n.contains("enraged")?Response.RETREAT:Response.NONE;
            case SPIDER -> n.equals("shoot me!")?Response.EGGS:n.contains("primordial bat")?Response.ADDS:Response.NONE;
            case WOLF -> n.contains("calling the pups!")?Response.IMMUNE:n.contains("sven pup")?Response.ADDS:Response.NONE;
            case ENDERMAN -> n.matches("\\d+ hits?")?Response.HITSHIELD:n.contains("yang glyph")?Response.GLYPH:n.contains("nukekubi")?Response.LOOK:Response.NONE;
            case BLAZE -> n.matches(".*\\b(ashen|spirit|crystal|auric)\\b.*")?Response.ATTUNEMENT:n.matches("\\d+s \\d+ hits")?Response.PILLAR:n.matches(".*\\bimmune\\b.*")?Response.IMMUNE:Response.NONE;
            case VAMPIRE -> n.contains("mania")?Response.SAFE_FLOOR:n.contains("twinclaws")?Response.DODGE:n.contains("clotgoyle")?Response.ADDS:Response.NONE;
            case OTHER -> Response.NONE;
        };
    }
    private static String clean(String text) { return text==null?"":text.replaceAll("§.","").strip().toLowerCase(Locale.ROOT); }
}
