package dev.veltraph.client.greenhouse;

import java.util.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GreenhouseSelectedWaterResourcesTest {
    @BeforeAll static void registry(){GreenhouseDiagnosticAssociationTest.nativeRegistry();}
    static GreenhouseNativeInventory inventory(int resourceCount,ItemStack cursor){var items=GreenhouseDiagnosticAssociationTest.items();items.set(2,new ItemStack(Items.STICK));items.set(10,new ItemStack(Items.GLASS_BOTTLE,resourceCount));return GreenhouseNativeInventory.capture(items,GreenhouseDiagnosticAssociationTest.gear(),cursor,3,GreenhouseDiagnosticAssociationTest.registry);}
    @Test void exactSelectedProposalKeepsFullNativeIdentityAndDoesNotConsume(){var before=inventory(3,ItemStack.EMPTY);var selection=GreenhouseSelectedWaterResources.select(before,2,10,1);assertEquals(3,selection.resourceBefore());assertEquals(2,selection.resourceAfter());assertEquals(before.inventorySlot(2).persistentEncoding(),selection.exactTool());assertEquals(before.inventorySlot(10).persistentEncoding(),selection.exactResource());assertEquals(3,before.inventorySlot(10).nativeCount());}
    @Test void stockExhaustionIsBoundedAndCannotCreateNegativeSupply(){var before=inventory(3,ItemStack.EMPTY);assertEquals(0,GreenhouseSelectedWaterResources.select(before,2,10,3).resourceAfter());assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(before,2,10,4));}
    @Test void sameSlotCannotBeBothToolAndResource(){assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(inventory(3,ItemStack.EMPTY),2,2,1));}
    @Test void absentOrEmptyResourceCannotAuthorizeAProposal(){assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(inventory(0,ItemStack.EMPTY),2,10,1));assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(inventory(3,ItemStack.EMPTY),2,100,1));}
    @Test void carriedCursorRefusesBeforePlanPublication(){assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(inventory(3,new ItemStack(Items.DIAMOND)),2,10,1));}
    @Test void originalUnitAndHotbarBoundsRemain(){var before=inventory(64,ItemStack.EMPTY);for(int units:List.of(-1,0,65,Integer.MAX_VALUE))assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(before,2,10,units));for(int slot:List.of(-1,9,100))assertThrows(IllegalArgumentException.class,()->GreenhouseSelectedWaterResources.select(before,slot,10,1));}
    @Test void completeEquipmentAndProtectedSlotsRemainExactlyUntouched(){var before=inventory(3,ItemStack.EMPTY);var projection=before.persistentProjection();GreenhouseSelectedWaterResources.select(before,2,10,1);assertEquals(projection,before.persistentProjection());assertEquals(7,before.inventorySlot(11).nativeCount());}
}
