package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.navigation.mc.GroundProfileMotionCarrier.NativePose;
/** Pure receipt consistency only; the real carrier identity and original selected geometry remain required. */
public final class CapturedProfileDropLatchMath {
    private CapturedProfileDropLatchMath(){}
    public static boolean mayRetain(NativePose before,NativePose after,Vec3 original,Vec3 landing,int actualJumpCalls) {
        return before!=null&&after!=null&&CapturedProfileMotionMath.originalDrop(original,landing)
                &&before.onGround()&&after.onGround()&&!before.sprinting()&&!after.sprinting()&&actualJumpCalls==0
                &&before.nativeTick()==after.nativeTick()
                &&Math.abs(before.position().y()-after.position().y())<=1E-8D
                &&Math.abs(after.position().y()-original.y())<=.081D
                &&Float.floatToIntBits(before.travelSpeed())==Float.floatToIntBits(after.travelSpeed());
    }
}
