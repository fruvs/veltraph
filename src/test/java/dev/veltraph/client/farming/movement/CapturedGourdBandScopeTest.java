package dev.veltraph.client.farming.movement;
import dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CapturedGourdBandScopeTest {
 @Test void onlyTheTwoOwnedCropAxesAndHeightAreEligible(){var band=new CapturedGourdWallPlanner.Band(Direction.EAST,Direction.WEST,1,"X:-46:-45:71",-46,-45,71);assertTrue(band.contains(new BlockPos(-46,71,-100)));assertTrue(band.contains(new BlockPos(-45,71,-100)));assertFalse(band.contains(new BlockPos(-44,71,-100)));assertFalse(band.contains(new BlockPos(-47,71,-100)));assertFalse(band.contains(new BlockPos(-45,72,-100)));}
 @Test void rotatedBandUsesZAxisAndUnknownScopeFailsClosed(){var band=new CapturedGourdWallPlanner.Band(Direction.NORTH,Direction.SOUTH,1,"Z:2:3:71",2,3,71);assertTrue(band.contains(new BlockPos(100,71,2)));assertFalse(band.contains(new BlockPos(100,71,4)));assertFalse(new CapturedGourdWallPlanner.Band(Direction.EAST,Direction.WEST,1,"unknown").contains(new BlockPos(0,71,0)));}
}
