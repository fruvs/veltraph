package dev.veltraph.client.navigation;
import dev.veltraph.client.service.InputService;
/** Exact individually fenced WATER input pair, never a dry-ground or Boolean fallback. */
public interface WaterPublicationInputs extends InputService {
 WaterPublicationActor actor();
 static InputService require(InputService input,WaterPublicationActor actor){if(actor==null||!(input instanceof WaterPublicationInputs guarded)||guarded.actor()!=actor)throw new IllegalArgumentException("WATER inputs must belong to exact issued actor");return input;}
}
