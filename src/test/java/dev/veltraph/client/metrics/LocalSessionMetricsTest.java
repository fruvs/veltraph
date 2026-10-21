package dev.veltraph.client.metrics;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalSessionMetricsTest {
    private final LocalSessionMetrics.Context ctx=new LocalSessionMetrics.Context(new Object(),new Object(),new Object());
    private final LocalSessionMetrics.Series source=new LocalSessionMetrics.Series("native",UUID.randomUUID());
    private LocalSessionMetrics metrics(MetricContracts contracts){var m=new LocalSessionMetrics(contracts,0,10000,3000,2000);m.bind(ctx,Map.of(),0);return m;}
    @Test void cumulativeBaselineDuplicatesAndRegressionsCannotAddLifetimeOrRecredit(){
        var m=metrics(MetricContracts.empty());assertTrue(m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,1,100,0));
        assertEquals(0,m.snapshot(0).count(LocalSessionMetrics.Counter.NATIVE_MOB_KILLS).orElseThrow());
        m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,2,102,10);
        assertFalse(m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,2,104,10));
        assertFalse(m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,3,101,20));
        m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,4,102,30);
        assertEquals(2,m.snapshot(30).count(LocalSessionMetrics.Counter.NATIVE_MOB_KILLS).orElseThrow());
        assertTrue(m.snapshot(30).count(LocalSessionMetrics.Counter.CONFIRMED_TREES).isEmpty());
    }
    @Test void twoNativeReceiptsNeedCombinedInventoryDeltaAndDuplicateSerialIsIgnored(){
        var c=new MetricContracts(Map.of("full",new MetricContracts.Item("full","Sorrow",Set.of(MetricContracts.Role.SORROW),null)),List.of());var m=metrics(c);
        assertTrue(m.pickupReceipt(ctx,1,"full",1,0));assertFalse(m.pickupReceipt(ctx,1,"full",1,0));m.pickupReceipt(ctx,2,"full",1,0);
        m.inventory(ctx,1,Map.of("full",1L),10);assertEquals(0,m.snapshot(10).count(LocalSessionMetrics.Counter.NATIVE_ITEMS_PICKED_UP).orElseThrow());
        m.inventory(ctx,2,Map.of("full",2L),20);assertEquals(2,m.snapshot(20).count(LocalSessionMetrics.Counter.SORROWS_COLLECTED).orElseThrow());
        m.inventory(ctx,3,Map.of("full",2L),30);assertEquals(2,m.snapshot(30).count(LocalSessionMetrics.Counter.NATIVE_ITEMS_PICKED_UP).orElseThrow());
        assertNull(m.snapshot(30).completeCollectedValue());assertEquals(2,m.snapshot(30).unpricedUnits());
    }
    @Test void extraOrMissingInventoryNeverConfirmsReceiptAndLookalikeHasNoRole(){
        var c=new MetricContracts(Map.of("exact",new MetricContracts.Item("exact","Eye",Set.of(MetricContracts.Role.SUMMONING_EYE),BigDecimal.TEN)),List.of());var m=metrics(c);
        m.pickupReceipt(ctx,1,"exact",1,0);m.inventory(ctx,1,Map.of("lookalike",1L),10);
        assertEquals(0,m.snapshot(10).count(LocalSessionMetrics.Counter.NATIVE_ITEMS_PICKED_UP).orElseThrow());
        m.inventory(ctx,2,Map.of("exact",2L),20);assertEquals(1,m.snapshot(20).rejectedPickupGroups());
        m.pickupReceipt(ctx,2,"lookalike",1,30);m.inventory(ctx,3,Map.of("exact",2L,"lookalike",2L),40);
        assertTrue(m.snapshot(40).count(LocalSessionMetrics.Counter.SUMMONING_EYES_COLLECTED).isEmpty());
    }
    @Test void actualCollectedValuationDoesNotAddRealizedSaleAgain(){
        var c=new MetricContracts(Map.of("exact",new MetricContracts.Item("exact","Eye",Set.of(),new BigDecimal("2.5"))),List.of());var m=metrics(c);
        m.pickupReceipt(ctx,1,"exact",2,0);m.inventory(ctx,1,Map.of("exact",2L),1);
        m.financial(ctx,"sale-1",false,BigDecimal.TEN,2);assertFalse(m.financial(ctx,"sale-1",false,BigDecimal.TEN,2));m.financial(ctx,"cost-1",true,new BigDecimal("3"),3);
        assertEquals(new BigDecimal("5.0"),m.snapshot(3).completeCollectedValue());assertEquals(new BigDecimal("7"),m.snapshot(3).completeRealizedNet());
        m.financial(ctx,"unknown-cost",true,null,4);assertNull(m.snapshot(4).completeRealizedNet());
    }
    @Test void playerChatAndPrefixedLookalikesCannotCreditXpGaugeOrPickup(){
        var c=new MetricContracts(Map.of(),List.of(new MetricContracts.Signal(MetricContracts.SignalKind.SKILL_XP_TOTAL,"MINING",MetricContracts.Channel.ACTION_BAR,"Mining XP: ",""),new MetricContracts.Signal(MetricContracts.SignalKind.RNG_PERCENT,"",MetricContracts.Channel.SYSTEM,"RNG Meter: ","%")));var m=metrics(c);
        m.message(ctx,MetricContracts.Channel.PLAYER_CHAT,1,"Mining XP: 100",0,source);
        m.message(ctx,MetricContracts.Channel.ACTION_BAR,2,"[Bob] Mining XP: 100",1,source);assertTrue(m.snapshot(1).xpGained().isEmpty());
        m.message(ctx,MetricContracts.Channel.ACTION_BAR,3,"Mining XP: 100",2,source);m.message(ctx,MetricContracts.Channel.ACTION_BAR,4,"Mining XP: 125.5",3,source);
        m.message(ctx,MetricContracts.Channel.ACTION_BAR,4,"Mining XP: 900",4,source);assertEquals(new BigDecimal("25.5"),m.snapshot(4).xpGained().get("SKILL_XP:MINING"));
        m.message(ctx,MetricContracts.Channel.SYSTEM,5,"RNG Meter: 42%",5,source);assertEquals(new BigDecimal("42"),m.snapshot(5).rngPercent());
        m.message(ctx,MetricContracts.Channel.SYSTEM,6,"RARE DROP! Sorrow",6,source);m.message(ctx,MetricContracts.Channel.SYSTEM,7,"RARE DROP! Sorrow",7,source);
        assertEquals(1,m.snapshot(7).sorrowNotices());assertTrue(m.snapshot(7).count(LocalSessionMetrics.Counter.SORROWS_COLLECTED).isEmpty());
    }
    @Test void oldContextCannotCreditNewScopeAndRatesAreExplicitNativeAverages(){
        var m=metrics(MetricContracts.empty());m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED,1,0,0);m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED,2,10,1000);
        assertEquals(10.0,m.snapshot(1000).nativeBlocksPerSecond());
        var next=new LocalSessionMetrics.Context(new Object(),new Object(),new Object());m.bind(next,Map.of(),2000);
        assertFalse(m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED,3,100,2000));
        assertEquals(0.0,m.snapshot(12000).nativeBlocksPerSecond());
    }
    @Test void producerRestartBaselinesNewGenerationAndRejectsRetiredOwner(){
        var m=metrics(MetricContracts.empty());m.cumulative(ctx,source,LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,1,20,0);m.cumulative(ctx,source,LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,2,21,1);
        var next=new LocalSessionMetrics.Series(source.producer(),UUID.randomUUID());m.cumulative(ctx,next,LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,1,0,2);m.cumulative(ctx,next,LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,2,1,3);
        assertFalse(m.cumulative(ctx,source,LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,3,100,4));assertEquals(2,m.snapshot(4).count(LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS).orElseThrow());
    }
    @Test void disableScopeDropsPendingDeliveryAndDoesNotCreditDisabledTotals(){
        var m=metrics(MetricContracts.empty());m.pickupReceipt(ctx,1,"full",2,0);m.cumulative(ctx,source,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,1,10,0);m.unbind(1);
        assertFalse(m.pickupReceipt(ctx,2,"full",2,1));m.bind(ctx,Map.of("full",2L),2);m.inventory(ctx,1,Map.of("full",2L),3);
        var next=new LocalSessionMetrics.Series(source.producer(),UUID.randomUUID());m.cumulative(ctx,next,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,1,20,3);m.cumulative(ctx,next,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,2,21,4);
        assertEquals(0,m.snapshot(4).count(LocalSessionMetrics.Counter.NATIVE_ITEMS_PICKED_UP).orElseThrow());assertEquals(1,m.snapshot(4).count(LocalSessionMetrics.Counter.NATIVE_MOB_KILLS).orElseThrow());
    }
    @Test void allExplicitOwnerCountersAreIndependentFromNativeKillsAndBlocks(){
        var m=metrics(MetricContracts.empty());for(var counter:List.of(LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,LocalSessionMetrics.Counter.CONFIRMED_TREES,LocalSessionMetrics.Counter.CONFIRMED_FISH_HUNTED)){
            m.cumulative(ctx,source,counter,1,100,0);m.cumulative(ctx,source,counter,2,103,0);assertEquals(3,m.snapshot(0).count(counter).orElseThrow());
        }assertTrue(m.snapshot(0).count(LocalSessionMetrics.Counter.NATIVE_MOB_KILLS).isEmpty());assertTrue(m.snapshot(0).count(LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED).isEmpty());
    }
    @Test void sourceEstimatedSkillXpIsSeparateAndLowerEqualCannotRecredit(){
        var m=metrics(MetricContracts.empty());m.estimatedSkillXpTotal(ctx,source,"MINING",1,112,0);m.estimatedSkillXpTotal(ctx,source,"MINING",2,150,1);
        m.estimatedSkillXpTotal(ctx,source,"MINING",2,170,2);m.estimatedSkillXpTotal(ctx,source,"MINING",3,100,3);m.estimatedSkillXpTotal(ctx,source,"MINING",4,150,4);
        m.xpTotal(ctx,source,"SKILL_XP:MINING",1,new BigDecimal("1000"),4);m.xpTotal(ctx,source,"SKILL_XP:MINING",2,new BigDecimal("1001.5"),5);
        assertEquals(new BigDecimal("38"),m.snapshot(5).xpGained().get("SOURCE_ESTIMATED_SKILL_XP:MINING"));assertEquals(new BigDecimal("1.5"),m.snapshot(5).xpGained().get("SKILL_XP:MINING"));
    }
    @Test void rngGaugeMayDecreaseWithoutBecomingARareDropCounter(){
        var m=metrics(new MetricContracts(Map.of(),List.of(new MetricContracts.Signal(MetricContracts.SignalKind.RNG_PERCENT,"",MetricContracts.Channel.SYSTEM,"RNG: ","%"))));
        m.message(ctx,MetricContracts.Channel.SYSTEM,1,"RNG: 80%",0,source);m.message(ctx,MetricContracts.Channel.SYSTEM,2,"RNG: 2%",1,source);
        assertEquals(new BigDecimal("2"),m.snapshot(1).rngPercent());assertTrue(m.snapshot(1).count(LocalSessionMetrics.Counter.RARE_ITEMS_COLLECTED).isEmpty());
    }
    @Test void contextChangeCannotConfirmOldPickupReceiptEvenWithSameFullComponents(){
        var m=metrics(MetricContracts.empty());m.pickupReceipt(ctx,1,"full",1,0);var next=new LocalSessionMetrics.Context(new Object(),new Object(),new Object());m.bind(next,Map.of(),1);
        m.inventory(next,1,Map.of("full",1L),2);assertEquals(0,m.snapshot(2).count(LocalSessionMetrics.Counter.NATIVE_ITEMS_PICKED_UP).orElseThrow());assertFalse(m.pickupReceipt(ctx,2,"full",1,3));
    }
    @Test void sourceLuckPercentageAboveHundredIsSeparateFromBoundedMeter(){
        var m=metrics(new MetricContracts(Map.of(),List.of(new MetricContracts.Signal(MetricContracts.SignalKind.LUCK_RATIO_PERCENT,"",MetricContracts.Channel.SYSTEM,"Luck: ","%"),new MetricContracts.Signal(MetricContracts.SignalKind.RNG_PERCENT,"",MetricContracts.Channel.SYSTEM,"Meter: ","%"))));
        m.message(ctx,MetricContracts.Channel.SYSTEM,1,"Luck: 172%",0,source);m.message(ctx,MetricContracts.Channel.SYSTEM,2,"Meter: 42%",1,source);
        assertEquals(new BigDecimal("172"),m.snapshot(1).luckRatioPercent());assertEquals(new BigDecimal("42"),m.snapshot(1).rngPercent());
    }
}
