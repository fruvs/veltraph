package dev.veltraph.client.observation.unexpected;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeNavigationCommandTest {
    @Test void nativeHubAndWarpKeepCategoryWithoutDestinationText() {
        assertTrue(NativeNavigationCommand.parse("/hub").orElseThrow().exactTarget().isEmpty());
        assertTrue(NativeNavigationCommand.parse("/warp private-place").orElseThrow().exactTarget().isEmpty());
    }
    @Test void unrelatedChatAndCommandsCannotCreateCorrelation() {
        for(var input:new String[]{"hi","/msg somebody hi","/give @s stone","/gamemode creative","/warp","/hub extra","/tp NaN 70 0","/tp ^ ~ ^","/tp ~. ~ ~"})assertTrue(NativeNavigationCommand.parse(input).isEmpty(),input);
    }
    @Test void numericLocalTeleportHasExactTarget() {
        var target=NativeNavigationCommand.parse("/tp 30.5 70 -0.5").orElseThrow().exactTarget().orElseThrow();
        assertEquals(30.5,target.x());assertEquals(70,target.y());assertEquals(-.5,target.z());
        assertEquals(target,NativeNavigationCommand.parse("/teleport @s 30.5 70 -0.5").orElseThrow().exactTarget().orElseThrow());
    }
    @Test void RelativeCoordinatesRemainUnknownRatherThanGuessed() {
        for(var input:new String[]{"/tp ~ ~ ~","/tp ~ 70 ~2","/teleport @s ^1 ^2 ^3"})assertTrue(NativeNavigationCommand.parse(input).orElseThrow().exactTarget().isEmpty());
    }
    @Test void noOversizedOrOutOfWorldCoordinates() {assertTrue(NativeNavigationCommand.parse("/warp "+"a".repeat(300)).isEmpty());assertTrue(NativeNavigationCommand.parse("/tp 40000000 70 0").isEmpty());assertTrue(NativeNavigationCommand.parse(null).isEmpty());}
}
