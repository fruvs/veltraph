package dev.veltraph.client.farming.movement;
import dev.veltraph.client.farming.module.FarmMaintenanceState;
/** Typed completion disposition: a denied or active tick must never fall through to row actions. */
public interface CropCompletionPort {
 enum Result { IDLE, OWNED, DENIED }
 Result tick(boolean admitted,float rowYaw,float rowPitch);
 /** Fixed row mode never falls back to a port that rotates toward missed crops. */
 default Result tickFixedRowView(boolean admitted,float rowYaw,float rowPitch){if(active())cancel();return Result.IDLE;}
 default void observePrimaryRowTick(float rowYaw,TurnDirection side,boolean farmingSideways) {}
 void cancel();boolean active();FarmMaintenanceState maintenanceState();
}
