package dev.veltraph.client.combat.ghost;

import net.minecraft.util.Mth;
/** Preserves original float atan2/wrap and mapped Mth lookup-table trigonometry. */
public final class GhostSourceFloatDirection {
    private GhostSourceFloatDirection() {}
    public static GhostSourceSampledRay.Point between(GhostSourceSampledRay.Point eye,
                                                       GhostSourceSampledRay.Point target,float actualPlayerYaw) {
        if(!Float.isFinite(actualPlayerYaw))throw new IllegalArgumentException("Actual finite native yaw required");
        double x=target.x()-eye.x(),y=target.y()-eye.y(),z=target.z()-eye.z();
        float pitch=(float)-Math.atan2(Math.sqrt(x*x+z*z),y);
        float yaw=(float)Math.atan2(z,x);
        pitch=(float)Mth.wrapDegrees((((double)(pitch*180.0f)/Math.PI)+90.0)*-1.0);
        yaw=(float)Mth.wrapDegrees((double)(yaw*180.0f)/Math.PI-90.0);
        if(pitch>=90||pitch<=-90)yaw=actualPlayerYaw;
        float a=Mth.cos(-yaw*((float)Math.PI/180)-(float)Math.PI);
        float b=Mth.sin(-yaw*((float)Math.PI/180)-(float)Math.PI);
        float c=-Mth.cos(-pitch*((float)Math.PI/180));
        float d=Mth.sin(-pitch*((float)Math.PI/180));
        return new GhostSourceSampledRay.Point((double)(b*c),(double)d,(double)(a*c));
    }
}
