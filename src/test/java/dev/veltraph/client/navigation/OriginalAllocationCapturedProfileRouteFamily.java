package dev.veltraph.client.navigation;
/** Pure initial-family classification only, never a native publication/custody capability. */
final class OriginalAllocationCapturedProfileRouteFamily {
    private OriginalAllocationCapturedProfileRouteFamily(){}
    public static boolean supported(OriginalAllocationGroundNativePhysicsProfile.State actual) {
        if(actual==null||OriginalAllocationGroundNativePhysicsProfile.classify(actual).isEmpty()||actual.effects().size()!=7||actual.jumpAmplifier()!=5)return false;
        long bits=Double.doubleToLongBits(actual.baseSpeed());
        return bits==Double.doubleToLongBits(OriginalAllocationGroundNativePhysicsProfile.CAPTURED_360)
                ||bits==Double.doubleToLongBits(OriginalAllocationGroundNativePhysicsProfile.CAPTURED_400);
    }
}
