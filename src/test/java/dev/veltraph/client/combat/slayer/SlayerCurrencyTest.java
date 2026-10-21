package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlayerCurrencyTest {
    private MenuSnapshot menu(int id,String title,String name,String... lore){return new MenuSnapshot(id,title,List.of(new MenuSnapshot.Slot(2,name,List.of(lore),false)));}
    @Test void oldCoinsDefaultAndExplicitMotesHaveSeparateAuthorization(){
        assertEquals(QuestCurrency.COINS,SlayerSettings.from(new MapConfigService()).currency());
        assertEquals(new SlayerQuestController.Quote(5000,QuestCurrency.MOTES),SlayerQuestController.quote(List.of("Cost:","5,000 Motes"),QuestCurrency.MOTES).quote().orElseThrow());
        assertTrue(SlayerQuestController.quote(List.of("Cost: 5,000 Motes"),QuestCurrency.COINS).quote().isEmpty());
        assertTrue(SlayerQuestController.quote(List.of("Cost: 5,000 Coins"),QuestCurrency.MOTES).quote().isEmpty());
    }
    @Test void contradictoryOrMalformedCostCannotBorrowAnEarlierPrice(){
        var config=new MapConfigService();config.set(SlayerConfig.SLAYER_QUEST_CURRENCY,QuestCurrency.MOTES);var settings=SlayerSettings.from(config);
        for(var lore:List.of(List.of("Cost: 500 Coins"),List.of("Cost: 500 Motes","Cost: 1 Motes"),List.of("Cost: -1 Motes"),List.of("Cost:","unreadable"))){
            var controller=new SlayerQuestController();controller.start(false,0);controller.tick(null,settings,0);
            controller.tick(menu(1,settings.rootTitle(),settings.bossLabel()),settings,300);
            assertEquals(SlayerQuestController.Kind.CLICK,controller.tick(menu(2,settings.bossTitle(),settings.tierLabel(),"Cost: 500 Motes"),settings,600).kind());
            assertEquals(SlayerQuestController.Kind.NONE,controller.tick(menu(3,settings.confirmTitle(),settings.confirmLabel(),lore.toArray(String[]::new)),settings,900).kind());
            assertEquals(SlayerQuestController.State.FAILED,controller.state());
        }
    }
    @Test void typedMotesStillNeedsActualStartAndCompletionReceipts(){
        var config=new MapConfigService();config.set(SlayerConfig.SLAYER_QUEST_CURRENCY,QuestCurrency.MOTES);var settings=SlayerSettings.from(config);
        var q=new SlayerQuestController();q.start(false,0);q.tick(null,settings,0);q.tick(menu(1,settings.rootTitle(),settings.bossLabel()),settings,300);
        q.tick(menu(2,settings.bossTitle(),settings.tierLabel(),"Cost: 500 Motes"),settings,600);q.tick(menu(3,settings.confirmTitle(),settings.confirmLabel()),settings,900);
        assertEquals(SlayerQuestController.State.WAIT_START,q.state());q.message("Player: SLAYER QUEST STARTED!",settings,950);assertEquals(SlayerQuestController.State.WAIT_START,q.state());
        q.message("SLAYER QUEST STARTED!",settings,1000);assertEquals(SlayerQuestController.State.ACTIVE,q.state());
        q.message("SLAYER QUEST COMPLETE!",settings,1200);assertEquals(1,q.completed());
    }
}
