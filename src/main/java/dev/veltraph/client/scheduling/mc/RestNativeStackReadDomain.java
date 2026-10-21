package dev.veltraph.client.scheduling.mc;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.objects.*;
import java.lang.reflect.*;
import java.util.*;

/** Negative native-read domain veto before any ItemStack getter or component iterator. */
final class RestNativeStackReadDomain {
    private static final Class<?> SIMPLE_MAP=installedClass("net.minecraft.core.component.DataComponentMap$Builder$SimpleMap");
    private static final Class<?> EMPTY_MAP=Reference2ObjectMaps.emptyMap().getClass();
    private static final Class<?> ARRAY_ENTRIES=new Reference2ObjectArrayMap<>().reference2ObjectEntrySet().getClass();
    private static final Class<?> OPEN_ENTRIES=new Reference2ObjectOpenHashMap<>().reference2ObjectEntrySet().getClass();
    private static final Set<Class<?>> MAP_CLASSES=Collections.unmodifiableSet(new HashSet<>(List.of(
        Reference2ObjectMaps.emptyMap().getClass(),Reference2ObjectArrayMap.class,Reference2ObjectOpenHashMap.class)));
    private RestNativeStackReadDomain(){}
    static boolean current(ItemStack stack){
        if(stack==null||stack.getClass()!=ItemStack.class)return false;
        try{
            Object item=field(ItemStack.class,stack,"item");
            if(item==null?stack!=ItemStack.EMPTY:item.getClass()!=Holder.Reference.class&&item.getClass()!=Holder.Direct.class)return false;
            Object components=field(ItemStack.class,stack,"components");
            return components!=null&&components.getClass()==PatchedDataComponentMap.class
                &&knownPrototype(field(PatchedDataComponentMap.class,components,"prototype"))
                &&knownMap(field(PatchedDataComponentMap.class,components,"patch"));
        }catch(ReflectiveOperationException|RuntimeException rejected){return false;}
    }
    static boolean knownMap(Object map){
        if(map==null||!MAP_CLASSES.contains(map.getClass()))return false;
        if(map.getClass()==EMPTY_MAP)return true;
        try{
            Object entries=field(map.getClass(),map,"entries");
            if(entries==null)return true;
            Class<?> expected=map.getClass()==Reference2ObjectArrayMap.class?ARRAY_ENTRIES:OPEN_ENTRIES;
            return entries.getClass()==expected&&field(expected,entries,"this$0")==map;
        }catch(ReflectiveOperationException|RuntimeException rejected){return false;}
    }
    static boolean knownPrototype(Object prototype)throws ReflectiveOperationException{
        if(prototype==DataComponentMap.EMPTY)return true;
        return prototype!=null&&SIMPLE_MAP!=null&&prototype.getClass()==SIMPLE_MAP&&knownMap(field(SIMPLE_MAP,prototype,"map"));
    }
    private static Class<?> installedClass(String name){try{return Class.forName(name,false,ItemStack.class.getClassLoader());}catch(ClassNotFoundException unsupported){return null;}}
    private static Object field(Class<?> owner,Object value,String name)throws ReflectiveOperationException{
        var field=owner.getDeclaredField(name);if(!field.trySetAccessible())throw new IllegalArgumentException("Native storage field unavailable");return field.get(value);
    }
}
