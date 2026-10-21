package dev.veltraph.client.capture.tool;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import dev.veltraph.client.mining.vein.VeinWatchGraphFields;
/** Bounded read-only native view. No codec, full component serialization, deep copy or unknown value rendering. */
final class SelectedToolNativeObservation {
 record Observation(JsonObject components,List<String> lore,JsonArray componentTypes,boolean truncated) {}
 private static final class Budget {int nodes;boolean truncated;}
 static Observation read(ItemStack stack){
  var schema=new JsonObject();var componentTypes=new JsonArray();var budget=new Budget();int n=0;
  for(var component:stack.getComponents()){
   if(++n>128){budget.truncated=true;break;}String id=BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString();var type=new JsonObject();type.addProperty("componentId",id);type.addProperty("nativeValueType",component.value()==null?"null":component.value().getClass().getName());componentTypes.add(type);
  }
  var custom=stack.get(DataComponents.CUSTOM_DATA);
  if((Object)custom instanceof VeinWatchGraphFields.Custom access&&custom.getClass()==net.minecraft.world.item.component.CustomData.class){
   var tag=access.veltraph$watchTag();schema.add("minecraft:custom_data",readTag(tag,List.of("minecraft:custom_data"),0,budget));
  }else if(custom!=null)budget.truncated=true;
  var lore=new ArrayList<String>();var nativeLore=stack.get(DataComponents.LORE);if(nativeLore!=null){int i=0;for(var line:nativeLore.lines()){
   if(++i>80){budget.truncated=true;break;}String text=literal(line,0,new int[]{0});if(text!=null)lore.add(text);else budget.truncated=true;
  }}
  return new Observation(schema,List.copyOf(lore),componentTypes,budget.truncated);
 }
 /** Complete bounded custom-data/schema identity without treating unselected translated lore as missing identity. Old read() remains unchanged. */
 static Observation probeIdentity(ItemStack stack){
  var schema=new JsonObject();var types=new JsonArray();var budget=new Budget();int n=0;
  for(var component:stack.getComponents()){
   if(++n>128){budget.truncated=true;break;}var row=new JsonObject();row.addProperty("componentId",BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString());
   row.addProperty("nativeValueType",component.value()==null?"null":component.value().getClass().getName());types.add(row);
  }
  var custom=stack.get(DataComponents.CUSTOM_DATA);
  if((Object)custom instanceof VeinWatchGraphFields.Custom access&&custom.getClass()==net.minecraft.world.item.component.CustomData.class)
   schema.add("minecraft:custom_data",readTag(access.veltraph$watchTag(),List.of("minecraft:custom_data"),0,budget));
  else if(custom!=null)budget.truncated=true;
  return new Observation(schema,List.of(),types,budget.truncated);
 }
 record WateringObservation(JsonObject values,JsonObject nativeTypes,Map<Integer,String> indexedLore,boolean complete) {}
 static WateringObservation readWatering(ItemStack stack,Set<List<String>> selected){
  var values=new JsonObject();var types=new JsonObject();var budget=new Budget();var custom=stack.get(DataComponents.CUSTOM_DATA);
  if((Object)custom instanceof VeinWatchGraphFields.Custom access&&custom.getClass()==net.minecraft.world.item.component.CustomData.class)
   readSelected(access.veltraph$watchTag(),List.of("minecraft:custom_data"),0,budget,selected,values,types);
  else if(!selected.isEmpty())budget.truncated=true;
  var indexed=new TreeMap<Integer,String>();var nativeLore=stack.get(DataComponents.LORE);
  if(nativeLore!=null){int index=0;for(var line:nativeLore.lines()){
   if(index>=80){budget.truncated=true;break;}String text=literal(line,0,new int[]{0});if(text!=null)indexed.put(index,text);index++;
  }}
  return new WateringObservation(values,types,Collections.unmodifiableMap(indexed),!budget.truncated);
 }
 private static void readSelected(Tag tag,List<String> path,int depth,Budget b,Set<List<String>> selected,JsonObject values,JsonObject types){
  if(tag==null||depth>6||b.nodes>=512){b.truncated=true;return;}b.nodes++;
  if(selected.contains(path)){
   var number=selectedNumber(tag);if(number==null){b.truncated=true;return;}
   String exact=String.join(".",path);values.add(exact,number);types.addProperty(exact,tag.getClass().getSimpleName());return;
  }
  if(tag.getClass()==CompoundTag.class&&(Object)tag instanceof VeinWatchGraphFields.Compound access){
   var map=access.veltraph$watchTags();if(map==null){b.truncated=true;return;}int entries=0;
   for(var e:map.entrySet()){
    if(++entries>128||b.nodes>=512){b.truncated=true;break;}
    if(!(e.getKey() instanceof String key)||!SelectedToolCaptureSchema.safeField(key)||!(e.getValue() instanceof Tag value))continue;
    var next=new ArrayList<>(path);next.add(key);var immutable=List.copyOf(next);
    // Only descend the explicitly selected exact segment paths. Unknown unrelated values are not read.
    if(selected.stream().anyMatch(p->p.size()>=immutable.size()&&p.subList(0,immutable.size()).equals(immutable)))readSelected(value,immutable,depth+1,b,selected,values,types);
   }
  }
 }
 static JsonElement selectedNumber(Tag tag){
  JsonElement value;
  if(tag instanceof ByteTag t)value=new JsonPrimitive(t.value());else if(tag instanceof ShortTag t)value=new JsonPrimitive(t.value());
  else if(tag instanceof IntTag t)value=new JsonPrimitive(t.value());else if(tag instanceof LongTag t)value=new JsonPrimitive(t.value());
  else if(tag instanceof FloatTag t){if(!Float.isFinite(t.value()))return null;value=new JsonPrimitive(t.value());}
  else if(tag instanceof DoubleTag t){if(!Double.isFinite(t.value()))return null;value=new JsonPrimitive(t.value());}else return null;
  return SelectedWateringToolProbe.boundedNumber(value);
 }
 private static JsonElement readTag(Tag tag,List<String> path,int depth,Budget b){
  if(tag==null||depth>6||b.nodes>=512){b.truncated=true;return JsonNull.INSTANCE;}b.nodes++;
  if(tag.getClass()==CompoundTag.class&&(Object)tag instanceof VeinWatchGraphFields.Compound access){var result=new JsonObject();var map=access.veltraph$watchTags();if(map==null){b.truncated=true;return result;}int entries=0;
   for(var e:map.entrySet()){
    if(++entries>128||b.nodes>=512){b.truncated=true;break;}if(!(e.getKey() instanceof String key)||!SelectedToolCaptureSchema.safeField(key)||!(e.getValue() instanceof Tag value)){b.truncated=true;continue;}
    var next=new ArrayList<>(path);next.add(key);result.add(key,readTag(value,List.copyOf(next),depth+1,b));
   }return result;
  }
  if(tag.getClass()==ListTag.class&&(Object)tag instanceof VeinWatchGraphFields.NbtList access){var result=new JsonArray();var list=access.veltraph$watchList();if(list==null){b.truncated=true;return result;}int n=0;for(var x:list){if(++n>32||b.nodes>=512){b.truncated=true;break;}if(x instanceof Tag t)result.add(readTag(t,path,depth+1,b));else{b.truncated=true;result.add(JsonNull.INSTANCE);}}return result;}
  boolean knownId=path.equals(List.of("minecraft:custom_data","id"))||path.equals(List.of("minecraft:custom_data","ExtraAttributes","id"));
  boolean knownUuid=path.equals(List.of("minecraft:custom_data","uuid"))||path.equals(List.of("minecraft:custom_data","ExtraAttributes","uuid"));
  boolean knownCounter=path.size()==3&&path.get(0).equals("minecraft:custom_data")&&path.get(1).equals("ExtraAttributes")&&Set.of("farmed_cultivating","mined_crops").contains(path.get(2));
  if(tag instanceof StringTag s){String value=s.value();return new JsonPrimitive((knownId&&value.length()<=128)||(knownUuid&&value.length()==36)?value:"");}
  if(tag instanceof ByteTag v)return new JsonPrimitive(knownCounter?v.value():0);
  if(tag instanceof ShortTag v)return new JsonPrimitive(knownCounter?v.value():0);
  if(tag instanceof IntTag v)return new JsonPrimitive(knownCounter?v.value():0);
  if(tag instanceof LongTag v)return new JsonPrimitive(knownCounter?v.value():0L);
  if(tag instanceof FloatTag||tag instanceof DoubleTag)return new JsonPrimitive(0.5); // type only; never interpreted as integer counter
  if(tag instanceof ByteArrayTag||tag instanceof IntArrayTag||tag instanceof LongArrayTag)return new JsonArray();
  b.truncated=true;return JsonNull.INSTANCE;
 }
 private static String literal(Component c,int depth,int[] nodes){
  if(c==null||c.getClass()!=net.minecraft.network.chat.MutableComponent.class||depth>6||++nodes[0]>32)return null;String own="";
  if(c.getContents()!=PlainTextContents.EMPTY){if(!(c.getContents() instanceof PlainTextContents.LiteralContents contents)||!((Object)contents instanceof VeinWatchGraphFields.Literal access))return null;own=access.veltraph$watchText();if(own==null||own.length()>512)return null;}
  var text=new StringBuilder(own);int n=0;for(var sibling:c.getSiblings()){if(++n>16)return null;String s=literal(sibling,depth+1,nodes);if(s==null||text.length()+s.length()>512)return null;text.append(s);}return text.toString();
 }
}
