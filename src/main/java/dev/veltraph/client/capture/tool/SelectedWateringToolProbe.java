package dev.veltraph.client.capture.tool;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit manual numeric observation, never an item capability, cost model or mutable-component permission. */
public final class SelectedWateringToolProbe {
    public static final int MAX_FIELDS=8,MAX_LINES=8;
    private static final Set<String> ROLES=Set.of("AQUAMASTER_X","AQUAMASTER_HYDROMAX");
    private static final Pattern SENSITIVE=Pattern.compile("(?i).*(account|profile|owner|player|uuid|identity|identifier|credential|token|secret|password|auth|signature|session|timestamp|epoch|created|update|birth|date|time|email|address|phone|key|uid).*|(?i)(id|.*_id|ip|.*_ip|key|.*_key)");
    private static final Pattern NUMBER=Pattern.compile("[-+]?[0-9][0-9,]*(?:\\.[0-9]+)?");
    private static final Set<String> LORE_WORDS=Set.of("water","remaining","capacity","charge","charges","amount","spray","area","radius","range","rate","speed","refill","refilling","max","maximum","current","can","uses","use","per","second","seconds","sec","s","tick","ticks","blocks","block","percent","watered","crops","crop","plants","plant","hold","right","click","for","to","a","the","and","x","by","watering","waters","sprays","reserve","reserves","stored","available","total","fill","filled","refills","flow","volume","unit","units","l","ml","liters","litres","every","ms","min","minute","minutes","cost","costs","consumes");
    private static final Set<String> MECHANIC_WORDS=Set.of("water","capacity","charge","charges","spray","area","radius","range","rate","speed","refill","uses","watering","waters","reserve","reserves","flow","volume");
    private String role;private JsonObject toolToken;
    private final Set<List<String>> fields=new LinkedHashSet<>();
    private final Map<Integer,String> lines=new TreeMap<>();private Map<Integer,String> loreLayout;

