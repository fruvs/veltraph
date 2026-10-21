package dev.veltraph.client.service.stub;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;

/** Safe placeholder {@link RotationService} used until the Minecraft-backed adapter is implemented. */
public final class NoOpRotationService implements RotationService {

    @Override
    public float yaw() {
        return 0f;
    }

    @Override
    public float pitch() {
        return 0f;
    }

    @Override
    public void aim(float yaw, float pitch) {
    }

    @Override
    public void lookAt(Vec3 target) {
    }
}
