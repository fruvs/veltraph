package dev.veltraph.client.inventory.selling;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.config.AutoSellMode;
import java.util.*;
import java.math.BigDecimal;
class NpcSellingQueueTest {
    private SellingRule r(AutoSellMode mode,int index){return new SellingRule(mode,index,"minecraft:wheat","Wheat","components",4,BigDecimal.valueOf(3),mode==AutoSellMode.TRASH?"":"Sell Wheat");}
    private SellingObservation o(){return new SellingObservation(null,List.of(new SellingObservation.Stack(-1,9,"minecraft:wheat","Wheat","components",4,false,false,Map.of()),new SellingObservation.Stack(-1,12,"minecraft:wheat","Wheat","components",4,false,false,Map.of())),Map.of(),null,BigDecimal.valueOf(100),List.of(),false);}
    @Test void sourceNativeNonemptyQueueSortedBothWays(){var rules=List.of(r(AutoSellMode.NPC,12),r(AutoSellMode.TRASH,9),r(AutoSellMode.NPC,13));assertEquals(List.of(9,12),NpcSellingQueue.freeze(rules,o(),true).stream().map(SellingRule::inventoryIndex).toList());assertEquals(List.of(12,9),NpcSellingQueue.freeze(rules,o(),false).stream().map(SellingRule::inventoryIndex).toList());}
    @Test void mismatchedQuantityAndEmptySlotsCannotEnterQueue(){var rule=new SellingRule(AutoSellMode.NPC,9,"minecraft:wheat","Wheat","components",3,BigDecimal.valueOf(3),"Sell Wheat");assertThrows(IllegalArgumentException.class,()->NpcSellingQueue.freeze(List.of(rule,r(AutoSellMode.NPC,13)),o(),true));}
    @Test void duplicatedNativeRowCannotBeSoldTwice(){assertThrows(IllegalArgumentException.class,()->NpcSellingQueue.freeze(List.of(r(AutoSellMode.NPC,9),r(AutoSellMode.TRASH,9)),o(),true));}
}
