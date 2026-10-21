package dev.veltraph.client.mining;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CobbleDropCapacityTest {
    static List<CobbleDropCapacity.Cell> full(){var rows=new ArrayList<CobbleDropCapacity.Cell>();for(int i=0;i<36;i++)rows.add(new CobbleDropCapacity.Cell(false,false,64,64));return rows;}
    @Test void fullInventoryWithOneExactPartialCobbleStackStillHasAttributedCapacity(){var rows=full();rows.set(8,new CobbleDropCapacity.Cell(false,true,63,64));assertTrue(CobbleDropCapacity.available(false,rows));}
    @Test void componentMismatchAndFullCompatibleStackCannotMerge(){var rows=full();rows.set(8,new CobbleDropCapacity.Cell(false,false,1,64));assertFalse(CobbleDropCapacity.available(false,rows));rows.set(8,new CobbleDropCapacity.Cell(false,true,64,64));assertFalse(CobbleDropCapacity.available(false,rows));}
    @Test void enchantedToolRetainsEmptySlotOnlyConservatism(){var rows=full();rows.set(8,new CobbleDropCapacity.Cell(false,true,63,64));assertFalse(CobbleDropCapacity.available(true,rows));rows.set(9,new CobbleDropCapacity.Cell(true,false,0,64));assertTrue(CobbleDropCapacity.available(true,rows));}
}
