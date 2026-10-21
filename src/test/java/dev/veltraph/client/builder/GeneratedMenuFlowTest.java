package dev.veltraph.client.builder;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedMenuFlowTest {
    private MenuSnapshot menu(String title,int revision){return new MenuSnapshot(7,title,List.of(new MenuSnapshot.Slot(10,"Bound Button",List.of(),false)),revision);}
    @Test void clickingCannotAdvanceUntilNativeRevisionAndMaterialIncrease(){var r=new GeneratedMenuFlow(List.of(new GeneratedMenuFlow.Step("BUY_RESOURCE","Bound Tool","Bound Button","","minecraft:dirt",64)));assertEquals(GeneratedMenuFlow.Action.CLICK,r.tick(menu("Bound Tool",1),0,0).action());assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(menu("Bound Tool",1),64,100).action());assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(menu("Bound Tool",2),0,200).action());assertEquals(GeneratedMenuFlow.Action.COMPLETE,r.tick(menu("Bound Tool",2),64,300).action());}
    @Test void sellingRequiresActualDeclaredItemDecreaseAndChangedMenu(){var r=new GeneratedMenuFlow(List.of(new GeneratedMenuFlow.Step("SELL_INVENTORY","Bound Bazaar","Bound Button","","minecraft:dirt",-64)));assertEquals(GeneratedMenuFlow.Action.CLICK,r.tick(menu("Bound Bazaar",1),64,0).action());assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(menu("Bound Bazaar",1),0,100).action());assertEquals(GeneratedMenuFlow.Action.COMPLETE,r.tick(menu("Bound Bazaar",2),0,200).action());}
    @Test void repeatedTitleAndNoReceiptCannotCompleteConfiguredTransition(){var r=new GeneratedMenuFlow(List.of(new GeneratedMenuFlow.Step("BUY_RESOURCE","Bound Tool","Bound Button","Bound Bazaar","",0)));assertEquals(GeneratedMenuFlow.Action.CLICK,r.tick(menu("Bound Tool",1),0,0).action());assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(menu("Bound Tool",2),0,100).action());assertEquals(GeneratedMenuFlow.Action.STOP,r.tick(menu("Bound Tool",2),0,3100).action());}
    @Test void invalidUnknownSourceStatesAndPlayerSlotsNeverClick(){assertThrows(IllegalArgumentException.class,()->GeneratedMenuFlow.decode(""));assertThrows(IllegalArgumentException.class,()->new GeneratedMenuFlow.Step("UNKNOWN","title","button","after","",0));var r=new GeneratedMenuFlow(List.of(new GeneratedMenuFlow.Step("BUY_RESOURCE","title","Bound Button","after","",0)));assertEquals(GeneratedMenuFlow.Action.STOP,r.tick(new MenuSnapshot(1,"title",List.of(new MenuSnapshot.Slot(5,"Bound Button",List.of(),true))),0,0).action());}
    @Test void inventoryDissolutionBindsOwnedSourceAndEmptyCraftInputWithCursorAndRevisionReceipts() {
        var pick=new GeneratedMenuFlow.Step("PICK_UP_BLOCK","Inventory","Dirt","","",0,9,true,"minecraft:dirt","picked");
        var put=new GeneratedMenuFlow.Step("PUT_IN_CRAFTING_TABLE","Inventory","","","",0,1,false,"minecraft:air","empty");
        var r=new GeneratedMenuFlow(List.of(pick,put));
        var before=new MenuSnapshot(0,"Inventory",List.of(new MenuSnapshot.Slot(9,"Dirt",List.of(),true,"minecraft:dirt",64,false),new MenuSnapshot.Slot(1,"",List.of(),false,"minecraft:air",0,false)),1);
        assertEquals(GeneratedMenuFlow.Action.CLICK,r.tick(before,64,"",0,0).action());
        assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(before,0,"minecraft:dirt",64,100).action());
        var picked=new MenuSnapshot(0,"Inventory",List.of(new MenuSnapshot.Slot(9,"",List.of(),true,"minecraft:air",0,false),new MenuSnapshot.Slot(1,"",List.of(),false,"minecraft:air",0,false)),2);
        assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(picked,0,"minecraft:dirt",64,200).action());
        assertEquals(GeneratedMenuFlow.Action.CLICK,r.tick(picked,0,"minecraft:dirt",64,300).action());
        assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(picked,0,"",0,400).action());
        var placed=new MenuSnapshot(0,"Inventory",List.of(new MenuSnapshot.Slot(1,"Dirt",List.of(),false,"minecraft:dirt",64,false)),3);
        assertEquals(GeneratedMenuFlow.Action.COMPLETE,r.tick(placed,0,"",0,500).action());
    }
    @Test void vanillaInventoryOpeningAndObservedCloseAreActualTransitions(){var r=new GeneratedMenuFlow(List.of(new GeneratedMenuFlow.Step("OPEN_INV_TO_DISSOLVE","@closed","","Inventory","",0),new GeneratedMenuFlow.Step("CLOSE_GUI_TO_LOOP","Inventory","","@closed","",0)));assertEquals(GeneratedMenuFlow.Action.OPEN_INVENTORY,r.tick(null,0,0).action());assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(menu("Inventory",1),0,100).action());assertEquals(GeneratedMenuFlow.Action.CLOSE,r.tick(menu("Inventory",1),0,200).action());assertEquals(GeneratedMenuFlow.Action.COMPLETE,r.tick(null,0,300).action());}
    @Test void marketOpeningIsExplicitAndNeedsTheObservedBoundMenu(){var r=new GeneratedMenuFlow(List.of(new GeneratedMenuFlow.Step("OPEN_BAZAAR","@closed","bazaar","Bound Bazaar","",0)));assertEquals(GeneratedMenuFlow.Action.OPEN_BAZAAR,r.tick(null,0,0).action());assertEquals(GeneratedMenuFlow.Action.NONE,r.tick(null,0,100).action());assertEquals(GeneratedMenuFlow.Action.COMPLETE,r.tick(menu("Bound Bazaar",1),0,200).action());assertThrows(IllegalArgumentException.class,()->new GeneratedMenuFlow.Step("OPEN_BAZAAR","@closed","other command","Bound Bazaar","",0));}
}
