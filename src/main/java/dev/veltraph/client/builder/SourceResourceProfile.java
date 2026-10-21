package dev.veltraph.client.builder;

import com.google.gson.*;
import java.util.*;

/** Values absent/salted in the source are configured explicitly; dispatch and random choice are source policy. */
record SourceResourceProfile(SourceResourcePolicy.Mode mode,SourceResourcePolicy.Order order,long moveCooldown,
                             long removeCooldown,long clickMin,long clickMax,long timeout,long acknowledgementTimeout,
                             String inventoryTitle,Item packed,List<Item> otherResources,Map<String,String> providerFlows,
                             String sellFlow) {
    record Item(String id,String name) {
        Item {if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||name.length()>256)throw new IllegalArgumentException("Explicit source item identity required");}
    }
    SourceResourceProfile {
        otherResources=List.copyOf(otherResources);providerFlows=Map.copyOf(providerFlows);
        if(inventoryTitle.isBlank()||inventoryTitle.length()>256||acknowledgementTimeout<1||acknowledgementTimeout>30000)throw new IllegalArgumentException("Inventory/acknowledgement contracts required");
    }
    static Optional<SourceResourceProfile> decode(String bindingJson) {
        if(bindingJson==null||bindingJson.isBlank())return Optional.empty();
        var root=JsonParser.parseString(bindingJson).getAsJsonObject();if(!root.has("sourcePolicy"))return Optional.empty();
        var p=root.getAsJsonObject("sourcePolicy");
        var providers=new LinkedHashMap<String,String>();
        if(p.has("providerFlows"))for(var e:p.getAsJsonObject("providerFlows").entrySet()) {
            SourceResourcePolicy.Mode.valueOf(e.getKey());providers.put(e.getKey(),e.getValue().toString());
        }
        var others=new ArrayList<Item>();for(var e:p.getAsJsonArray("otherResources"))others.add(item(e.getAsJsonObject()));
        return Optional.of(new SourceResourceProfile(SourceResourcePolicy.Mode.valueOf(text(p,"mode")),SourceResourcePolicy.Order.valueOf(text(p,"slotOrder")),
                number(p,"moveCooldownMillis"),number(p,"removeCooldownMillis"),number(p,"clickDelayMinMillis"),number(p,"clickDelayMaxMillis"),number(p,"timeoutMillis"),number(p,"acknowledgementTimeoutMillis"),
                text(p,"inventoryTitle"),p.has("packedItem")?item(p.getAsJsonObject("packedItem")):null,others,providers,p.has("sellOtherFlow")?p.get("sellOtherFlow").toString():""));
    }
    private static Item item(JsonObject p){return new Item(text(p,"id"),p.has("name")?p.get("name").getAsString():"");}
    private static String text(JsonObject p,String key){if(!p.has(key)||p.get(key).isJsonNull()||p.get(key).getAsString().isBlank())throw new IllegalArgumentException("Blank source policy contract "+key);return p.get(key).getAsString();}
    private static long number(JsonObject p,String key){if(!p.has(key)||!p.get(key).isJsonPrimitive()||!p.get(key).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Unresolved salted timing contract "+key);var n=p.get(key).getAsBigDecimal();return n.longValueExact();}
    String purchaseFlow(int deficit) {
        String raw=providerFlows.get(mode.name());if(raw==null||raw.isBlank())throw new IllegalArgumentException("Blank source provider flow for "+mode);
        // The helper delegates exactly required-minus-observed-tool-stock, never the original batch again.
        return instantiate(raw,deficit);
    }
    static String instantiate(String raw,int requested) {
        if(requested<1||requested>50000)throw new IllegalArgumentException("Invalid source purchase deficit");
        var a=JsonParser.parseString(raw).getAsJsonArray();
        for(var e:a) {var p=e.getAsJsonObject();if(p.has("receiptDelta")&&p.get("receiptDelta").isJsonPrimitive()&&p.get("receiptDelta").getAsJsonPrimitive().isString()&&p.get("receiptDelta").getAsString().equals("requested"))p.addProperty("receiptDelta",requested);
            if(p.has("label"))p.addProperty("label",p.get("label").getAsString().replace("{requested}",String.valueOf(requested)));}
        return a.toString();
    }
}
