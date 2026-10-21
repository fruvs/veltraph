package dev.veltraph.client.navigation;
/** Explicit WATER actor; native permission comes only from its actual issued Root leg. */
public interface WaterPublicationActor {
 boolean current();long publishYaw(float yaw);long publishTrackedPose(float yaw,float pitch);
 WaterPublicationIdentity.Scope scope();boolean bindPlan(Object plan);boolean planCurrent(Object plan);void retirePlan(Object plan);
 void cleanupOwned();
}
