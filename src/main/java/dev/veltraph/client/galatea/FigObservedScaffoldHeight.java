package dev.veltraph.client.galatea;

import com.google.gson.JsonParser;
import java.util.*;

/** Explicit observed item/marker binding; original encrypted labels remain unclaimed. */
public record FigObservedScaffoldHeight(String registryId,String name,String components,String marker) {
    public record Item(String registryId,String name,String components,List<String> lore) {
        public Item{lore=List.copyOf(lore);}
    }
    public FigObservedScaffoldHeight {
        if(registryId==null||!registryId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||name==null||name.isBlank()
                ||components==null||components.isBlank()||marker==null||marker.isBlank())
            throw new IllegalArgumentException("Exact observed scaffold item and lore marker required");
    }
    public static FigObservedScaffoldHeight parse(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();
        if(!o.keySet().equals(Set.of("schema","registryId","name","components","marker"))
                ||!o.get("schema").isJsonPrimitive()||!o.getAsJsonPrimitive("schema").isNumber()
                ||!o.get("schema").getAsString().equals("1"))throw new IllegalArgumentException("Scaffold binding schema1 required");
        for(String k:List.of("registryId","name","components","marker"))
            if(!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isString())throw new IllegalArgumentException("Scaffold binding strings required");
        return new FigObservedScaffoldHeight(o.get("registryId").getAsString(),o.get("name").getAsString(),
                o.get("components").getAsString(),o.get("marker").getAsString());
    }
    public int observe(List<Item> inventory) {
        List<Item> matches=inventory.stream().filter(i->registryId.equals(i.registryId())&&name.equals(i.name())
                &&components.equals(i.components())).toList();
        if(matches.size()!=1)throw new IllegalArgumentException("Exactly one bound native scaffolding item required");
        List<String> lore=matches.getFirst().lore();
        int markerIndex=-1;
        for(int i=0;i<lore.size();i++)if(lore.get(i).contains(marker)){markerIndex=i;break;}
        if(markerIndex<0)throw new IllegalArgumentException("Observed scaffolding marker is absent");
        String line=lore.get(markerIndex),value;
        if(line.endsWith(".")) {
            String[] parts=line.split(" ",-1);value=parts[parts.length-1];
        } else {
            if(markerIndex+1>=lore.size())throw new IllegalArgumentException("Scaffolding height line absent");
            value=lore.get(markerIndex+1);
        }
        String integer=kotlinTrim(value.replace(".",""));
        if(!integer.matches("[+-]?[0-9]+"))throw new IllegalArgumentException("Source scaffold height must be an integer");
        try{return Integer.parseInt(integer);}catch(NumberFormatException bad){throw new IllegalArgumentException("Source scaffold height exceeds int",bad);}
    }
    private static String kotlinTrim(String text) {
        int first=0,last=text.length();
        while(first<last&&space(text.charAt(first)))first++;
        while(last>first&&space(text.charAt(last-1)))last--;
        return text.substring(first,last);
    }
    private static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
}
