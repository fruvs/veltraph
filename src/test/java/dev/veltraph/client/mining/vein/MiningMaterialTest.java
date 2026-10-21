package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MiningMaterialTest {
 @Test void registryRepresentationsAreUniqueAndNamespaced(){var ids=new HashSet<String>();for(var m:MiningMaterial.values())for(var id:m.registryIds()){assertTrue(id.startsWith("minecraft:"));assertTrue(ids.add(id));assertEquals(m,MiningMaterial.classify(id).orElseThrow());}}
 @Test void mithrilUsesAllThreePrismarineVariants(){for(var id:List.of("prismarine","prismarine_bricks","dark_prismarine"))assertEquals(MiningMaterial.GREEN_MITHRIL,MiningMaterial.classify("minecraft:"+id).orElseThrow());}
 @Test void incorrectCakeRubyIdCannotBecomeATarget(){assertTrue(MiningMaterial.classify("minecraft:cake").isEmpty());assertEquals(MiningMaterial.RUBY,MiningMaterial.classify("minecraft:red_stained_glass").orElseThrow());}
 @Test void pureOreMeansSolidResourceBlocksNotVanillaOre(){assertTrue(MiningMaterial.classify("minecraft:diamond_ore").isEmpty());assertEquals(MiningMaterial.DIAMOND,MiningMaterial.classify("minecraft:diamond_block").orElseThrow());}
 @Test void paneConnectionsDoNotChangeRegistryRepresentation(){assertEquals(MiningMaterial.TOPAZ,MiningMaterial.classify("minecraft:yellow_stained_glass_pane").orElseThrow());}
 @Test void unknownNullAndLegacyNumbersDoNotClassify(){assertTrue(MiningMaterial.classify(null).isEmpty());assertTrue(MiningMaterial.classify("28707").isEmpty());assertTrue(MiningMaterial.classify("minecraft:bedrock").isEmpty());}
 @Test void disabledMaterialsRemainZeroPriority(){var p=MiningMaterial.mithril(true,false,true,true);assertEquals(0,p.get(MiningMaterial.GREEN_MITHRIL));assertEquals(10,p.get(MiningMaterial.TITANIUM));}
}
