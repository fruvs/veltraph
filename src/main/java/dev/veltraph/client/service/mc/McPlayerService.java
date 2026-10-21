package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.PlayerService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Minecraft-backed {@link PlayerService}. Reads the local client player's spatial state. All methods
 * are null-safe so callers do not crash when no world/player is present (they should still gate on
 * {@link #isAvailable()}).
 */
public final class McPlayerService implements PlayerService {

    private final Minecraft client;

    public McPlayerService(Minecraft client) {
        this.client = client;
    }

    private LocalPlayer player() {
        return client.player;
    }

    @Override
    public boolean isAvailable() {
        return client.player != null && client.level != null;
    }

    @Override
    public Vec3 position() {
        LocalPlayer p = player();
        if (p == null) {
            return new Vec3(0, 0, 0);
        }
        return new Vec3(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public double blockInteractionRange() {
        var player=player();
        return player==null?Double.NaN:player.blockInteractionRange();
    }

    @Override
    public Vec3 eyePosition() {
        LocalPlayer p = player();
        if (p == null) {
            return new Vec3(0, 0, 0);
        }
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        return new Vec3(eye.x, eye.y, eye.z);
    }

    @Override
    public BlockPos blockPos() {
        LocalPlayer p = player();
        if (p == null) {
            return new BlockPos(0, 0, 0);
        }
        return McConversions.fromMc(p.blockPosition());
    }

    @Override
    public float yaw() {
        LocalPlayer p = player();
        return p == null ? 0f : p.getYRot();
    }

    @Override
    public float pitch() {
        LocalPlayer p = player();
        return p == null ? 0f : p.getXRot();
    }

    @Override
    public boolean onGround() {
        LocalPlayer p = player();
        return p != null && p.onGround();
    }

    @Override
    public double horizontalSpeed() {
        LocalPlayer p = player();
        if (p == null) {
            return 0.0;
        }
        net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    @Override public java.util.Optional<Vec3> horizontalVelocity(){
        var p=player();if(p==null)return java.util.Optional.empty();var velocity=p.getDeltaMovement();
        return java.util.Optional.of(new Vec3(velocity.x,0,velocity.z));
    }

    @Override
    public Direction facing() {
        LocalPlayer p = player();
        return p == null ? Direction.NORTH : McConversions.fromMc(p.getDirection());
    }
}
