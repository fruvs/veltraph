package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
/** Recognition of one observed native ground-collision tick, not a broader airborne velocity grant. */
public final class GroundJumpObservedLanding {
    private GroundJumpObservedLanding(){}
    public record AirSample(int issuedTick,int tick,Vec3 takeoff,Vec3 target,Vec3 position,Vec3 velocity,double speed,boolean forwardPublished,boolean sprinting){}
    public record Contact(Vec3 coast,int nativeSteps,double horizontalBound){}
    public static double maximumCoastDistance(){return ((.16+(double).02F)*(double).91F)/(1-.600001*(double).91F);}
    public static Optional<Contact> contact(AirSample previous,int issuedTick,int tick,boolean nativeOnGround,boolean nativeSprinting,
            Vec3 actual,Vec3 velocity,Vec3 takeoff,Vec3 target,double speed){
        if(previous==null||!nativeOnGround||nativeSprinting||previous.sprinting()
                ||!GroundLocomotionMotion.finite(previous.takeoff())||!GroundLocomotionMotion.finite(previous.target())
                ||!GroundLocomotionMotion.finite(previous.position())||!GroundLocomotionMotion.finite(previous.velocity())||issuedTick<0||previous.issuedTick()!=issuedTick||tick!=previous.tick()+1
                ||!previous.takeoff().equals(takeoff)||!previous.target().equals(target)
                ||!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(velocity)
                ||!GroundLocomotionMotion.speedAttribute(speed,false)||!GroundLocomotionMotion.speedAttribute(previous.speed(),false)
                ||!GroundLocomotionMotion.airborneCurrent(previous.position(),previous.velocity(),takeoff,target,previous.speed())
                ||Math.abs(actual.y()-target.y())>.00001||actual.distanceTo(target)>.65)return Optional.empty();
        int steps=tick-issuedTick;var before=GroundJumpLandingForecast.ordinaryVertical(steps-1).orElse(null);var after=GroundJumpLandingForecast.ordinaryVertical(steps).orElse(null);
        if(before==null||after==null||before.velocity()>=0||takeoff.y()+before.height()<target.y()
                ||takeoff.y()+after.height()>target.y()+.00001
                ||Math.abs(previous.position().y()-takeoff.y()-before.height())>.00001
                ||Math.abs(previous.velocity().y()-before.velocity())>.00001
                ||Math.abs(velocity.y()-(-.08*(double).98F))>.000001)return Optional.empty();
        // No-sprint air input can add at most .02 before native .91 drag. The sample
        // is retained only after this exact navigator successfully publishes its owned keys.
        double acceleration=previous.forwardPublished()?(double).02F:0;
        double impulseX=velocity.x()/(double).91F-previous.velocity().x(),impulseZ=velocity.z()/(double).91F-previous.velocity().z();
        if(Math.hypot(impulseX,impulseZ)>acceleration+.000001
                ||Math.abs((actual.x()-previous.position().x())-velocity.x()/(double).91F)>.000001
                ||Math.abs((actual.z()-previous.position().z())-velocity.z()/(double).91F)>.000001)return Optional.empty();
        double dx=target.x()-takeoff.x(),dz=target.z()-takeoff.z(),length=Math.hypot(dx,dz);
        if(length<=.01||length>1.65)return Optional.empty();dx/=length;dz/=length;
        double x=actual.x()-takeoff.x(),z=actual.z()-takeoff.z();
        if(x*dx+z*dz<-.10||x*dx+z*dz>length+.35||Math.abs(x*dz-z*dx)>.18)return Optional.empty();
        double bound=(Math.hypot(previous.velocity().x(),previous.velocity().z())+acceleration)*(double).91F+.000001;
        if(Math.hypot(velocity.x(),velocity.z())>bound)return Optional.empty();
        double tail=1/(1-.600001*(double).91F);var coast=new Vec3(actual.x()+velocity.x()*tail,actual.y(),actual.z()+velocity.z()*tail);
        return Optional.of(new Contact(coast,steps,bound));
    }
}
