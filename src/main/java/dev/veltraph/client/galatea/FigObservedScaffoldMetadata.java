package dev.veltraph.client.galatea;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;

/** Explicit observed grammar for the two independent source fields.
 * Captions remain supplied evidence: this class does not claim original caption recovery.
 */
public record FigObservedScaffoldMetadata(String registryId,String name,String components,
    String itemNameContains,String marker,String riseStartToken,String riseEndToken) {
    public record Item(int slot,String registryId,String name,String components,List<String> lore) {
        public Item { lore=List.copyOf(lore); }
    }
    public record Observation(int hotbarSlot,FigScaffoldStateMachine.Metadata values,
        String observedMarkerLine) {}
    public FigObservedScaffoldMetadata {
        if(registryId==null || !registryId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Native registry identity required");
        for(String s:List.of(name,components,itemNameContains,marker,riseStartToken,riseEndToken))
            if(s.isBlank())throw new IllegalArgumentException("Observed scaffold identity and grammar required");
    }
    public static FigObservedScaffoldMetadata parse(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();
        var keys=Set.of("schema","registryId","name","components","itemNameContains","marker","riseStartToken","riseEndToken");
        if(!o.keySet().equals(keys) || !o.get("schema").isJsonPrimitive()
            || !o.getAsJsonPrimitive("schema").isNumber() || !o.get("schema").getAsString().equals("2"))
            throw new IllegalArgumentException("Two-field scaffold binding schema2 required");
        for(String k:keys)if(!k.equals("schema") && (!o.get(k).isJsonPrimitive()
            || !o.getAsJsonPrimitive(k).isString()))throw new IllegalArgumentException("Scaffold binding strings required");
        return new FigObservedScaffoldMetadata(o.get("registryId").getAsString(),o.get("name").getAsString(),
            o.get("components").getAsString(),o.get("itemNameContains").getAsString(),o.get("marker").getAsString(),
            o.get("riseStartToken").getAsString(),o.get("riseEndToken").getAsString());
    }
    public Observation observe(List<Item> inventory) {
        // Source chooses first nonempty hotbar item whose cleaned hover name contains caption.
        Item item=inventory.stream().filter(i->i.slot()>=0 && i.slot()<9 && i.name().contains(itemNameContains))
            .min(java.util.Comparator.comparingInt(Item::slot))
            .orElseThrow(()->new IllegalArgumentException("Observed scaffold caption absent from native hotbar"));
        // Exact supplied witness qualifies the chosen source-style item; it never skips a foreign first match.
        if(!registryId.equals(item.registryId()) || !name.equals(item.name()) || !components.equals(item.components()))
            throw new IllegalArgumentException("First scaffold-caption match differs from exact observed native item");
        int at=-1;for(int i=0;i<item.lore().size();i++)if(item.lore().get(i).contains(marker)){at=i;break;}
        if(at<0)throw new IllegalArgumentException("Observed scaffold marker absent");
        String line=item.lore().get(at),height;
        if(line.endsWith(".")){String[] parts=line.split(" ",-1);height=parts[parts.length-1];}
        else {if(at+1>=item.lore().size())throw new IllegalArgumentException("Following geometry-height line absent");height=item.lore().get(at+1);}
        int geometry=integer(kotlinTrim(height.replace(".","")),"geometryHeight");
        // Preserve original +5, independent of observed token length.
        int start=line.indexOf(riseStartToken)+5,end=line.indexOf(riseEndToken);
        if(start<0 || end<start || end>line.length() || !line.contains(riseStartToken))
            throw new IllegalArgumentException("Observed completion-rise substring unavailable");
        int rise=integer(kotlinTrim(line.substring(start,end)),"completionRise");
        return new Observation(item.slot(),new FigScaffoldStateMachine.Metadata(geometry,rise),line);
    }
    private static int integer(String value,String role) {
        if(!value.matches("[+-]?[0-9]+"))throw new IllegalArgumentException(role+" must be a source integer");
        try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new IllegalArgumentException(role+" exceeds source int",e);}
    }
    private static String kotlinTrim(String value) {
        int a=0,b=value.length();while(a<b&&space(value.charAt(a)))a++;while(b>a&&space(value.charAt(b-1)))b--;
        return value.substring(a,b);
    }
    private static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
}
