package dev.veltraph.client.gui;
import java.util.*;

/** Immutable container observation; player inventory is never an automation button source. */
public record MenuSnapshot(int id,String title,List<Slot> slots,int revision) {
    public MenuSnapshot(int id,String title,List<Slot> slots) { this(id,title,slots,0); }
    public record Slot(int id,String name,List<String> lore,boolean playerInventory,String itemId,int count,boolean foil,String componentIdentity) {
        public Slot(int id,String name,List<String> lore,boolean playerInventory,String itemId,int count,boolean foil) { this(id,name,lore,playerInventory,itemId,count,foil,""); }
        public Slot(int id,String name,List<String> lore,boolean playerInventory) { this(id,name,lore,playerInventory,"minecraft:paper",1,false); }
        public Slot(int id,String name,List<String> lore,boolean playerInventory,String itemId) { this(id,name,lore,playerInventory,itemId,1,false); }
        public Slot(int id,String name,List<String> lore,boolean playerInventory,String itemId,int count) { this(id,name,lore,playerInventory,itemId,count,false); }
        public Slot { componentIdentity=Objects.requireNonNull(componentIdentity);lore=List.copyOf(lore);if(count<0)throw new IllegalArgumentException("Negative stack count"); }
        public boolean locked() { return "minecraft:barrier".equals(itemId)||"minecraft:bedrock".equals(itemId); }
    }
    public MenuSnapshot { slots=List.copyOf(slots); }
    public static String clean(String value) { return value==null?"":value.replaceAll("§.","").strip(); }
    public List<Slot> matching(String label) {
        String wanted=clean(label);
        if(wanted.isBlank()) return List.of();
        var exact=slots.stream().filter(s->!s.playerInventory()&&clean(s.name()).equalsIgnoreCase(wanted)).toList();
        if(!exact.isEmpty()) return exact;
        var boundary=java.util.regex.Pattern.compile("(?i)(?<![\\p{L}\\p{N}_])"+java.util.regex.Pattern.quote(wanted)+"(?![\\p{L}\\p{N}_])");
        return slots.stream().filter(s->!s.playerInventory()&&boundary.matcher(clean(s.name())).find()).toList();
    }
}
