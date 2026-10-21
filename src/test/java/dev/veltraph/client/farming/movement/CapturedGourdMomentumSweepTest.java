package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.*;
import dev.veltraph.client.service.WorldService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CapturedGourdMomentumSweepTest {
 static class World implements WorldService {Set<BlockPos> blocks=new HashSet<>();public boolean isLoaded(BlockPos p){return true;}public boolean isPassable(BlockPos p){return !blocks.contains(p);}public boolean isStandable(BlockPos p){return p.y()==70;}public boolean envelopeProbe(Vec3 center,double margin){return bodyClear(center,.3+margin,1.8)&&hasGroundSupport(center,.3-margin);}
 public String blockId(BlockPos p){return blocks.contains(p)?"minecraft:stone":"minecraft:air";}}
 @Test void oldMomentumBlockedEvenThoughNewKeyDirectionIsClear(){var w=new World();var from=new Vec3(.5,71,.5);var newEastStep=new Vec3(.8,71,.5);assertTrue(CapturedGourdSweptWalk.clear(w,from,newEastStep));w.blocks.add(new BlockPos(0,71,1));assertTrue(CapturedGourdSweptWalk.clear(w,from,newEastStep));assertFalse(CapturedGourdSweptWalk.momentumClear(w,from,newEastStep,Optional.of(new Vec3(0,0,.2))));}
 @Test void unknownMomentumFailsClosedButStationarySupportedEnvelopePasses(){var w=new World();assertFalse(CapturedGourdSweptWalk.momentumClear(w,new Vec3(.5,71,.5),new Vec3(.8,71,.5),Optional.empty()));assertTrue(CapturedGourdSweptWalk.momentumClear(w,new Vec3(.5,71,.5),new Vec3(.8,71,.5),Optional.of(new Vec3(0,0,0))));}
 @Test void nonFiniteEveryInputComponentFailsClosed(){var w=new World();var from=new Vec3(.5,71,.5);var to=new Vec3(.8,71,.5);assertFalse(CapturedGourdSweptWalk.momentumClear(w,from,to,Optional.of(new Vec3(0,Double.NaN,0))));assertFalse(CapturedGourdSweptWalk.momentumClear(w,new Vec3(.5,Double.NaN,.5),to,Optional.of(new Vec3(0,0,0))));assertFalse(CapturedGourdSweptWalk.momentumClear(w,from,new Vec3(.8,71,Double.NaN),Optional.of(new Vec3(0,0,0))));}
 @Test void interiorSupportGapBetweenSparseEndpointRaysIsRejected(){var w=new World(){@Override public boolean hasGroundSupport(Vec3 p,double radius){return !(p.x()>.69&&p.x()<.81&&p.z()>.69&&p.z()<.81);}};assertFalse(CapturedGourdSweptWalk.momentumClear(w,new Vec3(.5,71,.5),new Vec3(1,71,.5),Optional.of(new Vec3(0,0,.2))));}

 @Test void legacyOnlyWorldHasNoEnvelopeCapability(){WorldService legacy=new WorldService(){public boolean isLoaded(BlockPos p){return true;}public boolean isPassable(BlockPos p){return true;}public boolean isStandable(BlockPos p){return true;}public String blockId(BlockPos p){return "minecraft:air";}};assertFalse(CapturedGourdSweptWalk.momentumClear(legacy,new Vec3(.5,71,.5),new Vec3(.8,71,.5),Optional.of(new Vec3(0,0,0))));}

}
