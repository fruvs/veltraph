package dev.veltraph.client.navigation;
import dev.veltraph.client.service.InputService;
/** Seven individually guarded movement setters belonging to the exact publication actor. */
public interface GroundPublicationInputs extends InputService {
    GroundPublicationActor actor();
    static InputService require(InputService input,GroundPublicationActor actor){
        if(actor==null||!(input instanceof GroundPublicationInputs guarded)||guarded.actor()!=actor)
            throw new IllegalArgumentException("Ground input port must belong to the exact publication actor");
        return input;
    }
}
