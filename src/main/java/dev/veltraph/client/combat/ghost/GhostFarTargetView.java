package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import dev.veltraph.client.navigation.TravelHeading;

/** User-requested modern viewing envelope; not a recovered source selection or collision rule. */
public final class GhostFarTargetView {
    public static final double ACQUIRE_CONE_DEGREES=30, TRACK_WINDOW_DEGREES=18;
    public record Frame(long tick,Point eye,Point targetCenter,float actualYaw,float actualPitch) {}
    public record Plan(boolean observed,boolean acquisitionReady,float yaw,float pitch,double actualAngle) {}
    private final TravelHeading yaw=new TravelHeading(),pitch=new TravelHeading();
    private long lastTick=Long.MIN_VALUE;
    public void reset(){yaw.reset();pitch.reset();lastTick=Long.MIN_VALUE;}
    public Plan advance(Frame f){
        if(!valid(f)||f.tick()<=lastTick)return new Plan(false,false,0,0,Double.NaN);
        lastTick=f.tick();double dx=f.targetCenter().x()-f.eye().x(),dy=f.targetCenter().y()-f.eye().y(),dz=f.targetCenter().z()-f.eye().z();
        float targetYaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float targetPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        double angle=angle(f),yawError=wrap(targetYaw-f.actualYaw()),pitchError=targetPitch-f.actualPitch();
        // Interior comfort, not a correction to the boundary. An already comfortable view stays off-center.
        float desiredYaw=f.actualYaw()+(float)excess(yawError),desiredPitch=f.actualPitch()+(float)excess(pitchError);
        float nextYaw=wrap(yaw.step(f.actualYaw(),desiredYaw,1));
        float nextPitch=Math.clamp(pitch.stepLinear(f.actualPitch(),desiredPitch,1),-90,90);
        return new Plan(true,angle<=ACQUIRE_CONE_DEGREES,nextYaw,nextPitch,angle);
    }
    public static boolean acquisitionReady(Frame f){return valid(f)&&angle(f)<=ACQUIRE_CONE_DEGREES;}
    public static double angle(Frame f){
        if(!valid(f))return Double.NaN;
        double dx=f.targetCenter().x()-f.eye().x(),dy=f.targetCenter().y()-f.eye().y(),dz=f.targetCenter().z()-f.eye().z();
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz),y=Math.toRadians(f.actualYaw()),p=Math.toRadians(f.actualPitch());
        double dot=(-Math.sin(y)*Math.cos(p)*dx-Math.sin(p)*dy+Math.cos(y)*Math.cos(p)*dz)/length;
        return Math.toDegrees(Math.acos(Math.clamp(dot,-1,1)));
    }
    private static double excess(double error){return Math.copySign(Math.max(0,Math.abs(error)-TRACK_WINDOW_DEGREES),error);}
    private static boolean valid(Frame f){
        if(f==null||f.tick()<0||f.eye()==null||f.targetCenter()==null||!Float.isFinite(f.actualYaw())||!Float.isFinite(f.actualPitch())||Math.abs(f.actualPitch())>90)return false;
        double dx=f.targetCenter().x()-f.eye().x(),dy=f.targetCenter().y()-f.eye().y(),dz=f.targetCenter().z()-f.eye().z();
        double n=Math.sqrt(dx*dx+dy*dy+dz*dz);return Double.isFinite(n)&&n>1e-9;
    }
    private static float wrap(float a){float r=a%360;if(r>=180)r-=360;if(r< -180)r+=360;return r;}
}
