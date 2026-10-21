package dev.veltraph.client.mining.vein;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.mining.vein.VeinWatchValueGraph.*;

final class VeinWatchValueGraphTest {
    private static Value namedLoot(String name,String lore,String id,int quantity) {
        var writer=new Writer();
        var title=writer.branch("component",List.of(writer.atom("literal",name,1)),0);
        var lines=writer.branch("lore",List.of(writer.atom("literal",lore,1)),0);
        var extra=writer.branch("nbt-compound",List.of(
                writer.branch("entry",List.of(writer.atom("key","id",2),writer.atom("nbt-string",id,2)),1),
                writer.branch("entry",List.of(writer.atom("key","quantity",2),writer.atom("nbt-int",Integer.toString(quantity),2)),1)),0);
        return writer.branch("components",List.of(title,lines,extra),0);
    }
    @Test void namedLoreAndExtraAttributesRemainIndependentAndExact() {
        var original=namedLoot("Example mineral","Example descriptive lore","EXAMPLE_MINERAL",2);
        assertEquals(original,namedLoot("Example mineral","Example descriptive lore","EXAMPLE_MINERAL",2));
        assertNotEquals(original,namedLoot("Other name","Example descriptive lore","EXAMPLE_MINERAL",2));
        assertNotEquals(original,namedLoot("Example mineral","Other descriptive lore","EXAMPLE_MINERAL",2));
        assertNotEquals(original,namedLoot("Example mineral","Example descriptive lore","OTHER_EXAMPLE",2));
        assertNotEquals(original,namedLoot("Example mineral","Example descriptive lore","EXAMPLE_MINERAL",3));
    }
    @Test void allArrayTypesAreDetachedAndObserveLaterChangedValues() {
        byte[] bytes={1,2};int[] ints={3,4};long[] longs={5,6};
        var byteBefore=new Writer().bytes(bytes,0);var intBefore=new Writer().ints(ints,0);var longBefore=new Writer().longs(longs,0);
        bytes[0]=7;ints[0]=8;longs[0]=9;
        assertNotEquals(byteBefore,new Writer().bytes(bytes,0));assertNotEquals(intBefore,new Writer().ints(ints,0));assertNotEquals(longBefore,new Writer().longs(longs,0));
        assertEquals("1",byteBefore.children().getFirst().scalar());assertEquals("3",intBefore.children().getFirst().scalar());assertEquals("5",longBefore.children().getFirst().scalar());
    }
    @Test void rawArrayRestoreCannotProveHistoryAndNativeRevisionStillMustStayCurrent() {
        int[] raw={1};var detached=new Writer().ints(raw,0);var registry=new VeinWatchedRevisions.Registry();Object actual=new Object();var token=registry.watch(actual);
        raw[0]=2;registry.mutation(actual);raw[0]=1;registry.mutation(actual);
        assertEquals(detached,new Writer().ints(raw,0));assertFalse(token.current());
    }
    @Test void unknownAndTransientTypeSubstitutesAreRejectedWithoutInvokingThem() {
        class UnsupportedType { boolean isTransient(){throw new AssertionError("Must not call unknown type");} }
        Object nativeName=new Object(),nativeData=new Object();Object unsupported=new UnsupportedType();
        assertFalse(oneOf(unsupported,nativeName,nativeData));assertTrue(oneOf(nativeData,nativeName,nativeData));
        assertFalse(oneOf(new String("name"),new String("name")));
    }
    @Test void classAndLoaderChecksPrecedeNativeFieldReads() {
        assertTrue(exactClass("literal",String.class,null));assertFalse(exactClass(new StringBuilder("literal"),String.class,null));
        assertFalse(exactClass("literal",String.class,new ClassLoader(){}));assertFalse(exactClass(null,String.class,null));
    }
    @Test void depthNodeAndTextOverflowRefuseBeforeUnboundedCapture() {
        assertThrows(Unsupported.class,()->new Writer().atom("text","x",17));
        assertThrows(Unsupported.class,()->new Writer().ints(new int[4096],0));
        assertThrows(Unsupported.class,()->new Writer().atom("text","x".repeat(8193),0));
        var writer=new Writer();for(int i=0;i<8;i++)writer.atom("text","x".repeat(8192),0);
        assertThrows(Unsupported.class,()->writer.atom("text","x",0));
    }
    @Test void nativeNbtTypesAndRawNumericBitsRemainDistinct() {
        assertNotEquals(new Writer().atom("nbt-byte","1",0),new Writer().atom("nbt-int","1",0));
        assertNotEquals(new Writer().atom("nbt-float-bits",Integer.toString(Float.floatToRawIntBits(0f)),0),
                new Writer().atom("nbt-float-bits",Integer.toString(Float.floatToRawIntBits(-0f)),0));
    }
    private static Value fuelReference(String burnRegistry,String burnKey,String speedRegistry,String speedKey) {
        var writer=new Writer();
        var burn=writer.branch("fuel-int-reference",List.of(writer.atom("registry",burnRegistry,2),writer.atom("identifier",burnKey,2)),1);
        var speed=writer.branch("fuel-float-reference",List.of(writer.atom("registry",speedRegistry,2),writer.atom("identifier",speedKey,2)),1);
        return writer.branch("cooking-fuel",List.of(burn,speed),0);
    }
    @Test void pinnedFuelComponentCanBeIncludedWithoutAcceptingOtherComponentTypes() {
        Object name=new Object(),fuel=new Object(),unknown=new Object();
        assertTrue(oneOf(fuel,name,fuel));assertFalse(oneOf(unknown,name,fuel));
        var original=fuelReference("example:int_provider","example:wool_time","example:float_provider","example:default_speed");
        assertEquals(original,fuelReference("example:int_provider","example:wool_time","example:float_provider","example:default_speed"));
        assertNotEquals(original,fuelReference("example:int_provider","example:other_time","example:float_provider","example:default_speed"));
        assertNotEquals(original,fuelReference("example:other_registry","example:wool_time","example:float_provider","example:default_speed"));
        assertNotEquals(original,fuelReference("example:int_provider","example:wool_time","example:float_provider","example:other_speed"));
    }
    @Test void constantFuelChangesAndReferenceVersusConstantRemainExact() {
        var first=new Writer().branch("cooking-fuel",List.of(new Writer().atom("fuel-int-constant","100",1),
                new Writer().atom("fuel-float-constant-bits",Integer.toString(Float.floatToRawIntBits(1f)),1)),0);
        var changedBurn=new Writer().branch("cooking-fuel",List.of(new Writer().atom("fuel-int-constant","101",1),
                new Writer().atom("fuel-float-constant-bits",Integer.toString(Float.floatToRawIntBits(1f)),1)),0);
        var changedSpeed=new Writer().branch("cooking-fuel",List.of(new Writer().atom("fuel-int-constant","100",1),
                new Writer().atom("fuel-float-constant-bits",Integer.toString(Float.floatToRawIntBits(2f)),1)),0);
        assertNotEquals(first,changedBurn);assertNotEquals(first,changedSpeed);
        assertNotEquals(first,fuelReference("example:int_provider","example:wool_time","example:float_provider","example:default_speed"));
    }

}
