package dev.veltraph.client.hunting;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class HuntingMotionTest {
    @Test void sourceFiveStepMeanUsesNativePerTickUnits(){var m=new HuntingMotion();m.sample(new Vec3(.1,.2,-.1));m.sample(new Vec3(.3,0,.1));assertEquals(new Vec3(11,20.5,30),m.predict(new Vec3(10,20,30)));}
    @Test void sourceFortySampleFifoEvictsOldMotionAndResets(){var m=new HuntingMotion();m.sample(new Vec3(80,80,80));for(int i=0;i<40;i++)m.sample(new Vec3(.1,.2,.3));assertEquals(40,m.sampleCount());var p=m.predict(new Vec3(0,0,0));assertEquals(.5,p.x(),1e-8);assertEquals(1,p.y(),1e-8);assertEquals(1.5,p.z(),1e-8);m.reset();assertEquals(0,m.sampleCount());assertEquals(new Vec3(1,2,3),m.predict(new Vec3(1,2,3)));}
    @Test void noFabricatedFallbackForNonfiniteNativeMotion(){assertThrows(IllegalArgumentException.class,()->new HuntingMotion().sample(new Vec3(Double.NaN,0,0)));}
}
