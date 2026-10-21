package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.service.PlayerService;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

/**
 * Concrete {@link RowGeometry} using deterministic block-ahead / can-stand checks against the world
 * and player services. This is the "row-end predicate using block-ahead/can-stand checks" from the
 * goal's movement foundation.
 *
 * <p>A lane direction is walkable when the floor below the next block is standable and the body+head
 * blocks ahead are passable. {@code ALLOW_IRREGULAR_CORNERS} relaxes the floor requirement (accepting
 * a one-block step down) so imperfect corners are tolerated.
 *
 * <p>Depends only on service interfaces and pure value types, so it remains unit-testable.
 */
public final class WorldRowGeometry implements RowGeometry {

    private final ConfigService config;
    private final PlayerService player;
    private final WorldService world;

    public WorldRowGeometry(ConfigService config, PlayerService player, WorldService world) {
        this.config = config;
        this.player = player;
        this.world = world;
    }

    @Override
    public boolean atRowEnd() {
        return !forwardLaneValid();
    }

    @Override
    public boolean forwardLaneValid() {
        return laneWalkable(player.facing());
    }

    @Override
    public boolean sideMovable(TurnDirection side) {
        Direction facing = player.facing();
        Direction dir = switch (side) {
            case LEFT -> facing.rotateYCounterclockwise();
            case RIGHT -> facing.rotateYClockwise();
            case FORWARD -> facing;
            case BACK -> facing.opposite();
        };
        return laneWalkable(dir);
    }

    /**
     * Whether the player could step one block in {@code dir}: head + body clear, and a standable
     * floor either level or (if irregular corners are allowed) one block down.
     */
    private boolean laneWalkable(Direction dir) {
        BlockPos base = player.blockPos();
        BlockPos body = base.offset(dir);
        BlockPos head = body.up();
        BlockPos floor = body.down();

        if (!world.isLoaded(body)) {
            return false;
        }
        if (!world.isPassable(body) || !world.isPassable(head)) {
            return false;
        }
        // Farm-aware floor check (accepts farmland/soul sand/paths), not the full-solid-square test.
        if (world.isSolidFloor(floor)) {
            return true;
        }
        // Tolerate a single step-down corner only when irregular corners are permitted.
        return config.get(FarmingConfig.ALLOW_IRREGULAR_CORNERS) && world.isSolidFloor(floor.down());
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "player", player,
                "world", world);
    }
}
