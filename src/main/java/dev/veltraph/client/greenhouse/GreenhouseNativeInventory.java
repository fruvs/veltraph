package dev.veltraph.client.greenhouse;

import dev.veltraph.client.inventory.NativeStackIdentity;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Complete per-slot native identities. No aggregate count or components.toString is integrity authority. */
public final class GreenhouseNativeInventory {
    public record Slot(String kind,int index,String equipment) {}
    public record Cell(NativeStackIdentity.Snapshot identity,int nativeCount,boolean empty) {
        public Cell {Objects.requireNonNull(identity);if(nativeCount<0)throw new IllegalArgumentException("Invalid actual native count");}
        public String persistentEncoding(){return identity.persistentCanonicalEncoding();}
    }
    public record EncodedCell(Slot slot,String nativePersistentIdentity,int nativeCount,boolean empty) {}
    public record Projection(String schema,int selectedSlot,List<EncodedCell> slots,String cursorIdentity,int cursorCount,boolean cursorEmpty) {
        public Projection {slots=List.copyOf(slots);}
    }
    private final Map<Slot,Cell> slots;private final int selected;private final Cell cursor;
    private GreenhouseNativeInventory(Map<Slot,Cell> slots,int selected,Cell cursor){this.slots=Map.copyOf(slots);this.selected=selected;this.cursor=cursor;}
    public static Cell captureCell(ItemStack value,HolderLookup.Provider registry){
        Objects.requireNonNull(value);Objects.requireNonNull(registry);
        // Check empty cells too: an unsupported component never becomes an omitted identity.
        for(var c:value.getComponents())if(c.type().isTransient())throw new IllegalArgumentException("Unsupported transient native inventory identity");
        var copy=value.copy();return new Cell(NativeStackIdentity.capture(copy,registry),copy.getCount(),copy.isEmpty());
    }
    public static GreenhouseNativeInventory capture(List<ItemStack> actualInventory,Map<EquipmentSlot,ItemStack> actualEquipment,ItemStack actualCursor,int selected,HolderLookup.Provider registry){
        if(actualInventory==null||actualInventory.size()<9||actualInventory.size()>128||selected<0||selected>8)throw new IllegalArgumentException("Complete actual native inventory and selected hotbar required");
        var cells=new LinkedHashMap<Slot,Cell>();for(int i=0;i<actualInventory.size();i++)cells.put(new Slot("INVENTORY",i,""),captureCell(actualInventory.get(i),registry));
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND)){
            if(!actualEquipment.containsKey(slot))throw new IllegalArgumentException("Complete native equipment snapshot required");
            cells.put(new Slot("EQUIPMENT",-1,slot.name()),captureCell(actualEquipment.get(slot),registry));
        }
        return new GreenhouseNativeInventory(cells,selected,captureCell(actualCursor,registry));
    }
    public Cell inventorySlot(int index){return slots.get(new Slot("INVENTORY",index,""));}
    public Cell tool(){return inventorySlot(selected);}
    public int selectedSlot(){return selected;}public boolean cursorEmpty(){return cursor.empty();}
    public Projection persistentProjection(){var rows=slots.entrySet().stream().sorted(Comparator.comparing((Map.Entry<Slot,Cell> e)->e.getKey().kind()).thenComparingInt(e->e.getKey().index()).thenComparing(e->e.getKey().equipment())).map(e->new EncodedCell(e.getKey(),e.getValue().persistentEncoding(),e.getValue().nativeCount(),e.getValue().empty())).toList();return new Projection("native-slot-integrity-v1",selected,rows,cursor.persistentEncoding(),cursor.nativeCount(),cursor.empty());}
    public boolean sameSlotsAndCursor(GreenhouseNativeInventory other){return other!=null&&slots.equals(other.slots)&&cursor.equals(other.cursor);}
    public GreenhouseNativeInventory replacingResource(int slot,ItemStack exactNativeAfter,HolderLookup.Provider registry){if(inventorySlot(slot)==null)throw new IllegalArgumentException("Actual resource slot missing");var next=new LinkedHashMap<>(slots);next.put(new Slot("INVENTORY",slot,""),captureCell(exactNativeAfter,registry));return new GreenhouseNativeInventory(next,selected,cursor);}
    @Override public boolean equals(Object o){return o instanceof GreenhouseNativeInventory s&&selected==s.selected&&slots.equals(s.slots)&&cursor.equals(s.cursor);}
    @Override public int hashCode(){return Objects.hash(slots,selected,cursor);}
}
