package dev.veltraph.client.fishing.support;
import dev.veltraph.client.fishing.FishingManaParser;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SupportProfilesTest {
    @Test void sourceManaGatesAndOverflowUseCurrentMaximum(){var row=new OrbProfile("Fixture Orb","minecraft:blaze_rod",30,"minecraft:regeneration",0,200,0,true,0L);assertFalse(row.ready(234,400));assertTrue(row.ready(235,400));assertFalse(row.ready(500,1000));var mana=FishingManaParser.detailed("500/1000✎ 50ʬ").orElseThrow();assertTrue(row.ready(mana.current(),mana.maximum()));assertEquals(1000,mana.maximum());}
    @Test void stableOverflowAndSentinelPreserveSourceFractionGate(){var row=new OrbProfile("Fixture Orb","minecraft:blaze_rod",30,"minecraft:regeneration",0,1,0,false,0L);var mana=FishingManaParser.detailed("500/1000✎ 50\uE017").orElseThrow();assertTrue(row.ready(mana.current(),mana.maximum()));assertFalse(row.ready(549,1000));assertTrue(row.ready(550,1000));assertFalse(row.ready(20000,32767));assertFalse(row.ready(550,0));assertTrue(row.ready(600,500));}
    @Test void durationsAndUnknownContractsAreNotInvented(){assertThrows(IllegalArgumentException.class,()->OrbProfile.parse("[]"));assertThrows(IllegalArgumentException.class,()->OrbProfile.parse("[{\"itemName\":\"Orb\"}]"));assertThrows(IllegalArgumentException.class,()->new OrbProfile("Orb","minecraft:blaze_rod",3,"minecraft:regeneration",0,1,0,false,0L));assertThrows(IllegalArgumentException.class,()->RainProfile.parse("{}"));}
    private RainProfile rain(){return new RainProfile("Fixture Vanessa","minecraft:villager",UUID.randomUUID().toString(),5.5,70d,.5,"Fixture Rain","Buy Rain","minecraft:water_bucket","Remaining: ","Cost: "," Coins","Purse: ","Bought {minutes} minutes for {cost} coins!",2,0L,new BigDecimal("10"));}
    @Test void timerQuoteReceiptAreExactAndAmbiguousPricesFail(){var row=rain();var slot=new MenuSnapshot.Slot(1,"Buy Rain",List.of("Remaining: 01:05","Cost: 5 Coins"),false,"minecraft:water_bucket");assertEquals(65,row.seconds(slot));assertEquals(0,row.cost(slot).compareTo(new BigDecimal("5")));assertTrue(row.receipt("Bought 1 minutes for 5 coins!",new BigDecimal("5")));assertFalse(row.receipt("Player: Bought 1 minutes for 5 coins!",new BigDecimal("5")));assertFalse(row.receipt("Bought 2 minutes for 5 coins!",new BigDecimal("5")));assertThrows(IllegalArgumentException.class,()->row.cost(new MenuSnapshot.Slot(1,"Buy Rain",List.of("Cost: 5 Coins","Cost: 1 Coins"),false)));}
}
