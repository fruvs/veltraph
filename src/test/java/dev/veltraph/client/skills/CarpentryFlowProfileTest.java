package dev.veltraph.client.skills;
import org.junit.jupiter.api.Test;
import java.util.*;
import dev.veltraph.client.world.BlockPos;
import static org.junit.jupiter.api.Assertions.*;
class CarpentryFlowProfileTest {
    CarpentryFlowProfile.Item item(String name,long value){return new CarpentryFlowProfile.Item("minecraft:oak_log",name,"{exact="+name+"}",value);}
    CarpentryFlowProfile plan(int crafts,int batches,long budget){return new CarpentryFlowProfile(new CarpentryFlowProfile.Target("Craft",new BlockPos(1,70,0),"minecraft:crafting_table",""),null,List.of(new CarpentryFlowProfile.Material(item("Log",5),1,null)),item("Product",1),"Recipe","{recipe}","^Cost: (?<cost>[0-9]+)$",1,crafts,batches,true,false,4,9,100,budget,"Purse:",5000,120000,0,true,null);}
    @Test void absentIdentitiesAndProfilesAreRequired(){assertThrows(IllegalArgumentException.class,()->CarpentryFlowProfile.parse(""));assertThrows(IllegalArgumentException.class,()->new CarpentryFlowProfile.Item("minecraft:oak_log","Log","",1));assertThrows(IllegalArgumentException.class,()->item("Log",0));}
    @Test void boundsFiniteCursorBatchAndMaterialValue(){assertThrows(IllegalArgumentException.class,()->plan(17,1,100));assertThrows(IllegalArgumentException.class,()->plan(16,17,100));assertThrows(IllegalArgumentException.class,()->plan(2,1,9));assertEquals(2,plan(2,16,10).craftsPerBatch());}
    @Test void commandIsExplicitSingleTokenGameplayNotChat(){assertThrows(IllegalArgumentException.class,()->new CarpentryFlowProfile.Target("Craft",null,"","msg someone secret"));assertEquals("craft",new CarpentryFlowProfile.Target("Craft",null,"","craft").command());}
    @Test void receiptsRequireExactQuantityCostAndQuotes(){assertThrows(IllegalArgumentException.class,()->new CarpentryFlowProfile.Offer("Buy",2,3,"Bought.*"));assertThrows(IllegalArgumentException.class,()->new CarpentryFlowProfile.Offer("Buy",2,3,"^Bought.*$"));assertEquals(2,new CarpentryFlowProfile.Offer("Buy",2,3,"^Bought (?<quantity>[0-9]+) for (?<cost>[0-9]+)$").amount());}
}
