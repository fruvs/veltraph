package dev.veltraph.client.foraging;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TreeDiscoveryTest {
 @Test void branchesRemainInOneTreeAndSpeciesRemainSeparate() {
  Map<BlockPos,String> logs=new HashMap<>();
  logs.put(new BlockPos(0,70,0),"minecraft:birch_log");
  logs.put(new BlockPos(0,71,0),"minecraft:birch_log");
  logs.put(new BlockPos(1,72,0),"minecraft:birch_log");
  logs.put(new BlockPos(1,70,0),"minecraft:oak_log");
  var trees=TreeDiscovery.discover(logs,20);
  assertEquals(2,trees.size());assertEquals(3,trees.get(0).logs().size());
  assertEquals(new BlockPos(0,70,0),trees.get(0).trunk());
 }
 @Test void oversizedTreeIsRejectedWholeRatherThanSilentlyTruncated() {
  Map<BlockPos,String> logs=new HashMap<>();for(int y=0;y<10;y++) logs.put(new BlockPos(0,y,0),"minecraft:oak_log");
  assertTrue(TreeDiscovery.discover(logs,9).isEmpty());assertEquals(10,TreeDiscovery.discover(logs,10).get(0).logs().size());
 }
 @Test void sourceDensityBoundsAndCheckpointConstantsAreExact() {
  assertEquals(7,ForagingRegion.BIRCH.minimumLogs(ForagingRegion.Density.MEDIUM));
  assertEquals(16,ForagingRegion.SPRUCE.minimumLogs(ForagingRegion.Density.HIGH));
  assertEquals(13,ForagingRegion.DARK_OAK.minimumLogs(ForagingRegion.Density.MEDIUM));
  assertFalse(ForagingRegion.BIRCH.matches("minecraft:dark_oak_log"));
  assertTrue(ForagingRegion.BIRCH.matches("minecraft:stripped_birch_log"));
  assertTrue(ForagingRegion.BIRCH.contains(new BlockPos(-299,81,1)));
  assertFalse(ForagingRegion.BIRCH.contains(new BlockPos(0,70,0)));
  assertEquals(67,HubTreeRoute.CHECKPOINTS.size());
 }
 @Test void keywordGuardCannotSelectFrogspawnOrCoalBlock() {
  assertFalse(LogFilter.matches("minecraft:frogspawn","log,wood,stem"));
  assertFalse(LogFilter.matches("minecraft:coal_block","oak_log"));
  assertTrue(LogFilter.matches("minecraft:oak_log","log,wood,stem"));
  assertTrue(LogFilter.matches("minecraft:oak_log","oak"));
  assertFalse(LogFilter.matches("minecraft:dark_oak_log","oak"));
  assertTrue(LogFilter.matches("minecraft:warped_stem","log,wood,stem"));
 }
}
