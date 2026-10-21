package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundNativeGravityTraitsTest {
    @Test void nativeFalseMustMatchActualAttributeGravity(){assertTrue(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,.08D,.08D));}
    @Test void noGravityTrueCannotMasqueradeAsPointZeroEightAttribute(){
        assertFalse(GroundNativeGravityTraits.consistent(true,Boolean.TRUE,.08D,0D));
        assertFalse(GroundNativeGravityTraits.consistent(true,Boolean.FALSE,.08D,.08D));}
    @Test void rawMetadataMustBeTheActualFalseBoolean(){
        for(Object value:new Object[]{null,Boolean.TRUE,0,"false"})assertFalse(GroundNativeGravityTraits.consistent(false,value,.08D,.08D));}
    @Test void nativeGetterMismatchAndSignedZeroDenied(){
        assertFalse(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,.08D,0D));
        assertFalse(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,+0D,-0D));}
    @Test void nonFiniteGravityCannotPassEquality(){
        assertFalse(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,Double.NaN,Double.NaN));
        assertFalse(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY));}
    @Test void consistencyDoesNotExpandOriginalNumericClassifier(){
        assertTrue(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,.09D,.09D));
        var outside=new GroundNativePhysicsProfile.State((double).4F,(double).4F,.4F,java.util.List.of(),(double).42F,.09D,1D,1D,-1);
        assertTrue(GroundNativePhysicsProfile.classify(outside).isEmpty());
    }
}
