package dev.veltraph.client.skills;

import org.junit.jupiter.api.Test;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyFlowProfileTest {
    AlchemyFlowProfile.Item item(String name,long value){return new AlchemyFlowProfile.Item("minecraft:potion",name,"{exact="+name+"}",value);}
    AlchemyFlowProfile.Resource resource(String name,long value){return new AlchemyFlowProfile.Resource(item(name,value),null);}
    AlchemyFlowProfile plan(long budget,int batches,List<AlchemyFlowProfile.Step> steps){return new AlchemyFlowProfile(List.of(new BlockPos(1,70,0)),"Brewing Stand",resource("Water",2),resource("Fuel",3),steps,3,batches,false,null,"","",0,budget,1000,30000,120000,0,null);}
    @Test void rejectsUnknownProfilesAndUnknownComponentsValues(){
        assertThrows(IllegalArgumentException.class,()->AlchemyFlowProfile.parse(""));
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile.Item("minecraft:potion","Water","",1));
        assertThrows(IllegalArgumentException.class,()->item("Water",0));
    }
    @Test void exactBudgetIncludesBottlesFuelAndAllOrderedIngredients(){
        var steps=List.of(new AlchemyFlowProfile.Step(resource("Ingredient1",4),item("Intermediate",2)),new AlchemyFlowProfile.Step(resource("Ingredient2",5),item("Final",2)));
        assertThrows(IllegalArgumentException.class,()->plan(17,2,steps));
        assertEquals(2,plan(18,2,steps).steps().size());
    }
    @Test void capsRepeatAndRejectsNoOpRecipeStages(){
        var noOp=List.of(new AlchemyFlowProfile.Step(resource("Ingredient",1),item("Water",2)));
        assertThrows(IllegalArgumentException.class,()->plan(20,1,noOp));
        var steps=List.of(new AlchemyFlowProfile.Step(resource("Ingredient",1),item("Final",2)));
        assertThrows(IllegalArgumentException.class,()->plan(20,17,steps));
        assertEquals(16,plan(20,16,steps).batches());
    }
    @Test void receiptsRequireWholeLineExactQuantityAndActualCostGroups(){
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile.Offer("Buy",3,10,"Bought.*"));
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile.Offer("Buy",3,10,"^Bought.*$"));
        var offer=new AlchemyFlowProfile.Offer("Buy",3,10,"^Bought (?<quantity>[0-9]+) for (?<cost>[0-9]+)$");
        assertEquals(3,offer.amount());
    }
    @Test void cannotDeclareConflictingStandOrUnboundedTimeout(){
        var step=List.of(new AlchemyFlowProfile.Step(resource("Ingredient",1),item("Final",2)));
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile(List.of(new BlockPos(1,70,0),new BlockPos(1,70,0)),"Stand",resource("Water",1),resource("Fuel",1),step,3,1,false,null,"","",0,100,1000,1000,10000,0,null));
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile(List.of(new BlockPos(1,70,0)),"Stand",resource("Water",1),resource("Fuel",1),step,3,1,false,null,"","",0,100,1000,1000,900000,0,null));
    }
    @Test void productionProfileConsumesTerminalSaleAndLeavesLegacyCollectionExplicit(){
        var steps=List.of(new AlchemyFlowProfile.Step(resource("Ingredient",1),item("Final",2)));
        var base=plan(20,2,steps);assertNull(base.terminalSale());
        var json=new com.google.gson.Gson().toJsonTree(base).getAsJsonObject();
        var stands=new com.google.gson.JsonArray();stands.add("1,70,0");json.add("stands",stands);
        json.add("terminalSale",new com.google.gson.Gson().toJsonTree(AlchemySaleParentTest.profile()));
        var configured=AlchemyFlowProfile.parse(json.toString());
        assertEquals("Bound cookie dealer",configured.terminalSale().saleTitle());
        assertEquals("4",configured.terminalSale().unitProceeds().toPlainString());
        assertEquals(2,configured.batches());
        json.getAsJsonObject("terminalSale").addProperty("activeCookieLine","");
        assertThrows(IllegalArgumentException.class,()->AlchemyFlowProfile.parse(json.toString()));
    }

    @Test void sourceLayoutUsesIngredient13AndRequiresDistinctContainerSlotsAndFuelObservation(){
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile.Layout("source_13",3,List.of(10,11,12),3,14,22,"^Fuel: (?<fuel>[0-9]+)$"));
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile.Layout("source_13",3,List.of(10,11,12),13,13,22,"^Fuel: (?<fuel>[0-9]+)$"));
        assertThrows(IllegalArgumentException.class,()->new AlchemyFlowProfile.Layout("source_13",3,List.of(10,11,12),13,14,22,""));
        assertEquals(13,new AlchemyFlowProfile.Layout("source_13",3,List.of(10,11,12),13,14,22,"^Fuel: (?<fuel>[0-9]+)$").ingredientSlot());
    }
}
