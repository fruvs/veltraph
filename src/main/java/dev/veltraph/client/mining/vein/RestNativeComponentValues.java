package dev.veltraph.client.mining.vein;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.lang.reflect.*;

/** Closed, read-only immutable SDK value projection. No CODECs, formatting, unknown accessors or permission. */
public final class RestNativeComponentValues {
    private RestNativeComponentValues(){}
    private record Node(String kind,Object scalar,List<Node> children){}
    private static final class Identity {
        final Object value;Identity(Object value){this.value=value;}
        @Override public boolean equals(Object other){return other instanceof Identity same&&same.value==value;}
        @Override public int hashCode(){return System.identityHashCode(value);}
    }
    public static final class Snapshot {
        private final DataComponentType<?> type;private final Object original;private final Object value;
        private Snapshot(DataComponentType<?> type,Object original,Object value){this.type=type;this.original=original;this.value=value;}
        public boolean current(Object current){if(current!=original)return false;var again=project(type,current);return again!=null&&value.equals(again);}
    }
    public static Snapshot capture(DataComponentType<?> type,Object value){var projected=project(type,value);return projected==null?null:new Snapshot(type,value,projected);}
    private static Object project(DataComponentType<?> type,Object value){try{
        var graph=new VeinWatchGraphCapture(null);
        if(type==DataComponents.ITEM_NAME||type==DataComponents.CUSTOM_NAME)return graph.name(value);
        if(type==DataComponents.LORE)return graph.lore(value);
        if(type==DataComponents.CUSTOM_DATA)return graph.custom(value);
        if(type==DataComponents.ENCHANTMENTS||type==DataComponents.STORED_ENCHANTMENTS)return graph.enchantments(value);
        if(type==DataComponents.COOKING_FUEL)return graph.fuel(value);
        return new Reader().read(value,0);
    }catch(ReflectiveOperationException|RuntimeException rejected){return null;}}
    // Trusted factory results pin the installed collection Class before any iterator runs.
    private static final Set<Class<?>> LIST_TYPES=Collections.unmodifiableSet(new HashSet<>(List.of(
        ArrayList.class,List.of().getClass(),List.of(1).getClass(),
        com.google.common.collect.ImmutableList.of().getClass(),com.google.common.collect.ImmutableList.of(1).getClass(),com.google.common.collect.ImmutableList.of(1,2).getClass())));
    private static final Set<Class<?>> SET_TYPES=Collections.unmodifiableSet(new HashSet<>(List.of(
        HashSet.class,LinkedHashSet.class,Set.of().getClass(),Set.of(1).getClass(),Collections.emptySet().getClass(),
        com.google.common.collect.ImmutableSet.of().getClass(),com.google.common.collect.ImmutableSet.of(1).getClass(),com.google.common.collect.ImmutableSet.of(1,2).getClass(),
        it.unimi.dsi.fastutil.objects.ReferenceSortedSets.emptySet().getClass())));
    private static final Map<String,List<String>> RECORDS=Map.ofEntries(
        Map.entry("net.minecraft.world.item.enchantment.Enchantable",List.of("value:I")),
        Map.entry("net.minecraft.world.item.enchantment.Repairable",List.of("items:Lnet/minecraft/core/HolderSet;")),
        Map.entry("net.minecraft.world.level.block.entity.BannerPatternLayers",List.of("layers:Ljava/util/List;")),
        Map.entry("net.minecraft.world.level.block.entity.BannerPatternLayers$Layer",List.of("pattern:Lnet/minecraft/core/Holder;","color:Lnet/minecraft/world/item/DyeColor;")),
        Map.entry("net.minecraft.world.entity.ai.attributes.AttributeModifier",List.of("id:Lnet/minecraft/resources/Identifier;","amount:D","operation:Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;")),
        Map.entry("net.minecraft.world.entity.ai.attributes.AttributeInstance$Packed",List.of("attribute:Lnet/minecraft/core/Holder;","baseValue:D","modifiers:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.AttackRange",List.of("minReach:F","maxReach:F","minCreativeReach:F","maxCreativeReach:F","hitboxMargin:F","mobFactor:F")),
        Map.entry("net.minecraft.world.item.component.BlocksAttacks$DamageReduction",List.of("horizontalBlockingAngle:F","type:Ljava/util/Optional;","base:F","factor:F")),
        Map.entry("net.minecraft.world.item.component.Bees",List.of("bees:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.BlocksAttacks$ItemDamageFunction",List.of("threshold:F","base:F","factor:F")),
        Map.entry("net.minecraft.world.item.component.BrewingFuel",List.of("uses:Lnet/minecraft/world/level/storage/loot/providers/number/ints/ResolvableInt;","speedMultiplier:Lnet/minecraft/world/level/storage/loot/providers/number/floats/ResolvableFloat;")),
        Map.entry("net.minecraft.world.item.component.BlockItemStateProperties",List.of("properties:Ljava/util/Map;")),
        Map.entry("net.minecraft.world.item.component.Compostable",List.of("layers:Lnet/minecraft/world/level/storage/loot/providers/number/ints/ResolvableInt;")),
        Map.entry("net.minecraft.world.item.component.ChargedProjectiles",List.of("items:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.BlocksAttacks",List.of("blockDelaySeconds:F","disableCooldownScale:F","damageReductions:Ljava/util/List;","itemDamage:Lnet/minecraft/world/item/component/BlocksAttacks$ItemDamageFunction;","bypassedBy:Ljava/util/Optional;","blockSound:Ljava/util/Optional;","disableSound:Ljava/util/Optional;")),
        Map.entry("net.minecraft.world.item.component.Consumable",List.of("consumeSeconds:F","animation:Lnet/minecraft/world/item/ItemUseAnimation;","sound:Lnet/minecraft/core/Holder;","hasConsumeParticles:Z","onConsumeEffects:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.DamageResistant",List.of("types:Lnet/minecraft/core/HolderSet;")),
        Map.entry("net.minecraft.world.item.component.DeathProtection",List.of("deathEffects:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.CustomModelData",List.of("floats:Ljava/util/List;","flags:Ljava/util/List;","strings:Ljava/util/List;","colors:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.DebugStickState",List.of("properties:Ljava/util/Map;")),
        Map.entry("net.minecraft.world.item.component.CookingFuel",List.of("burnTime:Lnet/minecraft/world/level/storage/loot/providers/number/ints/ResolvableInt;","speedMultiplier:Lnet/minecraft/world/level/storage/loot/providers/number/floats/ResolvableFloat;")),
        Map.entry("net.minecraft.world.item.component.InstrumentComponent",List.of("instrument:Lnet/minecraft/core/Holder;")),
        Map.entry("net.minecraft.world.item.component.ItemAttributeModifiers$Display$OverrideText",List.of("component:Lnet/minecraft/network/chat/Component;")),
        Map.entry("net.minecraft.world.item.component.ItemAttributeModifiers$Display$Hidden",List.of()),
        Map.entry("net.minecraft.world.item.component.FireworkExplosion",List.of("shape:Lnet/minecraft/world/item/component/FireworkExplosion$Shape;","colors:Lit/unimi/dsi/fastutil/ints/IntList;","fadeColors:Lit/unimi/dsi/fastutil/ints/IntList;","hasTrail:Z","hasTwinkle:Z")),
        Map.entry("net.minecraft.world.item.component.DyedItemColor",List.of("rgb:I")),
        Map.entry("net.minecraft.world.item.component.Fireworks",List.of("flightDuration:I","explosions:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.ItemAttributeModifiers$Entry",List.of("attribute:Lnet/minecraft/core/Holder;","modifier:Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;","slot:Lnet/minecraft/world/entity/EquipmentSlotGroup;","display:Lnet/minecraft/world/item/component/ItemAttributeModifiers$Display;")),
        Map.entry("net.minecraft.world.item.component.ItemContainerContents$Slot",List.of("index:I","item:Lnet/minecraft/world/item/ItemStackTemplate;")),
        Map.entry("net.minecraft.world.item.component.ItemAttributeModifiers$Display$Default",List.of()),
        Map.entry("net.minecraft.world.item.component.ItemAttributeModifiers",List.of("modifiers:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.MapDecorations",List.of("decorations:Ljava/util/Map;")),
        Map.entry("net.minecraft.world.item.component.LodestoneTracker",List.of("target:Ljava/util/Optional;","tracked:Z")),
        Map.entry("net.minecraft.world.item.component.MobVisibility",List.of("targetingEntityTypes:Lnet/minecraft/core/HolderSet;","visibility:F")),
        Map.entry("net.minecraft.world.item.component.KineticWeapon$Condition",List.of("maxDurationTicks:I","minSpeed:F","minRelativeSpeed:F")),
        Map.entry("net.minecraft.world.item.component.ItemLore",List.of("lines:Ljava/util/List;","styledLines:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.OminousBottleAmplifier",List.of("value:I")),
        Map.entry("net.minecraft.world.item.component.ProvidesTrimMaterial",List.of("material:Lnet/minecraft/core/Holder;")),
        Map.entry("net.minecraft.world.item.component.MapDecorations$Entry",List.of("type:Lnet/minecraft/core/Holder;","x:D","z:D","rotation:F")),
        Map.entry("net.minecraft.world.item.component.SeededContainerLoot",List.of("lootTable:Lnet/minecraft/resources/ResourceKey;","seed:J")),
        Map.entry("net.minecraft.world.item.component.PiercingWeapon",List.of("dealsKnockback:Z","dismounts:Z","sound:Ljava/util/Optional;","hitSound:Ljava/util/Optional;")),
        Map.entry("net.minecraft.world.item.component.ResolvableProfile$Partial",List.of("name:Ljava/util/Optional;","id:Ljava/util/Optional;","properties:Lcom/mojang/authlib/properties/PropertyMap;")),
        Map.entry("net.minecraft.world.item.component.SulfurCubeContent",List.of("absorbedBlockItemStack:Lnet/minecraft/world/item/ItemStackTemplate;")),
        Map.entry("net.minecraft.world.item.component.SuspiciousStewEffects$Entry",List.of("effect:Lnet/minecraft/core/Holder;","duration:I")),
        Map.entry("net.minecraft.world.item.component.KineticWeapon",List.of("contactCooldownTicks:I","delayTicks:I","dismountConditions:Ljava/util/Optional;","knockbackConditions:Ljava/util/Optional;","damageConditions:Ljava/util/Optional;","forwardMovement:F","damageMultiplier:F","sound:Ljava/util/Optional;","hitSound:Ljava/util/Optional;")),
        Map.entry("net.minecraft.world.item.component.SwingAnimation",List.of("type:Lnet/minecraft/world/item/SwingAnimationType;","duration:I")),
        Map.entry("net.minecraft.world.item.component.TooltipDisplay",List.of("hideTooltip:Z","hiddenComponents:Ljava/util/SequencedSet;")),
        Map.entry("net.minecraft.world.item.component.Tool$Rule",List.of("blocks:Lnet/minecraft/core/HolderSet;","speed:Ljava/util/Optional;","correctForDrops:Ljava/util/Optional;")),
        Map.entry("net.minecraft.world.item.component.SuspiciousStewEffects",List.of("effects:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.UseRemainder",List.of("convertInto:Lnet/minecraft/world/item/ItemStackTemplate;")),
        Map.entry("net.minecraft.world.item.component.Tool",List.of("rules:Ljava/util/List;","defaultMiningSpeed:F","damagePerBlock:I","canDestroyBlocksInCreative:Z")),
        Map.entry("net.minecraft.world.item.component.UseEffects",List.of("canSprint:Z","interactVibrations:Z","speedMultiplier:F")),
        Map.entry("net.minecraft.world.item.component.UseCooldown",List.of("seconds:F","cooldownGroup:Ljava/util/Optional;")),
        Map.entry("net.minecraft.world.item.component.Weapon",List.of("itemDamagePerAttack:I","disableBlockingForSeconds:F")),
        Map.entry("net.minecraft.world.item.component.WritableBookContent",List.of("pages:Ljava/util/List;")),
        Map.entry("net.minecraft.world.item.component.WrittenBookContent",List.of("title:Lnet/minecraft/server/network/Filterable;","author:Ljava/lang/String;","generation:I","pages:Ljava/util/List;","resolved:Z")),
        Map.entry("net.minecraft.world.item.equipment.ArmorMaterial",List.of("durability:I","defense:Ljava/util/Map;","enchantmentValue:I","equipSound:Lnet/minecraft/core/Holder;","toughness:F","knockbackResistance:F","repairIngredient:Lnet/minecraft/tags/TagKey;","assetId:Lnet/minecraft/resources/ResourceKey;")),
        Map.entry("net.minecraft.world.item.equipment.trim.TrimMaterial",List.of("paletteId:Lnet/minecraft/resources/Identifier;","description:Lnet/minecraft/network/chat/Component;")),
        Map.entry("net.minecraft.world.item.equipment.Equippable",List.of("slot:Lnet/minecraft/world/entity/EquipmentSlot;","equipSound:Lnet/minecraft/core/Holder;","assetId:Ljava/util/Optional;","cameraOverlay:Ljava/util/Optional;","allowedEntities:Ljava/util/Optional;","dispensable:Z","swappable:Z","damageOnHurt:Z","equipOnInteract:Z","canBeSheared:Z","shearingSound:Lnet/minecraft/core/Holder;")),
        Map.entry("net.minecraft.world.item.equipment.trim.TrimPattern",List.of("assetId:Lnet/minecraft/resources/Identifier;","description:Lnet/minecraft/network/chat/Component;","decal:Z")),
        Map.entry("net.minecraft.world.item.equipment.trim.ArmorTrim",List.of("material:Lnet/minecraft/core/Holder;","pattern:Lnet/minecraft/core/Holder;"))
    );
    private static final class Reader {
        int nodes;
        private Node atom(String type,Object value){return new Node(type,value,List.of());}
        private Node branch(String type,List<Node> values){return new Node(type,null,List.copyOf(values));}
        Node read(Object value,int depth)throws ReflectiveOperationException{
            if(depth>16||++nodes>1024)throw new IllegalArgumentException("Unbounded native component");
            if(value==null)return atom("null","");Class<?> type=value.getClass();
            if(type==String.class||type==Boolean.class||type==Integer.class||type==Long.class||type==Short.class||type==Byte.class)return atom(type.getName(),value);
            if(type==Float.class)return atom("float-bits",Float.floatToRawIntBits((Float)value));if(type==Double.class)return atom("double-bits",Double.doubleToRawLongBits((Double)value));
            if(type==Identifier.class){var id=(Identifier)value;return atom("identifier",id.getNamespace()+":"+id.getPath());}
            if(type==ResourceKey.class){var key=(ResourceKey<?>)value;return branch("resource-key",List.of(read(key.registry(),depth+1),read(key.identifier(),depth+1)));}
            if(type==Optional.class){var optional=(Optional<?>)value;return branch("optional",optional.isPresent()?List.of(read(optional.get(),depth+1)):List.of());}
            if(type==UUID.class)return atom("uuid",value);
            if(type.isEnum()&&type.getClassLoader()==ItemStack.class.getClassLoader()&&type.getName().startsWith("net.minecraft."))return atom(type.getName(),((Enum<?>)value).name());
            String name=type.getName();
            if(value instanceof List<?> list&&LIST_TYPES.contains(type)){var out=new ArrayList<Node>();for(var item:list)out.add(read(item,depth+1));return branch(name,out);}
            if(value instanceof Set<?> set&&SET_TYPES.contains(type)){var out=new ArrayList<Node>();for(var item:set)out.add(read(item,depth+1));return branch(name,out);}
            if(type==Holder.Reference.class){return branch("holder-reference",List.of(read(field(type,value,"key"),depth+1),atom("registry-owner",new Identity(field(type,value,"owner"))),atom("registry-value",new Identity(field(type,value,"value")))));}
            if(type==HolderSet.Named.class){return branch("holder-set-named",List.of(read(field(type,value,"key"),depth+1),atom("registry-owner",new Identity(field(type,value,"owner"))),read(field(type,value,"contents"),depth+1)));}
            if(type==HolderSet.Direct.class)return branch("holder-set-direct",List.of(read(field(type,value,"contents"),depth+1)));
            if(name.equals("net.minecraft.tags.TagKey")&&type.isRecord()&&type.getClassLoader()==ItemStack.class.getClassLoader())return branch(name,List.of(read(field(type,value,"registry"),depth+1),read(field(type,value,"location"),depth+1)));
            var layout=RECORDS.get(name);
            if(layout==null||!type.isRecord()||type.getClassLoader()!=ItemStack.class.getClassLoader())throw new IllegalArgumentException("Unknown native value: "+name);
            var actual=new ArrayList<String>();for(var f:type.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()))actual.add(f.getName()+":"+f.getType().descriptorString());
            if(!actual.equals(layout))throw new IllegalArgumentException("Native record layout changed: "+name);
            var out=new ArrayList<Node>();for(var slot:layout)out.add(read(field(type,value,slot.substring(0,slot.indexOf(':'))),depth+1));return branch(name,out);
        }
        private static Object field(Class<?> type,Object value,String name)throws ReflectiveOperationException{var field=type.getDeclaredField(name);if(!field.trySetAccessible())throw new IllegalArgumentException("Native field inaccessible");return field.get(value);}
    }
}
