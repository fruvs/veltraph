package dev.veltraph.client.garden.admission;
import com.google.gson.Strictness;
import com.google.gson.stream.*;
import dev.veltraph.client.garden.pests.PestAutoAdmissionSignals;
import java.io.*;
import java.util.*;

/** Each family's authentic row mapping is independently optional; no salted labels are guessed. */
public record GardenAdmissionBindings(Visitor visitor,PestAutoAdmissionSignals.Bindings pests) {
    public record Visitor(String contains,String prefix,String delimiter,String readyContains,String capabilityTrueRow,String capabilityFalseRow){
        public Visitor{for(String s:new String[]{contains,prefix,delimiter,readyContains,capabilityTrueRow,capabilityFalseRow})exact(s);if(capabilityTrueRow.equals(capabilityFalseRow))throw new IllegalArgumentException("Independent capability values required");}
    }
    public GardenAdmissionBindings {if(visitor==null&&pests==null)throw new IllegalArgumentException("At least one authentic family mapping required");if(pests!=null){exact(pests.tabPrefix());exact(pests.sidebarPrefix());}}
    private static void exact(String s){if(s==null||s.isBlank()||s.length()>256||s.indexOf('§')>=0||s.indexOf('\n')>=0||s.indexOf('\r')>=0)throw new IllegalArgumentException("Exact bounded color-stripped row binding required");}
    public static GardenAdmissionBindings read(String raw){
        if(raw==null||raw.isBlank()||raw.length()>4096)throw new IllegalArgumentException("Authentic observed row mapping required");
        try(var r=new JsonReader(new StringReader(raw))){
            r.setStrictness(Strictness.STRICT);r.beginObject();Visitor visitor=null;PestAutoAdmissionSignals.Bindings pests=null;var keys=new HashSet<String>();
            while(r.hasNext()){
                String key=r.nextName();if(!keys.add(key))throw new IllegalArgumentException("Duplicate family mapping");
                var values=strings(r);
                switch(key){
                    case "visitor"->{if(!values.keySet().equals(Set.of("contains","prefix","delimiter","readyContains","capabilityTrueRow","capabilityFalseRow")))throw new IllegalArgumentException("Exact visitor fields required");visitor=new Visitor(values.get("contains"),values.get("prefix"),values.get("delimiter"),values.get("readyContains"),values.get("capabilityTrueRow"),values.get("capabilityFalseRow"));}
                    case "pest"->{if(!values.keySet().equals(Set.of("tabPrefix","sidebarPrefix")))throw new IllegalArgumentException("Exact pest fields required");pests=new PestAutoAdmissionSignals.Bindings(values.get("tabPrefix"),values.get("sidebarPrefix"));}
                    default->throw new IllegalArgumentException("Unknown family mapping");
                }
            }
            r.endObject();if(r.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Exactly one mapping object required");return new GardenAdmissionBindings(visitor,pests);
        }catch(IOException|RuntimeException invalid){throw new IllegalArgumentException("Invalid Garden admission mapping",invalid);}
    }
    private static Map<String,String> strings(JsonReader r)throws IOException {
        r.beginObject();var values=new HashMap<String,String>();while(r.hasNext()){String key=r.nextName();if(values.containsKey(key)||r.peek()!=JsonToken.STRING)throw new IllegalArgumentException("Unique exact string fields required");values.put(key,r.nextString());}r.endObject();return values;
    }
    public Integer visitorCount(List<String> sidebar){
        if(visitor==null||sidebar==null)return null;
        for(String row:sidebar){if(row==null||!row.contains(visitor.contains()))continue;String value=row.strip();if(!value.startsWith(visitor.prefix()))return null;int start=value.indexOf(visitor.delimiter());if(start<0)return null;value=value.substring(start+visitor.delimiter().length());int end=value.indexOf(')');if(end<0)return null;try{return Integer.valueOf(value.substring(0,end));}catch(NumberFormatException invalid){return null;}}
        return null;
    }
    public Boolean visitorReady(List<String> sidebar){return visitor==null||sidebar==null?null:sidebar.stream().filter(Objects::nonNull).anyMatch(row->row.contains(visitor.readyContains()));}
    public Boolean visitorCapability(List<String> sidebar,List<String> tab){
        if(visitor==null||sidebar==null||tab==null)return null;boolean yes=false,no=false;for(var rows:List.of(sidebar,tab))for(String row:rows){yes|=visitor.capabilityTrueRow().equals(row);no|=visitor.capabilityFalseRow().equals(row);}return yes==no?null:yes;
    }
}
