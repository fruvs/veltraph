package dev.veltraph.client.mining.vein;

import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static dev.veltraph.client.mining.vein.VeinWatchValueGraph.*;
import static dev.veltraph.client.mining.vein.VeinWatchGraphFields.*;
import static dev.veltraph.client.mining.vein.VeinWatchNativeFields.*;

/** Read-only typed projection. No codecs, getters on unknown classes, registries, providers or formatting. */
final class VeinWatchGraphCapture {
    private final Writer writer=new Writer();
    private final List<Object> watched;
    VeinWatchGraphCapture(List<Object> watched) { this.watched=watched; }
    private static boolean nativeType(Object object,Class<?> type) {
        return VeinWatchValueGraph.exactClass(object,type,ItemStack.class.getClassLoader());
    }
    private void watch(Object object) {
        if(watched==null) return;
        for(Object old:watched) if(old==object) return;
        if(watched.size()>=128) throw new Unsupported(); watched.add(object);
    }
    private static boolean list(List<?> list) {
        if(list==null) return false;
        Class<?> type=list.getClass();
        return type==java.util.ArrayList.class || type==java.util.LinkedList.class
                || type==List.of().getClass() || type==List.of(0).getClass()
                || type==java.util.Collections.emptyList().getClass() || type==java.util.Collections.singletonList(0).getClass()
                || type==java.util.Arrays.asList(0).getClass()
                || type==com.google.common.collect.ImmutableList.of().getClass() || type==com.google.common.collect.ImmutableList.of(0).getClass();
    }
    private static boolean map(Map<?,?> map) {
        return map!=null && (map.getClass()==java.util.HashMap.class || map.getClass()==java.util.LinkedHashMap.class
                || map.getClass()==Map.of().getClass() || map.getClass()==Map.of("",0).getClass()
                || map.getClass()==java.util.Collections.emptyMap().getClass());
    }
    Value fuel(Object object) {
        if(!nativeType(object,CookingFuel.class)) throw new Unsupported();
        var fuel=(CookingFuel)object;
        return writer.branch("cooking-fuel",List.of(fuelInt(fuel.burnTime(),1),fuelFloat(fuel.speedMultiplier(),1)),0);
    }
    private Value fuelInt(Object object,int depth) {
        if(nativeType(object,ResolvableInt.Constant.class))
            return writer.atom("fuel-int-constant",Integer.toString(((ResolvableInt.Constant)object).value()),depth);
        if(nativeType(object,ResolvableInt.Reference.class))
            return fuelKey("fuel-int-reference",((ResolvableInt.Reference)object).key(),depth);
        throw new Unsupported();
    }
    private Value fuelFloat(Object object,int depth) {
        if(nativeType(object,ResolvableFloat.Constant.class))
            return writer.atom("fuel-float-constant-bits",Integer.toString(Float.floatToRawIntBits(((ResolvableFloat.Constant)object).value())),depth);
        if(nativeType(object,ResolvableFloat.Reference.class))
            return fuelKey("fuel-float-reference",((ResolvableFloat.Reference)object).key(),depth);
        throw new Unsupported();
    }
    private Value fuelKey(String kind,Object object,int depth) {
        if(!nativeType(object,ResourceKey.class)) throw new Unsupported();
        var key=(ResourceKey<?>)object; var registry=key.registry(); var id=key.identifier();
        if(!nativeType(registry,Identifier.class) || !nativeType(id,Identifier.class)) throw new Unsupported();
        return writer.branch(kind,List.of(writer.atom("registry",registry.getNamespace()+":"+registry.getPath(),depth+1),
                writer.atom("identifier",id.getNamespace()+":"+id.getPath(),depth+1)),depth);
    }
    Value name(Object object) { return component(object,0); }
    Value lore(Object object) {
        if(!nativeType(object,ItemLore.class) || !(object instanceof Lore fields) || !list(fields.veltraph$watchLines())) throw new Unsupported();
        var lines=fields.veltraph$watchLines(); if(lines.size()>Writer.LORE_LINES) throw new Unsupported();
        var children=new ArrayList<Value>(); for(Object line:lines) children.add(component(line,1));
        return writer.branch("lore",children,0);
    }
    Value custom(Object object) {
        if(!nativeType(object,CustomData.class) || !(object instanceof Custom fields)) throw new Unsupported();
        watch(object); return writer.branch("custom-data",List.of(nbt(fields.veltraph$watchTag(),1)),0);
    }
    Value enchantments(Object object) {
        if(!nativeType(object,ItemEnchantments.class) || !(object instanceof Enchantments fields)) throw new Unsupported();
        var values=fields.veltraph$watchEnchantments();
        if(values==null || values.getClass()!=it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap.class || values.size()>32) throw new Unsupported();
        var sorted=new TreeMap<String,Integer>();
        for(var entry:values.object2IntEntrySet()) {
            Object holder=entry.getKey();
            if(!nativeType(holder,Holder.Reference.class) || !(holder instanceof HolderFields raw) || raw.veltraph$watchKey()==null) throw new Unsupported();
            watch(holder); var identifier=raw.veltraph$watchKey().identifier(); if(!nativeType(identifier,Identifier.class)) throw new Unsupported();
            String key=identifier.getNamespace()+":"+identifier.getPath(); if(sorted.put(key,entry.getIntValue())!=null) throw new Unsupported();
        }
        var children=new ArrayList<Value>();
        for(var entry:sorted.entrySet()) children.add(writer.branch("enchantment",List.of(writer.atom("key",entry.getKey(),2),writer.atom("int",Integer.toString(entry.getValue()),2)),1));
        return writer.branch("enchantments",children,0);
    }
    private Value component(Object object,int depth) {
        writer.depth(depth);
        if(!nativeType(object,MutableComponent.class) || !(object instanceof NameFields fields) || !list(fields.veltraph$watchSiblings())) throw new Unsupported();
        watch(object);
        var children=new ArrayList<Value>(); Object contents=fields.veltraph$watchContents();
        if(contents==PlainTextContents.EMPTY) children.add(writer.atom("literal","",depth+1));
        else if(nativeType(contents,PlainTextContents.LiteralContents.class) && contents instanceof Literal literal)
            children.add(writer.atom("literal",literal.veltraph$watchText(),depth+1));
        else if(nativeType(contents,TranslatableContents.class) && contents instanceof TranslationFields translation) {
            if(translation.veltraph$watchKey()==null || translation.veltraph$watchArgs()==null) throw new Unsupported();
            Object[] args=translation.veltraph$watchArgs();writer.length(args.length);
            var translated=new ArrayList<Value>(); translated.add(writer.atom("key",translation.veltraph$watchKey(),depth+2));
            translated.add(writer.atom("fallback",translation.veltraph$watchFallback(),depth+2));
            for(Object arg:args) translated.add(argument(arg,depth+2));
            children.add(writer.branch("translatable",translated,depth+1));
        } else throw new Unsupported();
        children.add(style(fields.veltraph$watchStyle(),depth+1));
        var siblings=fields.veltraph$watchSiblings();writer.length(siblings.size());
        for(Object sibling:siblings) children.add(component(sibling,depth+1));
        return writer.branch("component",children,depth);
    }
    private Value argument(Object object,int depth) {
        if(object==null) return writer.atom("null",null,depth);
        if(object.getClass()==String.class) return writer.atom("string",(String)object,depth);
        if(object.getClass()==Integer.class) return writer.atom("int",Integer.toString((Integer)object),depth);
        if(object.getClass()==Long.class) return writer.atom("long",Long.toString((Long)object),depth);
        if(object.getClass()==Float.class) return writer.atom("float-bits",Integer.toString(Float.floatToRawIntBits((Float)object)),depth);
        if(object.getClass()==Double.class) return writer.atom("double-bits",Long.toString(Double.doubleToRawLongBits((Double)object)),depth);
        if(object.getClass()==Boolean.class) return writer.atom("boolean",Boolean.toString((Boolean)object),depth);
        return component(object,depth);
    }
    private Value style(Object object,int depth) {
        writer.depth(depth);
        if(!nativeType(object,Style.class) || !(object instanceof NativeStyle fields) || fields.veltraph$watchClick()!=null || fields.veltraph$watchHover()!=null) throw new Unsupported();
        var children=new ArrayList<Value>(); Object color=fields.veltraph$watchColor();
        if(color==null) children.add(writer.atom("color",null,depth+1));
        else {
            if(!nativeType(color,TextColor.class) || !(color instanceof Color fieldsColor)) throw new Unsupported();
            children.add(writer.branch("color",List.of(writer.atom("rgb",Integer.toString(fieldsColor.veltraph$watchColorValue()),depth+2),
                    writer.atom("name",fieldsColor.veltraph$watchColorName(),depth+2)),depth+1));
        }
        children.add(writer.atom("shadow",fields.veltraph$watchShadow()==null?null:Integer.toString(fields.veltraph$watchShadow()),depth+1));
        children.add(writer.atom("bold",nullable(fields.veltraph$watchBold()),depth+1));
        children.add(writer.atom("italic",nullable(fields.veltraph$watchItalic()),depth+1));
        children.add(writer.atom("underlined",nullable(fields.veltraph$watchUnderlined()),depth+1));
        children.add(writer.atom("strikethrough",nullable(fields.veltraph$watchStrikethrough()),depth+1));
        children.add(writer.atom("obfuscated",nullable(fields.veltraph$watchObfuscated()),depth+1));
        children.add(writer.atom("insertion",fields.veltraph$watchInsertion(),depth+1));
        Object font=fields.veltraph$watchFont();
        if(font==null) children.add(writer.atom("font",null,depth+1));
        else {
            if(!nativeType(font,FontDescription.Resource.class) || !(font instanceof Font raw) || !nativeType(raw.veltraph$watchFontId(),Identifier.class)) throw new Unsupported();
            var id=raw.veltraph$watchFontId(); children.add(writer.atom("font",id.getNamespace()+":"+id.getPath(),depth+1));
        }
        return writer.branch("style",children,depth);
    }
    private static String nullable(Boolean value) { return value==null?null:Boolean.toString(value); }
    private Value nbt(Object object,int depth) {
        writer.depth(depth);
        if(nativeType(object,CompoundTag.class) && object instanceof Compound compound) {
            watch(object); var map=compound.veltraph$watchTags(); if(!map(map)) throw new Unsupported(); writer.length(map.size());
            var sorted=new TreeMap<String,Object>();
            for(var entry:map.entrySet()) { if(entry.getKey()==null || entry.getKey().getClass()!=String.class) throw new Unsupported(); sorted.put((String)entry.getKey(),entry.getValue()); }
            var entries=new ArrayList<Value>();
            for(var entry:sorted.entrySet()) entries.add(writer.branch("entry",List.of(writer.atom("key",entry.getKey(),depth+2),nbt(entry.getValue(),depth+2)),depth+1));
            return writer.branch("nbt-compound",entries,depth);
        }
        if(nativeType(object,ListTag.class) && object instanceof NbtList fields) {
            watch(object); var list=fields.veltraph$watchList(); if(!list(list)) throw new Unsupported();writer.length(list.size());
            var children=new ArrayList<Value>(); for(Object child:list) children.add(nbt(child,depth+1)); return writer.branch("nbt-list",children,depth);
        }
        if(nativeType(object,ByteArrayTag.class) && object instanceof Bytes fields) { watch(object); return writer.bytes(fields.veltraph$watchBytes(),depth); }
        if(nativeType(object,IntArrayTag.class) && object instanceof Ints fields) { watch(object); return writer.ints(fields.veltraph$watchInts(),depth); }
        if(nativeType(object,LongArrayTag.class) && object instanceof Longs fields) { watch(object); return writer.longs(fields.veltraph$watchLongs(),depth); }
        if(nativeType(object,StringTag.class)) return writer.atom("nbt-string",((StringTag)object).value(),depth);
        if(nativeType(object,ByteTag.class)) return writer.atom("nbt-byte",Byte.toString(((ByteTag)object).value()),depth);
        if(nativeType(object,ShortTag.class)) return writer.atom("nbt-short",Short.toString(((ShortTag)object).value()),depth);
        if(nativeType(object,IntTag.class)) return writer.atom("nbt-int",Integer.toString(((IntTag)object).value()),depth);
        if(nativeType(object,LongTag.class)) return writer.atom("nbt-long",Long.toString(((LongTag)object).value()),depth);
        if(nativeType(object,FloatTag.class)) return writer.atom("nbt-float-bits",Integer.toString(Float.floatToRawIntBits(((FloatTag)object).value())),depth);
        if(nativeType(object,DoubleTag.class)) return writer.atom("nbt-double-bits",Long.toString(Double.doubleToRawLongBits(((DoubleTag)object).value())),depth);
        if(object==EndTag.INSTANCE) return writer.atom("nbt-end",null,depth);
        throw new Unsupported();
    }
}
