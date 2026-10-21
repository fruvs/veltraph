package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Exercises the production closed direct-field reader. No fake native installation or positive SDK proof. */
class RestNativeComponentValueDomainTest {
    static Object read(Object value)throws Exception{var type=Class.forName("dev.veltraph.client.mining.vein.RestNativeComponentValues$Reader");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var reader=constructor.newInstance();var read=type.getDeclaredMethod("read",Object.class,int.class);read.setAccessible(true);return read.invoke(reader,value,0);}
    @Test void mutableKnownListIsCopiedByValueRatherThanAliased()throws Exception{var list=new ArrayList<Object>(List.of("original",7));var before=read(list);list.set(0,"changed");assertNotEquals(before,read(list));list.set(0,"original");assertEquals(before,read(list));}
    @Test void unknownCollectionAndUserObjectsAreRefusedWithoutCallingTheirGetters(){class Unknown extends ArrayList<Object>{@Override public Iterator<Object> iterator(){throw new AssertionError("Unknown iterator called");}}assertThrows(InvocationTargetException.class,()->read(new Unknown()));assertThrows(InvocationTargetException.class,()->read(new Object()));}
    @Test void exactInstalledCollectionClassesSupportVanillaImmutableListsAndEmptyTooltipSet()throws Exception{assertEquals(read(List.of("a","b")),read(List.of("a","b")));assertEquals(read(com.google.common.collect.ImmutableList.of("a","b")),read(com.google.common.collect.ImmutableList.of("a","b")));assertEquals(read(com.google.common.collect.ImmutableSet.of("a","b")),read(com.google.common.collect.ImmutableSet.of("a","b")));assertNotNull(read(it.unimi.dsi.fastutil.objects.ReferenceSortedSets.emptySet()));}
    @Test void sameNamedForeignLoaderCollectionCannotEnterInstalledGuavaDomain()throws Exception{
        // A minimal ArrayList subclass with the historical Guava name; it is negative input only.
        byte[] bytes=Base64.getDecoder().decode("yv66vgAAADQACgEALmNvbS9nb29nbGUvY29tbW9uL2NvbGxlY3QvUmVndWxhckltbXV0YWJsZUxpc3QHAAEBABNqYXZhL3V0aWwvQXJyYXlMaXN0BwADAQAGPGluaXQ+AQADKClWDAAFAAYKAAQABwEABENvZGUAIQACAAQAAAAAAAEAAQAFAAYAAQAJAAAAEQABAAEAAAAFKrcACLEAAAAAAAA=");
        class ForeignLoader extends ClassLoader {Class<?> define(){return defineClass("com.google.common.collect.RegularImmutableList",bytes,0,bytes.length);}}
        var foreign=new ForeignLoader().define();assertEquals(com.google.common.collect.ImmutableList.of("a","b").getClass().getName(),foreign.getName());
        assertNotSame(com.google.common.collect.ImmutableList.of("a","b").getClass(),foreign);assertThrows(InvocationTargetException.class,()->read(foreign.getConstructor().newInstance()));
    }
    @Test void scalarFloatBitsAndOptionalValuesRemainExact()throws Exception{assertNotEquals(read(0.0f),read(-0.0f));assertNotEquals(read(Optional.of("before")),read(Optional.of("after")));assertEquals(read(Optional.empty()),read(Optional.empty()));}
    @Test void recursiveOrUnboundedGraphsCannotBecomeFreshnessEvidence(){var recursive=new ArrayList<Object>();recursive.add(recursive);assertThrows(InvocationTargetException.class,()->read(recursive));var many=new ArrayList<Object>();for(int i=0;i<1100;i++)many.add(i);assertThrows(InvocationTargetException.class,()->read(many));}
}
