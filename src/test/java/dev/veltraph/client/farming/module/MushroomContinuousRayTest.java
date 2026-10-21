package dev.veltraph.client.farming.module;
import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Model-only integration of renderer lag, actual bed predicate and unchanged continuous movement. */
class MushroomContinuousRayTest {
 static class World implements WorldService {
  Map<BlockPos,String> blocks=new HashMap<>();BlockPos missing;
  World(){for(int x=-2;x<=2;x++){blocks.put(new BlockPos(x,70,0),"minecraft:quartz_slab");blocks.put(new BlockPos(x,71,1),"minecraft:mycelium");blocks.put(new BlockPos(x,71,-1),"minecraft:white_stained_glass");}blocks.put(new BlockPos(0,72,1),"minecraft:brown_mushroom");}
  public boolean isLoaded(BlockPos p){return !p.equals(missing);}public String blockId(BlockPos p){return isLoaded(p)?blocks.getOrDefault(p,"minecraft:air"):"";}
  public boolean isPassable(BlockPos p){return blockId(p).equals("minecraft:air");}public boolean isStandable(BlockPos p){return blockId(p).equals("minecraft:quartz_slab");}
  public boolean bodyClear(Vec3 p,double w,double h){return true;}public boolean hasGroundSupport(Vec3 p,double w){return true;}
 }
 static class Breaking implements BlockBreakingService {boolean attack;BlockPos fresh=new BlockPos(0,72,1);String cached="minecraft:air";java.util.function.Predicate<BlockPos> guard;
  public void setBreaking(boolean b){attack=b;}public boolean isBreaking(){return attack;}public String targetBlockId(){return cached;}public Optional<BlockPos> freshTargetBlockPos(){return Optional.ofNullable(fresh);}public Optional<BlockPos> targetBlockPos(){return Optional.of(new BlockPos(-1,72,1));}public void installTargetGuard(Object o,java.util.function.Predicate<BlockPos> p){guard=p;}}
 @Test void freshLegitimateRayRetainsAttackAcrossPredictedCachedAirAndNeverStopsWS(){var w=new World();var b=new Breaking();var in=new RecordingInputService();var p=new FakePlayerService();p.position=new Vec3(.5,70.5,.5);p.blockPos=p.position.toBlockPos();p.onGround=true;var r=new FakeRotationService();r.yaw=63.43f;var cfg=new MapConfigService();cfg.set(FarmingConfig.MUSHROOM_FARM_DESIGN,MushroomFarmDesign.RING_CURRENT);var m=new MushroomFarmModule(new FarmServices(new FakeClock(),p,w,in,r,b,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());m.setEnabled(true);m.onTick();assertTrue(in.forward);assertFalse(in.back||in.left||in.right||in.sneak||in.sprint);assertTrue(b.attack);assertEquals(63.43f,r.yaw,.001);b.fresh=null;m.onTick();assertTrue(in.forward);assertFalse(b.attack);m.setEnabled(false);}
 @Test void unknownOrUnsupportedMarkerRayDoesNotRetainAttack(){for(boolean missing:new boolean[]{true,false}){var w=new World();var b=new Breaking();if(missing){b.fresh=new BlockPos(3,72,1);w.missing=b.fresh;}else{b.fresh=new BlockPos(94,70,85);w.blocks.put(b.fresh,"minecraft:brown_mushroom");}var p=new FakePlayerService();p.position=new Vec3(.5,70.5,.5);p.blockPos=p.position.toBlockPos();p.onGround=true;var r=new FakeRotationService();r.yaw=63.43f;var cfg=new MapConfigService();cfg.set(FarmingConfig.MUSHROOM_FARM_DESIGN,MushroomFarmDesign.RING_CURRENT);var in=new RecordingInputService();var m=new MushroomFarmModule(new FarmServices(new FakeClock(),p,w,in,r,b,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());m.setEnabled(true);m.onTick();assertTrue(in.forward);assertFalse(b.attack);assertFalse(b.guard.test(b.fresh));m.setEnabled(false);}}
 @Test void changedExplicitProfileCannotContinueTheOldNativeLayout(){var w=new World();var b=new Breaking();var p=new FakePlayerService();p.position=new Vec3(.5,70.5,.5);p.blockPos=p.position.toBlockPos();p.onGround=true;var r=new FakeRotationService();r.yaw=63.43f;var cfg=new MapConfigService();cfg.set(FarmingConfig.MUSHROOM_FARM_DESIGN,MushroomFarmDesign.RING_CURRENT);var in=new RecordingInputService();var m=new MushroomFarmModule(new FarmServices(new FakeClock(),p,w,in,r,b,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());m.setEnabled(true);m.onTick();cfg.set(FarmingConfig.MUSHROOM_FARM_DESIGN,MushroomFarmDesign.FARMHELPER_SDS);m.onTick();assertFalse(m.isEnabled());assertFalse(in.forward||in.back||in.left||in.right||b.attack);}
}
