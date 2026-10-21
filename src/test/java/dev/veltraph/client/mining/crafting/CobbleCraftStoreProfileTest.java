package dev.veltraph.client.mining.crafting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CobbleCraftStoreProfileTest {
    static String profile(String adapter){
        var inputs=new java.util.ArrayList<String>();int[] slots=adapter.equals("native_table")?new int[]{1,2,3,4,6,7,8,9}:new int[]{10,11,12,19,21,28,29,30};
        for(int slot:slots)inputs.add("{\"slot\":"+slot+",\"id\":\"minecraft:cobblestone\",\"name\":\"Cobblestone\",\"components\":\"default\",\"lore\":[],\"units\":1}");
        return "{\"adapter\":\""+adapter+"\",\"craft\":{\"position\":\"-2,70,0\",\"block\":\"minecraft:crafting_table\",\"title\":\"Crafting\"},\"store\":{\"position\":\"2,70,0\",\"block\":\"minecraft:chest\",\"title\":\"Chest\"},\"inputs\":["+String.join(",",inputs)+"],\"output\":{\"slot\":"+(adapter.equals("native_table")?0:23)+",\"id\":\"minecraft:furnace\",\"name\":\"Furnace\",\"components\":\"default\",\"lore\":[],\"units\":1,\"delivery\":\"cursor\"},\"storeSlot\":0,\"sink\":9,\"batches\":2,\"maxConsumed\":16,\"maxOutput\":2,\"maxSpend\":0,\"timeoutTicks\":600,\"delayTicks\":2}";
    }
    @Test void retainsNativeRingAndSourceSlot23Contract(){var n=CobbleCraftStoreProfile.parse(profile("native_table"));var s=CobbleCraftStoreProfile.parse(profile("source_menu"));assertTrue(n.nativeTable());assertFalse(s.nativeTable());assertEquals(23,s.output().slot());assertEquals(java.util.List.of(10,11,12,19,21,28,29,30),s.inputs().stream().map(CobbleCraftStoreProfile.Input::slot).toList());}
    @Test void rejectsOverlappingCellsAndBarrierAsOutput(){assertThrows(RuntimeException.class,()->CobbleCraftStoreProfile.parse(profile("native_table").replace("\"slot\":2,","\"slot\":1,")));assertThrows(RuntimeException.class,()->CobbleCraftStoreProfile.parse(profile("source_menu").replace("minecraft:furnace","minecraft:barrier")));}
    @Test void refusesResourceOutputAndUnquotedSpendOverruns(){for(String changed:java.util.List.of(profile("source_menu").replace("\"maxConsumed\":16","\"maxConsumed\":15"),profile("native_table").replace("\"maxOutput\":2","\"maxOutput\":1"),profile("source_menu").replace("\"maxSpend\":0","\"maxSpend\":1")))assertThrows(RuntimeException.class,()->CobbleCraftStoreProfile.parse(changed));}
    @Test void doesNotAllowNativeLayoutToSilentlyUseServerSlots(){assertThrows(RuntimeException.class,()->CobbleCraftStoreProfile.parse(profile("source_menu").replace("source_menu","native_table")));}
    @Test void retainsSourceExactComponentLoreAndInventoryDelivery(){String p=profile("source_menu").replace("\"components\":\"default\"","\"components\":\"{custom_data:{id:ENCHANTED_COBBLESTONE}}\"").replace("\"lore\":[]","\"lore\":[\"Exact configured recipe identity\"]").replace("\"delivery\":\"cursor\"","\"delivery\":\"inventory\"");var parsed=CobbleCraftStoreProfile.parse(p);assertFalse(parsed.output().cursor());assertEquals("{custom_data:{id:ENCHANTED_COBBLESTONE}}",parsed.inputs().getFirst().item().components());assertEquals(java.util.List.of("Exact configured recipe identity"),parsed.output().item().lore());}
}
