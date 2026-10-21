package dev.veltraph.client.rewarp.mc;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static dev.veltraph.client.mining.vein.VeinWatchNativeFields.*;
import static dev.veltraph.client.rewarp.mc.IssuedReturnValueGraph.*;

/** Detached typed native graph. Every prototype AND patch entry is checked, including removed values. */
final class IssuedReturnStackGraph {
    private IssuedReturnStackGraph() {}
    private static final class Identity {
        final Object object;Identity(Object object){this.object=object;}
        @Override public boolean equals(Object other){return other instanceof Identity i&&i.object==object;}
        @Override public int hashCode(){return System.identityHashCode(object);}
    }
    private record Leaf(Identity type,Object value) {}
    private record Use(boolean sprint,boolean vibration,int speed) {}
    private record Swing(String type,int duration) {}
    private record Tooltip(boolean hidden,List<Identity> types) {}
    static final class Stack {
        final ItemStack nativeStack;final Object holder,components,prototype,patch;
        final int count;final List<Leaf> prototypeValues,patchValues;
        private Stack(ItemStack nativeStack,StackFields fields,ComponentsFields raw,List<Leaf> prototypeValues,List<Leaf> patchValues){
            this.nativeStack=nativeStack;holder=fields.veltraph$watchItem();components=fields.veltraph$watchComponents();count=fields.veltraph$watchCount();
            prototype=raw.veltraph$watchPrototype();patch=raw.veltraph$watchPatch();this.prototypeValues=prototypeValues;this.patchValues=patchValues;
        }
        boolean current(){
            if(nativeStack.getClass()!=ItemStack.class||!((Object)nativeStack instanceof StackFields fields)||fields.veltraph$watchCount()!=count
                ||fields.veltraph$watchItem()!=holder||fields.veltraph$watchComponents()!=components||!(components instanceof ComponentsFields raw)
                ||raw.veltraph$watchPrototype()!=prototype||raw.veltraph$watchPatch()!=patch)return false;
            var graph=new IssuedReturnGraphCapture(null);
            return prototypeValues.equals(prototype(raw.veltraph$watchPrototype(),graph,null))&&patchValues.equals(patch(raw.veltraph$watchPatch(),graph,null));
        }
    }
    static Stack capture(ItemStack stack,List<Object> watched){
        if(stack==null||stack.getClass()!=ItemStack.class||!((Object)stack instanceof StackFields fields)
            ||fields.veltraph$watchCount()<0||fields.veltraph$watchCount()>999||fields.veltraph$watchComponents().getClass()!=PatchedDataComponentMap.class
            ||!((Object)fields.veltraph$watchComponents() instanceof ComponentsFields raw))throw new Unsupported();
        if(fields.veltraph$watchCount()==0&&stack!=ItemStack.EMPTY)throw new Unsupported();
        if(stack==ItemStack.EMPTY&&(fields.veltraph$watchCount()!=0||fields.veltraph$watchItem()!=null))throw new Unsupported();
        if(fields.veltraph$watchItem()!=null&&(fields.veltraph$watchItem().getClass()!=net.minecraft.core.Holder.Reference.class||!((Object)fields.veltraph$watchItem() instanceof HolderFields)))throw new Unsupported();
        watched.add(stack);watched.add(fields.veltraph$watchComponents());if(fields.veltraph$watchItem()!=null)watched.add(fields.veltraph$watchItem());
        var graph=new IssuedReturnGraphCapture(watched);
        var prototype=prototype(raw.veltraph$watchPrototype(),graph,watched);var patch=patch(raw.veltraph$watchPatch(),graph,watched);
        return new Stack(stack,fields,raw,prototype,patch);
    }
    private static boolean known(it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> map){
        return map!=null&&(map==it.unimi.dsi.fastutil.objects.Reference2ObjectMaps.emptyMap()
            ||map.getClass()==it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap.class
            ||map.getClass()==it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap.class)&&map.size()<=128;
    }
    private static List<Leaf> prototype(DataComponentMap prototype,IssuedReturnGraphCapture graph,List<Object> watched){
        if(prototype==DataComponentMap.EMPTY)return List.of();
        if(!(prototype instanceof PrototypeFields raw)||prototype.getClass().getClassLoader()!=ItemStack.class.getClassLoader()||!prototype.getClass().getName().equals("net.minecraft.core.component.DataComponentMap$Builder$SimpleMap")
            ||!known(raw.veltraph$watchMap()))throw new Unsupported();
        if(watched!=null)watched.add(prototype);
        var result=new ArrayList<Leaf>();for(var entry:raw.veltraph$watchMap().entrySet())result.add(new Leaf(new Identity(entry.getKey()),value(entry.getKey(),entry.getValue(),graph)));
        return List.copyOf(result);
    }
    private static List<Leaf> patch(it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> patch,IssuedReturnGraphCapture graph,List<Object> watched){
        if(!known(patch))throw new Unsupported();var result=new ArrayList<Leaf>();
        for(var entry:patch.entrySet()){
            if(!(entry.getValue() instanceof Optional<?> optional))throw new Unsupported();
            // Removed unsupported types are also refused: they remain part of the persistent identity.
            if(!supported(entry.getKey()))throw new Unsupported();
            result.add(new Leaf(new Identity(entry.getKey()),optional.isEmpty()?"removed-component":value(entry.getKey(),optional.get(),graph)));
        }
        return List.copyOf(result);
    }
    private static boolean supported(Object type){return oneOf(type,
        DataComponents.MAX_STACK_SIZE,DataComponents.REPAIR_COST,DataComponents.DAMAGE,DataComponents.MAX_DAMAGE,
        DataComponents.ENCHANTMENT_GLINT_OVERRIDE,DataComponents.RARITY,DataComponents.ITEM_MODEL,
        DataComponents.ITEM_NAME,DataComponents.CUSTOM_NAME,DataComponents.LORE,DataComponents.CUSTOM_DATA,
        DataComponents.ENCHANTMENTS,DataComponents.STORED_ENCHANTMENTS,DataComponents.ATTRIBUTE_MODIFIERS,
        DataComponents.TOOLTIP_DISPLAY,DataComponents.USE_EFFECTS,DataComponents.ATTACK_ANIMATION,
        DataComponents.INTERACT_ANIMATION,DataComponents.BREAK_SOUND,DataComponents.COOKING_FUEL,
        DataComponents.TOOL,DataComponents.WEAPON,DataComponents.EQUIPPABLE,DataComponents.UNBREAKABLE,
        DataComponents.ENCHANTABLE,DataComponents.REPAIRABLE,DataComponents.BLOCK_TRANSFORMER,DataComponents.FOOD,
        DataComponents.VILLAGER_FOOD,DataComponents.CONSUMABLE,DataComponents.COMPOSTABLE,DataComponents.DAMAGE_RESISTANT,DataComponents.DYED_COLOR,DataComponents.TRIM);
    }
    private static Object value(Object type,Object value,IssuedReturnGraphCapture graph){
        if(!supported(type)||value==null)throw new Unsupported();
        if(oneOf(type,DataComponents.MAX_STACK_SIZE,DataComponents.REPAIR_COST,DataComponents.DAMAGE,DataComponents.MAX_DAMAGE)&&value.getClass()==Integer.class)return value;
        if(type==DataComponents.ENCHANTMENT_GLINT_OVERRIDE&&value.getClass()==Boolean.class)return value;
        if(type==DataComponents.RARITY&&value.getClass()==Rarity.class)return ((Rarity)value).name();
        if(type==DataComponents.ITEM_MODEL)return graph.identifier(value,0);
        if(type==DataComponents.ITEM_NAME||type==DataComponents.CUSTOM_NAME)return graph.name(value);
        if(type==DataComponents.LORE)return graph.lore(value);
        if(type==DataComponents.CUSTOM_DATA)return graph.custom(value);
        if(type==DataComponents.ENCHANTMENTS||type==DataComponents.STORED_ENCHANTMENTS)return graph.enchantments(value);
        if(type==DataComponents.ATTRIBUTE_MODIFIERS)return graph.attributes(value);
        if(type==DataComponents.COOKING_FUEL)return graph.fuel(value);
        if(type==DataComponents.TOOL)return graph.tool(value);
        if(type==DataComponents.WEAPON)return graph.weapon(value);
        if(type==DataComponents.EQUIPPABLE)return graph.equippable(value);
        if(type==DataComponents.BREAK_SOUND||type==DataComponents.BLOCK_TRANSFORMER)return graph.holder(value,0);
        if(type==DataComponents.ENCHANTABLE&&value.getClass()==net.minecraft.world.item.enchantment.Enchantable.class)return ((net.minecraft.world.item.enchantment.Enchantable)value).value();
        if(type==DataComponents.REPAIRABLE)return graph.repairable(value);
        if(type==DataComponents.FOOD)return graph.food(value);
        if(type==DataComponents.VILLAGER_FOOD&&value.getClass()==net.minecraft.world.food.VillagerFood.class)return ((net.minecraft.world.food.VillagerFood)value).nutrition();
        if(type==DataComponents.CONSUMABLE)return graph.consumable(value);
        if(type==DataComponents.COMPOSTABLE)return graph.compost(value);
        if(type==DataComponents.DAMAGE_RESISTANT)return graph.resistant(value);
        if(type==DataComponents.TRIM)return graph.trim(value);
        if(type==DataComponents.DYED_COLOR&&value.getClass()==net.minecraft.world.item.component.DyedItemColor.class)return ((net.minecraft.world.item.component.DyedItemColor)value).rgb();
        if(type==DataComponents.UNBREAKABLE&&value.getClass()==net.minecraft.util.Unit.class)return "unbreakable";
        if(type==DataComponents.USE_EFFECTS&&value.getClass()==UseEffects.class){var use=(UseEffects)value;return new Use(use.canSprint(),use.interactVibrations(),Float.floatToRawIntBits(use.speedMultiplier()));}
        if(oneOf(type,DataComponents.ATTACK_ANIMATION,DataComponents.INTERACT_ANIMATION)&&value.getClass()==SwingAnimation.class){var swing=(SwingAnimation)value;return new Swing(swing.type().name(),swing.duration());}
        if(type==DataComponents.TOOLTIP_DISPLAY&&value.getClass()==TooltipDisplay.class){
            var tooltip=(TooltipDisplay)value;var types=tooltip.hiddenComponents();
            if(types!=it.unimi.dsi.fastutil.objects.ReferenceSortedSets.<DataComponentType<?>>emptySet()&&types.getClass()!=java.util.LinkedHashSet.class)throw new Unsupported();
            if(types.size()>128)throw new Unsupported();
            var hidden=new ArrayList<Identity>();for(Object key:types){if(!(key instanceof DataComponentType<?>))throw new Unsupported();hidden.add(new Identity(key));}
            return new Tooltip(tooltip.hideTooltip(),List.copyOf(hidden));
        }
        throw new Unsupported();
    }
}
