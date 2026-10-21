package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import dev.veltraph.client.garden.pests.philip.SourceContactCommand;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import java.util.*;

/** Current-source direct contact command ingress. No unrecovered command/name/title/marker defaults. */
public final class GeorgeContactProfile {
    private GeorgeContactProfile() {}
    public static SourceContactCommand.Settings parse(String json){
        var o=JsonParser.parseString(json).getAsJsonObject();if(!o.keySet().equals(Set.of("prefix","contact","title","marker","receipt","rejections","timeoutTicks")))throw new IllegalArgumentException("Exact George contact profile fields required");
        var marker=o.getAsJsonObject("marker");if(!marker.keySet().equals(Set.of("registryId","name","components","count")))throw new IllegalArgumentException("Exact native George marker identity required");
        var item=new AbiphoneCall.Item(string(marker,"registryId"),string(marker,"name"),integer(marker,"count"),string(marker,"components"));
        if(item.count()!=1||!item.registryId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||item.name().isBlank()||item.components().isBlank())throw new IllegalArgumentException("One exact full-component George marker required");
        var rejects=new ArrayList<String>();for(var entry:o.getAsJsonArray("rejections")){if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Literal George rejection required");rejects.add(entry.getAsString());}
        if(rejects.size()>8)throw new IllegalArgumentException("Bounded George rejection list required");
        return new SourceContactCommand.Settings(string(o,"prefix"),string(o,"contact"),string(o,"title"),item,string(o,"receipt"),rejects,integer(o,"timeoutTicks"));
    }
    private static String string(JsonObject o,String key){var value=o.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Required literal "+key);return value.getAsString();}
    private static int integer(JsonObject o,String key){var value=o.get(key);if(value==null||!value.isJsonPrimitive()||value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Required integer "+key);return new java.math.BigDecimal(value.getAsString()).intValueExact();}
}
