package dev.veltraph.client.testutil;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;

/** Controllable {@link RotationService}: {@link #aim} records the call and updates yaw/pitch. */
public final class FakeRotationService implements RotationService {

    public float yaw;
    public float pitch;
    public int aimCalls;

    public FakeRotationService() {
    }

    public FakeRotationService(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public float yaw() {
        return yaw;
    }

    @Override
    public float pitch() {
        return pitch;
    }

    @Override
    public void aim(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        aimCalls++;
    }

    @Override
    public void lookAt(Vec3 target) {
        // Not exercised by the row engine (which aims by yaw/pitch).
    }
}
