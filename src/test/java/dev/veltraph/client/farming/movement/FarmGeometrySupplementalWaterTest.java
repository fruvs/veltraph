package dev.veltraph.client.farming.movement;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Pure loaded-block/collision model; no Minecraft world, permission, or native receipts are synthesized. */
class FarmGeometrySupplementalWaterTest {
 static final class Actor implements PlayerService {
  Vec3 p=new Vec3(50.5,70,-46.5);double reach=4.5;
  public boolean isAvailable(){return true;}public Vec3 position(){return p;}public BlockPos blockPos(){return p.toBlockPos();}public float yaw(){return -90;}public float pitch(){return 2;}public boolean onGround(){return true;}public double horizontalSpeed(){return 0;}public Direction facing(){return Direction.EAST;}public double blockInteractionRange(){return reach;}
 }
 static final class Blocks implements WorldService,FarmCollisionProbe {
  final Map<BlockPos,String> changes=new HashMap<>();BlockPos unknown;boolean shapeBlocked;
  public boolean isLoaded(BlockPos p){return !p.equals(unknown);}public boolean isPassable(BlockPos p){return Set.of("minecraft:air","minecraft:water","minecraft:wheat").contains(blockId(p));}public boolean isStandable(BlockPos p){return blockId(p).equals("minecraft:dirt");}
  public String blockId(BlockPos p){if(changes.containsKey(p))return changes.get(p);if(p.y()==69)return "minecraft:dirt";if(p.y()==70&&p.x()==55&&p.z()>=-45&&p.z()<=-41)return "minecraft:water";if(p.y()==70&&p.x()==55&&(p.z()==-46||p.z()==-40))return "minecraft:dirt";if(p.y()==70&&((p.x()>=51&&p.x()<=54)||p.x()==56||p.x()==58)&&p.z()>=-46&&p.z()<=-41)return "minecraft:farmland";if(p.y()==71&&p.x()==56&&p.z()>=-46&&p.z()<=-41)return "minecraft:wheat";return "minecraft:air";}
  public boolean loaded(Vec3 p){return isLoaded(p.toBlockPos());}public boolean clear(Vec3 p,boolean allowWater){BlockPos b=p.toBlockPos();return !shapeBlocked&&loaded(p)&&isPassable(b)&&(allowWater||!isWater(b));}public OptionalDouble supportY(double x,double z,double highest,int down){double best=Double.NEGATIVE_INFINITY;for(int y=(int)Math.floor(highest);y>=Math.floor(highest)-down;y--){var b=new BlockPos((int)Math.floor(x),y,(int)Math.floor(z));double top=blockId(b).equals("minecraft:dirt")?y+1:blockId(b).equals("minecraft:farmland")?y+.9375:Double.NEGATIVE_INFINITY;if(top<=highest+.001)best=Math.max(best,top);}return best==Double.NEGATIVE_INFINITY?OptionalDouble.empty():OptionalDouble.of(best);}public boolean water(Vec3 p){return isWater(p.toBlockPos());}
 }
 static FarmGeometry geometry(Actor a,Blocks b){var g=new FarmGeometry(new MapConfigService(),a,b);g.setHeading(Direction.EAST);g.setSelectedCrop("minecraft:wheat");g.setCollisionProbe(b);return g;}
 @Test void findsOnlyAnOutOfNativeReachLoadedChannel(){var a=new Actor();var b=new Blocks();var p=geometry(a,b).supplementalCoveragePlan(TurnDirection.LEFT);assertNotNull(p);assertEquals(new Vec3(55.5,71,-45.5),p.capFeet());assertEquals(new Vec3(55.5,70,-40.5),p.channelFar());}
 @Test void reachableCropDoesNotInventSupplement(){var a=new Actor();a.reach=6;assertNull(geometry(a,new Blocks()).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void unknownNativeReachRefusesSupplement(){var a=new Actor();a.reach=Double.NaN;assertNull(geometry(a,new Blocks()).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void farmlandCannotReplaceTheDryCap(){var b=new Blocks();b.changes.put(new BlockPos(55,70,-46),"minecraft:farmland");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void missingRealChannelFloorRefusesPlan(){var b=new Blocks();b.changes.put(new BlockPos(55,69,-43),"minecraft:air");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void unknownColumnRefusesPlan(){var b=new Blocks();b.unknown=new BlockPos(55,70,-43);assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void foreignFluidNeverBecomesSupportedWater(){var b=new Blocks();b.changes.put(new BlockPos(55,70,-43),"minecraft:lava");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void changedCapRevokesPreviouslyReadPlan(){var a=new Actor();var b=new Blocks();var g=geometry(a,b);var plan=g.supplementalCoveragePlan(TurnDirection.LEFT);assertNotNull(plan);b.changes.put(new BlockPos(55,70,-46),"minecraft:farmland");assertFalse(g.supplementalCoverageCurrent(plan));}
 @Test void emptyFarColumnDoesNotInventAnotherPass(){var b=new Blocks();for(int z=-45;z<=-41;z++)b.changes.put(new BlockPos(56,71,z),"minecraft:air");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void livePlanSurvivesItsOwnCompleteHarvest(){var a=new Actor();var b=new Blocks();var g=geometry(a,b);var p=g.supplementalCoveragePlan(TurnDirection.LEFT);assertNotNull(p);for(int z=-45;z<=-41;z++)b.changes.put(new BlockPos(56,71,z),"minecraft:air");assertTrue(g.supplementalCoverageCurrent(p));}
 @Test void standingHeadUnknownAboveCrouchedPoseRefusesPlan(){var b=new Blocks();b.unknown=new BlockPos(55,74,-46);assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void laterLaneReachDoesNotBorrowAnotherLaneWaterPass(){var b=new Blocks();b.changes.put(new BlockPos(52,70,-46),"minecraft:air");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT),"The loaded next ordinary lane52 owns later water55 reach");}

 @Test void waterCeilingSlabAboveStandingBodyDoesNotRequireAir(){var b=new Blocks();b.changes.put(new BlockPos(55,72,-43),"minecraft:spruce_slab");var g=geometry(new Actor(),b);var plan=g.supplementalCoveragePlan(TurnDirection.LEFT);assertNotNull(plan);assertTrue(g.supplementalCoverageCurrent(plan));}
 @Test void unknownUpperWaterCeilingRemainsRefused(){var b=new Blocks();b.unknown=new BlockPos(55,72,-43);assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void lowSlabInsideStandingBodyRemainsRefused(){var b=new Blocks();b.changes.put(new BlockPos(55,71,-43),"minecraft:spruce_slab");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void fullBlockInsideStandingBodyRemainsRefused(){var b=new Blocks();b.changes.put(new BlockPos(55,71,-43),"minecraft:stone");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}
 @Test void nativeShapeObstructionStillRevokesPreviouslyReadPlan(){var b=new Blocks();b.changes.put(new BlockPos(55,72,-43),"minecraft:spruce_slab");var g=geometry(new Actor(),b);var plan=g.supplementalCoveragePlan(TurnDirection.LEFT);assertNotNull(plan);b.shapeBlocked=true;assertFalse(g.supplementalCoverageCurrent(plan));}
 @Test void capJumpHeadroomStillRefusesSlabInItsHigherBody(){var b=new Blocks();b.changes.put(new BlockPos(55,72,-46),"minecraft:spruce_slab");assertNull(geometry(new Actor(),b).supplementalCoveragePlan(TurnDirection.LEFT));}

}
