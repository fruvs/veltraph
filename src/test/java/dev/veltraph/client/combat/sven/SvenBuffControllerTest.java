package dev.veltraph.client.combat.sven;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvenBuffControllerTest {
    final SvenBindingProfile.Buff binding=new SvenBindingProfile.Buff("verified:menu","Verified item","Verified SkyBlock","Verified Cookie","Verified Cookie menu","Verified potion button","Verified Potions",List.of("Verified Jump Boost"),"Verified disabled","Verified Next","", ".*","start","end","inactive","row","marker","off");
    MenuSnapshot menu(String title,String label,List<String> lore,int revision){return new MenuSnapshot(2,title,List.of(new MenuSnapshot.Slot(10,label,lore,false)),revision);}
    SvenBuffController toPotions(){var flow=new SvenBuffController(binding);assertEquals(SvenBuffController.Kind.SELECT_SLOT_EIGHT,flow.tick(null,true,true).kind());flow.slotEightObserved(true);
        assertEquals(SvenBuffController.Kind.USE_MENU_ITEM,flow.tick(null,true,true).kind());flow.tick(menu("Verified SkyBlock","Verified Cookie",List.of(),1),false,true);
        assertEquals(SvenBuffController.Kind.CLICK,flow.tick(menu("Verified SkyBlock","Verified Cookie",List.of(),1),false,true).kind());flow.tick(menu("Verified Cookie menu","Verified potion button",List.of(),2),false,true);
        assertEquals(SvenBuffController.Kind.CLICK,flow.tick(menu("Verified Cookie menu","Verified potion button",List.of(),2),false,true).kind());flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Enabled"),3),false,true);return flow;}
    @Test void completeRequiresSkyblockCookiePotionLoreCloseAndNativeEffectOutcome(){
        var flow=toPotions();assertEquals(SvenBuffController.Kind.CLICK,flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Enabled"),3),false,true).kind());
        assertEquals(SvenBuffController.Kind.NONE,flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Enabled"),3),false,true).kind());
        flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Verified disabled"),4),false,true);
        flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Verified disabled"),4),false,true);
        assertEquals(SvenBuffController.Kind.CLOSE,flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Verified disabled"),4),false,true).kind());
        assertEquals(SvenBuffController.Kind.NONE,flow.tick(null,true,true).kind());assertEquals(SvenBuffController.Kind.COMPLETE,flow.tick(null,true,false).kind());
    }
    @Test void paginationAcknowledgmentIsObservedTitleChange(){
        var flow=toPotions();flow.tick(menu("Verified Potions 1","Other effect",List.of(),3),false,true);
        assertEquals(SvenBuffController.Kind.CLICK,flow.tick(menu("Verified Potions 1","Verified Next",List.of(),3),false,true).kind());
        assertEquals(SvenBuffController.Kind.NONE,flow.tick(menu("Verified Potions 1","Verified Next",List.of(),3),false,true).kind());
        flow.tick(menu("Verified Potions 2","Verified Jump Boost",List.of(),4),false,true);assertEquals(SvenBuffController.State.TOGGLE_POTIONS,flow.state());
    }
    @Test void missingSelectorsForeignMenusAndSlotSelectionFailuresCannotContinue(){
        var flow=new SvenBuffController(binding);flow.slotEightObserved(false);assertEquals(SvenBuffController.Kind.FAILED,flow.tick(null,true,true).kind());
        flow=toPotions();assertEquals(SvenBuffController.Kind.FAILED,flow.tick(menu("Unrelated menu","Verified Jump Boost",List.of(),4),false,true).kind());
        flow=toPotions();flow.tick(menu("Verified Potions 1","Other effect",List.of(),3),false,true);assertEquals(SvenBuffController.Kind.FAILED,flow.tick(menu("Verified Potions 1","Wrong next",List.of(),3),false,true).kind());
    }
    @Test void sourceFiveClickLimitDoesNotPretendDisabledOutcome(){
        var flow=toPotions();for(int revision=3;revision<8;revision++)assertEquals(SvenBuffController.Kind.CLICK,flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Enabled"),revision),false,true).kind());
        assertEquals(SvenBuffController.Kind.FAILED,flow.tick(menu("Verified Potions 1","Verified Jump Boost",List.of("Enabled"),8),false,true).kind());
    }
}
