package dev.veltraph.client.rewarp.mc;

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
import static dev.veltraph.client.rewarp.mc.IssuedReturnValueGraph.*;
import static dev.veltraph.client.mining.vein.VeinWatchGraphFields.*;
import static dev.veltraph.client.mining.vein.VeinWatchNativeFields.*;

/** Read-only typed projection. No codecs, getters on unknown classes, registries, providers or formatting. */
final class IssuedReturnGraphCapture {
    private final Writer writer=new Writer();
    private final List<Object> watched;
    IssuedReturnGraphCapture(List<Object> watched) { this.watched=watched; }
    private static boolean nativeType(Object object,Class<?> type) {
        return IssuedReturnValueGraph.exactClass(object,type,ItemStack.class.getClassLoader());
    }
    private void watch(Object object) {
        if(watched==null) return;
        for(Object old:watched) if(old==object) return;
        if(watched.size()>=16384) throw new Unsupported(); watched.add(object);
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
    Value identifier(Object object,int depth) {
        if(!nativeType(object,Identifier.class))throw new Unsupported();
        var id=(Identifier)object;return writer.atom("identifier",id.getNamespace()+":"+id.getPath(),depth);
    }
    Value holder(Object object,int depth) {
        if(!nativeType(object,Holder.Reference.class)||!(object instanceof HolderFields raw)||raw.veltraph$watchKey()==null)throw new Unsupported();
        watch(object);var key=raw.veltraph$watchKey();
        return writer.branch("holder",List.of(identifier(key.registry(),depth+1),identifier(key.identifier(),depth+1)),depth);
    }
    Value attributes(Object object) {
        if(!nativeType(object,net.minecraft.world.item.component.ItemAttributeModifiers.class))throw new Unsupported();
        var attributes=(net.minecraft.world.item.component.ItemAttributeModifiers)object;
        if(!list(attributes.modifiers())||attributes.modifiers().size()>64)throw new Unsupported();
        var values=new ArrayList<Value>();
        for(var entry:attributes.modifiers()){
            if(!nativeType(entry,net.minecraft.world.item.component.ItemAttributeModifiers.Entry.class))throw new Unsupported();
            var modifier=entry.modifier();if(!nativeType(modifier,net.minecraft.world.entity.ai.attributes.AttributeModifier.class)||entry.slot()==null||modifier.operation()==null)throw new Unsupported();
            Object display=entry.display();Value shown;
            if(display==net.minecraft.world.item.component.ItemAttributeModifiers.Display.attributeModifiers())shown=writer.atom("display","default",2);
            else if(display==net.minecraft.world.item.component.ItemAttributeModifiers.Display.hidden())shown=writer.atom("display","hidden",2);
            else if(nativeType(display,net.minecraft.world.item.component.ItemAttributeModifiers.Display.OverrideText.class))shown=component(((net.minecraft.world.item.component.ItemAttributeModifiers.Display.OverrideText)display).component(),2);
            else throw new Unsupported();
            values.add(writer.branch("attribute",List.of(holder(entry.attribute(),2),identifier(modifier.id(),2),
                writer.atom("amount-bits",Long.toString(Double.doubleToRawLongBits(modifier.amount())),2),
                writer.atom("operation",modifier.operation().name(),2),writer.atom("slot",entry.slot().name(),2),shown),1));
        }
        return writer.branch("attributes",values,0);
    }
    private Value holderSet(Object object,int depth){
        List<?> values;Value kind;
        if(nativeType(object,net.minecraft.core.HolderSet.Named.class)&&object instanceof IssuedReturnNativeFields.NamedSet raw){
            watch(object);values=raw.veltraph$returnContents();var tag=raw.veltraph$returnTag();
            if(!nativeType(tag,net.minecraft.tags.TagKey.class))throw new Unsupported();
            kind=writer.branch("named-set",List.of(identifier(tag.registry().registry(),depth+2),identifier(tag.registry().identifier(),depth+2),identifier(tag.location(),depth+2)),depth+1);
        }else if(nativeType(object,net.minecraft.core.HolderSet.Direct.class)&&object instanceof IssuedReturnNativeFields.DirectSet raw){values=raw.veltraph$returnContents();kind=writer.atom("direct-set",null,depth+1);}
        else throw new Unsupported();
        if(!list(values)||values.size()>4096)throw new Unsupported();var entries=new ArrayList<Value>();entries.add(kind);
        for(var value:values)entries.add(holder(value,depth+1));return writer.branch("holder-set",entries,depth);
    }
    Value tool(Object object){
        if(!nativeType(object,net.minecraft.world.item.component.Tool.class))throw new Unsupported();var tool=(net.minecraft.world.item.component.Tool)object;
        if(!list(tool.rules())||tool.rules().size()>128)throw new Unsupported();var values=new ArrayList<Value>();
        values.add(writer.atom("speed-bits",Integer.toString(Float.floatToRawIntBits(tool.defaultMiningSpeed())),1));
        values.add(writer.atom("damage",Integer.toString(tool.damagePerBlock()),1));values.add(writer.atom("creative",Boolean.toString(tool.canDestroyBlocksInCreative()),1));
        for(var rule:tool.rules()){
            if(!nativeType(rule,net.minecraft.world.item.component.Tool.Rule.class))throw new Unsupported();
            values.add(writer.branch("rule",List.of(holderSet(rule.blocks(),2),
                writer.atom("speed-bits",rule.speed().isEmpty()?null:Integer.toString(Float.floatToRawIntBits(rule.speed().get())),2),
                writer.atom("correct-drops",rule.correctForDrops().isEmpty()?null:Boolean.toString(rule.correctForDrops().get()),2)),1));
        }
        return writer.branch("tool",values,0);
    }
    Value weapon(Object object){
        if(!nativeType(object,net.minecraft.world.item.component.Weapon.class))throw new Unsupported();var weapon=(net.minecraft.world.item.component.Weapon)object;
        return writer.branch("weapon",List.of(writer.atom("damage",Integer.toString(weapon.itemDamagePerAttack()),1),writer.atom("disable-bits",Integer.toString(Float.floatToRawIntBits(weapon.disableBlockingForSeconds())),1)),0);
    }
    Value equippable(Object object){
        if(!nativeType(object,net.minecraft.world.item.equipment.Equippable.class))throw new Unsupported();var gear=(net.minecraft.world.item.equipment.Equippable)object;
        if(gear.slot()==null)throw new Unsupported();var values=new ArrayList<Value>();
        values.add(writer.atom("slot",gear.slot().name(),1));values.add(holder(gear.equipSound(),1));values.add(holder(gear.shearingSound(),1));
        if(gear.assetId().isPresent()){Object key=gear.assetId().get();if(!nativeType(key,ResourceKey.class))throw new Unsupported();var resource=(ResourceKey<?>)key;values.add(writer.branch("asset",List.of(identifier(resource.registry(),2),identifier(resource.identifier(),2)),1));}
        else values.add(writer.atom("asset",null,1));
        values.add(gear.cameraOverlay().isEmpty()?writer.atom("overlay",null,1):identifier(gear.cameraOverlay().get(),1));
        values.add(gear.allowedEntities().isEmpty()?writer.atom("allowed",null,1):holderSet(gear.allowedEntities().get(),1));
        values.add(writer.atom("dispensable",Boolean.toString(gear.dispensable()),1));values.add(writer.atom("swappable",Boolean.toString(gear.swappable()),1));
        values.add(writer.atom("damageOnHurt",Boolean.toString(gear.damageOnHurt()),1));values.add(writer.atom("equipOnInteract",Boolean.toString(gear.equipOnInteract()),1));values.add(writer.atom("canBeSheared",Boolean.toString(gear.canBeSheared()),1));
        return writer.branch("equippable",values,0);
    }
    Value repairable(Object object){
        if(!nativeType(object,net.minecraft.world.item.enchantment.Repairable.class))throw new Unsupported();
        return holderSet(((net.minecraft.world.item.enchantment.Repairable)object).items(),0);
    }
    Value resistant(Object object){
        if(!nativeType(object,net.minecraft.world.item.component.DamageResistant.class))throw new Unsupported();
        return holderSet(((net.minecraft.world.item.component.DamageResistant)object).types(),0);
    }
    Value compost(Object object){
        if(!nativeType(object,net.minecraft.world.item.component.Compostable.class))throw new Unsupported();
        return fuelInt(((net.minecraft.world.item.component.Compostable)object).layers(),0);
    }
    Value food(Object object){
        if(!nativeType(object,net.minecraft.world.food.FoodProperties.class))throw new Unsupported();var food=(net.minecraft.world.food.FoodProperties)object;
        return writer.branch("food",List.of(writer.atom("nutrition",Integer.toString(food.nutrition()),1),writer.atom("saturation-bits",Integer.toString(Float.floatToRawIntBits(food.saturation())),1),writer.atom("always",Boolean.toString(food.canAlwaysEat()),1)),0);
    }
    Value consumable(Object object){
        if(!nativeType(object,net.minecraft.world.item.component.Consumable.class))throw new Unsupported();var food=(net.minecraft.world.item.component.Consumable)object;
        if(!list(food.onConsumeEffects())||!food.onConsumeEffects().isEmpty()||food.animation()==null)throw new Unsupported();
        return writer.branch("consumable-no-effects",List.of(writer.atom("seconds-bits",Integer.toString(Float.floatToRawIntBits(food.consumeSeconds())),1),writer.atom("animation",food.animation().name(),1),holder(food.sound(),1),writer.atom("particles",Boolean.toString(food.hasConsumeParticles()),1)),0);
    }
    Value trim(Object object){
        if(!nativeType(object,net.minecraft.world.item.equipment.trim.ArmorTrim.class))throw new Unsupported();var trim=(net.minecraft.world.item.equipment.trim.ArmorTrim)object;
        return writer.branch("trim",List.of(holder(trim.material(),1),holder(trim.pattern(),1)),0);
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
