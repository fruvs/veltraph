package dev.veltraph.client.galatea;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialHarvestCycleTest {
    @Test void ripeBushAcknowledgesStableMaturityReset() {
        var cycle=new MaterialHarvestCycle();cycle.begin(3,0);cycle.clicked(0);
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(20,true,true,1,0,750,5000,false));
        assertEquals(MaterialHarvestCycle.Outcome.CONFIRMED,cycle.observe(780,true,true,1,0,750,5000,false));
    }
    @Test void temporaryAirRollbackDoesNotCreditCollection() {
        var cycle=new MaterialHarvestCycle();cycle.begin(3,0);cycle.clicked(0);
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(20,true,false,-1,0,750,3000,false));
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(700,true,true,3,0,750,3000,false));
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(1100,true,true,3,0,750,3000,false));
        assertEquals(MaterialHarvestCycle.Outcome.FAILED,cycle.observe(3000,true,true,3,0,750,3000,false));
    }
    @Test void growthIsNotHarvestAndRewardCorrectionResetsConfirmation() {
        var cycle=new MaterialHarvestCycle();cycle.begin(2,4);cycle.clicked(0);
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(10,true,true,3,4,750,3000,false));
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(100,true,true,2,5,750,3000,false));
        assertEquals(MaterialHarvestCycle.Outcome.WAITING,cycle.observe(400,true,true,2,4,750,3000,false));
        assertEquals(MaterialHarvestCycle.Outcome.FAILED,cycle.observe(3000,true,true,2,4,750,3000,false));
    }
    @Test void assumedClickIsUnconfirmedAndUnloadNeverCounts() {
        var cycle=new MaterialHarvestCycle();cycle.begin(-1,0);cycle.clicked(0);
        assertEquals(MaterialHarvestCycle.Outcome.ASSUMED,cycle.observe(500,true,true,-1,0,750,3000,true));
        assertEquals(MaterialHarvestCycle.Outcome.FAILED,cycle.observe(900,false,false,-1,1,750,3000,false));
    }
    @Test void rightClickToolRejectsAbilitiesConsumablesBlocksAndMenuSlot() {
        var unsafe=new LushlilacToolPolicy.Tool(0,false,true,false,false);
        var food=new LushlilacToolPolicy.Tool(1,false,false,false,true);
        var placing=new LushlilacToolPolicy.Tool(2,false,false,true,false);
        var empty=new LushlilacToolPolicy.Tool(3,true,false,false,false);
        var inert=new LushlilacToolPolicy.Tool(4,false,false,false,false);
        var menu=new LushlilacToolPolicy.Tool(8,true,false,false,false);
        assertEquals(4,LushlilacToolPolicy.choose(List.of(unsafe,food,placing,empty,inert,menu),GalateaPlanner.ClickType.RIGHT));
        assertEquals(-1,LushlilacToolPolicy.choose(List.of(unsafe,food,placing,menu),GalateaPlanner.ClickType.RIGHT));
        assertEquals(0,LushlilacToolPolicy.choose(List.of(unsafe,food,placing),GalateaPlanner.ClickType.LEFT));
        assertTrue(LushlilacToolPolicy.rightClickAbility(List.of("§6Ability: Ether Transmission RIGHT CLICK")));
        assertTrue(LushlilacToolPolicy.consumableLore(List.of("§eLeft-click to consume!")));
        assertEquals(-1,LushlilacToolPolicy.choose(List.of(food),GalateaPlanner.ClickType.LEFT));
    }
}
