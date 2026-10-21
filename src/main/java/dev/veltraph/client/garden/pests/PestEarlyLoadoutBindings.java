package dev.veltraph.client.garden.pests;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Current observed timer/spawn adapter; empty defaults cannot establish gear timing. */
public record PestEarlyLoadoutBindings(PestEarlyLoadoutPolicy.Bindings timer,PestEquipmentCooldown.Bindings equipment) {
    public PestEarlyLoadoutBindings {Objects.requireNonNull(timer);Objects.requireNonNull(equipment);}
    public static PestEarlyLoadoutBindings read(String text){
        if(text==null||text.isBlank()||text.length()>4096)throw new IllegalArgumentException("Explicit bounded observed early loadout bindings required");
        try(var reader=new JsonReader(new StringReader(text))){reader.setStrictness(Strictness.STRICT);var value=unique(reader,0);if(reader.peek()!=JsonToken.END_DOCUMENT||!value.isJsonObject())throw new IllegalArgumentException("One binding object required");
            var o=value.getAsJsonObject();if(!o.keySet().equals(Set.of("timerPrefix","readyRow","maximumRow","spawnPrefixes","cooldownSeconds")))throw new IllegalArgumentException("Exact timer/spawn binding fields required");
            var array=o.getAsJsonArray("spawnPrefixes");if(array==null||array.size()!=3)throw new IllegalArgumentException("Three observed prefixes required");var prefixes=new ArrayList<String>();for(var entry:array)prefixes.add(string(entry));
            var n=o.get("cooldownSeconds");if(n==null||!n.isJsonPrimitive()||!n.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Exact configured cooldown required");
            return new PestEarlyLoadoutBindings(new PestEarlyLoadoutPolicy.Bindings(string(o.get("timerPrefix")),string(o.get("readyRow")),string(o.get("maximumRow"))),new PestEquipmentCooldown.Bindings(prefixes,n.getAsBigDecimal().intValueExact()));
        }catch(IOException|RuntimeException invalid){throw new IllegalArgumentException("Invalid observed early loadout bindings",invalid);}
    }
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Exact string required");return e.getAsString();}
    private static JsonElement unique(JsonReader reader,int depth)throws IOException{
        if(depth>2)throw new IllegalArgumentException("Binding depth exceeds schema");return switch(reader.peek()){
            case BEGIN_OBJECT->{var o=new JsonObject();reader.beginObject();while(reader.hasNext()){String key=reader.nextName();if(o.has(key))throw new IllegalArgumentException("Duplicate field");o.add(key,unique(reader,depth+1));}reader.endObject();yield o;}
            case BEGIN_ARRAY->{var a=new JsonArray();reader.beginArray();while(reader.hasNext()){if(a.size()>=3)throw new IllegalArgumentException("Array exceeds schema");a.add(unique(reader,depth+1));}reader.endArray();yield a;}
            case STRING->new JsonPrimitive(reader.nextString());case NUMBER->new JsonPrimitive(new BigDecimal(reader.nextString()));
            default->throw new IllegalArgumentException("Unknown binding value");};
    }
}
