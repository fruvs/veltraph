package dev.veltraph.client.metrics;

import dev.veltraph.client.hud.HudLine;
import java.math.*;
import java.util.*;

/** Pure bounded presentation; unknown receipt, delivery valuation and cash outcomes stay explicit. */
public final class HudMetricsLines {
    public static final int MAX_LINES=12,MAX_CHARACTERS=56;
    public static final long STALE_RECEIPT_MS=30000;
    private HudMetricsLines() {}
    public static List<HudLine> build(LocalSessionMetrics.Snapshot s,long lastReceipt,long now,int maxLines){
        int limit=Math.max(0,Math.min(MAX_LINES,maxLines));if(s==null||limit==0)return List.of();var lines=new ArrayList<HudLine>();
        boolean received=lastReceipt>0&&now>=lastReceipt;long age=received?now-lastReceipt:0;boolean stale=received&&age>STALE_RECEIPT_MS;
        lines.add(!received?HudLine.warning("Stats receipt: UNKNOWN"):stale?HudLine.warning("Stats receipt: "+age/1000+"s ago (stale)"):HudLine.muted("Stats receipt: "+age/1000+"s ago"));
        lines.add(HudLine.muted("Native blocks "+count(s,LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED)+"; kills "+count(s,LocalSessionMetrics.Counter.NATIVE_MOB_KILLS)+"; fish "+count(s,LocalSessionMetrics.Counter.NATIVE_FISH_CAUGHT)));
        lines.add(!received||stale||s.nativeBlocksPerSecond()==null?HudLine.warning("Native BPS: UNKNOWN"):HudLine.muted("Native BPS window: "+number(BigDecimal.valueOf(s.nativeBlocksPerSecond()))));
        lines.add(s.completeCollectedValue()==null?HudLine.warning("Loot value: UNKNOWN ("+s.unpricedUnits()+" unpriced)"):HudLine.body("Loot value: "+number(s.completeCollectedValue())));
        lines.add(s.completeRealizedNet()==null?HudLine.warning("Realized net: UNKNOWN"+(s.unknownCashEvents()>0?" ("+s.unknownCashEvents()+" unknown outcomes)":"")):HudLine.body("Realized net: "+number(s.completeRealizedNet())));
        if(s.xpGained().isEmpty())lines.add(HudLine.muted("XP totals: UNKNOWN"));
        else for(var e:s.xpGained().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())lines.add(HudLine.body(xpLabel(e.getKey())+": "+number(e.getValue())));
        lines.add(HudLine.muted("Luck ratio "+percent(s.luckRatioPercent())+"; declared meter "+percent(s.rngPercent())));
        lines.add(HudLine.muted("Sorrows "+count(s,LocalSessionMetrics.Counter.SORROWS_COLLECTED)+"; Eyes "+count(s,LocalSessionMetrics.Counter.SUMMONING_EYES_COLLECTED)+"; Lushlilacs "+count(s,LocalSessionMetrics.Counter.LUSHLILACS_COLLECTED)));
        lines.add(HudLine.muted("Rare loot "+count(s,LocalSessionMetrics.Counter.RARE_ITEMS_COLLECTED)+"; Sorrow notices "+s.sorrowNotices()));
        var owners=new LinkedHashMap<LocalSessionMetrics.Counter,String>();owners.put(LocalSessionMetrics.Counter.CONFIRMED_TREES,"Trees");owners.put(LocalSessionMetrics.Counter.CONFIRMED_SLAYER_QUESTS,"Slayer quests");owners.put(LocalSessionMetrics.Counter.CONFIRMED_GHOST_KILLS,"Ghost kills");owners.put(LocalSessionMetrics.Counter.CONFIRMED_ZEALOT_KILLS,"Zealot kills");owners.put(LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS,"Boss kills");owners.put(LocalSessionMetrics.Counter.CONFIRMED_FISH_HUNTED,"Hunted fish");
        for(var e:owners.entrySet())if(s.count(e.getKey()).isPresent())lines.add(HudLine.muted(e.getValue()+": "+count(s,e.getKey())));
        if(owners.keySet().stream().noneMatch(k->s.count(k).isPresent()))lines.add(HudLine.muted("Owner counters: UNKNOWN"));
        if(lines.size()>limit){if(limit==1)lines=new ArrayList<>(lines.subList(0,1));else {int omitted=lines.size()-limit+1;lines=new ArrayList<>(lines.subList(0,limit-1));lines.add(HudLine.muted("Metrics: "+omitted+" more"));}}
        return lines.stream().map(l->new HudLine(bound(l.text()),l.role())).toList();
    }
    private static String count(LocalSessionMetrics.Snapshot s,LocalSessionMetrics.Counter key){return s.count(key).isPresent()?Long.toString(s.count(key).getAsLong()):"UNKNOWN";}
    private static String percent(BigDecimal value){return value==null?"UNKNOWN":number(value)+"%";}
    private static String number(BigDecimal value){return value.round(new MathContext(6,RoundingMode.HALF_UP)).stripTrailingZeros().toEngineeringString();}
    private static String xpLabel(String key){
        if(key.startsWith("SOURCE_ESTIMATED_SKILL_XP:"))return "Estimated "+key.substring("SOURCE_ESTIMATED_SKILL_XP:".length())+" XP";
        if(key.startsWith("SKILL_XP:"))return key.substring("SKILL_XP:".length())+" XP";
        return switch(key){case "SLAYER_XP_TOTAL"->"Slayer XP";case "HOTF_XP_TOTAL"->"HotF XP";case "WHISPERS_TOTAL"->"Whispers";default->"Declared XP";};
    }
    private static String bound(String text){return text.length()<=MAX_CHARACTERS?text:text.substring(0,MAX_CHARACTERS-1)+"…";}
}
