package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CapturedGourdApproachSafetyTest {
    static class World implements WorldService {
        Set<BlockPos> missing = new HashSet<>();
        Set<BlockPos> solid = new HashSet<>();
        boolean floor = true;

        public boolean isLoaded(BlockPos pos) { return !missing.contains(pos); }
        public boolean isPassable(BlockPos pos) { return !solid.contains(pos); }
        public boolean isStandable(BlockPos pos) { return floor && pos.y() == 63; }
        public String blockId(BlockPos pos) {
            return solid.contains(pos) ? "minecraft:stone" : "minecraft:air";
        }
    }

    @Test void actualFootprintRejectsUnloadedBodyObstructionAndMissingSupport() {
        var world = new World();
        var from = new Vec3(.5, 64, .5);
        var to = new Vec3(.5, 64, -1.5);
        assertTrue(CapturedGourdApproachSafety.clear(world, from, to, Direction.NORTH));
        world.missing.add(new BlockPos(0, 65, -1));
        assertFalse(CapturedGourdApproachSafety.clear(world, from, to, Direction.NORTH));
        world.missing.clear();
        world.solid.add(new BlockPos(0, 64, -1));
        assertFalse(CapturedGourdApproachSafety.clear(world, from, to, Direction.NORTH));
        world.solid.clear();
        world.floor = false;
        assertFalse(CapturedGourdApproachSafety.clear(world, from, to, Direction.NORTH));
    }

    @Test void noUnsupportedElevationOrUnboundedApproach() {
        var world = new World();
        assertFalse(CapturedGourdApproachSafety.clear(world, new Vec3(.5, 64, .5),
                new Vec3(.5, 65, .5), Direction.NORTH));
        assertFalse(CapturedGourdApproachSafety.clear(world, new Vec3(.5, 64, .5),
                new Vec3(30.5, 64, .5), Direction.EAST));
    }

    @Test void targetBehindIssuedMovementCannotAuthorizeWalkingAway() {
        var world = new World();
        assertFalse(CapturedGourdApproachSafety.clear(world, new Vec3(.5, 64, -1.61),
                new Vec3(.5, 64, -1.5), Direction.NORTH));
    }
}
