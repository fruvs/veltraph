package dev.veltraph.client.galatea;

import com.google.gson.JsonParser;
import java.util.*;

/** Explicit native region-line mapping while the source area parser remains unbound. */
public record FigSourceRegionBinding(String line,Region region) {
    public enum Region{OTHER_AREA,MOONGLADE_MARSH,UNBOUND}
    public FigSourceRegionBinding {
        if(line==null||line.isBlank()||region==null||region==Region.UNBOUND)throw new IllegalArgumentException("Observed Fig region line and source area mapping required");
    }
    public static FigSourceRegionBinding parse(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();
        if(!o.keySet().equals(Set.of("schema","line","region"))||!o.get("schema").isJsonPrimitive()
                ||!o.getAsJsonPrimitive("schema").isNumber()||!o.get("schema").getAsString().equals("1"))
            throw new IllegalArgumentException("Observed Fig region binding schema1 required");
        for(String key:List.of("line","region"))if(!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Fig region binding strings required");
        return new FigSourceRegionBinding(o.get("line").getAsString(),Region.valueOf(o.get("region").getAsString()));
    }
    public Region requireCurrent(List<String> currentCleanNativeLines) {
        if(!currentCleanNativeLines.contains(line))throw new IllegalStateException("Observed Fig region line changed or unavailable");
        return region;
    }
    /** Direct enum captions recovered from74 validated original entries; no guessed location words. */
    public static Region observed(List<String> currentCleanNativeLines) {
        Set<Region> regions=new HashSet<>();
        for(String line:currentCleanNativeLines) {
            String text=line.strip();
            if(text.startsWith("⏣ "))text=text.substring(2).strip();
            if(text.startsWith("Area: "))text=text.substring(6).strip();
            if(text.equals("Moonglade Marsh"))regions.add(Region.MOONGLADE_MARSH);
            if(Set.of("Backwater Bayou","Lotus Atoll","Torrhus Canyon").contains(text))regions.add(Region.OTHER_AREA);
        }
        return regions.size()==1?regions.iterator().next():Region.UNBOUND;
    }
}
