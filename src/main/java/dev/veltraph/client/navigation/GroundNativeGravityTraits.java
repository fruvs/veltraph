package dev.veltraph.client.navigation;
/** Pure native consistency only. Numeric/effect profile admission remains in its original classifier. */
public final class GroundNativeGravityTraits {
    private GroundNativeGravityTraits() {}
    public static boolean consistent(boolean nativeNoGravity,Object actualMetadataValue,double attributeGravity,double nativeGravity){
        return !nativeNoGravity&&Boolean.FALSE.equals(actualMetadataValue)&&Double.isFinite(attributeGravity)
            &&Double.isFinite(nativeGravity)&&Double.doubleToLongBits(attributeGravity)==Double.doubleToLongBits(nativeGravity);
    }
}
