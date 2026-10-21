package dev.veltraph.client.metrics;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudMetricsLinesTest {
    private final LocalSessionMetrics.Context context=new LocalSessionMetrics.Context(new Object(),new Object(),new Object());
    private final LocalSessionMetrics.Series series=new LocalSessionMetrics.Series("native",UUID.randomUUID());
    private LocalSessionMetrics metrics(){var m=new LocalSessionMetrics(MetricContracts.empty(),0,10000,3000,2000);m.bind(context,Map.of(),0);return m;}
    private static List<String> text(List<dev.veltraph.client.hud.HudLine> lines){return lines.stream().map(dev.veltraph.client.hud.HudLine::text).toList();}
    @Test void disabledNullSnapshotIsQuietAndAbsentNativeValuesRemainUnknown(){
        assertTrue(HudMetricsLines.build(null,0,0,12).isEmpty());var rows=text(HudMetricsLines.build(metrics().snapshot(0),0,0,12));
        assertTrue(rows.contains("Stats receipt: UNKNOWN"));assertTrue(rows.stream().anyMatch(s->s.contains("blocks UNKNOWN")&&s.contains("kills UNKNOWN")&&s.contains("fish UNKNOWN")));assertTrue(rows.contains("Native BPS: UNKNOWN"));assertTrue(rows.contains("Realized net: UNKNOWN"));
    }
    @Test void mixedKnownUnpricedAndExactEstimatedXpHaveIndependentHonestLabels(){
        var m=metrics();m.cumulative(context,series,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,1,10,0);m.cumulative(context,series,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,2,11,1);
        m.pickupReceipt(context,1,"unvalued-full",2,1);m.inventory(context,1,Map.of("unvalued-full",2L),2);
        m.xpTotal(context,series,"SKILL_XP:MINING",1,new BigDecimal("100"),2);m.xpTotal(context,series,"SKILL_XP:MINING",2,new BigDecimal("101.5"),3);
        m.estimatedSkillXpTotal(context,series,"MINING",1,112,3);m.estimatedSkillXpTotal(context,series,"MINING",2,150,4);
        var rows=text(HudMetricsLines.build(m.snapshot(4),1,4,12));assertTrue(rows.stream().anyMatch(s->s.contains("kills 1")));assertTrue(rows.contains("Loot value: UNKNOWN (2 unpriced)"));assertTrue(rows.contains("Realized net: UNKNOWN"));assertTrue(rows.contains("MINING XP: 1.5"));assertTrue(rows.contains("Estimated MINING XP: 38"));
    }
    @Test void staleReceiptCannotMakeExpiredNativeWindowZeroLookCurrent(){
        var m=metrics();m.cumulative(context,series,LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED,1,0,0);m.cumulative(context,series,LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED,2,1,1);
        var rows=text(HudMetricsLines.build(m.snapshot(40000),1,40000,12));assertTrue(rows.contains("Stats receipt: 39s ago (stale)"));assertTrue(rows.contains("Native BPS: UNKNOWN"));assertFalse(rows.stream().anyMatch(s->s.equals("Native BPS window: 0")));
        assertTrue(text(HudMetricsLines.build(m.snapshot(40000),50000,40000,12)).contains("Stats receipt: UNKNOWN"));
    }
    @Test void finitePanelBudgetIncludesAnOmittedCountAndOneLineKeepsReceiptState(){
        var s=metrics().snapshot(0);for(int maximum:new int[]{-1,0,1,2,6,12,200}){
            var lines=HudMetricsLines.build(s,0,0,maximum);assertTrue(lines.size()<=Math.max(0,Math.min(HudMetricsLines.MAX_LINES,maximum)));assertTrue(lines.stream().allMatch(l->l.text().length()<=HudMetricsLines.MAX_CHARACTERS));
        }
        assertEquals("Stats receipt: UNKNOWN",HudMetricsLines.build(s,0,0,1).getFirst().text());assertTrue(HudMetricsLines.build(s,0,0,6).getLast().text().matches("Metrics: [0-9]+ more"));
    }
    @Test void knownNetAndOverHundredLuckRatioStaySeparateFromBoundedGauge(){
        var contract=new MetricContracts(Map.of(),List.of(new MetricContracts.Signal(MetricContracts.SignalKind.LUCK_RATIO_PERCENT,"",MetricContracts.Channel.SYSTEM,"Luck ","%"),new MetricContracts.Signal(MetricContracts.SignalKind.RNG_PERCENT,"",MetricContracts.Channel.SYSTEM,"Meter ","%")));
        var m=new LocalSessionMetrics(contract,0,10000,3000,2000);m.bind(context,Map.of(),0);m.financial(context,"sale",false,BigDecimal.TEN,0);m.financial(context,"cost",true,new BigDecimal("3"),0);m.message(context,MetricContracts.Channel.SYSTEM,1,"Luck 172%",0,series);m.message(context,MetricContracts.Channel.SYSTEM,2,"Meter 42%",0,series);
        var rows=text(HudMetricsLines.build(m.snapshot(0),0,0,12));assertTrue(rows.contains("Realized net: 7"));assertTrue(rows.contains("Luck ratio 172%; declared meter 42%"));
    }
}
