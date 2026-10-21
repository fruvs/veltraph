package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
/** Conservative native ability-flight one-output plus neutral-coast bound, with all headings included.
 * Official26.3 LocalPlayer adds signed3*flyingSpeed vertically; Player restores pre-travel Y*.6;
 * LivingEntity horizontal air decay is clamp(1-(1-.91f)*AIR_DRAG_MODIFIER,0,1).
 * It cannot grant flight or normalize actual attributes. Native loaded geometry must prove this box. */
public final class FlightMotionEnvelope {
    public record Bounds(Vec3 min,Vec3 max){}
    private FlightMotionEnvelope(){}
    public static double horizontalDecay(double modifier){
        if(!Double.isFinite(modifier))return Double.NaN;
        float value=1-(1-.91f)*(float)modifier;return Math.max(0,Math.min(1,value));
    }
    public static Bounds bounds(Vec3 position,Vec3 velocity,FlightController.Output output,float flyingSpeed,
                                float nativeSpeed,double movementAttribute,double airDrag,boolean onGround,boolean sprinting,boolean discardFriction,double jumpPower){
        double decay=horizontalDecay(airDrag);
        if(discardFriction||!SharedMovementPolicy.finite(position)||!SharedMovementPolicy.finite(velocity)||output==null
            ||!Float.isFinite(flyingSpeed)||flyingSpeed<=0||!Float.isFinite(nativeSpeed)||nativeSpeed<0
            ||!Double.isFinite(movementAttribute)||movementAttribute<0||!Double.isFinite(decay)||decay>=1||!Double.isFinite(jumpPower)||jumpPower<0)return null;
        boolean horizontal=output.forward()||output.back()||output.left()||output.right();
        // Sprint may begin natively after the key publication. Native flying sprint doubles ability speed; grounded sprint has the built-in1.3 factor. These bounds cover
        // normalized horizontal input, non-amplifying ground friction correction and either sprint state.
        double groundSpeed=(double)Math.nextUp(Math.max(nativeSpeed,(float)(movementAttribute*1.3)));
        double impulse=horizontal?Math.nextUp(onGround?Math.max(2.0*flyingSpeed,groundSpeed):2.0*flyingSpeed):0;
        if(onGround&&output.up()&&(sprinting||output.forward()))impulse+=Math.nextUp((double).2f);
        double tail=1/(1-decay);if(!Double.isFinite(tail)||tail>100000)return null;
        float vertical=(output.up()?1:0)-(output.down()?1:0);double dy=(float)(vertical*flyingSpeed*3.0f);
        double low=velocity.y()+dy,high=low;
        if(onGround&&output.up())high=Math.max(high,jumpPower);
        low=Math.nextDown(low*2.5);high=Math.nextUp(high*2.5);
        var min=new Vec3(position.x()+Math.min(0,(velocity.x()-impulse)*tail),position.y()+Math.min(0,low),position.z()+Math.min(0,(velocity.z()-impulse)*tail));
        var max=new Vec3(position.x()+Math.max(0,(velocity.x()+impulse)*tail),position.y()+Math.max(0,high),position.z()+Math.max(0,(velocity.z()+impulse)*tail));
        return SharedMovementPolicy.finite(min)&&SharedMovementPolicy.finite(max)?new Bounds(min,max):null;
    }
}
