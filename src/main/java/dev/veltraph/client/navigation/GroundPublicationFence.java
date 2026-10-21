package dev.veltraph.client.navigation;
import java.util.Objects;
import java.util.function.BooleanSupplier;
/** Effect fence called only after all route geometry and diagnostic reads have completed. */
public final class GroundPublicationFence {
    private GroundPublicationFence(){}
    public static boolean publish(GroundPublicationActor actor,BooleanSupplier nativeCurrent,
            boolean camera,float yaw,Runnable acknowledgeOwnCamera,Runnable keys){
        Objects.requireNonNull(actor);Objects.requireNonNull(nativeCurrent);Objects.requireNonNull(acknowledgeOwnCamera);Objects.requireNonNull(keys);
        if(!actor.current()||!nativeCurrent.getAsBoolean())return false;
        if(camera){
            if(!Float.isFinite(yaw)||actor.publishYaw(yaw)<=0||!actor.current())return false;
            // The actor has just certified its own actual ticket. This callback may synchronize
            // the navigator's additional epoch fence, never adopt an unrelated later publication.
            acknowledgeOwnCamera.run();
        }
        if(!actor.current()||!nativeCurrent.getAsBoolean())return false;
        try {keys.run();}
        catch(GroundPublicationRevokedException revoked){return false;}
        return actor.current()&&nativeCurrent.getAsBoolean();
    }
}
