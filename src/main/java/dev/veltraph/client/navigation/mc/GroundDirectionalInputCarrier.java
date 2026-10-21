package dev.veltraph.client.navigation.mc;
/** Negative-only native call observations. No public constructor, input setter or admission grant. */
public interface GroundDirectionalInputCarrier {
 default Object directionalApplyCall(){return null;}
 default boolean directionalApplyCallCurrent(Object exact,boolean returned){return false;}
 default void directionalInvalidateCall(){}
 default LegacyTravelPublicationLease.Leg.ProfileInputFrame directionalAppliedFrame(net.minecraft.world.phys.Vec3 exact){return null;}
 default LegacyTravelPublicationLease.Leg.ProfileInputFrame directionalTravelFrame(net.minecraft.world.phys.Vec3 exact){return null;}
 default Object directionalRelativeCall(net.minecraft.world.phys.Vec3 exact){return null;}
 default boolean directionalRelativeCurrent(Object exact,net.minecraft.world.phys.Vec3 input){return false;}
}
