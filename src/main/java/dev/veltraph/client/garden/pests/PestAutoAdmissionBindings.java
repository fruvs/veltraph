package dev.veltraph.client.garden.pests;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Explicit observed adapter contract. The recovered native parent seed/reset is opaque: no public
 * counter, flag, generation label or initial value is supplied by this contract's implementation.
 */
public record PestAutoAdmissionBindings(Target target,int plot,PestAutoAdmissionSignals.Bindings nativeTotal,
        Counter excludedEntities,Counter excludedPlots,Counter scheduledTotal,Counter completed,
        Flag sourceBlocked,Marker batchGeneration) {
    public enum Target { TRACKER, CURRENT_PLOT }
    public enum Source { TAB, SIDEBAR }
    /** The whole suffix after this exact prefix is a signed integer; no trimming or token borrowing. */
    public record Counter(Source source,String prefix) { public Counter { Objects.requireNonNull(source);exact(prefix); } }
    public record Flag(Source source,String trueRow,String falseRow) {
        public Flag { Objects.requireNonNull(source);exact(trueRow);exact(falseRow);if(trueRow.equals(falseRow))throw new IllegalArgumentException("Independent blocked flag values required"); }
    }
    /** Nonempty whole suffix is a unique observed batch identity, never an inferred initial zero. */
    public record Marker(Source source,String prefix) { public Marker { Objects.requireNonNull(source);exact(prefix); } }
    private record Witness(String role,String prefix) {}
    public PestAutoAdmissionBindings {
        Objects.requireNonNull(target);Objects.requireNonNull(nativeTotal);Objects.requireNonNull(excludedEntities);
        Objects.requireNonNull(excludedPlots);Objects.requireNonNull(scheduledTotal);Objects.requireNonNull(completed);
        Objects.requireNonNull(sourceBlocked);Objects.requireNonNull(batchGeneration);
        if(plot<1||plot>24)throw new IllegalArgumentException("A specific source pest plot is required");
        exact(nativeTotal.tabPrefix());exact(nativeTotal.sidebarPrefix());
        var witnesses=new ArrayList<Witness>();
        witnesses.add(new Witness("nativeTotal",nativeTotal.tabPrefix()));witnesses.add(new Witness("nativeTotal",nativeTotal.sidebarPrefix()));
        int role=0;for(var counter:new Counter[]{excludedEntities,excludedPlots,scheduledTotal,completed})witnesses.add(new Witness("counter"+role++,counter.prefix()));
        witnesses.add(new Witness("flagTrue",sourceBlocked.trueRow()));witnesses.add(new Witness("flagFalse",sourceBlocked.falseRow()));
        witnesses.add(new Witness("generation",batchGeneration.prefix()));
        for(int i=0;i<witnesses.size();i++)for(int j=i+1;j<witnesses.size();j++){
            var first=witnesses.get(i);var second=witnesses.get(j);
            // Mirroring one census widget into TAB and SIDEBAR does not make two independent roles.
            if(!first.role().equals(second.role())&&(first.prefix().startsWith(second.prefix())||second.prefix().startsWith(first.prefix())))
                throw new IllegalArgumentException("Independent nonoverlapping native row witnesses are required");
        }
    }
    private static void exact(String value) {
        if(value==null||value.isBlank()||value.indexOf('\n')>=0||value.indexOf('\r')>=0||value.indexOf('§')>=0)
            throw new IllegalArgumentException("Exact case-preserved color-stripped native row binding required");
    }
    /** Strict external contract parsing: Gson's permissive duplicate/unknown-field adoption is forbidden. */
    public static PestAutoAdmissionBindings read(String raw) {
        if(raw==null||raw.isBlank()||raw.length()>16_384)throw new IllegalArgumentException("Explicit bounded observed parent bindings required");
        try(var reader=new JsonReader(new StringReader(raw))){
            reader.setStrictness(Strictness.STRICT);var root=unique(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT||!root.isJsonObject())throw new IllegalArgumentException("Exactly one parent binding object required");
            var object=root.getAsJsonObject();keys(object,"target","plot","nativeTotal","excludedEntities","excludedPlots","scheduledTotal","completed","sourceBlocked","batchGeneration");
            var nativeCount=object.getAsJsonObject("nativeTotal");keys(nativeCount,"tabPrefix","sidebarPrefix");
            var flag=object.getAsJsonObject("sourceBlocked");keys(flag,"source","trueRow","falseRow");
            var marker=object.getAsJsonObject("batchGeneration");keys(marker,"source","prefix");
            return new PestAutoAdmissionBindings(Target.valueOf(string(object,"target")),number(object,"plot"),
                new PestAutoAdmissionSignals.Bindings(string(nativeCount,"tabPrefix"),string(nativeCount,"sidebarPrefix")),
                counter(object,"excludedEntities"),counter(object,"excludedPlots"),counter(object,"scheduledTotal"),counter(object,"completed"),
                new Flag(Source.valueOf(string(flag,"source")),string(flag,"trueRow"),string(flag,"falseRow")),new Marker(Source.valueOf(string(marker,"source")),string(marker,"prefix")));
        }catch(IOException|RuntimeException invalid){throw new IllegalArgumentException("Invalid observed parent bindings: "+invalid.getMessage(),invalid);}
    }
    private static Counter counter(JsonObject root,String field){var object=root.getAsJsonObject(field);keys(object,"source","prefix");return new Counter(Source.valueOf(string(object,"source")),string(object,"prefix"));}
    private static void keys(JsonObject object,String... allowed){if(object==null||!object.keySet().equals(Set.of(allowed)))throw new IllegalArgumentException("Exact required parent binding fields must be present");}
    private static String string(JsonObject object,String field){var value=object.get(field);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("String binding required: "+field);return value.getAsString();}
    private static int number(JsonObject object,String field){var value=object.get(field);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Integer binding required: "+field);return value.getAsBigDecimal().intValueExact();}
    private static JsonElement unique(JsonReader reader,int depth)throws IOException {
        if(depth>4)throw new IllegalArgumentException("Parent binding nesting exceeds its explicit schema");
        return switch(reader.peek()){
            case BEGIN_OBJECT->{var result=new JsonObject();reader.beginObject();while(reader.hasNext()){String name=reader.nextName();if(result.has(name))throw new IllegalArgumentException("Duplicate parent binding field: "+name);result.add(name,unique(reader,depth+1));}reader.endObject();yield result;}
            case STRING->new JsonPrimitive(reader.nextString());
            case NUMBER->new JsonPrimitive(new BigDecimal(reader.nextString()));
            default->throw new IllegalArgumentException("Only explicit object/string/numeric parent bindings are supported");
        };
    }
}
