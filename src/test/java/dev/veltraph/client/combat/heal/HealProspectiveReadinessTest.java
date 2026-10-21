package dev.veltraph.client.combat.heal;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealProspectiveReadinessTest {
    private static final HealItemSelector.Item ASTRAEA=new HealItemSelector.Item("minecraft:iron_sword","Astraea",false);
    private static final HealItemSelector.Item FLORID=new HealItemSelector.Item("minecraft:golden_sword","Florid Zombie Sword",false);
    private static boolean ready(HealItemSelector selector,List<HealItemSelector.Item> items,int mana) {
        return HealProspectiveReadiness.ready(selector,items,HealStackingMode.LAZY,100001,true,true,false,
                mana,1000,false,false,new HealRawThresholds(1500,800));
    }
    @Test void firstTypeCoolingDoesNotHideEligibleFloridAndProspectiveReadIsNonMutating(){
        var ledger=new HealUseLedger();var selector=new HealItemSelector(ledger);
        selector.recordEmittedUse(HealItemType.HYPERION,100000);
        assertTrue(ready(selector,List.of(ASTRAEA,FLORID),70));
        assertEquals(100000,selector.lastEmittedUse(HealItemType.HYPERION));
        assertEquals(0,selector.lastEmittedUse(HealItemType.FLORID));
        assertEquals(new HealItemSelector.Selection(HealItemType.FLORID,1),
                selector.select(List.of(ASTRAEA,FLORID),HealStackingMode.LAZY,100001).orElseThrow());
    }
    @Test void noEligibleCandidateOrInsufficientProspectiveManaRejects(){
        var selector=new HealItemSelector();selector.recordEmittedUse(HealItemType.HYPERION,100000);
        assertFalse(ready(selector,List.of(ASTRAEA),500));
        assertFalse(ready(selector,List.of(ASTRAEA,FLORID),69));
        selector.recordEmittedUse(HealItemType.FLORID,115000);
        assertFalse(ready(selector,List.of(ASTRAEA,FLORID),500));
    }
    @Test void prospectivePolicyRetainsSourceHealthAndPlayerGates(){
        var selector=new HealItemSelector();
        assertFalse(HealProspectiveReadiness.ready(selector,List.of(FLORID),HealStackingMode.LAZY,
                100001,true,true,false,70,1501,false,false,new HealRawThresholds(1500,800)));
        assertFalse(HealProspectiveReadiness.ready(selector,List.of(FLORID),HealStackingMode.LAZY,
                100001,true,true,true,70,1000,false,false,new HealRawThresholds(1500,800)));
    }
}
