package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
/** Finite ordinary 26.3 jump sequence; prediction only, never an observed landing receipt. */
public final class GroundJumpLandingForecast {
    private GroundJumpLandingForecast(){}
    /** Two unaccelerated velocity steps cover the geometric ground-friction tail on this declared surface family. */
    public static boolean supportedBrakingFriction(double friction){
        if(!Double.isFinite(friction)||friction<=0||friction>.600001)return false;
        double multiplier=friction*(double).91F;
        return multiplier>0&&multiplier<1&&multiplier/(1-multiplier)<=2;
    }
    public record Forecast(Vec3 landing,Vec3 groundCoast,int nativeSteps,double expectedHeight,double expectedVelocityY){}
    public record Vertical(double height,double velocity){}
    /** LivingEntity ordinary jump uses float .42 and float .98 drag with the guarded .08 gravity. */
    public static Optional<Vertical> ordinaryVertical(int nativeSteps){
        if(nativeSteps<1||nativeSteps>12)return Optional.empty();double v=(double).42F,height=0;
        for(int step=0;step<nativeSteps;step++){height+=v;v=(v-.08)*(double).98F;}
        return Optional.of(new Vertical(height,v));
    }
    public static Optional<Forecast> forecast(Vec3 actual,Vec3 velocity,Vec3 takeoff,Vec3 destination,double speed,int nativeSteps){
        if(!GroundLocomotionMotion.airborneCurrent(actual,velocity,takeoff,destination,speed)
                ||GroundLocomotionMotion.airborne(actual,velocity,takeoff,destination,speed))return Optional.empty();
        var vertical=ordinaryVertical(nativeSteps).orElse(null);
        if(vertical==null||Math.abs(actual.y()-takeoff.y()-vertical.height())>.00001
                ||Math.abs(velocity.y()-vertical.velocity())>.00001||velocity.y()>=0
                ||actual.y()<destination.y()||actual.y()+velocity.y()>destination.y()+.001)return Optional.empty();
        var landing=new Vec3(actual.x()+velocity.x(),destination.y(),actual.z()+velocity.z());
        // The immediate airborne step remains inside the original rectangle. Later ground coast
        // requires its own fresh native support/body sweep instead of widening that flight rectangle.
        if(!GroundLocomotionMotion.airborneCurrent(landing,new Vec3(velocity.x(),0,velocity.z()),takeoff,destination,speed))return Optional.empty();
        var coast=new Vec3(landing.x()+velocity.x()*2,landing.y(),landing.z()+velocity.z()*2);
        return Optional.of(new Forecast(landing,coast,nativeSteps,vertical.height(),vertical.velocity()));
    }
}
