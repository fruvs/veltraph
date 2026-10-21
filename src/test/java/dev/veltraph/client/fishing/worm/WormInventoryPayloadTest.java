package dev.veltraph.client.fishing.worm;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WormInventoryPayloadTest {
 List<WormOwnedCatch.Cell> before(){var b=new ArrayList<WormOwnedCatch.Cell>();for(int i=0;i<36;i++)b.add(new WormOwnedCatch.Cell("air",0,"air"));return b;}
 List<WormOwnedCatch.Cell> after(){var a=before();a.set(1,new WormOwnedCatch.Cell("cod",1,"cod-all-components"));return a;}
 @Test void exactTypedPayloadAndOnlyItsChangedCellAuthorize(){assertTrue(WormInventoryPayload.applied(new WormInventoryPayload.Payload(1,"cod-all-components",true),before(),after()));}
 @Test void ignoredForeignWindowOrWrongTargetCannotAuthorizeClientMutation(){assertFalse(WormInventoryPayload.applied(new WormInventoryPayload.Payload(1,"cod-all-components",false),before(),after()));assertFalse(WormInventoryPayload.applied(new WormInventoryPayload.Payload(2,"cod-all-components",true),before(),after()));}
 @Test void fullComponentMismatchCannotAuthorizeSameBaseItem(){assertFalse(WormInventoryPayload.applied(new WormInventoryPayload.Payload(1,"cod-different-components",true),before(),after()));}
 @Test void anotherCellMutationAtTailCannotBeHidden(){var changed=after();changed.set(3,new WormOwnedCatch.Cell("diamond",1,"diamond"));assertFalse(WormInventoryPayload.applied(new WormInventoryPayload.Payload(1,"cod-all-components",true),before(),changed));}
 @Test void unchangedOrUnsupportedContentPacketsAreUnavailable(){assertFalse(WormInventoryPayload.applied(new WormInventoryPayload.Payload(1,"air",true),before(),before()));assertFalse(WormInventoryPayload.applied(null,before(),after()));}
 @Test void nativeInventoryMenuMappingsExcludeArmorCursorAndForeignWindows(){assertEquals(1,WormInventoryPayload.inventoryIndexForMenuSlot(0,37));assertEquals(9,WormInventoryPayload.inventoryIndexForMenuSlot(0,9));assertEquals(-1,WormInventoryPayload.inventoryIndexForMenuSlot(-2,1));assertEquals(-1,WormInventoryPayload.inventoryIndexForMenuSlot(5,37));assertEquals(-1,WormInventoryPayload.inventoryIndexForMenuSlot(0,5));assertEquals(-1,WormInventoryPayload.inventoryIndexForMenuSlot(0,45));}
}
