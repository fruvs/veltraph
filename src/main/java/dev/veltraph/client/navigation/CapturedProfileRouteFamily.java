package dev.veltraph.client.navigation;
/** Pure initial-family classification only, never a native publication/custody capability. */
public final class CapturedProfileRouteFamily {
    private CapturedProfileRouteFamily(){}
    public static boolean supported(GroundNativePhysicsProfile.State actual) {
        if(actual==null||!GroundNativePhysicsProfile.supportedState(actual)||actual.effects().size()!=7||actual.jumpAmplifier()!=5)return false;
        long bits=Double.doubleToLongBits(actual.baseSpeed());
        return bits==Double.doubleToLongBits(GroundNativePhysicsProfile.CAPTURED_360)
                ||bits==Double.doubleToLongBits(GroundNativePhysicsProfile.CAPTURED_400);
    }
}
