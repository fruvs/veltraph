package dev.veltraph.client.world;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production scalar projection compared with the retained original instance-distance methods. */
final class CoordinateDistanceProjectionParityTest {
    private static void same(double x,double y,double z,Vec3 target){
        double old=new Vec3(x,y,z).distanceTo(target);
        double actual=Vec3.distanceFromCoordinates(x,y,z,target);
        assertEquals(Double.doubleToRawLongBits(old),Double.doubleToRawLongBits(actual));
        assertEquals(old<.001,actual<.001);
    }
    @Test void exactThresholdBelowEqualAndAboveRemainUnchanged(){
        var zero=new Vec3(0,0,0);
        for(double distance:new double[]{Math.nextDown(.001),.001,Math.nextUp(.001)}){
            same(distance,0,0,zero);same(0,-distance,0,zero);same(0,0,distance,zero);
        }
        assertTrue(Vec3.distanceFromCoordinates(Math.nextDown(.001),0,0,zero)<.001);
        assertFalse(Vec3.distanceFromCoordinates(.001,0,0,zero)<.001);
        assertFalse(Vec3.distanceFromCoordinates(Math.nextUp(.001),0,0,zero)<.001);
    }
    @Test void finiteTranslatedAndDiagonalDistancesKeepRawBits(){
        for(Vec3 target:List.of(new Vec3(.5,70,.5),new Vec3(-10.5,-64,19.5),new Vec3(0,0,0)))
            for(double delta:new double[]{-.001,-.000999999999,.0,.000999999999,.001,3.5})
                same(target.x()+delta,target.y()-delta,target.z()+delta,target);
    }
    @Test void signedZerosSubnormalAndRoundingInputsKeepRawBits(){
        for(double x:new double[]{-0.0,0.0,Double.MIN_VALUE,-Double.MIN_VALUE,Math.nextUp(1.0),Math.nextDown(1.0)})
            for(double y:new double[]{-0.0,0.0,Double.MIN_NORMAL,-Double.MIN_NORMAL})
                for(Vec3 target:List.of(new Vec3(-0.0,0.0,-0.0),new Vec3(1,1,1)))same(x,y,-x,target);
    }
    @Test void infinitiesNaNsAndSquaredOverflowRetainOriginalResult(){
        double payload=Double.longBitsToDouble(0x7ff8000000001234L);
        for(double x:new double[]{Double.NaN,payload,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE,-Double.MAX_VALUE,1E200})
            for(Vec3 target:List.of(new Vec3(0,0,0),new Vec3(Double.POSITIVE_INFINITY,0,0),new Vec3(payload,Double.MAX_VALUE,Double.NEGATIVE_INFINITY)))
                same(x,-x,x,target);
    }
    @Test void deterministicRawDoubleCorpusPreservesOriginalOperationOrder(){
        Random random=new Random(0x19fba);
        for(int i=0;i<3000;i++){
            double x=Double.longBitsToDouble(random.nextLong()),y=Double.longBitsToDouble(random.nextLong()),z=Double.longBitsToDouble(random.nextLong());
            var target=new Vec3(Double.longBitsToDouble(random.nextLong()),Double.longBitsToDouble(random.nextLong()),Double.longBitsToDouble(random.nextLong()));
            same(x,y,z,target);
        }
    }
    @Test void nullTargetStillFailsAtFirstTargetCoordinateBeforeArithmetic(){
        assertThrows(NullPointerException.class,()->new Vec3(1,2,3).distanceTo(null));
        assertThrows(NullPointerException.class,()->Vec3.distanceFromCoordinates(1,2,3,null));
    }
}
