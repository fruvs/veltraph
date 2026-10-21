package dev.veltraph.client.garden.pests;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PestBindingsTest {
    @Test void exactCounterRejectsDuplicatesMalformedAndShorthand(){
        assertEquals(12,PestBindings.count(List.of("Bag: 12"),"Bag: ").orElse(-1));
        assertTrue(PestBindings.count(List.of("Bag: 12","Bag: 13"),"Bag: ").isEmpty());
        for(String v:List.of("Bag: 12k","Bag: -1","Bag: 2.5","Bag: 99999999999"))assertTrue(PestBindings.count(List.of(v),"Bag: ").isEmpty());
    }
    @Test void currencyIsExactDecimalAndUnique(){
        assertEquals(new BigDecimal("1234.50"),PestBindings.amount(List.of("Price: 1,234.50 Copper"),"Price: ","Copper").orElseThrow());
        assertTrue(PestBindings.amount(List.of("Price: 1k Copper"),"Price: ","Copper").isEmpty());
        assertTrue(PestBindings.amount(List.of("Price: 5 Coins"),"Price: ","Copper").isEmpty());
        assertTrue(PestBindings.amount(List.of("Price: 5 Copper","Price: 6 Copper"),"Price: ","Copper").isEmpty());
    }
    @Test void opaqueIdentityAndBindingsAreExplicit(){
        assertThrows(IllegalArgumentException.class,()->new PestBindings.Item("minecraft:wheat","Compost",""));
        assertThrows(IllegalArgumentException.class,()->PestBindings.read("",PestBindings.Trap.class));
        var i=PestBindings.read("{\"registryId\":\"minecraft:wheat\",\"name\":\"Compost\",\"skyblockId\":\"COMPOST\"}",PestBindings.Item.class);
        assertEquals("COMPOST",i.skyblockId());
    }
    @Test void cleanerBoundsAndExactModelIdentityRequired(){
        var tool=new PestBindings.Item("minecraft:stick","Vacuum","VACUUM");
        assertThrows(IllegalArgumentException.class,()->new PestBindings.Cleaner(tool,"Bag: ",new PestBindings.Entity("minecraft:armor_stand","Fly",""),1,4,500,"Captured"));
        assertThrows(IllegalArgumentException.class,()->new PestBindings.Cleaner(tool,"Bag: ",new PestBindings.Entity("minecraft:armor_stand","Fly","model"),9,4,500,"Captured"));
        assertThrows(IllegalArgumentException.class,()->new PestBindings.Cleaner(tool,"Bag: ",new PestBindings.Entity("minecraft:armor_stand","Fly","model"),1,100,500,"Captured"));
    }
}
