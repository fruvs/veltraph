package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import dev.veltraph.client.navigation.TravelHeading;

/**
 * Qualified modern WALK actuator, not an original path planner or collision authority.
 * Consumes the actual owned cursor point and native motion. The parent must revalidate
 * ownership, remaining path and the swept body of these actual keys before applying.
 * No position, velocity, packet, world, clock or input mutations occur here.
 */
public final class GhostSourceWalkSteering {
    public enum Outcome { MOVING, BRAKING, AT_POINT, REJECTED }
    public record Frame(long tick, Point position, float actualYaw, float actualPitch,
                        double velocityX, double velocityZ, boolean grounded,
                        Point steeringPoint, boolean sourceJump, float desiredPitch) {}
    public record Intent(Outcome outcome, float yaw, float pitch,
                         boolean forward, boolean back, boolean left, boolean right, boolean jump) {
        public boolean movingKeys() { return forward || back || left || right; }
    }
    private static final double ARRIVAL_RADIUS=.10, BRAKE_TICKS=1.35;
    private static final double KEY_HYSTERESIS_DEGREES=7;
    private static final long JUMP_PULSE_COOLDOWN_TICKS=6;
    private final TravelHeading yawHeading=new TravelHeading(), pitchHeading=new TravelHeading();
    private long lastTick=Long.MIN_VALUE, lastJumpTick=Long.MIN_VALUE;
    private int sector=-1;
    private boolean jumpArmed=true;

    /** A new installed cursor or revoked ownership must reset this cursor-local state. */
    public void reset() {
        yawHeading.reset(); pitchHeading.reset(); lastTick=Long.MIN_VALUE;
        lastJumpTick=Long.MIN_VALUE; sector=-1; jumpArmed=true;
    }

    /** At most one accepted observation per monotonically increasing simulation tick. */
    public Intent advance(Frame f) {
        if (!valid(f) || f.tick()<=lastTick) return rejected(f);
        lastTick=f.tick();
        if (!f.sourceJump() || !f.grounded()) jumpArmed=true;
        boolean cooldown=lastJumpTick==Long.MIN_VALUE
                || f.tick()>=lastJumpTick && f.tick()-lastJumpTick>=JUMP_PULSE_COOLDOWN_TICKS;
        boolean jump=f.sourceJump() && f.grounded() && jumpArmed && cooldown;
        if (jump) { jumpArmed=false; lastJumpTick=f.tick(); }
        double dx=f.steeringPoint().x()-f.position().x(), dz=f.steeringPoint().z()-f.position().z();
        double distance=Math.hypot(dx,dz);
        boolean needsVerticalStep=f.sourceJump() || Math.abs(f.steeringPoint().y()-f.position().y())>.35;
        if (distance<=ARRIVAL_RADIUS && !needsVerticalStep)
            return stationary(Outcome.AT_POINT,f);
        double ux=distance>1e-9?dx/distance:0, uz=distance>1e-9?dz/distance:0;
        double approach=f.velocityX()*ux+f.velocityZ()*uz;
        // Release propulsion before native momentum passes the cursor point. No reverse-key brake.
        if (!needsVerticalStep && distance<=ARRIVAL_RADIUS+Math.max(0,approach)*BRAKE_TICKS)
            return stationary(Outcome.BRAKING,f);
        // Remove only measured sideways drift, bounded relative to remaining displacement.
        // This is not clearance evidence; the caller validates the resulting swept key motion.
        double lateralX=f.velocityX()-approach*ux, lateralZ=f.velocityZ()-approach*uz;
        double lateralSpeed=Math.hypot(lateralX,lateralZ), correctionLimit=Math.min(.25,distance*.35);
        double correctionScale=lateralSpeed>1e-9?Math.min(1.4,correctionLimit/lateralSpeed):0;
        double directionX=dx-lateralX*correctionScale, directionZ=dz-lateralZ*correctionScale;
        float desiredYaw=distance>1e-9?(float)Math.toDegrees(Math.atan2(-directionX,directionZ)):f.actualYaw();
        float actualYaw=wrap(f.actualYaw());
        float yaw=wrap(yawHeading.step(actualYaw,desiredYaw,1));
        float pitch=Math.max(-90,Math.min(90,pitchHeading.stepLinear(f.actualPitch(),f.desiredPitch(),1)));
        if (distance<=ARRIVAL_RADIUS)
            return new Intent(Outcome.MOVING,yaw,pitch,false,false,false,false,jump);
        // Vanilla keys act against the CURRENT native yaw, never the requested next camera yaw.
        double relative=wrap(desiredYaw-actualYaw);
        if (sector<0 || Math.abs(wrap((float)(relative-sector*45)))>22.5+KEY_HYSTERESIS_DEGREES)
            sector=Math.floorMod((int)Math.round(relative/45),8);
        return new Intent(Outcome.MOVING,yaw,pitch,
                sector==0||sector==1||sector==7,
                sector==3||sector==4||sector==5,
                sector==5||sector==6||sector==7,
                sector==1||sector==2||sector==3,jump);
    }

    private Intent stationary(Outcome outcome,Frame f) {
        sector=-1; yawHeading.reset(); pitchHeading.reset();
        return new Intent(outcome,wrap(f.actualYaw()),f.actualPitch(),false,false,false,false,false);
    }
    private static boolean valid(Frame f) {
        return f!=null && f.tick()>=0 && f.position()!=null && f.steeringPoint()!=null
                && Float.isFinite(f.actualYaw()) && Float.isFinite(f.actualPitch())
                && Math.abs(f.actualPitch())<=90 && Float.isFinite(f.desiredPitch())
                && Math.abs(f.desiredPitch())<=90 && Double.isFinite(f.velocityX()) && Double.isFinite(f.velocityZ())
                && Double.isFinite(Math.hypot(f.velocityX(),f.velocityZ()))
                && Double.isFinite(f.steeringPoint().x()-f.position().x())
                && Double.isFinite(f.steeringPoint().z()-f.position().z())
                && Double.isFinite(Math.hypot(f.steeringPoint().x()-f.position().x(),f.steeringPoint().z()-f.position().z()));
    }
    private static Intent rejected(Frame f) {
        float yaw=f!=null&&Float.isFinite(f.actualYaw())?wrap(f.actualYaw()):0;
        float pitch=f!=null&&Float.isFinite(f.actualPitch())?Math.max(-90,Math.min(90,f.actualPitch())):0;
        return new Intent(Outcome.REJECTED,yaw,pitch,false,false,false,false,false);
    }
    private static float wrap(float angle) { float r=angle%360; if(r>=180)r-=360; if(r< -180)r+=360; return r; }
}
