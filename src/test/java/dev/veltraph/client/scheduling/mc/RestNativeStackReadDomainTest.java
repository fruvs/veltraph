package dev.veltraph.client.scheduling.mc;
import org.junit.jupiter.api.Test;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.component.DataComponentMap;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Isolated negative reader-domain inputs, never an installed SDK or permission proof. */
class RestNativeStackReadDomainTest {
    @Test void exactTrustedNativeMapClassesAreRecognizedWithoutIteration(){assertTrue(RestNativeStackReadDomain.knownMap(Reference2ObjectMaps.emptyMap()));assertTrue(RestNativeStackReadDomain.knownMap(new Reference2ObjectArrayMap<>()));assertTrue(RestNativeStackReadDomain.knownMap(new Reference2ObjectOpenHashMap<>()));assertFalse(RestNativeStackReadDomain.knownMap(new HashMap<>()));}
    @Test void nativeMapSubclassCannotSupplyAnUnknownIterator(){class Foreign extends Reference2ObjectArrayMap<Object,Object>{@Override public Reference2ObjectMap.FastEntrySet<Object,Object> reference2ObjectEntrySet(){throw new AssertionError("Foreign iterator entrypoint called");}}assertFalse(RestNativeStackReadDomain.knownMap(new Foreign()));}
    @Test void nativeEntryCacheCannotBeTransplantedFromAnotherMap()throws Exception{
        var original=new Reference2ObjectArrayMap<Object,Object>();var other=new Reference2ObjectArrayMap<Object,Object>();
        var cache=Reference2ObjectArrayMap.class.getDeclaredField("entries");cache.setAccessible(true);cache.set(original,other.reference2ObjectEntrySet());
        assertFalse(RestNativeStackReadDomain.knownMap(original));cache.set(original,null);assertTrue(RestNativeStackReadDomain.knownMap(original));
    }
    @Test void unknownPrototypeRefusesBeforeCallingAnyAccessor()throws Exception{assertTrue(RestNativeStackReadDomain.knownPrototype(DataComponentMap.EMPTY));assertFalse(RestNativeStackReadDomain.knownPrototype(new Object()));assertFalse(RestNativeStackReadDomain.current(null));}
}
