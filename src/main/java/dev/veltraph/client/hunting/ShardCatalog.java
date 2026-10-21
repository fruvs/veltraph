package dev.veltraph.client.hunting;
import java.util.*;

/** Explicit local catalog replaces the excluded original item-data network. Format: name=RARITY;name=RARITY. */
public final class ShardCatalog {
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }
    private final Map<String,Rarity> entries;
    private ShardCatalog(Map<String,Rarity> entries){this.entries=Map.copyOf(entries);}
    public static ShardCatalog parse(String text){
        Map<String,Rarity> entries=new LinkedHashMap<>();
        if(text!=null&&!text.isBlank())for(String row:text.split(";")){
            String[] pair=row.strip().split("=",-1);
            if(pair.length!=2||pair[0].isBlank())throw new IllegalArgumentException("Catalog requires name=RARITY entries");
            var rarity=Rarity.valueOf(pair[1].strip().toUpperCase(Locale.ROOT));
            String key=clean(pair[0]);
            if(entries.keySet().stream().anyMatch(n->n.equalsIgnoreCase(key)))throw new IllegalArgumentException("Duplicate shard identity: "+key);
            entries.put(key,rarity);
        }
        return new ShardCatalog(entries);
    }
    public Optional<String> identity(String name){String key=clean(name);return entries.keySet().stream().filter(n->n.equalsIgnoreCase(key)).findFirst();}
    public Rarity rarity(String name){return entries.get(identity(name).orElseThrow());}
    public Set<String> names(){return entries.keySet();}
    @Override public boolean equals(Object other){return other instanceof ShardCatalog catalog&&entries.equals(catalog.entries);}
    @Override public int hashCode(){return entries.hashCode();}
    public static String clean(String value){return value==null?"":value.replaceAll("§.","").strip();}
}
