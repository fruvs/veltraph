package dev.veltraph.client.rotation;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

/** Independent executable source-math proof; no Minecraft initialization or production interpolation model. */
class NativeViewRepresentationTest {
    private static final double SCALE=10430.378350470453d;
    private static final double SCALAR=1/SCALE+Math.scalb(1d,-24)+8*Math.ulp(8d);
    private static float table(double radians,boolean cosine){
        long index=(long)(radians*SCALE+(cosine?16384:0));
        return (float)Math.sin((index&65535L)/SCALE);
    }
    private static double[] nativeRay(float yaw,float pitch){
        float x=pitch*.017453292f,y=-yaw*.017453292f;
        float cy=table(y,true),sy=table(y,false),cx=table(x,true),sx=table(x,false);
        return new double[]{sy*cx,-sx,cy*cx};
    }
    private static Vector3f quaternion(float yaw,float pitch){
        return new Vector3f(0,0,-1).rotate(new Quaternionf().rotationYXZ((float)Math.PI-yaw*.017453292f,-pitch*.017453292f,0));
    }
    private static double[] ideal(float yaw,float pitch){
        float x=pitch*.017453292f,y=-yaw*.017453292f;
        return new double[]{Math.sin(y)*Math.cos(x),-Math.sin(x),Math.cos(y)*Math.cos(x)};
    }
    private static double distance(double[] a,double[] b){return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));}
    private static double[] vector(Vector3f v){return new double[]{v.x,v.y,v.z};}
    @Test void measuredTFrameReproducesVanillaLookupAndQuaternionDifference(){
        var ray=nativeRay(14.837981f,1.3619938f);var camera=quaternion(14.837981f,1.3619938f);
        assertArrayEquals(new double[]{-.2559988796710968,-.02367861568927765,.966362476348877},ray,0);
        assertArrayEquals(new double[]{-.25601432,-.023769038,.9663807},vector(camera),1e-7);
        assertEquals(9.352614200652149e-5,distance(ray,vector(camera)),2e-7);
        assertTrue(Math.abs(ray[1]-camera.y)>1e-5,"Old cross-representation predicate rejects a measured correct native frame");
    }
    @Test void everyPositiveAndNegativeLookupBoundaryRespectsTheSourceCellBound(){
        for(int cell=-65536;cell<=65536;cell++){
            double boundary=cell/SCALE;
            for(double radians:new double[]{Math.nextDown(boundary),boundary,Math.nextUp(boundary),(cell+.5)/SCALE}){
                assertTrue(Math.abs(table(radians,false)-Math.sin(radians))<=SCALAR,"sine cell "+cell);
                assertTrue(Math.abs(table(radians,true)-Math.cos(radians))<=SCALAR,"cosine cell "+cell);
            }
        }
    }
    @Test void sourceMultiplicationBoundCoversBothAxesAndWrappedYaw(){
        double product=2*SCALAR+SCALAR*SCALAR+Math.scalb(1d,-24)+Math.ulp(1d);
        for(float yaw=-360;yaw<=360;yaw+=7.5f)for(float pitch=-90;pitch<=90;pitch+=5){
            var actual=nativeRay(yaw,pitch);var continuous=ideal(yaw,pitch);var camera=quaternion(yaw,pitch);
            assertTrue(Math.abs(actual[0]-continuous[0])<=product);
            assertTrue(Math.abs(actual[1]-continuous[1])<=SCALAR);
            assertTrue(Math.abs(actual[2]-continuous[2])<=product);
            assertTrue(Math.abs(actual[0]-camera.x)<=product+Math.abs(continuous[0]-camera.x)+1e-5);
            assertTrue(Math.abs(actual[1]-camera.y)<=SCALAR+Math.abs(continuous[1]-camera.y)+1e-5);
            assertTrue(Math.abs(actual[2]-camera.z)<=product+Math.abs(continuous[2]-camera.z)+1e-5);
        }
    }
    @Test void realCameraAngleAndOldCurrentErrorsStillFailStrictGuards(){
        float yaw=14.837981f,pitch=1.3619938f;
        assertTrue(Math.abs((yaw+.01f)-yaw)>.001);
        assertTrue(distance(nativeRay(yaw,pitch),nativeRay(yaw+.01f,pitch))>1e-5);
        float old=yaw-.1f;assertTrue(Math.abs(old-yaw)>.001);
        assertTrue(distance(nativeRay(yaw,pitch),nativeRay((old+yaw)/2,pitch))>1e-5);
    }
    @Test void wrongQuaternionCannotHideBehindTheLookupAllowance(){
        var expected=quaternion(14.837981f,1.3619938f);
        var changed=quaternion(14.847981f,1.3619938f);
        assertTrue(distance(vector(expected),vector(changed))>1e-5,"Independent expected-quaternion guard rejects real camera displacement");
    }
}
