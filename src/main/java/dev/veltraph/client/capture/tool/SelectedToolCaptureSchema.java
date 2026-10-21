package dev.veltraph.client.capture.tool;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/** Selected-tool observation only. Recognized counters do not authorize mutable components. */
public final class SelectedToolCaptureSchema {
    private SelectedToolCaptureSchema() {}
    public static final int MAX_FIELDS=512,MAX_DEPTH=6,MAX_LORE=80;
    private static final Set<String> COUNTERS=Set.of("farmed_cultivating","mined_crops");
    private static final Set<List<String>> UUID_PATHS=Set.of(List.of("minecraft:custom_data","uuid"),List.of("minecraft:custom_data","ExtraAttributes","uuid"));
    private static final Pattern COUNTER_LORE=Pattern.compile("^(Cultivating|Counter): ([0-9][0-9,]{0,30})(?: .*)?$");
    public static JsonObject encode(String nativeId,int selectedSlot,JsonObject components,List<String> lore,byte[] salt){
        if(nativeId==null||!nativeId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||selectedSlot<0||selectedSlot>8||salt==null||salt.length<32)throw new IllegalArgumentException("Native ID/slot/session salt required");
        var row=new JsonObject();row.addProperty("schemaVersion",1);row.addProperty("captureKind","manual_selected_farming_tool");row.addProperty("nativeItemId",nativeId);row.addProperty("selectedSlot",selectedSlot);
        var fields=new JsonArray();var counters=new JsonObject();var ids=new JsonObject();var digests=new JsonObject();int[] count={0};boolean[] truncated={false};
        for(var e:components.entrySet())if(e.getKey().matches("[a-z0-9_-]+:[a-z0-9_/-]+"))walk(e.getKey(),List.of(e.getKey()),e.getValue(),0,fields,counters,ids,digests,salt,count,truncated);else truncated[0]=true;
        var loreCounters=new JsonArray();int n=0;
        for(String line:lore){if(++n>MAX_LORE){truncated[0]=true;break;}if(line==null||line.length()>512)continue;var clean=line.replaceAll("§.","");var match=COUNTER_LORE.matcher(clean).matches()?COUNTER_LORE.matcher(clean):null;if(match!=null&&match.matches()){var value=numeric(match.group(2).replace(",",""));if(value!=null){var c=new JsonObject();c.addProperty("label",match.group(1));c.add("value",value);loreCounters.add(c);}}}
        row.add("componentFields",fields);row.add("recognizedNumericCustomDataCounters",counters);row.add("recognizedNumericLoreCounters",loreCounters);row.add("allowlistedServerItemIds",ids);row.add("sessionSaltedToolUuidDigests",digests);row.addProperty("schemaBoundReached",truncated[0]);row.addProperty("counterInterpretation","Legacy reference labels are observation candidates only; no current mutable allowlist or server-update causality inferred");return row;
    }
    private static void walk(String path,List<String> segments,JsonElement value,int depth,JsonArray fields,JsonObject counters,JsonObject ids,JsonObject digests,byte[] salt,int[] count,boolean[] truncated){
        if(depth>MAX_DEPTH||count[0]>=MAX_FIELDS){truncated[0]=true;return;}if(!safePath(path)){truncated[0]=true;return;}count[0]++;
        var f=new JsonObject();f.addProperty("path",path);f.addProperty("type",type(value));fields.add(f);
        if(segments.equals(List.of("minecraft:custom_data","id"))||segments.equals(List.of("minecraft:custom_data","ExtraAttributes","id"))){
            if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().matches("[A-Z0-9_]{1,128}"))ids.addProperty(path,value.getAsString());
        }
        if(UUID_PATHS.contains(segments)&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()){
            try{String canonical=UUID.fromString(value.getAsString()).toString();var d=MessageDigest.getInstance("SHA-256");d.update(salt);d.update((path+"\0"+canonical).getBytes(StandardCharsets.UTF_8));digests.addProperty(path,HexFormat.of().formatHex(d.digest()));}catch(IllegalArgumentException|NoSuchAlgorithmException ignored){}
        }
        if(segments.size()==3&&segments.get(0).equals("minecraft:custom_data")&&segments.get(1).equals("ExtraAttributes")&&COUNTERS.contains(segments.get(2))){var v=numeric(value);if(v!=null)counters.add(path,v);}
        if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet()){if(count[0]>=MAX_FIELDS){truncated[0]=true;break;}if(!safeField(e.getKey())){truncated[0]=true;continue;}var child=new ArrayList<>(segments);child.add(e.getKey());walk(path+"."+e.getKey(),List.copyOf(child),e.getValue(),depth+1,fields,counters,ids,digests,salt,count,truncated);}
        else if(value.isJsonArray()){int i=0;for(var e:value.getAsJsonArray()){if(i==32){truncated[0]=true;break;}var child=new ArrayList<>(segments);child.add("["+i+"]");walk(path+"["+i+++"]",List.copyOf(child),e,depth+1,fields,counters,ids,digests,salt,count,truncated);}}
    }
    static boolean safeField(String key){return key!=null&&key.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}")&&!key.matches("(?i).*([0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}).*");}
    private static boolean safePath(String p){return p.length()<=256&&p.matches("[A-Za-z0-9_:./\\[\\]-]+")&&!p.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*");}
    private static String type(JsonElement v){if(v==null||v.isJsonNull())return "null";if(v.isJsonObject())return "object";if(v.isJsonArray())return "array";var p=v.getAsJsonPrimitive();return p.isNumber()?"number":p.isBoolean()?"boolean":"string";}
    private static JsonElement numeric(JsonElement v){if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())return null;return numeric(v.getAsString());}
    private static JsonElement numeric(String value){try{var n=new java.math.BigInteger(value);return n.signum()>=0&&n.bitLength()<=63?new JsonPrimitive(n):null;}catch(NumberFormatException bad){return null;}}
}
