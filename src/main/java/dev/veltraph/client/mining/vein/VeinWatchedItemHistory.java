package dev.veltraph.client.mining.vein;

import dev.veltraph.client.world.Vec3;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static dev.veltraph.client.mining.vein.VeinWatchNativeFields.*;

/** Private adjacent-collection movement watchpoint; never proves loot or grants native effects. */
final class VeinWatchedItemHistory {
    private VeinWatchedItemHistory() {}
    private record Leaf(DataComponentType<?> type, Object value) {}
    private record Name(String key, String fallback) {}
    private record Use(boolean sprint, boolean vibration, int speedBits) {}
    private record Swing(String type, int duration) {}
    static final class Snapshot {
        private final ItemEntity item;
        private final ItemStack stack;
        private final Object world, holder, data, dataItem, components;
        private final UUID uuid;
        private final int id, count;
        private final Vec3 position;
        private final List<Leaf> leaves;
        private final List<VeinWatchedRevisions.Token> tokens;
        private Snapshot(ItemEntity item, ItemStack stack, EntityFields entity, StackFields fields,
                         Object dataItem, List<Leaf> leaves, List<VeinWatchedRevisions.Token> tokens) {
            this.item=item; this.stack=stack; world=entity.veltraph$watchWorld(); uuid=entity.veltraph$watchUuid();
            id=entity.veltraph$watchId(); holder=fields.veltraph$watchItem(); data=entity.veltraph$watchData();
            this.dataItem=dataItem; components=fields.veltraph$watchComponents(); count=fields.veltraph$watchCount();
            var p=entity.veltraph$watchPosition(); position=new Vec3(p.x,p.y,p.z);
            this.leaves=List.copyOf(leaves); this.tokens=List.copyOf(tokens);
        }
        ItemEntity item() { return item; }
        ItemStack stack() { return stack; }
        Vec3 position() { return position; }
        int count() { return count; }
    }
    static Snapshot watch(ItemEntity item) {
        try { return capture(item); } catch (RuntimeException unsupported) { return null; }
    }
    private static Snapshot capture(ItemEntity item) {
        if (!((Object)item instanceof EntityFields entity) || !((Object)item instanceof ItemFields itemFields)
                || entity.veltraph$watchRemoval()!=null || !((Object)entity.veltraph$watchData() instanceof DataFields data)) return null;
        int accessor=itemFields.veltraph$watchItemAccessor().id();
        var items=data.veltraph$watchItems();
        if (accessor<0 || accessor>=items.length || !((Object)items[accessor] instanceof DataItemFields raw)
                || !(raw.veltraph$watchValue() instanceof ItemStack stack) || !((Object)stack instanceof StackFields fields)
                || !((Object)fields.veltraph$watchItem() instanceof HolderFields)
                || fields.veltraph$watchCount()<=0 || fields.veltraph$watchCount()>64
                || fields.veltraph$watchComponents().getClass()!=PatchedDataComponentMap.class
                || !((Object)fields.veltraph$watchComponents() instanceof ComponentsFields)) return null;
        var p=entity.veltraph$watchPosition();
        if (p==null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return null;
        var watched=new ArrayList<Object>();
        watched.add(item); watched.add(entity.veltraph$watchData()); watched.add(items[accessor]); watched.add(stack);
        watched.add(fields.veltraph$watchComponents()); watched.add(fields.veltraph$watchItem());
        var leaves=leaves(fields.veltraph$watchComponents(), watched);
        if (leaves==null) return null;
        var tokens=new ArrayList<VeinWatchedRevisions.Token>();
        for (var object : watched) {
            var token=VeinWatchedRevisions.watch(object); if (token==null) return null; tokens.add(token);
        }
        var result=new Snapshot(item,stack,entity,fields,items[accessor],leaves,tokens);
        return current(result) ? result : null;
    }
    /** No client/world/provider callbacks, codecs, custody, cleanup, writes, or native tick methods. */
    static boolean current(Snapshot saved) {
        try { return stable(saved); } catch (RuntimeException unsupported) { return false; }
    }
    private static boolean stable(Snapshot saved) {
        if (saved==null || saved.tokens.stream().anyMatch(token -> !token.current())
                || !((Object)saved.item instanceof EntityFields entity) || !((Object)saved.stack instanceof StackFields fields)
                || !((Object)entity.veltraph$watchData() instanceof DataFields data)) return false;
        var itemFields=(ItemFields)saved.item;
        int accessor=itemFields.veltraph$watchItemAccessor().id(); var items=data.veltraph$watchItems();
        if (entity.veltraph$watchRemoval()!=null || entity.veltraph$watchWorld()!=saved.world
                || entity.veltraph$watchUuid()!=saved.uuid || entity.veltraph$watchId()!=saved.id
                || entity.veltraph$watchData()!=saved.data || accessor<0 || accessor>=items.length
                || items[accessor]!=saved.dataItem || !((Object)items[accessor] instanceof DataItemFields raw)
                || raw.veltraph$watchValue()!=saved.stack || fields.veltraph$watchItem()!=saved.holder
                || fields.veltraph$watchComponents()!=saved.components || fields.veltraph$watchCount()!=saved.count) return false;
        var p=entity.veltraph$watchPosition();
        if (p==null || Double.doubleToLongBits(p.x)!=Double.doubleToLongBits(saved.position.x())
                || Double.doubleToLongBits(p.y)!=Double.doubleToLongBits(saved.position.y())
                || Double.doubleToLongBits(p.z)!=Double.doubleToLongBits(saved.position.z())) return false;
        var current=leaves(fields.veltraph$watchComponents(),null);
        if (current==null || !current.equals(saved.leaves)) return false;
        return saved.tokens.stream().allMatch(VeinWatchedRevisions.Token::current);
    }
    /* Bounded typed value graph. Every effective component is represented or admission fails.
       Raw Java collection mutation + reversion between reads is not observed by these native hooks. */
    private static boolean knownMap(it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> map) {
        return map!=null && (map==it.unimi.dsi.fastutil.objects.Reference2ObjectMaps.emptyMap()
                || map.getClass()==it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap.class
                || map.getClass()==it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap.class) && map.size()<=32;
    }
    private static List<Leaf> leaves(DataComponentMap map, List<Object> watched) {
        if (!(map instanceof ComponentsFields fields) || !knownMap(fields.veltraph$watchPatch())) return null;
        var prototype=fields.veltraph$watchPrototype();
        if (prototype!=DataComponentMap.EMPTY && (!(prototype instanceof PrototypeFields raw)
                || !prototype.getClass().getName().equals("net.minecraft.core.component.DataComponentMap$Builder$SimpleMap")
                || !knownMap(raw.veltraph$watchMap()))) return null;
        var result=new ArrayList<Leaf>();
        var graph=new VeinWatchGraphCapture(watched);
        for (var component : map) {
            if (result.size()>=32) return null;
            Object nativeValue=component.value(), value;
            var type=component.type();
            if (!VeinWatchValueGraph.oneOf(type,DataComponents.MAX_STACK_SIZE,DataComponents.REPAIR_COST,DataComponents.DAMAGE,DataComponents.MAX_DAMAGE,DataComponents.ENCHANTMENT_GLINT_OVERRIDE,DataComponents.RARITY,DataComponents.ITEM_MODEL,DataComponents.ITEM_NAME,DataComponents.CUSTOM_NAME,DataComponents.LORE,DataComponents.CUSTOM_DATA,DataComponents.ENCHANTMENTS,DataComponents.STORED_ENCHANTMENTS,DataComponents.ATTRIBUTE_MODIFIERS,DataComponents.TOOLTIP_DISPLAY,DataComponents.USE_EFFECTS,DataComponents.ATTACK_ANIMATION,DataComponents.INTERACT_ANIMATION,DataComponents.BREAK_SOUND,DataComponents.COOKING_FUEL)) return null;
            if ((type==DataComponents.MAX_STACK_SIZE || type==DataComponents.REPAIR_COST || type==DataComponents.DAMAGE || type==DataComponents.MAX_DAMAGE)
                    && (Object)nativeValue instanceof Integer) value=nativeValue;
            else if (type==DataComponents.ENCHANTMENT_GLINT_OVERRIDE && nativeValue instanceof Boolean) value=nativeValue;
            else if (type==DataComponents.RARITY && (Object)nativeValue instanceof Rarity rarity) value=rarity.name();
            else if (type==DataComponents.ITEM_MODEL && nativeValue!=null && nativeValue.getClass()==Identifier.class) {
                var id=(Identifier)nativeValue; value=id.getNamespace()+":"+id.getPath();
            } else if (type==DataComponents.ITEM_NAME || type==DataComponents.CUSTOM_NAME) value=graph.name(nativeValue);
            else if (type==DataComponents.LORE) value=graph.lore(nativeValue);
            else if (type==DataComponents.COOKING_FUEL) value=graph.fuel(nativeValue);
            else if (type==DataComponents.CUSTOM_DATA) value=graph.custom(nativeValue);
            else if (type==DataComponents.ENCHANTMENTS || type==DataComponents.STORED_ENCHANTMENTS) value=graph.enchantments(nativeValue);
            else if (type==DataComponents.ATTRIBUTE_MODIFIERS && nativeValue==ItemAttributeModifiers.EMPTY
                    && ((ItemAttributeModifiers)nativeValue).modifiers().isEmpty()) value="empty-attributes";
            else if (type==DataComponents.TOOLTIP_DISPLAY && nativeValue==TooltipDisplay.DEFAULT
                    && !((TooltipDisplay)nativeValue).hideTooltip() && ((TooltipDisplay)nativeValue).hiddenComponents().isEmpty()) value="default-tooltip";
            else if (type==DataComponents.USE_EFFECTS && nativeValue!=null && nativeValue.getClass()==UseEffects.class) {
                var use=(UseEffects)nativeValue; value=new Use(use.canSprint(),use.interactVibrations(),Float.floatToIntBits(use.speedMultiplier()));
            } else if ((type==DataComponents.ATTACK_ANIMATION || type==DataComponents.INTERACT_ANIMATION)
                    && nativeValue!=null && nativeValue.getClass()==SwingAnimation.class) {
                var swing=(SwingAnimation)nativeValue; value=new Swing(swing.type().name(),swing.duration());
            } else if (type==DataComponents.BREAK_SOUND && nativeValue!=null && nativeValue.getClass()==Holder.Reference.class
                    && nativeValue instanceof Holder.Reference<?> holder
                    && (Object)nativeValue instanceof HolderFields soundFields && soundFields.veltraph$watchKey()!=null) {
                value=soundFields.veltraph$watchKey().identifier().toString();
                if (watched!=null) watched.add(holder);
            } else return null;
            result.add(new Leaf(type,value));
        }
        // Native maps have stable native key iteration; compare by key identity without custom equals callbacks.
        return List.copyOf(result);
    }
}
