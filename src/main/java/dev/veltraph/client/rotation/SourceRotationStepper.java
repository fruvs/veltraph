package dev.veltraph.client.rotation;

/** Readable translation of RotationController + MaxConstants + NumberUtil.
 * See docs/ROTATION_SOURCE_AUDIT.md for method/line provenance and unresolved context modifiers.
 */
public final class SourceRotationStepper {
    public record Settings(double sensitivity,double speedMultiplier,double scriptMultiplier,
                           double actionMultiplier,double curveGain,double endSlope) {
        public Settings {
            if(!Double.isFinite(sensitivity)||sensitivity<0||sensitivity>1
                    ||!positive(speedMultiplier)||!positive(scriptMultiplier)||!positive(actionMultiplier)
                    ||!positive(curveGain)||!Double.isFinite(endSlope)||endSlope<0||endSlope>1.2)
                throw new IllegalArgumentException("Invalid rotation settings");
        }
        public static Settings standard(double sensitivity,double speed) { return new Settings(sensitivity,speed,1,1,3,1); }
        private static boolean positive(double n) { return Double.isFinite(n)&&n>0; }
    }
    public record Step(float yaw,float pitch,float yawSpeed,float pitchSpeed,boolean complete,boolean rejected) {}
    private final Mean4 gains=new Mean4(),slopes=new Mean4();
    private final double sessionMultiplier;
    public SourceRotationStepper() { this(1); }
    public SourceRotationStepper(double sessionMultiplier) {
        if(!Double.isFinite(sessionMultiplier)||sessionMultiplier<.97||sessionMultiplier>1.02)
            throw new IllegalArgumentException("Session multiplier outside recovered range");
        this.sessionMultiplier=sessionMultiplier;
    }
    public Step step(float yaw,float pitch,float targetYaw,float targetPitch,Settings settings) {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(targetYaw)||!Float.isFinite(targetPitch)
                ||targetPitch< -90||targetPitch>90) throw new IllegalArgumentException("Invalid rotation angles");
        double signedYaw=wrap(targetYaw-yaw),signedPitch=targetPitch-pitch;
        double yawError=Math.abs(signedYaw),pitchError=Math.abs(signedPitch),total=yawError+pitchError;
        if(total==0) return new Step(yaw,pitch,0,0,true,false);
        float gcd=mouseStep(settings.sensitivity());
        double compensation=Math.clamp(.35634039526/gcd,.1,24);
        double shared=compensation*24*settings.speedMultiplier()*settings.scriptMultiplier()*settings.actionMultiplier()*sessionMultiplier;
        float yawSpeed=quantizedSpeed(curve(yawError,true,gains.add(settings.curveGain()),slopes.add(settings.endSlope()))*(yawError/total)*shared,gcd);
        float pitchSpeed=quantizedSpeed(curve(pitchError,false,gains.add(settings.curveGain()),slopes.add(settings.endSlope()))*(pitchError/total)*shared,gcd);
        // The decoded implementation refuses the entire step; it does not silently clamp one axis.
        if(yawSpeed>48*settings.speedMultiplier()||pitchSpeed>48*settings.speedMultiplier())
            return new Step(yaw,pitch,yawSpeed,pitchSpeed,false,true);
        boolean yawContinues=yawSpeed<yawError,pitchContinues=pitchSpeed<pitchError;
        float nextYaw=yawContinues?yaw+(float)Math.copySign(yawSpeed,signedYaw):nearestStep(yaw,yawError,gcd,signedYaw);
        float nextPitch=Math.clamp(pitchContinues?pitch+(float)Math.copySign(pitchSpeed,signedPitch):nearestStep(pitch,pitchError,gcd,signedPitch),-90,90);
        boolean complete=(!yawContinues&&!pitchContinues)||(yawError<gcd&&pitchError<gcd);
        return new Step(nextYaw,nextPitch,yawSpeed,pitchSpeed,complete,false);
    }
    public static float mouseStep(double sensitivity) {
        float base=(float)sensitivity*.6f+.2f;
        return base*base*base*8f*.15f;
    }
    public static double curve(double remaining,boolean yaw,double gain,double slope) {
        double axis=yaw?180:90,progress=1-Math.clamp(remaining/axis,0,1);
        return gain*(Math.clamp(axis,30,130)/180)*Math.sin(Math.PI*progress)-slope*progress+1.2;
    }
    public static float wrap(float value) { return (value%360+540)%360-180; }
    private static float quantizedSpeed(double product,float gcd) { return gcd*Math.max(1,Math.round(product)); }
    private static float nearestStep(float current,double error,float gcd,double direction) {
        int count=Math.round((float)error/gcd);float increment=(float)Math.copySign(gcd,direction);
        for(int i=0;i<count;i++) current+=increment;
        return current;
    }
    private static final class Mean4 {
        private final double[] values=new double[4];private int count,index;
        double add(double value) {
            values[index]=value;index=(index+1)%4;count=Math.min(4,count+1);
            double total=0;for(int i=0;i<count;i++) total+=values[i];return total/count;
        }
    }
}
