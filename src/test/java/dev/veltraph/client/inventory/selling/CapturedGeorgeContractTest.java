package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.pests.philip.SourceContactCommand;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CapturedGeorgeContractTest {
    private static final UUID PET=new UUID(0,9);
    private static SellingRule rule(){return new SellingRule(AutoSellMode.GEORGE_PETS,9,"minecraft:player_head","Approved pet","native uuid "+PET,1,new BigDecimal("5"),"Quote pet","pet","\"eligible\"");}
    private static SellingObservation observation(String components,BigDecimal purse){var pet=new SellingObservation.Stack(27,9,"minecraft:player_head","Approved pet",components,1,false,true,Map.of("pet","\"eligible\"","uuid","\""+PET+"\""));
        var tool=new SellingObservation.Stack(54,0,"minecraft:diamond_hoe","Hoe","protected tool",1,true);
        return new SellingObservation(null,List.of(pet,tool),Map.of(),null,purse,List.of(),false);}
    private static CapturedGeorgeContract contract(){var hub=new GeorgeSourceHubProfile("nativehub","native hub ready","nativegarden","native garden ready",List.of("native warp rejected"),new GeorgeWarpPermit.Destination(GameArea.HUB,"minecraft:overworld",new Vec3(6.5,70,.5),.4),new UUID(0,42),"NativeGeorge",80);
        var dialogue=new SourceContactCommand.Settings("nativecall ","NativeGeorge","Offer Pets",new AbiphoneCall.Item("minecraft:paper","native marker",1,"native components"),"native npc ready",List.of(),80);
        var selling=new SellingRuntime.Settings(AutoSellMode.GEORGE_PETS,"Offer Pets","Accept","Confirm","Confirm",13,13,"George bought {quantity}x {item} for {coins} coins!",2,BigDecimal.ONE,new BigDecimal("20"),BigDecimal.ZERO,1,80);
        return new CapturedGeorgeContract(hub,CapturedGeorgeContract.parseGardenLanding("{\"dimension\":\"minecraft:overworld\",\"x\":2.5,\"y\":70,\"z\":0.5,\"radius\":0.4}"),new GeorgeBatchPlan(2,new BigDecimal("10"),300,"uuid",List.of()),List.of(rule()),dialogue,selling,"Purse:",SellingAcknowledgment.Denomination.COINS,20000);}
    @Test void emptyConfigCannotInventHubNpcOrGardenLanding(){var c=new MapConfigService();assertEquals("",c.get(GeorgeBatchConfig.GEORGE_SOURCE_HUB_PROFILE));assertEquals("",c.get(GeorgeBatchConfig.GEORGE_GARDEN_WARP_LANDING));assertThrows(IllegalArgumentException.class,()->CapturedGeorgeContract.read(c));assertThrows(RuntimeException.class,()->CapturedGeorgeContract.parseGardenLanding(""));}
    @Test void gardenLandingIsExplicitIndependentAndStrict(){var c=contract();assertEquals(new Vec3(2.5,70,.5),c.garden().position());assertNotEquals(c.hub().hub().position(),c.garden().position());
        for(String invalid:List.of("{}","{\"dimension\":\"minecraft:overworld\",\"x\":\"2.5\",\"y\":70,\"z\":0.5,\"radius\":0.4}","{\"dimension\":\"minecraft:overworld\",\"x\":2.5,\"y\":70,\"z\":0.5,\"radius\":2}","{\"dimension\":\"minecraft:overworld\",\"x\":2.5,\"y\":70,\"z\":0.5,\"radius\":0.4,\"savedFarmPose\":true}"))assertThrows(RuntimeException.class,()->CapturedGeorgeContract.parseGardenLanding(invalid));}
    @Test void gardenApprovalsBecomeImmutablePinnedQueueBeforeWarp(){var c=contract();var origin=observation(rule().components(),new BigDecimal("100"));var frozen=c.freeze(origin);
        assertEquals(List.of(new GeorgeBatchPlan.Pet(9,PET)),frozen.plan().pets());assertTrue(frozen.unchanged(origin));assertThrows(UnsupportedOperationException.class,()->frozen.rules().clear());assertThrows(UnsupportedOperationException.class,()->frozen.ledger().clear());
        assertFalse(frozen.unchanged(observation("different native uuid",new BigDecimal("100"))));assertFalse(frozen.unchanged(observation(rule().components(),new BigDecimal("105"))));
        assertEquals(1,frozen.plan().freeze(frozen.rules(),origin,c.selling()).size());}
    @Test void independentNativeUuidCannotChangeUnderTheSameLedger(){var c=contract();var origin=observation(rule().components(),new BigDecimal("100"));var frozen=c.freeze(origin);var old=origin.inventory().getFirst();
        var swapped=new SellingObservation.Stack(old.slot(),old.inventoryIndex(),old.registryId(),old.name(),old.components(),old.count(),false,true,Map.of("pet","\"eligible\"","uuid","\""+new UUID(0,10)+"\""));
        var replaced=new SellingObservation(null,List.of(swapped,origin.inventory().getLast()),Map.of(),null,origin.coins(),List.of(),false);assertEquals(origin.ledger(),replaced.ledger());assertFalse(frozen.unchanged(replaced));}
    @Test void laterProfileEditsCannotRebuildApprovalsAndAllInputsAreBound(){var config=new MapConfigService();String original=CapturedGeorgeContract.settings(config);config.set(GeorgeBatchConfig.GEORGE_GARDEN_WARP_LANDING,"{}");assertNotEquals(original,CapturedGeorgeContract.settings(config));
        var c=contract();var frozen=c.freeze(observation(rule().components(),new BigDecimal("100")));assertThrows(IllegalArgumentException.class,()->frozen.plan().freeze(frozen.rules(),observation("new pet",new BigDecimal("100")),c.selling()));assertEquals(PET,frozen.plan().pets().getFirst().uuid());}
}
