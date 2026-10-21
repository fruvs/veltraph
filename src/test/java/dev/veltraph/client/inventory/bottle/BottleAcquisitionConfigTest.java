package dev.veltraph.client.inventory.bottle;

import com.google.gson.Gson;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BottleAcquisitionConfigTest {
    private static BottleFillWorkflow.Request fill(){return new BottleFillWorkflow.Request(BottleAcquisitionRuntimeTest.GLASS,new BottleFillWorkflow.Identity("minecraft:potion","Water Bottle","[water]"),64,"minecraft:overworld",new BottleFillWorkflow.Position(10,70,0),new BottleFillWorkflow.Position(9,70,0),new BottleFillWorkflow.Position(20,70,0),new BottleFillWorkflow.Position(19,70,0),"Chest",2,100);}
    private static List<BottleAcquisitionConfig.Storage> routes(){var routes=new ArrayList<BottleAcquisitionConfig.Storage>();for(int i=0;i<12;i++)routes.add(new BottleAcquisitionConfig.Storage(new BottleFillWorkflow.Position(20+i*2,70,0),new BottleFillWorkflow.Position(20+i*2,70,1),"Chest",27));return routes;}
    @Test void fullFinite320UnstackableWaterCapacityExplicitlyRoundTrips(){var contract=new BottleAcquisitionConfig.Contract(BottleAcquisitionRuntimeTest.profile(1,1000),fill(),routes(),3);var config=new MapConfigService();config.set(BottleAcquisitionConfig.BOTTLE_ACQUISITION_PROFILE,new Gson().toJson(contract));assertEquals(contract,BottleAcquisitionConfig.read(config));assertFalse(config.get(BottleAcquisitionConfig.BOTTLE_ACQUISITION_ENABLED));}
    @Test void emptyDefaultsCannotStartHiddenTravel(){assertThrows(IllegalArgumentException.class,()->BottleAcquisitionConfig.read(new MapConfigService()));}
    @Test void insufficientOneChestCannotSubstituteForMultiStackStorage(){assertThrows(IllegalArgumentException.class,()->new BottleAcquisitionConfig.Contract(BottleAcquisitionRuntimeTest.profile(1,1000),fill(),routes().subList(0,1),3));}
    @Test void repeatedSameChestCannotFabricateTotalCapacity(){var r=new ArrayList<>(routes());r.set(11,r.getFirst());assertThrows(IllegalArgumentException.class,()->new BottleAcquisitionConfig.Contract(BottleAcquisitionRuntimeTest.profile(1,1000),fill(),r,3));}
    @Test void noArtificial64PotionChestCapacity(){assertThrows(IllegalArgumentException.class,()->new BottleAcquisitionConfig.Storage(fill().chest(),fill().chestStand(),"Chest",64));}
    @Test void finiteCycleCapacityMultipliesEveryNativeGlassStack(){assertThrows(IllegalArgumentException.class,()->new BottleAcquisitionConfig.Contract(BottleAcquisitionRuntimeTest.profile(2,1000),fill(),routes(),3));}
    @Test void malformedMissingBindingsCannotDeserialize(){var config=new MapConfigService();config.set(BottleAcquisitionConfig.BOTTLE_ACQUISITION_PROFILE,"{\"acquisition\":{}}");assertThrows(IllegalArgumentException.class,()->BottleAcquisitionConfig.read(config));}
}
