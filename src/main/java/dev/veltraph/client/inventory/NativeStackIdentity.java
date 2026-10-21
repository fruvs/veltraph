package dev.veltraph.client.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

/** Exact native item identity across rebuilt registry owners. Capture while the original registry lives. */
public final class NativeStackIdentity {
    private NativeStackIdentity() {}
    /** Both the native stack and ALL effective components, not only explicit component patches. */
    public static final class Snapshot {
        private final Tag stack,components;
        private Snapshot(Tag stack,Tag components){this.stack=stack.copy();this.components=components.copy();}
        /** Versioned structural encoding of both persistent codecs; ordering independent, never account authentication. */
        public String persistentCanonicalEncoding(){return canonicalPersistentEncoding(stack,components);}
        public boolean matches(ItemStack current,HolderLookup.Provider currentRegistry){return NativeStackIdentity.matches(this,current,currentRegistry);}
        @Override public boolean equals(Object other){return other instanceof Snapshot value&&stack.equals(value.stack)&&components.equals(value.components);}
        @Override public int hashCode(){return Objects.hash(stack,components);}
        @Override public String toString(){return "NativeStackIdentity[stack="+stack+", effectiveComponents="+components+"]";}
    }
    static String canonicalPersistentEncoding(Tag stack,Tag components){try{var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeUTF("native-persistent-tags-v1");writeCanonical(stack,out);writeCanonical(components,out);out.flush();return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());}catch(java.io.IOException impossible){throw new IllegalStateException(impossible);}}
    private static void writeCanonical(Tag tag,java.io.DataOutput out)throws java.io.IOException{
        if(tag==null)throw new IllegalArgumentException("Missing persistent identity tag");out.writeByte(tag.getId());
        if(tag instanceof CompoundTag compound){var keys=new java.util.TreeSet<>(compound.keySet());out.writeInt(keys.size());for(String key:keys){out.writeUTF(key);writeCanonical(compound.get(key),out);}}
        else if(tag instanceof ListTag list){out.writeInt(list.size());for(Tag value:list)writeCanonical(value,out);}
        else switch(tag.getId()){case Tag.TAG_END,Tag.TAG_BYTE,Tag.TAG_SHORT,Tag.TAG_INT,Tag.TAG_LONG,Tag.TAG_FLOAT,Tag.TAG_DOUBLE,Tag.TAG_BYTE_ARRAY,Tag.TAG_STRING,Tag.TAG_INT_ARRAY,Tag.TAG_LONG_ARRAY -> tag.write(out);default -> throw new IllegalArgumentException("Unsupported persistent identity tag type "+tag.getId());}
    }
    public static Snapshot capture(ItemStack item,HolderLookup.Provider originalRegistry){
        Objects.requireNonNull(item);Objects.requireNonNull(originalRegistry);
        if(item.isEmpty())return new Snapshot(new CompoundTag(),new CompoundTag());
        // The persistent map codec filters transient types; refuse rather than silently omitting one.
        for(var value:item.getComponents())if(value.type().isTransient())throw new IllegalArgumentException("No persistent native identity codec for "+value.type());
        var ops=originalRegistry.createSerializationContext(NbtOps.INSTANCE);
        var stack=ItemStack.CODEC.encodeStart(ops,item).getOrThrow();
        // ItemStack.CODEC encodes stored patches. The effective map additionally includes defaults,
        // damage, lore, enchantments, trim and all other persistent native component values.
        var effective=DataComponentMap.CODEC.encodeStart(ops,item.getComponents()).getOrThrow();
        return new Snapshot(stack,effective);
    }
    public static boolean matches(Snapshot saved,ItemStack current,HolderLookup.Provider currentRegistry){
        if(saved==null||current==null||currentRegistry==null)return false;
        try{return saved.equals(capture(current,currentRegistry));}
        catch(RuntimeException unsupported){return false;}
    }
}