    public record Preview(List<String> paths,List<Integer> loreIndices){public Preview{paths=List.copyOf(paths);loreIndices=List.copyOf(loreIndices);}}
    public static List<String> path(String text){
        if(text==null||text.length()>256)throw new IllegalArgumentException("Unsupported numeric field selection");
        var parts=List.of(text.split("\\.",-1));
        if(parts.size()<2||parts.size()>7||!parts.getFirst().equals("minecraft:custom_data"))throw new IllegalArgumentException("Unsupported numeric field selection");
        for(int i=1;i<parts.size();i++){
            String segment=parts.get(i);String normalized=segment.replaceAll("([A-Z]+)([A-Z][a-z])","$1_$2").replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            if(!SelectedToolCaptureSchema.safeField(segment)||SENSITIVE.matcher(normalized).matches()
                    ||Arrays.stream(normalized.split("[_-]")).anyMatch(s->Set.of("id","ip","user","creator","contact","serial","transaction").contains(s)))throw new IllegalArgumentException("Unsupported numeric field selection");
        }
        return parts;
    }
    private static String role(JsonObject frame){
        var ids=frame.getAsJsonObject("allowlistedServerItemIds");if(ids==null||ids.isEmpty())return null;
        var unique=new HashSet<String>();for(var value:ids.asMap().values())if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())unique.add(value.getAsString());else return null;
        return unique.size()==1&&ROLES.contains(unique.iterator().next())?unique.iterator().next():null;
    }
    private static JsonObject token(JsonObject frame){
        var tokens=frame.getAsJsonObject("sessionSaltedToolUuidDigests");if(tokens==null||tokens.isEmpty())return null;
        for(var e:tokens.entrySet())if(!Set.of("minecraft:custom_data.uuid","minecraft:custom_data.ExtraAttributes.uuid").contains(e.getKey())
            ||!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString()||!e.getValue().getAsString().matches("[0-9a-f]{64}"))return null;
        return tokens;
    }
    private void bind(JsonObject frame){
        String current=role(frame);var identity=token(frame);
        if(current==null||identity==null)throw new IllegalArgumentException("Recognized selected watering can with stable session identity required");
        if(role==null){role=current;toolToken=identity.deepCopy();}
        else if(!role.equals(current)||!toolToken.equals(identity))throw new IllegalArgumentException("Selected watering can was replaced");
    }
    public Preview preview(JsonObject frame,Map<Integer,String> indexedLore){
        bind(frame);var paths=new TreeSet<String>();var rows=frame.getAsJsonArray("componentFields");
        if(rows!=null)for(var value:rows){var row=value.getAsJsonObject();if(!row.get("type").getAsString().equals("number"))continue;String p=row.get("path").getAsString();try{path(p);paths.add(p);}catch(IllegalArgumentException refused){}}
        var eligible=new ArrayList<Integer>();for(var e:indexedLore.entrySet())if(e.getKey()>=0&&e.getKey()<SelectedToolCaptureSchema.MAX_LORE&&loreNumbers(e.getValue())!=null&&uniqueShape(indexedLore,e.getKey()))eligible.add(e.getKey());
        return new Preview(paths.stream().limit(64).toList(),eligible);
    }
    public void chooseField(JsonObject frame,Map<Integer,String> indexedLore,String exactPath){
        var parsed=path(exactPath);if(!preview(frame,indexedLore).paths().contains(exactPath))throw new IllegalArgumentException("Choose a numeric field shown in the preview");
        if(!fields.contains(parsed)&&fields.size()>=MAX_FIELDS)throw new IllegalArgumentException("Numeric field limit reached");fields.add(parsed);
    }
    public void chooseLore(JsonObject frame,Map<Integer,String> indexedLore,int nativeIndex){
        if(!preview(frame,indexedLore).loreIndices().contains(nativeIndex))throw new IllegalArgumentException("Choose a supported native lore index shown in the preview");
        if(!lines.containsKey(nativeIndex)&&lines.size()>=MAX_LINES)throw new IllegalArgumentException("Numeric lore limit reached");
        var layout=layout(indexedLore);if(loreLayout!=null&&!loreLayout.equals(layout))throw new IllegalArgumentException("Selected lore layout changed");
        loreLayout=layout;lines.put(nativeIndex,shape(indexedLore.get(nativeIndex)));
    }
    public void verifyIdentity(JsonObject frame){bind(frame);}
    public Set<List<String>> selectedFields(){return Set.copyOf(fields);}
    public boolean active(){return !fields.isEmpty()||!lines.isEmpty();}
    public void clear(){role=null;toolToken=null;fields.clear();lines.clear();loreLayout=null;}
    public JsonObject sample(JsonObject frame,JsonObject values,JsonObject nativeTypes,Map<Integer,String> indexedLore,boolean complete){
        bind(frame);if(!complete)throw new IllegalArgumentException("Selected native numeric observation is incomplete");
        var result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("observedServerItemId",role);result.addProperty("interpretation","Explicit manual numeric observations only; units, costs, causal update and care permission remain unknown");
        var selected=new JsonArray();for(var p:fields){String exact=String.join(".",p);if(!values.has(exact)||!nativeTypes.has(exact))throw new IllegalArgumentException("Selected numeric field is unavailable");var number=boundedNumber(values.get(exact));if(number==null)throw new IllegalArgumentException("Selected numeric field has unsupported value/type");String nativeType=nativeTypes.get(exact).getAsString();if(!Set.of("ByteTag","ShortTag","IntTag","LongTag","FloatTag","DoubleTag").contains(nativeType))throw new IllegalArgumentException("Selected native scalar type is unsupported");var row=new JsonObject();row.addProperty("path",exact);row.addProperty("nativeNumericType",nativeType);row.add("value",number);selected.add(row);}
        result.add("selectedNumericFields",selected);var lore=new JsonArray();
        if(!lines.isEmpty()&&!Objects.equals(loreLayout,layout(indexedLore)))throw new IllegalArgumentException("Selected native lore layout changed");
        for(var e:lines.entrySet()){String actual=indexedLore.get(e.getKey());if(actual==null||!e.getValue().equals(shape(actual))||!uniqueShape(indexedLore,e.getKey()))throw new IllegalArgumentException("Selected native lore line changed");var numbers=loreNumbers(actual);if(numbers==null)throw new IllegalArgumentException("Selected native lore is unsupported");var row=new JsonObject();row.addProperty("nativeLoreIndex",e.getKey());row.add("numericTokens",numbers);lore.add(row);}
        result.add("selectedNumericLore",lore);return result;
    }
    static JsonElement boundedNumber(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())return null;
        try{var n=new BigDecimal(value.getAsString());if(n.precision()>24||Math.abs((long)n.scale())>12)return null;return new JsonPrimitive(n);}catch(NumberFormatException invalid){return null;}
    }
    private static String clean(String text){return text==null?null:text.replaceAll("§.","");}
    private static String shape(String text){String c=clean(text);return c==null?null:NUMBER.matcher(c).replaceAll("#");}
    private static Map<Integer,String> layout(Map<Integer,String> lines){var result=new TreeMap<Integer,String>();lines.forEach((i,text)->result.put(i,shape(text)));return Collections.unmodifiableMap(result);}
    private static boolean uniqueShape(Map<Integer,String> lines,int index){String candidate=shape(lines.get(index));return candidate!=null&&lines.values().stream().filter(s->candidate.equals(shape(s))).count()==1;}
    private static JsonArray loreNumbers(String raw){
        String text=clean(raw);if(text==null||text.length()>512||text.contains("@")||text.contains("://")||SENSITIVE.matcher(text).matches()
                ||text.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")||text.matches(".*[0-9]{2,4}[-/][0-9]{1,2}[-/][0-9]{1,4}.*")||text.matches(".*[0-9]{3}[- .][0-9]{3}[- .][0-9]{4}.*"))return null;
        var matcher=NUMBER.matcher(text);var numbers=new JsonArray();while(matcher.find()){
            String token=matcher.group();if(token.contains(",")&&!token.matches("[-+]?[1-9][0-9]{0,2}(?:,[0-9]{3})+(?:\\.[0-9]+)?"))return null;
            String numeric=token.replace(",","");if(numeric.replaceAll("[^0-9]","").length()>9||numbers.size()>=8)return null;
            try{var value=boundedNumber(new JsonPrimitive(new BigDecimal(numeric)));if(value==null)return null;numbers.add(value);}catch(NumberFormatException invalid){return null;}
        }
        if(numbers.isEmpty())return null;String skeleton=shape(text);boolean mechanic=false;var words=Pattern.compile("[A-Za-z]+").matcher(skeleton);
        while(words.find()){String word=words.group().toLowerCase(Locale.ROOT);if(!LORE_WORDS.contains(word))return null;mechanic|=MECHANIC_WORDS.contains(word);}
        skeleton=skeleton.replaceAll("[➤►▶✦◆•]","");
        if(!mechanic||skeleton.matches(".*[^A-Za-z0-9#\\s:.,/()%+×*=-].*"))return null;
        return numbers;
    }
}
